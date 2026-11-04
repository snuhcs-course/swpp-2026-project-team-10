// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.guide.NormalizedRect

/** A rectangle in a view's pixels, in plain Kotlin, so the cut's frames are tested on the JVM. */
internal data class CutBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * One moment of the cut: where the whole screenshot is drawn ([screen]), and from 0 to 1 how much of the screenshot
 * around the photo shows ([outside]), the frame around the photo ([frame]), and how far the photo has gone to the
 * corner while the outline appears ([corner]).
 */
internal data class CutFrame(val screen: CutBox, val outside: Float, val frame: Float, val corner: Float)

/**
 * Reference confirm's cut (FR-2.10, R&S 6.2) in a [width] × [height] card, for a screenshot of [imageWidth] ×
 * [imageHeight] whose [photo] was kept: the screenshot whole; the rest of it dims and the photo is framed; the photo
 * fills the card inside [padding] and holds there; then it goes to the bottom right corner, at most [corner] on a
 * side and [margin] from the edges, while the outline appears.
 */
internal class ScreenshotCut(
    imageWidth: Int,
    imageHeight: Int,
    private val photo: NormalizedRect,
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

    /** The cut at [progress], 0 to 1. */
    fun at(progress: Float): CutFrame {
        val dim = phase(progress, DIM_START, ZOOM_START)
        val zoom = phase(progress, ZOOM_START, HOLD_START)
        val held = phase(progress, HOLD_START, CORNER_START)
        val corner = outlineAt(progress)
        val screen = if (corner > 0f) lerp(filled, cornered, corner) else lerp(whole, filled, zoom)
        return CutFrame(screen, outside = (1f - DIMMED * dim) * (1f - zoom), frame = dim * (1f - held), corner = corner)
    }

    /** Where the photo is when the whole screenshot is drawn at [screen]. */
    fun photoIn(screen: CutBox): CutBox = CutBox(
        screen.left + photo.left * screen.width,
        screen.top + photo.top * screen.height,
        screen.left + photo.right * screen.width,
        screen.top + photo.bottom * screen.height,
    )

    /** Where the whole screenshot is drawn so its photo lies at [target]. */
    private fun around(target: CutBox): CutBox {
        val width = target.width / (photo.right - photo.left)
        val height = target.height / photo.height
        val left = target.left - photo.left * width
        val top = target.top - photo.top * height
        return CutBox(left, top, left + width, top + height)
    }

    companion object {
        private const val DIM_START = 0.15f
        private const val ZOOM_START = 0.35f
        private const val HOLD_START = 0.6f
        private const val CORNER_START = 0.75f

        /** How much of the screenshot around the photo goes while the photo is framed. */
        private const val DIMMED = 0.65f

        /** The outline's alpha at [progress]: it appears as the photo goes to the corner ([CutFrame.corner]). */
        fun outlineAt(progress: Float): Float = phase(progress, CORNER_START, 1f)

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

        /** 0 before [start], 1 after [end], and eased in and out between. */
        private fun phase(progress: Float, start: Float, end: Float): Float {
            val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
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
