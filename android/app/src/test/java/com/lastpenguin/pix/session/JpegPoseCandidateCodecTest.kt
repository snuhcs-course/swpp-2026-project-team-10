// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Real JPEG encoding and decoding of a candidate's copy for the subject's Pick a pose (FR-6.15; Robolectric native
 * graphics). Partitions: a candidate wider or taller than 1024 px, one already small, and another format; the
 * receiver's byte limit (boundary): a copy that fits at quality 85, one that fits only at a lower quality, and one that
 * fits at none, which is left out; on receipt (boundary), a copy of 1024 px and of 1025 px on its long side, and bytes
 * that are not an image.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegPoseCandidateCodecTest {

    private val codec = JpegPoseCandidateCodec()

    private fun image(width: Int, height: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 140, 90)) }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        image(width, height).compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    @Test
    fun `a large candidate travels at most 1024 px on its long side and comes back the same size`() {
        for ((width, height) in listOf(1536 to 2048, 2048 to 1152)) {
            val copy = codec.encode("pose-1", image(width, height))
            assertNotNull(copy)
            copy!!
            assertEquals("pose-1", copy.poseId)
            assertEquals(1024, maxOf(copy.width, copy.height))
            assertEquals(width.toFloat() / height, copy.width.toFloat() / copy.height, 0.01f)
            assertTrue(copy.bytes.size <= PoseCandidateAssembler.MAX_BYTES)

            val decoded = codec.decode(copy.format, copy.bytes)
            assertEquals(copy.width, decoded?.width)
            assertEquals(copy.height, decoded?.height)
        }
    }

    /** A detailed image: noise, as in foliage or gravel, compresses least. */
    private fun noise(width: Int, height: Int): Bitmap {
        val random = java.util.Random(7)
        val pixels =
            IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun size(image: Bitmap, quality: Int): Int =
        ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG, quality, it) }.size()

    @Test
    fun `a copy over the receiver's limit is made at a lower quality, and one that never fits is left out`() {
        val detailed = noise(400, 300)
        val at85 = size(detailed, JpegPoseCandidateCodec.QUALITY)

        // At the limit, quality 85 is kept.
        assertEquals(at85, JpegPoseCandidateCodec(maxBytes = at85).encode("pose-1", detailed)?.bytes?.size)
        // One byte under it, a lower quality fits.
        val lower = JpegPoseCandidateCodec(maxBytes = at85 - 1).encode("pose-1", detailed)
        assertNotNull(lower)
        assertTrue(lower!!.bytes.size < at85)
        assertEquals(400, lower.width)
        // Under the lowest quality's size, nothing is sent.
        assertNull(JpegPoseCandidateCodec(maxBytes = size(detailed, 55) - 1).encode("pose-1", detailed))
    }

    @Test
    fun `a small candidate keeps its size, and another format is refused`() {
        val small = codec.encode("pose-2", image(600, 800))
        assertEquals(600, small?.width)
        assertEquals(800, small?.height)

        assertNull(codec.decode("webp", small!!.bytes))
    }

    @Test
    fun `a received copy of 1024 px is read, and one of 1025 px is refused before it is decoded`() {
        assertEquals(1024, codec.decode(JpegPoseCandidateCodec.FORMAT, jpeg(1024, 768))?.width)

        assertNull(codec.decode(JpegPoseCandidateCodec.FORMAT, jpeg(1025, 768)))
        assertNull(codec.decode(JpegPoseCandidateCodec.FORMAT, jpeg(768, 1025)))
        assertNull(codec.decode(JpegPoseCandidateCodec.FORMAT, byteArrayOf(1, 2, 3)))
    }
}
