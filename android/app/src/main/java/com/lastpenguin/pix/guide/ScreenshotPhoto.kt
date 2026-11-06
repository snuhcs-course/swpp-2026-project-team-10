// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

import kotlin.math.abs
import kotlin.math.max

/** A pixel is the app's background within this of its color on each channel: JPEG's noise, not a photo's sky. */
internal const val BACKGROUND_TOLERANCE = 12

/** A row is blank when this much of it is the background; the rest is a JPEG's speck. */
private const val BLANK_FRACTION = 0.99f

/** An app's flat background fills at least this part of the rows; a photo filling the screen has none. */
private const val MIN_BACKGROUND_ROWS = 0.05f

/** A margin's column has its color through this much of the block, so an icon over it, as Google Lens', is let by. */
private const val MARGIN_FRACTION = 0.9f

/**
 * The photo in a phone's screenshot (FR-2.10, Design 2.6.1, step 1), such as an Instagram post's, one opened in Google
 * Images or Naver, or a post shared in a story. The photo is a block of rows between rows of the app's flat background,
 * light or dark, without the margins of one color an image viewer centers it between; in a story's frame, it is the
 * card of a shared post when there is one ([SharedPostCard]). [pixels] are [width]×[height] ARGB, row-major. The outer
 * 2 % on each side, where a browser draws its scrollbar, does not decide whether a row is blank.
 * Null, and the image is used whole, for anything else: an image no taller than 1.9:1 (a camera's 16:9 photo is 1.78),
 * one without a flat background, a photo narrower than 60 % of the screen, shorter than half its width or taller than
 * twice it (a 9:16 story is 1.78), split by a column of background (a profile grid, photos side by side), or touching
 * the screen's top or bottom, or no photo that stands out. Rows of a white sky within [BACKGROUND_TOLERANCE] of a white
 * app count as the app. Plain Kotlin on the phone, so the screenshot never leaves it (FR-2.6).
 */
internal fun findScreenshotPhoto(pixels: IntArray, width: Int, height: Int): PixelRect? {
    require(pixels.size >= width * height) { "${pixels.size} pixels for ${width}x$height" }
    if (height * 10 <= width * 19) return null
    return Screenshot(pixels, width, height).photo()
}

private class Screenshot(private val pixels: IntArray, private val width: Int, private val height: Int) {

    /** The outer 2 % on each side: a scrollbar there must not join the rows above a photo to it. */
    private val edge = width / 50

    fun photo(): PixelRect? {
        val background = background() ?: return null
        val blank = BooleanArray(height) { y -> isBlank(y, background) }
        val photos = blocks(blank).mapNotNull { photoIn(it, background) }
            .map { photo -> if (isStoryFrame(photo)) sharedPostIn(photo) ?: photo else photo }
            .sortedByDescending { it.height }
        val largest = photos.firstOrNull() ?: return null
        // Two photos of a similar size: which one was meant is not clear, so neither.
        val next = photos.getOrNull(1)
        if (next != null && 5 * next.height >= 4 * largest.height) return null
        return largest
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
        for (x in edge until width - edge) if (close(pixels[row + x], background)) same++
        return same >= BLANK_FRACTION * (width - 2 * edge)
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

    /**
     * The photo in a block, between the margins a viewer centers it between; null when the block touches the screen's
     * top or bottom, or is not one photo of a photo's size and shape.
     */
    private fun photoIn(block: Rows, background: Int): PixelRect? {
        if (block.top == 0 || block.bottom == height) return null
        val sides = sides(block)
        val photo = PixelRect(sides.left, block.top, sides.right, block.bottom)
        if (!hasPhotoShape(photo) || hasGap(photo, background, sides.margin)) return null
        return photo
    }

    /**
     * The photo's sides: inside margins of one color, trimmed from both sides at once while both are that color, since
     * a viewer centers the photo. A flat area on one side only, such as a plain wall, is the photo's. A block that keeps
     * its outer 2 % reaches the screen's side.
     */
    private fun sides(block: Rows): Sides {
        var left = edge
        var right = width - edge
        var margin: Int? = null
        while (left < right) {
            val color = columnColor(left, block) ?: break
            val other = columnColor(right - 1, block) ?: break
            if (!close(color, other) || (margin != null && !close(color, margin))) break
            if (margin == null) margin = color
            left++
            right--
        }
        if (margin != null) {
            while (left < right && isColor(columnColor(left, block), margin)) left++
            while (right > left && isColor(columnColor(right - 1, block), margin)) right--
        }
        return Sides(if (left == edge) 0 else left, if (right == width - edge) width else right, margin)
    }

    /** The color column [x] has through [MARGIN_FRACTION] of the block's rows, or null when it has none. */
    private fun columnColor(x: Int, block: Rows): Int? {
        val rows = block.bottom - block.top
        val samples = listOf(block.top + rows / 4, block.top + rows / 2, block.top + rows * 3 / 4)
        return samples.map { pixels[it * width + x] }.firstOrNull { color ->
            var same = 0
            for (y in block.top until block.bottom) if (close(pixels[y * width + x], color)) same++
            same >= MARGIN_FRACTION * rows
        }
    }

    /** At least 60 % of the screen's width, and half to twice as tall as wide: 1.91:1 up to a 9:16 story. */
    private fun hasPhotoShape(photo: PixelRect): Boolean =
        5 * photo.width >= 3 * width && 2 * photo.height >= photo.width && photo.height <= 2 * photo.width

    /**
     * A column of the background or of the margins' color through the photo's rows, in its middle three fifths: a
     * grid's spacing or the space between photos side by side. A white wall at the photo's edge is not one.
     */
    private fun hasGap(photo: PixelRect, background: Int, margin: Int?): Boolean =
        (photo.left + photo.width / 5 until photo.right - photo.width / 5).any { x ->
            var same = 0
            for (y in photo.top until photo.bottom) {
                val pixel = pixels[y * width + x]
                if (close(pixel, background) || isColor(pixel, margin)) same++
            }
            same >= BLANK_FRACTION * photo.height
        }

    /** The card of a post shared in a story's [frame], when it has a photo's size and shape. */
    private fun sharedPostIn(frame: PixelRect): PixelRect? =
        SharedPostCard(pixels, width, frame).find()?.takeIf(::hasPhotoShape)

    /** A story's frame: the whole width, at least 1.7 times as tall. */
    private fun isStoryFrame(photo: PixelRect): Boolean =
        photo.left == 0 && photo.right == width && 10 * photo.height >= 17 * width

    private class Rows(val top: Int, val bottom: Int)

    /** A photo's [left] and [right] sides, and the color of the margins trimmed beside it, if any. */
    private class Sides(val left: Int, val right: Int, val margin: Int?)
}

private fun isColor(color: Int?, other: Int?): Boolean = color != null && other != null && close(color, other)

private fun close(a: Int, b: Int): Boolean = colorStep(a, b) <= BACKGROUND_TOLERANCE

/** The largest difference between two ARGB colors on a channel. */
internal fun colorStep(a: Int, b: Int): Int = max(
    abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
    max(abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)), abs((a and 0xFF) - (b and 0xFF))),
)
