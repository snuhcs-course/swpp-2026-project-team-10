// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.session

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lastpenguin.pix.camera.PhotoAlbum
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.PhotoRefusal
import com.lastpenguin.pix.session.protocol.SessionMessage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the Camera screen tells the subject's phone about its photos (FR-6.14), the photographer's side of [PhotoSyncer]. */
interface PhotoShare {
    /**
     * The shutter was pressed: the photo's shot, an id it keeps until it is sent or given up, or null outside a session.
     * The photo belongs to the session running now. A connected subject hears of it at once (`photo.shutter`), and
     * waits for that photo by its shot (#271).
     */
    fun shutter(): String?

    /** The photo of [shot], from [shutter], taken at [takenAt], is saved here as [uri]. */
    fun taken(shot: String, uri: Uri, takenAt: Long)

    /** The photo of [shot] could not be saved, so there is nothing to send. */
    fun notTaken(shot: String)

    /** For a phone with nothing to share, such as the ViewModel's tests. */
    object None : PhotoShare {
        override fun shutter(): String? = null

        override fun taken(shot: String, uri: Uri, takenAt: Long) = Unit

        override fun notTaken(shot: String) = Unit
    }
}

/** A saved photo, read when its turn comes. The caller closes [stream]. */
class PhotoContent(val bytes: Long, val stream: InputStream)

fun interface PhotoReader {
    /** null when the photo is gone, for example deleted from the gallery. */
    fun open(uri: Uri): PhotoContent?
}

