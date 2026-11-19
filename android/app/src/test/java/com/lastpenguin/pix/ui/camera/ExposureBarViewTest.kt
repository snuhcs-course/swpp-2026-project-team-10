// AI-generated with Claude Code, 2026-11-18, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/*
 * How far a drag moves the brightness (FR-1.11, Design 2.8): 1 EV for each `ExposureBarView.DP_PER_EV` of the finger's
 * way, as slowly as an iPhone's sun, whatever the camera's step or the bar's length.
 * Partitions (equivalence): a camera of 0.1 EV steps (Samsung's ±2 EV in 41) and one of 1/6 EV (Pixel's ±2 EV in 25);
 * a drag up and one down; a bar laid out short, tall, and not at all. Boundary: half a step's way, which rounds to the
 * step, and just under it; a drag past the range's end, which stops there; a camera without a step, which a drag leaves.
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
