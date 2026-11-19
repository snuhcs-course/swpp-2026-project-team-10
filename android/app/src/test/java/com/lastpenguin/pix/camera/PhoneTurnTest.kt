// AI-generated with Claude Code, 2026-11-17, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The phone's quarter turn from gravity (FR-1.5, #206), shared with the controls that turn (#207).
 * Partitions (equivalence): held upright, with its left side up (90), upside down (180), and with its right side up
 * (270), each from rest; a turn straight to the opposite side. Boundaries: a new quarter wins at 35° from it, not at
 * 36° (54° and 55° from upright, both ways, and across 0° / 360°); a phone lying flat keeps its turn, one tilted just
 * past flat follows. The CameraX rotation for each turn. Watchers: the sensor starts with the first screen resumed and
 * stops after the last one pauses.
 */
@RunWith(AndroidJUnit4::class)
class PhoneTurnTest {
    /** Gravity as the accelerometer reads it with the phone turned [degrees] clockwise, its screen upright. */
    private fun PhoneTurnTracker.held(degrees: Float, flatness: Float = 0f): Int {
        val radians = Math.toRadians(degrees.toDouble())
        return update((-G * sin(radians)).toFloat(), (G * cos(radians)).toFloat(), flatness)
    }

    @Test
    fun eachQuarter_fromRest_isTakenAsHeld() {
        for (quarter in listOf(0, 90, 180, 270)) {
            assertEquals("$quarter", quarter, PhoneTurnTracker().held(quarter.toFloat()))
        }
    }

    @Test
    fun aTurnStraightToTheOppositeSide_isTaken() {
        val tracker = PhoneTurnTracker()
        tracker.held(90f)

        assertEquals(270, tracker.held(270f))
    }

    @Test
    fun turningOver_takesTheNewQuarterAt55Degrees_notAt54() {
        val tracker = PhoneTurnTracker()

        assertEquals(0, tracker.held(54f))
        assertEquals(90, tracker.held(55f))
    }

    @Test
    fun turningBack_keepsTheQuarterUntil35Degrees_andTakesItAt35() {
        val tracker = PhoneTurnTracker()
        tracker.held(90f)

        assertEquals(90, tracker.held(36f))
        assertEquals(0, tracker.held(35f))
    }

    @Test
    fun turningTheOtherWay_acrossUpright_hasTheSameBand() {
        val tracker = PhoneTurnTracker()

        assertEquals(0, tracker.held(-54f))
        assertEquals(270, tracker.held(-55f))
        assertEquals(270, tracker.held(-36f))
        assertEquals(0, tracker.held(-35f))
    }

    @Test
    fun aPhoneLyingFlat_keepsItsTurn_andOneJustPastFlat_follows() {
        val tracker = PhoneTurnTracker()
        tracker.held(90f)

        // Gravity of 1 across the screen against 2.01 into it is flat; exactly 1 against 2 is not.
        assertEquals(90, tracker.update(0f, 1f, 2.01f))
        assertEquals(0, tracker.update(0f, 1f, 2f))
    }

    @Test
    fun eachTurn_givesCameraXTheRotationThatSavesThePhotoAsHeld() {
        assertEquals(Surface.ROTATION_0, targetRotationFor(0))
        assertEquals(Surface.ROTATION_270, targetRotationFor(90))
        assertEquals(Surface.ROTATION_180, targetRotationFor(180))
        assertEquals(Surface.ROTATION_90, targetRotationFor(270))
    }

    @Test
    fun theSensor_runsFromTheFirstScreenResumed_untilTheLastOnePauses() {
        val log = mutableListOf<String>()
        val watchers = Watchers(start = { log += "start" }, stop = { log += "stop" })
        val camera = owner()
        val subject = owner()
        watchers.watch(camera.first)
        watchers.watch(subject.first)

        camera.second.currentState = Lifecycle.State.RESUMED
        subject.second.currentState = Lifecycle.State.RESUMED
        camera.second.currentState = Lifecycle.State.STARTED
        assertEquals(listOf("start"), log)

        subject.second.currentState = Lifecycle.State.STARTED
        assertEquals(listOf("start", "stop"), log)
    }

    private fun owner(): Pair<LifecycleOwner, LifecycleRegistry> {
        lateinit var registry: LifecycleRegistry
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
        }
        registry = LifecycleRegistry.createUnsafe(owner).apply { currentState = Lifecycle.State.CREATED }
        return owner to registry
    }

    private companion object {
        const val G = 9.81
    }
}
