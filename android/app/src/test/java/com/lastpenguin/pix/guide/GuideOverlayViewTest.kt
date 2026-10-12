// AI-generated with Claude Code, 2026-10-06, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Exercises Android Canvas composition (Robolectric native graphics), including the frame's letterbox clipping. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideOverlayViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bitmaps = mutableListOf<Bitmap>()

    @After
    fun recycleBitmaps() = onMain {
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    @Test
    fun defaultStateDrawsTheOutlineCenteredAtSeventyPercentOfFrameHeight() = onMain {
        val guide = guide()
        val view = view(300, 400)
        view.render(guide, GuideState(guideId = guide.id))

        val rendered = snapshot(view)

        assertPixel(rendered, 150, 200, Color.argb(128, 0, 0, 255))
        // Height 280 and aspect 0.5 give bounds [80, 60, 220, 340].
        assertPixel(rendered, 90, 70, Color.argb(128, 0, 0, 255))
        assertPixel(rendered, 210, 330, Color.argb(128, 0, 0, 255))
        assertTransparent(rendered, 70, 200)
        assertTransparent(rendered, 230, 200)
        assertTransparent(rendered, 150, 50)
        assertTransparent(rendered, 150, 350)
    }

    @Test
    fun switchingStyleChangesTheBitmapWithoutMovingOrResizingIt() = onMain {
        val guide = guide()
        val view = view(300, 400)
        val state = GuideState(guideId = guide.id, cx = 0.35f, cy = 0.6f, height = 0.5f)
        view.render(guide, state)
        val outline = snapshot(view)

        view.render(guide, state.copy(style = GuideStyle.CUTOUT))
        val cutout = snapshot(view)

        assertPixel(outline, 105, 240, Color.argb(128, 0, 0, 255))
        assertPixel(cutout, 105, 240, Color.argb(128, 255, 0, 0))
        assertArrayEquals(pixels(outline).map(Color::alpha).toIntArray(), pixels(cutout).map(Color::alpha).toIntArray())
    }

    @Test
    fun opacityMultipliesSourceAlphaWithoutChangingEitherSourceBitmap() = onMain {
        val guide = guide(cutout = solid(Color.argb(128, 255, 0, 0)))
        val originalCutout = pixels(guide.cutout)
        val originalOutline = pixels(guide.outline)
        val view = view(300, 400)
        view.render(guide, GuideState(guideId = guide.id, opacity = 0.5f, style = GuideStyle.CUTOUT))

        val transparentBackground = snapshot(view)
        val blueBackground = snapshot(view, Color.BLUE)

        assertPixel(transparentBackground, 150, 200, Color.argb(64, 255, 0, 0), tolerance = 1)
        assertPixel(blueBackground, 150, 200, Color.rgb(64, 0, 191), tolerance = 1)
        assertArrayEquals(originalCutout, pixels(guide.cutout))
        assertArrayEquals(originalOutline, pixels(guide.outline))
        assertFalse(guide.cutout.isRecycled)
        assertFalse(guide.outline.isRecycled)
    }

    @Test
    fun aWideViewClipsAtTheFrameAndRestoresTheCanvasForLaterDrawing() = onMain {
        val guide = guide()
        val view = view(500, 400)
        view.render(guide, GuideState(guideId = guide.id, height = 2.1f, opacity = 0.9f, style = GuideStyle.CUTOUT))
        val rendered = bitmap(500, 400)
        val canvas = Canvas(rendered)
        val originalClip = canvas.clipBounds
        val originalSaveCount = canvas.saveCount

        view.draw(canvas)

        // The 300 x 400 camera frame occupies x = 100..400.
        assertTransparent(rendered, 50, 200)
        assertTransparent(rendered, 450, 200)
        assertPixel(rendered, 110, 200, Color.argb(230, 255, 0, 0))
        assertPixel(rendered, 390, 200, Color.argb(230, 255, 0, 0))
        assertEquals(originalClip, canvas.clipBounds)
        assertEquals(originalSaveCount, canvas.saveCount)
        canvas.drawColor(Color.MAGENTA)
        assertPixel(rendered, 50, 200, Color.MAGENTA)
        assertPixel(rendered, 450, 200, Color.MAGENTA)
    }

    @Test
    fun aTallViewKeepsTheTopAndBottomLetterboxesClear() = onMain {
        val guide = guide()
        val view = view(300, 600)
        view.render(guide, GuideState(guideId = guide.id, height = 2.1f, opacity = 0.9f, style = GuideStyle.CUTOUT))

        val rendered = snapshot(view)

        // The 300 x 400 camera frame occupies y = 100..500.
        assertTransparent(rendered, 150, 50)
        assertTransparent(rendered, 150, 550)
        assertPixel(rendered, 150, 110, Color.argb(230, 255, 0, 0))
        assertPixel(rendered, 150, 490, Color.argb(230, 255, 0, 0))
    }

    @Test
    fun clippingAnOffFrameGuideDoesNotSqueezeItsHiddenHalfIntoTheVisibleArea() = onMain {
        val cutout = solid(Color.RED)
        Canvas(cutout).drawRect(10f, 0f, 20f, 40f, Paint().apply { color = Color.GREEN })
        val guide = guide(cutout = cutout)
        val view = view(500, 400)
        view.render(
            guide,
            GuideState(guideId = guide.id, cx = 0f, height = 1f, opacity = 0.9f, style = GuideStyle.CUTOUT),
        )

        val rendered = snapshot(view)

        // Destination x = 0..200 crosses the frame's x = 100 boundary: only the green right half remains.
        assertTransparent(rendered, 50, 200)
        assertPixel(rendered, 120, 200, Color.argb(230, 0, 255, 0))
        assertPixel(rendered, 180, 200, Color.argb(230, 0, 255, 0))
        assertTransparent(rendered, 210, 200)
    }

    @Test
    fun replacingTheGuideHidesMismatchedIdentityUntilItsStateArrives() = onMain {
        val previous = guide(id = "previous")
        val replacement = guide(id = "replacement", outline = solid(Color.GREEN))
        val previousState = GuideState(guideId = previous.id)
        val replacementState = GuideState(guideId = replacement.id)
        val view = view(300, 400)
        view.render(previous, previousState)
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0, 0, 255))

        view.render(replacement, previousState)
        assertEmpty(snapshot(view))
        view.render(previous, replacementState)
        assertEmpty(snapshot(view))
        view.render(replacement, GuideState())
        assertEmpty(snapshot(view))

        view.render(replacement, replacementState)
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0, 255, 0))
    }

    @Test
    fun renderingNullClearsThePreviouslyRenderedGuide() = onMain {
        val guide = guide()
        val view = view(300, 400)
        val state = GuideState(guideId = guide.id)
        view.render(guide, state)
        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0, 0, 255))

        view.render(null, state)

        assertEmpty(snapshot(view))
    }

    @Test
    fun visibilityCanHideAndRestoreTheSameGuide() = onMain {
        val guide = guide()
        val view = view(300, 400)
        val state = GuideState(guideId = guide.id)
        view.render(guide, state)
        val visible = snapshot(view)

        view.render(guide, state.copy(visible = false))
        assertEmpty(snapshot(view))

        view.render(guide, state)
        assertArrayEquals(pixels(visible), pixels(snapshot(view)))
    }

    @Test
    fun renderingBeforeLayoutRetainsTheGuideForTheFirstNonzeroSize() = onMain {
        val guide = guide()
        val view = GuideOverlayView(instrumentation.targetContext)
        view.render(guide, GuideState(guideId = guide.id))
        val beforeLayout = bitmap(30, 40)

        view.draw(Canvas(beforeLayout))
        assertEmpty(beforeLayout)
        view.layout(0, 0, 300, 400)

        assertPixel(snapshot(view), 150, 200, Color.argb(128, 0, 0, 255))
    }

    @Test
    fun resizingRecomputesFramePlacementWithoutAnotherRenderCall() = onMain {
        val guide = guide()
        val view = view(300, 400)
        view.render(guide, GuideState(guideId = guide.id, cx = 0.25f, cy = 0.75f, height = 0.5f))
        assertPixel(snapshot(view), 75, 300, Color.argb(128, 0, 0, 255))

        view.layout(0, 0, 600, 400)
        val wide = snapshot(view)
        assertPixel(wide, 225, 300, Color.argb(128, 0, 0, 255))
        assertTransparent(wide, 75, 300)

        view.layout(0, 0, 300, 600)
        val tall = snapshot(view)
        assertPixel(tall, 75, 400, Color.argb(128, 0, 0, 255))
        assertTransparent(tall, 75, 200)
    }

    @Test
    fun readonlyRendersTheSameGuideAndDoesNotConsumeTouches() = onMain {
        val guide = guide()
        val state = GuideState(guideId = guide.id)
        val photographer = view(300, 400).apply { editable = true }
        val subject = view(300, 400).apply { editable = false }
        photographer.render(guide, state)
        subject.render(guide, state)

        assertArrayEquals(pixels(snapshot(photographer)), pixels(snapshot(subject)))
        assertFalse(subject.isClickable)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0L, 1L, action, 150f, 200f, 0)
            try {
                assertFalse(subject.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        // Report assertion failures to JUnit instead of crashing the app's main thread.
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }

    private fun view(width: Int, height: Int): GuideOverlayView =
        GuideOverlayView(instrumentation.targetContext).apply { layout(0, 0, width, height) }

    private fun guide(
        id: String = "guide",
        cutout: Bitmap = solid(Color.RED),
        outline: Bitmap = solid(Color.BLUE),
    ) = ReferenceGuide(id, cutout, outline, 0.5f, GuideSource.GALLERY)

    private fun solid(color: Int): Bitmap = bitmap(20, 40).apply { eraseColor(color) }

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also(bitmaps::add)

    private fun snapshot(view: GuideOverlayView, background: Int = Color.TRANSPARENT): Bitmap =
        bitmap(view.width, view.height).apply {
            eraseColor(background)
            view.draw(Canvas(this))
        }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun assertEmpty(bitmap: Bitmap) {
        assertTrue("The overlay should not draw any pixels", pixels(bitmap).all { Color.alpha(it) == 0 })
    }

    private fun assertTransparent(bitmap: Bitmap, x: Int, y: Int) {
        assertEquals("Alpha at ($x, $y)", 0, Color.alpha(bitmap.getPixel(x, y)))
    }

    private fun assertPixel(bitmap: Bitmap, x: Int, y: Int, expected: Int, tolerance: Int = 0) {
        val actual = bitmap.getPixel(x, y)
        fun assertComponent(name: String, expected: Int, actual: Int) {
            assertEquals("$name at ($x, $y)", expected.toDouble(), actual.toDouble(), tolerance.toDouble())
        }
        assertComponent("alpha", Color.alpha(expected), Color.alpha(actual))
        assertComponent("red", Color.red(expected), Color.red(actual))
        assertComponent("green", Color.green(expected), Color.green(actual))
        assertComponent("blue", Color.blue(expected), Color.blue(actual))
    }
}
