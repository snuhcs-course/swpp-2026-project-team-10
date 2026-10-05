package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import com.google.android.material.slider.Slider
import com.lastpenguin.pix.R
import java.text.DecimalFormat

/**
 * View-scoped slider input: the thumb stays under the user's control during a drag, and the last value is committed
 * once with `final = true`. Shared by camera zoom, remote zoom, and guide opacity.
 */
internal class TrackedSliderController(
    private val slider: Slider,
    formatLabel: (Float) -> String,
    private val onChange: (Float, Boolean) -> Unit,
) {
    var isTracking: Boolean = false
        private set
    private var changed = false
    private var handlingTouch = false
    init {
        slider.stepSize = 0f
        slider.setLabelFormatter(formatLabel)
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && slider.isEnabled && (!handlingTouch || isTracking)) {
                changed = isTracking
                // Keyboard and accessibility changes have no touch-stop callback: commit them immediately.
                onChange(value, !isTracking)
            }
        }
        handleTouch()
    }

    @SuppressLint("ClickableViewAccessibility") // Material Slider handles clicks, keyboard input, and accessibility.
    private fun handleTouch() {
        slider.setOnTouchListener { _, event ->
            // Material can change the value before its own onStartTrackingTouch callback, even on a track tap.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                isTracking = true
                changed = false
            }
            val interrupted = !isTracking
            val retainedValue = slider.value
            handlingTouch = true
            try {
                slider.onTouchEvent(event)
            } finally {
                handlingTouch = false
            }
            // Let Material release its pressed state, without moving the thumb for an already ended interaction.
            if (interrupted) slider.value = retainedValue
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                finishInteraction()
            }
            true
        }
    }

    /** Zoom ratios must be positive; an invalid range (before capabilities arrive) renders disabled at 1–2. */
    fun render(min: Float, max: Float, value: Float, enabled: Boolean) {
        val validRange = min.isFinite() && max.isFinite() && min > 0f && max > min
        // Material requires a strictly increasing range even while disabled and before capabilities arrive.
        val minimum = if (validRange) min else 1f
        val maximum = if (validRange) max else 2f
        val canChange = enabled && validRange
        val rangeChanged = slider.valueFrom != minimum || slider.valueTo != maximum
        if (isTracking && (!canChange || rangeChanged)) finishInteraction()
        slider.isEnabled = canChange
        slider.valueFrom = minimum
        slider.valueTo = maximum
        if (!isTracking) {
            slider.value = (value.takeIf { it.isFinite() } ?: minimum).coerceIn(minimum, maximum)
        }
    }

    /** Commit the last requested value on release, cancellation, or when the screen stops accepting input. */
    fun finishInteraction() {
        val publish = isTracking && changed
        isTracking = false
        changed = false
        if (publish) onChange(slider.value, true)
    }

    companion object {
        private val zoomFormat = DecimalFormat("0.0#")

        /** Values are camera ratios, not percentages. */
        fun zoom(slider: Slider, onZoomChange: (Float, Boolean) -> Unit) = TrackedSliderController(
            slider,
            { slider.context.getString(R.string.zoom_ratio, zoomFormat.format(it)) },
            onZoomChange,
        )
    }
}
