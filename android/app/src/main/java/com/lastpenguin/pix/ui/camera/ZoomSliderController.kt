package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import com.google.android.material.slider.Slider
import com.lastpenguin.pix.R
import java.text.DecimalFormat

/** View-scoped zoom input shared by the camera and remote controls. Values are camera ratios, not percentages. */
internal class ZoomSliderController(
    private val slider: Slider,
    private val onZoomChange: (Float, Boolean) -> Unit,
) {
    var isTracking: Boolean = false
        private set
    private var changed = false
    private var handlingTouch = false
    private val format = DecimalFormat("0.0#")

    init {
        slider.stepSize = 0f
        slider.setLabelFormatter { slider.context.getString(R.string.zoom_ratio, format.format(it)) }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && slider.isEnabled && (!handlingTouch || isTracking)) {
                changed = isTracking
                // Keyboard and accessibility changes have no touch-stop callback: commit them immediately.
                onZoomChange(value, !isTracking)
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

    fun render(minZoom: Float, maxZoom: Float, zoom: Float, enabled: Boolean) {
        val validRange = minZoom.isFinite() && maxZoom.isFinite() && minZoom > 0f && maxZoom > minZoom
        // Material requires a strictly increasing range even while disabled and before capabilities arrive.
        val minimum = if (validRange) minZoom else 1f
        val maximum = if (validRange) maxZoom else 2f
        val canZoom = enabled && validRange
        val rangeChanged = slider.valueFrom != minimum || slider.valueTo != maximum
        if (isTracking && (!canZoom || rangeChanged)) finishInteraction()
        slider.isEnabled = canZoom
        slider.valueFrom = minimum
        slider.valueTo = maximum
        if (!isTracking) {
            slider.value = (zoom.takeIf { it.isFinite() } ?: minimum).coerceIn(minimum, maximum)
        }
    }

    /** Commit the last requested value on release, cancellation, or when the screen stops accepting input. */
    fun finishInteraction() {
        val publish = isTracking && changed
        isTracking = false
        changed = false
        if (publish) onZoomChange(slider.value, true)
    }
}
