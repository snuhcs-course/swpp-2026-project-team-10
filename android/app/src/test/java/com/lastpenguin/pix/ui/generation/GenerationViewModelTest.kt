package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.FOUR_TEMPLATES
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseTemplate
import com.lastpenguin.pix.generation.testBitmap
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val generator = FakeGenerator()
    private val camera = FakeCamera()
    private val consent = FakeConsent()
    private lateinit var model: GenerationViewModel

    private val state get() = model.uiState.value

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = GenerationViewModel(generator, camera, consent, LIMIT_MS)
        store.put("generation", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun thePhotoIsTakenAndConfirmedBeforeAnythingIsSent() = runTest {
        model.beginScene()
        assertEquals(GenerationUiState(phase = GenerationUiState.Phase.FRAMING), state)

        model.takeScene()
        runCurrent()
        assertEquals(GenerationUiState(scene = camera.frame, phase = GenerationUiState.Phase.REVIEWING), state)
        assertTrue(generator.runs.isEmpty())

        model.useScene()
        assertEquals(GenerationUiState(scene = camera.frame, phase = GenerationUiState.Phase.GENERATING), state)
        runCurrent()
        assertSame(camera.frame, generator.runs.single().scene)
    }

    @Test
    fun shootAgainDropsThePhotoAndTakesAnother() = runTest {
        model.beginScene()
        model.takeScene()
        runCurrent()

        model.retakeScene()
        assertEquals(GenerationUiState(phase = GenerationUiState.Phase.FRAMING), state)

        val second = testBitmap()
        camera.frame = second
        model.takeScene()
        runCurrent()
        assertSame(second, state.scene)
        assertEquals(GenerationUiState.Phase.REVIEWING, state.phase)
        assertEquals(2, camera.grabs)
        assertTrue(generator.runs.isEmpty())
    }

    @Test
    fun aShutterWithoutAFrameIsToldAndCanBeRepeated() = runTest {
        camera.frame = null
        model.beginScene()

        model.takeScene()
        runCurrent()
        assertEquals(GenerationUiState(phase = GenerationUiState.Phase.FRAMING, shotFailed = true), state)

        camera.frame = testBitmap()
        model.takeScene()
        runCurrent()
        assertEquals(GenerationUiState(scene = camera.frame, phase = GenerationUiState.Phase.REVIEWING), state)
    }

    @Test
    fun theShutterAndTheConfirmationWorkOnlyInTheirStep() = runTest {
        // No scene photo was asked for yet.
        model.takeScene()
        model.useScene()
        model.retakeScene()
        runCurrent()
        assertEquals(GenerationUiState(), state)
        assertEquals(0, camera.grabs)

        // Two taps on the shutter take one photo, and there is nothing to confirm before it is there.
        model.beginScene()
        model.takeScene()
        model.takeScene()
        model.useScene()
        runCurrent()
        assertEquals(1, camera.grabs)
        assertEquals(GenerationUiState.Phase.REVIEWING, state.phase)
        assertTrue(generator.runs.isEmpty())

        // The shutter does nothing while the photo is shown.
        model.takeScene()
        runCurrent()
        assertEquals(1, camera.grabs)
    }

    @Test
    fun cancelWhileThePhotoIsBeingTakenDiscardsIt() = runTest {
        camera.hold = CompletableDeferred()
        model.beginScene()
        model.takeScene()
        runCurrent()

        model.cancel()
        camera.hold?.complete(Unit)
        runCurrent()

        assertEquals(GenerationUiState(), state)
    }

    @Test
    fun theSlotsFillAsCandidatesArrive() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        assertEquals(FOUR_TEMPLATES, state.templates)
        assertEquals(FOUR_TEMPLATES, run.templates)
        assertEquals(List(4) { null }, state.slots().map { it.event })

        val wave = ready("wave")
        run.send(wave)
        runCurrent()
        assertEquals(1, state.readyCount)
        assertEquals(listOf(null, wave, null, null), state.slots().map { it.event })
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)

        run.send(ready("hands_on_hips"), ready("arms_crossed"), failed("walking"))
        run.finish()
        runCurrent()
        assertEquals(3, state.readyCount)
        assertEquals(failed("walking"), state.candidates["walking"])
        // One pose failed; the other three can still be used (FR-4.7).
        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
    }

    @Test
    fun aSetWithoutAnyCandidateFailsAndKeepsTheScene() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()

        run.send(*FOUR_TEMPLATES.map { failed(it.id, GenerationError.TIMEOUT) }.toTypedArray())
        run.finish()
        runCurrent()

        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertSame(camera.frame, state.scene)
    }

    @Test
    fun templatesThatCannotBeFetchedFailWithoutARequest() = runTest {
        generator.templatesError = IOException("No route to host")

        generateFromAPhoto()

        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertSame(camera.frame, state.scene)
        assertTrue(generator.runs.isEmpty())
    }

    @Test
    fun aGeneratorThatBreaksStillShowsWhatArrived() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()

        run.send(ready("wave"))
        run.finish(IllegalStateException("Unexpected"))
        runCurrent()

        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
        assertEquals(1, state.readyCount)
    }

    @Test
    fun oneLimitCoversTheTemplatesAndTheRequests() = runTest {
        // The templates take two thirds of the limit; the requests get only what is left of it.
        generator.templatesDelayMs = 20_000
        generateFromAPhoto()
        advanceTimeBy(20_000)
        runCurrent()
        val run = generator.runs.single()
        run.send(ready("wave"))

        advanceTimeBy(LIMIT_MS - 20_000 - 1)
        runCurrent()
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
        assertTrue(run.cancelled)
        assertEquals(1, state.readyCount)
        assertEquals(failed("walking", GenerationError.TIMEOUT), state.candidates["walking"])
        assertEquals(FOUR_TEMPLATES.size, state.candidates.size)
    }

    @Test
    fun aRunWithoutAnyAnswerFailsAtTheLimit() = runTest {
        generateFromAPhoto()

        advanceTimeBy(LIMIT_MS)
        runCurrent()

        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertSame(camera.frame, state.scene)
        assertTrue(generator.runs.single().cancelled)
    }

    @Test
    fun retryResendsTheSameSceneWithANewSeed() = runTest {
        generateFromAPhoto()
        generator.runs.single().run {
            send(ready("wave"))
            finish()
        }
        runCurrent()
        model.select("wave")
        val scene = state.scene

        model.retry()
        // The earlier candidates and the selection are gone as soon as the new set starts.
        assertEquals(GenerationUiState(scene = scene, phase = GenerationUiState.Phase.GENERATING), state)
        runCurrent()

        assertEquals(2, generator.runs.size)
        assertSame(scene, generator.runs[1].scene)
        assertNotEquals(generator.runs[0].seed, generator.runs[1].seed)
        assertEquals(1, camera.grabs)
    }

    @Test
    fun retryWithoutAPhotoDoesNothing() = runTest {
        model.retry()
        runCurrent()

        assertEquals(GenerationUiState(), state)
        assertTrue(generator.runs.isEmpty())
    }

    @Test
    fun cancelStopsTheRequestsAndDiscardsEverything() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        run.send(ready("wave"))
        runCurrent()

        model.cancel()
        runCurrent()

        assertTrue(run.cancelled)
        assertEquals(GenerationUiState(), state)
    }

    @Test
    fun aNewScenePhotoStopsTheRequestsStillInProgress() = runTest {
        generateFromAPhoto()

        model.beginScene()
        runCurrent()

        assertTrue(generator.runs.single().cancelled)
        assertEquals(GenerationUiState(phase = GenerationUiState.Phase.FRAMING), state)
    }

    @Test
    fun onlyAFinishedCandidateCanBeSelectedAfterGenerationEnded() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        val wave = ready("wave")
        run.send(wave, failed("walking"))
        runCurrent()

        // Still generating: nothing can be selected yet.
        model.select("wave")
        assertNull(state.selectedTemplateId)

        run.finish()
        runCurrent()
        model.select("walking")
        model.select("hands_on_hips")
        assertNull(state.selectedTemplateId)
        assertNull(state.selectedImage)

        model.select("wave")
        assertEquals("wave", state.selectedTemplateId)
        assertSame(wave.image, state.selectedImage)
        assertEquals(listOf(false, true, false, false), state.slots().map { it.selected })
    }

    @Test
    fun consentIsNeededUntilItIsGivenAndIsThenStored() = runTest {
        // Before the stored answer has loaded, the notice is still required.
        assertTrue(model.needsConsent)
        runCurrent()
        assertTrue(model.needsConsent)

        model.onConsentGiven()
        assertFalse(model.needsConsent)
        runCurrent()
        assertTrue(consent.given)
    }

    @Test
    fun consentGivenEarlierIsNotAskedAgain() = runTest {
        consent.given = true
        val later = GenerationViewModel(generator, camera, consent, LIMIT_MS)
        store.put("later", later)

        runCurrent()

        assertFalse(later.needsConsent)
    }

    /** *Generate poses here*, the shutter, and *Use this photo*. */
    private fun TestScope.generateFromAPhoto() {
        model.beginScene()
        model.takeScene()
        runCurrent()
        model.useScene()
        runCurrent()
    }

    private fun ready(templateId: String) = CandidateEvent.Ready(templateId, testBitmap())

    private fun failed(templateId: String, error: GenerationError = GenerationError.SERVER) =
        CandidateEvent.Failed(templateId, error)

    /** Each call to generate is a [Run] the test feeds with events. */
    private class FakeGenerator : PoseGenerator {
        var templatesError: Exception? = null
        var templatesDelayMs = 0L
        val runs = mutableListOf<Run>()

        override suspend fun templates(): List<PoseTemplate> {
            delay(templatesDelayMs)
            templatesError?.let { throw it }
            return FOUR_TEMPLATES
        }

        override fun generate(scene: Bitmap, templates: List<PoseTemplate>, seed: Long): Flow<CandidateEvent> {
            val run = Run(scene, templates, seed).also(runs::add)
            return flow {
                try {
                    for (event in run.events) emit(event)
                } catch (error: CancellationException) {
                    run.cancelled = true
                    throw error
                }
            }
        }

        class Run(val scene: Bitmap, val templates: List<PoseTemplate>, val seed: Long) {
            val events = Channel<CandidateEvent>(Channel.UNLIMITED)
            var cancelled = false

            fun send(vararg sent: CandidateEvent) {
                for (event in sent) check(events.trySend(event).isSuccess)
            }

            fun finish(error: Exception? = null) {
                events.close(error)
            }
        }
    }

    private class FakeCamera : CameraController {
        var frame: Bitmap? = testBitmap()
        var grabs = 0

        /** When set, the frame is given only after this completes. */
        var hold: CompletableDeferred<Unit>? = null

        override val status = MutableStateFlow(CameraStatus.READY)
        override val capabilities = MutableStateFlow<CameraCapabilities?>(null)
        override val zoom = MutableStateFlow(1f)

        override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit

        override fun setZoom(ratio: Float) = Unit

        override suspend fun takePhoto(): Result<Uri> = Result.failure(UnsupportedOperationException())

        override suspend fun grabFrame(): Result<Bitmap> {
            grabs++
            hold?.await()
            return frame?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("No live camera frame is available"))
        }

        override fun setFrameSink(sink: FrameSink?) = Unit
    }

    private companion object {
        const val LIMIT_MS = 30_000L
    }

    private class FakeConsent : GenerationConsent {
        var given = false

        override suspend fun isGiven(): Boolean = given

        override suspend fun give() {
            given = true
        }
    }
}
