// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.FriendDto
import com.lastpenguin.pix.core.network.FriendRequestBody
import com.lastpenguin.pix.core.network.FriendRequestsDto
import com.lastpenguin.pix.core.network.IncomingRequestDto
import com.lastpenguin.pix.core.network.LookupBody
import com.lastpenguin.pix.core.network.LookupDto
import com.lastpenguin.pix.core.network.OutgoingRequestDto
import com.lastpenguin.pix.core.network.SentRequestDto
import com.lastpenguin.pix.core.network.UserDto
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for friends and requests (FR-5.4–5.10, FR-5.16, US-7, US-8; Design 2.5.3). Refresh: the friends, the
 * received requests, and the sent ones. Search: each relation (NONE, SELF, FRIEND, REQUESTED, INCOMING) with its request id, an unknown relation (read
 * as NONE), 404 (NotFound), capitals and spaces around the Pix ID. Add: a new request, and one that accepts the other
 * user's request (friends at once, the request leaves the list). Answers: accept (the friend joins the list in name
 * order), decline, a request already gone (404: it leaves the list and the call fails). Sent requests: a new request
 * joins them first, one that makes friends at once does not, a request without its user in the answer waits for the
 * next refresh; withdrawn (it leaves), already answered (404: it leaves and the call fails), not withdrawn for another
 * reason (it stays). Remove a friend. Another user signing in empties the lists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteFriendsRepositoryTest {
    private val api = FakeAccountApi()
    private val auth = MutableStateFlow<AuthState>(AuthState.SignedIn(accountDto().toAccount()))

    private fun TestScope.repository() = RemoteFriendsRepository(api, auth, backgroundScope).also { runCurrent() }

    private val testAccount3 = UserDto("u_3", "test3", "Test Account 3")
    private val testAccount2 = FriendDto("u_2", "test2", "Test Account 2", online = true, inSession = false)

    private val testAccount4 = UserDto("u_4", "test4", "Test Account 4")

    /** A repository whose last refresh brought [sent] as the sent requests. */
    private suspend fun TestScope.withSent(vararg sent: OutgoingRequestDto): RemoteFriendsRepository {
        api.requests = FriendRequestsDto(outgoing = sent.toList())
        return repository().also { it.refresh().getOrThrow() }
    }

    @Test
    fun refresh_loadsFriendsAndBothKindsOfRequests() = runTest {
        api.friends = listOf(testAccount2)
        api.requests = FriendRequestsDto(
            incoming = listOf(IncomingRequestDto("fr_80", testAccount3, 1_000)),
            outgoing = listOf(OutgoingRequestDto("fr_77", testAccount4, 900)),
        )
        val repository = repository()

        repository.refresh().getOrThrow()

        assertEquals(
            listOf(Friend("u_2", "test2", "Test Account 2", online = true, inSession = false)),
            repository.friends.value,
        )
        assertEquals(
            listOf(FriendRequest("fr_80", PixUser("u_3", "test3", "Test Account 3"), 1_000)),
            repository.requests.value,
        )
        assertEquals(
            listOf(SentRequest("fr_77", PixUser("u_4", "test4", "Test Account 4"), 900)),
            repository.sentRequests.value,
        )
    }

    @Test
    fun search_eachRelation_isFoundWithItsRequestId() = runTest {
        val repository = repository()
        for (relation in Relation.entries) {
            val requestId = if (relation == Relation.REQUESTED || relation == Relation.INCOMING) "fr_1" else null
            api.lookup = { LookupDto(testAccount3, relation.name, requestId) }

            val found = repository.search("test3").getOrThrow()

            assertEquals(UserLookup.Found(PixUser("u_3", "test3", "Test Account 3"), relation, requestId), found)
        }
    }

    @Test
    fun search_anUnknownRelation_isReadAsNone() = runTest {
        api.lookup = { LookupDto(testAccount3, "BLOCKED") }

        assertEquals(Relation.NONE, (repository().search("test3").getOrThrow() as UserLookup.Found).relation)
    }

    @Test
    fun search_unknownPixId_isNotFound() = runTest {
        assertEquals(UserLookup.NotFound, repository().search("test99").getOrThrow())
    }

    @Test
    fun search_sendsThePixIdWithoutCapitalsOrSpaces() = runTest {
        repository().search(" Test2 ")

        assertEquals(LookupBody("test2"), api.bodies.single())
    }

    @Test
    fun request_new_isSent() = runTest {
        api.sent = SentRequestDto(id = "fr_9")

        assertEquals(RequestOutcome.Sent("fr_9"), repository().request("u_2").getOrThrow())
        assertEquals(FriendRequestBody("u_2"), api.bodies.single())
    }

    @Test
    fun request_new_joinsTheSentRequestsFirst() = runTest {
        val repository = withSent(OutgoingRequestDto("fr_77", testAccount4, 900))
        api.sent = SentRequestDto(id = "fr_9", to = testAccount3, createdAt = 1_000)

        repository.request("u_3").getOrThrow()

        assertEquals(listOf("fr_9", "fr_77"), repository.sentRequests.value.map { it.id })
        assertEquals(PixUser("u_3", "test3", "Test Account 3"), repository.sentRequests.value.first().to)
    }

    @Test
    fun request_answeredWithoutItsUser_waitsForTheNextRefresh() = runTest {
        val repository = repository()
        api.sent = SentRequestDto(id = "fr_9")

        repository.request("u_3").getOrThrow()

        assertTrue(repository.sentRequests.value.isEmpty())
    }

    @Test
    fun request_toSomeoneWhoAlreadyAsked_makesFriendsAndClearsTheirRequest() = runTest {
        api.requests = FriendRequestsDto(incoming = listOf(IncomingRequestDto("fr_80", testAccount3, 1_000)))
        api.sent = SentRequestDto(friend = FriendDto("u_3", "test3", "Test Account 3", online = false))
        val repository = repository()
        repository.refresh()

        val outcome = repository.request("u_3").getOrThrow()

        assertTrue(outcome is RequestOutcome.BecameFriends)
        assertEquals(listOf("u_3"), repository.friends.value.map { it.userId })
        assertTrue(repository.requests.value.isEmpty())
        assertTrue(repository.sentRequests.value.isEmpty())
    }

    @Test
    fun accept_movesTheRequestIntoTheFriendsInNameOrder() = runTest {
        api.friends = listOf(FriendDto("u_9", "zed.z", "Zed", online = false))
        api.requests = FriendRequestsDto(incoming = listOf(IncomingRequestDto("fr_80", testAccount3, 1_000)))
        api.accepted = { FriendDto("u_3", "test3", "Test Account 3", online = true) }
        val repository = repository()
        repository.refresh()

        repository.accept("fr_80").getOrThrow()

        assertEquals(listOf("Test Account 3", "Zed"), repository.friends.value.map { it.displayName })
        assertTrue(repository.requests.value.isEmpty())
        assertTrue("accept:fr_80" in api.calls)
    }

    @Test
    fun decline_removesTheRequest() = runTest {
        api.requests = FriendRequestsDto(incoming = listOf(IncomingRequestDto("fr_80", testAccount3, 1_000)))
        val repository = repository()
        repository.refresh()

        repository.decline("fr_80").getOrThrow()

        assertTrue(repository.requests.value.isEmpty())
        assertTrue(repository.friends.value.isEmpty())
    }

    @Test
    fun answer_toARequestAlreadyGone_removesItAndFails() = runTest {
        api.requests = FriendRequestsDto(incoming = listOf(IncomingRequestDto("fr_80", testAccount3, 1_000)))
        val repository = repository()
        repository.refresh()
        api.failure = httpError(404, "NOT_FOUND")

        val result = repository.accept("fr_80")

        assertEquals(AccountError.NOT_FOUND, (result.exceptionOrNull() as AccountException).error)
        assertTrue(repository.requests.value.isEmpty())
    }

    @Test
    fun remove_takesTheFriendOffTheList() = runTest {
        api.friends = listOf(testAccount2)
        val repository = repository()
        repository.refresh()

        repository.remove("u_2").getOrThrow()

        assertTrue(repository.friends.value.isEmpty())
        assertTrue("remove:u_2" in api.calls)
    }

    @Test
    fun cancel_withdrawsTheSentRequestAndItLeavesTheList() = runTest {
        val repository =
            withSent(OutgoingRequestDto("fr_77", testAccount4, 900), OutgoingRequestDto("fr_76", testAccount3, 800))

        repository.cancel("fr_77").getOrThrow()

        assertEquals("withdraw:fr_77", api.calls.last())
        assertEquals(listOf("fr_76"), repository.sentRequests.value.map { it.id })
    }

    @Test
    fun cancel_aRequestAlreadyAnswered_leavesTheListAndFails() = runTest {
        val repository = withSent(OutgoingRequestDto("fr_77", testAccount4, 900))
        api.failure = httpError(404, "NOT_FOUND")

        val result = repository.cancel("fr_77")

        assertEquals(AccountError.NOT_FOUND, (result.exceptionOrNull() as AccountException).error)
        assertTrue(repository.sentRequests.value.isEmpty())
    }

    @Test
    fun cancel_failingForAnotherReason_keepsTheRequest() = runTest {
        val repository = withSent(OutgoingRequestDto("fr_77", testAccount4, 900))
        api.failure = IOException("offline")

        assertEquals(AccountError.OFFLINE, (repository.cancel("fr_77").exceptionOrNull() as AccountException).error)
        assertEquals(listOf("fr_77"), repository.sentRequests.value.map { it.id })
    }

    @Test
    fun anotherUserSigningIn_emptiesTheLists() = runTest {
        api.friends = listOf(testAccount2)
        val repository = withSent(OutgoingRequestDto("fr_77", testAccount4, 900))

        auth.value = AuthState.SignedIn(accountDto(id = "u_other").toAccount())
        runCurrent()

        assertTrue(repository.friends.value.isEmpty())
        assertTrue(repository.sentRequests.value.isEmpty())
    }
}
