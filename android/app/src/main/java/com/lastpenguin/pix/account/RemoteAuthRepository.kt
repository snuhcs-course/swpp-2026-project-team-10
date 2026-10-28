// AI-generated with Claude Code, 2026-10-28, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.GoogleTokenBody
import com.lastpenguin.pix.core.network.LogInBody
import com.lastpenguin.pix.core.network.PasswordBody
import com.lastpenguin.pix.core.network.PixAccountApi
import com.lastpenguin.pix.core.network.PixAuthApi
import com.lastpenguin.pix.core.network.PixIdCheckBody
import com.lastpenguin.pix.core.network.SignUpBody
import com.lastpenguin.pix.core.network.UpdateMeBody
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException

/**
 * [AuthRepository] on the Pix server (Design 2.5.3). Sign-ins go through [authApi] and become the phone's sign-in in
 * [tokens]; changes to the account go through [accountApi], whose client adds the token. Owner: Accounts (#54).
 */
class RemoteAuthRepository(
    private val authApi: PixAuthApi,
    private val accountApi: PixAccountApi,
    private val tokens: AuthTokens,
) : AuthRepository {

    override val state: StateFlow<AuthState> = tokens.state
    override val expired: SharedFlow<Unit> = tokens.expired

    override suspend fun signUp(pixId: String, password: String, displayName: String) = accountCall {
        tokens.signIn(authApi.signUp(SignUpBody(pixId, password, displayName.trim())))
    }

    override suspend fun isPixIdAvailable(pixId: String) = accountCall {
        authApi.checkPixId(PixIdCheckBody(pixId)).available
    }

    override suspend fun logIn(pixId: String, password: String) = accountCall {
        tokens.signIn(authApi.logIn(LogInBody(normalizePixId(pixId), password)))
    }

    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignIn> = accountCall {
        try {
            GoogleSignIn.SignedIn(tokens.signIn(authApi.signInWithGoogle(GoogleTokenBody(idToken))))
        } catch (error: HttpException) {
            // 404 is NOT_REGISTERED here, the only 404 of this endpoint.
            if (error.code() == NOT_FOUND) GoogleSignIn.NotRegistered else throw error
        }
    }

    override suspend fun signUpWithGoogle(idToken: String) = accountCall {
        tokens.signIn(authApi.signUpWithGoogle(GoogleTokenBody(idToken)))
    }

    override suspend fun addGoogle(idToken: String) = accountCall {
        tokens.update(accountApi.addGoogle(GoogleTokenBody(idToken)))
    }

    override suspend fun addPassword(password: String) = accountCall {
        tokens.update(accountApi.addPassword(PasswordBody(password)))
    }

    override suspend fun setPixId(pixId: String, password: String?) = accountCall {
        tokens.update(accountApi.updateMe(UpdateMeBody(pixId = pixId, password = password)))
    }

    override suspend fun setDisplayName(displayName: String) = accountCall {
        tokens.update(accountApi.updateMe(UpdateMeBody(displayName = displayName.trim())))
    }

    override suspend fun refreshAccount() = accountCall { tokens.reload { accountApi.me() } }

    override suspend fun freshAccessToken(): String? = tokens.freshAccessToken()

    override suspend fun signOut() {
        // Without an answer the server's session lasts until its refresh token expires; the phone forgets it anyway.
        if (tokens.state.value is AuthState.SignedIn) accountCall { accountApi.signOut() }
        tokens.clear()
    }

    private companion object {
        const val NOT_FOUND = 404
    }
}
