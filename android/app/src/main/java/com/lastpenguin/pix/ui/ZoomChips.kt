package com.lastpenguin.pix.ui

import android.view.View
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lastpenguin.pix.R
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * One chip per zoom stop in a [ChipGroup], with the chip for the applied zoom checked (FR-1.3, FR-7.1).
 * Chips are rebuilt only when the stops change; every chip keeps the 48 dp touch target (NFR-16).
 */
object ZoomChips {

    private const val MATCH = 0.01f
    private val format = DecimalFormat("0.#")

    fun render(group: ChipGroup, stops: List<Float>, zoom: Float, enabled: Boolean, onSelect: (Float) -> Unit) {
        if (group.tag != stops) {
            group.tag = stops
            group.removeAllViews()
            for (stop in stops) {
                group.addView(
                    Chip(group.context).apply {
                        id = View.generateViewId()
                        tag = stop
                        text = group.context.getString(R.string.zoom_ratio, format.format(stop))
                        isCheckable = true
                        setEnsureMinTouchTargetSize(true)
                        setOnClickListener { onSelect(stop) }
                    },
                )
            }
        }
        for (index in 0 until group.childCount) {
            val chip = group.getChildAt(index) as Chip
            chip.isEnabled = enabled
            chip.isChecked = abs((chip.tag as Float) - zoom) < MATCH
        }
    }
}
