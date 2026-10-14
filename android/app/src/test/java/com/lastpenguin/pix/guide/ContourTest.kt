// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourTest {
    @Test
    fun aSquareIsOneClosedLineHalfwayBetweenItsPixelsAndTheBackground() {
        val field = binary(10, 10, 3, 2, 7, 8) // pixels x 3..6, y 2..7

        val contours = traceContours(field, 10, 10, 0.5f)

        assertEquals(1, contours.size)
        val line = contours.single()
        assertTrue(line.closed)
        for (k in 0 until line.size) {
            val x = line.points[k * 2]
            val y = line.points[k * 2 + 1]
            val onSide = (abs(x - 2.5f) < 1e-4f || abs(x - 6.5f) < 1e-4f) && y in 2f..7f
            val onTopOrBottom = (abs(y - 1.5f) < 1e-4f || abs(y - 7.5f) < 1e-4f) && x in 3f..6f
            assertTrue("($x, $y) is off the square's edge", onSide || onTopOrBottom)
        }
    }

    @Test
    fun aPersonCutOffByThePhotosEdgeIsAnOpenLineEndingThere() {
        val field = binary(10, 10, 3, 4, 7, 10) // reaches the bottom row

        val line = traceContours(field, 10, 10, 0.5f).single()

        assertFalse(line.closed)
        // Both ends are on the bottom row, and no point runs along it between them.
        assertEquals(9f, line.points[1], 1e-4f)
        assertEquals(9f, line.points[line.points.size - 1], 1e-4f)
        assertEquals(2, (0 until line.size).count { line.points[it * 2 + 1] == 9f })
    }

    @Test
    fun twoPeopleGiveTwoLines() {
        val field = binary(20, 10, 2, 2, 6, 8)
        for (y in 2 until 8) for (x in 12 until 17) field[y * 20 + x] = 1f

        assertEquals(2, traceContours(field, 20, 10, 0.5f).count { it.closed })
    }

    @Test
    fun theCrossingIsInterpolatedBetweenPixels() {
        // A horizontal ramp 0, 0.25, 0.5, 0.75, 1 crosses 0.6 at x = 2.4, on every row.
        val field = FloatArray(5 * 3) { (it % 5) * 0.25f }

        val line = traceContours(field, 5, 3, 0.6f).single()

        for (k in 0 until line.size) assertEquals(2.4f, line.points[k * 2], 1e-4f)
    }

    @Test
    fun diagonalNeighboursJoinOnlyWhenTheCellsCenterIsInside() {
        val field = FloatArray(4 * 4)
        field[1 * 4 + 1] = 0.9f
        field[2 * 4 + 2] = 0.9f
        assertEquals(2, traceContours(field, 4, 4, 0.5f).size)

        field[1 * 4 + 1] = 1f
        field[2 * 4 + 2] = 1f
        field[1 * 4 + 2] = 0.3f
        field[2 * 4 + 1] = 0.3f
        assertEquals(1, traceContours(field, 4, 4, 0.5f).size)
    }

    @Test
    fun smoothingCutsCornersAndKeepsAnOpenLinesEnds() {
        val open = Contour(floatArrayOf(0f, 0f, 4f, 0f, 4f, 4f), closed = false)

        val smoothed = smoothContour(open, passes = 1)

        assertEquals(6, smoothed.size)
        assertEquals(0f, smoothed.points[0], 0f)
        assertEquals(4f, smoothed.points[smoothed.points.size - 1], 0f)
        // The corner at (4, 0) is gone: the nearest points are a quarter of a side away from it.
        assertTrue((0 until smoothed.size).none { smoothed.points[it * 2] == 4f && smoothed.points[it * 2 + 1] == 0f })

        val closed = Contour(floatArrayOf(0f, 0f, 4f, 0f, 4f, 4f, 0f, 4f), closed = true)
        assertEquals(16, smoothContour(closed, passes = 2).size)
    }

    private fun binary(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): FloatArray =
        FloatArray(width * height).also { field ->
            for (y in top until bottom) for (x in left until right) field[y * width + x] = 1f
        }
}
