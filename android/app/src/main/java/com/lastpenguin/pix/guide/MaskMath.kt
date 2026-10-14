// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

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
