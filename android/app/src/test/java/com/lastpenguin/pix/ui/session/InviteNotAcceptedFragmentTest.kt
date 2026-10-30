// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.inviteFromFriends
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
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * Invite not accepted (R&S 6.5, FR-6.3; Testing Documentation 2.1.6), on the real Fragment. Partitions: the screen as
 * opened (the friend's name, Invite again, Back to camera); Invite again sent (Invite sent), refused because the friend
 * is in another session or for another reason (said, and Invite again enabled again), or dropped while Pix was in the
 * background (Invite again enabled again on return); Back to camera.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InviteNotAcceptedFragmentTest {
    private var scenario: FragmentScenario<InviteNotAcceptedFragment>? = null
    private lateinit var nav: TestNavHostController
    private val friend = PixUser("u_2", "test2", "Test Account 2")

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** The screen after an invitation to [friend] that was not accepted, its Sent already read by Invite sent. */
    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        val (launched, navController) = launchPrepared<InviteNotAcceptedFragment>(R.id.inviteNotAcceptedFragment) {
            it.inviteFromFriends(friend)
        }
        scenario = launched
        nav = navController
        return modules
    }

    private fun <T> onScreen(block: (FragmentFailureBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentFailureBinding.bind(it.requireView())) }
        return result!!
    }

    @Test
    fun opens_withTheFriendsName_inviteAgain_andBackToCamera() {
        launch()

        onScreen { binding ->
            assertEquals(string(R.string.invite_not_accepted_title), binding.failureTitle.text.toString())
            assertEquals(
                "Test Account 2 declined or didn't answer within a minute.",
                binding.failureMessage.text.toString(),
            )
            assertEquals(string(R.string.invite_again), binding.primaryButton.text.toString())
            assertTrue(binding.primaryButton.isEnabled)
            assertEquals(string(R.string.back_to_camera), binding.secondaryButton.text.toString())
        }
    }

    @Test
    fun inviteAgain_sendsANewInvitation_andOpensInviteSent() {
        val modules = launch()

        onScreen { binding -> binding.primaryButton.performClick() }
        idle()

        assertEquals(listOf("invite:u_2", "invite:u_2"), modules.invitations.calls)
        assertEquals(R.id.inviteSentSheet, nav.currentDestination?.id)
    }

    @Test
    fun inviteAgain_refused_saysWhy_andCanBeTappedAgain() {
        val modules = launch()
        modules.invitations.failure = AccountError.BUSY

        onScreen { binding -> binding.primaryButton.performClick() }
        idle()
        assertEquals("Test Account 2 is in another session", ShadowToast.getTextOfLatestToast())
        assertTrue(onScreen { it.primaryButton.isEnabled })

        modules.invitations.failure = AccountError.OFFLINE
        onScreen { binding -> binding.primaryButton.performClick() }
        idle()
        assertEquals(string(R.string.offline_error), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.inviteNotAcceptedFragment, nav.currentDestination?.id)
    }

    @Test
    fun inviteAgain_droppedWhilePixIsInTheBackground_canBeTappedOnReturn() {
        val modules = TestAppContainer().install()
        // A whole activity, so Pix can go to the background with the screen's view kept, as on a phone.
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Pix) }
            .setup()
        controller.get().inviteFromFriends(friend)
        val screen = InviteNotAcceptedFragment()
        controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, screen).commitNow()
        idle()
        val button = FragmentFailureBinding.bind(screen.requireView()).primaryButton
        val answer = CompletableDeferred<Unit>()
        modules.invitations.inviteGate = answer

        button.performClick()
        idle()
        assertFalse(button.isEnabled)
        controller.pause().stop()
        idle()
        // The answer comes with no screen to show Invite sent, so the invitation is cancelled and reports nothing.
        answer.complete(Unit)
        idle()
        assertEquals(listOf("invite:u_2", "invite:u_2", "cancel:inv_1"), modules.invitations.calls)

        controller.start().resume()
        idle()
        assertTrue(button.isEnabled)
        controller.pause().stop().destroy()
    }

    @Test
    fun backToCamera_returnsToTheCamera() {
        launch()

        onScreen { binding -> binding.secondaryButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
