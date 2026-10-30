// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.account

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.Friend
import com.lastpenguin.pix.account.FriendRequest
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.databinding.FragmentFriendsBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * Partitions for the Friends screen (R&S 6.4; FR-5.4, FR-5.7, FR-5.9, FR-5.10, FR-5.16, FR-8.1), on the real layout.
 * The account: loading, signed out (Sign in and the room code instead of the list), signed in without a Pix ID (Choose
 * a Pix ID), with one. The lists: no friends (the empty card), friends online, offline, and in a session; received
 * requests, one being answered (its buttons wait); sent requests (none: no button; some: their count; signed out: no
 * button). The last refresh failed. Taps: the room code, My Pix ID (copied), Accept and Decline, Sent requests, a long
 * press on a friend (Remove).
 */
@RunWith(AndroidJUnit4::class)
class FriendsScreenTest {
    private val taps = mutableListOf<String>()
    private val account = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)
    private val request = FriendRequest("fr_80", PixUser("u_3", "test3", "Test Account 3"), 1_000)
    private val testAccount2 = Friend("u_2", "test2", "Test Account 2", online = true, inSession = false)

    private fun screen(): Pair<FragmentFriendsBinding, FriendsScreen> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = FragmentFriendsBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        val actions = object : FriendsScreen.Actions {
            override fun signIn() {
                taps += "signIn"
            }

            override fun invite(friend: Friend) {
                taps += "invite:${friend.userId}"
            }

            override fun choosePixId() {
                taps += "choosePixId"
            }

            override fun addFriend() {
                taps += "addFriend"
            }

            override fun openSentRequests() {
                taps += "sentRequests"
            }

            override fun openAccount() {
                taps += "account"
            }

            override fun useRoomCode() {
                taps += "roomCode"
            }

            override fun copyPixId(pixId: String) {
                taps += "copy:$pixId"
            }

            override fun accept(request: FriendRequest) {
                taps += "accept:${request.id}"
            }

            override fun decline(request: FriendRequest) {
                taps += "decline:${request.id}"
            }

            override fun remove(friend: Friend) {
                taps += "remove:${friend.userId}"
            }
        }
        return binding to FriendsScreen(binding, actions)
    }

    private val View.shown get() = visibility == View.VISIBLE

    @Test
    fun signedOut_offersSignInAndTheRoomCodeInsteadOfTheList() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedOut, friends = listOf(testAccount2)))

        assertTrue(binding.promptCard.shown)
        assertEquals("Shoot with friends", binding.promptTitle.text.toString())
        assertTrue(binding.signInButton.shown && binding.roomCodeInsteadButton.shown)
        assertFalse(binding.addFriendButton.shown || binding.accountButton.shown || binding.myPixIdText.shown)
        assertEquals(0, binding.friendsList.childCount)
        binding.signInButton.performClick()
        binding.roomCodeInsteadButton.performClick()
        assertEquals(listOf("signIn", "roomCode"), taps)
    }

    @Test
    fun loading_showsOnlyTheRoomCode() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.Loading))

        assertFalse(binding.promptCard.shown || binding.signInButton.shown || binding.addFriendButton.shown)
        assertTrue(binding.roomCodeButton.shown)
    }

    @Test
    fun withoutAPixId_asksToChooseOne() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account.copy(pixId = null))))

        assertEquals("Choose your Pix ID", binding.promptTitle.text.toString())
        assertTrue(binding.choosePixIdButton.shown && binding.roomCodeButton.shown && binding.accountButton.shown)
        assertFalse(binding.addFriendButton.shown || binding.myPixIdText.shown || binding.emptyCard.shown)
        binding.choosePixIdButton.performClick()
        assertEquals(listOf("choosePixId"), taps)
    }

    @Test
    fun noFriends_showsTheEmptyCardAndMyPixId() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account)))

        assertTrue(binding.emptyCard.shown && binding.addFriendButton.shown)
        assertFalse(binding.networkNote.shown)
        assertFalse(binding.promptCard.shown)
        assertEquals("My Pix ID: test1", binding.myPixIdText.text.toString())
        binding.myPixIdText.performClick()
        assertEquals(listOf("copy:test1"), taps)
    }

    @Test
    fun friends_showTheirNamePixIdStatusAndInvite_whichEveryFriendGetsEvenOffline() {
        val (binding, screen) = screen()
        val friends = listOf(
            testAccount2,
            Friend("u_4", "test4", "Test Account 4", online = false, inSession = false),
            Friend("u_5", "test3", "Test Account 3", online = true, inSession = true),
        )

        screen.render(FriendsUiState(AuthState.SignedIn(account), friends = friends))

        assertFalse(binding.emptyCard.shown)
        val statuses = (0 until 3).map { binding.friendsList.getChildAt(it).findText(R.id.statusText) }
        assertEquals(listOf("Online · @test2", "Offline · @test4", "In a session · @test3"), statuses)
        // A friend in Pix's background or with Pix closed still gets the push (FR-6.1); one in a session is BUSY on
        // the server, which says so (FR-6.9).
        val invites = (0 until 3).map { binding.friendsList.getChildAt(it).findViewById<Button>(R.id.inviteButton) }
        assertTrue(invites.all { it.isEnabled })
        assertEquals("Invite Test Account 4", invites[1].contentDescription)
        invites[1].performClick()
        assertEquals(listOf("invite:u_4"), taps)
        assertTrue(binding.networkNote.shown)
    }

    @Test
    fun longPressOnAFriend_asksToRemoveThem() {
        val (binding, screen) = screen()
        screen.render(FriendsUiState(AuthState.SignedIn(account), friends = listOf(testAccount2)))

        binding.friendsList.getChildAt(0).performLongClick()

        assertEquals(listOf("remove:u_2"), taps)
    }

    @Test
    fun requests_offerAcceptAndDecline_andWaitWhileAnswered() {
        val (binding, screen) = screen()
        screen.render(FriendsUiState(AuthState.SignedIn(account), requests = listOf(request)))

        assertTrue(binding.requestsLabel.shown)
        val row = binding.requestsList.getChildAt(0)
        assertEquals("Test Account 3", row.findText(R.id.nameText))
        assertEquals("@test3", row.findText(R.id.pixIdText))
        row.findViewById<Button>(R.id.acceptButton).performClick()
        row.findViewById<Button>(R.id.declineButton).performClick()
        assertEquals(listOf("accept:fr_80", "decline:fr_80"), taps)

        screen.render(
            FriendsUiState(AuthState.SignedIn(account), requests = listOf(request), answering = setOf("fr_80")),
        )

        val waiting = binding.requestsList.getChildAt(0)
        assertFalse(waiting.findViewById<Button>(R.id.acceptButton).isEnabled)
        assertFalse(waiting.findViewById<Button>(R.id.declineButton).isEnabled)
    }

    @Test
    fun sentRequests_showTheirCountAndOpenTheirScreen() {
        val (binding, screen) = screen()
        val sent = listOf(
            SentRequest("fr_77", PixUser("u_4", "test4", "Test Account 4"), 900),
            SentRequest("fr_76", PixUser("u_5", "test5", "Test Account 5"), 800),
        )

        screen.render(FriendsUiState(AuthState.SignedIn(account), sent = sent))

        assertTrue(binding.sentRequestsButton.shown)
        assertEquals("Sent requests · 2", binding.sentRequestsButton.text.toString())
        binding.sentRequestsButton.performClick()
        assertEquals(listOf("sentRequests"), taps)

        screen.render(FriendsUiState(AuthState.SignedIn(account)))
        assertFalse(binding.sentRequestsButton.shown)
        screen.render(FriendsUiState(AuthState.SignedOut, sent = sent))
        assertFalse(binding.sentRequestsButton.shown)
    }

    @Test
    fun aFailedRefresh_isShownAboveTheList() {
        val (binding, screen) = screen()

        screen.render(FriendsUiState(AuthState.SignedIn(account), friends = listOf(testAccount2), offline = true))

        assertTrue(binding.offlineText.shown)
        assertEquals(1, binding.friendsList.childCount)
    }

    private fun View.findText(id: Int): String = findViewById<TextView>(id).text.toString()
}
