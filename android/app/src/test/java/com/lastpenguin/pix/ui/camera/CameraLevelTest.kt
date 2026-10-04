package com.lastpenguin.pix.ui.camera

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLevelTest {
    @Test
    fun uprightAndUpsideDownBothHaveAHorizontalHorizon() {
        assertEquals(CameraLevel(0f, true), cameraLevelFromGravity(0f, 9.81f, 0f))
        assertEquals(CameraLevel(0f, true), cameraLevelFromGravity(0f, -9.81f, 0f))
    }

    @Test
    fun horizonCounterRotatesClockwiseAndCounterClockwiseDeviceTilt() {
        assertEquals(-15f, fromAngle(-15f)!!.angleDegrees, 0.001f)
        assertEquals(15f, fromAngle(15f)!!.angleDegrees, 0.001f)
        assertFalse(fromAngle(-15f)!!.isLevel)
    }

    @Test
    fun rotatesNaturalDeviceCoordinatesIntoEachDisplayOrientation() {
        assertEquals(CameraLevel(0f, true), cameraLevelFromGravity(9.81f, 0f, 0f, 90))
        assertEquals(CameraLevel(0f, true), cameraLevelFromGravity(0f, -9.81f, 0f, 180))
        assertEquals(CameraLevel(0f, true), cameraLevelFromGravity(-9.81f, 0f, 0f, 270))
        assertEquals(-15f, cameraLevelFromGravity(9.4757f, 2.539f, 0f, 90)!!.angleDegrees, 0.01f)
    }

    @Test
    fun hidesUnknownHorizonWhenFlatOrGravityIsInvalid() {
        assertNull(cameraLevelFromGravity(0f, 0f, 9.81f))
        assertNull(cameraLevelFromGravity(0f, 0f, -9.81f))
        assertNull(cameraLevelFromGravity(0.1f, 0.2f, 9.8f))
        assertNull(cameraLevelFromGravity(0f, 0f, 0f))
        assertNull(cameraLevelFromGravity(Float.NaN, 9.81f, 0f))
        assertNull(cameraLevelFromGravity(0f, Float.POSITIVE_INFINITY, 0f))
        assertNull(cameraLevelFromGravity(0f, 9.81f, Float.NEGATIVE_INFINITY))
        assertNull(cameraLevelFromGravity(0f, 9.81f, 0f, 45))
        assertNotNull(cameraLevelFromGravity(0f, 3f, 9f))
    }

    @Test
    fun levelColorUsesSeparateEntryAndExitThresholds() {
        assertTrue(fromAngle(1.99f)!!.isLevel)
        assertFalse(fromAngle(2.01f)!!.isLevel)
        assertFalse(fromAngle(-2.5f)!!.isLevel)
        assertTrue(fromAngle(-2.5f, wasLevel = true)!!.isLevel)
        assertTrue(fromAngle(2.99f, wasLevel = true)!!.isLevel)
        assertFalse(fromAngle(3.01f, wasLevel = true)!!.isLevel)
    }

    @Test
    fun smoothingKeepsUpsideDownReadingsAtTheHorizonAcrossAngleWrap() {
        val tracker = CameraLevelTracker()
        tracker.update(0.1f, -9.81f, 0f, 1_000_000_000, 0)
        val next = tracker.update(-0.1f, -9.81f, 0f, 1_060_000_000, 0)!!
        assertTrue(next.isLevel)
        assertEquals(0f, next.angleDegrees, 1f)
    }

    @Test
    fun resetRemovesPreviousTiltAndInvalidReadingsDoNotPoisonTheFilter() {
        val tracker = CameraLevelTracker()
        tracker.update(4f, 9f, 0f, 1_000_000_000, 0)
        tracker.reset()
        assertTrue(tracker.update(0f, 9.81f, 0f, 1_060_000_000, 0)!!.isLevel)
        assertNull(tracker.update(Float.NaN, 9.81f, 0f, 1_120_000_000, 0))
        assertTrue(tracker.update(0f, 9.81f, 0f, 1_180_000_000, 0)!!.isLevel)
    }

    private fun fromAngle(degrees: Float, wasLevel: Boolean = false): CameraLevel? {
        val radians = Math.toRadians(degrees.toDouble())
        return cameraLevelFromGravity(
            (sin(radians) * 9.81).toFloat(),
            (cos(radians) * 9.81).toFloat(),
            0f,
            wasLevel = wasLevel,
        )
    }
}
