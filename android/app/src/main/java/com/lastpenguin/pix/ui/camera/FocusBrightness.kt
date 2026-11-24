// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.graphics.PointF
import android.view.View
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The focus box at a tap and the brightness bar beside it (FR-1.4, FR-1.11, Design 2.8), the same on Camera's preview
 * and on Subject view's video (FR-7.3, FR-7.7): where the box goes and how it shrinks and fades, where the bar stands,
 * and how long both stay. The screen keeps whether the bar is open in its ViewModel's state and shows it through
 * [show]; this keeps what belongs to the views: the tap the bar stands beside, and the time without a touch after
 * which [onIdle] asks the screen to close it.
 */
internal class FocusBrightness(
    private val box: View,
    private val bar: ExposureBarView,
    /** The screen's view scope, which the bar's idle time runs in. */
    private val scope: CoroutineScope,
    private val onIdle: () -> Unit,
) {
    // Where the bar stands beside: the last tap, or the middle of the image's right edge (null).
    private var anchor: PointF? = null
    private var idleJob: Job? = null
    private var barHiding = false

    /** The box is beside the bar, so it stays as long as the bar does and goes with it. */
    private var boxWithBar = false

    init {
        // The bar stays up while a finger is on it.
        bar.onTouching = { touching -> if (touching) idleJob?.cancel() else hold() }
    }

    /**
     * A tap at [x], [y] of the view that holds the box and the bar: the box there on a camera that focuses on a point
     * ([focuses]), and, with a brightness range ([brightness]), the place the bar stands beside. The screen then
     * opens the bar, places it ([place]), and starts its time ([hold]).
     */
    fun tap(x: Float, y: Float, focuses: Boolean, brightness: Boolean) {
        // A box left beside the bar by an earlier tap goes when this camera shows none, as the bar moves on alone.
        if (boxWithBar && !focuses) fadeBox(delay = 0L, duration = BOX_FADE_MS)
        boxWithBar = focuses && brightness
        if (focuses) showBox(x, y, alone = !brightness)
        // Brightness is set beside where the camera focused, or where the tap was on a camera that cannot focus on a
        // point.
        if (brightness) anchor = PointF(x, y)
    }

    /**
     * The box at [x], [y] by itself, for a tap on the other phone (FR-7.7). A bar that is up is no longer beside the
     * focus: the screen closes it after this, and the box shown anew stays its own time.
     */
    fun showBoxAlone(x: Float, y: Float) {
        boxWithBar = false
        idleJob?.cancel()
        showBox(x, y, alone = true)
    }

    /**
     * The bar beside the box in the image's [frame]: on the box's right, or its left near the image's right edge, and
     * inside the image; at the middle of the image's right edge without a tap.
     */
    fun place(frame: ViewfinderFrame) {
        if (frame.width <= 0f || frame.height <= 0f) return
        val width = bar.layoutParams.width.toFloat()
        val height = bar.layoutParams.height.toFloat()
        val anchor = anchor
        if (anchor == null) {
            // At the middle of the image's right edge, clear of the horizon bar across its middle.
            bar.translationX = maxOf(frame.left, frame.right - width)
            bar.translationY = frame.centerY - height / 2f
            return
        }
        val half = box.layoutParams.width / 2f
        // The bar's line is in the middle of its 48 dp touch area, so the area reaches a little over the box.
        val overlap = BAR_OVERLAP_DP * bar.resources.displayMetrics.density
        val right = anchor.x + half - overlap
        val left = if (right + width <= frame.right) right else anchor.x - half + overlap - width
        bar.translationX = left.coerceIn(frame.left, maxOf(frame.left, frame.right - width))
        bar.translationY = (anchor.y - height / 2f).coerceIn(frame.top, maxOf(frame.top, frame.bottom - height))
    }

    /** Shows the bar at once, or fades it out, and the box beside it with it. */
    fun show(open: Boolean) {
        if (open) {
            if (barHiding || !bar.isVisible) {
                barHiding = false
                bar.animate().cancel()
                bar.alpha = 1f
                bar.isVisible = true
            }
        } else {
            if (bar.isVisible && !barHiding) {
                bar.finishDrag()
                barHiding = true
                bar.animate().alpha(0f).setDuration(BAR_FADE_MS).withEndAction {
                    barHiding = false
                    bar.isVisible = false
                }
            }
            // The box beside it fades out with it, for any reason the bar goes.
            if (boxWithBar) {
                boxWithBar = false
                fadeBox(delay = 0L, duration = BAR_FADE_MS)
            }
        }
    }

    /** The bar goes again after [IDLE_MS] without a touch on it. */
    fun hold() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            if (!bar.isHeld) onIdle()
        }
    }

    /** A drag of the bar ends with its value, as when the screen pauses under the finger. */
    fun finishDrag() = bar.finishDrag()

    /** The screen's view is going: a drag ends with its value, and nothing is kept for the next view. */
    fun clear() {
        idleJob?.cancel()
        idleJob = null
        bar.finishDrag()
        anchor = null
        barHiding = false
        boxWithBar = false
    }

    /**
     * The box at the tap, shrinking into place. [alone], without the bar, it fades by itself after a moment; beside
     * the bar it stays until the bar goes, and goes with it ([show]).
     */
    private fun showBox(x: Float, y: Float, alone: Boolean) {
        box.animate().cancel()
        box.translationX = x - box.layoutParams.width / 2f
        box.translationY = y - box.layoutParams.height / 2f
        box.alpha = 1f
        box.scaleX = BOX_START_SCALE
        box.scaleY = BOX_START_SCALE
        box.isVisible = true
        // The start delay stays with the view's animator, so a fade before would hold the shrink back.
        box.animate().scaleX(1f).scaleY(1f).setStartDelay(0L).setDuration(BOX_SHRINK_MS).withEndAction {
            if (alone) fadeBox(delay = BOX_HOLD_MS, duration = BOX_FADE_MS)
        }
    }

    private fun fadeBox(delay: Long, duration: Long) {
        box.animate().cancel()
        box.animate().alpha(0f).setStartDelay(delay).setDuration(duration).withEndAction { box.isVisible = false }
    }

    companion object {
        /** How long the bar stays up without a touch (FR-1.11). */
        const val IDLE_MS = 3_000L

        private const val BOX_START_SCALE = 1.25f
        private const val BOX_SHRINK_MS = 150L
        private const val BOX_HOLD_MS = 700L
        private const val BOX_FADE_MS = 200L
        private const val BAR_OVERLAP_DP = 12f
        private const val BAR_FADE_MS = 200L
    }
}
