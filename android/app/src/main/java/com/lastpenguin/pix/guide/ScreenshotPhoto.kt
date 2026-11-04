// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

import kotlin.math.abs

/** A pixel is the app's background within this of its color on each channel: JPEG's noise, not a photo's sky. */
internal const val BACKGROUND_TOLERANCE = 12

/** A row is blank when this much of it is the background; the rest is a JPEG's speck. */
private const val BLANK_FRACTION = 0.99f

/** An app's flat background fills at least this part of the rows; a story or a photo has none. */
private const val MIN_BACKGROUND_ROWS = 0.05f

/**
 * The photo of a feed post in a phone's screenshot (FR-2.10, Design 2.6.1, step 1): a block of rows that fills the
 * width, between rows of the app's flat background, light or dark. [pixels] are [width]×[height] ARGB, row-major.
 * Null, and the image is used whole, for anything else: an image no taller than 1.9:1 (a camera's 16:9 photo is 1.78),
 * one without a flat background (a story), a block with a column of background in it (the app's margins beside a
 * photo, or a profile grid's spacing), one shorter than 2:1 or taller than 2:3 of the width (Instagram's posts are
 * 1.91:1 to 3:4, a story 9:16), or no block that stands out. The rows are the photo's exact edges; rows of a white sky within [BACKGROUND_TOLERANCE] of a white app count as
 * the app. Plain Kotlin on the phone, so the screenshot never leaves it (FR-2.6).
 */
internal fun findScreenshotPhoto(pixels: IntArray, width: Int, height: Int): PixelRect? {
    require(pixels.size >= width * height) { "${pixels.size} pixels for ${width}x$height" }
    if (height * 10 <= width * 19) return null
    return Screenshot(pixels, width, height).photo()
}

private class Screenshot(private val pixels: IntArray, private val width: Int, private val height: Int) {

    fun photo(): PixelRect? {
        val background = background() ?: return null
        val blank = BooleanArray(height) { y -> isBlank(y, background) }
        val photos = blocks(blank).filter { isPhoto(it, background) }.sortedByDescending { it.bottom - it.top }
        val largest = photos.firstOrNull() ?: return null
        // Two posts of a similar size: which one was meant is not clear, so neither.
        val next = photos.getOrNull(1)
        if (next != null && 5 * (next.bottom - next.top) >= 4 * (largest.bottom - largest.top)) return null
        return PixelRect(0, largest.top, width, largest.bottom)
    }

    /** The color most rows are filled with alone, or null when too few are. */
    private fun background(): Int? {
        val colors = mutableListOf<Int>()
        val rows = mutableListOf<Int>()
        for (y in 0 until height) {
            val color = pixels[y * width + width / 2]
            if (!isBlank(y, color)) continue
            val known = colors.indexOfFirst { close(it, color) }
            if (known < 0) {
                colors += color
                rows += 1
            } else {
                rows[known]++
            }
        }
        val most = rows.indices.maxByOrNull { rows[it] } ?: return null
        return colors[most].takeIf { rows[most] >= MIN_BACKGROUND_ROWS * height }
    }

    private fun isBlank(y: Int, background: Int): Boolean {
        val row = y * width
        var same = 0
        for (x in 0 until width) if (close(pixels[row + x], background)) same++
        return same >= BLANK_FRACTION * width
    }

    /** The runs of rows that are not blank, bottom exclusive. */
    private fun blocks(blank: BooleanArray): List<Rows> {
        val blocks = mutableListOf<Rows>()
        var y = 0
        while (y < height) {
            if (blank[y]) {
                y++
                continue
            }
            val top = y
            while (y < height && !blank[y]) y++
            blocks += Rows(top, y)
        }
        return blocks
    }

    /** A photo's place: 2:1 to 2:3 of the width, the background above and below, and nothing but photo across. */
    private fun isPhoto(block: Rows, background: Int): Boolean {
        val rows = block.bottom - block.top
        if (2 * rows < width || 2 * rows > 3 * width) return false
        if (block.top == 0 || block.bottom == height) return false
        return !hasGap(block, background)
    }

    /**
     * A column of background inside the block, away from its outer 2 %: the app's margins beside a photo that does not
     * fill the width, or the space between a grid's photos.
     */
    private fun hasGap(block: Rows, background: Int): Boolean {
        val margin = width / 50
        val rows = block.bottom - block.top
        return (margin until width - margin).any { x ->
            var same = 0
            for (y in block.top until block.bottom) if (close(pixels[y * width + x], background)) same++
            same >= BLANK_FRACTION * rows
        }
    }

    private class Rows(val top: Int, val bottom: Int)
}

private fun close(a: Int, b: Int): Boolean =
    abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= BACKGROUND_TOLERANCE &&
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= BACKGROUND_TOLERANCE &&
        abs((a and 0xFF) - (b and 0xFF)) <= BACKGROUND_TOLERANCE
