// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.PoseTipper
import com.lastpenguin.pix.generation.PoseTips
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val maker = FakeMaker()
    private val guides = FakeGuides()
    private lateinit var model: ReferenceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = ReferenceViewModel(maker, guides)
        store.put("reference", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun onCandidatePicked_personFound_isReadyToConfirm() = runTest {
        val guide = guide()

        model.onCandidatePicked(bitmap())
        assertEquals(ReferenceUiState.Working, model.uiState.value)
        runCurrent()
        maker.finish(Result.success(guide))
        runCurrent()

        assertEquals(listOf(GuideSource.GENERATED), maker.sources)
        assertEquals(ReferenceUiState.Ready(guide), model.uiState.value)
    }

    @Test
    fun replacing_guideOnTheCamera_isTrueOnlyThen() = runTest {
        assertEquals(false, model.replacing)

        guides.guide.value = guide()

        assertEquals(true, model.replacing)
    }

    @Test
    fun noPersonAndOtherFailuresAreTold() = runTest {
        model.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.failure(NoPersonFoundException()))
        runCurrent()
        assertEquals(ReferenceUiState.NoPersonFound, model.uiState.value)

        model.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.failure(IOException("Unreadable photo")))
        runCurrent()
        assertEquals(ReferenceUiState.Failed, model.uiState.value)
    }

    @Test
    fun onUseGuide_readyGuide_isStoredWithoutAnotherChange() = runTest {
        model.onUseGuide()
        assertNull(guides.stored)

        val guide = guide()
        model.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.success(guide))
        runCurrent()
        model.onUseGuide()

        // setGuide places it (FR-3.1); there is no style left to apply afterwards.
        assertSame(guide, guides.stored)
        assertEquals(emptyList<Boolean>(), guides.finals)
        assertEquals(ReferenceUiState.Idle, model.uiState.value)
    }

    @Test
    fun onUseGuide_realRepository_replacesTheGuideWithOneFinalChange() = runTest {
        val repository = InMemoryGuideRepository()
        val reference = ReferenceViewModel(maker, repository)
        store.put("integration-reference", reference)
        val previous = guide()
        repository.setGuide(previous)
        repository.update(final = true) {
            it.copy(cx = 0.7f, cy = 0.6f, height = 1.1f, opacity = 0.8f, visible = false)
        }
        val previousState = repository.state.value
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes.toList(changes)
        }

        val replacement = guide()
        reference.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.success(replacement))
        runCurrent()

        assertEquals(ReferenceUiState.Ready(replacement), reference.uiState.value)
        assertSame(previous, repository.guide.value)
        assertEquals(previousState, repository.state.value)
        assertEquals(emptyList<GuideChange>(), changes)

        reference.onUseGuide()
        runCurrent()

        val initialState = GuideState(guideId = replacement.id)
        assertSame(replacement, repository.guide.value)
        assertEquals(initialState, repository.state.value)
        assertEquals(listOf(GuideChange(initialState, final = true)), changes)
        assertEquals(ReferenceUiState.Idle, reference.uiState.value)
    }

    @Test
    fun aNewerPhotoReplacesOneStillInProgress() = runTest {
        model.onCandidatePicked(bitmap())
        runCurrent()
        val first = maker.pending.removeFirst()

        val second = guide()
        model.onCandidatePicked(bitmap())
        runCurrent()
        first.complete(Result.success(guide()))
        runCurrent()
        assertEquals(ReferenceUiState.Working, model.uiState.value)

        maker.finish(Result.success(second))
        runCurrent()
        assertEquals(ReferenceUiState.Ready(second), model.uiState.value)
    }

    @Test
    fun onCandidatePicked_withItsSceneAndPose_asksForItsTipOnceThePersonIsFound() = runTest {
        val tipper = RecordingTipper()
        val model = withTips(tipper)
        val candidate = bitmap()
        val scene = bitmap()
        val guide = guide()

        model.onCandidatePicked(candidate, TipSource(scene, "Walk past the railing"))
        runCurrent()
        assertEquals(emptyList<String>(), tipper.poses)
        maker.finish(Result.success(guide))
        runCurrent()

        assertEquals(ReferenceUiState.Ready(guide), model.uiState.value)
        assertEquals(listOf("Walk past the railing"), tipper.poses)
        assertSame(scene, tipper.scenes.single())
        assertSame(candidate, tipper.candidates.single())
    }

    @Test
    fun onCandidatePicked_withoutItsSource_orWithoutAPerson_asksForNoTip() = runTest {
        val tipper = RecordingTipper()
        val model = withTips(tipper)

        model.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.success(guide()))
        model.onCandidatePicked(bitmap(), TipSource(bitmap(), "Wave"))
        runCurrent()
        maker.finish(Result.failure(NoPersonFoundException()))
        runCurrent()

        assertEquals(ReferenceUiState.NoPersonFound, model.uiState.value)
        assertEquals(emptyList<String>(), tipper.poses)
    }

    @Test
    fun leavingWithoutUsingTheGuide_stopsTheAskForItsTip() = runTest {
        val tipper = RecordingTipper(waits = true)
        val model = withTips(tipper)
        model.onCandidatePicked(bitmap(), TipSource(bitmap(), "Wave"))
        runCurrent()
        maker.finish(Result.success(guide()))
        runCurrent()

        model.onConfirmLeft()
        runCurrent()

        assertEquals(listOf("Wave"), tipper.cancelled)
    }

    @Test
    fun anotherPhotoInPlaceOfTheGeneratedGuide_stopsTheAskForItsTip() = runTest {
        val tipper = RecordingTipper(waits = true)
        val model = withTips(tipper)
        model.onCandidatePicked(bitmap(), TipSource(bitmap(), "Wave"))
        runCurrent()
        maker.finish(Result.success(guide()))
        runCurrent()

        // A photo without a tip of its own; one from the gallery is in ReferenceConfirmFragmentTest, as a JVM Uri is null.
        model.onCandidatePicked(bitmap())
        runCurrent()

        assertEquals(listOf("Wave"), tipper.cancelled)
    }

    @Test
    fun leavingAfterUseThisGuide_keepsItsTip_thoughTheGuideIsNotOnCameraForThePoseTipsYet() = runTest {
        val tipper = RecordingTipper(waits = true)
        val camera = InMemoryGuideRepository()
        val tips = PoseTips(tipper, camera.guide, backgroundScope)
        val model = ReferenceViewModel(maker, camera, tips = tips)
        val guide = guide()
        model.onCandidatePicked(bitmap(), TipSource(bitmap(), "Wave"))
        runCurrent()
        maker.finish(Result.success(guide))
        runCurrent()

        // The tips follow the guide on their own scope, after the screen is left.
        model.onUseGuide()
        model.onConfirmLeft()
        runCurrent()

        assertEquals(emptyList<String>(), tipper.cancelled)
        assertEquals(GuideTip(guide.id, TipState.Preparing), tips.onCamera.value)
    }

    @Test
    fun leavingWhileThePersonIsBeingFound_stopsIt_andAsksForNoTip() = runTest {
        val tipper = RecordingTipper(waits = true)
        val model = withTips(tipper)
        model.onCandidatePicked(bitmap(), TipSource(bitmap(), "Wave"))
        runCurrent()

        model.onConfirmLeft()
        maker.finish(Result.success(guide()))
        runCurrent()

        assertEquals(ReferenceUiState.Idle, model.uiState.value)
        assertEquals(emptyList<String>(), tipper.poses)
    }

    private fun TestScope.withTips(tipper: PoseTipper) =
        ReferenceViewModel(maker, guides, tips = PoseTips(tipper, guides.guide, backgroundScope))

    /** Answers at once, or, with [waits], only when its ask is cancelled, which [cancelled] records by pose. */
    private class RecordingTipper(private val waits: Boolean = false) : PoseTipper {
        val scenes = mutableListOf<Bitmap>()
        val candidates = mutableListOf<Bitmap>()
        val poses = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override suspend fun tip(scene: Bitmap, candidate: Bitmap, pose: String): Result<PoseTip> {
            scenes += scene
            candidates += candidate
            poses += pose
            if (waits) {
                try {
                    awaitCancellation()
                } catch (error: CancellationException) {
                    cancelled += pose
                    throw error
                }
            }
            return Result.success(PoseTip("Hold the phone at your chest and keep it level."))
        }
    }

    private fun guide() = ReferenceGuide(
        id = "guide-${guideCount++}",
        mask = bitmap(),
        outline = bitmap(),
        aspect = 0.5f,
        source = GuideSource.GENERATED,
    )

    private var guideCount = 0

    private class FakeMaker : ReferenceGuideMaker {
        val pending = ArrayDeque<CompletableDeferred<Result<ReferenceGuide>>>()
        val sources = mutableListOf<GuideSource>()

        override suspend fun make(source: Uri): Result<ReferenceGuide> = next(GuideSource.GALLERY)

        override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> = next(source)

        fun finish(result: Result<ReferenceGuide>) {
            pending.removeFirst().complete(result)
        }

        private suspend fun next(source: GuideSource): Result<ReferenceGuide> {
            sources += source
            return CompletableDeferred<Result<ReferenceGuide>>().also(pending::addLast).await()
        }
    }

    private class FakeGuides : GuideRepository {
        override val guide = MutableStateFlow<ReferenceGuide?>(null)
        override val state = MutableStateFlow(GuideState())
        override val changes = MutableSharedFlow<GuideChange>()
        var stored: ReferenceGuide? = null
        val finals = mutableListOf<Boolean>()

        override fun setGuide(guide: ReferenceGuide?) {
            stored = guide
        }

        override fun update(final: Boolean, change: (GuideState) -> GuideState) {
            finals += final
            state.value = change(state.value)
        }
    }

    private companion object {
        /**
         * The JVM test stubs of android.jar cannot construct a Bitmap, and the view model only passes it along,
         * so this allocates one without running a constructor.
         */
        fun bitmap(): Bitmap {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
            return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Bitmap::class.java)
                as Bitmap
        }
    }
}
