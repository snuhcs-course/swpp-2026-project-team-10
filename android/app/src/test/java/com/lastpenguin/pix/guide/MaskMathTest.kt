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
    fun alphaScalesConfidenceAndPadsEachRow() {
        val alpha = confidenceToAlpha(floatArrayOf(0f, 1f, 0.5f, 2f), width = 2, height = 2, rowBytes = 3)
        assertArrayEquals(byteArrayOf(0, 255.toByte(), 0, 128.toByte(), 255.toByte(), 0), alpha)
    }
}
