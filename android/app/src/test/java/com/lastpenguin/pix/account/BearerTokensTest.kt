// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.TokensDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Partitions for the account client's tokens (Design 2.5.3, Session tokens), over a real HTTP server on the JVM. A
 * request: signed in (header added), signed out (sent without one). The answer: 200; 401 UNAUTHENTICATED (one refresh,
 * one retry with the new token); 401 with another code such as INVALID_CREDENTIALS (no retry); 401 again after the
 * retry (reaches the caller); a refresh that fails (the 401 reaches the caller). Several requests rejected at once
 * share one refresh (with the real AuthTokens).
 */
class BearerTokensTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun stop() {
        server.shutdown()
    }

    /** Tokens that a test sets; it counts refreshes. */
    private class Tokens(var current: String?, val next: String?) : AccessTokenSource {
        val refreshed = mutableListOf<String>()

        override suspend fun freshAccessToken(): String? = current

        override suspend fun refreshAfterRejection(rejected: String): String? {
            refreshed += rejected
            current = next
            return next
        }
    }

    private fun client(tokens: AccessTokenSource): OkHttpClient {
        val bearer = BearerTokens(tokens)
        return OkHttpClient.Builder().addInterceptor(bearer).authenticator(bearer).build()
    }

    private fun get(client: OkHttpClient): Int =
        client.newCall(Request.Builder().url(server.url("/api/v1/me")).build()).execute().use { it.code }

    private fun unauthorized(code: String) =
        MockResponse().setResponseCode(401).setBody("""{"error":{"code":"$code","message":"Sign in to use this"}}""")

    @Test
    fun request_signedIn_carriesTheAccessToken() {
        server.enqueue(MockResponse())

        assertEquals(200, get(client(Tokens("access-1", null))))

        assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun request_signedOut_goesWithoutAToken() {
        server.enqueue(MockResponse())

        get(client(Tokens(null, null)))

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun unauthenticated_refreshesOnceAndRetriesWithTheNewToken() {
        server.enqueue(unauthorized("UNAUTHENTICATED"))
        server.enqueue(MockResponse())
        val tokens = Tokens("access-1", "access-2")

        assertEquals(200, get(client(tokens)))

        assertEquals(listOf("access-1"), tokens.refreshed)
        assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun anotherUnauthorizedCode_reachesTheCallerWithoutARefresh() {
        server.enqueue(unauthorized("INVALID_CREDENTIALS"))
        val tokens = Tokens("access-1", "access-2")

        assertEquals(401, get(client(tokens)))

        assertEquals(emptyList<String>(), tokens.refreshed)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unauthenticatedAgainAfterTheRetry_reachesTheCaller() {
        server.enqueue(unauthorized("UNAUTHENTICATED"))
        server.enqueue(unauthorized("UNAUTHENTICATED"))
        val tokens = Tokens("access-1", "access-2")

        assertEquals(401, get(client(tokens)))

        assertEquals(1, tokens.refreshed.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aFailedRefresh_letsThe401ReachTheCaller() {
        server.enqueue(unauthorized("UNAUTHENTICATED"))

        assertEquals(401, get(client(Tokens("access-1", next = null))))

        assertEquals(1, server.requestCount)
    }

    @Test
    fun requestsRejectedAtOnce_shareOneRefresh() {
        // The old token is refused only once all three requests carry it, so all three are rejected together.
        val arrived = CountDownLatch(3)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") == "Bearer access-2") return MockResponse()
                arrived.countDown()
                arrived.await(5, TimeUnit.SECONDS)
                return unauthorized("UNAUTHENTICATED")
            }
        }
        val api = FakeAuthApi()
        val refreshes = AtomicInteger()
        api.refresh = {
            refreshes.incrementAndGet()
            TokensDto("access-2", "refresh-2", 900)
        }
        val store = FakeSignInStore(StoredSignIn(accountDto(), "access-1", "refresh-1", Long.MAX_VALUE))
        val tokens = runBlocking { AuthTokens(store, api, this) }
        val client = client(tokens)
        val pool = Executors.newFixedThreadPool(3)

        val results = List(3) { pool.submit<Int> { get(client) } }

        assertEquals(listOf(200, 200, 200), results.map { it.get(10, TimeUnit.SECONDS) })
        assertEquals(1, refreshes.get())
        assertEquals("refresh-2", store.stored?.refreshToken)
        pool.shutdown()
    }
}
