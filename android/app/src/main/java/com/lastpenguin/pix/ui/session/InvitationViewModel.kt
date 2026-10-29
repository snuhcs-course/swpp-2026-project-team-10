// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.InvitationManager
import com.lastpenguin.pix.account.InvitationStatus
import com.lastpenguin.pix.account.OpenedInvitation
import com.lastpenguin.pix.session.SessionPlace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the friend's *Invitation* screen shows (R&S 6.6, FR-6.4). */
sealed interface InvitationUiState {
    data object Loading : InvitationUiState

    /** [from] invites; *Accept* and *Decline*, disabled while [answering]. */
    data class Open(val from: String, val answering: Boolean = false) : InvitationUiState

    /** The invitation is open, but this phone must sign in with the invited account first. */
    data class SignedOut(val from: String) : InvitationUiState

    /** "This invitation is no longer available": answered, cancelled, expired, unknown, or for another account. */
    data object Gone : InvitationUiState

    /** The server could not be reached; *Try again*. */
    data object Failed : InvitationUiState
}

sealed interface InvitationEvent {
    /** The friend's place, which the session takes next. */
    data class Accepted(val invitationId: String, val place: SessionPlace) : InvitationEvent

    data object Declined : InvitationEvent

    /** An answer that failed for another reason than the invitation being gone, such as no connection. */
    data class AnswerFailed(val error: AccountError) : InvitationEvent
}

/**
 * The *Invitation* screen, opened from a push or a link (Design 2.6.6). An invitation still open is offered for an
 * answer only to a signed-in phone, since accepting needs the account it was sent to. Owner: Real-time (#119).
 */
class InvitationViewModel(private val invitations: InvitationManager, private val auth: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<InvitationUiState>(InvitationUiState.Loading)
    val uiState: StateFlow<InvitationUiState> = _uiState.asStateFlow()

    private val _events = Channel<InvitationEvent>(Channel.BUFFERED)
    val events: Flow<InvitationEvent> = _events.receiveAsFlow()

    private var invitationId: String? = null
    private var opened: OpenedInvitation? = null

    init {
        // A sign-in made from this screen (through Friends) offers the answer when the screen comes back.
        viewModelScope.launch { auth.state.collect { show() } }
    }

    /** Loads [id] once; the same id again, as after a rotation, keeps what was loaded. */
    fun open(id: String) {
        if (id == invitationId) return
        invitationId = id
        load()
    }

    fun retry() = load()

    /** The session could not take the place: the photographer has left since. */
    fun gone() {
        _uiState.value = InvitationUiState.Gone
    }

    fun accept() {
        val id = invitationId ?: return
        val current = _uiState.value as? InvitationUiState.Open ?: return
        if (current.answering) return
        _uiState.value = current.copy(answering = true)
        viewModelScope.launch {
            invitations.accept(id)
                .onSuccess { place -> _events.send(InvitationEvent.Accepted(id, place)) }
                .onFailure { error -> answerFailed(current, error) }
        }
    }

    /** The photographer sees *Invite not accepted* (FR-6.3). A decline that fails still closes: it expires anyway. */
    fun decline() {
        val id = invitationId ?: return
        val current = _uiState.value as? InvitationUiState.Open ?: return
        if (current.answering) return
        _uiState.value = current.copy(answering = true)
        viewModelScope.launch {
            invitations.decline(id)
            _events.send(InvitationEvent.Declined)
        }
    }

    private fun load() {
        val id = invitationId ?: return
        opened = null
        _uiState.value = InvitationUiState.Loading
        viewModelScope.launch {
            invitations.open(id)
                .onSuccess {
                    opened = it
                    show()
                }
                .onFailure { error ->
                    val notFound = (error as? AccountException)?.error == AccountError.NOT_FOUND
                    _uiState.value = if (notFound) InvitationUiState.Gone else InvitationUiState.Failed
                }
        }
    }

    private fun show() {
        val invitation = opened ?: return
        if (_uiState.value is InvitationUiState.Gone) return
        val from = invitation.from.displayName
        _uiState.value = when {
            invitation.status != InvitationStatus.PENDING -> InvitationUiState.Gone

            auth.state.value !is AuthState.SignedIn -> InvitationUiState.SignedOut(from)

            else -> (_uiState.value as? InvitationUiState.Open)?.takeIf { it.from == from }
                ?: InvitationUiState.Open(from)
        }
    }

    private suspend fun answerFailed(current: InvitationUiState.Open, error: Throwable) {
        when (val reason = (error as? AccountException)?.error ?: AccountError.UNKNOWN) {
            // Answered, cancelled, or expired since it was opened; NOT_FOUND is an invitation for another account.
            AccountError.INVITATION_GONE, AccountError.NOT_FOUND -> _uiState.value = InvitationUiState.Gone

            else -> {
                _uiState.value = current.copy(answering = false)
                _events.send(InvitationEvent.AnswerFailed(reason))
            }
        }
    }
}
