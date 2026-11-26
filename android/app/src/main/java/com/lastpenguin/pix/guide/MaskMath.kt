// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.guide

import kotlin.math.roundToInt

/** A pixel counts as the person from this confidence on (Design 2.6.1, steps 3–5). */
internal const val FOREGROUND_THRESHOLD = 0.5f

/** Pixels with confidence ≥ [FOREGROUND_THRESHOLD]: their bounding box (right and bottom exclusive) and count. */
internal data class MaskBounds(val left: Int, val top: Int, val right: Int, val bottom: Int, val count: Int)

/** The foreground of a row-major confidence mask, or null if no pixel reaches [threshold]. */
internal fun foregroundBounds(
    confidence: FloatArray,
    width: Int,
    height: Int,
    threshold: Float = FOREGROUND_THRESHOLD,
): MaskBounds? {
    require(confidence.size >= width * height) { "${confidence.size} values for ${width}x$height" }
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    var count = 0
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if (confidence[row + x] < threshold) continue
            count++
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            bottom = y
        }
    }
    return if (count == 0) null else MaskBounds(left, top, right + 1, bottom + 1, count)
}

/**
 * The inside of a guide's smoothed [mask] (#271), as guide-edit mode tints it: opaque above the outline's level
 * ([FOREGROUND_THRESHOLD]) and clear below it, with a short ramp across the level for a smooth edge, so the tint ends
 * where the outline is drawn.
 */
internal fun guideInside(mask: MaskPixels): MaskPixels {
    val level = FOREGROUND_THRESHOLD * 255f
    val alpha = IntArray(mask.alpha.size) { k ->
        (((mask.alpha[k] - level) / INSIDE_RAMP + 0.5f) * 255f).roundToInt().coerceIn(0, 255)
    }
    return MaskPixels(mask.width, mask.height, alpha)
}

/** How many mask values the inside's edge takes to go from clear to opaque. */
private const val INSIDE_RAMP = 32f
