// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.account.FakeInvitationManager
import com.lastpenguin.pix.account.InvitationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the friend's Invitation screen (R&S 6.6, FR-6.4). Open: pending and signed in (Open), pending and
 * signed out (SignedOut, then Open after a sign-in), each status that is not pending (Gone), unknown (Gone), no
 * connection (Failed, then Try again). Accept: the place (Accepted), answering meanwhile, gone or another account's
 * (Gone), another failure (Open again, and said). Decline: Declined, also when the call fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvitationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val auth = FakeAuthRepository()
    private val invitations = FakeInvitationManager()
    private val events = mutableListOf<InvitationEvent>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        auth.signInAs(auth.account)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.opened(): InvitationViewModel {
        val model = InvitationViewModel(invitations, auth).also { store.put("invitation", it) }
        backgroundScope.launch { model.events.collect { events += it } }
        model.open("inv_1")
        runCurrent()
        return model
    }

    @Test
    fun open_aPendingInvitation_offersAnAnswer_andLoadsItOnce() = runTest(dispatcher) {
        val model = InvitationViewModel(invitations, auth).also { store.put("invitation", it) }
        model.open("inv_1")
        assertEquals(InvitationUiState.Loading, model.uiState.value)
        runCurrent()
        assertEquals(InvitationUiState.Open("Test Account 1"), model.uiState.value)

        model.open("inv_1")
        runCurrent()
        assertEquals(listOf("open:inv_1"), invitations.calls)
    }

    @Test
    fun open_anInvitationNotPendingOrUnknown_isGone() = runTest(dispatcher) {
        for (status in InvitationStatus.entries - InvitationStatus.PENDING) {
            invitations.opened = invitations.opened.copy(status = status)
            assertEquals(status.name, InvitationUiState.Gone, opened().uiState.value)
        }
        invitations.failure = AccountError.NOT_FOUND
        assertEquals(InvitationUiState.Gone, opened().uiState.value)
    }

    @Test
    fun open_signedOut_asksToSignIn_thenOffersTheAnswer() = runTest(dispatcher) {
        auth.expire()
        val model = opened()
        assertEquals(InvitationUiState.SignedOut("Test Account 1"), model.uiState.value)

        auth.signInAs(auth.account)
        runCurrent()
        assertEquals(InvitationUiState.Open("Test Account 1"), model.uiState.value)
    }

    @Test
    fun open_withoutAConnection_fails_andTryAgainLoadsIt() = runTest(dispatcher) {
        invitations.failure = AccountError.OFFLINE
        val model = opened()
        assertEquals(InvitationUiState.Failed, model.uiState.value)

        invitations.failure = null
        model.retry()
        runCurrent()
        assertEquals(InvitationUiState.Open("Test Account 1"), model.uiState.value)
    }

    @Test
    fun accept_givesThePlace_andAnswersOnce() = runTest(dispatcher) {
        val model = opened()
        invitations.acceptGate = CompletableDeferred()
        model.accept()
        runCurrent()
        assertEquals(InvitationUiState.Open("Test Account 1", answering = true), model.uiState.value)
        model.accept()
        model.decline()

        invitations.acceptGate?.complete(Unit)
        runCurrent()
        assertEquals(listOf<InvitationEvent>(InvitationEvent.Accepted("inv_1", invitations.place)), events)
        assertEquals(listOf("open:inv_1", "accept:inv_1"), invitations.calls)
    }

    @Test
    fun accept_aGoneInvitationOrAnotherAccounts_saysItIsGone() = runTest(dispatcher) {
        for (error in listOf(AccountError.INVITATION_GONE, AccountError.NOT_FOUND)) {
            val model = opened()
            invitations.failure = error
            model.accept()
            runCurrent()
            assertEquals(error.name, InvitationUiState.Gone, model.uiState.value)
            invitations.failure = null
        }
        assertEquals(emptyList<InvitationEvent>(), events)
    }

    @Test
    fun accept_failingOtherwise_offersTheAnswerAgain_andSaysWhy() = runTest(dispatcher) {
        val model = opened()
        invitations.failure = AccountError.OFFLINE
        model.accept()
        runCurrent()
        assertEquals(InvitationUiState.Open("Test Account 1"), model.uiState.value)
        assertEquals(listOf<InvitationEvent>(InvitationEvent.AnswerFailed(AccountError.OFFLINE)), events)
    }

    @Test
    fun decline_closes_alsoWhenTheCallFails() = runTest(dispatcher) {
        val model = opened()
        invitations.failure = AccountError.OFFLINE
        model.decline()
        runCurrent()
        assertEquals(listOf<InvitationEvent>(InvitationEvent.Declined), events)
        assertEquals("decline:inv_1", invitations.calls.last())
    }

    @Test
    fun gone_afterTheSessionCouldNotTakeThePlace_staysGone() = runTest(dispatcher) {
        val model = opened()
        model.gone()
        auth.expire()
        runCurrent()
        assertEquals(InvitationUiState.Gone, model.uiState.value)
    }
}
