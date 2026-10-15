// AI-generated with Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Partitions.
 * frameAspect: each of the three ratios.
 * clampedExposure: index inside the range, below it, above it, at each end; a camera without compensation (0..0).
 * frameToBuffer: rotation 0, 90, 180, 270, and -90 (same as 270), and one that is not a quarter turn; a crop that
 * fills the buffer and one with an offset (16:9 cut from 4:3); a point inside the frame, at its corners, outside
 * it (below 0, above 1), and not finite.
 */
class CameraControlsTest {
    @Test
    fun frameAspect_eachRatio_isPortraitWidthOverHeight() {
        assertEquals(0.75f, AspectRatio.R4_3.frameAspect, EPSILON)
        assertEquals(0.5625f, AspectRatio.R16_9.frameAspect, EPSILON)
        assertEquals(1f, AspectRatio.R1_1.frameAspect, EPSILON)
    }

    @Test
    fun clampedExposure_indexInsideRange_isKept() {
        assertEquals(3, clampedExposure(3, -12..12))
    }

    @Test
    fun clampedExposure_indexOutsideRange_movesToTheNearestEnd() {
        assertEquals(-12, clampedExposure(-13, -12..12))
        assertEquals(12, clampedExposure(40, -12..12))
        assertEquals(12, clampedExposure(12, -12..12))
    }

    @Test
    fun clampedExposure_cameraWithoutCompensation_isNull() {
        assertNull(clampedExposure(0, 0..0))
        assertNull(clampedExposure(5, 0..0))
    }

    @Test
    fun frameToBuffer_noRotation_scalesIntoTheCrop() {
        val point = frameToBuffer(0.25f, 0.5f, 0, 0, 0, 1440, 1080)

        assertPoint(360f, 540f, point)
    }

    @Test
    fun frameToBuffer_rearCameraTurnedClockwise_frameTopLeftIsBufferBottomLeft() {
        // A portrait phone's rear camera: the landscape buffer is turned 90° clockwise to stand upright.
        assertPoint(0f, 1080f, frameToBuffer(0f, 0f, 90, 0, 0, 1440, 1080))
        assertPoint(0f, 0f, frameToBuffer(1f, 0f, 90, 0, 0, 1440, 1080))
        assertPoint(1440f, 1080f, frameToBuffer(0f, 1f, 90, 0, 0, 1440, 1080))
    }

    @Test
    fun frameToBuffer_upsideDownAndCounterClockwise_mapTheCornersBack() {
        assertPoint(1440f, 1080f, frameToBuffer(0f, 0f, 180, 0, 0, 1440, 1080))
        assertPoint(1440f, 0f, frameToBuffer(0f, 0f, 270, 0, 0, 1440, 1080))
        assertPoint(1440f, 0f, frameToBuffer(0f, 0f, -90, 0, 0, 1440, 1080))
    }

    @Test
    fun frameToBuffer_cropWithOffset_staysInsideTheCrop() {
        // 9:16 portrait: 1440×810 cut from the middle of a 1440×1080 buffer.
        assertPoint(720f, 540f, frameToBuffer(0.5f, 0.5f, 90, 0, 135, 1440, 810))
        assertPoint(0f, 135f, frameToBuffer(1f, 0f, 90, 0, 135, 1440, 810))
        assertPoint(1440f, 945f, frameToBuffer(0f, 1f, 90, 0, 135, 1440, 810))
    }

    @Test
    fun frameToBuffer_pointOutsideTheFrameOrNotFinite_isNull() {
        assertNull(frameToBuffer(-0.01f, 0.5f, 90, 0, 0, 1440, 1080))
        assertNull(frameToBuffer(0.5f, 1.01f, 90, 0, 0, 1440, 1080))
        assertNull(frameToBuffer(Float.NaN, 0.5f, 90, 0, 0, 1440, 1080))
    }

    @Test
    fun frameToBuffer_rotationNotAQuarterTurn_isNull() {
        assertNull(frameToBuffer(0.5f, 0.5f, 45, 0, 0, 1440, 1080))
    }

    private fun assertPoint(x: Float, y: Float, point: BufferPoint?) {
        assertEquals(x, point!!.x, EPSILON)
        assertEquals(y, point.y, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
