// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The mask's arithmetic on the plain JVM.
 * Partitions: the foreground's bounds (two separate people; every pixel below the threshold). Guide-edit mode's inside
 * (#271): values well below the outline's level, just below and just above it, well above it, and the mask's size.
 */
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
    fun guideInside_isClearBelowTheOutlinesLevel_opaqueAboveIt_andRampsAcrossIt() {
        val inside = guideInside(MaskPixels(6, 1, intArrayOf(0, 111, 127, 128, 144, 255)))

        assertEquals(0, inside.alpha[0])
        assertEquals(0, inside.alpha[1])
        // Across the 0.5 level, where the outline is traced, the edge is half covered and rising.
        assertTrue(inside.alpha[2] in 100..127)
        assertTrue(inside.alpha[3] in 128..155)
        assertEquals(255, inside.alpha[4])
        assertEquals(255, inside.alpha[5])
    }

    @Test
    fun guideInside_keepsTheMasksSize() {
        val inside = guideInside(MaskPixels(3, 2, IntArray(6) { 200 }))

        assertEquals(3, inside.width)
        assertEquals(2, inside.height)
    }
}
