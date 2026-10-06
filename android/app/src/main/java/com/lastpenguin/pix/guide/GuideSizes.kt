package com.lastpenguin.pix.guide

import kotlin.math.roundToInt

/** The reference is segmented at this long side at most (Design 2.6.1, step 1). */
internal const val MAX_REFERENCE_LONG_SIDE = 1280

/**
 * The outline's stroke where the guide first appears: about 8 px on screen, 3 dp on a typical phone (Design 2.6.1,
 * step 5). Much lower values round to a 1 px stroke on the 720 px cutout the subject receives.
 */
private const val STROKE_ON_SCREEN_PX = 8f

/** A new guide is [GuideState.DEFAULT_HEIGHT] of the frame; a 1080 px wide phone's 3:4 frame is 1440 px tall. */
private const val GUIDE_ON_SCREEN_PX = GuideState.DEFAULT_HEIGHT * 1440f

internal data class PixelSize(val width: Int, val height: Int)

/** Right and bottom exclusive, as android.graphics.Rect. */
internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * The largest whole sample size that keeps the long side at least [target], so decoding a 12 MP photo stays cheap
 * and the exact downscale afterwards only refines it.
 */
internal fun decodeSampleSize(width: Int, height: Int, target: Int = MAX_REFERENCE_LONG_SIDE): Int =
    maxOf(1, maxOf(width, height) / target)

/** [width]×[height] scaled down so the long side is at most [maxLongSide]; never scaled up. */
internal fun fitLongSide(width: Int, height: Int, maxLongSide: Int = MAX_REFERENCE_LONG_SIDE): PixelSize {
    val longSide = maxOf(width, height)
    if (longSide <= maxLongSide) return PixelSize(width, height)
    val scale = maxLongSide.toFloat() / longSide
    return PixelSize(maxOf(1, (width * scale).roundToInt()), maxOf(1, (height * scale).roundToInt()))
}

/**
 * The person's bounding box grown by [fraction] of its longer side on every side, clamped to the image
 * (Design 2.6.1, step 4).
 */
internal fun cropWithMargin(bounds: PixelRect, width: Int, height: Int, fraction: Float = 0.02f): PixelRect {
    val margin = (maxOf(bounds.width, bounds.height) * fraction).roundToInt()
    return PixelRect(
        left = maxOf(0, bounds.left - margin),
        top = maxOf(0, bounds.top - margin),
        right = minOf(width, bounds.right + margin),
        bottom = minOf(height, bounds.bottom + margin),
    )
}

/**
 * The outline stroke in cutout pixels, scaled so it is about [STROKE_ON_SCREEN_PX] when the guide first appears
 * (Design 2.6.1, step 5): a small cutout is enlarged more on screen, so it gets a thinner stroke.
 */
internal fun outlineStrokePx(cutoutHeight: Int): Float = STROKE_ON_SCREEN_PX * cutoutHeight / GUIDE_ON_SCREEN_PX
