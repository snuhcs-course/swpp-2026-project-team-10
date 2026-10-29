// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Invitation, the friend's phone (R&S 6.6; FR-6.1, FR-6.4; Testing Documentation 2.1.6), on the real Fragment opened
 * with its deep link's argument. Partitions: an open invitation (who invites, Accept and Decline); Accept taking the
 * place (Subject view once the session has it) or finding the session gone (no longer available); Decline (the camera);
 * not open any more (no longer available, Back to camera only); signed out (Sign in, through Friends); not loaded (Try
 * again).
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
