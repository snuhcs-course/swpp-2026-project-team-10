// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.generation

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.databinding.FragmentGeneratingBinding
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.guide.ReferenceUiState
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowToast

/*
 * The four generation screens (R&S 6.3, FR-4.4–4.8, US-5; Testing Documentation 2.1.6) on the real Fragments,
 * with the shared GenerationViewModel put in the right phase before each screen's view is created. Generate poses: opens with the scene and the
 * descriptions used before, newest first (FR-4.11; none hides them; a chip fills the field, and its ✕ drops it; in an
 * invitation's session, the switch to the ones used with the friend, kept while the link reconnects, for a set started
 * meanwhile), the one button starts a run, of the text or of suggested poses for an empty field (Iter3-A13), and opens
 * Generating poses, Cancel; in a session, the hint, this
 * phone's typing holding the field, the subject's typing shown and holding it, and the subject's Generate 4 poses that
 * comes first, which opens Generating poses (Design 2.6.5). Generating poses: the set
 * planned, candidates arriving with the count, all ready (to Pick a pose), none ready (to Couldn't create poses),
 * Cancel. Pick a pose: only the ready candidates, one per page with a counter; the page on screen is the selected
 * pose, so Use this pose works at once and takes the pose swiped to (to Reference confirm); a pose selected before
 * the screen opens is the one shown; Try other poses (back to Generate poses), system back; in a session, the hint
 * that the subject sees the poses too, and the subject's pick that comes first, which opens Reference confirm with its
 * candidate (Design 2.6.5). The ♥ on Pick a pose (FR-4.14): on the page on screen only, saving the pose as a generated
 * guide (disabled meanwhile, filled in red, "Pose saved"), a filled one removing it, a pose without a person (nothing saved),
 * Use this pose after it (the saved guide goes on, cut out once), and a ♥ still saving when the pose is used (saved
 * all the same). The ♥'s pop (Iter3-A13), with the display's frames paused: a ♥ that saves pops once, and one that
 * removes does not; with animations off, it only fills. A candidate's fade is in PoseCandidateAdapterTest. Couldn't create poses: Try again, a rejected description with Edit description, a scene in
 * which the server found no one with Shoot again (FR-4.13), Back to
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
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
        scenario?.close()
    }

    /**
     * Lays [list] out where it is, as its next frame would: a list binds a change in its layout. Letting a frame through
     * instead would play the fade it starts to its end at once (Testing 2.1.8).
     */
    private fun layOut(list: View) {
        // A list of a fixed size posts its changes to the next frame instead of asking for a layout.
        list.forceLayout()
        list.measure(
            View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(list.height, View.MeasureSpec.EXACTLY),
        )
        list.layout(list.left, list.top, list.right, list.bottom)
    }

    private fun pagesOf(s: FragmentScenario<*>): RecyclerView {
        var pages: RecyclerView? = null
        s.onFragment {
            pages =
                FragmentPickPoseBinding.bind(it.requireView()).candidatePager.getChildAt(0) as RecyclerView
        }
        return checkNotNull(pages)
    }

    /** Lets the display's frames go until a fade or a pop has ended. */
    private fun play() {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(MOTION_END_MS))
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
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

    /** Two descriptions used before: "Jump" with the friend u_2, then "Wave" alone, so "Wave" is the newer. */
    private fun usedBefore(modules: TestAppContainer) = runBlocking {
        modules.history.record("Jump", Role.PHOTOGRAPHER, "u_2")
        modules.history.record("Wave", Role.PHOTOGRAPHER, null)
    }

    private fun FragmentDescribePoseBinding.chipTexts(): List<String> =
        (0 until historyChips.childCount).map { (historyChips.getChildAt(it) as Chip).text.toString() }

    @Test
    fun describe_opensWithTheScene_andTheDescriptionsUsedBefore_newestFirst() {
        val modules = modules()
        usedBefore(modules)
        val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s
        idle()

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertNotNull(binding.sceneImage.drawable)
            assertEquals(View.VISIBLE, binding.historySection.visibility)
            assertEquals(View.GONE, binding.historyFilter.visibility)
            assertEquals(listOf("Wave", "Jump"), binding.chipTexts())
            assertEquals(string(R.string.description_counter, 0, 100), binding.descriptionCounter.text.toString())
            assertEquals(View.GONE, binding.descriptionError.visibility)
        }
    }

    @Test
    fun describe_withoutADescriptionUsedBefore_hasNoSuchSection() {
        modules()
        val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s
        idle()

        s.onFragment { fragment ->
            assertEquals(View.GONE, FragmentDescribePoseBinding.bind(fragment.requireView()).historySection.visibility)
        }
    }

    @Test
    fun describe_aChipFillsTheField_andItsCloseDropsItFromTheHistory() {
        val modules = modules()
        usedBefore(modules)
        val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s
        idle()

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            (binding.historyChips.getChildAt(1) as Chip).performClick()
            assertEquals("Jump", binding.descriptionInput.text.toString())
            (binding.historyChips.getChildAt(0) as Chip).performCloseIconClick()
        }
        idle()

        assertEquals(listOf("Jump"), modules.history.entries.value.map { it.text })
        s.onFragment { fragment ->
            assertEquals(listOf("Jump"), FragmentDescribePoseBinding.bind(fragment.requireView()).chipTexts())
        }
    }

    @Test
    fun describe_inAnInvitationsSession_switchesToTheDescriptionsUsedWithTheFriend() {
        val modules = modules()
        usedBefore(modules)
        val friend = SessionPartner("u_2", "Junhyeong")
        modules.session.setState(SessionState.Connected("s_1", PeerInfo("Junhyeong", null), Role.PHOTOGRAPHER, friend))
        val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s
        idle()

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertEquals(View.VISIBLE, binding.historyFilter.visibility)
            assertEquals(string(R.string.history_with, "Junhyeong"), binding.historyWithPartner.text.toString())
            assertTrue(binding.historyAll.isSelected)

            binding.historyWithPartner.performClick()

            assertTrue(binding.historyWithPartner.isSelected)
            assertEquals(listOf("Jump"), binding.chipTexts())
        }
    }

    @Test
    fun describe_aSetStartedWhileTheLinkReconnects_keepsTheFriend() {
        val modules = modules()
        usedBefore(modules)
        val friend = SessionPartner("u_2", "Junhyeong")
        modules.session.setState(SessionState.Reconnecting("s_1", 0L, friend))
        val (s, _) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s
        idle()

        s.onFragment { fragment ->
            val binding = FragmentDescribePoseBinding.bind(fragment.requireView())
            assertEquals(View.VISIBLE, binding.historyFilter.visibility)
            binding.descriptionInput.setText("Lean on the wall")
            binding.generateButton.performClick()
        }
        idle()

        assertEquals(
            listOf("Lean on the wall" to setOf("u_2"), "Wave" to emptySet(), "Jump" to setOf("u_2")),
            modules.history.entries.value.map { it.text to it.partners },
        )
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
    fun describe_theButtonWithAnEmptyField_asksForSuggestions_andCancelReturnsToTheCamera() {
        val modules = modules()
        val (s, nav) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { describing(it) }
        scenario = s

        s.onFragment {
            val button = FragmentDescribePoseBinding.bind(it.requireView()).generateButton
            assertEquals(it.getString(R.string.suggest_four_poses), button.text.toString())
            button.performClick()
        }
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
            assertEquals(string(R.string.shared_hint, "Junhyeong"), binding.sharedHint.text.toString())
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

    /** The ♥ of the page at [position] of Pick a pose. */
    private fun heartAt(s: FragmentScenario<*>, position: Int): MaterialButton {
        var heart: MaterialButton? = null
        s.onFragment { fragment ->
            val pages = FragmentPickPoseBinding.bind(
                fragment.requireView(),
            ).candidatePager.getChildAt(0) as RecyclerView
            heart =
                checkNotNull(pages.findViewHolderForAdapterPosition(position)).itemView.findViewById(R.id.heartButton)
        }
        return checkNotNull(heart)
    }

    private val generated = guide().copy(id = "saved-pose", source = GuideSource.GENERATED)

    private fun color(view: View, id: Int) = view.context.getColor(id)

    @Test
    fun pick_heart_savesThePoseOnScreen_toTheLibrary() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        assertEquals(string(R.string.save_pose), heartAt(s, 0).contentDescription)
        // Empty in the other icons' white.
        assertEquals(color(heartAt(s, 0), R.color.pix_text), heartAt(s, 0).iconTint.defaultColor)

        heartAt(s, 0).performClick()
        // A page's heart changes as the pages are compared again, off the main thread.
        idleUntil { !heartAt(s, 0).isEnabled }
        // The person is cut out as for Use this pose; meanwhile the heart takes no second tap.
        assertEquals(listOf(GuideSource.GENERATED), modules.maker.sources)
        assertFalse(heartAt(s, 0).isEnabled)

        modules.maker.finish(Result.success(generated))
        idleUntil { heartAt(s, 0).isEnabled }

        assertEquals(
            listOf("saved-pose" to GuideSource.GENERATED),
            modules.saved.saved.value.map {
                it.id to it.source
            },
        )
        assertTrue(heartAt(s, 0).isEnabled)
        assertEquals(string(R.string.remove_saved_pose), heartAt(s, 0).contentDescription)
        // Filled in red, so a saved pose shows at a glance.
        assertEquals(color(heartAt(s, 0), R.color.pix_heart), heartAt(s, 0).iconTint.defaultColor)
        assertEquals(string(R.string.pose_saved_notice), ShadowToast.getTextOfLatestToast())
        // The next page's pose is not saved.
        s.onFragment { FragmentPickPoseBinding.bind(it.requireView()).candidatePager.setCurrentItem(1, false) }
        idle()
        assertEquals(string(R.string.save_pose), heartAt(s, 1).contentDescription)
    }

    /** The heart of the page at [position] as the pager's adapter has it, which it compares off the main thread. */
    private fun heartStateAt(s: FragmentScenario<*>, position: Int): Heart? {
        var heart: Heart? = null
        s.onFragment { fragment ->
            val adapter = FragmentPickPoseBinding.bind(fragment.requireView()).candidatePager.adapter
            heart = (adapter as PosePageAdapter).currentList[position].heart
        }
        return heart
    }

    private fun MaterialButton.popping(): Boolean = (getTag(R.id.pop) as? Animator)?.isRunning == true

    @Test
    fun pick_aHeartThatSaves_popsOnce_andOneThatRemoves_doesNot() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        heartAt(s, 0).performClick()
        idleUntil { !heartAt(s, 0).isEnabled }
        assertFalse(heartAt(s, 0).popping())
        ShadowChoreographer.setPaused(true)

        modules.maker.finish(Result.success(generated))
        idleUntil { heartStateAt(s, 0) is Heart.Saved }
        layOut(pagesOf(s))

        assertTrue(heartAt(s, 0).popping())
        play()
        assertFalse(heartAt(s, 0).popping())
        assertEquals(1f, heartAt(s, 0).scaleX, 0f)

        heartAt(s, 0).performClick()
        idleUntil { heartAt(s, 0).contentDescription == string(R.string.save_pose) }
        assertFalse(heartAt(s, 0).popping())
        assertEquals(1f, heartAt(s, 0).scaleX, 0f)
    }

    @Test
    fun pick_withAnimationsOff_aHeartThatSaves_justFills() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        setAnimatorScale(0f)
        heartAt(s, 0).performClick()
        idleUntil { !heartAt(s, 0).isEnabled }

        modules.maker.finish(Result.success(generated))
        idleUntil { heartAt(s, 0).contentDescription == string(R.string.remove_saved_pose) }

        assertFalse(heartAt(s, 0).popping())
        assertEquals(1f, heartAt(s, 0).scaleX, 0f)
    }

    @Test
    fun pick_aFilledHeart_removesThePose() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        heartAt(s, 0).performClick()
        idle()
        modules.maker.finish(Result.success(generated))
        idleUntil { heartAt(s, 0).contentDescription == string(R.string.remove_saved_pose) }

        heartAt(s, 0).performClick()
        idleUntil { heartAt(s, 0).contentDescription == string(R.string.save_pose) }

        assertTrue(modules.saved.saved.value.isEmpty())
        assertEquals(string(R.string.save_pose), heartAt(s, 0).contentDescription)
        assertEquals(string(R.string.pose_removed_notice), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun pick_aHeartOnAPoseWithoutAPerson_savesNothing() {
        val modules = modules()
        val (s, _) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s

        heartAt(s, 0).performClick()
        idle()
        modules.maker.finish(Result.failure(NoPersonFoundException()))
        idleUntil { heartAt(s, 0).isEnabled }

        assertTrue(modules.saved.saved.value.isEmpty())
        assertEquals(string(R.string.save_pose), heartAt(s, 0).contentDescription)
        assertTrue(heartAt(s, 0).isEnabled)
        assertEquals(string(R.string.pose_no_person_notice), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun pick_useThisPoseAfterItsHeart_goesOnWithTheSavedGuide() {
        val modules = modules()
        val (s, nav) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        heartAt(s, 0).performClick()
        idle()
        modules.maker.finish(Result.success(generated))
        idle()

        s.onFragment { FragmentPickPoseBinding.bind(it.requireView()).usePoseButton.performClick() }
        idle()

        // Cut out once, for the heart; Reference confirm has the saved guide, so Camera knows it is saved.
        assertEquals(listOf(GuideSource.GENERATED), modules.maker.sources)
        assertEquals(R.id.referenceConfirmFragment, nav.currentDestination?.id)
        s.onFragment {
            assertEquals(
                ReferenceUiState.Ready(generated),
                it.requireActivity().viewModel<ReferenceViewModel>().uiState.value,
            )
        }
    }

    @Test
    fun pick_aHeartStillSavingWhenThePoseIsUsed_savesAllTheSame() {
        val modules = modules()
        val (s, nav) = launchPrepared<PickPoseFragment>(R.id.pickPoseFragment) { picking(modules, it) }
        scenario = s
        heartAt(s, 0).performClick()
        idle()

        s.onFragment { FragmentPickPoseBinding.bind(it.requireView()).usePoseButton.performClick() }
        idle()
        assertEquals(R.id.referenceConfirmFragment, nav.currentDestination?.id)
        // The heart's cut first, then Reference confirm's own, since the heart had not saved yet.
        modules.maker.finish(Result.success(generated))
        idle()

        assertEquals(listOf("saved-pose"), modules.saved.saved.value.map { it.id })
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
            assertEquals(string(R.string.shared_hint, "Junhyeong"), hint.text.toString())
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
    fun noPersonInThePhoto_offersToShootAgain() {
        val modules = modules()
        val (s, nav) = launchPrepared<GenerationFailedFragment>(R.id.generationFailedFragment) {
            failed(modules, it, GenerationError.NO_PERSON)
        }
        scenario = s

        s.onFragment { fragment ->
            val binding = FragmentFailureBinding.bind(fragment.requireView())
            assertEquals(string(R.string.generation_no_person_message), binding.failureMessage.text.toString())
            assertEquals(string(R.string.shoot_again), binding.primaryButton.text.toString())
            assertEquals(string(R.string.back_to_camera), binding.secondaryButton.text.toString())
            binding.primaryButton.performClick()
        }
        idle()

        // The camera takes a new scene photo; the old one and the set are gone.
        s.onFragment {
            val state = it.requireActivity().viewModel<GenerationViewModel>().uiState.value
            assertEquals(GenerationUiState(phase = GenerationUiState.Phase.FRAMING), state)
        }
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        assertEquals(1, modules.generator.runs.size)
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

    private companion object {
        /** The longest of the fades and the pop, and a little more. */
        const val MOTION_END_MS = 300L
    }
}
