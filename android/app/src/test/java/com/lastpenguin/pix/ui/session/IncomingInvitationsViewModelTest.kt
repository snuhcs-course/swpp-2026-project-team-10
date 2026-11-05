// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.account.FakeInvitationManager
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.account.OpenedInvitation
import com.lastpenguin.pix.account.PixUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the received invitations (R&S 6.7, FR-6.17). Read: signed out (none, nothing asked), signed in (none,
 * one, two, at once and every 5 s), one answered elsewhere since (gone at the next read), a read that fails (the list
 * kept), a sign-in to another account (none until its read). Time: before and at an invitation's expiry by the phone's
 * clock. Accept: the place (Accepted, the row gone, answering meanwhile and once), gone or another account's (the row
 * gone, said), another failure (the row kept, said). Decline: the row gone, also when the call fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomingInvitationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val auth = FakeAuthRepository()
    private val invitations = FakeInvitationManager()
    private val events = mutableListOf<IncomingInvitationEvent>()

    // Test Account 1's invitation has 30 s left when the test starts, Test Account 3's 50 s.
    private val first = invitation("inv_1", PixUser("u_1", "test1", "Test Account 1"), expiresAt = START + 30_000)
    private val second = invitation("inv_2", PixUser("u_3", "test3", "Test Account 3"), expiresAt = START + 50_000)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        auth.signInAs(Account("u_2", "test2", "Test Account 2", hasPassword = true, hasGoogle = false))
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.refreshing(): IncomingInvitationsViewModel {
        val model = IncomingInvitationsViewModel(invitations, auth) { START + dispatcher.scheduler.currentTime }
        store.put("incoming", model)
        backgroundScope.launch { model.events.collect { events += it } }
        backgroundScope.launch { model.keepRefreshing() }
        runCurrent()
        return model
    }

    private val IncomingInvitationsViewModel.ids get() = uiState.value.invitations.map { it.id }

    @Test
    fun signedOut_listsNone_andAsksNothing() = runTest(dispatcher) {
        auth.expire()
        invitations.incoming = listOf(first)
        val model = refreshing()
        assertEquals(emptyList<String>(), model.ids)
        assertEquals(emptyList<String>(), invitations.calls)
    }

    @Test
    fun signedIn_readsTheList_atOnce_andEveryFiveSeconds() = runTest(dispatcher) {
        val model = refreshing()
        assertEquals(emptyList<String>(), model.ids)
        assertEquals(listOf("incoming"), invitations.calls)

        invitations.incoming = listOf(second, first)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(emptyList<String>(), model.ids)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("inv_2", "inv_1"), model.ids)

        // Accepted on another phone signed in to this account, or cancelled by the photographer.
        invitations.incoming = listOf(first)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("inv_1"), model.ids)
        assertEquals(List(3) { "incoming" }, invitations.calls)
    }

    @Test
    fun aReadThatFails_keepsTheList() = runTest(dispatcher) {
        invitations.incoming = listOf(first)
        val model = refreshing()
        invitations.failure = AccountError.NOT_FOUND // a server older than the list
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("inv_1"), model.ids)
    }

    @Test
    fun anInvitation_leavesTheListWhenItsTimeRunsOut() = runTest(dispatcher) {
        invitations.incoming = listOf(second, first)
        val model = refreshing()
        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(listOf("inv_2", "inv_1"), model.ids)
        assertEquals(START + 29_000, model.uiState.value.now)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("inv_2"), model.ids)
    }

    @Test
    fun aSignInToAnotherAccount_startsWithNone() = runTest(dispatcher) {
        invitations.incoming = listOf(first)
        val model = refreshing()
        invitations.failure = AccountError.OFFLINE
        auth.signInAs(Account("u_4", "test4", "Test Account 4", hasPassword = true, hasGoogle = false))
        runCurrent()
        assertEquals(emptyList<String>(), model.ids)
    }

    @Test
    fun accept_givesThePlace_answersOnce_andTheRowLeaves() = runTest(dispatcher) {
        invitations.incoming = listOf(second, first)
        val model = refreshing()
        invitations.acceptGate = CompletableDeferred()
        model.accept(first)
        runCurrent()
        assertEquals("inv_1", model.uiState.value.answering)
        model.accept(second)
        model.decline(second)

        invitations.acceptGate?.complete(Unit)
        runCurrent()
        assertEquals(
            listOf<IncomingInvitationEvent>(IncomingInvitationEvent.Accepted("inv_1", invitations.place, first.from)),
            events,
        )
        assertNull(model.uiState.value.answering)
        // Kept out also while the server still lists it.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("inv_2"), model.ids)
        assertEquals(listOf("incoming", "accept:inv_1", "incoming"), invitations.calls)
    }

    @Test
    fun accept_aGoneInvitationOrAnotherAccounts_removesTheRow_andSaysSo() = runTest(dispatcher) {
        invitations.incoming = listOf(second, first)
        val model = refreshing()
        invitations.failure = AccountError.INVITATION_GONE
        model.accept(first)
        runCurrent()
        invitations.failure = AccountError.NOT_FOUND
        model.accept(second)
        runCurrent()
        assertEquals(emptyList<String>(), model.ids)
        assertEquals(
            listOf<IncomingInvitationEvent>(
                IncomingInvitationEvent.Gone("inv_1"),
                IncomingInvitationEvent.Gone("inv_2"),
            ),
            events,
        )
    }

    @Test
    fun accept_failingOtherwise_keepsTheRow_andSaysWhy() = runTest(dispatcher) {
        invitations.incoming = listOf(first)
        val model = refreshing()
        invitations.failure = AccountError.OFFLINE
        model.accept(first)
        runCurrent()
        assertEquals(listOf("inv_1"), model.ids)
        assertNull(model.uiState.value.answering)
        assertEquals(
            listOf<IncomingInvitationEvent>(IncomingInvitationEvent.AnswerFailed(AccountError.OFFLINE)),
            events,
        )
    }

    @Test
    fun decline_removesTheRow_alsoWhenTheCallFails() = runTest(dispatcher) {
        invitations.incoming = listOf(second, first)
        val model = refreshing()
        invitations.failure = AccountError.OFFLINE
        model.decline(first)
        runCurrent()
        assertEquals(listOf("inv_2"), model.ids)
        assertEquals(listOf<IncomingInvitationEvent>(IncomingInvitationEvent.Declined("inv_1")), events)
        assertEquals("decline:inv_1", invitations.calls.last())
    }

    private companion object {
        const val START = 1_000_000L

        fun invitation(id: String, from: PixUser, expiresAt: Long) =
            OpenedInvitation(id, from, InvitationStatus.PENDING, expiresAt)
    }
}