class ContentPhotoReader(private val resolver: ContentResolver) : PhotoReader {
    override fun open(uri: Uri): PhotoContent? = try {
        resolver.openFileDescriptor(uri, "r")?.let { file ->
            PhotoContent(file.statSize, ParcelFileDescriptor.AutoCloseInputStream(file))
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/**
 * Brings each photo taken in a session to the subject's "Pix" album (Design 2.5.1: photo.*; Design 2.6.5, *Photos on
 * both phones*). Unlike the other syncers it runs for the whole session, through a reconnect, because a photo goes on
 * from the next chunk instead of being sent again (Design 2.6.7).
 *
 * Photographer ([PhotoShare], [startAsSender]): sends the photos one at a time, in the order they were taken. Each
 * chunk goes through [SessionManager.sendBulk], so it never holds up another message, and only while at most
 * [WINDOW_CHUNKS] chunks are unacknowledged, so it never overruns the phones' message buffers. A photo that does not
 * reach the album says so in [sendFailed], and the next one goes on. [drop] ends it all with the session.
 *
 * Subject ([startAsReceiver]): writes the chunks to one file in [partDir], not to memory, saves it to [album] at
 * `photo.end`, and answers `photo.saved`. [received] says how each ended, for the subject's toast, [shutters] when each
 * was taken, for Subject view's blink (Iter3-A12), and [receiving] whether one is on its way, for its sign (#271).
 *
 * Both: [sessionPhotos] are the session's photos on this phone, taken or received, for the thumbnail and the viewer
 * of Camera and Subject view (Iter4-A2).
 * Owner: Shared steps (Iter3-P17).
 */
class PhotoSyncer(
    private val session: SessionManager,
    private val reader: PhotoReader,
    private val album: PhotoAlbum,
    private val partDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PhotoShare {

    // ---- Both -------------------------------------------------------------------------------------------------

    /** A session's photos so far; another session's id starts them again. */
    private class Kept(val sessionId: String, val photos: List<Uri>)

    private val kept = MutableStateFlow<Kept?>(null)

    /**
     * The photos of the session running now in this phone's album, oldest first (FR-1.6, FR-6.14): those taken here on
     * the photographer's phone, those received on the subject's. null outside a session, and empty until its first.
     * They stay through a reconnect, which keeps the session's id.
     */
    val sessionPhotos: Flow<List<Uri>?> = combine(session.state, kept) { state, kept ->
        state.liveSessionId()?.let { id -> kept?.takeIf { it.sessionId == id }?.photos.orEmpty() }
    }.distinctUntilChanged()

    /**
     * [uri] is one more photo of [sessionId] in this phone's album, while that session still runs: a save that ends
     * after it, once another started, would put the photo in the other session and take that one's photos away.
     */
    private fun keep(sessionId: String, uri: Uri) {
        if (currentSession() != sessionId) return
        kept.update { old -> Kept(sessionId, old?.takeIf { it.sessionId == sessionId }?.photos.orEmpty() + uri) }
    }

    /** [uri] was deleted from this phone (FR-1.16): it leaves the session's photos, and the viewer's. */
    private fun forget(uri: Uri) {
        kept.update { old -> old?.let { Kept(it.sessionId, it.photos - uri) } }
    }

    /**
     * Deletes [uri], one of this phone's photos, through [album] (FR-1.16), and drops it from the session's photos.
     * True once it is gone, false when [album] could not delete it. On the photographer's phone a photo still waiting
     * to go, or going, is read again at its turn, so it leaves the session's photos at once but stays on the phone
     * until it has reached the subject, failed, or its session ended; [later] deletes it then, and true says so.
     * Deleted at once, it would never reach the subject, and no copy would be left (#247).
     */
    suspend fun delete(uri: Uri, album: PhotoDeleter, later: CoroutineScope): Boolean {
        if (uri !in outgoing.value) return album.delete(uri).also { gone -> if (gone) forget(uri) }
        forget(uri)
        later.launch {
            outgoing.first { uri !in it }
            if (!album.delete(uri)) Log.w(TAG, "A photo deleted while it went to the subject stays on this phone")
        }
        return true
    }

    // ---- Photographer -----------------------------------------------------------------------------------------

    private data class Outgoing(val photoId: String, val uri: Uri, val takenAt: Long, val shot: String)

    /** How far the subject has the photo that is going; [saved] is its `photo.saved`. */
    private data class Progress(val photoId: String, val next: Int = 0, val saved: Boolean? = null)

    private val queue = Channel<Outgoing>(Channel.UNLIMITED)

    /** The photos waiting to go and the one going, which [delete] leaves on the phone until they have gone. */
    private val outgoing = MutableStateFlow<Set<Uri>>(emptySet())
    private val progress = MutableStateFlow<Progress?>(null)

    /** The photos of this session not yet confirmed or given up, from the shutter on, the one going included. */
    private val unconfirmed = AtomicInteger(0)

    private val _sendFailed = Channel<Unit>(Channel.UNLIMITED)

    /** One for each photo of this session that will not reach the subject's album; none is lost unread. */
    val sendFailed: Flow<Unit> = _sendFailed.receiveAsFlow()

    /** The session of each shot not yet taken or given up. */
    private val shots = ConcurrentHashMap<String, String>()

    override fun shutter(): String? {
        val sessionId = currentSession() ?: return null
        val shot = UUID.randomUUID().toString()
        shots[shot] = sessionId
        unconfirmed.incrementAndGet()
        if (session.state.value is SessionState.Connected) session.send(SessionMessage.PhotoShutter(shot))
        return shot
    }

    /**
     * Saving takes seconds, so another session may run by now: a photo goes only to the session of its shutter. One
     * whose session ended meanwhile was counted when it ended, by [drop].
     */
    override fun taken(shot: String, uri: Uri, takenAt: Long) {
        val sessionId = shots.remove(shot) ?: return
        if (currentSession() != sessionId) return
        keep(sessionId, uri)
        outgoing.update { it + uri }
        queue.trySend(Outgoing(UUID.randomUUID().toString(), uri, takenAt, shot))
    }

    override fun notTaken(shot: String) {
        val sessionId = shots.remove(shot) ?: return
        if (currentSession() != sessionId) return
        unconfirmed.updateAndGet { maxOf(it - 1, 0) }
        dropped(shot)
    }

    /**
     * The subject waits for each photo from its `photo.shutter` on (#271): one that will not come after all, before its
     * `photo.begin`, is said by its [shot], or Subject view would say a photo is on its way until its 30 s end.
     */
    private fun dropped(shot: String) {
        if (currentSession() != null) session.send(SessionMessage.PhotoDropped(shot))
    }

    private fun currentSession(): String? = session.state.value.liveSessionId()

    private fun SessionState.liveSessionId(): String? = when (this) {
        is SessionState.Connected -> sessionId
        is SessionState.Reconnecting -> sessionId
        else -> null
    }

    fun startAsSender(scope: CoroutineScope) {
        scope.launch(dispatcher) {
            launch {
                session.incoming.collect { message ->
                    when (message) {
                        is SessionMessage.PhotoAck -> onProgress(message.photoId) {
                            it.copy(next = maxOf(it.next, message.next))
                        }

                        is SessionMessage.PhotoSaved -> onProgress(message.photoId) { it.copy(saved = message.ok) }

                        else -> Unit
                    }
                }
            }
            for (photo in queue) {
                val sent = try {
                    send(photo)
                } catch (error: IOException) {
                    Log.w(TAG, "Photo ${photo.photoId} could not be read", error)
                    false
                }
                unconfirmed.updateAndGet { maxOf(it - 1, 0) }
                if (!sent) _sendFailed.trySend(Unit)
                progress.value = null
                outgoing.update { it - photo.uri }
            }
        }
    }

    private fun onProgress(photoId: String, change: (Progress) -> Progress) {
        progress.update { current -> if (current?.photoId == photoId) change(current) else current }
    }

    /** The session ended: what is not confirmed stays here. Returns how many photos that is, failures unread included. */
    fun drop(): Int {
        while (queue.tryReceive().isSuccess) Unit
        shots.clear()
        progress.value = null
        // None goes any more, so a photo deleted on its way is deleted now.
        outgoing.value = emptySet()
        var unread = 0
        while (_sendFailed.tryReceive().isSuccess) unread++
        return unconfirmed.getAndSet(0) + unread
    }

    private suspend fun send(photo: Outgoing): Boolean {
        val content = reader.open(photo.uri) ?: return false.also { dropped(photo.shot) }
        return content.stream.use { stream ->
            if (content.bytes !in 1..MAX_PHOTO_BYTES) return@use false.also { dropped(photo.shot) }
            progress.value = Progress(photo.photoId)
            val chunks = ((content.bytes + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()
            val begin = SessionMessage.PhotoBegin(
                photo.photoId,
                FORMAT,
                content.bytes,
                chunks,
                photo.takenAt,
                photo.shot,
            )
            Timings.mark("photo.sent", "begin ${photo.photoId} ${content.bytes}")
            if (!session.sendBulk(begin)) return@use false
            val saved = sendChunks(photo.photoId, stream, chunks)
            Timings.mark("photo.sent", "end ${photo.photoId} $saved")
            saved
        }
    }

    /** Whether the subject saved the photo, which it may say before the last chunk when it refuses. */
    private suspend fun sendChunks(photoId: String, stream: InputStream, chunks: Int): Boolean {
        val crc = CRC32()
        val encoder = Base64.getEncoder()
        val buffer = ByteArray(CHUNK_BYTES)
        for (index in 0 until chunks) {
            val room = withTimeoutOrNull(STALL_MS) {
                progress.first { it == null || it.saved != null || index - it.next < WINDOW_CHUNKS }
            } ?: return false
            room.saved?.let { return it }
            val read = stream.readChunk(buffer)
            if (read <= 0) return false
            crc.update(buffer, 0, read)
            val data = encoder.encodeToString(buffer.copyOf(read))
            if (!session.sendBulk(SessionMessage.PhotoChunk(photoId, index, data))) return false
        }
        if (!session.sendBulk(SessionMessage.PhotoEnd(photoId, crc.value))) return false
        val answer = withTimeoutOrNull(STALL_MS) { progress.first { it == null || it.saved != null } }
        return answer?.saved == true
    }

    /** Fills [buffer] unless the stream ends first; a read may return less than asked. Returns how much came. */
    private fun InputStream.readChunk(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    // ---- Subject ----------------------------------------------------------------------------------------------

    /** The photo arriving, as far as it came. */
    private class Arriving(val begin: SessionMessage.PhotoBegin, val file: File) {
        val output = FileOutputStream(file)
        val crc = CRC32()
        var next = 0
        var written = 0L
        var startsAsJpeg = false

        fun close() {
            try {
                output.close()
            } catch (_: IOException) {
                Unit
            }
        }
    }

    private var arriving: Arriving? = null

    /** This session's saved photos, so that one cannot be saved twice. */
    private val saved = HashSet<String>()

    private val _received = Channel<Boolean>(Channel.UNLIMITED)

    /** True when a photo reached this phone's album, false when it could not be saved. */
    val received: Flow<Boolean> = _received.receiveAsFlow()

    private val _shutters =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each `photo.shutter` while a screen listens; one that no screen saw is a moment gone, not kept for later. */
    val shutters: Flow<Unit> = _shutters.asSharedFlow()

    /**
     * The photographer's photos from their shutter on that are not yet saved, refused, or given up here: by shot, and
     * as a count for a phone before #271, whose messages name none.
     */
    private data class Waits(val shots: Set<String> = emptySet(), val unnamed: Int = 0) {
        val any: Boolean get() = shots.isNotEmpty() || unnamed > 0

        fun add(shot: String?): Waits = if (shot != null) copy(shots = shots + shot) else copy(unnamed = unnamed + 1)

        /** The photo of [shot] begins, its shutter heard or not; an unnamed begin is the one unnamed photo at least. */
        fun began(shot: String?): Waits =
            if (shot != null) copy(shots = shots + shot) else copy(unnamed = maxOf(unnamed, 1))

        /** The photo of [shot] came or will not: that wait, and only that one, ends. */
        fun end(shot: String?): Waits =
            if (shot != null) copy(shots = shots - shot) else copy(unnamed = maxOf(unnamed - 1, 0))
    }

    private val awaited = MutableStateFlow(Waits())

    /** Counts the photo messages, each of which shows that a photo is still on its way. */
    private val photoMessagesSeen = MutableStateFlow(0L)

    /**
     * Whether a photo of the photographer's is on its way to this phone's album (#271): from its `photo.shutter`, or its
     * `photo.begin` when the shutter was lost or came during a drop, until it is saved, refused, or given up, by
     * `photo.dropped` before its begin. Each is waited for by its shot, so a lost shutter or a dropped photo never ends
     * another's wait. A photographer's phone before #271 never says a photo is dropped, and [STALL_MS] without a photo
     * message ends the wait then, as does the session's end.
     */
    val receiving: Flow<Boolean> = awaited.map { it.any }.distinctUntilChanged()

    fun startAsReceiver(scope: CoroutineScope) {
        scope.launch(dispatcher) {
            clearParts()
            saved.clear()
            awaited.value = Waits()
            // What an earlier session left unread is not about this one's photographer.
            while (_received.tryReceive().isSuccess) Unit
            // Files and the album take their time, and a reader of incoming that waits costs every reader its messages
            // once the buffer is full. The window keeps this queue short.
            val photoMessages = Channel<SessionMessage>(Channel.UNLIMITED)
            launch {
                session.incoming.collect { message ->
                    if (message is SessionMessage.PhotoBegin || message is SessionMessage.PhotoChunk ||
                        message is SessionMessage.PhotoEnd
                    ) {
                        photoMessagesSeen.update { it + 1 }
                        photoMessages.trySend(message)
                    }
                    if (message is SessionMessage.PhotoShutter) {
                        awaited.update { it.add(message.shot) }
                        photoMessagesSeen.update { it + 1 }
                        _shutters.tryEmit(Unit)
                    }
                    // That photo will not come; one whose shutter never arrived ends no other's wait.
                    if (message is SessionMessage.PhotoDropped) awaited.update { it.end(message.shot) }
                }
            }
            launch {
                // A wait with no photo message for this long is over: that photo is not coming.
                combine(awaited, photoMessagesSeen) { waits, _ -> waits.any }.collectLatest { waiting ->
                    if (!waiting) return@collectLatest
                    delay(STALL_MS)
                    awaited.value = Waits()
                }
            }
            try {
                for (message in photoMessages) {
                    when (message) {
                        is SessionMessage.PhotoBegin -> onBegin(message)
                        is SessionMessage.PhotoChunk -> onChunk(message)
                        is SessionMessage.PhotoEnd -> onEnd(message)
                        else -> Unit
                    }
                }
            } finally {
                abandon()
                clearParts()
                awaited.value = Waits()
            }
        }
    }

    private fun onBegin(begin: SessionMessage.PhotoBegin) {
        // The photographer gave up the photo that was arriving, if this is another, so it is no longer awaited.
        val gone = arriving?.begin?.takeIf { it.photoId != begin.photoId }
        if (gone != null) awaited.update { it.end(gone.shot) }
        abandon()
        if (begin.photoId in saved) {
            session.send(SessionMessage.PhotoSaved(begin.photoId, ok = true))
            return
        }
        // Its shutter may have been lost, or come during a drop and never been sent.
        awaited.update { it.began(begin.shot) }
        val refusal = when {
            begin.bytes !in 1..MAX_PHOTO_BYTES -> PhotoRefusal.TOO_LARGE
            begin.format != FORMAT -> PhotoRefusal.BROKEN
            begin.chunks.toLong() != (begin.bytes + CHUNK_BYTES - 1) / CHUNK_BYTES -> PhotoRefusal.BROKEN
            else -> null
        }
        if (refusal != null) return answer(begin, refusal)
        arriving = try {
            partDir.mkdirs()
            Arriving(begin, File(partDir, "${UUID.randomUUID()}$PART_SUFFIX"))
        } catch (error: IOException) {
            Log.w(TAG, "No file for photo ${begin.photoId}", error)
            answer(begin, PhotoRefusal.STORAGE)
            null
        }
    }

    private fun onChunk(chunk: SessionMessage.PhotoChunk) {
        val photo = arriving?.takeIf { it.begin.photoId == chunk.photoId } ?: return
        val bytes = decode(chunk.data)
        val fits = bytes != null && bytes.size <= CHUNK_BYTES && photo.written + bytes.size <= photo.begin.bytes
        if (chunk.index != photo.next || !fits) return fail(photo, PhotoRefusal.BROKEN)
        try {
            photo.output.write(bytes)
        } catch (error: IOException) {
            Log.w(TAG, "Photo ${chunk.photoId} could not be written", error)
            return fail(photo, PhotoRefusal.STORAGE)
        }
        if (photo.next == 0) photo.startsAsJpeg = bytes.size >= 2 && bytes[0] == JPEG_0 && bytes[1] == JPEG_1
        photo.crc.update(bytes)
        photo.written += bytes.size
        photo.next++
        if (photo.next % ACK_EVERY == 0) session.send(SessionMessage.PhotoAck(chunk.photoId, photo.next))
    }

    private fun decode(data: String): ByteArray? = try {
        Base64.getDecoder().decode(data)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun onEnd(end: SessionMessage.PhotoEnd) {
        val photo = arriving?.takeIf { it.begin.photoId == end.photoId } ?: return
        val whole = photo.next == photo.begin.chunks && photo.written == photo.begin.bytes &&
            photo.crc.value == end.crc32 && photo.startsAsJpeg
        if (!whole) return fail(photo, PhotoRefusal.BROKEN)
        photo.close()
        // The session the photo came in, before the save, which takes its time and outlasts a session that ends.
        val sessionId = currentSession()
        album.save(photo.file, end.photoId, photo.begin.takenAt).fold(
            onSuccess = { uri ->
                saved += end.photoId
                sessionId?.let { keep(it, uri) }
                answer(photo.begin, null)
                Timings.mark("photo.applied", "saved ${end.photoId}")
            },
            onFailure = { error ->
                Log.w(TAG, "Photo ${end.photoId} could not be saved", error)
                answer(photo.begin, PhotoRefusal.STORAGE)
            },
        )
        abandon()
    }

    private fun fail(photo: Arriving, refusal: PhotoRefusal) {
        answer(photo.begin, refusal)
        abandon()
    }

    /** Tells the photographer, and this phone's person, how the photo of [begin] ended: saved, or [refusal]. */
    private fun answer(begin: SessionMessage.PhotoBegin, refusal: PhotoRefusal?) {
        awaited.update { it.end(begin.shot) }
        session.send(SessionMessage.PhotoSaved(begin.photoId, ok = refusal == null, reason = refusal))
        _received.trySend(refusal == null)
    }

    private fun abandon() {
        arriving?.let { photo ->
            photo.close()
            photo.file.delete()
        }
        arriving = null
    }

    /** What an app that died left behind, and what a session that ended did not finish. */
    private fun clearParts() {
        partDir.listFiles()?.filter { it.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
    }

    companion object {
        /** A photo over this is not sent; 12 MP JPEGs are a few MB. */
        const val MAX_PHOTO_BYTES = 40L * 1024 * 1024
        const val CHUNK_BYTES = GuideImageChunks.CHUNK_BYTES

        /** Fewer than the 64 messages `SessionManager.incoming` holds, so that a slow reader loses none. */
        const val WINDOW_CHUNKS = 32
        const val ACK_EVERY = 16

        /** No word from the subject for this long gives the photo up. Longer than a reconnect's 10 s. */
        const val STALL_MS = 30_000L

        private const val FORMAT = "jpeg"
        private const val PART_SUFFIX = ".part"
        private const val JPEG_0 = 0xFF.toByte()
        private const val JPEG_1 = 0xD8.toByte()
        private const val TAG = "PixPhotoSync"
    }
}
