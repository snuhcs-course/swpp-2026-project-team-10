package com.lastpenguin.pix.ui.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** The horizon's clockwise Canvas angle; a null reading means its direction is unknown. */
data class CameraLevel(val angleDegrees: Float, val isLevel: Boolean)

/** Maps Android's natural-device gravity coordinates into the currently displayed viewfinder. */
internal fun cameraLevelFromGravity(
    x: Float,
    y: Float,
    z: Float,
    displayRotationDegrees: Int = 0,
    wasLevel: Boolean = false,
): CameraLevel? {
    if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
    val (screenX, screenY) = when (displayRotationDegrees) {
        0 -> x to y
        90 -> -y to x
        180 -> -x to -y
        270 -> y to -x
        else -> return null
    }
    val planeSquared = screenX.toDouble() * screenX + screenY.toDouble() * screenY
    val magnitudeSquared = planeSquared + z.toDouble() * z
    // Close to face-up/down there is no reliable horizon, even if atan2 happens to return zero.
    if (magnitudeSquared < 1.0 || sqrt(planeSquared / magnitudeSquared) < 0.2) return null

    val roll = Math.toDegrees(atan2(screenX.toDouble(), screenY.toDouble())).toFloat()
    // A horizon is a line, so upside-down portrait has the same level position as upright.
    val angle = when {
        roll > 90f -> roll - 180f
        roll < -90f -> roll + 180f
        roll == 0f -> 0f
        else -> roll
    }
    val threshold = if (wasLevel) 3f else 2f
    return CameraLevel(angle, abs(angle) <= threshold)
}

/** Smooth vectors before deriving an angle so the +/-180-degree boundary cannot average to zero. */
internal class CameraLevelTracker {
    private var gravity: FloatArray? = null
    private var timestampNanos = 0L
    private var wasLevel = false

    fun update(x: Float, y: Float, z: Float, timestampNanos: Long, displayRotationDegrees: Int): CameraLevel? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            reset()
            return null
        }
        val elapsedSeconds = (timestampNanos - this.timestampNanos).toDouble() / 1_000_000_000
        val previous = gravity
        val filtered = if (previous == null || elapsedSeconds <= 0 || elapsedSeconds > 0.5) {
            floatArrayOf(x, y, z)
        } else {
            val weight = (elapsedSeconds / (0.16 + elapsedSeconds)).toFloat()
            previous.apply {
                this[0] += weight * (x - this[0])
                this[1] += weight * (y - this[1])
                this[2] += weight * (z - this[2])
            }
        }
        gravity = filtered
        this.timestampNanos = timestampNanos
        val level = cameraLevelFromGravity(filtered[0], filtered[1], filtered[2], displayRotationDegrees, wasLevel)
        wasLevel = level?.isLevel == true
        return level
    }

    fun reset() {
        gravity = null
        timestampNanos = 0L
        wasLevel = false
    }
}
