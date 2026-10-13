// AI-generated with Claude Code, 2026-10-13, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * View-scoped pinch input for camera zoom (AD-17): a pinch on the Camera preview or the subject's live video. The
 * requested ratio starts at the zoom shown when the pinch begins and accumulates locally, so asynchronous
 * observations never move it under the fingers; each step is reported with `final = false` and the last value once
 * with `final = true` when the fingers lift, the gesture is cancelled, or the screen stops accepting input.
 *
 * [enabled] is false while guide-edit mode is on: the overlay above owns every touch then, and whatever still
 * reaches the view is left alone. Shared by the Camera screen and Subject view.
 */
internal class PinchZoomController(
    view: View,
    /** The zoom range while zooming is possible, or null; read when a pinch begins and at every step. */
    private val range: () -> ClosedFloatingPointRange<Float>?,
    /** The zoom to start from: the applied ratio on Camera, the shown ratio on Subject view. */
    private val current: () -> Float,
    private val onZoom: (ratio: Float, final: Boolean) -> Unit,
) {
    var isTracking: Boolean = false
        private set
    private var requested = 1f

    /** While false, touches pass through untouched; a pinch in progress is finished first. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) finishInteraction()
        }

    private val detector = ScaleGestureDetector(
        view.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val range = range() ?: return false
                requested = current().takeIf { it.isFinite() }?.coerceIn(range) ?: range.start
                isTracking = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isTracking) return false
                val range = range()
                if (range == null) {
                    finishInteraction()
                    return false
                }
                val factor = detector.scaleFactor
                if (!factor.isFinite() || factor <= 0f) return false
                requested = (requested * factor).coerceIn(range)
                onZoom(requested, false)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) = finishInteraction()
        },
    ).apply {
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }

    init {
        handleTouch(view)
    }

    @SuppressLint("ClickableViewAccessibility") // The readout keeps the Zoom in / Zoom out accessibility actions.
    private fun handleTouch(view: View) {
        view.setOnTouchListener { _, event ->
            if (!enabled) return@setOnTouchListener false
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                finishInteraction()
            }
            // Keep the whole sequence, so the second finger reaches the detector.
            true
        }
    }

    /** Commit the last requested value once; safe to call when no pinch is in progress. */
    fun finishInteraction() {
        if (!isTracking) return
        isTracking = false
        onZoom(requested, true)
    }
}
