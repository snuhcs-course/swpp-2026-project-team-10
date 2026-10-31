// AI-generated with Claude Code, 2026-10-31, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lastpenguin.pix.camera.PhotoAlbum
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.PhotoRefusal
import com.lastpenguin.pix.session.protocol.SessionMessage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the Camera screen tells the subject's phone about its photos (FR-6.14), the photographer's side of [PhotoSyncer]. */
interface PhotoShare {
    /** The photo [uri], taken at [takenAt], is saved here; outside a session this does nothing. */
    fun taken(uri: Uri, takenAt: Long)

    /** For a phone with nothing to share, such as the ViewModel's tests. */
    object None : PhotoShare {
        override fun taken(uri: Uri, takenAt: Long) = Unit
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
 * `photo.end`, and answers `photo.saved`. [received] says how each ended, for the subject's toast.
 * Owner: Shared steps (Iter3-P17).
 */
class PhotoSyncer(
    private val session: SessionManager,
    private val reader: PhotoReader,
    private val album: PhotoAlbum,
    private val partDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PhotoShare {

    // ---- Photographer -----------------------------------------------------------------------------------------

    private data class Outgoing(val photoId: String, val uri: Uri, val takenAt: Long)

    /** How far the subject has the photo that is going; [saved] is its `photo.saved`. */
    private data class Progress(val photoId: String, val next: Int = 0, val saved: Boolean? = null)

    private val queue = Channel<Outgoing>(Channel.UNLIMITED)
    private val progress = MutableStateFlow<Progress?>(null)

    /** The photos taken in this session that are not yet confirmed or given up, the one going included. */
    private val unconfirmed = AtomicInteger(0)

    private val _sendFailed = MutableSharedFlow<Unit>(extraBufferCapacity = SIGNALS)

    /** A photo of this session that will not reach the subject's album; the session goes on. */
    val sendFailed: Flow<Unit> = _sendFailed.asSharedFlow()

    override fun taken(uri: Uri, takenAt: Long) {
        val state = session.state.value
        if (state !is SessionState.Connected && state !is SessionState.Reconnecting) return
        unconfirmed.incrementAndGet()
        queue.trySend(Outgoing(UUID.randomUUID().toString(), uri, takenAt))
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
                if (!sent) _sendFailed.tryEmit(Unit)
                progress.value = null
            }
        }
    }

    private fun onProgress(photoId: String, change: (Progress) -> Progress) {
        progress.update { current -> if (current?.photoId == photoId) change(current) else current }
    }

    /** The session ended: what is not confirmed stays here. Returns how many photos that is. */
    fun drop(): Int {
        while (queue.tryReceive().isSuccess) Unit
        progress.value = null
        return unconfirmed.getAndSet(0)
    }

    private suspend fun send(photo: Outgoing): Boolean {
        val content = reader.open(photo.uri) ?: return false
        return content.stream.use { stream ->
            if (content.bytes !in 1..MAX_PHOTO_BYTES) return@use false
            progress.value = Progress(photo.photoId)
            val chunks = ((content.bytes + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()
            val begin = SessionMessage.PhotoBegin(photo.photoId, FORMAT, content.bytes, chunks, photo.takenAt)
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

    private val _received = MutableSharedFlow<Boolean>(extraBufferCapacity = SIGNALS)

    /** True when a photo reached this phone's album, false when it could not be saved. */
    val received: Flow<Boolean> = _received.asSharedFlow()

    fun startAsReceiver(scope: CoroutineScope) {
        scope.launch(dispatcher) {
            clearParts()
            saved.clear()
            try {
                session.incoming.collect { message ->
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
            }
        }
    }

    private fun onBegin(begin: SessionMessage.PhotoBegin) {
        abandon()
        if (begin.photoId in saved) {
            session.send(SessionMessage.PhotoSaved(begin.photoId, ok = true))
            return
        }
        val refusal = when {
            begin.bytes !in 1..MAX_PHOTO_BYTES -> PhotoRefusal.TOO_LARGE
            begin.format != FORMAT -> PhotoRefusal.BROKEN
            begin.chunks.toLong() != (begin.bytes + CHUNK_BYTES - 1) / CHUNK_BYTES -> PhotoRefusal.BROKEN
            else -> null
        }
        if (refusal != null) return answer(begin.photoId, refusal)
        arriving = try {
            partDir.mkdirs()
            Arriving(begin, File(partDir, "${UUID.randomUUID()}$PART_SUFFIX"))
        } catch (error: IOException) {
            Log.w(TAG, "No file for photo ${begin.photoId}", error)
            answer(begin.photoId, PhotoRefusal.STORAGE)
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
        album.save(photo.file, end.photoId, photo.begin.takenAt).fold(
            onSuccess = {
                saved += end.photoId
                answer(end.photoId, null)
                Timings.mark("photo.applied", "saved ${end.photoId}")
            },
            onFailure = { error ->
                Log.w(TAG, "Photo ${end.photoId} could not be saved", error)
                answer(end.photoId, PhotoRefusal.STORAGE)
            },
        )
        abandon()
    }

    private fun fail(photo: Arriving, refusal: PhotoRefusal) {
        answer(photo.begin.photoId, refusal)
        abandon()
    }

    /** Tells the photographer, and this phone's person, how the photo ended: saved, or [refusal]. */
    private fun answer(photoId: String, refusal: PhotoRefusal?) {
        session.send(SessionMessage.PhotoSaved(photoId, ok = refusal == null, reason = refusal))
        _received.tryEmit(refusal == null)
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
        private const val SIGNALS = 16
        private const val JPEG_0 = 0xFF.toByte()
        private const val JPEG_1 = 0xD8.toByte()
        private const val TAG = "PixPhotoSync"
    }
}
