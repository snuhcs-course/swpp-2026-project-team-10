// AI-generated with Claude Code, 2026-10-30, reviewed by Joonhyung Han, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.CameraState
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.GenerationEvent
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.generation.SUGGESTED
import com.lastpenguin.pix.generation.planned
import com.lastpenguin.pix.generation.testBitmap
import com.lastpenguin.pix.session.DescriptionRequest
import com.lastpenguin.pix.session.DescriptionShare
import com.lastpenguin.pix.session.PoseShare
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SubjectPick
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val share = RecordingShare()
    private val descriptions = RecordingDescriptions()
    private lateinit var model: GenerationViewModel

    private val state get() = model.uiState.value

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = GenerationViewModel(generator, camera, consent, LIMIT_MS, share, descriptions)
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

        // Generate poses asks what to pose; still nothing is sent (FR-4.8).
        model.useScene()
        assertEquals(GenerationUiState(scene = camera.frame, phase = GenerationUiState.Phase.DESCRIBING), state)
        runCurrent()
        assertTrue(generator.runs.isEmpty())

        model.generate("벽에 기대서 웃기")
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)
        runCurrent()
        val run = generator.runs.single()
        assertSame(camera.frame, run.scene)
        assertEquals(PoseRequest.Described("벽에 기대서 웃기"), run.request)
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
    fun theLightIsOnOnlyWhileTheSceneIsFramedWithItTurnedOn() = runTest {
        model.beginScene()
        runCurrent()
        assertFalse(camera.torchOn)

        model.toggleLight()
        runCurrent()
        assertTrue(state.light)
        assertTrue(camera.torchOn)

        // Off for Use this photo?, and on again for Shoot again, which keeps the choice.
        model.takeScene()
        runCurrent()
        assertFalse(camera.torchOn)
        model.retakeScene()
        runCurrent()
        assertTrue(state.light)
        assertTrue(camera.torchOn)

        model.toggleLight()
        runCurrent()
        assertFalse(camera.torchOn)
    }

    @Test
    fun theLightGoesOffWhenTheStepIsLeft_andStartsOffTheNextTime() = runTest {
        model.beginScene()
        model.toggleLight()
        runCurrent()
        model.takeScene()
        runCurrent()
        model.retakeScene()
        runCurrent()
        assertTrue(camera.torchOn)

        model.cancel()
        runCurrent()
        assertFalse(camera.torchOn)

        model.beginScene()
        runCurrent()
        assertFalse(state.light)
        assertFalse(camera.torchOn)
    }

    @Test
    fun theLightButtonDoesNothingButWhileTheSceneIsFramed() = runTest {
        model.toggleLight()
        model.beginScene()
        model.takeScene()
        runCurrent()
        model.toggleLight()
        runCurrent()

        assertFalse(state.light)
        assertFalse(camera.torchOn)
        model.useScene()
        model.toggleLight()
        runCurrent()
        assertFalse(state.light)
    }

    @Test
    fun theLightGoesOffWithTheModel() = runTest {
        model.beginScene()
        model.toggleLight()
        runCurrent()
        assertTrue(camera.torchOn)

        store.clear()

        assertFalse(camera.torchOn)
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
        model.generate("Wave")
        model.suggestPoses()
        runCurrent()
        assertEquals(GenerationUiState(), state)
        assertEquals(0, camera.grabs)

        // Two taps on the shutter take one photo, and there is nothing to confirm or generate before it is there.
        model.beginScene()
        model.takeScene()
        model.takeScene()
        model.useScene()
        model.generate("Wave")
        runCurrent()
        assertEquals(1, camera.grabs)
        assertEquals(GenerationUiState.Phase.REVIEWING, state.phase)
        assertTrue(generator.runs.isEmpty())

        // The shutter does nothing while the photo is shown, and nothing is generated before Generate poses.
        model.takeScene()
        model.generate("Wave")
        model.suggestPoses()
        runCurrent()
        assertEquals(1, camera.grabs)
        assertTrue(generator.runs.isEmpty())
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
    fun aBlankDescriptionAsksForSuggestionsAndWhitespaceIsFolded() = runTest {
        describeAPhoto()
        model.generate(" \n\t ")
        runCurrent()
        assertEquals(PoseRequest.Suggested, generator.runs.last().request)

        model.cancel()
        describeAPhoto()
        model.generate("  벽에\n기대서   웃기 ")
        runCurrent()
        assertEquals(PoseRequest.Described("벽에 기대서 웃기"), generator.runs.last().request)
    }

    @Test
    fun skipAsksForSuggestionsWhateverTheFieldHolds() = runTest {
        describeAPhoto()
        model.onDescriptionChanged("Wave")

        model.suggestPoses()
        runCurrent()

        assertEquals(PoseRequest.Suggested, generator.runs.single().request)
    }

    @Test
    fun theDescriptionIsKeptOnlyOnGeneratePoses() = runTest {
        model.onDescriptionChanged("Wave")
        assertEquals("", state.description)

        describeAPhoto()
        model.onDescriptionChanged("Wave")
        assertEquals("Wave", state.description)
    }

    @Test
    fun theSlotsAppearWithThePosesAndFillAsCandidatesArrive() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        // The server is still choosing the poses: no slots yet.
        assertTrue(state.slots().isEmpty())

        run.plan()
        runCurrent()
        assertEquals(planned(SUGGESTED), state.poses)
        assertEquals(List(4) { null }, state.slots().map { it.event })

        val second = ready("pose-2")
        run.send(second)
        runCurrent()
        assertEquals(1, state.readyCount)
        assertEquals(listOf(null, second, null, null), state.slots().map { it.event })
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)

        run.send(ready("pose-1"), ready("pose-4"), failed("pose-3"))
        run.finish()
        runCurrent()
        assertEquals(3, state.readyCount)
        assertEquals(failed("pose-3"), state.candidates["pose-3"])
        // One pose failed; the other three can still be used (FR-4.7).
        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
    }

    @Test
    fun aSetWithoutAnyCandidateFailsAndKeepsTheScene() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()

        run.plan()
        run.send(*(1..4).map { failed("pose-$it", GenerationError.TIMEOUT) }.toTypedArray())
        run.finish()
        runCurrent()

        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertSame(camera.frame, state.scene)
    }

    @Test
    fun aRejectedDescriptionFailsAndCanBeEditedWithItsText() = runTest {
        describeAPhoto()
        model.onDescriptionChanged("피 흘리는 모습")
        model.generate("피 흘리는 모습")
        runCurrent()
        val run = generator.runs.single()

        run.plan(List(4) { "피 흘리는 모습" })
        run.send(*(1..4).map { failed("pose-$it", GenerationError.REJECTED) }.toTypedArray())
        run.finish()
        runCurrent()
        // Couldn't create poses offers a different description or another photo (US-5).
        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertTrue(state.descriptionRejected)

        // Edit description: Generate poses with the text and the photo kept.
        model.describeAgain()
        assertEquals(GenerationUiState.Phase.DESCRIBING, state.phase)
        assertEquals("피 흘리는 모습", state.description)
        assertSame(camera.frame, state.scene)
        assertTrue(state.slots().isEmpty())

        // The next set clears the message.
        model.generate("Wave")
        assertFalse(state.descriptionRejected)
    }

    @Test
    fun tryOtherPosesGoesBackToGeneratePosesWithThePhotoAndTheText() = runTest {
        describeAPhoto()
        model.onDescriptionChanged("Wave")
        model.generate("Wave")
        runCurrent()
        generator.runs.single().run {
            plan(List(4) { "Wave" })
            send(ready("pose-1"))
            finish()
        }
        runCurrent()
        model.select("pose-1")
        val scene = state.scene

        model.describeAgain()
        runCurrent()

        // The set and the selection are gone, and nothing is sent until the user asks again (FR-4.6).
        assertEquals(GenerationUiState.Phase.DESCRIBING, state.phase)
        assertSame(scene, state.scene)
        assertEquals("Wave", state.description)
        assertTrue(state.candidates.isEmpty() && state.poses.isEmpty())
        assertNull(state.selectedPoseId)
        assertFalse(state.descriptionRejected)
        assertEquals(1, generator.runs.size)

        // The next set can ask for something else.
        model.suggestPoses()
        runCurrent()
        assertEquals(PoseRequest.Suggested, generator.runs[1].request)
        assertSame(scene, generator.runs[1].scene)
        assertEquals(1, camera.grabs)
    }

    @Test
    fun describeAgainWorksOnlyOnceASetHasEnded() = runTest {
        model.describeAgain()
        assertEquals(GenerationUiState(), state)

        describeAPhoto()
        val describing = state
        model.describeAgain()
        assertEquals(describing, state)

        model.generate("Wave")
        val generating = state
        model.describeAgain()
        assertEquals(generating, state)
    }

    @Test
    fun aDescriptionRejectedOnlyInPartStillShowsTheOthers() = runTest {
        describeAPhoto()
        model.generate("Wave")
        runCurrent()
        val run = generator.runs.single()

        run.plan(List(4) { "Wave" })
        run.send(ready("pose-1"), failed("pose-2", GenerationError.REJECTED))
        run.send(failed("pose-3", GenerationError.REJECTED), failed("pose-4", GenerationError.REJECTED))
        run.finish()
        runCurrent()

        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
        assertFalse(state.descriptionRejected)
    }

    @Test
    fun rejectedSuggestionsFailLikeAnyOtherSet() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()

        run.plan()
        run.send(*(1..4).map { failed("pose-$it", GenerationError.REJECTED) }.toTypedArray())
        run.finish()
        runCurrent()

        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertFalse(state.descriptionRejected)
    }

    @Test
    fun posesThatCannotBeHadFailWithoutACandidate() = runTest {
        generateFromAPhoto()

        generator.runs.single().finish(IOException("No route to host"))
        runCurrent()

        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        assertSame(camera.frame, state.scene)
        assertTrue(state.poses.isEmpty())
    }

    @Test
    fun aGeneratorThatBreaksStillShowsWhatArrived() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()

        run.plan()
        run.send(ready("pose-2"))
        run.finish(IllegalStateException("Unexpected"))
        runCurrent()

        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
        assertEquals(1, state.readyCount)
    }

    @Test
    fun oneLimitCoversTheSuggestionsAndTheRequests() = runTest {
        // The suggestions take two thirds of the limit; the requests get only what is left of it.
        generateFromAPhoto()
        val run = generator.runs.single()
        advanceTimeBy(20_000)
        run.plan()
        run.send(ready("pose-2"))

        advanceTimeBy(LIMIT_MS - 20_000 - 1)
        runCurrent()
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(GenerationUiState.Phase.PICKING, state.phase)
        assertTrue(run.cancelled)
        assertEquals(1, state.readyCount)
        assertEquals(failed("pose-3", GenerationError.TIMEOUT), state.candidates["pose-3"])
        assertEquals(4, state.candidates.size)
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
    fun retryAsksForTheSameRequestWithANewSeed() = runTest {
        describeAPhoto()
        model.generate("Wave")
        runCurrent()
        generator.runs.single().run {
            plan(List(4) { "Wave" })
            send(failed("pose-1"))
            finish()
        }
        runCurrent()
        assertEquals(GenerationUiState.Phase.FAILED, state.phase)
        val scene = state.scene

        // Try again on Couldn't create poses: the earlier set is gone as soon as the new one starts.
        model.retry()
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)
        assertTrue(state.candidates.isEmpty() && state.poses.isEmpty())
        runCurrent()

        assertEquals(2, generator.runs.size)
        assertSame(scene, generator.runs[1].scene)
        assertEquals(PoseRequest.Described("Wave"), generator.runs[1].request)
        assertNotEquals(generator.runs[0].seed, generator.runs[1].seed)
        assertEquals(1, camera.grabs)
    }

    @Test
    fun retryOfSuggestionsAsksForNewSuggestions() = runTest {
        generateFromAPhoto()
        generator.runs.single().finish(IOException("No route to host"))
        runCurrent()

        model.retry()
        runCurrent()

        assertEquals(listOf(PoseRequest.Suggested, PoseRequest.Suggested), generator.runs.map { it.request })
    }

    @Test
    fun retryWithoutAPhotoDoesNothing() = runTest {
        model.retry()
        runCurrent()

        assertEquals(GenerationUiState(), state)
        assertTrue(generator.runs.isEmpty())
    }

    @Test
    fun cancelOnGeneratePosesDiscardsThePhoto() = runTest {
        describeAPhoto()

        model.cancel()

        assertEquals(GenerationUiState(), state)
        assertTrue(generator.runs.isEmpty())
    }

    @Test
    fun cancelStopsTheRequestsAndDiscardsEverything() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        run.plan()
        run.send(ready("pose-1"))
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
        val second = ready("pose-2")
        run.plan()
        run.send(second, failed("pose-3"))
        runCurrent()

        // Still generating: nothing can be selected yet.
        model.select("pose-2")
        assertNull(state.selectedPoseId)

        run.finish()
        runCurrent()
        model.select("pose-3")
        model.select("pose-1")
        assertNull(state.selectedPoseId)
        assertNull(state.selectedImage)

        model.select("pose-2")
        assertEquals("pose-2", state.selectedPoseId)
        assertSame(second.image, state.selectedImage)
        assertEquals(listOf(false, true, false, false), state.slots().map { it.selected })
    }

    @Test
    fun theSelectedCandidatesTipIsAskedFromTheSceneAndItsPose() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        run.plan()
        run.send(ready("pose-1"), ready("pose-2"))
        run.finish()
        runCurrent()
        assertNull(state.tipSource)

        model.select("pose-2")

        val source = state.tipSource!!
        assertSame(state.scene, source.scene)
        assertEquals(SUGGESTED[1], source.pose)
    }

    // Selfie mode (FR-1.14): a scene photo from the front camera is generated as a selfie, kept as one for Try again,
    // and gets no tip; one from the rear camera is not a selfie.
    @Test
    fun aScenePhotoFromTheFrontCamera_isGeneratedAsASelfie_andAgainOnTryAgain() = runTest {
        camera.facing.value = CameraFacing.FRONT
        generateFromAPhoto()
        assertTrue(state.selfie)
        generator.runs.single().finish()
        runCurrent()
        // The user turned back to the rear camera meanwhile; the photo is still a selfie.
        camera.facing.value = CameraFacing.BACK

        model.retry()
        runCurrent()

        assertEquals(listOf(true, true), generator.runs.map { it.selfie })
    }

    @Test
    fun aScenePhotoFromTheRearCamera_isNotASelfie() = runTest {
        generateFromAPhoto()

        assertFalse(state.selfie)
        assertFalse(generator.runs.single().selfie)
    }

    @Test
    fun aSelfiesCandidate_hasNoTipSource() = runTest {
        camera.facing.value = CameraFacing.FRONT
        generateFromAPhoto()
        val run = generator.runs.single()
        run.plan()
        run.send(ready("pose-1"))
        run.finish()
        runCurrent()

        model.select("pose-1")

        assertEquals("pose-1", state.selectedPoseId)
        assertNull(state.tipSource)
    }

    // ---- the set on the subject's phone (Design 2.6.5, Iter3-P16) -------------------------------------------------
    // Partitions: each phase change is told (a new set, each created candidate, Pick a pose with the pager's order, an
    // end without a choice from GENERATING, PICKING, or FAILED); the first choice wins (this phone's own, the
    // subject's pick of this set); a pick is ignored for another set, while generating, for a candidate that failed,
    // and after a choice; a new set gets a new id.

    @Test
    fun eachPhaseOfTheSetIsToldToTheSubjectsPhone() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        run.plan()
        run.send(ready("pose-3"), failed("pose-2"), ready("pose-1"))
        run.finish()
        runCurrent()

        val set = share.opened.single()
        assertEquals(
            listOf("open $set", "candidate pose-3", "candidate pose-1", "picking pose-1,pose-3"),
            share.calls,
        )
    }

    @Test
    fun aSetThatEndsWithoutAChoiceIsClosed() = runTest {
        generateFromAPhoto()
        model.cancel()
        generateFromAPhoto()
        generator.runs.last().finish()
        runCurrent()
        model.describeAgain()
        model.suggestPoses()
        runCurrent()
        generator.runs.last().plan()
        generator.runs.last().send(ready("pose-1"))
        generator.runs.last().finish()
        runCurrent()
        model.describeAgain()

        val (first, second, third) = share.opened
        assertEquals(
            listOf("close $first", "close $second", "close $third"),
            share.calls.filter {
                it.startsWith("close")
            },
        )
        assertEquals(3, share.opened.toSet().size)
    }

    @Test
    fun usingAPoseChoosesItHereAndNothingElseCanBeChosen() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        val second = ready("pose-2")
        run.plan()
        run.send(ready("pose-1"), second)
        run.finish()
        runCurrent()
        model.select("pose-2")

        val use = model.usePose()

        assertSame(second.image, use?.image)
        assertEquals(SUGGESTED[1], use?.tipSource?.pose)
        assertEquals(Role.PHOTOGRAPHER, state.chosenBy)
        assertEquals("chosen pose-2 by PHOTOGRAPHER", share.calls.last())
        assertNull("one choice per set", model.usePose())
        model.select("pose-1")
        assertEquals("pose-2", state.selectedPoseId)
        share.pick(SubjectPick(share.opened.single(), "pose-1"))
        runCurrent()
        assertEquals(Role.PHOTOGRAPHER, state.chosenBy)
        assertEquals(1, share.calls.count { it.startsWith("chosen") })
    }

    @Test
    fun theSubjectsPickThatComesFirstIsTheChoice() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        run.plan()
        run.send(ready("pose-1"), ready("pose-2"))
        run.finish()
        runCurrent()
        model.select("pose-1")

        share.pick(SubjectPick(share.opened.single(), "pose-2"))
        runCurrent()

        assertEquals("pose-2", state.selectedPoseId)
        assertEquals(Role.SUBJECT, state.chosenBy)
        assertEquals("chosen pose-2 by SUBJECT", share.calls.last())
        assertNull("this phone's tap comes second", model.usePose())
    }

    @Test
    fun aPickForAnotherSetWhileGeneratingOrOfAFailedCandidateIsIgnored() = runTest {
        generateFromAPhoto()
        val run = generator.runs.single()
        val set = share.opened.single()
        run.plan()
        run.send(ready("pose-1"), failed("pose-2"))
        runCurrent()
        share.pick(SubjectPick(set, "pose-1"))
        runCurrent()
        run.finish()
        runCurrent()

        share.pick(SubjectPick("another set", "pose-1"))
        share.pick(SubjectPick(set, "pose-2"))
        runCurrent()

        assertNull(state.chosenBy)
        assertTrue(share.calls.none { it.startsWith("chosen") })
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

    @Test
    fun consentGivenWhileTheStoredAnswerLoadsIsKept() = runTest {
        consent.hold = CompletableDeferred()
        val slow = GenerationViewModel(generator, camera, consent, LIMIT_MS)
        store.put("slow", slow)
        runCurrent()

        slow.onConsentGiven()
        runCurrent()
        // The answer that was read before the user agreed arrives only now.
        consent.hold?.complete(Unit)
        runCurrent()

        assertFalse(slow.needsConsent)
        assertTrue(consent.given)
    }

    // ---- Generate poses on both phones (FR-6.15, Design 2.6.5) ---------------------------------------------------
    // Partitions: the description shared from its opening to its end (a set started, Cancel), opened again with its text
    // and whether it was rejected; this phone's field holding it while focused; the subject's edit taken while this
    // phone's field is free and dropped while it holds it or for another description; this phone's typing and start
    // refused while the subject's field holds it; the subject's Generate 4 poses with its text and Skip, dropped while
    // this phone's field holds it; a dropped link freeing the subject's field.

    @Test
    fun generatePoses_isSharedFromItsOpeningToTheSetItStarts() = runTest {
        describeAPhoto()
        assertTrue(model.onDescriptionFocus(true))
        model.onDescriptionChanged("wave")
        model.onDescriptionFocus(false)
        model.generate("wave")
        runCurrent()

        assertEquals(
            listOf("open ''", "update '' PHOTOGRAPHER", "update 'wave' PHOTOGRAPHER", "update 'wave' null", "started"),
            descriptions.calls,
        )
        assertEquals(1, descriptions.ids.toSet().size)
    }

    @Test
    fun aRejectedDescription_opensAgainWithItsText_andCancelClosesIt() = runTest {
        describeAPhoto()
        model.onDescriptionChanged("피 흘리는 모습")
        model.generate("피 흘리는 모습")
        runCurrent()
        val run = generator.runs.single()
        run.plan(List(4) { "피 흘리는 모습" })
        run.send(*(1..4).map { failed("pose-$it", GenerationError.REJECTED) }.toTypedArray())
        run.finish()
        runCurrent()

        model.describeAgain()
        model.cancel()

        assertEquals("open '피 흘리는 모습' rejected", descriptions.calls[descriptions.calls.size - 2])
        assertEquals("closed", descriptions.calls.last())
        assertEquals(2, descriptions.ids.toSet().size)
    }

    @Test
    fun theSubjectsField_holdsTheDescriptionOnlyWhileThisPhonesDoesNot() = runTest {
        describeAPhoto()
        val id = descriptions.ids.single()
        model.onDescriptionFocus(true)

        descriptions.request(DescriptionRequest.Edit(id, holding = true, text = "jump"))
        runCurrent()
        assertEquals(Role.PHOTOGRAPHER, state.descriptionWriter)
        assertEquals("", state.description)

        model.onDescriptionFocus(false)
        descriptions.request(DescriptionRequest.Edit(id, holding = true, text = "jump"))
        descriptions.request(DescriptionRequest.Edit("another", holding = true, text = "other"))
        runCurrent()
        assertEquals(Role.SUBJECT, state.descriptionWriter)
        assertEquals("jump", state.description)

        // This phone's field and buttons wait meanwhile.
        assertFalse(model.onDescriptionFocus(true))
        model.onDescriptionChanged("mine")
        model.generate("mine")
        model.suggestPoses()
        runCurrent()
        assertEquals(Role.SUBJECT, state.descriptionWriter)
        assertEquals("jump", state.description)
        assertTrue(generator.runs.isEmpty())

        descriptions.request(DescriptionRequest.Edit(id, holding = false, text = "jump high"))
        runCurrent()
        assertNull(state.descriptionWriter)
        assertEquals("jump high", state.description)
        assertEquals("update 'jump high' null", descriptions.calls.last())
    }

    @Test
    fun theSubjectsGenerate_startsTheSetWithItsText_andItsSkipAsksForSuggestions() = runTest {
        describeAPhoto()
        descriptions.request(DescriptionRequest.Edit(descriptions.ids.last(), holding = true, text = "jump"))
        descriptions.request(DescriptionRequest.Go(descriptions.ids.last(), "jump high", suggest = false))
        runCurrent()
        assertEquals(PoseRequest.Described("jump high"), generator.runs.single().request)
        assertEquals("jump high", state.description)
        assertEquals("started", descriptions.calls.last())

        model.cancel()
        describeAPhoto()
        model.onDescriptionChanged("lean")
        descriptions.request(DescriptionRequest.Go(descriptions.ids.last(), "stale", suggest = true))
        runCurrent()
        assertEquals(PoseRequest.Suggested, generator.runs.last().request)
        assertEquals(GenerationUiState.Phase.GENERATING, state.phase)
    }

    @Test
    fun theSubjectsGenerate_isDroppedWhileThisPhonesFieldHoldsTheDescription_orForAnotherOne() = runTest {
        describeAPhoto()
        model.onDescriptionFocus(true)

        descriptions.request(DescriptionRequest.Go(descriptions.ids.single(), "jump", suggest = false))
        descriptions.request(DescriptionRequest.Go("another", "jump", suggest = true))
        runCurrent()

        assertTrue(generator.runs.isEmpty())
        assertEquals(GenerationUiState.Phase.DESCRIBING, state.phase)
    }

    @Test
    fun aDroppedLink_freesTheSubjectsField() = runTest {
        describeAPhoto()
        descriptions.request(DescriptionRequest.Edit(descriptions.ids.single(), holding = true, text = "jump"))
        runCurrent()

        descriptions.request(DescriptionRequest.Gone)
        runCurrent()

        assertNull(state.descriptionWriter)
        assertEquals("jump", state.description)
        assertTrue(model.onDescriptionFocus(true))
    }

    /** *Generate poses here*, the shutter, and *Use this photo*: Generate poses is shown. */
    private fun TestScope.describeAPhoto() {
        model.beginScene()
        model.takeScene()
        runCurrent()
        model.useScene()
        runCurrent()
    }

    /** The same, then *Skip, suggest poses for me*. */
    private fun TestScope.generateFromAPhoto() {
        describeAPhoto()
        model.suggestPoses()
        runCurrent()
    }

    private fun ready(poseId: String) = CandidateEvent.Ready(poseId, testBitmap())

    private fun failed(poseId: String, error: GenerationError = GenerationError.SERVER) =
        CandidateEvent.Failed(poseId, error)

    /** Records what the ViewModel tells the subject's Generate poses, and passes on the requests a test sends. */
    private class RecordingDescriptions : DescriptionShare {
        val calls = mutableListOf<String>()
        val ids = mutableListOf<String>()
        private val sent = MutableSharedFlow<DescriptionRequest>(extraBufferCapacity = 8)
        override val requests: Flow<DescriptionRequest> = sent

        fun request(request: DescriptionRequest) = check(sent.tryEmit(request))

        override fun open(descriptionId: String, scene: Bitmap, text: String, rejected: Boolean) {
            ids += descriptionId
            calls += "open '$text'" + if (rejected) " rejected" else ""
        }

        override fun update(descriptionId: String, text: String, writer: Role?) {
            check(descriptionId == ids.last())
            calls += "update '$text' $writer"
        }

        override fun end(descriptionId: String, started: Boolean) {
            check(descriptionId == ids.last())
            calls += if (started) "started" else "closed"
        }
    }

    /** Records what the ViewModel tells the subject's phone, and passes on the picks a test sends. */
    private class RecordingShare : PoseShare {
        val calls = mutableListOf<String>()
        val opened = mutableListOf<String>()
        private val sentPicks = MutableSharedFlow<SubjectPick>(extraBufferCapacity = 8)
        override val picks: Flow<SubjectPick> = sentPicks

        fun pick(pick: SubjectPick) = check(sentPicks.tryEmit(pick))

        override fun open(setId: String) {
            opened += setId
            calls += "open $setId"
        }

        override fun candidate(setId: String, poseId: String, image: Bitmap) {
            check(setId == opened.last())
            calls += "candidate $poseId"
        }

        override fun picking(setId: String, poseIds: List<String>) {
            check(setId == opened.last())
            calls += "picking ${poseIds.joinToString(",")}"
        }

        override fun chosen(setId: String, poseId: String, by: Role) {
            calls += "chosen $poseId by $by"
        }

        override fun close(setId: String) {
            calls += "close $setId"
        }
    }

    /** Each call to generate is a [Run] the test feeds with events. */
    private class FakeGenerator : PoseGenerator {
        val runs = mutableListOf<Run>()

        override fun generate(
            scene: Bitmap,
            request: PoseRequest,
            seed: Long,
            selfie: Boolean,
        ): Flow<GenerationEvent> {
            val run = Run(scene, request, seed, selfie).also(runs::add)
            return flow {
                try {
                    for (event in run.events) emit(event)
                } catch (error: CancellationException) {
                    run.cancelled = true
                    throw error
                }
            }
        }

        class Run(val scene: Bitmap, val request: PoseRequest, val seed: Long, val selfie: Boolean) {
            val events = Channel<GenerationEvent>(Channel.UNLIMITED)
            var cancelled = false

            /** The poses of the set are known: four suggestions unless [texts] says otherwise. */
            fun plan(texts: List<String> = SUGGESTED) = send(GenerationEvent.Planned(planned(texts)))

            fun send(vararg sent: GenerationEvent) {
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

        /** Whether the light is on, as the controller keeps it. */
        var torchOn = false

        /** When set, the frame is given only after this completes. */
        var hold: CompletableDeferred<Unit>? = null

        override val status = MutableStateFlow(CameraStatus.READY)
        override val capabilities = MutableStateFlow<CameraCapabilities?>(null)
        override val zoom = MutableStateFlow(1f)
        override val state = MutableStateFlow(CameraState(zoom = 1f))

        override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit

        override fun setZoom(ratio: Float, final: Boolean) = Unit

        override fun setFocusPoint(x: Float, y: Float) = Unit

        override fun setExposure(index: Int) = Unit

        override fun setFlash(mode: FlashMode) = Unit

        override fun setTorch(on: Boolean) {
            torchOn = on
        }

        override fun setAspectRatio(ratio: AspectRatio) = Unit

        override suspend fun takePhoto(): Result<Uri> = Result.failure(UnsupportedOperationException())

        override suspend fun grabFrame(): Result<Bitmap> {
            grabs++
            hold?.await()
            return frame?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("No live camera frame is available"))
        }

        override fun setFrameSink(sink: FrameSink?) = Unit
        override val facing = MutableStateFlow(CameraFacing.BACK)
        override val hasFrontCamera: StateFlow<Boolean> = MutableStateFlow(true)
        override fun setFacing(facing: CameraFacing) = Unit
    }

    private companion object {
        const val LIMIT_MS = 30_000L
    }

    private class FakeConsent : GenerationConsent {
        var given = false

        /** When set, the stored answer is read at once but handed over only after this completes. */
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun isGiven(): Boolean {
            val stored = given
            hold?.await()
            return stored
        }

        override suspend fun give() {
            given = true
        }
    }
}
