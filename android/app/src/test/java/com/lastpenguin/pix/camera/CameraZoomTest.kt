// AI-generated with ChatGPT Codex and Claude Code, 2026-10-27, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraZoomTest {
    @Test
    fun doesNotAdvertiseUltrawideWhenCameraMinimumIsAboveTheChipRatio() {
        assertEquals(listOf(1f, 2f, 3f), cameraZoomStops(0.8f, 10f))
        assertEquals(listOf(0.5f, 0.6f, 1f, 2f, 3f), cameraZoomStops(0.5f, 10f))
        assertEquals(listOf(0.6f, 1f, 2f, 3f), cameraZoomStops(0.6f, 10f))
    }

    @Test
    fun excludesUnsupportedTelephotoStops() {
        assertEquals(listOf(1f, 2f), cameraZoomStops(1f, 2.5f))
        assertEquals(listOf(1f), cameraZoomStops(1f, 1f))
    }

    // Quick steps (FR-1.3). The minimum: 0.5× and 0.6× (a stop), 0.55× and 0.7× (none), within 2 % of 1×, 1×, and not
    // a number; the maximum below 2× and at 1×. A tap: below, at, and between the steps, at the last and beyond it, and
    // within the tolerance of a step.
    @Test
    fun quickStepsAreTheCamerasWidestBelowOneThenOneAndTwo_whereTheCameraHasThem() {
        assertEquals(listOf(0.5f, 1f, 2f), quickZoomSteps(0.5f, 10f))
        assertEquals(listOf(0.6f, 1f, 2f), quickZoomSteps(0.6f, 10f))
        assertEquals(listOf(0.55f, 1f, 2f), quickZoomSteps(0.55f, 10f))
        assertEquals(listOf(0.7f, 1f, 2f), quickZoomSteps(0.7f, 10f))
        assertEquals(listOf(1f, 2f), quickZoomSteps(0.99f, 10f))
        assertEquals(listOf(1f, 2f), quickZoomSteps(1f, 10f))
        assertEquals(listOf(0.6f, 1f), quickZoomSteps(0.6f, 1.5f))
        assertEquals(listOf(1f), quickZoomSteps(1f, 1f))
        assertEquals(emptyList<Float>(), quickZoomSteps(Float.NaN, Float.NaN))
    }

    @Test
    fun aTapGoesToTheNextStep_andFromTheLastOrBeyondBackToTheFirst() {
        val steps = listOf(0.6f, 1f, 2f)
        assertEquals(0.6f, nextZoomStep(steps, 0.55f))
        assertEquals(1f, nextZoomStep(steps, 0.6f))
        assertEquals(2f, nextZoomStep(steps, 1f))
        assertEquals(2f, nextZoomStep(steps, 1.4f))
        assertEquals(0.6f, nextZoomStep(steps, 2f))
        assertEquals(0.6f, nextZoomStep(steps, 5f))
        // A pinch that ended just past 1× counts as 1×.
        assertEquals(2f, nextZoomStep(steps, 1.01f))
        assertNull(nextZoomStep(emptyList(), 1f))
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
