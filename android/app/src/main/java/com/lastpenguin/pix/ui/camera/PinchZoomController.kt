// AI-generated with Claude Code, 2026-11-05, reviewed by Dongje Park and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * View-scoped pinch input for camera zoom (AD-17): a pinch on the Camera preview or the subject's live video. The
 * requested ratio starts at the zoom shown when the pinch begins and accumulates locally, so asynchronous
 * observations never move it under the fingers; each step is reported with `final = false` and the last value once
 * with `final = true` when the fingers lift, the gesture is cancelled, or the screen stops accepting input.
 *
 * [enabled] is false while guide-edit mode is on: the overlay above owns every touch then, and whatever still
 * reaches the view is left alone. Shared by the Camera screen and Subject view. Since this controller owns the
 * view's touches, it also reports a single tap to [onTap] (tap to focus on Camera, FR-1.4); a pinch is never a tap.
 * A single finger that moves up or down past the touch slop is a [VerticalDrag] instead (brightness on Camera,
 * FR-1.11); a second finger ends it, and the pinch goes on from there.
 */
internal class PinchZoomController(
    view: View,
    /** The zoom range while zooming is possible, or null; read when a pinch begins and at every step. */
    private val range: () -> ClosedFloatingPointRange<Float>?,
    /** The zoom to start from: the applied ratio on Camera, the shown ratio on Subject view. */
    private val current: () -> Float,
    /** A single-finger tap at view coordinates, or null for a view without taps. */
    private val onTap: ((x: Float, y: Float) -> Unit)? = null,
    /** A single finger's drag up or down, or null for a view without one. */
    private val onDrag: VerticalDrag? = null,
    private val onZoom: (ratio: Float, final: Boolean) -> Unit,
) {
    /** One finger moving up or down on the view, which the pinch's owner turns into something else. */
    interface VerticalDrag {
        /** The finger went down at [x], [y] and moved up or down past the slop; false leaves the touch alone. */
        fun onStart(x: Float, y: Float): Boolean

        /** The finger is [up] pixels above where it went down; negative below. */
        fun onMove(up: Float)

        /** The finger lifted, a second one came, or the touch was cancelled. */
        fun onEnd()
    }

    var isTracking: Boolean = false
        private set
    private var requested = 1f
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    // Until the drag starts, or once a pinch or a sideways move rules it out, the touch is not a drag.
    private var dragging = false
    private var dragRuledOut = false

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

    // A second finger cancels the tap, so the end of a pinch is never reported as one.
    private val tapDetector = onTap?.let { tap ->
        GestureDetector(
            view.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!isTracking) tap(e.x, e.y)
                    return true
                }
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility") // The readout keeps the Zoom in / Zoom out accessibility actions.
    private fun handleTouch(view: View) {
        view.setOnTouchListener { _, event ->
            if (!enabled) return@setOnTouchListener false
            val wasDragging = dragging
            trackDrag(event)
            detector.onTouchEvent(event)
            // A drag is not a tap either, up to the finger's lift that ends it; the detector then starts again at the next
            // touch's down.
            if (!dragging && !wasDragging) tapDetector?.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                finishInteraction()
            }
            // Keep the whole sequence, so the second finger reaches the detector.
            true
        }
    }

    private fun trackDrag(event: MotionEvent) {
        val drag = onDrag ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                dragRuledOut = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                dragRuledOut = true
                endDrag()
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    drag.onMove(downY - event.y)
                } else if (!dragRuledOut && !isTracking && event.pointerCount == 1) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dy > slop && dy > dx) {
                        dragging = drag.onStart(downX, downY)
                        dragRuledOut = !dragging
                        if (dragging) drag.onMove(downY - event.y)
                    } else if (dx > slop) {
                        dragRuledOut = true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
    }

    private fun endDrag() {
        if (!dragging) return
        dragging = false
        onDrag?.onEnd()
    }

    /** Commit the last requested value once, and end a drag; safe to call when neither is in progress. */
    fun finishInteraction() {
        endDrag()
        if (!isTracking) return
        isTracking = false
        onZoom(requested, true)
    }
}
