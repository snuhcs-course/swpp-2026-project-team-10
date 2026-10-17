// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Partitions for the reference thumbnail's touches (FR-3.9), single-finger MotionEvents as the thumbnail receives them.
 * A tap, and a move within the touch slop (both a tap); a drag past the slop (follows the finger, kept inside the
 * area); a drag released in each quarter of the area (that corner); a drag the system cancels (no corner).
 */
@RunWith(AndroidJUnit4::class)
class ReferenceDragControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val area = ReferenceArea(left = 31, top = 31, right = 1049, bottom = 1667)

    @Test
    fun touch_releasedWithoutMovingOrWithinTheSlop_isATap() = onMain {
        val fixture = fixture()
        val slop = ViewConfiguration.get(fixture.view.context).scaledTouchSlop

        drag(fixture.view, dx = 0f, dy = 0f)
        drag(fixture.view, dx = slop / 2f, dy = slop / 2f)

        assertEquals(2, fixture.taps)
        assertTrue(fixture.drops.isEmpty())
        assertEquals(31f, fixture.view.translationX)
        assertEquals(31f, fixture.view.translationY)
    }

    @Test
    fun drag_pastTheSlop_followsTheFingerAndStaysInsideTheArea() = onMain {
        val fixture = fixture()
        val view = fixture.view

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 400f, 600f)
        assertTrue(fixture.controller.isDragging)
        assertEquals(331f, view.translationX)
        assertEquals(531f, view.translationY)

        touch(view, MotionEvent.ACTION_MOVE, 5_000f, -5_000f)
        assertEquals(923f, view.translationX)
        assertEquals(31f, view.translationY)
        assertEquals(0, fixture.taps)
    }

    @Test
    fun drag_releasedInEachQuarter_dropsIntoThatCorner() = onMain {
        val drops = mapOf(
            (100f to 100f) to ReferenceCorner.TOP_LEFT,
            (700f to 100f) to ReferenceCorner.TOP_RIGHT,
            (100f to 1_000f) to ReferenceCorner.BOTTOM_LEFT,
            (700f to 1_000f) to ReferenceCorner.BOTTOM_RIGHT,
        )
        for ((move, corner) in drops) {
            val fixture = fixture()

            drag(fixture.view, dx = move.first, dy = move.second)

            assertEquals(listOf(corner), fixture.drops)
            assertFalse(fixture.controller.isDragging)
            assertEquals(0, fixture.taps)
        }
    }

    @Test
    fun drag_cancelledBySystem_reportsNoCorner() = onMain {
        val fixture = fixture()

        touch(fixture.view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(fixture.view, MotionEvent.ACTION_MOVE, 600f, 900f)
        touch(fixture.view, MotionEvent.ACTION_CANCEL, 600f, 900f)

        assertEquals(listOf<ReferenceCorner?>(null), fixture.drops)
        assertFalse(fixture.controller.isDragging)
        assertEquals(0, fixture.taps)
    }

    private class Fixture(val view: View, val drops: MutableList<ReferenceCorner?>) {
        var taps = 0
        lateinit var controller: ReferenceDragController
    }

    /** A small thumbnail, 126 × 189 px, in the top-left corner of [area]. */
    private fun fixture(): Fixture {
        val view = View(instrumentation.targetContext).apply {
            layout(0, 0, 126, 189)
            translationX = 31f
            translationY = 31f
        }
        val fixture = Fixture(view, mutableListOf())
        fixture.controller = ReferenceDragController(view, { area }, { fixture.taps++ }) { fixture.drops += it }
        return fixture
    }

    /** Down at (100, 100), one move by ([dx], [dy]), and up there. */
    private fun drag(view: View, dx: Float, dy: Float) {
        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 100f + dx, 100f + dy)
        touch(view, MotionEvent.ACTION_UP, 100f + dx, 100f + dy)
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }
}
