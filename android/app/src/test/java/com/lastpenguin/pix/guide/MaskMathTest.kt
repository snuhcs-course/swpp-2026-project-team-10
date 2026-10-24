// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

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
}
