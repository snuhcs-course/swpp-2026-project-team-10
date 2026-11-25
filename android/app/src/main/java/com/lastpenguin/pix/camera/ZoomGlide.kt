// AI-generated with Claude Code, 2026-11-25, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * A tap's way from one zoom to another (FR-1.3, #310): [DURATION_MS] long, fastest at the tap and slow into the step.
 * It runs along the logarithm of the ratio, where equal steps enlarge the picture by equal shares, so the picture
 * grows as evenly to the eye from 1× to 2× as from 2× to 4×. Times are in nanoseconds, as the display's frames give
 * them.
 * Owner: Camera/Overlay (#310).
 */
internal class ZoomGlide(val from: Float, val to: Float, private val startNanos: Long) {

    /** The ratio at [nowNanos]: [from] until the start, and [to] itself from the end on. */
    fun at(nowNanos: Long): Float {
        val elapsed = nowNanos - startNanos
        if (elapsed >= DURATION_NANOS) return to
        if (elapsed <= 0) return from
        // A quarter of a sine wave: its full speed from the first frame, so the picture answers the tap at once, and
        // none left at the step. A slow start would add to the frames the camera takes to show a new zoom (#310).
        val eased = sin(Math.PI / 2 * elapsed / DURATION_NANOS)
        val start = ln(from.toDouble())
        return exp(start + (ln(to.toDouble()) - start) * eased).toFloat()
    }

    /** Whether the glide is over at [nowNanos]. */
    fun endsBy(nowNanos: Long): Boolean = nowNanos - startNanos >= DURATION_NANOS

    companion object {
        const val DURATION_MS = 250L
        private const val DURATION_NANOS = DURATION_MS * 1_000_000L

        /** The least step that shows as a glide, on the logarithm: a hundredth of the ratio. */
        private val LEAST_STEP = ln(1.01)

        /** Whether the step from [from] to [to] shows as a glide: more than a hundredth of the ratio. */
        fun worth(from: Float, to: Float): Boolean =
            from > 0f && to > 0f && from.isFinite() && to.isFinite() &&
                abs(ln(to.toDouble() / from.toDouble())) > LEAST_STEP
    }
}
