// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The base brightness as an exposure index (FR-1.11; Testing Documentation 2.1.6), plain JVM: 0 EV, where each tap to
 * focus goes back to. Partitions: a camera with an exposure range that holds 0 (index 0) / one that does not (its
 * nearest end) / without one (null); for the EV of an index (`exposureIndexOf`, the brightness bar's): on an index,
 * between two (the nearest), past either end (clamped), a step of 0, and an EV that is not a number (0, within the range).
 */
class BaseBrightnessTest {
    private fun camera(range: IntRange, step: Float) =
        CameraCapabilities(0.6f, 10f, listOf(1f, 2f), exposureRange = range, exposureStep = step)

    @Test
    fun `the base is index 0 on a camera whose range holds it`() {
        assertEquals(0, camera(-6..6, 1f / 3f).baseExposureIndex())
        assertEquals(0, camera(-4..4, 0f).baseExposureIndex())
    }

    @Test
    fun `on a range without 0 the base is its nearest end`() {
        assertEquals(1, camera(1..4, 0.5f).baseExposureIndex())
        assertEquals(-1, camera(-4..-1, 0.5f).baseExposureIndex())
    }

    @Test
    fun `a camera without exposure compensation has no base index`() {
        assertNull(camera(0..0, 0f).baseExposureIndex())
    }

    @Test
    fun `an EV's index is the nearest of the camera's steps, within its range`() {
        assertEquals(3, exposureIndexOf(1f, 1f / 3f, -6..6))
        assertEquals(-2, exposureIndexOf(-0.7f, 1f / 3f, -6..6))
        // 0.7 EV in halves lies between index 1 and 2, nearer 1.
        assertEquals(1, exposureIndexOf(0.7f, 0.5f, -4..4))
        assertEquals(2, exposureIndexOf(2f, 0.5f, -2..2))
        assertEquals(-2, exposureIndexOf(-2f, 0.5f, -2..2))
    }

    @Test
    fun `a step of zero or an EV that is no number gives 0 within the range`() {
        assertEquals(0, exposureIndexOf(Float.NaN, 0.5f, -4..4))
        assertEquals(0, exposureIndexOf(Float.POSITIVE_INFINITY, 0.5f, -4..4))
        assertEquals(1, exposureIndexOf(1f, 0f, 1..4))
    }
}
