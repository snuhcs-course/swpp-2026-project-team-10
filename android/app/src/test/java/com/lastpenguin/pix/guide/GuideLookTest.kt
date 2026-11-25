// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.guide

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpenguin.pix.ui.settings.followLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The guide in the settings' look (FR-3.10, Design 2.6.1). Partitions: the overlay's color, white (no tint) and another
 * one, with the opacity kept; a restyled outline for the guide on screen, for another guide, and none; the outline at
 * the default thickness (the guide's own), at another (traced again from the mask, wider when thicker), and asked again
 * (kept). The inside guide-edit mode tints (#271): the mask's size, kept when asked again, and none from a recycled mask;
 * `followLook` hands it to the overlay for its guide.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideLookTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bitmaps = mutableListOf<Bitmap>()

    // What is drawn after each change, as with the phone's animations off: the guide's own animations are in
    // GuideOverlayMotionTest (Iter3-A13).
    @Before
    fun animationsOff() = setAnimatorScale(0f)

    @After
    fun recycleBitmaps() = onMain {
        setAnimatorScale(1f)
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    @Test
    fun lineColor_tintsTheWhiteLine_andKeepsTheOpacity() = onMain {
        val guide = guide(outline = solid(Color.WHITE))
        val view = view(300, 400)
        view.render(guide, GuideState(guideId = guide.id))

        assertPixel(snapshot(view), 150, 200, Color.argb(128, 255, 255, 255))

        view.lineColor = GuideColor.MINT.argb
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0x5E, 0xEA, 0xD4))

        view.lineColor = Color.WHITE
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 255, 255, 255))
    }

    @Test
    fun restyle_drawsTheTracedOutline_onlyForItsGuide() = onMain {
        val guide = guide(outline = solid(Color.BLUE))
        val view = view(300, 400)
        view.render(guide, GuideState(guideId = guide.id))

        view.restyle(guide, solid(Color.GREEN))
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0, 255, 0))

        // A late result for the previous guide never draws over the new one.
        val next = guide(id = "next", outline = solid(Color.RED))
        view.render(next, GuideState(guideId = next.id))
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 255, 0, 0))

        view.render(guide, GuideState(guideId = guide.id))
        view.restyle(null, null)
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0, 0, 255))
    }

    @Test
    fun outline_atTheDefaultThickness_isTheGuidesOwn() = runBlocking {
        val guide = guide(mask = person())
        assertSame(guide.outline, outlines().outline(guide, GuideThickness.NORMAL))
    }

    @Test
    fun outline_atAnotherThickness_isTracedFromTheMask_widerWhenThicker() = runBlocking {
        val guide = guide(mask = person())
        val outlines = outlines()

        val thin = outlines.outline(guide, GuideThickness.THIN)
        val thick = outlines.outline(guide, GuideThickness.THICK)

        assertNotSame(guide.outline, thin)
        assertEquals(guide.mask.width, thick.width)
        assertEquals(guide.mask.height, thick.height)
        assertTrue("thin ${drawn(thin)}, thick ${drawn(thick)}", drawn(thick) > drawn(thin) * 3 / 2)
        // Asked again, the kept outline comes back instead of a new trace.
        assertSame(thick, outlines.outline(guide, GuideThickness.THICK))
    }

    @Test
    fun inside_isMadeFromTheMask_andKept() = runBlocking {
        val guide = guide(mask = person())
        val outlines = outlines()

        val inside = checkNotNull(outlines.inside(guide))

        assertEquals(guide.mask.width, inside.width)
        assertEquals(guide.mask.height, inside.height)
        assertSame(inside, outlines.inside(guide))
    }

    @Test
    fun inside_ofARecycledMask_isNone() = runBlocking {
        val guide = guide(mask = person().apply { recycle() })
        assertNull(outlines().inside(guide))
    }

    @Test
    fun followLook_handsTheOverlayItsGuidesInside() = onMain {
        val guide = guide(mask = leftHalf(), outline = solid(Color.TRANSPARENT))
        val view = view(300, 400)
        view.filled = true
        view.render(guide, GuideState(guideId = guide.id, opacity = 1f))
        // Unconfined, as the outlines are made here: the inside is there once the collectors wait.
        val following = CoroutineScope(Dispatchers.Unconfined).launch {
            view.followLook(MutableStateFlow(GuideLook()), MutableStateFlow(guide), outlines())
        }
        try {
            // Bounds [80, 60, 220, 340]: the mask's left half covers x = 80..150, its right half x = 150..220.
            assertTrue(Color.alpha(snapshot(view).getPixel(110, 200)) > 0)
            assertEquals(0, Color.alpha(snapshot(view).getPixel(190, 200)))
        } finally {
            following.cancel()
        }
    }

    private fun outlines() = GuideOutlines(dispatcher = Dispatchers.Unconfined)

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }

    private fun view(width: Int, height: Int): GuideOverlayView =
        GuideOverlayView(instrumentation.targetContext).apply { layout(0, 0, width, height) }

    private fun guide(id: String = "guide", mask: Bitmap = solid(Color.RED), outline: Bitmap = solid(Color.BLUE)) =
        ReferenceGuide(id, mask, outline, 0.5f, GuideSource.GALLERY)

    /** A mask with an opaque block for the person, so its outline is a rectangle with a line of a few pixels. */
    private fun person(): Bitmap = bitmap(300, 600).apply {
        eraseColor(Color.TRANSPARENT)
        Canvas(this).drawRect(75f, 100f, 225f, 500f, Paint().apply { color = Color.BLACK })
    }

    private fun solid(color: Int): Bitmap = bitmap(20, 40).apply { eraseColor(color) }

    /** A mask whose left half is the person. */
    private fun leftHalf(): Bitmap = bitmap(20, 40).apply {
        val colors = IntArray(20 * 40) { if (it % 20 < 10) Color.BLACK else Color.TRANSPARENT }
        setPixels(colors, 0, 20, 0, 0, 20, 40)
    }

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also(bitmaps::add)

    private fun snapshot(view: GuideOverlayView): Bitmap = bitmap(view.width, view.height).apply {
        eraseColor(Color.TRANSPARENT)
        view.draw(Canvas(this))
    }

    private fun drawn(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 0 }
    }

    private fun assertPixel(bitmap: Bitmap, x: Int, y: Int, expected: Int) {
        val actual = bitmap.getPixel(x, y)
        assertEquals("alpha", Color.alpha(expected).toDouble(), Color.alpha(actual).toDouble(), 1.0)
        assertEquals("red", Color.red(expected).toDouble(), Color.red(actual).toDouble(), 1.0)
        assertEquals("green", Color.green(expected).toDouble(), Color.green(actual).toDouble(), 1.0)
        assertEquals("blue", Color.blue(expected).toDouble(), Color.blue(actual).toDouble(), 1.0)
    }
}
