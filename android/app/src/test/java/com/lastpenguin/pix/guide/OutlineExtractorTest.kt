// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real Canvas drawing (Robolectric native graphics). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutlineExtractorTest {

    private val extractor = OutlineExtractor()

    @Test
    fun `a disc gets a white line on its edge and nothing inside or outside`() {
        val mask = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawCircle(50f, 50f, 30f, Paint().apply { color = Color.BLACK })

        val outline = extractor.extract(mask, strokePx = 4f)

        assertEquals(100, outline.width)
        assertEquals(100, outline.height)
        // On the edge at every compass point.
        for ((x, y) in listOf(80 to 50, 20 to 50, 50 to 20, 50 to 80)) {
            assertEquals("($x, $y)", 255, Color.alpha(outline.getPixel(x, y)))
            assertEquals(Color.WHITE, outline.getPixel(x, y) or (0xFF shl 24))
        }
        assertEquals(0, Color.alpha(outline.getPixel(50, 50)))
        assertEquals(0, Color.alpha(outline.getPixel(5, 5)))
    }

    @Test
    fun `the line is anti-aliased instead of stepping from pixel to pixel`() {
        val mask = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawCircle(50f, 50f, 30f, Paint().apply { color = Color.BLACK })

        val outline = extractor.extract(mask, strokePx = 4f)

        val pixels = IntArray(100 * 100).also { outline.getPixels(it, 0, 100, 0, 0, 100, 100) }
        assertTrue(pixels.any { Color.alpha(it) in 1..254 })
    }

    @Test
    fun `a person cut off by the photo's bottom gets no line along it`() {
        // Alpha 255 in the lower part of the middle columns, as for legs leaving the photo.
        val alpha = IntArray(60 * 60) { i -> if (i % 60 in 20 until 40 && i / 60 >= 20) 255 else 0 }

        val outline = extractor.extract(alpha, 60, 60, strokePx = 3f)

        assertEquals(0, Color.alpha(outline.getPixel(30, 59)))
        assertEquals(0, Color.alpha(outline.getPixel(30, 58)))
        // The sides still run down to the bottom.
        assertTrue(Color.alpha(outline.getPixel(20, 58)) > 0)
        assertTrue(Color.alpha(outline.getPixel(39, 58)) > 0)
    }
}
