// AI-generated with Claude Code, 2026-11-11, reviewed by Sungmin Jo, Joonhyung Han, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentGeneratingBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * The subject's Generating poses (R&S 6.6, FR-6.15, Iter3-A13) on the real Fragment, with the photographer's set
 * delivered to the container's PoseSyncer and its description to the DescriptionSyncer. Partitions: the slots in the
 * photographer's order, filled as copies arrive, a failed one saying so, with the count; before the slots are known,
 * "Choosing poses…" for suggested poses and "Creating…" for a description; a photographer of before Iter3-A13, whose
 * copies fill in as they arrive; the set's Pick a pose with copies here (on to the subject's Pick a pose, in this
 * screen's place) and without any (back to Subject view); the set's end (back, with a notice that the photographer
 * stopped, that every pose failed, or, from a photographer that says why, the reason as on its phone, #203); Back
 * (Subject view, nothing sent); the session ending (back).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubjectGeneratingFragmentTest {
    private var scenario: FragmentScenario<SubjectGeneratingFragment>? = null
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val slots = listOf("pose-1", "pose-2", "pose-3", "pose-4")

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    /** A subject's phone whose syncers have the photographer's description d1 started into set1, GENERATING. */
    private fun generating(suggested: Boolean = false): TestAppContainer {
        // Copies with pixels, as the grid shows them.
        val modules = TestAppContainer(candidateImage = { bitmap() }).install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.poses.startAsReceiver(receiving)
        modules.descriptions.startAsReceiver(receiving)
        idle()
        modules.session.deliver(SessionMessage.PoseDescription("d1", DescriptionStep.OPEN, "jump"))
        val text = if (suggested) "" else "jump"
        modules.session.deliver(SessionMessage.PoseDescription("d1", DescriptionStep.STARTED, text))
        step(modules, PoseSetStep.GENERATING)
        return modules
    }

    private fun step(modules: TestAppContainer, step: PoseSetStep, poseIds: List<String> = emptyList()) {
        modules.session.deliver(SessionMessage.PoseStep("set1", step, poseIds))
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
        nav.navigate(R.id.action_subject_to_generating)
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_Pix) {
            SubjectGeneratingFragment().also { fragment ->
                fragment.viewLifecycleOwnerLiveData.observeForever { owner ->
                    if (owner != null) Navigation.setViewNavController(fragment.requireView(), nav)
                }
            }
        }
        idle()
        return nav
    }

    private fun <T> onScreen(block: (FragmentGeneratingBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentGeneratingBinding.bind(it.requireView())) }
        return result!!
    }

    private fun RecyclerView.slotViews(): List<View> = (0 until childCount).map { getChildAt(it) }

    /** What each slot on screen shows: its candidate ("ready"), or its status text. */
    private fun shownSlots(): List<String> = onScreen { binding ->
        binding.candidateGrid.slotViews().map { slot ->
            val image = (slot.findViewById<ImageView>(R.id.poseImage).drawable as? BitmapDrawable)?.bitmap
            if (image != null) "ready" else slot.findViewById<TextView>(R.id.poseStatus).text.toString()
        }
    }

    /** Waits for the grid, whose adapter compares each new list off the main thread, to show [expected]. */
    private fun waitForSlots(expected: List<String>) {
        idleUntil { shownSlots() == expected }
        assertEquals(expected, shownSlots())
    }

    @Test
    fun showsTheSlotsInThePhotographersOrder_fillingAsCopiesArrive_andAFailure() {
        val modules = generating()
        launch()

        modules.session.deliver(SessionMessage.PosePlanned("set1", slots))
        candidate(modules, "pose-3")
        modules.session.deliver(SessionMessage.PoseCandidateFailed("set1", "pose-2"))
        idle()

        val creating = string(R.string.creating_pose)
        waitForSlots(listOf(creating, string(R.string.pose_failed), "ready", creating))
        onScreen { binding ->
            assertEquals(string(R.string.ready_count, 1, 4), binding.readyCount.text.toString())
            assertNotNull(binding.sceneImage.drawable)
            // Back at the top left; the set is the photographer's to stop.
            assertEquals(string(R.string.back), binding.backButton.contentDescription)
        }
    }

    @Test
    fun beforeItsSlots_saysWhatTheSetDoes() {
        generating(suggested = true)
        launch()
        assertEquals(string(R.string.choosing_poses), onScreen { it.readyCount.text.toString() })

        scenario!!.close()
        generating(suggested = false)
        launch()
        assertEquals(string(R.string.creating_pose), onScreen { it.readyCount.text.toString() })
    }

    @Test
    fun aPhotographerThatSaysNoSlots_fillsItsCopiesInAsTheyArrive() {
        val modules = generating()
        launch()

        candidate(modules, "pose-2")
        candidate(modules, "pose-1")

        waitForSlots(listOf("ready", "ready"))
    }

    @Test
    fun itsPickAPose_takesThisScreensPlace() {
        val modules = generating()
        val nav = launch()
        candidate(modules, "pose-1")

        step(modules, PoseSetStep.PICKING, poseIds = listOf("pose-1"))

        val screens = nav.currentBackStack.value.map { it.destination.id }
        assertEquals(listOf(R.id.subjectFragment, R.id.subjectPickPoseFragment), screens.takeLast(2))
        assertTrue(R.id.subjectGeneratingFragment !in screens)
    }

    @Test
    fun itsPickAPoseWithoutACopy_goesBackToSubjectView() {
        val modules = generating()
        val nav = launch()

        step(modules, PoseSetStep.PICKING, poseIds = listOf("pose-1"))

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun theSetStopped_goesBack_sayingSo() {
        val modules = generating()
        val nav = launch()

        step(modules, PoseSetStep.CLOSED)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_stopped_poses, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun everyPoseFailing_goesBack_sayingSo() {
        val modules = generating()
        val nav = launch()
        modules.session.deliver(SessionMessage.PosePlanned("set1", slots))
        for (slot in slots) modules.session.deliver(SessionMessage.PoseCandidateFailed("set1", slot))
        idle()

        step(modules, PoseSetStep.CLOSED)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_poses_failed, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aSetThatFailed_goesBack_sayingWhy_asThePhotographersPhoneDoes() {
        val modules = generating()
        val nav = launch()

        // As suggestions that reached the day's limit end a set before any slot (#203).
        modules.session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.CLOSED, failure = "DAILY_LIMITED"))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(
            string(R.string.peer_poses_failed, "Dongje") + "\n" + string(R.string.generation_daily_limit_message),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun back_returnsToSubjectView_andSendsNothing() {
        val modules = generating()
        val nav = launch()
        modules.session.sent.clear()

        onScreen { it.backButton.performClick() }
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertTrue(modules.session.sent.isEmpty())
        assertNotNull(modules.poses.mirror.value)
    }

    @Test
    fun theSessionEnding_goesBackToSubjectView() {
        val modules = generating()
        val nav = launch()

        modules.session.setState(SessionState.Ended(EndReason.LEFT))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertNull(ShadowToast.getTextOfLatestToast())
    }
}
