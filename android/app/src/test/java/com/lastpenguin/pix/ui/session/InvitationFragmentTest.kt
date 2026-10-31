// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * Invitation, the friend's phone (R&S 6.6; FR-6.1, FR-6.4; Testing Documentation 2.1.6), on the real Fragment opened
 * with its deep link's argument. Partitions: an open invitation (who invites, Accept and Decline); Accept taking the
 * place (Subject view once the session has it, also when it connected while the screen was stopped or after the screen
 * was recreated) or finding the session gone (no longer available); Accept with another session open on this phone,
 * before the answer (said, nothing accepted) or after it (said, no longer available); Decline (the camera); not open
 * any more (no longer available, Back to camera only); signed out (Sign in, through Friends); not loaded (Try again).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvitationFragmentTest {
    private var scenario: FragmentScenario<InvitationFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(prepare: (TestAppContainer) -> Unit = {}): TestAppContainer {
        val modules = TestAppContainer().also { it.auth.signInAs(it.auth.account) }
        prepare(modules)
        modules.install()
        val (launched, navController) = launchPrepared<InvitationFragment>(
            R.id.invitationFragment,
            bundleOf(InvitationFragment.ARG_INVITATION_ID to "inv_1"),
        ) {}
        scenario = launched
        nav = navController
        return modules
    }

    private fun <T> onScreen(block: (FragmentFailureBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentFailureBinding.bind(it.requireView())) }
        return result!!
    }

    private fun assertGone() = onScreen { binding ->
        assertEquals(string(R.string.invitation_gone_title), binding.failureTitle.text.toString())
        assertEquals(string(R.string.back_to_camera), binding.primaryButton.text.toString())
        assertFalse(binding.secondaryButton.isShown)
    }

    @Test
    fun anOpenInvitation_namesWhoInvites_withAcceptAndDecline() {
        val modules = launch()

        assertEquals(listOf("open:inv_1"), modules.invitations.calls)
        onScreen { binding ->
            assertEquals("Test Account 1 wants to show you their camera", binding.failureTitle.text.toString())
            assertEquals(string(R.string.invitation_body), binding.failureMessage.text.toString())
            assertEquals(string(R.string.accept), binding.primaryButton.text.toString())
            assertEquals(string(R.string.decline), binding.secondaryButton.text.toString())
        }
    }

    @Test
    fun accept_takesThePlace_andOpensSubjectViewOnceTheSessionHasIt() {
        val modules = launch()

        onScreen { binding -> binding.primaryButton.performClick() }
        idle()
        assertEquals(
            SessionEntry.Invitation("inv_1", modules.invitations.place) to Role.SUBJECT,
            modules.session.starts.single(),
        )
        assertEquals(R.id.invitationFragment, nav.currentDestination?.id)

        modules.session.setState(SessionState.Connecting("s_1"))
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun accept_theSessionConnectsWhileTheScreenIsStopped_opensSubjectViewOnReturn() {
        val modules = launch()
        onScreen { binding -> binding.primaryButton.performClick() }
        idle()

        // Pix in the background while the phones connect: the screen sees none of the session's changes.
        scenario!!.moveToState(Lifecycle.State.CREATED)
        modules.session.setState(SessionState.Connecting("s_1"))
        modules.session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 1"), Role.SUBJECT))
        idle()
        assertEquals(R.id.invitationFragment, nav.currentDestination?.id)

        scenario!!.moveToState(Lifecycle.State.RESUMED)
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun accept_theScreenRecreatedWhileConnecting_stillFollowsTheSession() {
        val modules = launch()
        onScreen { binding -> binding.primaryButton.performClick() }
        idle()

        scenario!!.recreate()
        // The NavController stood on the old activity's content view.
        scenario!!.onFragment {
            Navigation.setViewNavController(it.requireActivity().findViewById(android.R.id.content), nav)
        }
        idle()
        onScreen { binding ->
            assertEquals(string(R.string.connecting), binding.failureMessage.text.toString())
            assertFalse(binding.primaryButton.isEnabled)
        }
        modules.session.setState(SessionState.Connecting("s_1"))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(1, modules.session.starts.size)
    }

    @Test
    fun accept_whileInAnotherSession_saysSo_andAcceptsNothing() {
        val modules = launch()
        modules.session.setState(SessionState.Connected("s_0", PeerInfo("Dongje"), Role.SUBJECT))
        idle()

        onScreen { binding -> binding.primaryButton.performClick() }
        idle()

        assertEquals(string(R.string.invitation_in_session), ShadowToast.getTextOfLatestToast())
        assertEquals(listOf("open:inv_1"), modules.invitations.calls)
        assertTrue(modules.session.starts.isEmpty())
        onScreen { binding ->
            assertEquals(string(R.string.invitation_body), binding.failureMessage.text.toString())
            assertTrue(binding.primaryButton.isEnabled && binding.secondaryButton.isEnabled)
        }
    }

    @Test
    fun accept_answeredAsASessionOpensHere_saysSo_andTheInvitationIsGone() {
        val modules = launch()
        // The server's answer comes after another session opened on this phone.
        val gate = CompletableDeferred<Unit>()
        modules.invitations.acceptGate = gate
        onScreen { binding -> binding.primaryButton.performClick() }
        idle()
        modules.session.setState(SessionState.Waiting("482915"))
        gate.complete(Unit)
        idle()

        assertEquals(string(R.string.invitation_in_session), ShadowToast.getTextOfLatestToast())
        assertTrue(modules.session.starts.isEmpty())
        assertGone()
    }

    @Test
    fun accept_whenTheSessionIsGoneMeanwhile_saysTheInvitationIsNoLongerAvailable() {
        val modules = launch()

        onScreen { binding -> binding.primaryButton.performClick() }
        idle()
        modules.session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        idle()

        assertGone()
    }

    @Test
    fun decline_answersAndReturnsToTheCamera() {
        val modules = launch()

        onScreen { binding -> binding.secondaryButton.performClick() }
        idle()

        assertEquals("decline:inv_1", modules.invitations.calls.last())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun anInvitationNotOpenAnyMore_isNoLongerAvailable() {
        launch { it.invitations.opened = it.invitations.opened.copy(status = InvitationStatus.CANCELLED) }

        assertGone()
        onScreen { binding -> binding.primaryButton.performClick() }
        idle()
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun signedOut_offersToSignIn_throughFriends() {
        launch { it.auth.expire() }

        onScreen { binding ->
            assertEquals("Test Account 1 wants to show you their camera", binding.failureTitle.text.toString())
            assertEquals(string(R.string.invitation_sign_in), binding.failureMessage.text.toString())
            assertEquals(string(R.string.sign_in), binding.primaryButton.text.toString())
            binding.primaryButton.performClick()
        }
        idle()

        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun notLoaded_offersTryAgain() {
        val modules = launch { it.invitations.failure = AccountError.OFFLINE }

        onScreen { binding ->
            assertEquals(string(R.string.invitation_failed_title), binding.failureTitle.text.toString())
            assertEquals(string(R.string.try_again), binding.primaryButton.text.toString())
        }
        modules.invitations.failure = null
        onScreen { binding -> binding.primaryButton.performClick() }
        idle()

        assertTrue(onScreen { it.failureTitle.text.toString() } == "Test Account 1 wants to show you their camera")
    }
}
