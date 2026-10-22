// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Signs the account client's requests (Design 2.5.3, *Session tokens*). As an interceptor it adds a fresh access
 * token; as an authenticator it answers `401 UNAUTHENTICATED` with one refresh and one retry, so a token that expired
 * on the way, or one the server no longer accepts, costs the caller nothing. Any other `401`, such as a wrong password
 * on `PUT /me`, and a second `401` reach the caller. OkHttp runs both on its own threads, which may block here; the
 * refresh goes through another client, so it never waits behind the calls that wait for it. Owner: Accounts (#54).
 */
class BearerTokens(private val tokens: AccessTokenSource) :
    Interceptor,
    Authenticator {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokens.freshAccessToken() }
        val request = chain.request()
        return chain.proceed(if (token == null) request else request.withBearer(token))
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val rejected = response.request.header(AUTHORIZATION)?.removePrefix(BEARER) ?: return null
        if (errorCode(response) != UNAUTHENTICATED) return null
        val fresh = runBlocking { tokens.refreshAfterRejection(rejected) } ?: return null
        return response.request.withBearer(fresh)
    }

    private fun errorCode(response: Response): String? {
        val body = response.peekBody(ERROR_BODY_BYTES).string()
        return parseApiError(body)?.first
    }

    private fun Request.withBearer(token: String): Request = newBuilder().header(AUTHORIZATION, BEARER + token).build()

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
        const val UNAUTHENTICATED = "UNAUTHENTICATED"
        const val ERROR_BODY_BYTES = 4096L
    }
}
