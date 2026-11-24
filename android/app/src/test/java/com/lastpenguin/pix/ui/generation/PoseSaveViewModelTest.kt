// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.fakeBitmap
import com.lastpenguin.pix.session.fakeGuide
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The ♥ on Pick a pose (FR-4.14, #297), state-based, with a scripted guide maker, the in-memory store, and the real
 * PoseLibrary over them.
 * toggle (equivalence partitioning): an empty heart (Saving, then Saved with the guide kept in the library as a favorite
 * generated pose, SAVED, with the set's situation or none), a candidate the library kept already (marked, not cut out
 * again), a filled one (history again, still in the library, REMOVED), one still saving (ignored: one guide made), a
 * candidate without a person (nothing kept, NO_PERSON), a maker or a store that fails (nothing kept, FAILED).
 * Keys: the same pose id in another set (another heart); a guide deleted in the library (the heart empty again, and a
 * ♥ keeps it again without cutting it out again).
 * keptGuide: waits for the cut, the same guide for the ♥ and *Select*, cut once.
 * results: kept until read, so a ♥ finished after Pick a pose was left still tells its set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoseSaveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val maker = FakeMaker()
    private val saved = SavedGuideStore.InMemory()
    private val library = PoseLibrary(maker, saved, CoroutineScope(dispatcher))
    private lateinit var model: PoseSaveViewModel

    private val first = PoseKey("set-1", "pose-1")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = PoseSaveViewModel(library, saved)
        store.put("saves", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun generated(id: String) = ReferenceGuide(id, fakeBitmap(), fakeBitmap(), 0.75f, GuideSource.GENERATED)

    @Test
    fun anEmptyHeart_keepsTheCandidateAsAFavoriteGeneratedPose() = runTest {
        model.toggle(first, fakeBitmap())
        runCurrent()
        assertEquals(Heart.Saving, model.hearts.value[first])
        assertEquals(listOf(GuideSource.GENERATED), maker.sources)

        maker.finish(Result.success(generated("guide-1")))
        runCurrent()

        assertEquals(Heart.Saved("guide-1"), model.hearts.value[first])
        assertEquals(
            listOf(Triple("guide-1", GuideSource.GENERATED, true)),
            saved.saved.value.map { Triple(it.id, it.source, it.favorite) },
        )
        assertNull("no situation was given", saved.saved.value.single().situation)
        assertEquals(PoseSaveResult("set-1", PoseSaveNotice.SAVED), model.results.first())
    }

    @Test
    fun anEmptyHeart_keepsTheSetsSituationInTheLibrary() = runTest {
        model.toggle(first, fakeBitmap(), situation = Situation.FULL_BODY)
        runCurrent()
        maker.finish(Result.success(generated("guide-1")))
        runCurrent()

        assertEquals(listOf(Situation.FULL_BODY), saved.saved.value.map { it.situation })
        assertEquals(Situation.FULL_BODY, saved.load("guide-1").getOrThrow().first.situation)
    }

    @Test
    fun aCandidateTheLibraryKeptAlready_isMarkedWithoutCuttingItAgain() = runTest {
        library.keep("set-1", "pose-1", fakeBitmap())
        runCurrent()
        maker.finish(Result.success(generated("guide-1")))
        runCurrent()
        assertEquals(listOf(false), saved.saved.value.map { it.favorite })

        model.toggle(first, fakeBitmap())
        runCurrent()

        assertEquals(1, maker.sources.size)
        assertEquals(Heart.Saved("guide-1"), model.hearts.value[first])
        assertEquals(listOf(true), saved.saved.value.map { it.favorite })
    }

    @Test
    fun aFilledHeart_makesThePoseHistoryAgain() = runTest {
        saveFirst("guide-1")

        model.toggle(first, fakeBitmap())
        runCurrent()

        assertNull(model.hearts.value[first])
        assertEquals(listOf("guide-1" to false), saved.saved.value.map { it.id to it.favorite })
        val (_, removed) = model.results.take(2).toList()
        assertEquals(PoseSaveResult("set-1", PoseSaveNotice.REMOVED), removed)
    }

    @Test
    fun aSecondTapWhileSaving_isIgnored() = runTest {
        model.toggle(first, fakeBitmap())
        model.toggle(first, fakeBitmap())
        runCurrent()

        assertEquals(1, maker.pending.size)
        assertEquals(Heart.Saving, model.hearts.value[first])
    }

    @Test
    fun aCandidateWithoutAPerson_savesNothing() = runTest {
        model.toggle(first, fakeBitmap())
        runCurrent()
        maker.finish(Result.failure(NoPersonFoundException()))
        runCurrent()

        assertNull(model.hearts.value[first])
        assertEquals(emptyList<SavedGuide>(), saved.saved.value)
        assertEquals(PoseSaveResult("set-1", PoseSaveNotice.NO_PERSON), model.results.first())
    }

    @Test
    fun aMakerThatFails_savesNothing() = runTest {
        model.toggle(first, fakeBitmap())
        runCurrent()
        maker.finish(Result.failure(IOException("model not ready")))
        runCurrent()

        assertNull(model.hearts.value[first])
        assertEquals(PoseSaveResult("set-1", PoseSaveNotice.FAILED), model.results.first())
    }

    @Test
    fun aStoreThatFails_leavesTheHeartEmpty() = runTest {
        val failing = FailingStore()
        val failingModel = PoseSaveViewModel(PoseLibrary(maker, failing, CoroutineScope(dispatcher)), failing)
        store.put("failing", failingModel)

        failingModel.toggle(first, fakeBitmap())
        runCurrent()
        maker.finish(Result.success(generated("guide-1")))
        runCurrent()

        assertNull(failingModel.hearts.value[first])
        assertEquals(PoseSaveResult("set-1", PoseSaveNotice.FAILED), failingModel.results.first())
    }

    @Test
    fun theSamePoseInAnotherSet_isAnotherHeart() = runTest {
        saveFirst("guide-1")
        val next = PoseKey("set-2", "pose-1")

        assertNull(model.hearts.value[next])
        assertEquals(mapOf("pose-1" to Heart.Saved("guide-1")), model.hearts.value.inSet("set-1"))
        assertEquals(emptyMap<String, Heart>(), model.hearts.value.inSet("set-2"))

        model.toggle(next, fakeBitmap())
        runCurrent()
        maker.finish(Result.success(generated("guide-2")))
        runCurrent()

        assertEquals(Heart.Saved("guide-2"), model.hearts.value[next])
        assertEquals(listOf("guide-2", "guide-1"), saved.saved.value.map { it.id })
    }

    @Test
    fun aGuideDeletedInTheLibrary_emptiesItsHeart_andAHeartKeepsItAgainWithoutCutting() = runTest {
        saveFirst("guide-1")

        saved.delete("guide-1")
        runCurrent()
        assertNull(model.hearts.value[first])

        model.toggle(first, fakeBitmap())
        runCurrent()

        assertEquals(1, maker.sources.size)
        assertEquals(Heart.Saved("guide-1"), model.hearts.value[first])
        assertEquals(listOf("guide-1" to true), saved.saved.value.map { it.id to it.favorite })
    }

    @Test
    fun keptGuide_waitsForTheCut_andIsTheGuideTheHeartKept() = runTest {
        val guide = generated("guide-1")
        val kept = async { model.keptGuide(first, fakeBitmap()) }
        runCurrent()
        assertTrue(kept.isActive)

        maker.finish(Result.success(guide))
        runCurrent()
        model.toggle(first, fakeBitmap())
        runCurrent()

        assertSame(guide, kept.await().getOrThrow())
        assertEquals("cut out once", 1, maker.sources.size)
        assertEquals(Heart.Saved("guide-1"), model.hearts.value[first])
    }

    @Test
    fun results_waitUntilRead_eachForItsSet() = runTest {
        saveFirst("guide-1")
        val next = PoseKey("set-2", "pose-3")
        model.toggle(next, fakeBitmap())
        runCurrent()
        maker.finish(Result.failure(NoPersonFoundException()))
        runCurrent()

        assertEquals(
            listOf(
                PoseSaveResult("set-1", PoseSaveNotice.SAVED),
                PoseSaveResult("set-2", PoseSaveNotice.NO_PERSON),
            ),
            model.results.take(2).toList(),
        )
    }

    private fun TestScope.saveFirst(id: String) {
        model.toggle(first, fakeBitmap())
        runCurrent()
        maker.finish(Result.success(generated(id)))
        runCurrent()
    }

    private class FakeMaker : ReferenceGuideMaker {
        val pending = ArrayDeque<CompletableDeferred<Result<ReferenceGuide>>>()
        val sources = mutableListOf<GuideSource>()

        override suspend fun make(source: Uri, wholeImage: Boolean): Result<ReferenceGuide> = next(GuideSource.GALLERY)

        override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> = next(source)

        fun finish(result: Result<ReferenceGuide>) {
            pending.removeFirst().complete(result)
        }

        private suspend fun next(source: GuideSource): Result<ReferenceGuide> {
            sources += source
            return CompletableDeferred<Result<ReferenceGuide>>().also(pending::addLast).await()
        }
    }

    /** A library whose disk is full: nothing saves. */
    private class FailingStore : SavedGuideStore by SavedGuideStore.InMemory() {
        override suspend fun save(guide: ReferenceGuide, state: GuideState, favorite: Boolean): Result<SavedGuide> =
            Result.failure(IOException("No space left"))
    }
}
