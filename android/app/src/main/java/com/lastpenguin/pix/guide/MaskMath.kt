// AI-generated with Claude Code, 2026-10-05, reviewed by Dongje Park
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

/** [FOREGROUND_THRESHOLD] on the 0..255 alpha scale that [confidenceToAlpha] writes; 0.5 rounds to 128. */
internal const val FOREGROUND_ALPHA = 128

/**
 * Foreground pixels (alpha ≥ [threshold]) with at least one background pixel among their 8 neighbors (Design 2.6.1,
 * step 5). Pixels outside the image do not count as background, so a person cut off by the photo's edge stays open
 * there instead of getting a line along the border.
 */
internal fun maskEdges(alpha: IntArray, width: Int, height: Int, threshold: Int = FOREGROUND_ALPHA): BooleanArray {
    require(alpha.size >= width * height) { "${alpha.size} values for ${width}x$height" }
    val edges = BooleanArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (alpha[y * width + x] < threshold) continue
            edges[y * width + x] = hasBackgroundNeighbor(alpha, width, height, x, y, threshold)
        }
    }
    return edges
}

private fun hasBackgroundNeighbor(alpha: IntArray, width: Int, height: Int, x: Int, y: Int, threshold: Int): Boolean {
    for (ny in maxOf(y - 1, 0)..minOf(y + 1, height - 1)) {
        for (nx in maxOf(x - 1, 0)..minOf(x + 1, width - 1)) {
            if (alpha[ny * width + nx] < threshold) return true
        }
    }
    return false
}

/** Thickens [pixels] to a round stroke reaching [radius] px past every set pixel; 0 leaves them unchanged. */
internal fun dilate(pixels: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
    if (radius <= 0) return pixels.copyOf()
    val out = BooleanArray(width * height)
    // r² + r keeps the rasterized disc round: a full 3×3 block for radius 1.
    val limit = radius * radius + radius
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!pixels[y * width + x]) continue
            for (dy in -radius..radius) {
                val ny = y + dy
                if (ny !in 0 until height) continue
                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until width && dx * dx + dy * dy <= limit) out[ny * width + nx] = true
                }
            }
        }
    }
    return out
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
