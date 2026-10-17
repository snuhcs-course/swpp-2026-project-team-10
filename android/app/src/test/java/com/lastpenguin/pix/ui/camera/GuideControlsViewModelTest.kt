// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.fakeBitmap
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
 * Partitions for the guide's controls on the Camera screen, state-based.
 * Hide / Show (FR-3.6): hide a placed guide in guide-edit mode, show it again, and the toggle without a guide.
 * Reference thumbnail (FR-3.9): enlarge and shrink a guide's reference; a guide without a reference and no guide
 * (stays small); the same guide's state changing (stays enlarged); a new guide and a removed guide (back to small);
 * a corner chosen by a drop, kept for the next guide.
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
    fun setReferenceExpanded_guideWithAReference_enlargesAndShrinks() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()

        model.setReferenceExpanded(true)
        assertTrue(model.uiState.value.referenceExpanded)
        model.setReferenceExpanded(false)
        assertFalse(model.uiState.value.referenceExpanded)
    }

    @Test
    fun setReferenceExpanded_noReferenceOrNoGuide_staysSmall() = runTest(dispatcher) {
        model.setReferenceExpanded(true)
        assertFalse(model.uiState.value.referenceExpanded)

        guides.setGuide(fakeGuide("without-reference"))
        runCurrent()
        model.setReferenceExpanded(true)

        assertFalse(model.uiState.value.referenceExpanded)
    }

    @Test
    fun referenceExpanded_sameGuideMovesOrHides_staysEnlarged() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()
        model.setReferenceExpanded(true)

        guides.update(final = true) { it.copy(cx = 0.3f) }
        model.onHideToggle()
        runCurrent()

        assertTrue(model.uiState.value.referenceExpanded)
    }

    @Test
    fun referenceExpanded_newOrRemovedGuide_isSmallAgain() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()
        model.setReferenceExpanded(true)

        guides.setGuide(fakeGuide("g2").copy(reference = fakeBitmap()))
        runCurrent()
        assertFalse(model.uiState.value.referenceExpanded)

        model.setReferenceExpanded(true)
        model.onRemoveGuide()
        runCurrent()
        assertFalse(model.uiState.value.referenceExpanded)
    }

    @Test
    fun setReferenceCorner_thenANewGuide_keepsTheCorner() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()
        assertEquals(ReferenceCorner.TOP_LEFT, model.uiState.value.referenceCorner)

        model.setReferenceCorner(ReferenceCorner.BOTTOM_RIGHT)
        guides.setGuide(fakeGuide("g2").copy(reference = fakeBitmap()))
        runCurrent()

        assertEquals(ReferenceCorner.BOTTOM_RIGHT, model.uiState.value.referenceCorner)
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
