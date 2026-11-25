// AI-generated with Claude Code, 2026-11-24, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/*
 * How far a drag moves the brightness (FR-1.11, Design 2.8): 1 EV for each `ExposureBarView.DP_PER_EV` of the finger's
 * way, as slowly as an iPhone's sun, whatever the camera's step or the bar's length.
 * Partitions (equivalence): a camera of 0.1 EV steps (Samsung's ±2 EV in 41) and one of 1/6 EV (Pixel's ±2 EV in 25);
 * a drag up and one down; a bar laid out short, tall, and not at all. Boundary: half a step's way, which rounds to the
 * step, and just under it; a drag past the range's end, which stops there; a camera without a step, which a drag leaves.
 * A value not known yet (Subject view after a tap to focus, #273), state-based: a touch and TalkBack's change while it
 * is awaited (nothing changes), and the same touch once it is known (a drag from the value shown then, by the finger's
 * way from there); a waiting touch on a bar that is disabled before the finger lifts, which holds it no longer.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xxhdpi")
class ExposureBarViewTest {
    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Pix)
    private val density = context.resources.displayMetrics.density

    private fun bar(range: IntRange, step: Float, value: Int = 0) = ExposureBarView(context).apply {
        render(range, step, value, enabled = true)
    }

    /** The index a drag of [dp] up from [value] ends on, and every index it reported on the way. */
    private fun ExposureBarView.drag(dp: Float): Pair<Int, List<Pair<Int, Boolean>>> {
        val reported = mutableListOf<Pair<Int, Boolean>>()
        onChange = { index, final -> reported += index to final }
        startDrag()
        dragTo(dp * density)
        finishDrag()
        return index to reported
    }

    private fun ExposureBarView.touch(action: Int, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, 10f, y, 0)
        onTouchEvent(event)
        event.recycle()
    }

    @Test
    fun aTouchWhileTheValueIsAwaited_changesNothing_andItsDragThenStartsFromTheValueThatCame() {
        val reported = mutableListOf<Pair<Int, Boolean>>()
        // 0.5 EV steps: 150 dp of the finger's way is two steps.
        val bar = bar(-4..4, 0.5f, value = 3).apply { onChange = { index, final -> reported += index to final } }
        bar.awaitingValue = true

        bar.touch(MotionEvent.ACTION_DOWN, 600f)
        bar.touch(MotionEvent.ACTION_MOVE, 600f - 150f * density)
        assertEquals(3, bar.index)
        assertTrue(reported.isEmpty())
        // The finger holds the bar up, as a drag does.
        assertTrue(bar.isHeld)

        // The value came: the camera went back to 0. The finger's way so far does not count.
        bar.render(-4..4, 0.5f, 0, enabled = true)
        bar.awaitingValue = false
        bar.touch(MotionEvent.ACTION_MOVE, 600f - 160f * density)
        assertEquals(0, bar.index)
        bar.touch(MotionEvent.ACTION_MOVE, 600f - 310f * density)
        bar.touch(MotionEvent.ACTION_UP, 600f - 310f * density)

        assertEquals(2, bar.index)
        assertEquals(listOf(2 to false, 2 to true), reported)
        assertFalse(bar.isHeld)
    }

    @Test
    fun aWaitingTouch_onABarDisabledMeanwhile_holdsItNoLonger() {
        val touching = mutableListOf<Boolean>()
        val bar = bar(-4..4, 0.5f, value = 3).apply { onTouching = { touching += it } }
        bar.awaitingValue = true
        bar.touch(MotionEvent.ACTION_DOWN, 600f)
        assertTrue(bar.isHeld)

        // The link dropped under the finger: the screen disables the bar before the finger lifts.
        bar.render(-4..4, 0.5f, 3, enabled = false)
        assertFalse(bar.isHeld)
        bar.touch(MotionEvent.ACTION_UP, 600f)
        assertFalse(bar.isHeld)
        assertEquals(listOf(true, false), touching)
    }

    @Test
    fun talkBacksChangeWhileTheValueIsAwaited_isNotTaken() {
        val reported = mutableListOf<Pair<Int, Boolean>>()
        val bar = bar(-4..4, 0.5f, value = 3).apply { onChange = { index, final -> reported += index to final } }
        bar.awaitingValue = true

        assertFalse(bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
        assertEquals(3, bar.index)
        assertTrue(reported.isEmpty())

        bar.awaitingValue = false
        assertTrue(bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
        assertEquals(2, bar.index)
    }

    @Test
    fun aSwipeOf150dp_isOneEv_whateverTheCamerasStep() {
        assertEquals(10, bar(-20..20, 0.1f).drag(150f).first)
        assertEquals(6, bar(-12..12, 1f / 6f).drag(150f).first)
        assertEquals(-10, bar(-20..20, 0.1f).drag(-150f).first)
    }

    @Test
    fun aShortSwipe_changesLittle_andGoesOnFromWhereItWas() {
        val (index, reported) = bar(-20..20, 0.1f, value = 4).drag(30f)

        // 30 dp is 0.2 EV: two of Samsung's steps from +0.4 EV.
        assertEquals(6, index)
        assertEquals(listOf(6 to false, 6 to true), reported)
    }

    @Test
    fun theBarsLength_changesNothing() {
        val short = bar(-20..20, 0.1f).apply { layout(0, 0, 48, 128) }
        val tall = bar(-20..20, 0.1f).apply { layout(0, 0, 48, 600) }

        assertEquals(short.drag(75f).first, tall.drag(75f).first)
        assertEquals(5, short.index)
    }

    @Test
    fun halfAStepsWay_roundsToTheStep_andJustUnderItStays() {
        // A 0.1 EV step is 15 dp of the way.
        assertEquals(1, bar(-20..20, 0.1f).drag(7.6f).first)
        assertEquals(0, bar(-20..20, 0.1f).drag(7.4f).first)
    }

    @Test
    fun aDragPastTheRangesEnd_stopsThere() {
        assertEquals(20, bar(-20..20, 0.1f).drag(1_000f).first)
        assertEquals(-20, bar(-20..20, 0.1f).drag(-1_000f).first)
    }

    @Test
    fun aCameraWithoutAStep_isLeftAsItIs() {
        val (index, reported) = bar(0..0, 0f).drag(150f)

        assertEquals(0, index)
        assertEquals(emptyList<Pair<Int, Boolean>>(), reported)
    }
}
