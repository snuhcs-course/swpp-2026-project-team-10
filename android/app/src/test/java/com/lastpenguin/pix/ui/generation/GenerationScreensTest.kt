// AI-generated with Claude Code, 2026-10-30, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.databinding.FragmentGeneratingBinding
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The four generation screens (R&S 6.3, FR-4.4–4.8, US-5; Testing Documentation 2.1.6) on the real Fragments,
 * with the shared GenerationViewModel put in the right phase before each screen's view is created. Generate poses: opens with the scene and the
 * idea chips, Generate 4 poses and Skip start a run and open Generating poses, Cancel; in a session, the hint, this
 * phone's typing holding the field, the subject's typing shown and holding it, and the subject's Generate 4 poses that
 * comes first, which opens Generating poses (Design 2.6.5). Generating poses: the set
 * planned, candidates arriving with the count, all ready (to Pick a pose), none ready (to Couldn't create poses),
 * Cancel. Pick a pose: only the ready candidates, one per page with a counter; the page on screen is the selected
 * pose, so Use this pose works at once and takes the pose swiped to (to Reference confirm); a pose selected before
 * the screen opens is the one shown; Try other poses (back to Generate poses), system back; in a session, the hint
 * that the subject sees the poses too, and the subject's pick that comes first, which opens Reference confirm with its
 * candidate (Design 2.6.5). Couldn't create poses: Try again, a rejected description with Edit description, Back to
 * camera.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerationScreensTest {
    private var scenario: FragmentScenario<*>? = null
    private val poses = listOf("Lean on the wall", "Walk toward the camera", "Sit on the step", "Wave")

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun modules(): TestAppContainer =
        TestAppContainer(camera = FakeCameraController().apply { frame = bitmap(300, 400) }).install()

    /** The scene photo taken and confirmed, so Generate poses has something to describe (DESCRIBING). */
    private fun describing(activity: FragmentActivity): GenerationViewModel =
        activity.viewModel<GenerationViewModel>().apply {
            beginScene()
            takeScene()
            useScene()
        }

    private fun generating(activity: FragmentActivity, description: String = "hands on hips") =
        describing(activity).apply { generate(description) }

    // ---- Generate poses ------------------------------------------------------------

    @Test
    fun describe_opensWithTheSceneAndTheIdeas() {
        modules()
        val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertNotNull(binding.sceneImage.drawable)
            assertEquals(
                fragment.resources.getStringArray(R.array.pose_ideas).size,
                binding.ideaChips.childCount,
            )
            assertEquals(string(R.string.description_counter, 0, 100), binding.descriptionCounter.text.toString())
            assertEquals(View.GONE, binding.descriptionError.visibility)
        }
    }

    @Test
    fun describe_generateStartsARunOfTheText_andOpensGeneratingPoses() {
        val modules = modules()
        val (s, nav) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            binding.descriptionInput.setText("벽에 기대서 웃기")
            binding.generateButton.performClick()
        }
        idle()

        assertEquals(PoseRequest.Described("벽에 기대서 웃기"), modules.generator.runs.single().request)
        assertEquals(R.id.generatingFragment, nav.currentDestination?.id)
    }

    @Test
    fun describe_skipAsksForSuggestions_andCancelReturnsToTheCamera() {
        val modules = modules()
        val (s, nav) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s

        s.onFragment { FragmentDescribePoseBinding.bind(it.requireView()).suggestButton.performClick() }
        idle()
        assertEquals(PoseRequest.Suggested, modules.generator.runs.single().request)
        assertEquals(R.id.generatingFragment, nav.currentDestination?.id)

        nav.setCurrentDestination(R.id.describePoseFragment)
        s.onFragment { FragmentDescribePoseBinding.bind(it.requireView()).cancelButton.performClick() }
        idle()
        assertTrue(modules.generator.runs.single().cancelled)
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    /** Generate poses in a session with Junhyeong; the screen's SessionViewModel sends it to the subject. */
    private fun describingInASession(
        modules: TestAppContainer,
    ): Pair<FragmentScenario<DescribePoseFragment>, TestNavHostController> {
        modules.session.setState(SessionState.Connected("s_1", PeerInfo("Junhyeong", null), Role.PHOTOGRAPHER))
        val (s, nav) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s
        idle()
        return s to nav
    }

    private fun descriptionId(modules: TestAppContainer): String =
        modules.session.sent.filterIsInstance<SessionMessage.PoseDescription>().last().descriptionId

    @Test
    fun describe_inASession_saysSo_andTypingHereHoldsTheField() {
        val modules = modules()
        val (s, _) = describingInASession(modules)

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertTrue(binding.sharedHint.isShown)
            assertEquals(string(R.string.describe_shared_hint, "Junhyeong"), binding.sharedHint.text.toString())
            binding.descriptionInput.requestFocus()
            binding.descriptionInput.setText("wave")
        }
        idle()

        val last = modules.session.sent.filterIsInstance<SessionMessage.PoseDescription>().last()
        assertEquals("wave", last.text)
        assertEquals(Role.PHOTOGRAPHER, last.writer)
        assertNotNull(modules.session.sent.filterIsInstance<SessionMessage.PoseSceneBegin>().singleOrNull())
    }

    @Test
    fun describe_theSubjectsTyping_isShownAndHoldsTheField_untilItEnds() {
        val modules = modules()
        val (s, _) = describingInASession(modules)
        val id = descriptionId(modules)

        modules.session.deliver(SessionMessage.PoseDescriptionEdit(id, holding = true, text = "jump"))
        idle()
        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertEquals("jump", binding.descriptionInput.text.toString())
            assertFalse(binding.descriptionInput.isEnabled)
            assertFalse(binding.generateButton.isEnabled)
            assertTrue(binding.descriptionTyping.isShown)
            assertEquals(string(R.string.peer_typing, "Junhyeong"), binding.descriptionTyping.text.toString())
        }

        modules.session.deliver(SessionMessage.PoseDescriptionEdit(id, holding = false, text = "jump high"))
        idle()
        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertEquals("jump high", binding.descriptionInput.text.toString())
            assertTrue(binding.descriptionInput.isEnabled)
            assertFalse(binding.descriptionTyping.isShown)
        }
    }

    @Test
    fun describe_theSubjectsGenerateComingFirst_opensGeneratingPoses() {
        val modules = modules()
        val (_, nav) = describingInASession(modules)

        val id = descriptionId(modules)
        modules.session.deliver(SessionMessage.PoseDescriptionEdit(id, holding = true, text = "jump"))
        modules.session.deliver(SessionMessage.PoseDescriptionGo(id, "jump", suggest = false))
        idle()

        assertEquals(PoseRequest.Described("jump"), modules.generator.runs.single().request)
        assertEquals(R.id.generatingFragment, nav.currentDestination?.id)
        val end = modules.session.sent.filterIsInstance<SessionMessage.PoseDescription>().last()
        assertEquals(DescriptionStep.STARTED, end.step)
    }

    // ---- Generating poses ------------------------------------------------------------

    @Test
    fun generating_countsTheCandidatesAsTheyArrive_andOpensPickAPoseWhenDone() {
        val modules = modules()
        val (s, nav) = launchPrepared<GeneratingFragment>(R.id.generatingFragment) { generating(it) }
        scenario = s
        val run = modules.generator.runs.single()

        s.onFragment { fragment ->
            val binding = FragmentGeneratingBinding.bind(fragment.requireView())
            assertNotNull(binding.sceneImage.drawable)
            assertEquals(string(R.string.creating_pose), binding.readyCount.text.toString())
        }

        run.plan(poses)
        // The grid's ListAdapter diffs this second list on a background thread: wait for its result, not one loop.
        idleUntil {
            var count = 0
            s.onFragment {
                count =
                    FragmentGeneratingBinding.bind(it.requireView()).candidateGrid.adapter?.itemCount ?: 0
            }
            count == 4
        }
        s.onFragment { fragment ->
            val binding = FragmentGeneratingBinding.bind(fragment.requireView())
            assertEquals(4, binding.candidateGrid.adapter?.itemCount)
            assertEquals(string(R.string.ready_count, 0, 4), binding.readyCount.text.toString())
        }

        run.send(CandidateEvent.Ready("pose-1", bitmap()))
        idle()
        s.onFragment { fragment ->
            assertEquals(
                string(R.string.ready_count, 1, 4),
                FragmentGeneratingBinding.bind(fragment.requireView()).readyCount.text.toString(),
            )
        }

        run.send(
            CandidateEvent.Ready("pose-2", bitmap()),
            CandidateEvent.Ready("pose-3", bitmap()),
            CandidateEvent.Failed("pose-4", GenerationError.SERVER),
        )
        run.finish()
        idle()

        assertEquals(R.id.pickPoseFragment, nav.currentDestination?.id)
    }

    @Test
    fun generating_withSuggestedPoses_saysChoosing_andNothingReadyOpensCouldntCreate() {
        val modules = modules()
        val (s, nav) = launchPrepared<GeneratingFragment>(R.id.generatingFragment) {
            describing(it).suggestPoses()
        }
        scenario = s
        val run = modules.generator.runs.single()

        s.onFragment { fragment ->
            assertEquals(
                string(R.string.choosing_poses),
                FragmentGeneratingBinding.bind(fragment.requireView()).readyCount.text.toString(),
            )
        }

        run.plan(poses)
        run.send(*poses.indices.map { CandidateEvent.Failed("pose-${it + 1}", GenerationError.SERVER) }.toTypedArray())
        run.finish()
        idle()

        assertEquals(R.id.generationFailedFragment, nav.currentDestination?.id)
    }

    @Test
    fun generating_cancelStopsTheRunAndReturnsToTheCamera() {
        val modules = modules()
        val (s, nav) = launchPrepared<GeneratingFragment>(R.id.generatingFragment) { generating(it) }
        scenario = s

        s.onFragment { FragmentGeneratingBinding.bind(it.requireView()).cancelButton.performClick() }
        idle()

        assertTrue(modules.generator.runs.single().cancelled)
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    // ---- Pick a pose ------------------------------------------------------------

    /** A finished set: three ready, one failed (PICKING). */
    private fun picking(modules: TestAppContainer, activity: FragmentActivity) {
        generating(activity)
        val run = modules.generator.runs.single()
        run.plan(poses)
        run.send(
            CandidateEvent.Ready("pose-1", bitmap()),
            CandidateEvent.Ready("pose-2", bitmap()),
            CandidateEvent.Failed("pose-3", GenerationError.TIMEOUT),
            CandidateEvent.Ready("pose-4", bitmap()),
        )
        run.finish()
    }

    @Test
    fun pick_showsTheReadyCandidatesOneAtATime_andUseThisPoseTakesThePoseOnScreen() {
        val modules = modules()
        val (s, nav) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentPickPoseBinding.bind(fragment.requireView())
            assertEquals(3, binding.candidatePager.adapter?.itemCount)
            assertEquals("1 / 3", binding.pageCounter.text.toString())
            // The pose on screen is selected, so it can be used without a tap.
            assertEquals(
                "pose-1",
                fragment.requireActivity().viewModel<GenerationViewModel>().uiState.value.selectedPoseId,
            )
            assertTrue(binding.usePoseButton.isEnabled)
            binding.candidatePager.setCurrentItem(1, false)
        }
        idle()

        s.onFragment { fragment ->
            val binding = FragmentPickPoseBinding.bind(fragment.requireView())
            assertEquals(
                "pose-2",
                fragment.requireActivity().viewModel<GenerationViewModel>().uiState.value.selectedPoseId,
            )
            assertEquals("2 / 3", binding.pageCounter.text.toString())
            binding.usePoseButton.performClick()
        }
        idle()

        // The chosen image goes through the same step as an uploaded photo (FR-4.6), and the set is let go.
        assertEquals(listOf(GuideSource.GENERATED), modules.maker.sources)
        assertEquals(R.id.referenceConfirmFragment, nav.currentDestination?.id)
        s.onFragment {
            assertEquals(
                GenerationUiState.Phase.IDLE,
                it.requireActivity().viewModel<GenerationViewModel>().uiState.value.phase,
            )
        }
    }

    @Test
    fun pick_saysTheSubjectSeesThePosesToo_onlyInASession() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        s.onFragment { assertFalse(FragmentPickPoseBinding.bind(it.requireView()).sharedHint.isShown) }

        modules.session.setState(SessionState.Connected("s_1", PeerInfo("Junhyeong", null), Role.PHOTOGRAPHER))
        idle()

        s.onFragment {
            val hint = FragmentPickPoseBinding.bind(it.requireView()).sharedHint
            assertTrue(hint.isShown)
            assertEquals(string(R.string.pose_shared_hint, "Junhyeong"), hint.text.toString())
        }
    }

    @Test
    fun pick_theSubjectsPickThatComesFirst_opensReferenceConfirmWithItsCandidate() {
        val modules = modules()
        modules.session.setState(SessionState.Connected("s_1", PeerInfo("Junhyeong", null), Role.PHOTOGRAPHER))
        val (s, nav) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        val set = modules.session.sent.filterIsInstance<SessionMessage.PoseStep>().first().setId

        modules.session.deliver(SessionMessage.PosePick(set, "pose-4"))
        idle()

        assertEquals(R.id.referenceConfirmFragment, nav.currentDestination?.id)
        assertEquals(listOf(GuideSource.GENERATED), modules.maker.sources)
        val end = modules.session.sent.filterIsInstance<SessionMessage.PoseStep>().last()
        assertEquals(PoseSetStep.CHOSEN, end.step)
        assertEquals("pose-4" to Role.SUBJECT, end.poseId to end.by)
        s.onFragment {
            assertEquals("Junhyeong", it.requireActivity().viewModel<ReferenceViewModel>().chosenBy.value)
        }
    }

    @Test
    fun pick_poseSelectedBeforeTheScreenOpens_isTheOneShown() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) {
            picking(modules, it)
            it.viewModel<GenerationViewModel>().select("pose-4")
        }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentPickPoseBinding.bind(fragment.requireView())
            assertEquals(2, binding.candidatePager.currentItem)
            assertEquals("3 / 3", binding.pageCounter.text.toString())
            assertEquals(
                "pose-4",
                fragment.requireActivity().viewModel<GenerationViewModel>().uiState.value.selectedPoseId,
            )
        }
    }

    @Test
    fun pick_tryOtherPosesReturnsToGeneratePoses_andBackDiscardsTheSet() {
        val modules = modules()
        val (s, nav) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s

        s.onFragment { FragmentPickPoseBinding.bind(it.requireView()).tryOtherButton.performClick() }
        idle()
        s.onFragment {
            val state = it.requireActivity().viewModel<GenerationViewModel>().uiState.value
            assertEquals(GenerationUiState.Phase.DESCRIBING, state.phase)
            assertEquals("hands on hips", state.description)
        }
        assertEquals(R.id.describePoseFragment, nav.currentDestination?.id)

        nav.setCurrentDestination(R.id.pickPoseFragment)
        s.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    // ---- Couldn't create poses ------------------------------------------------------------

    private fun failed(modules: TestAppContainer, activity: FragmentActivity, error: GenerationError) {
        generating(activity)
        val run = modules.generator.runs.last()
        run.plan(poses)
        run.send(*poses.indices.map { CandidateEvent.Failed("pose-${it + 1}", error) }.toTypedArray())
        run.finish()
    }

    @Test
    fun failed_keepsThePhoto_andTryAgainStartsANewRun() {
        val modules = modules()
        val (s, nav) = launchPrepared<GenerationFailedFragment>(R.id.generationFailedFragment) {
            failed(modules, it, GenerationError.SERVER)
        }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentFailureBinding.bind(fragment.requireView())
            assertEquals(string(R.string.generation_failed_title), binding.failureTitle.text.toString())
            assertEquals(View.VISIBLE, binding.failurePhoto.visibility)
            assertNotNull(binding.failurePhotoImage.drawable)
            assertEquals(string(R.string.try_again), binding.primaryButton.text.toString())
            assertEquals(string(R.string.back_to_camera), binding.secondaryButton.text.toString())
            binding.primaryButton.performClick()
        }
        idle()

        assertEquals(2, modules.generator.runs.size)
        assertEquals(PoseRequest.Described("hands on hips"), modules.generator.runs.last().request)
        assertEquals(R.id.generatingFragment, nav.currentDestination?.id)
    }

    @Test
    fun rejectedDescription_offersToEditIt() {
        val modules = modules()
        val (s, nav) = launchPrepared<GenerationFailedFragment>(R.id.generationFailedFragment) {
            failed(modules, it, GenerationError.REJECTED)
        }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentFailureBinding.bind(fragment.requireView())
            assertEquals(
                string(R.string.description_rejected_message, "hands on hips"),
                binding.failureMessage.text.toString(),
            )
            assertEquals(string(R.string.edit_description), binding.primaryButton.text.toString())
            binding.primaryButton.performClick()
        }
        idle()

        s.onFragment {
            val state = it.requireActivity().viewModel<GenerationViewModel>().uiState.value
            assertEquals(GenerationUiState.Phase.DESCRIBING, state.phase)
            assertTrue(state.descriptionRejected)
        }
        assertEquals(R.id.describePoseFragment, nav.currentDestination?.id)
    }

    @Test
    fun failed_backToCameraDiscardsTheSet() {
        val modules = modules()
        val (s, nav) = launchPrepared<GenerationFailedFragment>(R.id.generationFailedFragment) {
            failed(modules, it, GenerationError.SERVER)
        }
        scenario = s

        s.onFragment { FragmentFailureBinding.bind(it.requireView()).secondaryButton.performClick() }
        idle()

        s.onFragment {
            assertEquals(
                GenerationUiState.Phase.IDLE,
                it.requireActivity().viewModel<GenerationViewModel>().uiState.value.phase,
            )
        }
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
