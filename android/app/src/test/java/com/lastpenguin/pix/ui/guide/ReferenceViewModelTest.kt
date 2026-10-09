// AI-generated with ChatGPT Codex and Claude Code, 2026-10-05, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
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
    fun aPersonIsShownWithTheStyleTheCameraUsesNow() = runTest {
        guides.state.value = GuideState(style = GuideStyle.CUTOUT)
        val guide = guide()

        model.onCandidatePicked(bitmap())
        assertEquals(ReferenceUiState.Working, model.uiState.value)
        runCurrent()
        maker.finish(Result.success(guide))
        runCurrent()

        assertEquals(listOf(GuideSource.GENERATED), maker.sources)
        assertEquals(ReferenceUiState.Ready(guide, GuideStyle.CUTOUT), model.uiState.value)
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
    fun theSwitchChangesOnlyAReadyGuide() = runTest {
        model.onStylePreview(GuideStyle.CUTOUT)
        assertEquals(ReferenceUiState.Idle, model.uiState.value)

        val guide = guide()
        model.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.success(guide))
        runCurrent()
        model.onStylePreview(GuideStyle.CUTOUT)

        assertEquals(ReferenceUiState.Ready(guide, GuideStyle.CUTOUT), model.uiState.value)
    }

    @Test
    fun usingTheGuideStoresItWithTheChosenStyle() = runTest {
        model.onUseGuide()
        assertNull(guides.stored)

        val guide = guide()
        model.onCandidatePicked(bitmap())
        runCurrent()
        maker.finish(Result.success(guide))
        runCurrent()
        model.onStylePreview(GuideStyle.CUTOUT)
        model.onUseGuide()

        assertSame(guide, guides.stored)
        assertEquals(GuideStyle.CUTOUT, guides.state.value.style)
        assertEquals(listOf(true), guides.finals)
        assertEquals(ReferenceUiState.Idle, model.uiState.value)
    }

    @Test
    fun usingANewGuideResetsTheRealRepositoryAndPublishesTheConfirmedStyle() = runTest {
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
        reference.onStylePreview(GuideStyle.CUTOUT)

        assertEquals(ReferenceUiState.Ready(replacement, GuideStyle.CUTOUT), reference.uiState.value)
        assertSame(previous, repository.guide.value)
        assertEquals(previousState, repository.state.value)
        assertEquals(emptyList<GuideChange>(), changes)

        reference.onUseGuide()
        runCurrent()

        val initialState = GuideState(guideId = replacement.id)
        val confirmedState = initialState.copy(style = GuideStyle.CUTOUT)
        assertSame(replacement, repository.guide.value)
        assertEquals(confirmedState, repository.state.value)
        assertEquals(
            listOf(GuideChange(initialState, final = true), GuideChange(confirmedState, final = true)),
            changes,
        )
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
        assertEquals(ReferenceUiState.Ready(second, GuideStyle.OUTLINE), model.uiState.value)
    }

    private fun guide() = ReferenceGuide(
        id = "guide-${guideCount++}",
        cutout = bitmap(),
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
