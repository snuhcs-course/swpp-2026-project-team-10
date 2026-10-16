// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.fakeGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the guide's *Hide* / *Show* on the Camera screen (FR-3.6), a state-based test of the guide's
 * visibility: hide a placed guide in guide-edit mode, show it again, and the toggle without a guide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideControlsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val guides = InMemoryGuideRepository()
    private lateinit var model: CameraViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = CameraViewModel(FakeCameraController(), guides)
        store.put("camera", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun onHideToggle_placedGuideInEditMode_hidesItWithAFinalChangeAndEndsEditing() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1"))
        guides.update(final = true) { it.copy(cx = 0.3f, cy = 0.6f, height = 1.2f) }
        runCurrent()
        assertTrue(model.uiState.value.guideEditing)
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onHideToggle()
        runCurrent()

        assertFalse(guides.state.value.visible)
        assertEquals(listOf(true), changes.map { it.final })
        assertFalse(model.uiState.value.guideEditing)
    }

    @Test
    fun onHideToggle_hiddenGuide_comesBackWhereItWas() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1"))
        guides.update(final = true) { it.copy(cx = 0.3f, cy = 0.6f, height = 1.2f) }
        val placed = guides.state.value
        runCurrent()

        model.onHideToggle()
        model.onHideToggle()
        runCurrent()

        assertEquals(placed, guides.state.value)
        assertTrue(model.uiState.value.guideState.visible)
    }

    @Test
    fun onHideToggle_noGuide_changesNothing() = runTest(dispatcher) {
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onHideToggle()
        runCurrent()

        assertTrue(guides.state.value.visible)
        assertTrue(changes.isEmpty())
    }
}
