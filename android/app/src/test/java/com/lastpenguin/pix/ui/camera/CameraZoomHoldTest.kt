// AI-generated with Claude Code, 2026-11-20, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.ZoomHold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Partitions for the photographer's zoom while the subject may zoom too (FR-7.6, #236), state-based on who holds it.
 * - A pinch while the zoom is free: applied, and the zoom is the photographer's until the pinch's final value.
 * - A pinch while the subject's pinch holds it: nothing applied and one label; held back to its final value even when
 *   the subject lets go meanwhile, and the next pinch zooms.
 * - A single final value (a quick step, a TalkBack action) while the subject holds the zoom: nothing applied, a label;
 *   while it is free: applied, and the zoom never the photographer's.
 * - The screen gone in the middle of a pinch: the zoom free.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CameraZoomHoldTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val camera = FakeCameraController()
    private val hold = ZoomHold()
    private val labels = mutableListOf<ToggleLabel>()
    private lateinit var model: CameraViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = CameraViewModel(camera, InMemoryGuideRepository(), zoomHold = hold)
        store.put("camera", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun pinch_zoomFree_holdsTheZoomUntilItsFinalValue() = runTest(dispatcher) {
        runCurrent()

        model.onZoomChanged(1.5f, final = false)
        assertEquals(Role.PHOTOGRAPHER, hold.holder.value)

        model.onZoomChanged(2f, final = true)
        assertNull(hold.holder.value)
        assertEquals(listOf(1.5f, 2f), camera.requests)
    }

    @Test
    fun pinch_subjectHolds_changesNothingAndSaysWhoIsZooming() = runTest(dispatcher) {
        backgroundScope.launch { model.toggleLabels.collect { labels += it } }
        runCurrent()
        hold.take(Role.SUBJECT)

        model.onZoomChanged(1.5f, final = false)
        runCurrent()
        model.onZoomChanged(1.8f, final = false)
        runCurrent()

        assertEquals(emptyList<Float>(), camera.requests)
        assertEquals(listOf<ToggleLabel>(ToggleLabel.ZoomHeld), labels)
        assertEquals(Role.SUBJECT, hold.holder.value)
    }

    @Test
    fun heldBackPinch_subjectLetsGo_staysHeldBackUntilItsFingersLift() = runTest(dispatcher) {
        runCurrent()
        hold.take(Role.SUBJECT)
        model.onZoomChanged(1.5f, final = false)

        hold.release(Role.SUBJECT)
        model.onZoomChanged(1.8f, final = false)
        model.onZoomChanged(2f, final = true)
        assertEquals(emptyList<Float>(), camera.requests)

        model.onZoomChanged(1.2f, final = false)
        model.onZoomChanged(1.3f, final = true)
        assertEquals(listOf(1.2f, 1.3f), camera.requests)
    }

    @Test
    fun quickStepAndTalkBack_subjectHolds_changeNothing() = runTest(dispatcher) {
        backgroundScope.launch { model.toggleLabels.collect { labels += it } }
        runCurrent()
        hold.take(Role.SUBJECT)

        model.onZoomTap()
        runCurrent()
        model.onZoomChanged(3f)
        runCurrent()

        assertEquals(emptyList<Float>(), camera.requests)
        assertEquals(listOf<ToggleLabel>(ToggleLabel.ZoomHeld, ToggleLabel.ZoomHeld), labels)
    }

    @Test
    fun quickStep_zoomFree_isAppliedWithoutTakingTheZoom() = runTest(dispatcher) {
        // Unconfined, so a hold taken and freed within one call would show too.
        val holders = mutableListOf<Role?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { hold.holder.collect { holders += it } }
        runCurrent()

        model.onZoomTap()
        runCurrent()

        assertEquals(listOf(2f), camera.requests)
        assertEquals(listOf<Role?>(null), holders)
    }

    @Test
    fun screenGone_midPinch_freesTheZoom() = runTest(dispatcher) {
        runCurrent()
        model.onZoomChanged(1.5f, final = false)

        store.clear()

        assertNull(hold.holder.value)
    }
}
