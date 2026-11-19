// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * The photo of the viewer's pages (FR-1.6; Design 2.6.5), as a gallery app handles one, on the real view in a parent
 * that records what it was asked to leave alone. A 300 × 200 photo fits a 400 × 300 view as 400 × 267, in the middle.
 * The pinch (boundary): spread twice around the middle, twice as large around the middle; spread past the limit, at
 * MAX_ZOOM; pinched in past the whole photo, the whole photo; its second finger keeps the touches from the pages.
 * The pan (boundary): zoomed, a drag moves the photo, never past its edges, and every touch is kept from the pages;
 * whole, no pan. The double tap (state-based): whole, it zooms in on where it lands, the point there staying, or as far
 * as an edge lets it near one; zoomed, it shows the whole photo again; no photo, nothing.
 * The drag to close (state-based): whole, a drag down moves the photo with the finger and tells the viewer, a lift
 * short of the line puts it back, one past it closes, and so does a flick; a drag up or sideways does neither, and a
 * sideways one is left to the pages; a second finger stops it and puts the photo back.
 * The tap (state-based): one tap tells the viewer once the wait for a second is over; a double tap does not.
 * A new photo, and reset: whole again, in its place.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoomablePhotoViewTest {
    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Pix)

    /** A parent that remembers whether it was asked to keep its hands off the touches, as the pages are. */
    private class Pages(context: Context) : FrameLayout(context) {
        var leftAlone = false

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            leftAlone = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private val pages = Pages(context)
    private val photo = ZoomablePhotoView(context)
    private val taps = mutableListOf<Unit>()
    private val drags = mutableListOf<Float>()
    private var dismissed = 0
    private var time = 0L
    private var down = 0L

    @Before
    fun setUp() {
        photo.setImageBitmap(Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888))
        photo.listener = object : ZoomablePhotoView.Listener {
            override fun onTap() {
                taps += Unit
            }

            override fun onDrag(offset: Float) {
                drags += offset
            }

            override fun onDismiss() {
                dismissed++
            }
        }
        pages.addView(photo, FrameLayout.LayoutParams(400, 300))
        pages.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        )
        pages.layout(0, 0, 400, 300)
        time = SystemClock.uptimeMillis()
    }

    private val density get() = context.resources.displayMetrics.density
    private val slop get() = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // ---- Touches ----------------------------------------------------------------------------------------------

    private class Finger(val id: Int, val x: Float, val y: Float)

    private fun touch(action: Int, vararg fingers: Finger, index: Int = 0, after: Long = 16L) {
        time += after
        if (action == MotionEvent.ACTION_DOWN) down = time
        val properties = fingers.map { finger ->
            MotionEvent.PointerProperties().apply {
                id = finger.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coordinates = fingers.map { finger ->
            MotionEvent.PointerCoords().apply {
                x = finger.x
                y = finger.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            down,
            time,
            action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            fingers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0,
        )
        try {
            photo.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Two fingers on a line through ([x], [y]), [from] px apart, then [to] px apart, then lifted. */
    private fun pinch(from: Float, to: Float, x: Float = 200f, y: Float = 150f) {
        fun apart(span: Float) = arrayOf(Finger(1, x - span / 2, y), Finger(2, x + span / 2, y))
        touch(MotionEvent.ACTION_DOWN, apart(from)[0])
        touch(MotionEvent.ACTION_POINTER_DOWN, *apart(from), index = 1)
        // Past the span slop, so the pinch begins; it scales from there.
        val begun = from + slop * 2 + 2
        touch(MotionEvent.ACTION_MOVE, *apart(begun))
        touch(MotionEvent.ACTION_MOVE, *apart(begun * to / from))
        touch(MotionEvent.ACTION_POINTER_UP, *apart(begun * to / from), index = 1)
        touch(MotionEvent.ACTION_UP, apart(begun * to / from)[0])
    }

    /** One finger from ([x], [y]) by ([dx], [dy]) in ten moves of [step] ms, held still for [hold] ms, then lifted. */
    private fun drag(x: Float, y: Float, dx: Float, dy: Float, step: Long = 50L, hold: Long = 200L) {
        touch(MotionEvent.ACTION_DOWN, Finger(0, x, y))
        for (i in 1..10) touch(MotionEvent.ACTION_MOVE, Finger(0, x + dx * i / 10, y + dy * i / 10), after = step)
        if (hold > 0) touch(MotionEvent.ACTION_MOVE, Finger(0, x + dx, y + dy), after = hold)
        touch(MotionEvent.ACTION_UP, Finger(0, x + dx, y + dy), after = if (hold > 0) 100L else step)
        idle()
    }

    private fun tap(x: Float = 200f, y: Float = 150f) {
        touch(MotionEvent.ACTION_DOWN, Finger(0, x, y))
        touch(MotionEvent.ACTION_UP, Finger(0, x, y), after = 50L)
    }

    private fun doubleTap(x: Float = 200f, y: Float = 150f) {
        tap(x, y)
        touch(MotionEvent.ACTION_DOWN, Finger(0, x, y), after = 100L)
        touch(MotionEvent.ACTION_UP, Finger(0, x, y), after = 50L)
        idle()
    }

    private fun waitForASecondTap() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

    private val whole = RectF(0f, 50f / 3, 400f, 300f - 50f / 3)

    private fun assertShows(expected: RectF, actual: RectF = photo.shownRect()) {
        val near = { a: Float, b: Float -> kotlin.math.abs(a - b) <= 1f }
        assertTrue(
            "$actual, not $expected",
            near(expected.left, actual.left) && near(expected.top, actual.top) &&
                near(expected.right, actual.right) && near(expected.bottom, actual.bottom),
        )
    }

    // ---- The pinch --------------------------------------------------------------------------------------------

    @Test
    fun aPinch_zoomsAroundTheFingers_upToItsLimit_andNeverBelowTheWholePhoto() {
        assertShows(whole)

        // Wide apart, as ScaleGestureDetector needs a span over its minimum.
        pinch(from = 300f, to = 600f)
        assertEquals(2f, photo.zoom, 0.01f)
        // Around the middle: the middle stays.
        assertEquals(200f, photo.shownRect().centerX(), 1f)
        assertEquals(150f, photo.shownRect().centerY(), 1f)
        assertTrue(pages.leftAlone)

        pinch(from = 300f, to = 1200f)
        assertEquals(ZoomablePhotoView.MAX_ZOOM, photo.zoom)

        pinch(from = 1200f, to = 260f)
        assertEquals(1f, photo.zoom)
        assertShows(whole)
    }

    // ---- The pan ----------------------------------------------------------------------------------------------

    @Test
    fun zoomed_aDragPansThePhoto_neverPastItsEdges_andTheTouchesStayFromThePages() {
        doubleTap()
        val zoomed = photo.shownRect()

        drag(200f, 150f, dx = 40f, dy = 0f)
        assertTrue(pages.leftAlone)
        assertEquals(zoomed.left + 40f, photo.shownRect().left, 1f)

        // Far past the left edge and the top: the photo stops at them.
        drag(200f, 150f, dx = 2000f, dy = 2000f)
        assertEquals(0f, photo.shownRect().left, 1f)
        assertEquals(0f, photo.shownRect().top, 1f)
        drag(200f, 150f, dx = -4000f, dy = -4000f)
        assertEquals(400f, photo.shownRect().right, 1f)
        assertEquals(300f, photo.shownRect().bottom, 1f)
        assertEquals(0, dismissed)
    }

    @Test
    fun whole_aSidewaysDrag_movesNothing_andIsLeftToThePages() {
        drag(200f, 150f, dx = 120f, dy = 10f)

        assertShows(whole)
        assertFalse(pages.leftAlone)
        assertTrue(drags.isEmpty())
    }

    // ---- The double tap ---------------------------------------------------------------------------------------

    @Test
    fun aDoubleTap_zoomsInOnWhereItLands_orAsFarAsAnEdgeLets_andAnotherShowsTheWholePhoto() {
        // Right of the middle: the point under the finger stays there.
        doubleTap(x = 260f, y = 150f)
        assertEquals(ZoomablePhotoView.DOUBLE_TAP_ZOOM, photo.zoom, 0.01f)
        val zoomed = photo.shownRect()
        assertEquals(260f, zoomed.left + (260f - whole.left) * ZoomablePhotoView.DOUBLE_TAP_ZOOM, 1f)
        assertTrue(taps.isEmpty())

        doubleTap()
        assertEquals(1f, photo.zoom)
        assertShows(whole)

        // At the very corner: the photo's corner stays in the view's.
        doubleTap(x = 0f, y = 20f)
        assertEquals(0f, photo.shownRect().left, 1f)
        assertEquals(0f, photo.shownRect().top, 1f)
    }

    @Test
    fun withoutAPhoto_aDoubleTapZoomsNothing() {
        photo.setImageDrawable(null)

        doubleTap()

        assertEquals(1f, photo.zoom)
    }

    // ---- The drag to close ------------------------------------------------------------------------------------

    @Test
    fun whole_aDragDown_movesThePhotoWithTheFinger_aShortOneGoesBack_andOnePastTheLineCloses() {
        val line = ZoomablePhotoView.DISMISS_DP * density

        drag(200f, 100f, dx = 0f, dy = line - 20f)
        assertTrue(drags.max() >= line - 21f)
        assertTrue(pages.leftAlone)
        assertEquals(0, dismissed)
        assertEquals(0f, photo.translationY)
        assertEquals(0f, drags.last())

        drags.clear()
        drag(200f, 100f, dx = 0f, dy = line + 20f)
        assertEquals(1, dismissed)
    }

    @Test
    fun whole_aQuickFlickDown_closes_however_short() {
        drag(200f, 100f, dx = 0f, dy = ZoomablePhotoView.DISMISS_DP * density / 2, step = 3L, hold = 0L)

        assertEquals(1, dismissed)
    }

    @Test
    fun whole_aDragUp_movesNothing() {
        drag(200f, 200f, dx = 0f, dy = -150f)

        assertTrue(drags.isEmpty())
        assertEquals(0f, photo.translationY)
        assertEquals(0, dismissed)
    }

    @Test
    fun aSecondFinger_stopsTheDragToClose_andPutsThePhotoBack() {
        touch(MotionEvent.ACTION_DOWN, Finger(0, 200f, 100f))
        for (i in 1..5) touch(MotionEvent.ACTION_MOVE, Finger(0, 200f, 100f + 30f * i))
        assertTrue(photo.translationY > 0f)

        touch(MotionEvent.ACTION_POINTER_DOWN, Finger(0, 200f, 250f), Finger(1, 100f, 250f), index = 1)
        idle()
        touch(MotionEvent.ACTION_POINTER_UP, Finger(0, 200f, 250f), Finger(1, 100f, 250f), index = 1)
        touch(MotionEvent.ACTION_UP, Finger(0, 200f, 250f))
        idle()

        assertEquals(0f, photo.translationY)
        assertEquals(0, dismissed)
    }

    // ---- The tap ----------------------------------------------------------------------------------------------

    @Test
    fun aTap_tellsTheViewerOnce_afterTheWaitForASecond_andADoubleTapDoesNot() {
        tap()
        assertTrue(taps.isEmpty())
        waitForASecondTap()
        assertEquals(1, taps.size)

        doubleTap()
        waitForASecondTap()
        assertEquals(1, taps.size)
    }

    // ---- A new photo, and reset -------------------------------------------------------------------------------

    @Test
    fun aNewPhoto_andReset_showTheWholePhotoInItsPlace() {
        doubleTap()
        photo.setImageBitmap(Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888))
        assertEquals(1f, photo.zoom)
        assertShows(whole)

        doubleTap()
        photo.reset()
        assertEquals(1f, photo.zoom)
        assertShows(whole)
    }
}
