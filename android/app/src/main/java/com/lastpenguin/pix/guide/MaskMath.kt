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

/** Confidence 0..1 as ALPHA_8 bytes 0..255, [rowBytes] bytes per row as the bitmap stores them. */
internal fun confidenceToAlpha(confidence: FloatArray, width: Int, height: Int, rowBytes: Int = width): ByteArray {
    require(rowBytes >= width) { "rowBytes $rowBytes is narrower than $width" }
    val alpha = ByteArray(rowBytes * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val value = (confidence[y * width + x].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            alpha[y * rowBytes + x] = value.toByte()
        }
    }
    return alpha
}
