// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * The guide's own animations on GuideOverlayView (Iter3-A13, Design 2.6.2), drawn with Robolectric's native graphics
 * and the display's frames paused, so each is seen at its start; a test looks inside the coming in by setting its
 * fraction, since paused frames catch up at once (Testing 2.1.8). The outline is solid blue at alpha 128 and the
 * opacity 0.5 halves it, so the guide drawn whole is (0, 0, 255) at 64 in the middle of a 300 x 400 view.
 * What changes (equivalence partitioning): the first guide the view renders (at once), a new guide (fades in as it
 * grows from 95 %), a replacement (the old fading out meanwhile), Hide (fades out), its return (fades in without
 * growing), and Remove (fades out). What stays immediate: a move and the opacity during the coming in, and a drag
 * that starts during it. With the phone's animations off, each change is drawn at its end (GuideOverlayViewTest).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideOverlayMotionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bitmaps = mutableListOf<Bitmap>()

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    private fun solid(color: Int): Bitmap = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888).also {
        it.eraseColor(color)
        bitmaps += it
    }

    private fun guide(id: String, color: Int = Color.argb(128, 0, 0, 255)) =
        ReferenceGuide(id, solid(Color.RED), solid(color), 0.5f, GuideSource.GALLERY)

    /** A 300 x 400 view that has rendered [first] already, as a screen opens with its guide. */
    private fun view(first: ReferenceGuide? = null): GuideOverlayView =
        GuideOverlayView(context).apply {
            layout(0, 0, 300, 400)
            render(first, GuideState(guideId = first?.id))
            ShadowChoreographer.setPaused(true)
        }

    private fun GuideOverlayView.snapshot(): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            bitmaps += it
            draw(Canvas(it))
        }

    /** The drawn alpha at ([x], [y]). */
    private fun Bitmap.alphaAt(x: Int = 150, y: Int = 200) = Color.alpha(getPixel(x, y))

    private fun play() {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(MOTION_END_MS))
    }

    @Test
    fun theFirstGuideTheViewRenders_isDrawnAtOnce() {
        val first = guide("first")
        val view = GuideOverlayView(context).apply { layout(0, 0, 300, 400) }
        ShadowChoreographer.setPaused(true)

        view.render(first, GuideState(guideId = first.id))

        assertEquals(1f, view.appearing, 0f)
        assertEquals(WHOLE, view.snapshot().alphaAt())
    }

    @Test
    fun aNewGuide_fadesInAsItGrowsFrom95Percent() {
        val view = view()
        val next = guide("next")

        view.render(next, GuideState(guideId = next.id))

        assertEquals(0, view.snapshot().alphaAt())
        // A little way in: faint, and smaller than its place. Its top edge, at 60 when whole, is still lower.
        view.appearance!!.setCurrentFraction(0.3f)
        val coming = view.appearing
        val drawn = view.snapshot()
        assertEquals(WHOLE * coming, drawn.alphaAt().toFloat(), 2f)
        assertEquals(0, drawn.alphaAt(150, 62))
        play()
        assertEquals(1f, view.appearing, 0f)
        assertEquals(WHOLE, view.snapshot().alphaAt(150, 62))
    }

    @Test
    fun aReplacement_comesIn_whileTheOldGuideFadesOut() {
        val old = guide("old")
        val view = view(old)
        val next = guide("next", Color.argb(128, 0, 255, 0))

        // The repository gives the new guide before its state: nothing of it is drawn until the state comes.
        view.render(next, GuideState(guideId = old.id))
        view.render(next, GuideState(guideId = next.id))

        assertTrue(view.fadingOut)
        val start = view.snapshot().getPixel(150, 200)
        assertEquals(WHOLE, Color.alpha(start))
        assertEquals(255, Color.blue(start))
        play()
        assertFalse(view.fadingOut)
        val end = view.snapshot().getPixel(150, 200)
        assertEquals(WHOLE, Color.alpha(end))
        assertEquals(255, Color.green(end))
    }

    @Test
    fun hide_fadesOut_andItsReturn_fadesInWithoutGrowing() {
        val shown = guide("shown")
        val view = view(shown)
        val state = GuideState(guideId = shown.id)

        view.render(shown, state.copy(visible = false))
        assertTrue(view.fadingOut)
        assertEquals(WHOLE, view.snapshot().alphaAt())
        play()
        assertEquals(0, view.snapshot().alphaAt())

        view.render(shown, state)
        assertEquals(0, view.snapshot().alphaAt())
        view.appearance!!.setCurrentFraction(0.3f)
        // Faint, but already at its own size.
        assertTrue(view.snapshot().alphaAt(150, 62) > 0)
        play()
        assertEquals(WHOLE, view.snapshot().alphaAt())
    }

    @Test
    fun remove_fadesTheGuideOut() {
        val shown = guide("shown")
        val view = view(shown)

        view.render(null, GuideState(guideId = shown.id))
        view.render(null, GuideState())

        assertTrue(view.fadingOut)
        assertEquals(WHOLE, view.snapshot().alphaAt())
        play()
        assertFalse(view.fadingOut)
        assertEquals(0, view.snapshot().alphaAt())
    }

    @Test
    fun aMoveAndTheOpacity_duringTheComingIn_areDrawnAtOnce() {
        val view = view()
        val next = guide("next")
        view.render(next, GuideState(guideId = next.id))
        view.appearance!!.setCurrentFraction(0.99f)

        // Moved left by a quarter of the frame, at the highest opacity, while the coming in still runs.
        view.render(next, GuideState(guideId = next.id, cx = 0.25f, opacity = 0.9f))

        val drawn = view.snapshot()
        assertEquals(0, drawn.alphaAt(150 + 60, 200))
        assertEquals(128 * 0.9f * view.appearing, drawn.alphaAt(75, 200).toFloat(), 2f)
    }

    @Test
    fun aDragThatStartsWhileTheGuideComesIn_movesItAtOnce() {
        val view = view()
        val next = guide("next")
        val steps = mutableListOf<Float>()
        view.onGesture = { dx, _, _, _ -> steps += dx }
        view.render(next, GuideState(guideId = next.id))

        val time = SystemClock.uptimeMillis()
        assertTrue(view.onTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 150f, 200f, 0)))
        view.onTouchEvent(MotionEvent.obtain(time, time + 16, MotionEvent.ACTION_MOVE, 210f, 200f, 0))

        assertEquals(0f, view.snapshot().alphaAt().toFloat(), 0f)
        assertEquals(listOf(60f / 300f), steps)
    }

    private companion object {
        /** The guide's middle drawn whole: the outline's 128 at the opacity's 0.5. */
        const val WHOLE = 64

        /** The longest of the guide's animations, 250 ms, and a little more. */
        const val MOTION_END_MS = 350L
    }
}
