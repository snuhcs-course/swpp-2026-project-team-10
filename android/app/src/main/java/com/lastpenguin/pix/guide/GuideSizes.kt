// AI-generated with Claude Code, 2026-10-27, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.roundToInt

/** The reference is segmented at this long side at most (Design 2.6.1, step 1). */
internal const val MAX_REFERENCE_LONG_SIDE = 1280

/** The reference kept for the Camera screen's thumbnail, enlarged to at most half a phone's width (FR-3.9). */
internal const val REFERENCE_THUMBNAIL_LONG_SIDE = 512

/**
 * The outline's stroke where the guide first appears: about 8 px on screen, 3 dp on a typical phone (Design 2.6.1,
 * step 5). Much lower values round to a 1 px stroke on the 720 px mask the subject receives.
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

/** [bitmap] scaled down so its long side is at most [maxLongSide]; the same bitmap when it already fits. */
internal fun shrink(bitmap: Bitmap, maxLongSide: Int): Bitmap {
    val size = fitLongSide(bitmap.width, bitmap.height, maxLongSide)
    return if (size.width == bitmap.width && size.height == bitmap.height) {
        bitmap
    } else {
        bitmap.scale(size.width, size.height)
    }
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

/** This rectangle as fractions of a [width]×[height] image: where the mask lies in the reference (FR-3.1). */
internal fun PixelRect.relativeTo(width: Int, height: Int): NormalizedRect = NormalizedRect(
    left = left.toFloat() / width,
    top = top.toFloat() / height,
    right = right.toFloat() / width,
    bottom = bottom.toFloat() / height,
)

/**
 * The outline stroke in mask pixels, scaled so it is about [STROKE_ON_SCREEN_PX] when the guide first appears
 * (Design 2.6.1, step 5): a small mask is enlarged more on screen, so it gets a thinner stroke.
 */
internal fun outlineStrokePx(maskHeight: Int): Float = STROKE_ON_SCREEN_PX * maskHeight / GUIDE_ON_SCREEN_PX

/** The mask's values, from the alpha of any bitmap: an ALPHA_8 mask, or the WebP cutout of an older saved guide. */
internal fun Bitmap.maskPixels(): MaskPixels {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    for (k in pixels.indices) pixels[k] = pixels[k] ushr 24
    return MaskPixels(width, height, pixels)
}

/** The mask as the ALPHA_8 bitmap that [ReferenceGuide.mask] holds. */
internal fun MaskPixels.toMaskBitmap(): Bitmap {
    val colors = IntArray(alpha.size) { alpha[it] shl 24 }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
        setPixels(colors, 0, width, 0, 0, width, height)
    }
}
