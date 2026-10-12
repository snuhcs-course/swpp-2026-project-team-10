// AI-generated with Claude Code, 2026-10-05, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaskMathTest {
    @Test
    fun boundsCoverEveryConfidentPixelWithExclusiveRightAndBottom() {
        // 4x3 mask; two separate people, as in a couple photo (FR-2.5).
        val confidence = floatArrayOf(
            0f, 0.9f, 0f, 0f,
            0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0.5f,
        )
        assertEquals(
            MaskBounds(left = 1, top = 0, right = 4, bottom = 3, count = 2),
            foregroundBounds(confidence, 4, 3),
        )
    }

    @Test
    fun pixelsBelowTheThresholdAreBackground() {
        assertNull(foregroundBounds(floatArrayOf(0.49f, 0f, 0.2f, 0.1f), 2, 2))
    }

    @Test
    fun edgesAreTheForegroundPixelsTouchingTheBackground() {
        // A 3x3 person in the middle of a 5x5 photo: its ring is the edge, its center is not.
        val alpha = grid(
            "00000",
            "01110",
            "01110",
            "01110",
            "00000",
        )
        assertArrayEquals(
            grid(
                "00000",
                "01110",
                "01010",
                "01110",
                "00000",
            ).map { it != 0 }.toBooleanArray(),
            maskEdges(alpha, 5, 5),
        )
    }

    @Test
    fun aPersonCutOffByThePhotoEdgeStaysOpenThere() {
        // The person fills the bottom rows; no line is drawn along the photo's bottom edge.
        val alpha = grid(
            "000",
            "111",
            "111",
        )
        assertArrayEquals(
            grid(
                "000",
                "111",
                "000",
            ).map { it != 0 }.toBooleanArray(),
            maskEdges(alpha, 3, 3),
        )
    }

    @Test
    fun alphaBelowHalfIsBackgroundForTheOutline() {
        assertArrayEquals(
            booleanArrayOf(false, true),
            maskEdges(intArrayOf(FOREGROUND_ALPHA - 1, FOREGROUND_ALPHA), 2, 1),
        )
    }

    @Test
    fun dilationGrowsAPixelIntoARoundStroke() {
        val center = BooleanArray(25).also { it[12] = true }
        assertArrayEquals(center, dilate(center, 5, 5, 0))
        assertArrayEquals(
            grid(
                "00000",
                "01110",
                "01110",
                "01110",
                "00000",
            ).map { it != 0 }.toBooleanArray(),
            dilate(center, 5, 5, 1),
        )
        assertArrayEquals(
            grid(
                "01110",
                "11111",
                "11111",
                "11111",
                "01110",
            ).map { it != 0 }.toBooleanArray(),
            dilate(center, 5, 5, 2),
        )
    }

    @Test
    fun alphaScalesConfidenceAndPadsEachRow() {
        val alpha = confidenceToAlpha(floatArrayOf(0f, 1f, 0.5f, 2f), width = 2, height = 2, rowBytes = 3)
        assertArrayEquals(byteArrayOf(0, 255.toByte(), 0, 128.toByte(), 255.toByte(), 0), alpha)
    }

    /** Rows of '0' (background) and '1' (person) as alpha 0 and 255. */
    private fun grid(vararg rows: String): IntArray =
        rows.joinToString("").map { if (it == '1') 255 else 0 }.toIntArray()
}
