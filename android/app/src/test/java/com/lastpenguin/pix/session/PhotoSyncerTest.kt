// AI-generated with Claude Code, 2026-10-31, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.net.Uri
import com.lastpenguin.pix.camera.PhotoAlbum
import com.lastpenguin.pix.session.protocol.PhotoRefusal
import com.lastpenguin.pix.session.protocol.SessionMessage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.CRC32
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/*
 * The photos of a session on the subject's album (Design 2.5.1, 2.6.5, 2.6.7; Iter3-P17), with the fakes of
 * ModuleFakes.kt, a fake reader and album, and Robolectric only for `Uri`.
 * Photographer, one photo (state-based): begin, then the chunks in order, then the end; the next photo only after
 * `photo.saved`; nothing outside a session.
 * Photographer, the limits (boundary): 0 B, one chunk, one byte more, 40 MiB, one byte more; 32 chunks past the last
 * `photo.ack` and one more; a chunk that waits in `sendBulk` goes once.
 * Photographer, failures (fault injection): a photo that cannot be opened or read, one the subject refuses, one with
 * no answer for 30 s (just before and just after), and `sendBulk` saying the session is over; each is one `sendFailed`,
 * and the next photo goes on.
 * Photographer, the session's end: every unconfirmed photo counted once, the one going included; a confirmed one not.
 * Subject, one photo (state-based): saved once with its `takenAt`, then its file gone; a `photo.ack` every 16 chunks.
 * Subject, what is refused (equivalence partitioning and boundary): a header of 0 B or over 40 MiB (at the limit read),
 * in another format, or with the wrong chunk count; a skipped index, data that is not Base64, a chunk or a photo larger
 * than promised, a short photo, a wrong CRC, and bytes that are not a JPEG.
 * Subject, failures (fault injection): an album or a folder that fails is STORAGE, and says so here.
 * Subject, the file: kept while the link reconnects, dropped at another `photo.begin`, a stale chunk ignored, removed
 * when the session ends, and an old one removed when a session starts; a photo already saved answered, not saved again.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoSyncerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val session = FakeSessionManager()

    private val photos = HashMap<Uri, PhotoContent>()
    private val reader = PhotoReader { photos[it] }

    private data class SavedPhoto(val photoId: String, val takenAt: Long, val bytes: ByteArray)

    private val saved = mutableListOf<SavedPhoto>()
    private var albumFails = false
    private val album = PhotoAlbum { file, photoId, takenAt ->
        if (albumFails) {
            Result.failure(IOException("The disk is full"))
        } else {
            saved += SavedPhoto(photoId, takenAt, file.readBytes())
            Result.success(uri("saved/$photoId"))
        }
    }

    private val parts: File get() = File(folder.root, "parts")

    private fun TestScope.syncer(partDir: File = parts) =
        PhotoSyncer(session, reader, album, partDir, StandardTestDispatcher(testScheduler))

    private fun connectedAs(role: Role) {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje", null), role))
    }

    private fun uri(name: String): Uri = Uri.parse("content://media/$name")

    /** [size] bytes that start as a JPEG. */
    private fun jpeg(size: Int) = ByteArray(size) { i ->
        when (i) {
            0 -> 0xFF.toByte()
            1 -> 0xD8.toByte()
            else -> i.toByte()
        }
    }

    private fun photo(name: String, size: Int): Uri {
        val bytes = jpeg(size)
        return uri(name).also { photos[it] = PhotoContent(size.toLong(), ByteArrayInputStream(bytes)) }
    }

    private fun chunksOf(size: Int) = (size + PhotoSyncer.CHUNK_BYTES - 1) / PhotoSyncer.CHUNK_BYTES

    private fun <T> sentOf(type: Class<T>): List<T> = session.sent.filterIsInstance(type)

    private val begins get() = sentOf(SessionMessage.PhotoBegin::class.java)
    private val chunks get() = sentOf(SessionMessage.PhotoChunk::class.java)
    private val ends get() = sentOf(SessionMessage.PhotoEnd::class.java)
    private val answers get() = sentOf(SessionMessage.PhotoSaved::class.java)
    private val acks get() = sentOf(SessionMessage.PhotoAck::class.java)

    /** A photographer's syncer running for the session, and how many photos it gave up. */
    private fun TestScope.sender(): Pair<PhotoSyncer, () -> Int> {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        var failed = 0
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) { syncer.sendFailed.collect { failed++ } }
        runCurrent()
        return syncer to { failed }
    }

    private fun deliverSaved(photoId: String, ok: Boolean = true, reason: PhotoRefusal? = null) {
        session.deliver(SessionMessage.PhotoSaved(photoId, ok, reason))
    }

    @Test
    fun `a photo goes as a begin, its chunks in order, and an end`() = runTest {
        val (syncer, failed) = sender()
        val size = 2 * PhotoSyncer.CHUNK_BYTES + 1

        syncer.taken(photo("1", size), takenAt = 1_000)
        runCurrent()

        val begin = begins.single()
        assertEquals(size.toLong(), begin.bytes)
        assertEquals(3, begin.chunks)
        assertEquals(1_000, begin.takenAt)
        assertEquals("jpeg", begin.format)
        assertEquals(listOf(0, 1, 2), chunks.map { it.index })
        val whole = chunks.flatMap { Base64.getDecoder().decode(it.data).toList() }.toByteArray()
        assertArrayEquals(jpeg(size), whole)
        assertEquals(CRC32().apply { update(whole) }.value, ends.single().crc32)

        deliverSaved(begin.photoId)
        runCurrent()
        assertEquals(0, failed())
        assertEquals(0, syncer.drop())
    }

    @Test
    fun `the next photo goes only after the last one is saved, in the order they were taken`() = runTest {
        val (syncer, _) = sender()
        syncer.taken(photo("1", 10), takenAt = 1)
        syncer.taken(photo("2", 10), takenAt = 2)
        runCurrent()
        assertEquals(listOf(1L), begins.map { it.takenAt })

        deliverSaved(begins[0].photoId)
        runCurrent()
        assertEquals(listOf(1L, 2L), begins.map { it.takenAt })
    }

    @Test
    fun `a photo taken outside a session is not sent or counted`() = runTest {
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        session.setState(SessionState.Waiting("123456"))

        syncer.taken(photo("1", 10), takenAt = 1)
        runCurrent()

        assertTrue(begins.isEmpty())
        assertEquals(0, syncer.drop())
    }

    @Test
    fun `a photo taken while the link reconnects waits its turn`() = runTest {
        val (syncer, _) = sender()
        session.setState(SessionState.Reconnecting("s_1", since = 0))
        session.bulkGate = CompletableDeferred()

        syncer.taken(photo("1", 10), takenAt = 1)
        runCurrent()
        assertTrue(begins.isEmpty())

        session.bulkGate?.complete(true)
        runCurrent()
        assertEquals(1, begins.size)
    }

    @Test
    fun `one chunk fits exactly and one byte more is two`() = runTest {
        val (syncer, _) = sender()
        syncer.taken(photo("1", PhotoSyncer.CHUNK_BYTES), takenAt = 1)
        runCurrent()
        assertEquals(1, begins[0].chunks)

        deliverSaved(begins[0].photoId)
        syncer.taken(photo("2", PhotoSyncer.CHUNK_BYTES + 1), takenAt = 2)
        runCurrent()
        assertEquals(2, begins[1].chunks)
    }

    @Test
    fun `a photo of no bytes or over the limit is given up, and 40 MiB is sent`() = runTest {
        val (syncer, failed) = sender()
        photos[uri("empty")] = PhotoContent(0, ByteArrayInputStream(ByteArray(0)))
        photos[uri("over")] = PhotoContent(PhotoSyncer.MAX_PHOTO_BYTES + 1, ByteArrayInputStream(ByteArray(0)))
        photos[uri("limit")] = PhotoContent(PhotoSyncer.MAX_PHOTO_BYTES, endlessJpeg())

        syncer.taken(uri("empty"), takenAt = 1)
        syncer.taken(uri("over"), takenAt = 2)
        syncer.taken(uri("limit"), takenAt = 3)
        runCurrent()

        assertEquals(2, failed())
        assertEquals(listOf(3L), begins.map { it.takenAt })
    }

    private fun endlessJpeg(): InputStream = object : InputStream() {
        override fun read(): Int = 0xD8
    }

    @Test
    fun `at most 32 chunks go past the last ack`() = runTest {
        val (syncer, _) = sender()
        val extra = 10
        syncer.taken(photo("1", (PhotoSyncer.WINDOW_CHUNKS + extra) * PhotoSyncer.CHUNK_BYTES), takenAt = 1)
        runCurrent()
        assertEquals(PhotoSyncer.WINDOW_CHUNKS, chunks.size)

        session.deliver(SessionMessage.PhotoAck(begins[0].photoId, next = 1))
        runCurrent()
        assertEquals(PhotoSyncer.WINDOW_CHUNKS + 1, chunks.size)

        session.deliver(SessionMessage.PhotoAck(begins[0].photoId, next = extra))
        runCurrent()
        assertEquals(PhotoSyncer.WINDOW_CHUNKS + extra, chunks.size)
        assertEquals(1, ends.size)
    }

    @Test
    fun `an ack of another photo moves nothing`() = runTest {
        val (syncer, _) = sender()
        syncer.taken(photo("1", (PhotoSyncer.WINDOW_CHUNKS + 1) * PhotoSyncer.CHUNK_BYTES), takenAt = 1)
        runCurrent()

        session.deliver(SessionMessage.PhotoAck("another", next = 16))
        runCurrent()

        assertEquals(PhotoSyncer.WINDOW_CHUNKS, chunks.size)
    }

    @Test
    fun `a chunk that waits in sendBulk goes once, and the rest follow`() = runTest {
        val (syncer, _) = sender()
        syncer.taken(photo("1", 3 * PhotoSyncer.CHUNK_BYTES), takenAt = 1)
        val gate = CompletableDeferred<Boolean>()
        session.bulkGate = gate
        runCurrent()
        assertTrue(begins.isEmpty())

        session.bulkGate = null
        gate.complete(true)
        runCurrent()

        assertEquals(1, begins.size)
        assertEquals(listOf(0, 1, 2), chunks.map { it.index })
    }

    @Test
    fun `a photo that cannot be opened is given up and the next one goes`() = runTest {
        val (syncer, failed) = sender()
        syncer.taken(uri("gone"), takenAt = 1)
        syncer.taken(photo("2", 10), takenAt = 2)
        runCurrent()

        assertEquals(1, failed())
        assertEquals(listOf(2L), begins.map { it.takenAt })
    }

    @Test
    fun `a photo that fails while it is read is given up`() = runTest {
        val (syncer, failed) = sender()
        photos[uri("1")] = PhotoContent(
            10,
            object : InputStream() {
                override fun read(): Int = throw IOException("The file went away")
            },
        )

        syncer.taken(uri("1"), takenAt = 1)
        runCurrent()

        assertEquals(1, failed())
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `a photo shorter than it said is given up`() = runTest {
        val (syncer, failed) = sender()
        photos[uri("1")] = PhotoContent(10, ByteArrayInputStream(ByteArray(0)))

        syncer.taken(uri("1"), takenAt = 1)
        runCurrent()

        assertEquals(1, failed())
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `a photo the subject refuses stops at once and the next one goes`() = runTest {
        val (syncer, failed) = sender()
        syncer.taken(photo("1", (PhotoSyncer.WINDOW_CHUNKS + 5) * PhotoSyncer.CHUNK_BYTES), takenAt = 1)
        syncer.taken(photo("2", 10), takenAt = 2)
        runCurrent()

        deliverSaved(begins[0].photoId, ok = false, reason = PhotoRefusal.STORAGE)
        runCurrent()

        assertEquals(PhotoSyncer.WINDOW_CHUNKS + 1, chunks.size)
        assertEquals(1, failed())
        assertEquals(listOf(1L, 2L), begins.map { it.takenAt })
    }

    @Test
    fun `no answer gives the photo up after 30 s and not before`() = runTest {
        val (syncer, failed) = sender()
        syncer.taken(photo("1", 10), takenAt = 1)
        runCurrent()

        advanceTimeBy(PhotoSyncer.STALL_MS - 1)
        runCurrent()
        assertEquals(0, failed())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, failed())
    }

    @Test
    fun `no ack gives the photo up after 30 s`() = runTest {
        val (syncer, failed) = sender()
        syncer.taken(photo("1", (PhotoSyncer.WINDOW_CHUNKS + 1) * PhotoSyncer.CHUNK_BYTES), takenAt = 1)

        advanceTimeBy(PhotoSyncer.STALL_MS + 1)
        runCurrent()

        assertEquals(1, failed())
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `a session over while a chunk waits gives the photo up`() = runTest {
        val (syncer, failed) = sender()
        session.bulkGate = CompletableDeferred(false)

        syncer.taken(photo("1", 10), takenAt = 1)
        runCurrent()

        assertEquals(1, failed())
    }

    @Test
    fun `the end of a session counts each unconfirmed photo once, the one going included`() = runTest {
        val (syncer, _) = sender()
        syncer.taken(photo("1", 10), takenAt = 1)
        runCurrent()
        deliverSaved(begins[0].photoId)
        runCurrent()
        syncer.taken(photo("2", 10), takenAt = 2)
        syncer.taken(photo("3", 10), takenAt = 3)
        syncer.taken(photo("4", 10), takenAt = 4)
        runCurrent()

        assertEquals(3, syncer.drop())
        assertEquals(0, syncer.drop())
    }

    // ---- Subject ----------------------------------------------------------------------------------------------

    /** A subject's syncer running until the returned job is cancelled, as the session's end does. */
    private fun TestScope.receiver(partDir: File = parts): Triple<PhotoSyncer, Job, List<Boolean>> {
        connectedAs(Role.SUBJECT)
        val syncer = syncer(partDir)
        val job = Job()
        syncer.startAsReceiver(CoroutineScope(backgroundScope.coroutineContext + job))
        val received = mutableListOf<Boolean>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) { syncer.received.collect { received += it } }
        runCurrent()
        return Triple(syncer, job, received)
    }

    private fun TestScope.deliverBegin(
        id: String,
        bytes: ByteArray,
        takenAt: Long = 1,
        chunks: Int = chunksOf(
            bytes.size,
        ),
    ) {
        session.deliver(SessionMessage.PhotoBegin(id, "jpeg", bytes.size.toLong(), chunks, takenAt))
        runCurrent()
    }

    private fun TestScope.deliverChunks(
        id: String,
        bytes: ByteArray,
        from: Int = 0,
        until: Int = chunksOf(
            bytes.size,
        ),
    ) {
        val encoder = Base64.getEncoder()
        for (index in from until until) {
            val start = index * PhotoSyncer.CHUNK_BYTES
            val part = bytes.copyOfRange(start, minOf(start + PhotoSyncer.CHUNK_BYTES, bytes.size))
            session.deliver(SessionMessage.PhotoChunk(id, index, encoder.encodeToString(part)))
            runCurrent()
        }
    }

    private fun TestScope.deliverEnd(id: String, bytes: ByteArray) {
        session.deliver(SessionMessage.PhotoEnd(id, CRC32().apply { update(bytes) }.value))
        runCurrent()
    }

    private fun TestScope.deliverWhole(id: String, bytes: ByteArray, takenAt: Long = 1) {
        deliverBegin(id, bytes, takenAt)
        deliverChunks(id, bytes)
        deliverEnd(id, bytes)
    }

    private fun assertRefused(reason: PhotoRefusal) {
        val answer = answers.single()
        assertFalse(answer.ok)
        assertEquals(reason, answer.reason)
        assertTrue(saved.isEmpty())
    }

    private fun partFiles() = parts.listFiles()?.toList().orEmpty()

    @Test
    fun `a whole photo is saved with its time, answered, and its file removed`() = runTest {
        val (_, _, received) = receiver()
        val bytes = jpeg(2 * PhotoSyncer.CHUNK_BYTES + 1)

        deliverWhole("p1", bytes, takenAt = 1_000)

        val photo = saved.single()
        assertEquals("p1", photo.photoId)
        assertEquals(1_000, photo.takenAt)
        assertArrayEquals(bytes, photo.bytes)
        assertEquals(SessionMessage.PhotoSaved("p1", ok = true), answers.single())
        assertEquals(listOf(true), received)
        assertTrue(partFiles().isEmpty())
    }

    @Test
    fun `an ack goes every 16 chunks`() = runTest {
        receiver()
        val bytes = jpeg(33 * PhotoSyncer.CHUNK_BYTES)

        deliverBegin("p1", bytes)
        deliverChunks("p1", bytes)

        assertEquals(listOf(16, 32), acks.map { it.next })
    }

    @Test
    fun `a header of no bytes or over 40 MiB is refused and 40 MiB is read`() = runTest {
        receiver()
        val limit = PhotoSyncer.MAX_PHOTO_BYTES

        session.deliver(SessionMessage.PhotoBegin("empty", "jpeg", 0, 0, 1))
        session.deliver(SessionMessage.PhotoBegin("over", "jpeg", limit + 1, chunksFor(limit + 1), 1))
        session.deliver(SessionMessage.PhotoBegin("limit", "jpeg", limit, chunksFor(limit), 1))
        runCurrent()

        assertEquals(listOf("empty", "over"), answers.map { it.photoId })
        assertTrue(answers.all { !it.ok && it.reason == PhotoRefusal.TOO_LARGE })
        assertEquals(1, partFiles().size)
    }

    private fun chunksFor(bytes: Long) = ((bytes + PhotoSyncer.CHUNK_BYTES - 1) / PhotoSyncer.CHUNK_BYTES).toInt()

    @Test
    fun `another format is refused`() = runTest {
        receiver()
        session.deliver(SessionMessage.PhotoBegin("p1", "webp", 10, 1, 1))
        runCurrent()
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `a wrong chunk count is refused`() = runTest {
        receiver()
        deliverBegin("p1", jpeg(10), chunks = 2)
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `a skipped index breaks the photo and what follows is ignored`() = runTest {
        receiver()
        val bytes = jpeg(3 * PhotoSyncer.CHUNK_BYTES)
        deliverBegin("p1", bytes)

        deliverChunks("p1", bytes, from = 1)
        deliverEnd("p1", bytes)

        assertRefused(PhotoRefusal.BROKEN)
        assertTrue(partFiles().isEmpty())
    }

    @Test
    fun `data that is not Base64 breaks the photo`() = runTest {
        receiver()
        deliverBegin("p1", jpeg(10))
        session.deliver(SessionMessage.PhotoChunk("p1", 0, "not base64!"))
        runCurrent()
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `a chunk larger than a chunk breaks the photo`() = runTest {
        receiver()
        val bytes = jpeg(3 * PhotoSyncer.CHUNK_BYTES)
        deliverBegin("p1", bytes)
        val data = Base64.getEncoder().encodeToString(jpeg(PhotoSyncer.CHUNK_BYTES + 1))
        session.deliver(SessionMessage.PhotoChunk("p1", 0, data))
        runCurrent()
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `more bytes than promised break the photo`() = runTest {
        receiver()
        deliverBegin("p1", jpeg(10))
        session.deliver(SessionMessage.PhotoChunk("p1", 0, Base64.getEncoder().encodeToString(jpeg(11))))
        runCurrent()
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `a photo that ends short is refused`() = runTest {
        receiver()
        val bytes = jpeg(2 * PhotoSyncer.CHUNK_BYTES)
        deliverBegin("p1", bytes)
        deliverChunks("p1", bytes, until = 1)
        deliverEnd("p1", bytes)
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `a wrong CRC is refused`() = runTest {
        receiver()
        val bytes = jpeg(10)
        deliverBegin("p1", bytes)
        deliverChunks("p1", bytes)
        session.deliver(SessionMessage.PhotoEnd("p1", crc32 = 1))
        runCurrent()
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `bytes that are not a JPEG are refused`() = runTest {
        receiver()
        deliverWhole("p1", ByteArray(10))
        assertRefused(PhotoRefusal.BROKEN)
    }

    @Test
    fun `a failing album is STORAGE and says so here`() = runTest {
        val (_, _, received) = receiver()
        albumFails = true

        deliverWhole("p1", jpeg(10))

        assertRefused(PhotoRefusal.STORAGE)
        assertEquals(listOf(false), received)
        assertTrue(partFiles().isEmpty())
    }

    @Test
    fun `a folder that cannot be written is STORAGE`() = runTest {
        val blocked = folder.newFile("not-a-folder")
        receiver(File(blocked, "parts"))

        deliverBegin("p1", jpeg(10))

        assertRefused(PhotoRefusal.STORAGE)
    }

    @Test
    fun `a photo already saved is answered and not saved or told again`() = runTest {
        val (_, _, received) = receiver()
        val bytes = jpeg(10)
        deliverWhole("p1", bytes)

        deliverBegin("p1", bytes)

        assertEquals(1, saved.size)
        assertEquals(listOf(true, true), answers.map { it.ok })
        assertEquals(listOf(true), received)
    }

    @Test
    fun `the file stays while the link reconnects and the photo finishes`() = runTest {
        receiver()
        val bytes = jpeg(3 * PhotoSyncer.CHUNK_BYTES)
        deliverBegin("p1", bytes)
        deliverChunks("p1", bytes, until = 1)

        session.setState(SessionState.Reconnecting("s_1", since = 0))
        runCurrent()
        assertEquals(1, partFiles().size)
        connectedAs(Role.SUBJECT)

        deliverChunks("p1", bytes, from = 1)
        deliverEnd("p1", bytes)
        assertArrayEquals(bytes, saved.single().bytes)
    }

    @Test
    fun `another begin drops the unfinished file and a stale chunk is ignored`() = runTest {
        receiver()
        val first = jpeg(2 * PhotoSyncer.CHUNK_BYTES)
        val second = jpeg(10)
        deliverBegin("p1", first)
        deliverChunks("p1", first, until = 1)

        deliverBegin("p2", second)
        assertEquals(1, partFiles().size)
        deliverChunks("p1", first, from = 1)
        deliverChunks("p2", second)
        deliverEnd("p2", second)

        assertEquals(listOf("p2"), saved.map { it.photoId })
        assertEquals(listOf("p2"), answers.map { it.photoId })
    }

    @Test
    fun `the end of the session removes the unfinished file`() = runTest {
        val (_, job, _) = receiver()
        val bytes = jpeg(2 * PhotoSyncer.CHUNK_BYTES)
        deliverBegin("p1", bytes)
        assertEquals(1, partFiles().size)

        job.cancel()
        runCurrent()

        assertTrue(partFiles().isEmpty())
    }

    @Test
    fun `a session's start removes a file an app that died left behind`() = runTest {
        parts.mkdirs()
        File(parts, "old.part").writeBytes(ByteArray(1))

        receiver()

        assertTrue(partFiles().isEmpty())
    }
}
