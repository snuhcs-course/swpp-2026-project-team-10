// AI-generated with Claude Code, 2026-11-25, reviewed by Dongje Park and Sungmin Jo
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
 * step on the received mask, so both phones draw the same line. Reference confirm draws the same lines ([lines]) along
 * the person as a photo turns into its guide (#295).
 * Owner: PM (#5).
 */
class OutlineExtractor {
    /** [mask]'s outline (alpha ≥ 0.5 is the person) as a white [strokePx] line on a transparent bitmap of its size. */
    fun extract(mask: Bitmap, strokePx: Float): Bitmap = extract(alphaOf(mask), mask.width, mask.height, strokePx)

    /** The same from row-major alpha values 0..255. */
    fun extract(alpha: IntArray, width: Int, height: Int, strokePx: Float): Bitmap {
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = STROKE_COLOR
        }
        for (line in lines(alpha, width, height)) canvas.drawPath(line, paint)
        return out
    }

    /**
     * The lines [extract] draws, in [mask]'s pixels. A line the photo's edge cuts off runs from one end at that edge to
     * the other, so it can be drawn along the person.
     */
    fun lines(mask: Bitmap): List<Path> = lines(alphaOf(mask), mask.width, mask.height)

    private fun lines(alpha: IntArray, width: Int, height: Int): List<Path> {
        val field = FloatArray(width * height) { alpha[it].toFloat() }
        return traceContours(field, width, height, OUTLINE_LEVEL).filter { it.size >= 2 }.map { contour ->
            val points = smoothContour(contour).points
            // Contour points are pixel centers; a pixel's center on the canvas is half a pixel in.
            Path().apply {
                moveTo(points[0] + 0.5f, points[1] + 0.5f)
                for (k in 2 until points.size step 2) lineTo(points[k] + 0.5f, points[k + 1] + 0.5f)
                if (contour.closed) close()
            }
        }
    }

    /** [mask]'s alpha values, row-major: getPixels works for ALPHA_8 masks and ARGB_8888 bitmaps alike. */
    private fun alphaOf(mask: Bitmap): IntArray {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        return IntArray(pixels.size) { pixels[it] ushr 24 }
    }

    private companion object {
        const val STROKE_COLOR = Color.WHITE

        /** Halfway between alpha 0 and 255: confidence 0.5 (Design 2.6.1, step 3). */
        const val OUTLINE_LEVEL = 127.5f
    }
}
