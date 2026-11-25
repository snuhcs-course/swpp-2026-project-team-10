// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.guide.NormalizedRect

/** A rectangle in a view's pixels, in plain Kotlin, so the frames are tested on the JVM. */
internal data class CutBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * One moment of the picked image turning into its guide: where the whole image is drawn ([screen]); from 0 to 1, how
 * much of a screenshot around its photo shows ([outside]), the frame around the photo ([frame]), the photo's alpha
 * ([photo]), how much of the outline is drawn ([line]), and how far the photo has gone to the corner ([corner]); and
 * where the outline is drawn ([lineBox]): on the person in the photo, then in its own place.
 */
internal data class GuideFrame(
    val screen: CutBox,
    val outside: Float,
    val frame: Float,
    val photo: Float,
    val line: Float,
    val lineBox: CutBox,
    val corner: Float,
)

/**
 * Reference confirm's picked image turning into its guide (FR-2.3, FR-2.10, R&S 6.2, #295) in a [width] × [height]
 * card, for an image of [imageWidth] × [imageHeight] whose [photo] the guide was made from, its person at [person] in
 * the photo, an outline of [outlineAspect]. A screenshot cut to its photo ([cut]) is first shown whole; the rest of it
 * dims, the photo is framed and comes to fill the card inside [padding]. A photo used whole starts there and holds.
 * Then the outline is drawn along the person on the photo, which dims under it, and the photo goes to the bottom right
 * corner, at most [corner] on a side and [margin] from the edges, while the outline goes to where the outline's view
 * shows it. The photo moves as the scene photo comes to its thumbnail (SceneArrival): the same hold and move.
 */
internal class PhotoToGuide(
    imageWidth: Int,
    imageHeight: Int,
    private val photo: NormalizedRect,
    person: NormalizedRect?,
    outlineAspect: Float,
    private val cut: Boolean,
    width: Float,
    height: Float,
    padding: Float,
    corner: Float,
    margin: Float,
) {
    private val photoAspect = imageWidth * (photo.right - photo.left) / (imageHeight * photo.height)
    private val card = CutBox(padding, padding, width - padding, height - padding)
    private val whole = fit(imageWidth.toFloat() / imageHeight, card)
    private val filled = around(fit(photoAspect, card))
    private val cornered = around(inCorner(photoAspect, corner, width - margin, height - margin))
    private val placed = fit(outlineAspect, card)
    private val onPhoto = person?.let { place(it, photoIn(filled)) } ?: placed
    private val lineStart = if (cut) CUT_MS else HOLD_MS
    private val total = lineStart + LINE_MS + MOVE_MS

    /** How long it plays ([durationMs]). */
    val durationMs: Long = durationMs(cut)

    /** The frame at [progress], 0 to 1 of [durationMs]. */
    fun at(progress: Float): GuideFrame {
        val ms = progress * total
        val dim = if (cut) phase(ms, DIM_START_MS, ZOOM_START_MS) else 0f
        val zoom = if (cut) phase(ms, ZOOM_START_MS, lineStart) else 1f
        val line = phase(ms, lineStart, lineStart + LINE_MS)
        val move = phase(ms, lineStart + LINE_MS, total)
        val screen = if (move > 0f) lerp(filled, cornered, move) else lerp(whole, filled, zoom)
        return GuideFrame(
            screen = screen,
            outside = if (cut) (1f - DIMMED * dim) * (1f - zoom) else 0f,
            frame = dim * (1f - line),
            photo = 1f - SHADED * line * (1f - move),
            line = line,
            lineBox = lerp(onPhoto, placed, move),
            corner = move,
        )
    }

    /** Where the photo is when the whole image is drawn at [screen]. */
    fun photoIn(screen: CutBox): CutBox = place(photo, screen)

    /** Where the whole image is drawn so its photo lies at [target]. */
    private fun around(target: CutBox): CutBox {
        val width = target.width / (photo.right - photo.left)
        val height = target.height / photo.height
        val left = target.left - photo.left * width
        val top = target.top - photo.top * height
        return CutBox(left, top, left + width, top + height)
    }

    companion object {
        /** How long it plays: 2 s for a screenshot's cut, 1.3 s for a photo. */
        fun durationMs(cut: Boolean): Long = ((if (cut) CUT_MS else HOLD_MS) + LINE_MS + MOVE_MS).toLong()

        /** A screenshot's cut, before the line: whole until [DIM_START_MS], dimmed around the photo, then zoomed to it. */
        private const val CUT_MS = 1000f
        private const val DIM_START_MS = 250f
        private const val ZOOM_START_MS = 600f

        /** A photo holds before the line, as the scene photo does before it moves (SceneArrival.HOLD_MS). */
        private const val HOLD_MS = 300f

        /** The outline drawn along the person. */
        private const val LINE_MS = 500f

        /** The photo to the corner and the outline to its place, as the scene photo to its thumbnail (MOVE_MS). */
        private const val MOVE_MS = 500f

        /** How much of the screenshot around the photo goes while the photo is framed. */
        private const val DIMMED = 0.65f

        /** How much the photo dims while the line is drawn on it, so a line of any color shows. */
        private const val SHADED = 0.5f

        /** [rect], a part of a box, where it lies when that box is drawn at [box]. */
        private fun place(rect: NormalizedRect, box: CutBox) = CutBox(
            box.left + rect.left * box.width,
            box.top + rect.top * box.height,
            box.left + rect.right * box.width,
            box.top + rect.bottom * box.height,
        )

        /** [aspect] as large as fits in [area], centered. */
        private fun fit(aspect: Float, area: CutBox): CutBox {
            val width = minOf(area.width, area.height * aspect)
            val height = width / aspect
            val left = area.left + (area.width - width) / 2f
            val top = area.top + (area.height - height) / 2f
            return CutBox(left, top, left + width, top + height)
        }

        /** [aspect] at most [size] on a side, with its bottom right corner at [right], [bottom]. */
        private fun inCorner(aspect: Float, size: Float, right: Float, bottom: Float): CutBox {
            val width = if (aspect >= 1f) size else size * aspect
            return CutBox(right - width, bottom - width / aspect, right, bottom)
        }

        /** 0 before [start] ms, 1 after [end], and eased in and out between. */
        private fun phase(ms: Float, start: Float, end: Float): Float {
            val t = ((ms - start) / (end - start)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private fun lerp(from: CutBox, to: CutBox, t: Float) = CutBox(
            from.left + (to.left - from.left) * t,
            from.top + (to.top - from.top) * t,
            from.right + (to.right - from.right) * t,
            from.bottom + (to.bottom - from.bottom) * t,
        )
    }
}
