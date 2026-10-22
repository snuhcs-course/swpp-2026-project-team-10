// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Signing up, in, and out, and the account's own settings (Design 2.1, FR-5.1–5.3, FR-5.11–5.14, AD-12). A failure is
 * an [AccountException]. The sign-in, with its tokens, is kept on the phone, so the next start is signed in.
 * [RemoteAuthRepository] is the real one. Owner: Accounts (#54).
 */
interface AuthRepository {
    val state: StateFlow<AuthState>

    /** One event each time the server ended this phone's sign-in, so a screen can ask to sign in again. */
    val expired: SharedFlow<Unit>

    /** Fails with PIX_ID_TAKEN, PIX_ID_INVALID, or PASSWORD_INVALID. */
    suspend fun signUp(pixId: String, password: String, displayName: String): Result<Account>

    /**
     * Whether a sign-up or the Pix ID setup could take [pixId] now (FR-5.15), without signing in; fails with
     * PIX_ID_INVALID. Someone may take it before the form is sent, so those still fail with PIX_ID_TAKEN then.
     */
    suspend fun isPixIdAvailable(pixId: String): Result<Boolean>

    /** Fails with INVALID_CREDENTIALS. */
    suspend fun logIn(pixId: String, password: String): Result<Account>

    /** NotRegistered asks whether the person already has a Pix account (FR-5.13). */
    suspend fun signInWithGoogle(idToken: String): Result<GoogleSignIn>

    /** A new account without a Pix ID; the Pix ID setup comes next. */
    suspend fun signUpWithGoogle(idToken: String): Result<Account>

    /** Fails with GOOGLE_IN_USE or GOOGLE_ALREADY_ADDED. */
    suspend fun addGoogle(idToken: String): Result<Account>

    /** For an account made with Google that has a Pix ID; fails with PASSWORD_INVALID. */
    suspend fun addPassword(password: String): Result<Account>

    /** Fails with PIX_ID_TAKEN or PIX_ID_INVALID; a password account changing its Pix ID sends its [password]. */
    suspend fun setPixId(pixId: String, password: String? = null): Result<Account>

    /** 1–30 characters after trimming. */
    suspend fun setDisplayName(displayName: String): Result<Account>

    /**
     * An access token that is not about to expire, refreshed first if needed, or null when signed out or offline.
     * For the signaling socket, which sends it only when it opens (Design 2.5.2).
     */
    suspend fun freshAccessToken(): String?

    /** Signs this phone out on the server, if it answers, and always on the phone. */
    suspend fun signOut()
}
