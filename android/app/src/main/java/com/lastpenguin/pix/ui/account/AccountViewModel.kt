// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.GoogleIdTokens
import com.lastpenguin.pix.account.GoogleSignIn
import com.lastpenguin.pix.account.GoogleSignInException
import com.lastpenguin.pix.account.GoogleSignInProblem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    /** A call is running: the screen's buttons wait. */
    val busy: Boolean = false,
    /** The last call's failure, an [AccountException] or a [GoogleSignInException], until the next call. */
    val error: Exception? = null,
    /** A Google account no Pix account uses waits to be added once the person signs in (FR-5.13). */
    val linkingGoogle: Boolean = false,
    /** The last *Check* of a Pix ID; a form shows it while its field still holds that Pix ID (FR-5.15). */
    val pixIdCheck: PixIdCheck? = null,
)

/** *Check* of [pixId]: asked while both [available] and [error] are null, then answered with one of them. */
data class PixIdCheck(val pixId: String, val available: Boolean? = null, val error: AccountException? = null) {
    val checking: Boolean get() = available == null && error == null
}

/** One-shot results for navigation and short messages. */
sealed interface AccountEvent {
    /** To Friends, or first to Pix ID setup for an account without one. */
    data class SignedIn(val needsPixId: Boolean) : AccountEvent

    /** Ask "Do you already have a Pix account?" (FR-5.13). */
    data object AskExistingAccount : AccountEvent

    /** Signed in, but the waiting Google account could not be added; the sign-in stands. */
    data class GoogleNotAdded(val error: AccountException) : AccountEvent

    data object PixIdSaved : AccountEvent

    data object NameSaved : AccountEvent

    data object GoogleAdded : AccountEvent

    data object PasswordAdded : AccountEvent

    data object SignedOut : AccountEvent
}

/**
 * Sign in, Sign up, Pix ID setup, and the Account sheet (R&S 6.4, 6.8; FR-5.2, FR-5.3, FR-5.11–5.14). Shared by those
 * screens (activity scope), because the Google account of "Do you already have a Pix account?" waits here while the
 * person signs in. One call at a time. Owner: Accounts (#54).
 */
class AccountViewModel(private val auth: AuthRepository, private val google: GoogleIdTokens) : ViewModel() {

    val authState: StateFlow<AuthState> = auth.state

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    /** The ID token of a Google account no Pix account uses, while the person decides or signs in (FR-5.13). */
    private var pendingGoogleToken: String? = null

    private var pixIdCheckJob: Job? = null

    fun logIn(pixId: String, password: String) = call {
        val account = auth.logIn(pixId, password).getOrThrow()
        val token = pendingGoogleToken
        if (token != null) {
            pendingGoogleToken = null
            _uiState.update { it.copy(linkingGoogle = false) }
            val failure = auth.addGoogle(token).exceptionOrNull()
            if (failure is AccountException) _events.emit(AccountEvent.GoogleNotAdded(failure))
        }
        _events.emit(AccountEvent.SignedIn(needsPixId = account.pixId == null))
    }

    /** [context] is the Activity that Google's account chooser opens over. */
    fun continueWithGoogle(context: Context) = call {
        val token = googleToken(context) ?: return@call
        when (val result = auth.signInWithGoogle(token).getOrThrow()) {
            is GoogleSignIn.SignedIn -> _events.emit(AccountEvent.SignedIn(needsPixId = result.account.pixId == null))

            GoogleSignIn.NotRegistered -> {
                pendingGoogleToken = token
                _events.emit(AccountEvent.AskExistingAccount)
            }
        }
    }

    /** The answer to "Do you already have a Pix account?": sign in to add Google to it, or make a new account. */
    fun answerExistingAccount(hasAccount: Boolean) {
        val token = pendingGoogleToken ?: return
        if (hasAccount) {
            _uiState.update { it.copy(linkingGoogle = true, error = null) }
            return
        }
        call {
            pendingGoogleToken = null
            val account = auth.signUpWithGoogle(token).getOrThrow()
            _events.emit(AccountEvent.SignedIn(needsPixId = account.pixId == null))
        }
    }

    /** The person left Sign in, or closed the question, without adding the Google account. */
    fun forgetGoogle() {
        pendingGoogleToken = null
        _uiState.update { it.copy(linkingGoogle = false) }
    }

    fun signUp(pixId: String, displayName: String, password: String) = call {
        auth.signUp(pixId, password, displayName).getOrThrow()
        _events.emit(AccountEvent.SignedIn(needsPixId = false))
    }

    /**
     * *Check* on Sign up and the Pix ID setup (FR-5.15): whether [pixId] is free now. It runs beside the screen's own
     * call, and a newer check replaces one still waiting.
     */
    fun checkPixId(pixId: String) {
        pixIdCheckJob?.cancel()
        _uiState.update { it.copy(pixIdCheck = PixIdCheck(pixId)) }
        pixIdCheckJob = viewModelScope.launch {
            val check = auth.isPixIdAvailable(pixId).fold(
                onSuccess = { PixIdCheck(pixId, available = it) },
                onFailure = {
                    PixIdCheck(
                        pixId,
                        error =
                            it as? AccountException ?: AccountException(AccountError.UNKNOWN),
                    )
                },
            )
            _uiState.update { it.copy(pixIdCheck = check) }
        }
    }

    fun savePixId(pixId: String) = call {
        auth.setPixId(pixId).getOrThrow()
        _events.emit(AccountEvent.PixIdSaved)
    }

    fun saveDisplayName(displayName: String) = call {
        auth.setDisplayName(displayName).getOrThrow()
        _events.emit(AccountEvent.NameSaved)
    }

    fun addGoogle(context: Context) = call {
        val token = googleToken(context) ?: return@call
        auth.addGoogle(token).getOrThrow()
        _events.emit(AccountEvent.GoogleAdded)
    }

    fun addPassword(password: String) = call {
        auth.addPassword(password).getOrThrow()
        _events.emit(AccountEvent.PasswordAdded)
    }

    fun signOut() = call {
        auth.signOut()
        _events.emit(AccountEvent.SignedOut)
    }

    /** A screen opens without the failure another one showed. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Sign up and the Pix ID setup open without the failure, or the *Check* answer, another screen showed. */
    fun startForm() {
        pixIdCheckJob?.cancel()
        _uiState.update { it.copy(error = null, pixIdCheck = null) }
    }

    /** Google's ID token, or null when the person closed Google's sheet, which needs no message. */
    private suspend fun googleToken(context: Context): String? = google.request(context).getOrElse { error ->
        if (error is GoogleSignInException && error.problem == GoogleSignInProblem.CANCELLED) return null
        throw error
    }

    private fun call(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: AccountException) {
                _uiState.update { it.copy(error = error) }
            } catch (error: GoogleSignInException) {
                _uiState.update { it.copy(error = error) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
