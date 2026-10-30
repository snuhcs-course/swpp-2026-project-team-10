// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
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
import org.robolectric.annotation.Config
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
 * camera. The scene photo's shape (FR-4.3), on the Galaxy S22's 360 × 705 dp: in each ratio (3:4, 9:16, 1:1), the
 * thumbnails, the slots, and the pages in that shape; the four slots of a 9:16 scene whole on screen, centered, and
 * those of 3:4 and 1:1 as wide as half the screen as before; a candidate whose ratio the model rounded shown whole.
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

    /** The scene photo in each of the app's ratios: 3:4, 9:16, 1:1. */
    private val scenes = listOf(bitmap(300, 400), bitmap(288, 512), bitmap(400, 400))

    private fun modules(scene: Bitmap = bitmap(300, 400)): TestAppContainer =
        TestAppContainer(camera = FakeCameraController().apply { frame = scene }).install()

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
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun describe_thumbnailHasTheScenesShape() {
        for (scene in scenes) {
            modules(scene)
            val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
            s.onFragment { fragment ->
                val thumbnail = FragmentDescribePoseBinding.bind(fragment.requireView()).sceneImage
                assertEquals(scene.aspect(), thumbnail.aspect(), 0.01f)
                assertEquals(fragment.resources.displayMetrics.density * 72, thumbnail.height.toFloat(), 1f)
            }
            s.close()
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
        idle()
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

    /** Generating poses for [scene] with its four slots planned and on screen, the first ones filled with [ready]. */
    private fun generatingSlots(scene: Bitmap, vararg ready: Bitmap): FragmentScenario<GeneratingFragment> {
        val modules = modules(scene)
        val (s, _) = launchPrepared<GeneratingFragment>(R.id.generatingFragment) { generating(it) }
        val run = modules.generator.runs.single()
        run.plan(poses)
        run.send(*ready.mapIndexed { i, image -> CandidateEvent.Ready("pose-${i + 1}", image) }.toTypedArray())
        // The grid's adapter compares each new list off the main thread.
        idleUntil {
            var shown = false
            s.onFragment { fragment ->
                val slots = FragmentGeneratingBinding.bind(fragment.requireView()).candidateGrid.slots()
                shown = slots.size == poses.size && slots.count { it.hasCandidate() } == ready.size
            }
            shown
        }
        return s
    }

    private fun RecyclerView.slots(): List<View> = (0 until childCount).map { getChildAt(it) }

    /** Whether a slot shows its candidate; an empty one holds a drawable without a bitmap. */
    private fun View.hasCandidate(): Boolean =
        (findViewById<ImageView>(R.id.poseImage).drawable as? BitmapDrawable)?.bitmap != null

    @Test
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun generating_thumbnailAndSlotsHaveTheScenesShape_inEachRatio() {
        for (scene in scenes) {
            val s = generatingSlots(scene)
            s.onFragment { fragment ->
                val binding = FragmentGeneratingBinding.bind(fragment.requireView())
                assertEquals(scene.aspect(), binding.sceneImage.aspect(), 0.01f)
                val slots = binding.candidateGrid.slots()
                assertEquals(4, slots.size)
                for (slot in slots) {
                    assertEquals(
                        scene.aspect(),
                        slot.findViewById<View>(R.id.poseImage).aspect(),
                        0.01f,
                    )
                }
            }
            s.close()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun generating_nineBySixteenScene_keepsAllFourSlotsOnScreen_centered() {
        scenario = generatingSlots(bitmap(288, 512))

        scenario!!.onFragment { fragment ->
            val grid = FragmentGeneratingBinding.bind(fragment.requireView()).candidateGrid
            val slots = grid.slots()
            assertEquals(4, slots.size)
            for (slot in slots) {
                assertTrue(slot.left >= 0 && slot.top >= 0 && slot.right <= grid.width && slot.bottom <= grid.height)
            }
            // Narrower than half the screen, with as much room on the left as on the right.
            val left = slots.minOf { it.left }
            val right = grid.width - slots.maxOf { it.right }
            assertTrue(left > fragment.resources.getDimensionPixelSize(R.dimen.pose_card_margin))
            assertEquals(left.toFloat(), right.toFloat(), 1f)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun generating_threeByFourAndSquareScenes_fillTheWidthAsBefore() {
        for (scene in listOf(bitmap(300, 400), bitmap(400, 400))) {
            val s = generatingSlots(scene)
            s.onFragment { fragment ->
                val grid = FragmentGeneratingBinding.bind(fragment.requireView()).candidateGrid
                val margin = fragment.resources.getDimensionPixelSize(R.dimen.pose_card_margin)
                val slots = grid.slots()
                assertEquals(4, slots.size)
                assertEquals(margin.toFloat(), slots.minOf { it.left }.toFloat(), 1f)
                assertEquals((grid.width - margin).toFloat(), slots.maxOf { it.right }.toFloat(), 1f)
                for (slot in slots) assertTrue(slot.bottom <= grid.height)
            }
            s.close()
        }
    }

    @Test
    fun generating_candidateWhoseRatioTheModelRounded_isShownWhole() {
        // A model that has no 3:4 answers with its nearest size, here 2:3; the slot keeps the scene's shape.
        scenario = generatingSlots(bitmap(300, 400), bitmap(200, 300))

        scenario!!.onFragment { fragment ->
            val image = FragmentGeneratingBinding.bind(fragment.requireView()).candidateGrid.slots().first()
                .findViewById<ImageView>(R.id.poseImage)
            assertEquals(0.75f, image.aspect(), 0.01f)
            assertTrue(image.showsWholePicture())
        }
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
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun pick_showsEachCandidateWholeInItsOwnShape() {
        val images = listOf(bitmap(300, 400), bitmap(288, 512), bitmap(400, 400))
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) {
            generating(it)
            val run = modules.generator.runs.single()
            run.plan(poses)
            run.send(*images.mapIndexed { i, image -> CandidateEvent.Ready("pose-${i + 1}", image) }.toTypedArray())
            run.send(CandidateEvent.Failed("pose-4", GenerationError.TIMEOUT))
            run.finish()
        }
        scenario = s

        for ((position, candidate) in images.withIndex()) {
            s.onFragment {
                FragmentPickPoseBinding.bind(it.requireView()).candidatePager.setCurrentItem(position, false)
            }
            idle()
            s.onFragment { fragment ->
                val pages = FragmentPickPoseBinding.bind(fragment.requireView()).candidatePager.getChildAt(0)
                    as RecyclerView
                val page = checkNotNull(pages.findViewHolderForAdapterPosition(position)).itemView
                val image = page.findViewById<ImageView>(R.id.poseImage)
                assertEquals(candidate.aspect(), image.aspect(), 0.01f)
                // As large as the page allows: as wide or as tall as the page.
                assertTrue(image.width <= page.width && image.height <= page.height)
                assertTrue(image.width >= page.width - 1 || image.height >= page.height - 1)
                assertTrue(image.showsWholePicture())
            }
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

    private fun Bitmap.aspect(): Float = width.toFloat() / height

    private fun View.aspect(): Float = width.toFloat() / height

    /** Whether the whole picture is inside the image's bounds, so none of it is cut off. */
    private fun ImageView.showsWholePicture(): Boolean {
        val picture = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        imageMatrix.mapRect(picture)
        picture.offset(paddingLeft.toFloat(), paddingTop.toFloat())
        return picture.left >= -0.5f && picture.top >= -0.5f &&
            picture.right <= width + 0.5f && picture.bottom <= height + 0.5f
    }
}
