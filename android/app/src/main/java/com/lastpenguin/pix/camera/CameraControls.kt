// AI-generated with Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.camera

/** Width / height of the portrait frame: 3:4, 9:16, or 1:1 (AD-10). */
val AspectRatio.frameAspect: Float
    get() = when (this) {
        AspectRatio.R4_3 -> 3f / 4f
        AspectRatio.R16_9 -> 9f / 16f
        AspectRatio.R1_1 -> 1f
    }

/** Width and height of the portrait frame in its smallest whole numbers, for a CameraX `ViewPort`. */
internal val AspectRatio.portraitParts: Pair<Int, Int>
    get() = when (this) {
        AspectRatio.R4_3 -> 3 to 4
        AspectRatio.R16_9 -> 9 to 16
        AspectRatio.R1_1 -> 1 to 1
    }

/** An exposure index within [range], or null for a camera without exposure compensation. */
internal fun clampedExposure(index: Int, range: IntRange): Int? =
    if (range.first == 0 && range.last == 0) null else index.coerceIn(range)

/** A point in a camera buffer, in pixels. */
internal data class BufferPoint(val x: Float, val y: Float)

/**
 * Maps a point of the upright frame, 0..1 from its top-left corner, to the camera buffer the frame is cut from:
 * the buffer's [cropLeft], [cropTop], [cropWidth] × [cropHeight] rectangle, turned clockwise by [rotationDegrees]
 * to stand upright. Null for a point outside the frame or a rotation that is not a quarter turn.
 */
internal fun frameToBuffer(
    x: Float,
    y: Float,
    rotationDegrees: Int,
    cropLeft: Int,
    cropTop: Int,
    cropWidth: Int,
    cropHeight: Int,
): BufferPoint? {
    if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return null
    // (u, v): the same point in the crop before it was turned, 0..1 of its width and height.
    val (u, v) = when (Math.floorMod(rotationDegrees, 360)) {
        0 -> x to y
        90 -> y to 1f - x
        180 -> 1f - x to 1f - y
        270 -> 1f - y to x
        else -> return null
    }
    return BufferPoint(cropLeft + u * cropWidth, cropTop + v * cropHeight)
}
