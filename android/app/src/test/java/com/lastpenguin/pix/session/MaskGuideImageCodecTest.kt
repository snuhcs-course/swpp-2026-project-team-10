// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.MaskPixels
import com.lastpenguin.pix.guide.MaskPng
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.maskPixels
import com.lastpenguin.pix.guide.toMaskBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The guide image as it travels (Design 2.5.1, `mask-png`), with real bitmaps (Robolectric native graphics):
 * height above, at, and below 720 px; the mask's values both ways, the outline rebuilt, and no colors sent; bytes
 * that are not a mask, another format, and a mask taller than 720 px or wider than 1280 px refused, which no phone
 * makes (a wide, flat mask compresses to a few bytes but would take memory to trace).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaskGuideImageCodecTest {

    private val codec = MaskGuideImageCodec()

    /** An ellipse of 255 on 0, with a soft edge of other values, like a smoothed mask. */
    private fun mask(width: Int, height: Int): MaskPixels {
        val alpha = IntArray(width * height) { k ->
            val dx = (k % width - width / 2f) / (width * 0.3f)
            val dy = (k / width - height / 2f) / (height * 0.4f)
            ((1.5f - (dx * dx + dy * dy)) * 255f).toInt().coerceIn(0, 255)
        }
        return MaskPixels(width, height, alpha)
    }

    private fun guide(id: String, mask: MaskPixels): ReferenceGuide {
        val bitmap = mask.toMaskBitmap()
        return ReferenceGuide(id, bitmap, bitmap, mask.width.toFloat() / mask.height, GuideSource.GALLERY)
    }

    @Test
    fun `the mask travels at most 720 px tall and comes back with an outline`() {
        val encoded = codec.encode(guide("g1", mask(300, 900)))
        assertEquals("mask-png", encoded.format)
        assertEquals(720, encoded.height)
        assertEquals(240, encoded.width)
        assertEquals(GuideImageChunks.chunkCount(encoded.bytes.size), encoded.chunks.size)
        assertEquals(240f / 720f, checkNotNull(encoded.begin().aspect), 0.001f)

        val received = checkNotNull(codec.decode("g1", encoded.format, encoded.bytes))
        assertEquals("g1", received.id)
        assertEquals(Bitmap.Config.ALPHA_8, received.mask.config)
        assertEquals(240 to 720, received.mask.width to received.mask.height)
        assertEquals(240 to 720, received.outline.width to received.outline.height)
        assertEquals(240f / 720f, received.aspect, 0.001f)
        assertEquals(0, Color.alpha(received.mask.getPixel(2, 2)))
        assertEquals(255, Color.alpha(received.mask.getPixel(120, 360)))
        assertTrue("the outline has a line", received.outline.maskPixels().alpha.any { it > 0 })
    }

    @Test
    fun `a mask that fits arrives with exactly its values`() {
        val sent = mask(120, 720)

        val encoded = codec.encode(guide("g2", sent))
        val received = checkNotNull(codec.decode("g2", encoded.format, encoded.bytes))

        assertEquals(120 to 720, encoded.width to encoded.height)
        assertEquals(sent, received.mask.maskPixels())
    }

    @Test
    fun `a small mask is not scaled up and a mask is a few KB`() {
        val encoded = codec.encode(guide("g3", mask(100, 200)))

        assertEquals(100 to 200, encoded.width to encoded.height)
        assertTrue("${encoded.bytes.size} B", encoded.bytes.size < 8 * 1024)
    }

    @Test
    fun `only the mask is sent, never a color`() {
        // A bitmap in color, as an older cutout was: only its alpha goes into the PNG.
        val colored = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        colored.eraseColor(Color.argb(200, 220, 30, 30))
        val guide = ReferenceGuide("g4", colored, colored, 0.5f, GuideSource.GALLERY)

        val encoded = codec.encode(guide)

        val sent = checkNotNull(MaskPng.decode(encoded.bytes))
        assertTrue(sent.alpha.all { it == 200 })
    }

    @Test
    fun `bytes that are not a mask, another format, or a taller mask decode to nothing`() {
        assertNull(codec.decode("g5", "mask-png", byteArrayOf(1, 2, 3, 4)))
        assertNull(codec.decode("g5", "webp", codec.encode(guide("g5", mask(10, 20))).bytes))
        assertNull(codec.decode("g5", "mask-png", MaskPng.encode(mask(10, 721))))
        val flat = { width: Int -> MaskPng.encode(MaskPixels(width, 4, IntArray(width * 4) { 255 })) }
        assertNull(codec.decode("g5", "mask-png", flat(1281)))
        assertEquals(1280, codec.decode("g5", "mask-png", flat(1280))?.mask?.width)
    }
}
