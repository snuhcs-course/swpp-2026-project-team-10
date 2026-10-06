package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideImageChunksTest {

    private val bytes = Random(7).nextBytes(20_000)
    private val image = EncodedGuideImage("g1", "webp", 540, 720, bytes)

    @Test
    fun `an image is split into chunks of at most 12 KiB of Base64`() {
        assertEquals(3, image.chunks.size)
        assertEquals(3, image.begin().chunks)
        assertEquals(20_000, image.begin().bytes)
        assertTrue(image.chunks.all { it.length <= GuideImageChunks.MAX_CHUNK_CHARS })
        assertEquals(12 * 1024, image.chunks[0].length)
        assertTrue(image.chunks[2].length < 12 * 1024)
        assertTrue(GuideImageChunks.split(ByteArray(0)).isEmpty())
    }

    @Test
    fun `the chunks reassemble into the same bytes when the CRC matches`() {
        val assembler = checkNotNull(GuideImageAssembler.start(image.begin()))
        for (i in image.chunks.indices) assertTrue(assembler.add(image.chunk(i)))

        assertArrayEquals(bytes, assembler.finish(image.end()))
    }

    @Test
    fun `a missing, repeated, or foreign chunk breaks the transfer`() {
        val assembler = checkNotNull(GuideImageAssembler.start(image.begin()))
        assertTrue(assembler.add(image.chunk(0)))
        assertFalse(assembler.add(image.chunk(0)))
        assertFalse(assembler.add(image.chunk(2)))
        assertFalse(assembler.add(SessionMessage.GuideImageChunk("other", 1, image.chunks[1])))
        assertFalse(assembler.add(SessionMessage.GuideImageChunk("g1", 1, "not base64!")))

        assertNull(assembler.finish(image.end()))
    }

    @Test
    fun `a wrong CRC or size is refused`() {
        val assembler = checkNotNull(GuideImageAssembler.start(image.begin()))
        for (i in image.chunks.indices) assembler.add(image.chunk(i))

        assertNull(assembler.finish(SessionMessage.GuideImageEnd("g1", image.crc32 + 1)))
        assertNull(assembler.finish(SessionMessage.GuideImageEnd("g2", image.crc32)))

        val shorter = GuideImageAssembler.start(image.begin().copy(bytes = 19_999, chunks = 3))!!
        for (i in image.chunks.indices) shorter.add(image.chunk(i))
        assertNull(shorter.finish(image.end()))
    }

    @Test
    fun `an implausible begin is refused`() {
        assertNull(GuideImageAssembler.start(image.begin().copy(bytes = 0, chunks = 0)))
        assertNull(GuideImageAssembler.start(image.begin().copy(bytes = GuideImageChunks.MAX_IMAGE_BYTES + 1)))
        assertNull(GuideImageAssembler.start(image.begin().copy(chunks = 2)))
        assertNull(GuideImageAssembler.start(image.begin().copy(height = 0)))
    }
}
