// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import kotlin.math.roundToInt

/** The preview's corners, where the reference thumbnail sits (FR-3.9); a drag drops it into the nearest one. */
enum class ReferenceCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Where the reference thumbnail may sit, in the preview's pixels: inside the preview's margins and above the guide's
 * controls. Right and bottom are exclusive.
 */
internal data class ReferenceArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/** The thumbnail's top-left corner and size, in the preview's pixels. */
internal data class ReferencePlacement(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The reference thumbnail in [corner] of [area] (FR-3.9), keeping the photo's [aspect] (width / height). Small, its
 * long side is [smallLongSide]; enlarged, it is half of [previewWidth] wide, or narrower if it would be taller than
 * [area]. An invalid aspect shows a square, and an area not laid out yet keeps it small.
 */
internal fun placeReference(
    aspect: Float,
    expanded: Boolean,
    corner: ReferenceCorner,
    smallLongSide: Int,
    previewWidth: Int,
    area: ReferenceArea,
): ReferencePlacement {
    val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: 1f
    val (width, height) = when {
        expanded && previewWidth > 0 && area.height > 0 -> {
            val width = minOf(previewWidth / 2f, area.height * ratio)
            width.roundToInt() to (width / ratio).roundToInt()
        }

        ratio < 1f -> (smallLongSide * ratio).roundToInt() to smallLongSide

        else -> smallLongSide to (smallLongSide / ratio).roundToInt()
    }
    val left = corner == ReferenceCorner.TOP_LEFT || corner == ReferenceCorner.BOTTOM_LEFT
    val top = corner == ReferenceCorner.TOP_LEFT || corner == ReferenceCorner.TOP_RIGHT
    return ReferencePlacement(
        x = if (left) area.left else area.right - width,
        y = if (top) area.top else area.bottom - height,
        width = width,
        height = height,
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
