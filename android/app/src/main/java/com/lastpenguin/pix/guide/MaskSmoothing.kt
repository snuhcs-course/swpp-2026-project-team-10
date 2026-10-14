// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import kotlin.math.roundToInt

/** Blur radius per pixel of the person's height (Design 2.6.1, step 4): 9 px for a 1100 px tall person. */
internal const val SMOOTH_RADIUS_FRACTION = 0.008f

/** A separate foreground region smaller than this share of all foreground is a speck, not a person (FR-2.5). */
internal const val SPECK_FRACTION = 0.01f

/** A hole in the person smaller than this share of all foreground is noise and is filled. */
internal const val HOLE_FRACTION = 0.002f

/** The blur radius for a person [personHeight] px tall, at least 1. */
internal fun smoothRadius(personHeight: Int): Int = maxOf(1, (personHeight * SMOOTH_RADIUS_FRACTION).roundToInt())

/**
 * Design 2.6.1, step 4: the confidence mask blurred so its 0.5 level is a smooth curve, without specks or pinholes.
 * Three box blurs of [radius] come close to a Gaussian with sigma ≈ [radius]. A region separate from the people that
 * covers less than [SPECK_FRACTION] of the foreground is dropped, and a hole covering less than [HOLE_FRACTION] is
 * filled; holes that reach the photo's edge are background, not holes. Returns a new row-major field in 0..1.
 */
internal fun smoothMask(confidence: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
    require(confidence.size >= width * height) { "${confidence.size} values for ${width}x$height" }
    val field = confidence.copyOf(width * height)
    val scratch = FloatArray(width * height)
    repeat(BLUR_PASSES) {
        boxBlurRows(field, scratch, width, height, radius)
        boxBlurColumns(scratch, field, width, height, radius)
    }
    val foreground = field.count { it >= FOREGROUND_THRESHOLD }
    flipSmallRegions(field, width, height, foreground = true, maxArea = (foreground * SPECK_FRACTION).toInt())
    flipSmallRegions(field, width, height, foreground = false, maxArea = (foreground * HOLE_FRACTION).toInt())
    return field
}

/**
 * A smoothed [value] as alpha 0..255 with a steep ramp around the 0.5 level, so the cutout's edge stays a few pixels
 * soft and the 0.5 level, where the outline is traced, stays exactly where it was. [radius] is the blur radius used.
 */
internal fun smoothedAlpha(value: Float, radius: Int): Int {
    // The blurred edge rises by about 0.4 / radius per pixel; this ramp spreads it over about 3 px.
    val ramp = maxOf(1f, radius * 0.8f)
    return (((value - 0.5f) * ramp + 0.5f).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}

private const val BLUR_PASSES = 3

private fun boxBlurRows(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
    val size = 2 * radius + 1
    for (y in 0 until height) {
        val row = y * width
        var sum = 0f
        for (i in -radius..radius) sum += src[row + i.coerceIn(0, width - 1)]
        for (x in 0 until width) {
            dst[row + x] = sum / size
            sum += src[row + (x + radius + 1).coerceAtMost(width - 1)] - src[row + (x - radius).coerceAtLeast(0)]
        }
    }
}

private fun boxBlurColumns(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
    val size = 2 * radius + 1
    for (x in 0 until width) {
        var sum = 0f
        for (i in -radius..radius) sum += src[i.coerceIn(0, height - 1) * width + x]
        for (y in 0 until height) {
            dst[y * width + x] = sum / size
            sum += src[(y + radius + 1).coerceAtMost(height - 1) * width + x] -
                src[(y - radius).coerceAtLeast(0) * width + x]
        }
    }
}

/**
 * Turns every 4-connected region on the [foreground] side of the 0.5 level that is smaller than [maxArea] to the other
 * side. Background regions touching the photo's edge are left alone: they are the background, not holes.
 */
private fun flipSmallRegions(field: FloatArray, width: Int, height: Int, foreground: Boolean, maxArea: Int) {
    if (maxArea <= 0) return
    val visited = BooleanArray(field.size)
    val queue = IntArray(field.size)
    var tail = 0
    fun visit(i: Int) {
        if (visited[i] || (field[i] >= FOREGROUND_THRESHOLD) != foreground) return
        visited[i] = true
        queue[tail++] = i
    }
    for (start in field.indices) {
        if (visited[start]) continue
        tail = 0
        visit(start)
        if (tail == 0) continue
        var head = 0
        var touchesEdge = false
        while (head < tail) {
            val i = queue[head++]
            val x = i % width
            val y = i / width
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesEdge = true
            if (x > 0) visit(i - 1)
            if (x < width - 1) visit(i + 1)
            if (y > 0) visit(i - width)
            if (y < height - 1) visit(i + width)
        }
        if (tail >= maxArea || (!foreground && touchesEdge)) continue
        val flipped = if (foreground) 0f else 1f
        for (k in 0 until tail) field[queue[k]] = flipped
    }
}
