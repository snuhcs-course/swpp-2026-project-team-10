package com.lastpenguin.pix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraZoomTest {
    @Test
    fun doesNotAdvertiseUltrawideWhenCameraMinimumIsAboveTheChipRatio() {
        assertEquals(listOf(1f, 2f, 3f), cameraZoomStops(0.8f, 10f))
        assertEquals(listOf(0.6f, 1f, 2f, 3f), cameraZoomStops(0.5f, 10f))
    }

    @Test
    fun excludesUnsupportedTelephotoStops() {
        assertEquals(listOf(1f, 2f), cameraZoomStops(1f, 2.5f))
        assertEquals(listOf(1f), cameraZoomStops(1f, 1f))
    }

    @Test
    fun clampsLocalAndRemoteRequestsToHardwareRange() {
        assertEquals(1f, clampedCameraZoom(0.6f, 1f, 3f))
        assertEquals(3f, clampedCameraZoom(10f, 1f, 3f))
        assertEquals(1.8f, clampedCameraZoom(1.8f, 1f, 3f))
    }

    @Test
    fun rejectsNonFiniteRemoteInputs() {
        assertNull(clampedCameraZoom(Float.NaN, 1f, 3f))
        assertNull(clampedCameraZoom(Float.POSITIVE_INFINITY, 1f, 3f))
        assertNull(clampedCameraZoom(Float.NEGATIVE_INFINITY, 1f, 3f))
    }
}
