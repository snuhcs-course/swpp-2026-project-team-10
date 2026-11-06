// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The brightness slider's scale, shared by the Camera screen and Subject view (FR-1.11, FR-7.3). The slider runs
 * from 1, because [TrackedSliderController] takes positive values, and each step is one exposure-compensation
 * index; labels and notices show an index in EV. Main thread only, like the views it serves.
 */
internal object ExposureScale {
    private val evFormat = DecimalFormat("+0.0;-0.0")

    /** The slider's top value for [range]: one position per index. */
    fun sliderMax(range: IntRange): Float = (range.last - range.first + 1).toFloat()

    /** The slider position of an exposure [index]. */
    fun sliderValue(index: Int, range: IntRange): Float = (index - range.first + 1).toFloat()

    /** The exposure index of a slider position. */
    fun index(value: Float, range: IntRange): Int = value.roundToInt() - 1 + range.first

    /** The exposure index nearest [ev] for a camera of [step] EV per index, within its [range] (the base brightness). */
    fun indexOf(ev: Float, step: Float, range: IntRange): Int =
        if (step <= 0f || !ev.isFinite()) 0.coerceIn(range) else (ev / step).roundToInt().coerceIn(range)

    /** An EV value, such as "+0.7" or "0". */
    fun formatEv(ev: Float): String = if (abs(ev) < 0.05f) "0" else evFormat.format(ev)

    /** An exposure index in EV, such as "+0.7" or "0". */
    fun formatEv(index: Int, step: Float): String = formatEv(index * step)
}
