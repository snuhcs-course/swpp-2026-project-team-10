// AI-generated with Claude Code, 2026-11-17, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import android.view.Surface
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * How the phone is held, in quarter turns clockwise from upright as its user sees it (FR-1.5, NFR-17): 0 upright, 90
 * with its left side up, 180 upside down, and 270 with its right side up. The screens stay portrait; the photos (#206)
 * and the controls that turn (#207) follow this one source.
 */

/** Turns on 0, 90, 180, and 270 from gravity, held still near 45° so a phone at the edge does not flicker. */
class PhoneTurnTracker {
    /** The last quarter turn; 0 until gravity says otherwise. */
    var turn: Int = 0
        private set

    /**
     * Gravity in the phone's axes ([x] to its right, [y] to its top, [z] out of its screen), as the accelerometer reads
     * it. A phone lying flat keeps its last turn; otherwise a new quarter wins once the phone is within
     * [SWITCH_DEGREES] of it, so between 35° and 55° from upright nothing changes.
     */
    fun update(x: Float, y: Float, z: Float): Int {
        if (FLAT_RATIO * (x * x + y * y) < z * z) return turn
        val angle = Math.floorMod(Math.toDegrees(atan2(-x.toDouble(), y.toDouble())).roundToTenths(), 3600) / 10f
        val nearest = Math.floorMod((angle / QUARTER).roundToInt() * QUARTER, FULL)
        if (nearest != turn && apart(angle, nearest) <= SWITCH_DEGREES) turn = nearest
        return turn
    }

    /** The degrees between [angle] and [quarter] the short way round. */
    private fun apart(angle: Float, quarter: Int): Float {
        val difference = abs(angle - quarter) % FULL
        return minOf(difference, FULL - difference)
    }

    private fun Double.roundToTenths(): Int = (this * 10).roundToInt()

    companion object {
        /** How close to a new quarter the phone must be to take it: 45° less a 10° margin on each side. */
        const val SWITCH_DEGREES = 35f

        /**
         * Below this, the screen faces up or down too squarely to say how the phone is turned: gravity across the
         * screen under half of gravity into it, about 27° from flat, as Android's own orientation listener does.
         */
        const val FLAT_RATIO = 4f
        private const val QUARTER = 90
        private const val FULL = 360
    }
}

/** The [Surface] rotation CameraX takes for a photo held at [turn] (`ImageCapture.setTargetRotation`). */
fun targetRotationFor(turn: Int): Int = when (Math.floorMod(turn, 360)) {
    90 -> Surface.ROTATION_270
    180 -> Surface.ROTATION_180
    270 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

/** The phone's turn while a camera screen is in front; one source for the photos (#206) and the controls (#207). */
interface PhoneTurns {
    /** 0, 90, 180, or 270 ([PhoneTurnTracker]); the last turn while nothing watches. */
    val turn: StateFlow<Int>

    /** Follows the phone while [owner] is resumed, since a sensor that runs behind the screen only drains the battery. */
    fun watch(owner: LifecycleOwner)

    /** Turns that a test or a phone without a sensor sets by hand; [watch] does nothing. */
    class Fixed(turn: Int = 0) : PhoneTurns {
        private val current = MutableStateFlow(turn)
        override val turn: StateFlow<Int> = current.asStateFlow()

        fun set(turn: Int) {
            current.value = turn
        }

        override fun watch(owner: LifecycleOwner) = Unit
    }
}

/** Adds and drops one watcher per resume of [owner]: [start] runs for the first and [stop] after the last. */
internal class Watchers(private val start: () -> Unit, private val stop: () -> Unit) {
    private var count = 0

    fun watch(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    if (count++ == 0) start()
                }

                override fun onPause(owner: LifecycleOwner) {
                    if (--count == 0) stop()
                }
            },
        )
    }
}
