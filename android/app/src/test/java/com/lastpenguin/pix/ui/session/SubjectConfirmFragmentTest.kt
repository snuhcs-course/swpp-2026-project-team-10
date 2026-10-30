// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.os.Looper
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.session.ConfirmEnd
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.confirmBegin
import com.lastpenguin.pix.session.confirmChunk
import com.lastpenguin.pix.session.confirmEnd
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.ConfirmStep
import com.lastpenguin.pix.session.protocol.SessionMessage
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * The subject's Your reference (R&S 6.6, FR-6.15, Design 2.6.5) on the real Fragment, with the photographer's confirm
 * delivered to the container's ConfirmSyncer. Partitions: the outline shown as on the photographer's phone, with the
 * hint and without Choose another photo, and the progress while their phone finds the person, again after another
 * photo; Use this guide sent as a use and waiting; the confirm's end by the photographer's use or close (a notice and
 * back to Subject view), by this phone's own use (back without a notice), and an end kept from an earlier confirm
 * (nothing); a use no step answers in 5 s (a notice, and it can be tapped again); Back (Subject view, the confirm
 * kept); the link reconnecting (the outline kept, Use this guide waiting until it is back); the session ending (back
 * to Subject view, which shows what ended it).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubjectConfirmFragmentTest {
    private var scenario: FragmentScenario<SubjectConfirmFragment>? = null
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    /** A subject's phone whose syncer has the photographer's c1, finding the person, or READY with g1's outline. */
    private fun confirming(ready: Boolean = true): TestAppContainer {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.confirms.startAsReceiver(receiving)
        idle()
        step(modules, ConfirmStep.FINDING)
        if (ready) readyWith(modules, "g1")
        return modules
    }

    private fun readyWith(modules: TestAppContainer, guideId: String) {
        step(modules, ConfirmStep.READY, guideId)
        val image = FakeGuideImageCodec().encode(fakeGuide(guideId))
        modules.session.deliver(image.confirmBegin("c1"))
        for (i in image.chunks.indices) modules.session.deliver(image.confirmChunk("c1", i))
        modules.session.deliver(image.confirmEnd("c1"))
        idle()
    }

    private fun step(modules: TestAppContainer, step: ConfirmStep, guideId: String? = null, by: Role? = null) {
        modules.session.deliver(SessionMessage.GuideConfirm("c1", step, guideId, by))
        idle()
    }

    /** The screen over Subject view, as Subject view opens it. */
    private fun launch(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.setGraph(R.navigation.nav_graph)
        nav.setCurrentDestination(R.id.subjectFragment)
        nav.navigate(R.id.action_subject_to_confirm)
        // The controller is there as soon as the view is, before the screen's collectors start, as in the app.
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_Pix) {
            SubjectConfirmFragment().also { fragment ->
                fragment.viewLifecycleOwnerLiveData.observeForever { owner ->
                    if (owner != null) Navigation.setViewNavController(fragment.requireView(), nav)
                }
            }
        }
        idle()
        return nav
    }

    private fun <T> onScreen(block: (FragmentReferenceConfirmBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentReferenceConfirmBinding.bind(it.requireView())) }
        return result!!
    }

    private fun uses(modules: TestAppContainer) =
        modules.session.sent.filterIsInstance<SessionMessage.GuideConfirmUse>()

    @Test
    fun showsThePhotographersOutline_withTheHint_andWithoutChooseAnotherPhoto() {
        confirming()
        launch()

        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.referenceImage.visibility)
            assertEquals(View.GONE, binding.referenceProgress.visibility)
            assertEquals(string(R.string.person_found), binding.referenceStatus.text.toString())
            assertEquals(View.GONE, binding.chooseAnotherButton.visibility)
            assertEquals(View.GONE, binding.chosenByText.visibility)
            assertEquals(string(R.string.reference_shared_hint, "Dongje"), binding.sharedHint.text.toString())
            assertTrue(binding.useGuideButton.isEnabled)
        }
    }

    @Test
    fun whileThePhotographersPhoneFindsThePerson_showsProgress_andAgainForAnotherPhoto() {
        val modules = confirming(ready = false)
        launch()
        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.referenceProgress.visibility)
            assertEquals(View.GONE, binding.referenceImage.visibility)
            assertEquals(string(R.string.finding_person), binding.referenceStatus.text.toString())
            assertFalse(binding.useGuideButton.isEnabled)
        }

        readyWith(modules, "g1")
        assertTrue(onScreen { it.useGuideButton.isEnabled })
        step(modules, ConfirmStep.FINDING)

        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.referenceProgress.visibility)
            assertFalse(binding.useGuideButton.isEnabled)
        }
    }

    @Test
    fun useThisGuide_sendsTheOutlineOnScreen_andWaitsForTheAnswer() {
        val modules = confirming()
        launch()

        onScreen { it.useGuideButton.performClick() }
        idle()

        assertEquals(listOf(SessionMessage.GuideConfirmUse("c1", "g1")), uses(modules))
        onScreen { binding ->
            assertFalse(binding.useGuideButton.isEnabled)
            binding.useGuideButton.performClick()
        }
        idle()
        assertEquals(1, uses(modules).size)
    }

    @Test
    fun thePhotographerUsingTheGuideFirst_closesItWithANotice() {
        val modules = confirming()
        val nav = launch()

        step(modules, ConfirmStep.USED, "g1", Role.PHOTOGRAPHER)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_used_guide, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun thePhotographerClosingTheReference_closesItWithANotice() {
        val modules = confirming()
        val nav = launch()

        step(modules, ConfirmStep.CLOSED)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_closed_reference, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun thisPhonesUseTaken_closesItWithoutANotice() {
        val modules = confirming()
        val nav = launch()
        onScreen { it.useGuideButton.performClick() }
        idle()

        step(modules, ConfirmStep.USED, "g1", Role.SUBJECT)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun anEndKeptFromAnEarlierConfirm_leavesThisOneOpen() {
        // c0 ended while no screen read it; its end waits in the syncer.
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.confirms.startAsReceiver(receiving)
        idle()
        modules.session.deliver(SessionMessage.GuideConfirm("c0", ConfirmStep.FINDING))
        modules.session.deliver(SessionMessage.GuideConfirm("c0", ConfirmStep.CLOSED))
        idle()
        step(modules, ConfirmStep.FINDING)
        readyWith(modules, "g1")

        val nav = launch()

        assertEquals(R.id.subjectConfirmFragment, nav.currentDestination?.id)
        assertEquals(0, ShadowToast.shownToastCount())
        assertTrue(onScreen { it.useGuideButton.isEnabled })
    }

    @Test
    fun aUseNotAnsweredInTime_saysSo_andCanBeTappedAgain() {
        val modules = confirming()
        launch()
        onScreen { it.useGuideButton.performClick() }
        idle()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ConfirmSyncer.USE_TIMEOUT_MS + 100))

        assertEquals(string(R.string.guide_use_failed), ShadowToast.getTextOfLatestToast())
        onScreen { binding ->
            assertTrue(binding.useGuideButton.isEnabled)
            binding.useGuideButton.performClick()
        }
        idle()
        assertEquals(2, uses(modules).size)
    }

    @Test
    fun back_returnsToSubjectView_andTheConfirmStays() {
        val modules = confirming()
        val nav = launch()

        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertNotNull(modules.confirms.mirror.value)
        assertTrue(uses(modules).isEmpty())
    }

    @Test
    fun whileTheLinkReconnects_theOutlineStays_andUseThisGuideWaits() {
        val modules = confirming()
        val nav = launch()

        modules.session.setState(SessionState.Reconnecting("s1", since = 0))
        idle()

        assertEquals(R.id.subjectConfirmFragment, nav.currentDestination?.id)
        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.referenceImage.visibility)
            assertFalse(binding.useGuideButton.isEnabled)
            binding.useGuideButton.performClick()
        }
        idle()
        assertTrue(uses(modules).isEmpty())

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        idle()
        onScreen { binding ->
            assertTrue(binding.useGuideButton.isEnabled)
            binding.useGuideButton.performClick()
        }
        idle()
        assertEquals(1, uses(modules).size)
    }

    @Test
    fun theSessionEnding_returnsToSubjectView() {
        val modules = confirming()
        val nav = launch()

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun theNotice_isThePhotographersAlone() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals(
            string(R.string.peer_used_guide, "Dongje"),
            confirmEndNotice(context, ConfirmEnd("c1", ConfirmStep.USED, Role.PHOTOGRAPHER), "Dongje"),
        )
        assertEquals(null, confirmEndNotice(context, ConfirmEnd("c1", ConfirmStep.USED, Role.SUBJECT), "Dongje"))
        assertEquals(null, confirmEndNotice(context, ConfirmEnd("c1", ConfirmStep.CLOSED, null), null))
    }
}
