// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.FriendRequestBody
import com.lastpenguin.pix.core.network.LookupBody
import com.lastpenguin.pix.core.network.PixAccountApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * [FriendsRepository] on the Pix server (Design 2.5.3). Each answer also updates the lists, so a screen shows an
 * accepted friend, a declined request, or a sent or withdrawn one at once, before the next refresh. The lists empty whenever the signed-in user
 * changes, so one account never shows another's friends. Owner: Accounts (#54).
 */
class RemoteFriendsRepository(private val api: PixAccountApi, auth: StateFlow<AuthState>, scope: CoroutineScope) :
    FriendsRepository {

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    override val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _requests = MutableStateFlow<List<FriendRequest>>(emptyList())
    override val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<SentRequest>>(emptyList())
    override val sentRequests: StateFlow<List<SentRequest>> = _sentRequests.asStateFlow()

    init {
        scope.launch {
            auth.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged().collect {
                _friends.value = emptyList()
                _requests.value = emptyList()
                _sentRequests.value = emptyList()
            }
        }
    }

    override suspend fun refresh(): Result<Unit> = accountCall {
        coroutineScope {
            val friends = async { api.friends() }
            val requests = async { api.friendRequests() }
            _friends.value = friends.await().friends.map { it.toFriend() }
            val pending = requests.await()
            _requests.value = pending.incoming.map { it.toFriendRequest() }
            _sentRequests.value = pending.outgoing.map { it.toSentRequest() }
        }
    }

    override suspend fun search(pixId: String): Result<UserLookup> = accountCall {
        try {
            api.lookUp(LookupBody(normalizePixId(pixId))).toLookup()
        } catch (error: HttpException) {
            if (error.code() == NOT_FOUND) UserLookup.NotFound else throw error
        }
    }

    override suspend fun request(userId: String): Result<RequestOutcome> = accountCall {
        val sent = api.sendFriendRequest(FriendRequestBody(userId))
        val friend = sent.friend?.toFriend()
        if (friend != null) {
            // The other user had already asked: their request is accepted, so it leaves the list too.
            _requests.update { requests -> requests.filterNot { it.from.userId == userId } }
            add(friend)
            RequestOutcome.BecameFriends(friend)
        } else {
            val requestId = sent.id ?: throw AccountException(AccountError.UNKNOWN, "No request id")
            // Newest first, as the server lists them; without the user, the next refresh brings it.
            sent.to?.let { to ->
                val request = SentRequest(requestId, to.toPixUser(), sent.createdAt ?: 0)
                _sentRequests.update { requests -> listOf(request) + requests.filterNot { it.id == requestId } }
            }
            RequestOutcome.Sent(requestId)
        }
    }

    override suspend fun accept(requestId: String): Result<Friend> = accountCall {
        val friend = settle({ removeRequest(requestId) }) { api.acceptFriendRequest(requestId).friend.toFriend() }
        add(friend)
        friend
    }

    override suspend fun decline(requestId: String): Result<Unit> = accountCall {
        settle({ removeRequest(requestId) }) { api.declineFriendRequest(requestId) }
    }

    override suspend fun cancel(requestId: String): Result<Unit> = accountCall {
        settle({ _sentRequests.update { requests -> requests.filterNot { it.id == requestId } } }) {
            api.withdrawFriendRequest(requestId)
        }
    }

    override suspend fun remove(userId: String): Result<Unit> = accountCall {
        api.removeFriend(userId)
        _friends.update { friends -> friends.filterNot { it.userId == userId } }
    }

    /**
     * Answers or withdraws a request, which then [leaves] its list. It leaves on a `404` too, which means the request
     * was answered or withdrawn already.
     */
    private suspend fun <T> settle(leaves: () -> Unit, call: suspend () -> T): T {
        val result = try {
            call()
        } catch (error: HttpException) {
            if (error.code() == NOT_FOUND) leaves()
            throw error
        }
        leaves()
        return result
    }

    private fun removeRequest(requestId: String) {
        _requests.update { requests -> requests.filterNot { it.id == requestId } }
    }

    private fun add(friend: Friend) {
        _friends.update { friends ->
            (friends.filterNot { it.userId == friend.userId } + friend).sortedBy { it.displayName.lowercase() }
        }
    }

    private companion object {
        const val NOT_FOUND = 404
    }
}
