// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.fakeGuide
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * Partitions for *Save guide* on the Camera screen (FR-3.8), state-based, with the in-memory store.
 * onSaveGuide: a guide not saved yet (stored with its state, shown as saved, SAVED), a guide already saved
 * (ALREADY_SAVED, stored once), no guide (nothing), a full store (SAVED_OLDEST_REMOVED), a store that cannot write
 * (FAILED, not shown as saved, the button free again), a store that throws (the same, no crash), a second tap while
 * the first is writing (one save). A notice that comes while no screen collects waits for the screen.
 * guideSaved: a new guide (not saved), another guide replacing a saved one, a saved guide deleted from the store,
 * a saved guide put on the camera again with its id (saved).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveGuideViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val guides = InMemoryGuideRepository()
    private val saved = SavedGuideStore.InMemory()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(savedGuides: SavedGuideStore = saved): CameraViewModel =
        CameraViewModel(FakeCameraController(), guides, savedGuides = savedGuides).also { store.put("camera", it) }

    /** Collects the notices from now on. */
    private fun TestScope.notices(model: CameraViewModel): List<GuideSaveNotice> {
        val notices = mutableListOf<GuideSaveNotice>()
        backgroundScope.launch { model.guideSaveNotices.collect { notices += it } }
        runCurrent()
        return notices
    }

    @Test
    fun onSaveGuide_guideNotSavedYet_storesItWithItsStateAndShowsItSaved() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        guides.update(final = true) { it.copy(opacity = 0.3f) }
        runCurrent()
        val notices = notices(model)
        assertFalse(model.uiState.value.guideSaved)

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf("g1"), saved.saved.value.map { it.id })
        assertEquals(0.3f, saved.load("g1").getOrThrow().second.opacity, 0f)
        assertTrue(model.uiState.value.guideSaved)
        assertFalse(model.uiState.value.savingGuide)
        assertEquals(listOf(GuideSaveNotice.SAVED), notices)
    }

    @Test
    fun onSaveGuide_guideAlreadySaved_saysSoAndKeepsOneCopy() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        val notices = notices(model)
        model.onSaveGuide()
        runCurrent()

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf(GuideSaveNotice.SAVED, GuideSaveNotice.ALREADY_SAVED), notices)
        assertEquals(1, saved.saved.value.size)
    }

    @Test
    fun onSaveGuide_noGuide_doesNothing() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        val notices = notices(model)

        model.onSaveGuide()
        runCurrent()

        assertTrue(saved.saved.value.isEmpty())
        assertTrue(notices.isEmpty())
    }

    @Test
    fun onSaveGuide_storeFull_savesAndSaysTheOldestWasRemoved() = runTest(dispatcher) {
        repeat(SavedGuideStore.MAX_SAVED) { saved.save(fakeGuide("old$it"), GuideState()) }
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        val notices = notices(model)

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf(GuideSaveNotice.SAVED_OLDEST_REMOVED), notices)
        assertEquals(SavedGuideStore.MAX_SAVED, saved.saved.value.size)
        assertEquals("g1", saved.saved.value.first().id)
    }

    @Test
    fun onSaveGuide_storeCannotWrite_saysItFailedAndFreesTheButton() = runTest(dispatcher) {
        val model = model(FailingStore(saved))
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        val notices = notices(model)

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf(GuideSaveNotice.FAILED), notices)
        assertFalse(model.uiState.value.guideSaved)
        assertFalse(model.uiState.value.savingGuide)
    }

    @Test
    fun onSaveGuide_storeThrows_saysItFailedAndFreesTheButton() = runTest(dispatcher) {
        val model = model(ThrowingStore(saved))
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        val notices = notices(model)

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf(GuideSaveNotice.FAILED), notices)
        assertFalse(model.uiState.value.savingGuide)
    }

    @Test
    fun notice_whileNoScreenCollects_waitsForTheScreen() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        runCurrent()

        model.onSaveGuide()
        runCurrent()
        val notices = notices(model)

        assertEquals(listOf(GuideSaveNotice.SAVED), notices)
    }

    @Test
    fun onSaveGuide_secondTapWhileWriting_savesOnce() = runTest(dispatcher) {
        val slow = SlowStore(saved)
        val model = model(slow)
        guides.setGuide(fakeGuide("g1"))
        runCurrent()

        model.onSaveGuide()
        runCurrent()
        assertTrue(model.uiState.value.savingGuide)
        model.onSaveGuide()
        slow.release.complete(Unit)
        runCurrent()

        assertEquals(1, slow.calls)
        assertTrue(model.uiState.value.guideSaved)
    }

    @Test
    fun guideSaved_anotherGuideReplacesASavedOne_isFalse() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        model.onSaveGuide()
        runCurrent()

        guides.setGuide(fakeGuide("g2"))
        runCurrent()

        assertFalse(model.uiState.value.guideSaved)
    }

    @Test
    fun guideSaved_savedGuideDeletedFromTheStore_isFalse() = runTest(dispatcher) {
        val model = model()
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        model.onSaveGuide()
        runCurrent()

        saved.delete("g1")
        runCurrent()

        assertFalse(model.uiState.value.guideSaved)
    }

    @Test
    fun guideSaved_savedGuidePutOnTheCameraAgain_isTrue() = runTest(dispatcher) {
        saved.save(fakeGuide("g1"), GuideState())
        val model = model()
        runCurrent()

        guides.setGuide(saved.load("g1").getOrThrow().first)
        runCurrent()

        assertTrue(model.uiState.value.guideSaved)
    }

    /** A store whose writes fail, as when the phone's storage is full. */
    private class FailingStore(private val inner: SavedGuideStore) : SavedGuideStore by inner {
        override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> =
            Result.failure(IOException("No space left"))
    }

    /** A store whose writes throw instead of failing, as with a bitmap that was recycled. */
    private class ThrowingStore(private val inner: SavedGuideStore) : SavedGuideStore by inner {
        override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> =
            throw IllegalStateException("Can't compress a recycled bitmap")
    }

    /** A store whose writes wait for [release], so a second tap arrives while the first is writing. */
    private class SlowStore(private val inner: SavedGuideStore) : SavedGuideStore by inner {
        val release = CompletableDeferred<Unit>()
        var calls = 0

        override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> {
            calls++
            release.await()
            return inner.save(guide, state)
        }
    }
}
