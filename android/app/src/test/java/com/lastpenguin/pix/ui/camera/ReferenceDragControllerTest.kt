// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

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
 * A tap, and a move within the touch slop (both a tap); a drag past the slop (follows the finger, inside the area's
 * height, and sideways out to where only its peek shows, fading as it goes); a drag released in each quarter of the
 * area (that corner, shown); a drag the system cancels (no drop).
 * Hiding (#237): released with more than half of it past the side, and with half or less (hidden, and its corner);
 * flung toward its side, and dragged as far slowly (hidden, and its corner). A hidden thumbnail: a tap on its strip,
 * a drag back in, and one that ends still out. The handle beside a hidden thumbnail: its touches are the thumbnail's.
 * The lift: at the last move's place (above), carrying the last of the move (counted for the place and the speed), and
 * far from the touch with no move between (a drag). A thumbnail narrower than the peek: out to just over half of it.
 * A drag begun while the thumbnail glides: from where it is when the finger passes the slop, not where it was pressed,
 * and held by the size it is drawn at then, smaller than it is laid out.
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
    fun drag_pastTheSlop_followsTheFinger_outToThePeekSidewaysAndInsideTheAreasHeight() = onMain {
        val fixture = fixture()
        val view = fixture.view

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 400f, 600f)
        assertTrue(fixture.controller.isDragging)
        assertEquals(331f, view.translationX)
        assertEquals(531f, view.translationY)
        assertEquals(1f, view.alpha)

        // To the right, it goes out until only its peek of 42 px is in the 1080 px wide preview; up, it stops.
        touch(view, MotionEvent.ACTION_MOVE, 5_000f, -5_000f)
        assertEquals(1_038f, view.translationX)
        assertEquals(31f, view.translationY)
        assertEquals(REFERENCE_HIDDEN_ALPHA, view.alpha, 0.001f)

        // To the left the same, and down it stops above the guide's controls.
        touch(view, MotionEvent.ACTION_MOVE, -5_000f, 5_000f)
        assertEquals(-84f, view.translationX)
        assertEquals(1_478f, view.translationY)
        assertEquals(REFERENCE_HIDDEN_ALPHA, view.alpha, 0.001f)
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

            assertEquals(listOf(ReferenceDrop(corner, hidden = false)), fixture.drops)
            assertFalse(fixture.controller.isDragging)
            assertEquals(0, fixture.taps)
        }
    }

    @Test
    fun drag_cancelledBySystem_reportsNoDrop() = onMain {
        val fixture = fixture()

        touch(fixture.view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(fixture.view, MotionEvent.ACTION_MOVE, 600f, 900f)
        touch(fixture.view, MotionEvent.ACTION_CANCEL, 600f, 900f)

        assertEquals(listOf<ReferenceDrop?>(null), fixture.drops)
        assertFalse(fixture.controller.isDragging)
        assertEquals(0, fixture.taps)
    }

    // ---- hiding (#237) ----------------------------------------------------------------------------------------------

    @Test
    fun drag_releasedWithMoreThanHalfOfItPastTheSide_dropsHiddenThere() = onMain {
        // The thumbnail is 126 px wide at x = 31: 95 px to the left puts its center one pixel past the side.
        val out = fixture()
        drag(out.view, dx = -95f, dy = 0f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true)), out.drops)

        val half = fixture()
        drag(half.view, dx = -94f, dy = 0f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false)), half.drops)

        // At the right side, from the top-right corner at x = 923.
        val right = fixture(x = 923f)
        drag(right.view, dx = 95f, dy = 0f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_RIGHT, hidden = true)), right.drops)
    }

    @Test
    fun drag_flungTowardItsSide_dropsHidden_andAsFarSlowlyDoesNot() = onMain {
        // 60 px in 20 ms is 3000 px/s, over the fling's 2100 px/s, with the thumbnail's center still in the preview.
        val flung = fixture()
        touchAt(flung.view, MotionEvent.ACTION_DOWN, 300f, 100f, time = 1_000)
        touchAt(flung.view, MotionEvent.ACTION_MOVE, 270f, 100f, time = 1_010)
        touchAt(flung.view, MotionEvent.ACTION_MOVE, 240f, 100f, time = 1_020)
        touchAt(flung.view, MotionEvent.ACTION_UP, 240f, 100f, time = 1_020)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true)), flung.drops)

        val slow = fixture()
        touchAt(slow.view, MotionEvent.ACTION_DOWN, 300f, 100f, time = 1_000)
        touchAt(slow.view, MotionEvent.ACTION_MOVE, 270f, 100f, time = 1_300)
        touchAt(slow.view, MotionEvent.ACTION_MOVE, 240f, 100f, time = 1_600)
        touchAt(slow.view, MotionEvent.ACTION_UP, 240f, 100f, time = 1_600)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false)), slow.drops)

        // A fast start that ended in a pause is not a fling: only the last moves count.
        val paused = fixture()
        touchAt(paused.view, MotionEvent.ACTION_DOWN, 300f, 100f, time = 1_000)
        touchAt(paused.view, MotionEvent.ACTION_MOVE, 240f, 100f, time = 1_020)
        touchAt(paused.view, MotionEvent.ACTION_MOVE, 240f, 100f, time = 1_400)
        touchAt(paused.view, MotionEvent.ACTION_UP, 240f, 100f, time = 1_400)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false)), paused.drops)
    }

    @Test
    fun release_carryingTheLastOfTheMove_countsForThePlaceAndTheSpeed() = onMain {
        // The lift brings 40 of the 60 px: with it the drag ends at 3000 px/s, a fling; without it at 2000, none.
        val flung = fixture()
        touchAt(flung.view, MotionEvent.ACTION_DOWN, 300f, 100f, time = 1_000)
        touchAt(flung.view, MotionEvent.ACTION_MOVE, 280f, 100f, time = 1_010)
        touchAt(flung.view, MotionEvent.ACTION_UP, 240f, 100f, time = 1_020)
        assertEquals(-29f, flung.view.translationX)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true)), flung.drops)

        // A lift far from the touch with no move between is a drag to there, not a tap.
        val jumped = fixture()
        touchAt(jumped.view, MotionEvent.ACTION_DOWN, 100f, 100f, time = 1_000)
        touchAt(jumped.view, MotionEvent.ACTION_UP, 800f, 1_100f, time = 3_000)
        assertEquals(0, jumped.taps)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.BOTTOM_RIGHT, hidden = false)), jumped.drops)
    }

    @Test
    fun drag_begunWhileTheThumbnailGlides_goesOnFromWhereItIsThen() = onMain {
        // A press does not stop a glide, so the thumbnail has moved on by the time the finger passes the slop.
        val fixture = fixture()
        touch(fixture.view, MotionEvent.ACTION_DOWN, 100f, 100f)
        fixture.view.translationX = 200f
        fixture.view.translationY = 300f
        touch(fixture.view, MotionEvent.ACTION_MOVE, 150f, 140f)

        assertEquals(250f, fixture.view.translationX)
        assertEquals(340f, fixture.view.translationY)
    }

    @Test
    fun thumbnailTakenWhileItChangesSize_isHeldByTheSizeItIsDrawnAt() = onMain {
        // Stopped halfway through shrinking: laid out 400 x 600 px and drawn at half of that, 200 x 300, about its
        // middle, with its drawn left and top at the area's.
        val fixture = fixture(width = 400, height = 600)
        val view = fixture.view
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        view.translationX = 31f - 100f
        view.translationY = 31f - 150f
        fun left() = view.translationX + 100f
        fun top() = view.translationY + 150f

        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 150f, 140f)
        assertEquals(81f to 71f, left() to top())

        // Out to the left, its peek of 42 px stays in the preview; down, it stops with its drawn bottom at the area's.
        touch(view, MotionEvent.ACTION_MOVE, -5_000f, 5_000f)
        assertEquals(42f, left() + 200f)
        assertEquals(1_667f, top() + 300f)
        assertEquals(REFERENCE_HIDDEN_ALPHA, view.alpha, 0.001f)
        touch(view, MotionEvent.ACTION_UP, -5_000f, 5_000f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.BOTTOM_LEFT, hidden = true)), fixture.drops)

        // To the right and up the same.
        touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(view, MotionEvent.ACTION_MOVE, 5_000f, -5_000f)
        assertEquals(1_038f, left())
        assertEquals(31f, top())
    }

    @Test
    fun narrowThumbnail_dragsOutUntilJustOverHalfOfItIsOut_andHidesThere() = onMain {
        // 24 px wide, narrower than the 42 px peek: 11 px of it stay, so its center is past the side.
        val left = fixture(width = 24)
        touch(left.view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(left.view, MotionEvent.ACTION_MOVE, -5_000f, 100f)
        assertEquals(-13f, left.view.translationX)
        assertEquals(REFERENCE_HIDDEN_ALPHA, left.view.alpha, 0.001f)
        touch(left.view, MotionEvent.ACTION_UP, -5_000f, 100f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true)), left.drops)

        val right = fixture(x = 1_025f, width = 24)
        touch(right.view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(right.view, MotionEvent.ACTION_MOVE, 5_000f, 100f)
        assertEquals(1_069f, right.view.translationX)
        touch(right.view, MotionEvent.ACTION_UP, 5_000f, 100f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_RIGHT, hidden = true)), right.drops)
    }

    @Test
    fun hiddenThumbnail_tappedOnItsStrip_isATap() = onMain {
        val fixture = fixture(x = -84f)

        drag(fixture.view, dx = 0f, dy = 0f)

        assertEquals(1, fixture.taps)
        assertTrue(fixture.drops.isEmpty())
        assertEquals(-84f, fixture.view.translationX)
    }

    @Test
    fun hiddenThumbnail_draggedBackIn_dropsShownInItsCorner_andLeftOutStaysHidden() = onMain {
        val back = fixture(x = -84f)
        touch(back.view, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(back.view, MotionEvent.ACTION_MOVE, 300f, 100f)
        // It comes with the finger, and is itself again once it is inside.
        assertEquals(116f, back.view.translationX)
        assertEquals(1f, back.view.alpha)
        touch(back.view, MotionEvent.ACTION_UP, 300f, 100f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false)), back.drops)

        // Pulled a little and let go with most of it still out: hidden again.
        val out = fixture(x = -84f)
        drag(out.view, dx = 20f, dy = 0f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true)), out.drops)
    }

    @Test
    fun touchesOnTheHandle_areTheThumbnails() = onMain {
        val fixture = fixture(x = -84f)
        val handle = View(instrumentation.targetContext).apply { layout(0, 0, 126, 189) }
        fixture.controller.listenOn(handle)

        drag(handle, dx = 0f, dy = 0f)
        assertEquals(1, fixture.taps)

        touch(handle, MotionEvent.ACTION_DOWN, 100f, 100f)
        touch(handle, MotionEvent.ACTION_MOVE, 300f, 100f)
        assertEquals(116f, fixture.view.translationX)
        touch(handle, MotionEvent.ACTION_UP, 300f, 100f)
        assertEquals(listOf(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false)), fixture.drops)
    }

    private class Fixture(val view: View, val drops: MutableList<ReferenceDrop?>) {
        var taps = 0
        lateinit var controller: ReferenceDragController
    }

    /**
     * A small thumbnail, [width] × [height] px (126 × 189 for a 2:3 photo), at [x] in the top of [area], in a preview
     * 1080 px wide: its peek is 42 px (16 dp), and a fling is 2100 px/s (800 dp/s).
     */
    private fun fixture(x: Float = 31f, width: Int = 126, height: Int = 189): Fixture {
        val view = View(instrumentation.targetContext).apply {
            layout(0, 0, width, height)
            translationX = x
            translationY = 31f
        }
        val fixture = Fixture(view, mutableListOf())
        fixture.controller = ReferenceDragController(
            view,
            area = { area },
            previewWidth = { 1080 },
            peek = 42,
            flingSpeed = 2_100f,
            onTap = { fixture.taps++ },
        ) { fixture.drops += it }
        return fixture
    }

    /** Down at (100, 100), one move by ([dx], [dy]), and up there, each a long moment after the last. */
    private fun drag(view: View, dx: Float, dy: Float) {
        touchAt(view, MotionEvent.ACTION_DOWN, 100f, 100f, time = 1_000)
        touchAt(view, MotionEvent.ACTION_MOVE, 100f + dx, 100f + dy, time = 2_000)
        touchAt(view, MotionEvent.ACTION_UP, 100f + dx, 100f + dy, time = 3_000)
    }

    /** A touch without a speed: every event a second after the one before. */
    private var clock = 10_000L

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        clock += 1_000
        touchAt(view, action, x, y, clock)
    }

    private fun touchAt(view: View, action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(time, time, action, x, y, 0)
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
