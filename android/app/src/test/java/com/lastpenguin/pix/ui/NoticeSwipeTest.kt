// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.R as materialR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNotice
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/*
 * A notice swiped down and away (Design 2.8; SwipeDownToDismiss in ui/Notices.kt), on a notice of the activity's and on
 * one in a sheet's window. Partitions of the drag (boundary value analysis on its length, and its speed): down past
 * half the notice's height (gone), down less and slowly (back where it was, whole again), down less but flung (gone),
 * up (left where it is), sideways while wandering down past the slop (left), and a quick fling mostly sideways after a
 * start down (left). Where it starts: on the text, or on the action, whose tap still runs it and whose drag goes
 * without running it. In a sheet, the drag takes the notice away and leaves the sheet open. How long it goes: 300 ms,
 * swiped away or by itself.
 */
@RunWith(AndroidJUnit4::class)
class NoticeSwipeTest {
    @get:Rule
    val activities = OwnActivities()

    private val activity = activities.start(R.style.Theme_Pix)

    private var undone = 0

    private fun showNotice() {
        activity.showNotice("A notice", NoticeLength.UNDO, NoticeAction(R.string.undo) { undone++ })
        // Past the notice's own entrance.
        settle()
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SETTLE_MS))

    /** The notice's own view in [root]'s windows, the newest. */
    private fun noticeIn(root: View): Snackbar.SnackbarLayout {
        val text = checkNotNull(root.findViewById<View>(materialR.id.snackbar_text))
        var view: View = text
        while (view !is Snackbar.SnackbarLayout) view = view.parent as View
        return view
    }

    private fun notice() = noticeIn(activity.window.decorView)

    /**
     * A finger down at [from] on [notice] (its own coordinates), moved [by] px down (up when negative) and [across] px to
     * the right in [steps] over [ms], held there for [holdMs], and lifted: the notice's events as a finger's, timed so
     * the speed at the lift is the way over [ms] unless the finger held still first.
     */
    private fun drag(
        notice: View,
        from: Pair<Float, Float>,
        by: Float,
        ms: Long,
        holdMs: Long = 0,
        steps: Int = 5,
        across: Float = 0f,
    ) {
        val start = SystemClock.uptimeMillis()
        val (x, y) = from
        fun send(action: Int, at: Long, dx: Float, dy: Float) {
            val event = MotionEvent.obtain(start, start + at, action, x + dx, y + dy, 0)
            notice.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0, 0f, 0f)
        for (step in 1..steps) {
            send(
                MotionEvent.ACTION_MOVE,
                ms * step / steps,
                across * step / steps,
                by * step / steps,
            )
        }
        if (holdMs > 0) {
            send(MotionEvent.ACTION_MOVE, ms + holdMs / 2, across, by)
            send(MotionEvent.ACTION_MOVE, ms + holdMs, across, by)
        }
        send(MotionEvent.ACTION_UP, ms + holdMs, across, by)
    }

    private fun middleOf(notice: View) = notice.width / 3f to notice.height / 2f

    /** The action's middle, in the notice's coordinates. */
    private fun actionOn(notice: View): Pair<Float, Float> {
        val action = notice.findViewById<View>(materialR.id.snackbar_action)
        val at = IntArray(2).also(action::getLocationInWindow)
        val origin = IntArray(2).also(notice::getLocationInWindow)
        return at[0] - origin[0] + action.width / 2f to at[1] - origin[1] + action.height / 2f
    }

    @Test
    fun aDragDownPastHalfItsHeight_takesTheNoticeAway() {
        showNotice()
        val notice = notice()

        drag(notice, middleOf(notice), by = notice.height * 0.6f, ms = 600, holdMs = 300)
        // It slides away over as long as it fades by itself.
        assertEquals(OUT_MS, notice.animate().duration)
        settle()

        assertNull(shownNotice())
        assertEquals(0, undone)
    }

    @Test
    fun aShorterSlowDrag_springsTheNoticeBack_whole() {
        showNotice()
        val notice = notice()

        drag(notice, middleOf(notice), by = notice.height * 0.4f, ms = 600, holdMs = 300)
        settle()

        assertEquals("A notice", shownNotice())
        assertEquals(0f, notice.translationY, 0.5f)
        assertEquals(1f, notice.alpha, 0.01f)
    }

    @Test
    fun theNoticeFollowsTheFingerDown_fading() {
        showNotice()
        val notice = notice()
        val (x, y) = middleOf(notice)
        val start = SystemClock.uptimeMillis()
        val down = notice.height * 0.5f

        notice.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0))
        notice.dispatchTouchEvent(MotionEvent.obtain(start, start + 100, MotionEvent.ACTION_MOVE, x, y + down, 0))

        assertEquals(down, notice.translationY, 0.5f)
        assertEquals(0.5f, notice.alpha, 0.01f)
    }

    @Test
    fun aShortQuickFlingDown_takesTheNoticeAway() {
        showNotice()
        val notice = notice()

        drag(notice, middleOf(notice), by = notice.height * 0.3f, ms = 30, steps = 3)
        settle()

        assertNull(shownNotice())
    }

    @Test
    fun aDragUp_leavesTheNoticeWhereItIs() {
        showNotice()
        val notice = notice()

        drag(notice, middleOf(notice), by = -notice.height.toFloat(), ms = 60, steps = 3)
        settle()

        assertEquals("A notice", shownNotice())
        assertEquals(0f, notice.translationY, 0.5f)
    }

    @Test
    fun aSidewaysSwipe_thatWandersDownPastTheSlop_leavesTheNoticeWhereItIs() {
        showNotice()
        val notice = notice()
        val slop = ViewConfiguration.get(notice.context).scaledTouchSlop.toFloat()
        val start = SystemClock.uptimeMillis()
        val (x, y) = middleOf(notice)
        fun send(action: Int, at: Long, dx: Float, dy: Float) {
            val event = MotionEvent.obtain(start, start + at, action, x + dx, y + dy, 0)
            notice.dispatchTouchEvent(event)
            event.recycle()
        }

        // Twice the slop down on the way, but four times as far sideways: the notice does not follow.
        send(MotionEvent.ACTION_DOWN, 0, 0f, 0f)
        send(MotionEvent.ACTION_MOVE, 100, slop * 4, slop)
        send(MotionEvent.ACTION_MOVE, 200, slop * 8, slop * 2)
        assertEquals(0f, notice.translationY, 0.5f)
        send(MotionEvent.ACTION_UP, 500, slop * 8, slop * 2)
        settle()

        assertEquals("A notice", shownNotice())
        assertEquals(0f, notice.translationY, 0.5f)
    }

    @Test
    fun aQuickDiagonalFling_mostlySideways_leavesTheNotice() {
        showNotice()
        val notice = notice()
        val slop = ViewConfiguration.get(notice.context).scaledTouchSlop.toFloat()
        // Past the slop, so the drag starts, and short of half the notice's height at the end, so only a fling could
        // take it: still going down at the lift, fast enough for one, but far faster sideways.
        val down = slop * 1.1f
        val last = slop * 1.5f
        assertTrue("$last px is under half of ${notice.height} px", last < notice.height / 2f)
        val start = SystemClock.uptimeMillis()
        val (x, y) = middleOf(notice)
        fun send(action: Int, at: Long, dx: Float, dy: Float) {
            val event = MotionEvent.obtain(start, start + at, action, x + dx, y + dy, 0)
            notice.dispatchTouchEvent(event)
            event.recycle()
        }

        // Down first, then flung far sideways.
        send(MotionEvent.ACTION_DOWN, 0, 0f, 0f)
        send(MotionEvent.ACTION_MOVE, 20, 0f, down)
        assertTrue("the drag started", notice.translationY > 0f)
        send(MotionEvent.ACTION_MOVE, 30, slop * 6, (down + last) / 2)
        send(MotionEvent.ACTION_MOVE, 40, slop * 12, last)
        send(MotionEvent.ACTION_UP, 40, slop * 12, last)
        settle()

        assertEquals("A notice", shownNotice())
        assertEquals(0f, notice.translationY, 0.5f)
    }

    @Test
    fun aDragThatStartsOnTheAction_takesTheNoticeAway_withoutRunningIt() {
        showNotice()
        val notice = notice()

        drag(notice, actionOn(notice), by = notice.height * 0.8f, ms = 600, holdMs = 300)
        settle()

        assertNull(shownNotice())
        assertEquals(0, undone)
        assertFalse(notice.findViewById<View>(materialR.id.snackbar_action).isPressed)
    }

    @Test
    fun goingByItself_theNoticeFadesOverItsOwnTime_notMaterials() {
        showNotice()

        // Material's Snackbar fades out over motionDurationMedium1, in the notice's own theme.
        val going = TypedValue()
        assertTrue(notice().context.theme.resolveAttribute(materialR.attr.motionDurationMedium1, going, true))
        assertEquals(OUT_MS, going.data.toLong())
    }

    @Test
    fun aTapOnTheAction_stillRunsIt() {
        showNotice()
        val notice = notice()

        drag(notice, actionOn(notice), by = 0f, ms = 50, steps = 1)
        idle()

        assertEquals(1, undone)
    }

    @Test
    fun aNoticeInASheet_goesWithTheSwipe_andTheSheetStaysOpen() {
        val sheet = Sheet()
        sheet.show(activity.supportFragmentManager, "sheet")
        idle()
        sheet.showNoticeInDialog(R.string.link_copied)
        settle()
        assertEquals(string(R.string.link_copied), shownNotice())
        val dialog = sheet.dialog as BottomSheetDialog
        val notice = noticeIn(checkNotNull(dialog.window).decorView)

        drag(notice, middleOf(notice), by = notice.height * 0.8f, ms = 600, holdMs = 300)
        settle()

        assertNull(shownNotice())
        assertTrue(dialog.isShowing)
        assertNotNull(sheet.view)
        assertTrue(dialog.behavior.state != BottomSheetBehavior.STATE_HIDDEN)
    }

    /** A sheet that stays open, as Account and Invite sent are, with room for a notice in its window. */
    class Sheet : BottomSheetDialogFragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
            FrameLayout(inflater.context).apply { minimumHeight = SHEET_HEIGHT_PX }
    }

    private companion object {
        /** Longer than a notice's entrance and its going, shorter than its 5 s. */
        const val SETTLE_MS = 500L

        /** A notice's going, `notice_out_ms`. */
        const val OUT_MS = 300L
        const val SHEET_HEIGHT_PX = 400
    }
}
