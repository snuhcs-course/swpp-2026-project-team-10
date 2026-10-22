// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.account

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.Friend
import com.lastpenguin.pix.account.FriendRequest
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.databinding.FragmentFriendsBinding
import com.lastpenguin.pix.databinding.ItemFriendRequestBinding
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/*
 * Friends on the real Fragment (R&S 6.4; FR-5.4, FR-5.7, FR-5.9, FR-5.16, FR-8.1), with the account fakes behind
 * the ViewModels; `FriendsScreenTest` covers the layout's states. Partitions here: the account signed out (Sign in
 * and the room code lead on), without a Pix ID (Choose a Pix ID), with one (Add a friend, Sent requests, Account,
 * the refresh running); My Pix ID copied; Accept and Decline on a request, Remove on a friend after its question;
 * Back; the server ending the sign-in and a failed answer, each as a message.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FriendsFragmentTest {
    private var scenario: FragmentScenario<FriendsFragment>? = null
    private lateinit var nav: TestNavHostController

    private val account = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)
    private val friend = Friend("u_2", "test2", "Test Account 2", online = true, inSession = false)
    private val request = FriendRequest("fr_80", PixUser("u_3", "test3", "Test Account 3"), 1_000)

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(
        auth: AuthState = AuthState.SignedIn(
            account,
        ),
        modules: TestAppContainer = TestAppContainer(),
    ): TestAppContainer {
        (auth as? AuthState.SignedIn)?.let { modules.auth.signInAs(it.account) }
        modules.install()
        scenario = launchFragmentInContainer<FriendsFragment>(themeResId = R.style.Theme_Pix)
        nav = scenario!!.attachNav(R.id.friendsFragment)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentFriendsBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentFriendsBinding.bind(it.requireView())) }
        return result!!
    }

    @Test
    fun signedOut_signInOpensSignIn() {
        launch(AuthState.SignedOut)

        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.promptCard.visibility)
            assertEquals(string(R.string.signed_out_title), binding.promptTitle.text.toString())
            binding.signInButton.performClick()
        }
        idle()

        assertEquals(R.id.signInFragment, nav.currentDestination?.id)
    }

    @Test
    fun signedOut_roomCodeInstead_startsARoomAndOpensTheCode() {
        val modules = launch(AuthState.SignedOut)

        onScreen { it.roomCodeInsteadButton.performClick() }
        idle()

        assertEquals(listOf(SessionEntry.NewRoom to Role.PHOTOGRAPHER), modules.session.starts)
        assertEquals(R.id.roomCodeSheet, nav.currentDestination?.id)
    }

    @Test
    fun withoutAPixId_chooseOneOpensTheSetup() {
        launch(AuthState.SignedIn(account.copy(pixId = null, hasGoogle = true)))

        onScreen { binding ->
            assertEquals(string(R.string.choose_pix_id_title), binding.promptTitle.text.toString())
            binding.choosePixIdButton.performClick()
        }
        idle()

        assertEquals(R.id.pixIdSetupFragment, nav.currentDestination?.id)
    }

    @Test
    fun signedIn_refreshesTheLists_andOpensAddAFriend() {
        val modules = launch()

        assertTrue(modules.friends.calls.contains("refresh"))
        onScreen { binding ->
            assertEquals(string(R.string.my_pix_id, "test1"), binding.myPixIdText.text.toString())
            binding.addFriendButton.performClick()
        }
        idle()

        assertEquals(R.id.addFriendFragment, nav.currentDestination?.id)
    }

    @Test
    fun sentRequests_andAccount_openTheirScreens() {
        val modules = TestAppContainer()
        modules.friends.sentFlow.value = listOf(SentRequest("fr_9", PixUser("u_4", "test4", "Test Account 4"), 1))
        launch(modules = modules)

        onScreen { binding ->
            assertEquals(string(R.string.sent_requests_count, 1), binding.sentRequestsButton.text.toString())
            binding.sentRequestsButton.performClick()
        }
        idle()
        assertEquals(R.id.sentRequestsFragment, nav.currentDestination?.id)

        nav.setCurrentDestination(R.id.friendsFragment)
        onScreen { it.accountButton.performClick() }
        idle()
        assertEquals(R.id.accountSheet, nav.currentDestination?.id)
    }

    @Test
    fun myPixId_copiesIt() {
        launch()

        onScreen { it.myPixIdText.performClick() }
        idle()

        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService<ClipboardManager>()!!
        assertEquals("test1", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun acceptAndDecline_answerTheRequest() {
        val modules = TestAppContainer()
        modules.friends.requestsFlow.value = listOf(request)
        launch(modules = modules)

        onScreen { ItemFriendRequestBinding.bind(it.requestsList.getChildAt(0)).acceptButton.performClick() }
        idle()
        assertTrue(modules.friends.calls.contains("accept:fr_80"))

        onScreen { ItemFriendRequestBinding.bind(it.requestsList.getChildAt(0)).declineButton.performClick() }
        idle()
        assertTrue(modules.friends.calls.contains("decline:fr_80"))
    }

    @Test
    fun aLongPressOnAFriend_asksBeforeRemoving() {
        val modules = TestAppContainer()
        modules.friends.friendsFlow.value = listOf(friend)
        launch(modules = modules)

        onScreen { it.friendsList.getChildAt(0).performLongClick() }
        idle()
        val question = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(question.isShowing)

        question.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertTrue(modules.friends.calls.none { it.startsWith("remove") })

        onScreen { it.friendsList.getChildAt(0).performLongClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue(modules.friends.calls.contains("remove:u_2"))
    }

    @Test
    fun back_leavesTheScreen() {
        launch()

        onScreen { it.backButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun theServerEndingTheSignIn_andAFailedAnswer_showMessages() {
        val modules = TestAppContainer()
        modules.friends.requestsFlow.value = listOf(request)
        launch(modules = modules)

        modules.auth.expire()
        idle()
        assertEquals(string(R.string.signed_out_expired), ShadowToast.getTextOfLatestToast())

        modules.auth.signInAs(account)
        modules.friends.failure = AccountException(AccountError.UNAVAILABLE)
        onScreen { ItemFriendRequestBinding.bind(it.requestsList.getChildAt(0)).acceptButton.performClick() }
        idle()
        assertEquals(string(R.string.unavailable_error), ShadowToast.getTextOfLatestToast())
    }
}
