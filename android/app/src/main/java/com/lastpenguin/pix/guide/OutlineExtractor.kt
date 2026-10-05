package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * Draws the person's outline from a mask (Design 2.6.1, step 5).
 * The subject runs the same step on the received cutout's alpha channel.
 * Owner: PM (#5).
 */
class OutlineExtractor {
    /** Edge pixels of [mask] (alpha ≥ 0.5) drawn as a light [strokePx] stroke on a transparent bitmap. */
    fun extract(mask: Bitmap, strokePx: Float): Bitmap {
        val width = mask.width
        val height = mask.height
        // getPixels works for ALPHA_8 masks and ARGB_8888 cutouts alike; only the alpha byte is used.
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) pixels[i] = pixels[i] ushr 24
        // The edge is one pixel wide, so a radius r stroke is 2r + 1 px: 2–3 px gives radius 1.
        val radius = ((strokePx - 1f) / 2f).roundToInt().coerceAtLeast(0)
        val stroke = dilate(maskEdges(pixels, width, height), width, height, radius)
        for (i in pixels.indices) pixels[i] = if (stroke[i]) STROKE_COLOR else Color.TRANSPARENT
        return createBitmap(width, height).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    }

    private companion object {
        const val STROKE_COLOR = Color.WHITE
    }
}
