// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.account.FakeFriendsRepository
import com.lastpenguin.pix.account.Friend
import com.lastpenguin.pix.account.FriendRequest
import com.lastpenguin.pix.account.FriendsRepository
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.Relation
import com.lastpenguin.pix.account.RequestOutcome
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.account.UserLookup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Partitions for Friends, Add a friend, and Sent requests (FR-5.4–5.10, FR-5.16, US-7, US-8). Refreshing: signed out, signed in without a Pix
 * ID (never), with one (at once, then every 10 s), a failed refresh (offline shown) and a later good one, signing out
 * (stops). Search: found, not found, failed, blank input, capitals and spaces. On the result: Add (Requested with its
 * id, or Friends when they had asked), Accept for INCOMING, Withdraw for REQUESTED, a button that does not match the
 * relation (ignored), a failure (the result stays, an event). Requests: accept and decline (disabled while answered),
 * a failure (event). Sent requests: in the state; Withdraw (disabled while it runs, a second tap ignored), a failure
 * (event). Remove. The server ending the sign-in (event).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val auth = FakeAuthRepository()
    private val repository = FakeFriendsRepository()
    private lateinit var model: FriendsViewModel
    private val events = mutableListOf<FriendsEvent>()

    private val testAccount3 = PixUser("u_3", "test3", "Test Account 3")
    private val request = FriendRequest("fr_80", testAccount3, 1_000)
    private val sent = SentRequest("fr_77", PixUser("u_4", "test4", "Test Account 4"), 900)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = FriendsViewModel(auth, repository)
        store.put("friends", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.collectEvents() {
        backgroundScope.launch(dispatcher) { model.events.toList(events) }
        runCurrent()
    }

    private fun TestScope.keepRefreshing() {
        backgroundScope.launch(dispatcher) { model.keepRefreshing() }
        runCurrent()
    }

    private fun refreshes() = repository.calls.count { it == "refresh" }

    @Test
    fun refreshing_signedOutOrWithoutAPixId_neverAsks() = runTest {
        keepRefreshing()
        auth.signInAs(auth.account.copy(pixId = null))
        advanceTimeBy(30_000)

        assertEquals(0, refreshes())
    }

    @Test
    fun refreshing_withAPixId_asksAtOnceThenEveryTenSeconds() = runTest {
        auth.signInAs(auth.account)
        keepRefreshing()
        assertEquals(1, refreshes())

        advanceTimeBy(FriendsViewModel.REFRESH_INTERVAL_MS - 1)
        assertEquals(1, refreshes())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, refreshes())
    }

    @Test
    fun refreshing_aFailure_showsOfflineUntilTheNextGoodOne() = runTest {
        auth.signInAs(auth.account)
        repository.failure = AccountException(AccountError.OFFLINE)
        keepRefreshing()
        assertTrue(model.uiState.value.offline)

        repository.failure = null
        advanceTimeBy(FriendsViewModel.REFRESH_INTERVAL_MS)
        runCurrent()
        assertFalse(model.uiState.value.offline)
    }

    @Test
    fun refreshing_stopsWhenSignedOut() = runTest {
        auth.signInAs(auth.account)
        keepRefreshing()

        auth.signOut()
        advanceTimeBy(60_000)

        assertEquals(1, refreshes())
    }

    @Test
    fun uiState_followsTheAccountAndTheLists() = runTest {
        auth.signInAs(auth.account)
        repository.requestsFlow.value = listOf(request)
        repository.sentFlow.value = listOf(sent)
        runCurrent()

        assertEquals(AuthState.SignedIn(auth.account), model.uiState.value.auth)
        assertEquals(listOf(request), model.uiState.value.requests)
        assertEquals(listOf(sent), model.uiState.value.sent)
    }

    @Test
    fun search_found_showsTheResult() = runTest {
        val found = UserLookup.Found(testAccount3, Relation.NONE, null)
        repository.lookup = found

        model.search("  Test3 ")
        assertEquals(SearchPhase.SEARCHING, model.search.value.phase)
        runCurrent()

        assertEquals(SearchUiState(SearchPhase.FOUND, "test3", result = found), model.search.value)
        assertEquals(listOf("search:test3"), repository.calls)
    }

    @Test
    fun search_notFound_keepsTheQuery() = runTest {
        model.search("test99")
        runCurrent()

        assertEquals(SearchUiState(SearchPhase.NOT_FOUND, "test99"), model.search.value)
    }

    @Test
    fun search_failed_saysWhy() = runTest {
        repository.failure = AccountException(AccountError.OFFLINE)

        model.search("test3")
        runCurrent()

        assertEquals(SearchPhase.FAILED, model.search.value.phase)
        assertEquals(AccountError.OFFLINE, model.search.value.error)
    }

    @Test
    fun search_blank_sendsNothing() = runTest {
        model.search("   ")
        runCurrent()

        assertEquals(SearchUiState(), model.search.value)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun add_sent_showsRequestedWithItsId() = runTest {
        repository.lookup = UserLookup.Found(testAccount3, Relation.NONE, null)
        repository.outcome = RequestOutcome.Sent("fr_9")
        model.search("test3")
        runCurrent()

        model.addFound()
        assertTrue(model.search.value.busy)
        runCurrent()

        assertEquals(UserLookup.Found(testAccount3, Relation.REQUESTED, "fr_9"), model.search.value.result)
        assertFalse(model.search.value.busy)
    }

    @Test
    fun add_toSomeoneWhoHadAsked_showsFriends() = runTest {
        repository.lookup = UserLookup.Found(testAccount3, Relation.NONE, null)
        repository.outcome = RequestOutcome.BecameFriends(Friend("u_3", "test3", "Test Account 3", true, false))
        model.search("test3")
        runCurrent()

        model.addFound()
        runCurrent()

        assertEquals(Relation.FRIEND, model.search.value.result?.relation)
    }

    @Test
    fun acceptAndWithdraw_onTheResult_useItsRequestId() = runTest {
        repository.lookup = UserLookup.Found(testAccount3, Relation.INCOMING, "fr_80")
        model.search("test3")
        runCurrent()
        model.acceptFound()
        runCurrent()
        assertEquals(UserLookup.Found(testAccount3, Relation.FRIEND, null), model.search.value.result)

        repository.lookup = UserLookup.Found(testAccount3, Relation.REQUESTED, "fr_9")
        model.search("test3")
        runCurrent()
        model.withdrawFound()
        runCurrent()

        assertEquals(UserLookup.Found(testAccount3, Relation.NONE, null), model.search.value.result)
        assertTrue("accept:fr_80" in repository.calls && "cancel:fr_9" in repository.calls)
    }

    @Test
    fun aButtonThatDoesNotMatchTheRelation_isIgnored() = runTest {
        repository.lookup = UserLookup.Found(testAccount3, Relation.SELF, null)
        model.search("test1")
        runCurrent()

        model.addFound()
        model.acceptFound()
        runCurrent()

        assertEquals(listOf("search:test1"), repository.calls)
    }

    @Test
    fun add_failed_keepsTheResultAndTellsWhy() = runTest {
        collectEvents()
        repository.lookup = UserLookup.Found(testAccount3, Relation.NONE, null)
        model.search("test3")
        runCurrent()
        repository.failure = AccountException(AccountError.OFFLINE)

        model.addFound()
        runCurrent()

        assertEquals(Relation.NONE, model.search.value.result?.relation)
        assertEquals(listOf<FriendsEvent>(FriendsEvent.Failed(AccountError.OFFLINE)), events)
    }

    @Test
    fun answeringARequest_disablesItUntilAnswered() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = object : FriendsRepository by repository {
            override suspend fun accept(requestId: String): Result<Friend> {
                gate.await()
                return repository.accept(requestId)
            }
        }
        val model = FriendsViewModel(auth, slow).also { store.put("slow", it) }

        model.accept(request)
        runCurrent()
        assertEquals(setOf("fr_80"), model.uiState.value.answering)
        model.accept(request)
        gate.complete(Unit)
        runCurrent()

        assertEquals(emptySet<String>(), model.uiState.value.answering)
        assertEquals(listOf("accept:fr_80"), repository.calls)
    }

    @Test
    fun withdrawingASentRequest_disablesItUntilDone() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = object : FriendsRepository by repository {
            override suspend fun cancel(requestId: String): Result<Unit> {
                gate.await()
                return repository.cancel(requestId)
            }
        }
        val model = FriendsViewModel(auth, slow).also { store.put("slow", it) }

        model.withdraw(sent)
        runCurrent()
        assertEquals(setOf("fr_77"), model.uiState.value.answering)
        model.withdraw(sent)
        gate.complete(Unit)
        runCurrent()

        assertEquals(emptySet<String>(), model.uiState.value.answering)
        assertEquals(listOf("cancel:fr_77"), repository.calls)
    }

    @Test
    fun withdraw_failed_tellsWhy() = runTest {
        collectEvents()
        repository.failure = AccountException(AccountError.NOT_FOUND)

        model.withdraw(sent)
        runCurrent()

        assertEquals(listOf<FriendsEvent>(FriendsEvent.Failed(AccountError.NOT_FOUND)), events)
    }

    @Test
    fun declineAndRemove_failed_tellWhy() = runTest {
        collectEvents()
        repository.failure = AccountException(AccountError.NOT_FOUND)

        model.decline(request)
        model.remove(Friend("u_2", "test2", "Test Account 2", online = false, inSession = false))
        runCurrent()

        assertEquals(listOf("decline:fr_80", "remove:u_2"), repository.calls)
        assertEquals(List(2) { FriendsEvent.Failed(AccountError.NOT_FOUND) }, events)
    }

    @Test
    fun theServerEndingTheSignIn_isTold() = runTest {
        collectEvents()

        auth.expire()
        runCurrent()

        assertEquals(listOf<FriendsEvent>(FriendsEvent.SignedOutByServer), events)
    }

    @Test
    fun clearSearch_startsOver() = runTest {
        model.search("test3")
        runCurrent()

        model.clearSearch()

        assertEquals(SearchPhase.IDLE, model.search.value.phase)
        assertNull(model.search.value.result)
    }
}
