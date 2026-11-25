// AI-generated with Claude Code, 2026-11-25, reviewed by Dongje Park and Sungmin Jo
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the guide's controls on the Camera screen, state-based.
 * Hide / Show (FR-3.6): hide a placed guide in guide-edit mode, show it again, and the toggle without a guide.
 * Reference thumbnail (FR-3.9): enlarge and shrink a guide's reference; a guide without a reference and no guide
 * (stays small); the same guide's state changing (stays enlarged); a new guide and a removed guide (back to small);
 * a corner chosen by a drop, kept for the next guide.
 * Remove guide and its Undo (#298): a placed guide removed, with one notice, and put back as the same guide where it
 * was; Undo after another guide went on, after the guide came back, and twice (nothing); Remove with no guide (no
 * notice).
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
        // A new guide starts with the mode off (#304); the toggle turns it on.
        model.setGuideEditing(true)
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

    // ---- Hiding the reference thumbnail (#237), state-based: shown; hidden by a drop, with its corner; shown again
    // by a tap or by a drop in a corner; hidden while enlarged (small, and small when it is back); nothing to hide
    // without a reference; kept through the same guide's moves, hiding, and saved copy; shown again for another guide
    // and after the guide is removed.

    @Test
    fun onReferenceDropped_atASide_hidesItThereWithItsCorner_andInACornerShowsIt() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()
        assertFalse(model.uiState.value.referenceHidden)

        model.onReferenceDropped(ReferenceDrop(ReferenceCorner.BOTTOM_RIGHT, hidden = true))
        assertTrue(model.uiState.value.referenceHidden)
        assertEquals(ReferenceCorner.BOTTOM_RIGHT, model.uiState.value.referenceCorner)

        model.onReferenceDropped(ReferenceDrop(ReferenceCorner.TOP_RIGHT, hidden = false))
        assertFalse(model.uiState.value.referenceHidden)
        assertEquals(ReferenceCorner.TOP_RIGHT, model.uiState.value.referenceCorner)
    }

    @Test
    fun onReferenceTap_shownThumbnail_changesItsSize_andAHiddenOneComesBack() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()

        model.onReferenceTap()
        assertTrue(model.uiState.value.referenceExpanded)
        model.onReferenceTap()
        assertFalse(model.uiState.value.referenceExpanded)

        model.setReferenceHidden(true)
        assertTrue(model.uiState.value.referenceHidden)
        model.onReferenceTap()
        assertFalse(model.uiState.value.referenceHidden)
        assertFalse(model.uiState.value.referenceExpanded)
    }

    @Test
    fun referenceHidden_whileEnlarged_makesItSmall_andItComesBackSmall() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()

        // By TalkBack's action, and back by a tap.
        model.setReferenceExpanded(true)
        model.setReferenceHidden(true)
        assertTrue(model.uiState.value.referenceHidden)
        assertFalse(model.uiState.value.referenceExpanded)
        model.onReferenceTap()
        assertFalse(model.uiState.value.referenceHidden)
        assertFalse(model.uiState.value.referenceExpanded)

        // By a drag to the side, and back by a drag into a corner.
        model.setReferenceExpanded(true)
        model.onReferenceDropped(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = true))
        assertFalse(model.uiState.value.referenceExpanded)
        // Nothing enlarges it where it is hidden.
        model.setReferenceExpanded(true)
        assertFalse(model.uiState.value.referenceExpanded)
        model.onReferenceDropped(ReferenceDrop(ReferenceCorner.TOP_LEFT, hidden = false))
        assertFalse(model.uiState.value.referenceHidden)
        assertFalse(model.uiState.value.referenceExpanded)

        // Dropped in a corner, an enlarged one stays enlarged.
        model.setReferenceExpanded(true)
        model.onReferenceDropped(ReferenceDrop(ReferenceCorner.BOTTOM_LEFT, hidden = false))
        assertTrue(model.uiState.value.referenceExpanded)
    }

    @Test
    fun referenceHidden_noReferenceOrNoGuide_staysShown() = runTest(dispatcher) {
        model.setReferenceHidden(true)
        assertFalse(model.uiState.value.referenceHidden)

        guides.setGuide(fakeGuide("without-reference"))
        runCurrent()
        model.setReferenceHidden(true)
        model.onReferenceDropped(ReferenceDrop(ReferenceCorner.TOP_RIGHT, hidden = true))

        assertFalse(model.uiState.value.referenceHidden)
    }

    @Test
    fun referenceHidden_sameGuideMovesHidesOrIsSavedAgain_staysHidden() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()
        model.setReferenceHidden(true)

        guides.update(final = true) { it.copy(cx = 0.3f) }
        model.onHideToggle()
        // A saved copy is another object with the guide's id, and the same photo to the user.
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()

        assertTrue(model.uiState.value.referenceHidden)
    }

    @Test
    fun referenceHidden_newOrRemovedGuide_isShownAgain() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1").copy(reference = fakeBitmap()))
        runCurrent()
        model.setReferenceHidden(true)

        // Another guide has another reference, which the user has not seen yet.
        guides.setGuide(fakeGuide("g2").copy(reference = fakeBitmap()))
        runCurrent()
        assertFalse(model.uiState.value.referenceHidden)

        model.setReferenceHidden(true)
        model.onRemoveGuide()
        runCurrent()
        assertFalse(model.uiState.value.referenceHidden)
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

    // ---- Remove guide and its Undo (#298) ---------------------------------------------------

    @Test
    fun onRemoveGuide_thenUndo_putsTheSameGuideBackWhereItWas() = runTest(dispatcher) {
        val guide = fakeGuide("g1")
        guides.setGuide(guide)
        guides.update(final = true) { it.copy(cx = 0.3f, cy = 0.6f, height = 1.2f, opacity = 0.7f) }
        val placed = guides.state.value
        val notices = mutableListOf<Unit>()
        backgroundScope.launch { model.guideRemoved.collect { notices += it } }
        runCurrent()

        model.onRemoveGuide()
        runCurrent()
        assertNull(guides.guide.value)
        assertEquals(1, notices.size)

        model.onUndoRemoveGuide()
        runCurrent()
        assertSame(guide, guides.guide.value)
        assertEquals(placed, guides.state.value)
        assertSame(guide, model.uiState.value.guide)
    }

    @Test
    fun onUndoRemoveGuide_afterAnotherGuide_changesNothing() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        model.onRemoveGuide()
        val other = fakeGuide("g2")
        guides.setGuide(other)
        runCurrent()

        model.onUndoRemoveGuide()
        runCurrent()

        assertSame(other, guides.guide.value)
        // Nothing waits to come back once that guide is removed too.
        guides.setGuide(null)
        model.onUndoRemoveGuide()
        assertNull(guides.guide.value)
    }

    @Test
    fun onUndoRemoveGuide_twice_putsItBackOnce() = runTest(dispatcher) {
        val guide = fakeGuide("g1")
        guides.setGuide(guide)
        runCurrent()
        model.onRemoveGuide()
        model.onUndoRemoveGuide()
        runCurrent()
        guides.update(final = true) { it.copy(cx = 0.2f) }
        val moved = guides.state.value

        model.onUndoRemoveGuide()
        runCurrent()

        assertSame(guide, guides.guide.value)
        assertEquals(moved, guides.state.value)
    }

    @Test
    fun onRemoveGuide_noGuide_sendsNoNotice() = runTest(dispatcher) {
        val notices = mutableListOf<Unit>()
        backgroundScope.launch { model.guideRemoved.collect { notices += it } }
        runCurrent()

        model.onRemoveGuide()
        model.onUndoRemoveGuide()
        runCurrent()

        assertTrue(notices.isEmpty())
        assertNull(guides.guide.value)
    }
}
