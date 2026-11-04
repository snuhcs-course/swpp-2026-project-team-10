// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

/** A step this large to the left or upper neighbor is detail; a blur has almost none. */
private const val DETAIL_STEP = 10

/** A step this large across the card's side is an edge. */
private const val EDGE_STEP = 12

/** The columns compared on each side of a possible side of the card. */
private const val WINDOW = 8

/** A column outside the card is blurred under this much detail, and at least [BLURRED_SHARE] of them are. */
private const val BLUR_DETAIL = 0.05f

/** A name or a caption over the blur leaves the rest of the columns outside the card blurred. */
private const val BLURRED_SHARE = 0.6f

/** The card has at least this much detail, and this much more than the blur beside it: a plain wall has little. */
private const val MIN_DETAIL = 0.04f

/** Each side of the card is an edge along at least this share of its rows; a person's outline crosses a column at few. */
private const val SIDE_EDGE = 0.2f

/** The card's top and bottom are edges across at least this share of its width. */
private const val TOP_EDGE = 0.2f

/**
 * A post shared in a story (FR-2.10, Design 2.6.1, step 1): a photo card centered in the story's [frame] on a blurred
 * copy of itself, as Instagram shows one. Its left and right sides are where detail starts, at the same distance from
 * both of the frame's sides, and straight edges run along part of them; its top and bottom, at the same distance from
 * the frame's, are where its horizontal edges are strongest. The edges are faint where the photo meets its own blurred
 * copy, so only part of each needs one. Null, and the frame is the photo, when there is no such card.
 */
internal class SharedPostCard(private val pixels: IntArray, private val width: Int, private val frame: PixelRect) {

    fun find(): PixelRect? {
        val side = sideMargin() ?: return null
        val top = topMargin(frame.left + side, frame.right - side) ?: return null
        val card = PixelRect(frame.left + side, frame.top + top, frame.right - side, frame.bottom - top)
        return card.takeIf { minOf(sideEdge(card, card.left), sideEdge(card, card.right)) >= SIDE_EDGE }
    }

    /** The margin on both sides where detail starts: blurred outside, sharp inside; null without a clear one. */
    private fun sideMargin(): Int? {
        val detail = columnDetail()
        val n = detail.size
        var best = -1
        var bestScore = 0f
        for (m in maxOf(WINDOW, n / 50) until n * 45 / 100) {
            val inside = detail.mean(m, m + WINDOW) + detail.mean(n - m - WINDOW, n - m)
            val outside = detail.mean(m - WINDOW, m) + detail.mean(n - m, n - m + WINDOW)
            if (inside - outside > bestScore) {
                best = m
                bestScore = inside - outside
            }
        }
        if (best < 0 || bestScore < MIN_DETAIL || detail.mean(best, n - best) < MIN_DETAIL) return null
        val outside = (0 until best) + (n - best until n)
        val blurred = outside.count { detail[it] < BLUR_DETAIL }
        if (blurred < BLURRED_SHARE * outside.size) return null
        // The columns themselves, among those 2 px around: where the sides' edges are, not only near them.
        return (best - 2..best + 2).maxBy { edgeShare(frame.left + it) + edgeShare(frame.right - it) }
    }

    /** The share of the frame's rows that step from column [x] - 1 to [x]. */
    private fun edgeShare(x: Int): Float =
        (frame.top until frame.bottom).count { y -> colorStep(pixel(x, y), pixel(x - 1, y)) > EDGE_STEP }.toFloat() /
            frame.height

    /** Each column's share of the frame's rows that step to their left or upper neighbor; the first has none. */
    private fun columnDetail(): FloatArray {
        val detail = FloatArray(frame.width)
        for (x in frame.left + 1 until frame.right) {
            var sharp = 0
            for (y in frame.top + 1 until frame.bottom) {
                val pixel = pixel(x, y)
                if (colorStep(pixel, pixel(x - 1, y)) > DETAIL_STEP ||
                    colorStep(pixel, pixel(x, y - 1)) > DETAIL_STEP
                ) {
                    sharp++
                }
            }
            detail[x - frame.left] = sharp.toFloat() / frame.height
        }
        return detail
    }

    /** The most of the [card]'s rows that step across a column within 2 px of [x], its side. */
    private fun sideEdge(card: PixelRect, x: Int): Float = (x - 2..x + 2).maxOf { column ->
        val edges = (card.top until card.bottom).count { y ->
            colorStep(pixel(column, y), pixel(column - 1, y)) >
                EDGE_STEP
        }
        edges.toFloat() / card.height
    }

    /** The margin above and below where the card's horizontal edges are strongest; null without a clear one. */
    private fun topMargin(left: Int, right: Int): Int? {
        val edges = FloatArray(frame.height)
        for (y in frame.top + 2 until frame.bottom - 1) {
            val count = (left until right).count { x -> colorStep(pixel(x, y), pixel(x, y - 1)) > EDGE_STEP }
            edges[y - frame.top] = count.toFloat() / (right - left)
        }
        fun near(row: Int) = (row - 2..row + 2).maxOf { edges.getOrElse(it) { 0f } }
        val rows = frame.height
        var best = -1
        var bestScore = 0f
        for (m in rows / 50 until rows * 45 / 100) {
            val score = minOf(near(m), near(rows - m))
            if (score > bestScore) {
                best = m
                bestScore = score
            }
        }
        if (best < 0 || bestScore < TOP_EDGE) return null
        // The rows themselves, among those 2 px around: where the edges are, not only near them.
        return (maxOf(0, best - 2)..best + 2).maxBy { edges.getOrElse(it) { 0f } + edges.getOrElse(rows - it) { 0f } }
    }

    private fun pixel(x: Int, y: Int): Int = pixels[y * width + x]

    private fun FloatArray.mean(from: Int, to: Int): Float {
        var sum = 0f
        for (i in from until to) sum += this[i]
        return sum / maxOf(1, to - from)
    }
}
