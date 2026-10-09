// AI-generated with Claude Code, 2026-10-06, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.ReferenceGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real WebP encoding and decoding (Robolectric native graphics). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebpGuideImageCodecTest {

    private val codec = WebpGuideImageCodec()

    /** A tall transparent bitmap with an opaque figure in the middle, like a cutout. */
    private fun cutout(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawOval(
            width * 0.2f,
            height * 0.1f,
            width * 0.8f,
            height * 0.9f,
            Paint().apply { color = Color.rgb(200, 120, 80) },
        )
        return bitmap
    }

    @Test
    fun `the cutout travels at most 720 px tall and comes back with an outline`() {
        val guide = ReferenceGuide("g1", cutout(300, 900), cutout(300, 900), 300f / 900f, GuideSource.GALLERY)

        val encoded = codec.encode(guide)
        assertEquals("webp", encoded.format)
        assertEquals(720, encoded.height)
        assertEquals(240, encoded.width)
        assertTrue(encoded.bytes.isNotEmpty())
        assertEquals(GuideImageChunks.chunkCount(encoded.bytes.size), encoded.chunks.size)

        val received = checkNotNull(codec.decode("g1", encoded.format, encoded.bytes))
        assertEquals("g1", received.id)
        assertEquals(240, received.cutout.width)
        assertEquals(720, received.cutout.height)
        assertEquals(240, received.outline.width)
        assertEquals(720, received.outline.height)
        assertEquals(240f / 720f, received.aspect, 0.001f)
        // The background stays transparent and the figure opaque, so the outline can be found.
        assertEquals(0, Color.alpha(received.cutout.getPixel(2, 2)))
        assertEquals(255, Color.alpha(received.cutout.getPixel(120, 360)))
    }

    @Test
    fun `a small cutout is not scaled up`() {
        val guide = ReferenceGuide("g2", cutout(100, 200), cutout(100, 200), 0.5f, GuideSource.GALLERY)

        val encoded = codec.encode(guide)

        assertEquals(100, encoded.width)
        assertEquals(200, encoded.height)
    }

    @Test
    fun `bytes that are not an image, or another format, decode to nothing`() {
        assertNull(codec.decode("g3", "webp", byteArrayOf(1, 2, 3, 4)))
        assertNull(codec.decode("g3", "png", byteArrayOf(1, 2, 3, 4)))
    }
}
