// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The base brightness as an exposure index (FR-1.11; Testing Documentation 2.1.6), plain JVM. Partitions: a camera
 * with an exposure range / without one (null); the EV on an index / between two (the nearest) / past either end of the
 * range (clamped); a range that does not hold 0; a step of 0 and an EV that is not a number (0, within the range).
 */
class BaseBrightnessTest {
    private fun camera(range: IntRange, step: Float) =
        CameraCapabilities(0.6f, 10f, listOf(1f, 2f), exposureRange = range, exposureStep = step)

    @Test
    fun `the base is the nearest index of the camera's steps`() {
        val thirds = camera(-6..6, 1f / 3f)
        assertEquals(0, thirds.baseExposureIndex(0f))
        assertEquals(3, thirds.baseExposureIndex(1f))
        assertEquals(-2, thirds.baseExposureIndex(-0.7f))
        // 0.7 EV in halves lies between index 1 and 2, nearer 1.
        assertEquals(1, camera(-4..4, 0.5f).baseExposureIndex(0.7f))
    }

    @Test
    fun `a base past the camera's range is clamped to it`() {
        val small = camera(-2..2, 0.5f)
        assertEquals(2, small.baseExposureIndex(2f))
        assertEquals(-2, small.baseExposureIndex(-2f))
        assertEquals(1, camera(1..4, 0.5f).baseExposureIndex(0f))
    }

    @Test
    fun `a camera without exposure compensation has no base index`() {
        assertNull(camera(0..0, 0f).baseExposureIndex(1f))
    }

    @Test
    fun `a step of zero or an EV that is no number gives 0 within the range`() {
        assertEquals(0, camera(-4..4, 0f).baseExposureIndex(1f))
        assertEquals(0, camera(-4..4, 0.5f).baseExposureIndex(Float.NaN))
        assertEquals(0, exposureIndexOf(Float.POSITIVE_INFINITY, 0.5f, -4..4))
        assertEquals(1, exposureIndexOf(1f, 0f, 1..4))
    }
}
