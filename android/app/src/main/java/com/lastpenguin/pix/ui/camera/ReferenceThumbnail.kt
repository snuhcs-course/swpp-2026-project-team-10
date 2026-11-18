// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import kotlin.math.abs
import kotlin.math.roundToInt

/** The preview's corners, where the reference thumbnail sits (FR-3.9); a drag drops it into the nearest one. */
enum class ReferenceCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    ;

    /** At the preview's left side, where a hidden thumbnail of this corner waits. */
    val isLeft: Boolean get() = this == TOP_LEFT || this == BOTTOM_LEFT
}

/** How see-through a hidden thumbnail's strip is, so it reads as put away and covers little (#237). */
internal const val REFERENCE_HIDDEN_ALPHA = 0.55f

/**
 * Where the reference thumbnail may sit, in the preview's pixels: inside the preview's margins and above the guide's
 * controls. Right and bottom are exclusive.
 */
internal data class ReferenceArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/** The thumbnail's top-left corner and size, in the preview's pixels, and how see-through it is there. */
internal data class ReferencePlacement(val x: Int, val y: Int, val width: Int, val height: Int, val alpha: Float = 1f)

/**
 * The reference thumbnail in [corner] of [area] (FR-3.9), keeping the photo's [aspect] (width / height). Small, its
 * long side is [smallLongSide]; enlarged, it is half of [previewWidth] wide, or narrower if it would be taller than
 * [area]. An invalid aspect shows a square, and an area not laid out yet keeps it small. [hidden] (#237), it is small
 * whatever [expanded] says, at its height in the corner, and out at that corner's side of the preview, all but [peek]
 * of it.
 */
internal fun placeReference(
    aspect: Float,
    expanded: Boolean,
    corner: ReferenceCorner,
    smallLongSide: Int,
    previewWidth: Int,
    area: ReferenceArea,
    hidden: Boolean = false,
    peek: Int = 0,
): ReferencePlacement {
    val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    val (width, height) = when {
        expanded && !hidden && previewWidth > 0 && area.height > 0 -> {
            val width = minOf(previewWidth / 2f, area.height * ratio)
            width.roundToInt() to (width / ratio).roundToInt()
        }

        ratio < 1f -> (smallLongSide * ratio).roundToInt() to smallLongSide

        else -> smallLongSide to (smallLongSide / ratio).roundToInt()
    }
    val left = corner.isLeft
    val top = corner == ReferenceCorner.TOP_LEFT || corner == ReferenceCorner.TOP_RIGHT
    return ReferencePlacement(
        x = when {
            hidden && left -> peek - width
            hidden -> previewWidth - peek
            left -> area.left
            else -> area.right - width
        },
        y = if (top) area.top else area.bottom - height,
        width = width,
        height = height,
        alpha = if (hidden) REFERENCE_HIDDEN_ALPHA else 1f,
    )
}

/** The corner nearest to a thumbnail dropped with its center at ([centerX], [centerY]). */
internal fun nearestCorner(centerX: Float, centerY: Float, area: ReferenceArea): ReferenceCorner {
    val left = centerX < (area.left + area.right) / 2f
    val top = centerY < (area.top + area.bottom) / 2f
    return when {
        top && left -> ReferenceCorner.TOP_LEFT
        top -> ReferenceCorner.TOP_RIGHT
        left -> ReferenceCorner.BOTTOM_LEFT
        else -> ReferenceCorner.BOTTOM_RIGHT
    }
}

/** Where a thumbnail that was dragged goes when it is let go: into [corner], or [hidden] at that corner's side. */
data class ReferenceDrop(val corner: ReferenceCorner, val hidden: Boolean)

/**
 * Where a thumbnail goes that is let go with its center at ([centerX], [centerY]) while moving at ([velocityX],
 * [velocityY]) pixels a second (#237): into the nearest corner, and hidden at that corner's side when more than half
 * of it is past the side of a preview [previewWidth] wide, or when it was flung at that side, at [flingSpeed] or
 * faster and more sideways than up or down. Flung at the far side, it is on its way to the other corner, not out.
 */
internal fun dropReference(
    centerX: Float,
    centerY: Float,
    velocityX: Float,
    velocityY: Float,
    area: ReferenceArea,
    previewWidth: Int,
    flingSpeed: Float,
): ReferenceDrop {
    val corner = nearestCorner(centerX, centerY, area)
    val out = if (corner.isLeft) centerX < 0f else centerX > previewWidth
    val outward = if (corner.isLeft) -velocityX else velocityX
    val flung = outward >= flingSpeed && outward > abs(velocityY)
    return ReferenceDrop(corner, hidden = out || flung)
}

/**
 * How see-through a thumbnail [width] wide is at [x] in a preview [previewWidth] wide (#237): itself while all of it
 * is inside, [REFERENCE_HIDDEN_ALPHA] when only its [peek] is, and in between as it slides out at either side.
 */
internal fun referenceAlpha(x: Float, width: Int, previewWidth: Int, peek: Int): Float {
    val outside = maxOf(-x, x + width - previewWidth, 0f)
    val share = (outside / (width - peek).coerceAtLeast(1)).coerceIn(0f, 1f)
    return 1f - (1f - REFERENCE_HIDDEN_ALPHA) * share
}
