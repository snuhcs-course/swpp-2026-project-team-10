// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.fakeBitmap
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the saved guides on Add a pose guide (FR-3.8), state-based, with the in-memory store.
 * savedGuides: the store's list, newest first.
 * onUseSaved: a saved guide with no guide on the camera (on the camera, at its reference's place and not where it
 * was saved; USED), one replacing the guide on the camera, an id the store cannot load (FAILED, the camera's guide
 * kept), a guide whose values cannot place it (FAILED, no crash), a second tap while the first is opening (one load),
 * the sheet closing before it arrives (the camera's guide kept). A result that comes while no sheet collects waits.
 * onDeleteSaved: a saved guide (gone from the list) that is also on the camera (it stays there).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedGuidesViewModelTest {
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

    private fun model(savedGuides: SavedGuideStore = saved): ReferenceViewModel =
        ReferenceViewModel(UnusedMaker, guides, savedGuides).also { store.put("reference", it) }

    private fun TestScope.results(model: ReferenceViewModel): List<SavedGuideResult> {
        val results = mutableListOf<SavedGuideResult>()
        backgroundScope.launch { model.savedGuideResults.collect { results += it } }
        runCurrent()
        return results
    }

    /** A guide whose person stood left of center in the reference, 60 % of its height. */
    private fun anchored(id: String) = ReferenceGuide(
        id,
        fakeBitmap(),
        fakeBitmap(),
        0.5f,
        GuideSource.GALLERY,
        anchor = NormalizedRect(0.1f, 0.2f, 0.5f, 0.8f),
    )

    @Test
    fun savedGuides_store_listsItsGuidesNewestFirst() = runTest(dispatcher) {
        saved.save(fakeGuide("g1"), GuideState())
        saved.save(fakeGuide("g2"), GuideState())

        val model = model()

        assertEquals(listOf("g2", "g1"), model.savedGuides.value.map { it.id })
    }

    @Test
    fun onUseSaved_noGuideOnTheCamera_startsItWhereThePersonStandsInItsReference() = runTest(dispatcher) {
        // Saved after it was moved to the right and enlarged.
        saved.save(anchored("g1"), GuideState(guideId = "g1", cx = 0.8f, cy = 0.4f, height = 1.2f))
        val model = model()
        val results = results(model)

        model.onUseSaved("g1")
        runCurrent()

        assertEquals("g1", guides.guide.value?.id)
        assertEquals(0.3f, guides.state.value.cx, 1e-4f)
        assertEquals(0.5f, guides.state.value.cy, 1e-4f)
        assertEquals(0.6f, guides.state.value.height, 1e-4f)
        assertEquals(listOf(SavedGuideResult.USED), results)
    }

    @Test
    fun onUseSaved_guideOnTheCamera_replacesIt() = runTest(dispatcher) {
        saved.save(anchored("g1"), GuideState())
        guides.setGuide(fakeGuide("current"))
        val model = model()

        model.onUseSaved("g1")
        runCurrent()

        assertEquals("g1", guides.guide.value?.id)
    }

    @Test
    fun onUseSaved_guideTheStoreCannotLoad_failsAndKeepsTheCamerasGuide() = runTest(dispatcher) {
        guides.setGuide(fakeGuide("current"))
        val model = model()
        val results = results(model)

        model.onUseSaved("missing")
        runCurrent()

        assertEquals("current", guides.guide.value?.id)
        assertEquals(listOf(SavedGuideResult.FAILED), results)
    }

    @Test
    fun onUseSaved_secondTapWhileOpening_loadsOnce() = runTest(dispatcher) {
        saved.save(anchored("g1"), GuideState())
        val slow = SlowLoads(saved)
        val model = model(slow)
        val results = results(model)

        model.onUseSaved("g1")
        runCurrent()
        model.onUseSaved("g1")
        slow.release.complete(Unit)
        runCurrent()

        assertEquals(1, slow.loads)
        assertEquals(listOf(SavedGuideResult.USED), results)
    }

    @Test
    fun onUseSaved_guideThatCannotBePlaced_failsInsteadOfCrashing() = runTest(dispatcher) {
        saved.save(ReferenceGuide("g1", fakeBitmap(), fakeBitmap(), 0f, GuideSource.GALLERY), GuideState())
        guides.setGuide(fakeGuide("current"))
        val model = model()
        val results = results(model)

        model.onUseSaved("g1")
        runCurrent()

        assertEquals("current", guides.guide.value?.id)
        assertEquals(listOf(SavedGuideResult.FAILED), results)
    }

    @Test
    fun onUseSaved_sheetClosesBeforeItArrives_keepsTheCamerasGuide() = runTest(dispatcher) {
        saved.save(anchored("g1"), GuideState())
        guides.setGuide(fakeGuide("current"))
        val slow = SlowLoads(saved)
        val model = model(slow)
        val results = results(model)

        model.onUseSaved("g1")
        runCurrent()
        model.cancelOpening()
        slow.release.complete(Unit)
        runCurrent()

        assertEquals("current", guides.guide.value?.id)
        assertEquals(emptyList<SavedGuideResult>(), results)
    }

    @Test
    fun result_whileNoSheetCollects_waitsForTheSheet() = runTest(dispatcher) {
        saved.save(anchored("g1"), GuideState())
        val model = model()

        model.onUseSaved("g1")
        runCurrent()
        val results = results(model)

        assertEquals(listOf(SavedGuideResult.USED), results)
    }

    @Test
    fun onDeleteSaved_guideAlsoOnTheCamera_leavesTheListButStaysOnTheCamera() = runTest(dispatcher) {
        saved.save(anchored("g1"), GuideState())
        val model = model()
        model.onUseSaved("g1")
        runCurrent()

        model.onDeleteSaved("g1")
        runCurrent()

        assertEquals(emptyList<String>(), model.savedGuides.value.map { it.id })
        assertEquals("g1", guides.guide.value?.id)
    }

    @Test
    fun onDeleteSaved_unknownId_keepsTheList() = runTest(dispatcher) {
        saved.save(anchored("g1"), GuideState())
        val model = model()

        model.onDeleteSaved("g2")
        runCurrent()

        assertEquals(listOf("g1"), model.savedGuides.value.map { it.id })
        assertNull(guides.guide.value)
    }

    /** Saved guides never go through the maker. */
    private object UnusedMaker : ReferenceGuideMaker {
        override suspend fun make(source: Uri): Result<ReferenceGuide> = Result.failure(IOException("unused"))

        override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> =
            Result.failure(IOException("unused"))
    }

    /** A store whose loads wait for [release], so a second tap arrives while the first is opening. */
    private class SlowLoads(private val inner: SavedGuideStore) : SavedGuideStore by inner {
        val release = CompletableDeferred<Unit>()
        var loads = 0

        override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> {
            loads++
            release.await()
            return inner.load(id)
        }
    }
}
