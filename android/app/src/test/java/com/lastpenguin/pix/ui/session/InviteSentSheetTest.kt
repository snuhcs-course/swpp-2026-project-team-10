// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.content.ClipboardManager
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.databinding.SheetInviteSentBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * Invite sent's render paths (R&S 6.5; FR-6.1–6.3; Testing Documentation 2.1.6), on the real bottom sheet. Partitions
 * on the session state: Inviting (the friend's name, the link enabled), Connecting (accepted), Connected (the sheet
 * closes, nothing cancelled), Ended by a decline or the expiry (Invite not accepted), Ended otherwise (Couldn't connect,
 * the sheet closes). Input: Share invite link (the share sheet with the link), Copy link (the clipboard), Cancel
 * invite (cancelled on the server, the session left, Friends), the sheet closed while waiting (cancelled), and a
 * configuration change while waiting (kept).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InviteSentSheetTest {
    private var scenario: FragmentScenario<InviteSentSheet>? = null
    private lateinit var sheet: InviteSentSheet
    private val friend = PixUser("u_2", "test2", "Test Account 2")

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragment<InviteSentSheet>(themeResId = R.style.Theme_Pix)
        scenario!!.onFragment { sheet = it }
        idle()
        return modules
    }

    private fun <T> onSheet(block: (SheetInviteSentBinding) -> T): T =
        block(SheetInviteSentBinding.bind(sheet.requireView()))

    /** The invitation went out, as from Friends, and the session waits for the friend. */
    private fun inviting(modules: TestAppContainer, activity: FragmentActivity = sheet.requireActivity()) {
        activity.viewModel<SessionViewModel>().invite(friend)
        idle()
        modules.session.setState(SessionState.Inviting("inv_1"))
        idle()
    }

    @Test
    fun waiting_namesTheFriend_andOffersTheLink() {
        val modules = launch()
        inviting(modules)

        onSheet { binding ->
            assertEquals("T", binding.avatarText.text.toString())
            assertEquals("Waiting for Test Account 2", binding.statusText.text.toString())
            assertEquals(string(R.string.invite_waiting_body, "Test Account 2"), binding.bodyText.text.toString())
            assertTrue(binding.shareButton.isEnabled && binding.copyButton.isEnabled)
        }
    }

    @Test
    fun share_handsTheLinkToTheShareSheet() {
        val modules = launch()
        inviting(modules)

        onSheet { binding -> binding.shareButton.performClick() }

        val chooser = shadowOf(sheet.requireActivity()).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.endsWith("http://pix.test/i/inv_1"))
    }

    @Test
    fun copy_putsTheLinkOnTheClipboard() {
        val modules = launch()
        inviting(modules)

        onSheet { binding -> binding.copyButton.performClick() }

        val clipboard = sheet.requireContext().getSystemService<ClipboardManager>()!!
        assertEquals("http://pix.test/i/inv_1", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun theFriendAccepts_connecting_thenConnectedClosesTheSheetWithoutCancelling() {
        val modules = launch()
        inviting(modules)

        modules.session.setState(SessionState.Connecting("s_1"))
        idle()
        onSheet { binding ->
            assertEquals("Test Account 2 accepted · connecting…", binding.statusText.text.toString())
            assertFalse(binding.shareButton.isEnabled)
        }

        modules.session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        idle()
        assertFalse(sheet.isAdded)
        assertTrue(modules.session.leaves.isEmpty())
        assertEquals(listOf("invite:u_2"), modules.invitations.calls)
    }

    @Test
    fun aDeclineOrTheExpiry_opensInviteNotAccepted() {
        for (reason in listOf(EndReason.DECLINED, EndReason.EXPIRED)) {
            val modules = launch()
            val nav = scenario!!.attachNav(R.id.inviteSentSheet)
            inviting(modules)

            modules.session.setState(SessionState.Ended(reason))
            idle()

            assertEquals(reason.name, R.id.inviteNotAcceptedFragment, nav.currentDestination?.id)
            // Going away on purpose cancels nothing.
            assertTrue(modules.session.leaves.isEmpty())
            scenario?.close()
        }
    }

    @Test
    fun anotherEnd_saysCouldntConnect_andCloses() {
        val modules = launch()
        inviting(modules)

        modules.session.setState(SessionState.Ended(EndReason.ERROR))
        idle()

        assertEquals(string(R.string.couldnt_connect), ShadowToast.getTextOfLatestToast())
        assertFalse(sheet.isAdded)
    }

    @Test
    fun cancelInvite_cancelsOnTheServer_leaves_andOpensFriends() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.inviteSentSheet)
        inviting(modules)

        onSheet { binding -> binding.cancelButton.performClick() }
        idle()

        assertEquals(listOf("invite:u_2", "cancel:inv_1"), modules.invitations.calls)
        assertEquals(listOf(EndReason.CANCELLED), modules.session.leaves)
        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun closingTheSheetWhileWaiting_cancelsTheInvitation() {
        val modules = launch()
        inviting(modules)

        sheet.dismiss()
        idle()

        assertEquals("cancel:inv_1", modules.invitations.calls.last())
        assertEquals(listOf(EndReason.CANCELLED), modules.session.leaves)
    }

    @Test
    fun aConfigurationChange_whileWaiting_keepsTheInvitation() {
        val modules = TestAppContainer().install()
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            .apply { get().setTheme(R.style.Theme_Pix) }
            .setup()
        InviteSentSheet().show(controller.get().supportFragmentManager, "inviteSent")
        idle()
        inviting(modules, controller.get())

        controller.recreate()
        idle()

        assertTrue(modules.session.leaves.isEmpty())
        val sheet = controller.get().supportFragmentManager.findFragmentByTag("inviteSent") as InviteSentSheet
        val status = SheetInviteSentBinding.bind(sheet.requireView()).statusText.text.toString()
        assertEquals("Waiting for Test Account 2", status)
    }
}
