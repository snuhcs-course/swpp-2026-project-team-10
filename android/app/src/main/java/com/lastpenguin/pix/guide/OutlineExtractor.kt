// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap

/**
 * Draws the person's outline from a mask (Design 2.6.1, step 5): the 0.5 level of the alpha channel traced to a line,
 * rounded, and drawn with an anti-aliased round stroke. The mask is already smooth (step 4); the subject runs the same
 * step on the received mask, so both phones draw the same line.
 * Owner: PM (#5).
 */
class OutlineExtractor {
    /** [mask]'s outline (alpha ≥ 0.5 is the person) as a white [strokePx] line on a transparent bitmap of its size. */
    fun extract(mask: Bitmap, strokePx: Float): Bitmap {
        val width = mask.width
        val height = mask.height
        // getPixels works for ALPHA_8 masks and ARGB_8888 bitmaps alike; only the alpha byte is used.
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        return extract(IntArray(pixels.size) { pixels[it] ushr 24 }, width, height, strokePx)
    }

    /** The same from row-major alpha values 0..255. */
    fun extract(alpha: IntArray, width: Int, height: Int, strokePx: Float): Bitmap {
        val field = FloatArray(width * height) { alpha[it].toFloat() }
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = STROKE_COLOR
        }
        for (contour in traceContours(field, width, height, OUTLINE_LEVEL)) {
            if (contour.size < 2) continue
            val points = smoothContour(contour).points
            // Contour points are pixel centers; a pixel's center on the canvas is half a pixel in.
            val path = Path().apply {
                moveTo(points[0] + 0.5f, points[1] + 0.5f)
                for (k in 2 until points.size step 2) lineTo(points[k] + 0.5f, points[k + 1] + 0.5f)
                if (contour.closed) close()
            }
            canvas.drawPath(path, paint)
        }
        return out
    }

    private companion object {
        const val STROKE_COLOR = Color.WHITE

        /** Halfway between alpha 0 and 255: confidence 0.5 (Design 2.6.1, step 3). */
        const val OUTLINE_LEVEL = 127.5f
    }
}
