// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.OutlineExtractor
import com.lastpenguin.pix.guide.ReferenceGuide
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * What PhotoToGuideView draws (#295), into a bitmap at moments of a photo's 1.3 s, on a 400 × 560 px card: a blue photo
 * whose person is a disc, the outline's view holding the disc's white line. Partitions on the moment: before the line
 * (the photo alone), the line half drawn (part of the outline, in the outline view's own drawing), the line whole on the
 * photo, and the end (the photo alone in the corner, the outline's view shown in its place). Lines not traced yet:
 * the outline fades in instead of being drawn.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoToGuideViewTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Pix)
    private val extractor = OutlineExtractor()
    private val mask = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawCircle(50f, 50f, 30f, Paint().apply { color = Color.BLACK })
    }

    /** A 300 × 400 blue photo; the person's 150 × 150 box, as square as the mask, is in its middle. */
    private val guide = ReferenceGuide(
        id = "guide-1",
        mask = mask,
        outline = extractor.extract(mask, strokePx = 4f),
        aspect = 1f,
        source = GuideSource.GALLERY,
        anchor = NormalizedRect(0.25f, 0.3f, 0.75f, 0.675f),
        photo = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) },
    )
    private val outline = ImageView(context).apply { setImageBitmap(guide.outline) }
    private val view = PhotoToGuideView(context).apply {
        outline = this@PhotoToGuideViewTest.outline
        layout(0, 0, 400, 560)
    }

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
    }

    /** The view drawn at [ms] of the photo's 1.3 s. */
    private fun drawnAt(ms: Int): Bitmap {
        checkNotNull(view.playing).setCurrentFraction(ms / 1300f)
        return Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** How many pixels the white line covers, left of the corner's photo and its light edge: the blue photo has no red. */
    private fun linePixels(drawn: Bitmap): Int {
        val pixels = IntArray(CORNER_LEFT * 560).also { drawn.getPixels(it, 0, CORNER_LEFT, 0, 0, CORNER_LEFT, 560) }
        return pixels.count { Color.red(it) > 128 }
    }

    private fun play(withLines: Boolean = true) {
        ShadowChoreographer.setPaused(true)
        assertTrue(view.show(guide, play = true))
        if (withLines) view.drawLines(guide, extractor.lines(mask))
    }

    @Test
    fun theLine_isDrawnPartByPart_onThePhoto_thenWhole() {
        play()

        val before = drawnAt(300)
        val half = linePixels(drawnAt(550))
        val whole = linePixels(drawnAt(800))

        assertEquals(0, linePixels(before))
        // The photo fills the card: blue in its middle.
        assertEquals(Color.BLUE, before.getPixel(200, 280))
        assertTrue("whole $whole", whole > 300)
        assertTrue("half $half of $whole", half in (whole / 5)..(whole * 4 / 5))
        assertEquals(0f, outline.alpha, 0f)
    }

    @Test
    fun atTheEnd_theOutlinesViewTakesOver_andThePhotoIsInTheCorner() {
        play()

        val end = drawnAt(1300)

        assertEquals(1f, view.progress, 0f)
        assertEquals(1f, outline.alpha, 0f)
        assertEquals(0, linePixels(end))
        // 300 × 400 in a 72 px corner 14 px from the edges: 54 × 72 from (332, 474).
        assertEquals(Color.BLUE, end.getPixel(359, 510))
        assertEquals(0, Color.alpha(end.getPixel(200, 280)))
    }

    @Test
    fun withoutItsLinesYet_theOutlineFadesIn() {
        play(withLines = false)

        val half = drawnAt(550)
        val whole = linePixels(drawnAt(800))

        assertTrue("whole $whole", whole > 300)
        // All of the line at once, half faded: the white over the dimmed photo is pinkish, not white.
        val faded = IntArray(400 * 560).also { half.getPixels(it, 0, 400, 0, 0, 400, 560) }
            .filter { Color.red(it) > 40 }
        assertTrue(faded.size >= whole)
        assertFalse(faded.any { Color.red(it) == 255 && Color.green(it) == 255 })
    }

    private companion object {
        /** The corner's photo starts at x = 332; its edge is drawn a pixel around it. */
        const val CORNER_LEFT = 330
    }
}
