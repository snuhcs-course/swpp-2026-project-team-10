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
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.session.DescriptionEnd
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.fakeBitmap
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.sceneBegin
import com.lastpenguin.pix.session.sceneChunk
import com.lastpenguin.pix.session.sceneEnd
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * The subject's Generate poses (R&S 6.6, FR-6.15, Design 2.6.5) on the real Fragment, with the photographer's
 * description delivered to the container's DescriptionSyncer. Partitions: the scene photo, the text, and the hint as on
 * the photographer's phone; the photographer typing (the field, the chips, and the buttons wait, saying who types) and
 * done; this phone's typing sent while it holds the field and given back when it ends; a chip sent at once; Generate 4
 * poses and Skip sent and waiting; the description's end by a set started (back to Subject view without a notice), by
 * the photographer's close (a notice), and an end kept from an earlier description (nothing); a go no end answers in
 * 5 s (a notice); Back (Subject view, the description kept); the link reconnecting (everything waits); the session
 * ending (back to Subject view).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubjectDescribeFragmentTest {
    private var scenario: FragmentScenario<SubjectDescribeFragment>? = null
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    /** A subject's phone whose syncer has the photographer's d1 with [text], typed by [writer], and its photo. */
    private fun describing(text: String = "wave", writer: Role? = null): TestAppContainer {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.descriptions.startAsReceiver(receiving)
        idle()
        step(modules, DescriptionStep.OPEN, text, writer)
        val copy = checkNotNull(FakePoseCandidateCodec().encode("d1", fakeBitmap()))
        modules.session.deliver(copy.sceneBegin("d1"))
        for (i in copy.chunks.indices) modules.session.deliver(copy.sceneChunk("d1", i))
        modules.session.deliver(copy.sceneEnd("d1"))
        idle()
        return modules
    }

    private fun step(
        modules: TestAppContainer,
        step: DescriptionStep,
        text: String = "",
        writer: Role? = null,
        id: String = "d1",
    ) {
        modules.session.deliver(SessionMessage.PoseDescription(id, step, text, writer))
        idle()
    }

    /** The screen over Subject view, as Subject view opens it. */
    private fun launch(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.setGraph(R.navigation.nav_graph)
        nav.setCurrentDestination(R.id.subjectFragment)
        nav.navigate(R.id.action_subject_to_describe)
        // The controller is there as soon as the view is, before the screen's collectors start, as in the app.
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_Pix) {
            SubjectDescribeFragment().also { fragment ->
                fragment.viewLifecycleOwnerLiveData.observeForever { owner ->
                    if (owner != null) Navigation.setViewNavController(fragment.requireView(), nav)
                }
            }
        }
        idle()
        return nav
    }

    private fun <T> onScreen(block: (FragmentDescribePoseBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentDescribePoseBinding.bind(it.requireView())) }
        return result!!
    }

    private fun edits(modules: TestAppContainer) =
        modules.session.sent.filterIsInstance<SessionMessage.PoseDescriptionEdit>()

    private fun goes(modules: TestAppContainer) =
        modules.session.sent.filterIsInstance<SessionMessage.PoseDescriptionGo>()

    @Test
    fun showsThePhotographersPhotoAndText_withTheHint() {
        describing()
        launch()

        onScreen { binding ->
            assertNotNull(binding.sceneImage.drawable)
            assertEquals("wave", binding.descriptionInput.text.toString())
            assertEquals(string(R.string.describe_shared_hint, "Dongje"), binding.sharedHint.text.toString())
            assertTrue(binding.descriptionInput.isEnabled)
            assertTrue(binding.generateButton.isEnabled)
            assertEquals(View.GONE, binding.descriptionTyping.visibility)
        }
    }

    @Test
    fun whileThePhotographerTypes_everythingWaits_andTheirTextIsShown() {
        val modules = describing(writer = Role.PHOTOGRAPHER)
        launch()

        onScreen { binding ->
            assertFalse(binding.descriptionInput.isEnabled)
            assertFalse(binding.ideaChips.getChildAt(0).isEnabled)
            assertFalse(binding.generateButton.isEnabled)
            assertFalse(binding.suggestButton.isEnabled)
            assertEquals(string(R.string.peer_typing, "Dongje"), binding.descriptionTyping.text.toString())
        }

        step(modules, DescriptionStep.OPEN, "wave hello")
        onScreen { binding ->
            assertEquals("wave hello", binding.descriptionInput.text.toString())
            assertTrue(binding.descriptionInput.isEnabled)
            assertEquals(View.GONE, binding.descriptionTyping.visibility)
        }
    }

    @Test
    fun typingHere_isSentWhileThisFieldHoldsIt_andGivenBackWhenItEnds() {
        val modules = describing()
        launch()

        onScreen { binding ->
            binding.descriptionInput.requestFocus()
            binding.descriptionInput.setText("jump")
        }
        idle()
        // The photographer's echo while this phone types does not move the text under the fingers.
        step(modules, DescriptionStep.OPEN, "ju", Role.SUBJECT)
        assertEquals("jump", onScreen { it.descriptionInput.text.toString() })
        onScreen { it.descriptionInput.clearFocus() }
        idle()

        assertEquals(
            listOf(
                SessionMessage.PoseDescriptionEdit("d1", holding = true, text = "wave"),
                SessionMessage.PoseDescriptionEdit("d1", holding = true, text = "jump"),
                SessionMessage.PoseDescriptionEdit("d1", holding = false, text = "jump"),
            ),
            edits(modules),
        )
    }

    @Test
    fun aChip_isSentAtOnce() {
        val modules = describing()
        launch()

        onScreen { it.ideaChips.getChildAt(0).performClick() }
        idle()

        val idea = onScreen { it.descriptionInput.text.toString() }
        assertEquals(
            listOf(
                SessionMessage.PoseDescriptionEdit("d1", holding = true, text = idea),
                SessionMessage.PoseDescriptionEdit("d1", holding = false, text = idea),
            ),
            edits(modules),
        )
    }

    @Test
    fun generateAndSkip_areSent_andWaitForTheAnswer() {
        val modules = describing()
        launch()

        onScreen { it.generateButton.performClick() }
        idle()

        assertEquals(listOf(SessionMessage.PoseDescriptionGo("d1", "wave", suggest = false)), goes(modules))
        onScreen { binding ->
            assertFalse(binding.generateButton.isEnabled)
            assertFalse(binding.suggestButton.isEnabled)
            binding.suggestButton.performClick()
        }
        idle()
        assertEquals(1, goes(modules).size)
    }

    @Test
    fun aSetStarted_closesItWithoutANotice() {
        val modules = describing()
        val nav = launch()
        onScreen { it.suggestButton.performClick() }
        idle()

        step(modules, DescriptionStep.STARTED)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun thePhotographerClosingIt_closesItWithANotice() {
        val modules = describing()
        val nav = launch()

        step(modules, DescriptionStep.CLOSED)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.peer_closed_describe, "Dongje"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun anEndKeptFromAnEarlierDescription_leavesThisOneOpen() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.descriptions.startAsReceiver(receiving)
        idle()
        step(modules, DescriptionStep.OPEN, id = "d0")
        step(modules, DescriptionStep.CLOSED, id = "d0")
        step(modules, DescriptionStep.OPEN, "wave")

        val nav = launch()

        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun aGoNotAnsweredInTime_saysSo_andCanBeTappedAgain() {
        val modules = describing()
        launch()
        onScreen { it.generateButton.performClick() }
        idle()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DescriptionSyncer.GO_TIMEOUT_MS + 100))

        assertEquals(string(R.string.describe_go_failed), ShadowToast.getTextOfLatestToast())
        onScreen { binding ->
            assertTrue(binding.generateButton.isEnabled)
            binding.generateButton.performClick()
        }
        idle()
        assertEquals(2, goes(modules).size)
    }

    @Test
    fun back_returnsToSubjectView_andTheDescriptionStays() {
        val modules = describing()
        val nav = launch()

        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertNotNull(modules.descriptions.mirror.value)
        assertTrue(goes(modules).isEmpty())
    }

    @Test
    fun whileTheLinkReconnects_everythingWaits_untilItIsBack() {
        val modules = describing()
        val nav = launch()

        modules.session.setState(SessionState.Reconnecting("s1", since = 0))
        idle()

        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
        onScreen { binding ->
            assertFalse(binding.descriptionInput.isEnabled)
            assertFalse(binding.generateButton.isEnabled)
            binding.generateButton.performClick()
        }
        idle()
        assertTrue(goes(modules).isEmpty())

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        idle()
        assertTrue(onScreen { it.generateButton.isEnabled })
    }

    @Test
    fun theSessionEnding_returnsToSubjectView() {
        val modules = describing()
        val nav = launch()

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertNull(modules.descriptions.going.value)
    }

    @Test
    fun theNotice_isForTheCloseAlone() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals(
            string(R.string.peer_closed_describe, "Dongje"),
            describeEndNotice(context, DescriptionEnd("d1", started = false), "Dongje"),
        )
        assertNull(describeEndNotice(context, DescriptionEnd("d1", started = true), "Dongje"))
        assertNull(describeEndNotice(context, DescriptionEnd("d1", started = false), null))
    }
}
