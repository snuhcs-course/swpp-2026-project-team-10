// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.session

import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.robolectric.shadows.ShadowToast

/*
 * The subject's Pick a pose (R&S 6.6, FR-6.15, Design 2.6.5) on the real Fragment, with the photographer's set
 * delivered to the container's PoseSyncer. Partitions: the candidates shown as on the photographer's phone, with the
 * hint and without Try other poses; Use this pose sent as a pick and waiting; the set's end by the photographer's
 * choice or close (a notice and back to Subject view), by this phone's own pick (back without a notice), and an end
 * kept from an earlier set (nothing); a pick no step answers in 5 s (a notice, and it can be tapped again); Back
 * (Subject view, the set kept); the link reconnecting (the candidates kept, Use this pose waiting until it is back);
 * the session ending (back to Subject view, which shows what ended it); a 9:16 candidate in its own shape (FR-4.3).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubjectPickPoseFragmentTest {
    private var scenario: FragmentScenario<SubjectPickPoseFragment>? = null
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    /** A subject's phone whose syncer has the photographer's set1, PICKING with pose-1 and pose-2. */
    private fun picking(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.poses.startAsReceiver(receiving)
        idle()
        step(modules, PoseSetStep.GENERATING)
        candidate(modules, "pose-1")
        candidate(modules, "pose-2")
        step(modules, PoseSetStep.PICKING, poseIds = listOf("pose-1", "pose-2"))
        return modules
    }

    private fun step(
        modules: TestAppContainer,
        step: PoseSetStep,
        poseIds: List<String> = emptyList(),
        poseId: String? = null,
        by: Role? = null,
    ) {
        modules.session.deliver(SessionMessage.PoseStep("set1", step, poseIds, poseId, by))
        idle()
    }

    private fun candidate(modules: TestAppContainer, poseId: String) {
        val copy = checkNotNull(FakePoseCandidateCodec().encode(poseId, bitmap()))
        modules.session.deliver(copy.begin("set1"))
        for (i in copy.chunks.indices) modules.session.deliver(copy.chunk("set1", i))
        modules.session.deliver(copy.end("set1"))
        idle()
    }

    /** The screen over Subject view, as Subject view opens it. */
    private fun launch(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.setGraph(R.navigation.nav_graph)
        nav.setCurrentDestination(R.id.subjectFragment)
        nav.navigate(R.id.action_subject_to_pickPose)
        // The controller is there as soon as the view is, before the screen's collectors start, as in the app.
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_Pix) {
            SubjectPickPoseFragment().also { fragment ->
                fragment.viewLifecycleOwnerLiveData.observeForever { owner ->
                    if (owner != null) Navigation.setViewNavController(fragment.requireView(), nav)
                }
            }
        }
        idle()
        return nav
    }

    private fun <T> onScreen(block: (FragmentPickPoseBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentPickPoseBinding.bind(it.requireView())) }
        return result!!
    }

    private fun picks(modules: TestAppContainer) = modules.session.sent.filterIsInstance<SessionMessage.PosePick>()

    @Test
    fun showsThePhotographersCandidates_withTheHint_andWithoutTryOtherPoses() {
        picking()
        launch()

        onScreen { binding ->
            assertEquals(2, binding.candidatePager.adapter?.itemCount)
            assertEquals("1 / 2", binding.pageCounter.text.toString())
            assertEquals(View.GONE, binding.tryOtherButton.visibility)
            assertEquals(string(R.string.pose_shared_hint, "Dongje"), binding.sharedHint.text.toString())
            assertTrue(binding.usePoseButton.isEnabled)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun aNineBySixteenCandidate_showsWholeInItsOwnShape() {
        // The subject's phone has no scene photo; the page takes the shape of the copy it received.
        picking(TestAppContainer(candidateImage = { bitmap(288, 512) }))
        launch()

        onScreen { binding ->
            val pages = binding.candidatePager.getChildAt(0) as RecyclerView
            val page = checkNotNull(pages.findViewHolderForAdapterPosition(0)).itemView
            val image = page.findViewById<ImageView>(R.id.poseImage)
            assertEquals(288f / 512, image.width.toFloat() / image.height, 0.01f)
            // As large as the page allows: as wide or as tall as the page.
            assertTrue(image.width <= page.width && image.height <= page.height)
            assertTrue(image.width >= page.width - 1 || image.height >= page.height - 1)
        }
    }

    @Test
    fun useThisPose_sendsThePoseOnScreen_andWaitsForTheAnswer() {
        val modules = picking()
        launch()
        onScreen { it.candidatePager.setCurrentItem(1, false) }
        idle()

        onScreen { it.usePoseButton.performClick() }
        idle()

        assertEquals(listOf(SessionMessage.PosePick("set1", "pose-2")), picks(modules))
        onScreen { binding ->
            assertFalse(binding.usePoseButton.isEnabled)
            binding.usePoseButton.performClick()
        }
        idle()
        assertEquals(1, picks(modules).size)
    }

    @Test
    fun thePhotographerChoosingFirst_closesItWithANotice() {
        val modules = picking()
        val nav = launch()

        step(modules, PoseSetStep.CHOSEN, poseId = "pose-1", by = Role.PHOTOGRAPHER)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_chose_pose, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun thePhotographerClosingThePoses_closesItWithANotice() {
        val modules = picking()
        val nav = launch()

        step(modules, PoseSetStep.CLOSED)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_closed_poses, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun thisPhonesPickChosen_closesItWithoutANotice() {
        val modules = picking()
        val nav = launch()
        onScreen { it.usePoseButton.performClick() }
        idle()

        step(modules, PoseSetStep.CHOSEN, poseId = "pose-1", by = Role.SUBJECT)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun anEndKeptFromAnEarlierSet_leavesThisOneOpen() {
        // set0 ended while no screen read it; its end waits in the syncer.
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.poses.startAsReceiver(receiving)
        idle()
        modules.session.deliver(SessionMessage.PoseStep("set0", PoseSetStep.GENERATING))
        modules.session.deliver(SessionMessage.PoseStep("set0", PoseSetStep.CLOSED))
        idle()
        step(modules, PoseSetStep.GENERATING)
        candidate(modules, "pose-1")
        step(modules, PoseSetStep.PICKING, poseIds = listOf("pose-1"))

        val nav = launch()

        assertEquals(R.id.subjectPickPoseFragment, nav.currentDestination?.id)
        assertEquals(0, ShadowToast.shownToastCount())
        assertTrue(onScreen { it.usePoseButton.isEnabled })
    }

    @Test
    fun aPickNotAnsweredInTime_saysSo_andCanBeTappedAgain() {
        val modules = picking()
        launch()
        onScreen { it.usePoseButton.performClick() }
        idle()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PoseSyncer.PICK_TIMEOUT_MS + 100))

        assertEquals(string(R.string.pose_pick_failed), ShadowToast.getTextOfLatestToast())
        onScreen { binding ->
            assertTrue(binding.usePoseButton.isEnabled)
            binding.usePoseButton.performClick()
        }
        idle()
        assertEquals(2, picks(modules).size)
    }

    @Test
    fun back_returnsToSubjectView_andTheSetStays() {
        val modules = picking()
        val nav = launch()

        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertNotNull(modules.poses.mirror.value)
        assertTrue(picks(modules).isEmpty())
    }

    @Test
    fun whileTheLinkReconnects_theCandidatesStay_andUseThisPoseWaits() {
        val modules = picking()
        val nav = launch()

        modules.session.setState(SessionState.Reconnecting("s1", since = 0))
        idle()

        assertEquals(R.id.subjectPickPoseFragment, nav.currentDestination?.id)
        onScreen { binding ->
            assertEquals(2, binding.candidatePager.adapter?.itemCount)
            assertFalse(binding.usePoseButton.isEnabled)
            binding.usePoseButton.performClick()
        }
        idle()
        assertTrue(picks(modules).isEmpty())

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        idle()
        onScreen { binding ->
            assertTrue(binding.usePoseButton.isEnabled)
            binding.usePoseButton.performClick()
        }
        idle()
        assertEquals(1, picks(modules).size)
    }

    @Test
    fun theSessionEnding_returnsToSubjectView() {
        val modules = picking()
        val nav = launch()

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }
}
