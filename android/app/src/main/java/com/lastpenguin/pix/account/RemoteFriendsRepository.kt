// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
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
 * accepted friend or a declined request at once, before the next refresh. The lists empty whenever the signed-in user
 * changes, so one account never shows another's friends. Owner: Accounts (#54).
 */
class RemoteFriendsRepository(private val api: PixAccountApi, auth: StateFlow<AuthState>, scope: CoroutineScope) :
    FriendsRepository {

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    override val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _requests = MutableStateFlow<List<FriendRequest>>(emptyList())
    override val requests: StateFlow<List<FriendRequest>> = _requests.asStateFlow()

    init {
        scope.launch {
            auth.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged().collect {
                _friends.value = emptyList()
                _requests.value = emptyList()
            }
        }
    }

    override suspend fun refresh(): Result<Unit> = accountCall {
        coroutineScope {
            val friends = async { api.friends() }
            val requests = async { api.friendRequests() }
            _friends.value = friends.await().friends.map { it.toFriend() }
            _requests.value = requests.await().incoming.map { it.toFriendRequest() }
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
            RequestOutcome.Sent(sent.id ?: throw AccountException(AccountError.UNKNOWN, "No request id"))
        }
    }

    override suspend fun accept(requestId: String): Result<Friend> = accountCall {
        val friend = answer(requestId) { api.acceptFriendRequest(requestId).friend.toFriend() }
        add(friend)
        friend
    }

    override suspend fun decline(requestId: String): Result<Unit> = accountCall {
        answer(requestId) { api.declineFriendRequest(requestId) }
    }

    override suspend fun cancel(requestId: String): Result<Unit> = accountCall {
        api.withdrawFriendRequest(requestId)
    }

    override suspend fun remove(userId: String): Result<Unit> = accountCall {
        api.removeFriend(userId)
        _friends.update { friends -> friends.filterNot { it.userId == userId } }
    }

    /** Answers a received request. It leaves the list either way: a `404` means it was withdrawn or answered already. */
    private suspend fun <T> answer(requestId: String, call: suspend () -> T): T {
        val result = try {
            call()
        } catch (error: HttpException) {
            if (error.code() == NOT_FOUND) removeRequest(requestId)
            throw error
        }
        removeRequest(requestId)
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
