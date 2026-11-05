// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.InvitationManager
import com.lastpenguin.pix.account.OpenedInvitation
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.session.SessionPlace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The received invitations that *See their camera* lists and the *I'll be in the photo* tile counts (R&S 6.7,
 * FR-6.17): pending, not expired by this phone's clock at [now], newest first.
 */
data class IncomingInvitationsUiState(
    val invitations: List<OpenedInvitation> = emptyList(),
    /** The invitation whose answer is on its way; every row waits for it. */
    val answering: String? = null,
    val now: Long = 0,
)

sealed interface IncomingInvitationEvent {
    /** This phone's place, which the session takes next, and the friend who invited ([from]). */
    data class Accepted(val invitationId: String, val place: SessionPlace, val from: PixUser) : IncomingInvitationEvent

    data class Declined(val invitationId: String) : IncomingInvitationEvent

    /** Answered, cancelled, or expired since the list was read; its row is gone. */
    data class Gone(val invitationId: String) : IncomingInvitationEvent

    /** An accept that failed for another reason than the invitation being gone, such as no connection. */
    data class AnswerFailed(val error: AccountError) : IncomingInvitationEvent
}

/**
 * Received invitations, so one whose notification was dismissed can still be answered within its minute (FR-6.17;
 * #188). Shared by Who takes the photo? and See their camera (activity scope), which keep the list fresh with
 * [keepRefreshing] while they are shown. The time left counts down on this phone's clock, as the notification's does,
 * and a row leaves the list when it runs out. Answering is the Invitation screen's: an accept gives the place the
 * session takes ([InvitationJoin]), and a decline closes the row even when its call fails, since it expires anyway.
 * Owner: Real-time (#188).
 */
class IncomingInvitationsViewModel(
    private val invitations: InvitationManager,
    private val auth: AuthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val listed = MutableStateFlow(emptyList<OpenedInvitation>())

    // Answered here: kept out of the list, also from a read that started before the answer.
    private val answered = MutableStateFlow(emptySet<String>())
    private val answering = MutableStateFlow<String?>(null)
    private val now = MutableStateFlow(clock())

    /** The user the list was read for, so a sign-in to another account starts with none. */
    private var listedFor: String? = null

    val uiState: StateFlow<IncomingInvitationsUiState> =
        combine(listed, answered, answering, now) { listed, answered, answering, now ->
            IncomingInvitationsUiState(
                listed.filter { it.id !in answered && it.expiresAt > now },
                answering,
                now,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, IncomingInvitationsUiState(now = now.value))

    private val _events = Channel<IncomingInvitationEvent>(Channel.BUFFERED)
    val events: Flow<IncomingInvitationEvent> = _events.receiveAsFlow()

    /**
     * The session took the place an *Accept* here gave, so its state decides what follows. Kept here, not in the
     * screen, so a screen recreated while the phones connect still follows the session.
     */
    var joining = false

    /**
     * While someone is signed in, reads the list every [REFRESH_INTERVAL_MS], and moves the clock on every
     * [TICK_MS] for the time left. A failed read, as from a server older than the list, keeps what the phone has.
     * Runs until cancelled; the screens run it while started.
     */
    suspend fun keepRefreshing() = coroutineScope {
        launch {
            while (true) {
                now.value = clock()
                delay(TICK_MS)
            }
        }
        auth.state
            .map { (it as? AuthState.SignedIn)?.account?.userId }
            .distinctUntilChanged()
            .collectLatest { userId ->
                if (userId != listedFor) {
                    listedFor = userId
                    listed.value = emptyList()
                }
                if (userId == null) return@collectLatest
                while (true) {
                    invitations.incoming().onSuccess { listed.value = it }
                    delay(REFRESH_INTERVAL_MS)
                }
            }
    }

    fun accept(invitation: OpenedInvitation) = answer(invitation) {
        invitations.accept(invitation.id)
            .onSuccess { place ->
                answered.update { it + invitation.id }
                _events.send(IncomingInvitationEvent.Accepted(invitation.id, place, invitation.from))
            }
            .onFailure { error ->
                when (val reason = (error as? AccountException)?.error ?: AccountError.UNKNOWN) {
                    // NOT_FOUND is an invitation for another account, as on Invitation.
                    AccountError.INVITATION_GONE, AccountError.NOT_FOUND -> gone(invitation.id)

                    else -> _events.send(IncomingInvitationEvent.AnswerFailed(reason))
                }
            }
    }

    /** The photographer sees *Invite not accepted* (FR-6.3). */
    fun decline(invitation: OpenedInvitation) = answer(invitation) {
        invitations.decline(invitation.id)
        answered.update { it + invitation.id }
        _events.send(IncomingInvitationEvent.Declined(invitation.id))
    }

    private suspend fun gone(invitationId: String) {
        answered.update { it + invitationId }
        _events.send(IncomingInvitationEvent.Gone(invitationId))
    }

    private fun answer(invitation: OpenedInvitation, call: suspend () -> Unit) {
        if (answering.value != null) return
        answering.value = invitation.id
        viewModelScope.launch {
            try {
                call()
            } finally {
                answering.value = null
            }
        }
    }

    companion object {
        /** An invitation lasts a minute, so a few seconds is soon enough for a new one to show. */
        const val REFRESH_INTERVAL_MS = 5_000L
        const val TICK_MS = 1_000L
    }
}
