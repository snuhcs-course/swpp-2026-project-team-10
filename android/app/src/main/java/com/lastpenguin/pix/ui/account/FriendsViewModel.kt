// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.Friend
import com.lastpenguin.pix.account.FriendRequest
import com.lastpenguin.pix.account.FriendsRepository
import com.lastpenguin.pix.account.Relation
import com.lastpenguin.pix.account.RequestOutcome
import com.lastpenguin.pix.account.SentRequest
import com.lastpenguin.pix.account.UserLookup
import com.lastpenguin.pix.account.normalizePixId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val auth: AuthState = AuthState.Loading,
    val friends: List<Friend> = emptyList(),
    /** Received and pending (FR-5.7). */
    val requests: List<FriendRequest> = emptyList(),
    /** Sent and not answered yet (FR-5.16). */
    val sent: List<SentRequest> = emptyList(),
    /** The last refresh failed, so the lists may be out of date. */
    val offline: Boolean = false,
    /** Requests being answered or withdrawn; their buttons wait. */
    val answering: Set<String> = emptySet(),
)

enum class SearchPhase { IDLE, SEARCHING, FOUND, NOT_FOUND, FAILED }

data class SearchUiState(
    val phase: SearchPhase = SearchPhase.IDLE,
    /** The Pix ID that was searched for. */
    val query: String = "",
    /** FOUND: the user and the button their relation gives (FR-5.8). */
    val result: UserLookup.Found? = null,
    /** FAILED: why. */
    val error: AccountError? = null,
    /** *Add*, *Accept*, or *Withdraw request* is running. */
    val busy: Boolean = false,
)

sealed interface FriendsEvent {
    /** The server ended the sign-in (FR-5.2): ask to sign in again. */
    data object SignedOutByServer : FriendsEvent

    data class Failed(val error: AccountError) : FriendsEvent
}

/**
 * Friends, Add a friend, and Sent requests (R&S 6.4; FR-5.4–5.10, FR-5.16, US-7, US-8), shared by all three (activity
 * scope). Without push, which comes with invitations (Iter2-P15), Friends and Sent requests keep the lists fresh with
 * [keepRefreshing] while they are shown. Owner: Accounts (#54).
 */
class FriendsViewModel(private val auth: AuthRepository, private val repository: FriendsRepository) : ViewModel() {

    private val offline = MutableStateFlow(false)
    private val answering = MutableStateFlow(emptySet<String>())

    val uiState: StateFlow<FriendsUiState> = combine(
        auth.state,
        repository.friends,
        combine(repository.requests, repository.sentRequests, ::Pair),
        offline,
        answering,
    ) { auth, friends, (requests, sent), offline, answering ->
        FriendsUiState(auth, friends, requests, sent, offline, answering)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FriendsUiState(auth.state.value))

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _events = MutableSharedFlow<FriendsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FriendsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch { auth.expired.collect { _events.emit(FriendsEvent.SignedOutByServer) } }
    }

    /**
     * Refreshes the lists every [REFRESH_INTERVAL_MS] while the user is signed in with a Pix ID, the only users who
     * have friends. Runs until cancelled; Friends and Sent requests run it while started.
     */
    suspend fun keepRefreshing() {
        auth.state
            .map { (it as? AuthState.SignedIn)?.account?.takeIf { account -> account.pixId != null }?.userId }
            .distinctUntilChanged()
            .collectLatest { userId ->
                offline.value = false
                if (userId == null) return@collectLatest
                while (true) {
                    offline.value = repository.refresh().isFailure
                    delay(REFRESH_INTERVAL_MS)
                }
            }
    }

    fun accept(request: FriendRequest) = answer(request.id) { repository.accept(request.id) }

    fun decline(request: FriendRequest) = answer(request.id) { repository.decline(request.id) }

    /** *Withdraw* on Sent requests (FR-5.16); the request leaves the list. */
    fun withdraw(request: SentRequest) = answer(request.id) { repository.cancel(request.id) }

    fun remove(friend: Friend) {
        viewModelScope.launch { repository.remove(friend.userId).exceptionOrNull()?.let { fail(it) } }
    }

    /** Exact Pix ID only (FR-5.5); capitals and spaces around it are ignored. */
    fun search(text: String) {
        val pixId = normalizePixId(text)
        if (pixId.isEmpty() || _search.value.phase == SearchPhase.SEARCHING) return
        _search.value = SearchUiState(SearchPhase.SEARCHING, pixId)
        viewModelScope.launch {
            _search.value = repository.search(pixId).fold(
                onSuccess = { lookup ->
                    when (lookup) {
                        is UserLookup.Found -> SearchUiState(SearchPhase.FOUND, pixId, result = lookup)
                        UserLookup.NotFound -> SearchUiState(SearchPhase.NOT_FOUND, pixId)
                    }
                },
                onFailure = { SearchUiState(SearchPhase.FAILED, pixId, error = it.accountError()) },
            )
        }
    }

    /** *Add* on the result: "Requested", or "Friends" when they had already asked (FR-5.6). */
    fun addFound() = onFound(Relation.NONE) { found ->
        when (val outcome = repository.request(found.user.userId).getOrThrow()) {
            is RequestOutcome.Sent -> found.copy(relation = Relation.REQUESTED, requestId = outcome.requestId)
            is RequestOutcome.BecameFriends -> found.copy(relation = Relation.FRIEND, requestId = null)
        }
    }

    /** *Accept* on a result who already sent a request. */
    fun acceptFound() = onFound(Relation.INCOMING) { found ->
        repository.accept(checkNotNull(found.requestId)).getOrThrow()
        found.copy(relation = Relation.FRIEND, requestId = null)
    }

    /** *Withdraw request* on a result that was sent a request. */
    fun withdrawFound() = onFound(Relation.REQUESTED) { found ->
        repository.cancel(checkNotNull(found.requestId)).getOrThrow()
        found.copy(relation = Relation.NONE, requestId = null)
    }

    /** Add a friend opens empty. */
    fun clearSearch() {
        _search.value = SearchUiState()
    }

    private fun onFound(relation: Relation, change: suspend (UserLookup.Found) -> UserLookup.Found) {
        val state = _search.value
        val found = state.result ?: return
        if (state.busy || found.relation != relation) return
        if (relation != Relation.NONE && found.requestId == null) return
        _search.update { it.copy(busy = true) }
        viewModelScope.launch {
            val changed = try {
                change(found)
            } catch (error: AccountException) {
                fail(error)
                null
            }
            _search.update { it.copy(busy = false, result = changed ?: it.result) }
        }
    }

    private fun answer(requestId: String, call: suspend () -> Result<*>) {
        if (requestId in answering.value) return
        answering.update { it + requestId }
        viewModelScope.launch {
            call().exceptionOrNull()?.let { fail(it) }
            answering.update { it - requestId }
        }
    }

    private suspend fun fail(error: Throwable) {
        _events.emit(FriendsEvent.Failed(error.accountError()))
    }

    private fun Throwable.accountError(): AccountError = (this as? AccountException)?.error ?: AccountError.UNKNOWN

    companion object {
        /** How often the Friends screen asks for the lists, for online status and new requests. */
        const val REFRESH_INTERVAL_MS = 10_000L
    }
}
