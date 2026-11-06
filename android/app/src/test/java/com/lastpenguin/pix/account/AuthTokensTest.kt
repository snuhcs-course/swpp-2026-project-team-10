// AI-generated with Claude Code, 2026-10-28, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.RefreshBody
import com.lastpenguin.pix.core.network.TokensDto
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for the phone's sign-in (Design 2.5.3, Session tokens). Start: nothing kept, a kept sign-in. Access token:
 * more than a minute left, less than a minute left (refreshed first), rejected by the server (refreshed), rejected
 * while another call already refreshed (no second refresh). Refresh answer: new tokens, 401 (signed out, expired
 * emitted, store cleared), no answer (tokens kept, null). Several callers at once: one refresh. Account update: the
 * same user, another user after signing in again, after signing out. Account read from the server (another phone signed
 * in to it may have changed it): changed (kept, tokens stay), the same (nothing written), overtaken by a change on this
 * phone, also one that left the account as it was (dropped), with a token refresh on the way (still kept), another
 * user, signed out on the way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthTokensTest {
    private val api = FakeAuthApi()
    private val store = FakeSignInStore()
    private var now = 1_000_000L

    private fun TestScope.tokens(): AuthTokens = AuthTokens(store, api, backgroundScope) { now }.also { runCurrent() }

    private fun stored(access: String = "access-0", expiresAt: Long = now + 900_000) =
        StoredSignIn(accountDto(), access, "refresh-0", expiresAt)

    private val refreshes get() = api.calls.filterIsInstance<RefreshBody>()

    @Test
    fun start_withNothingKept_isSignedOut() = runTest {
        assertEquals(AuthState.SignedOut, tokens().state.value)
    }

    @Test
    fun start_withAKeptSignIn_isSignedInWithoutAskingTheServer() = runTest {
        store.stored = stored()

        val tokens = tokens()

        assertEquals(AuthState.SignedIn(accountDto().toAccount()), tokens.state.value)
        assertEquals("access-0", tokens.freshAccessToken())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun signIn_keepsTheTokensWithTheirExpiry() = runTest {
        val tokens = tokens()

        val account = tokens.signIn(authDto(expiresIn = 900))

        assertEquals("test1", account.pixId)
        assertEquals(StoredSignIn(accountDto(), "access-1", "refresh-1", now + 900_000), store.stored)
        assertEquals(AuthState.SignedIn(account), tokens.state.value)
    }

    @Test
    fun freshAccessToken_lessThanAMinuteLeft_refreshesFirst() = runTest {
        store.stored = stored(expiresAt = now + AuthTokens.EARLY_REFRESH_MS - 1)
        val tokens = tokens()

        val token = tokens.freshAccessToken()

        assertEquals(listOf(RefreshBody("refresh-0")), refreshes)
        assertEquals(token, store.stored?.accessToken)
        assertEquals("refresh-2", store.stored?.refreshToken)
    }

    @Test
    fun refreshAfterRejection_ofTheCurrentToken_refreshes() = runTest {
        store.stored = stored()
        val tokens = tokens()

        val token = tokens.refreshAfterRejection("access-0")

        assertEquals(1, refreshes.size)
        assertEquals(store.stored?.accessToken, token)
    }

    @Test
    fun refreshAfterRejection_ofATokenAlreadyReplaced_takesTheNewOneWithoutRefreshing() = runTest {
        store.stored = stored(access = "access-new")
        val tokens = tokens()

        assertEquals("access-new", tokens.refreshAfterRejection("access-old"))
        assertTrue(refreshes.isEmpty())
    }

    @Test
    fun severalRejectionsAtOnce_shareOneRefresh() = runTest {
        store.stored = stored()
        val answer = CompletableDeferred<Unit>()
        api.refresh = {
            answer.await()
            TokensDto("access-9", "refresh-9", 900)
        }
        val tokens = tokens()

        val calls = List(3) { async { tokens.refreshAfterRejection("access-0") } }
        runCurrent()
        answer.complete(Unit)

        assertEquals(listOf("access-9", "access-9", "access-9"), calls.map { it.await() })
        assertEquals(1, refreshes.size)
    }

    @Test
    fun refresh_refusedWith401_signsOutAndEmitsExpired() = runTest {
        store.stored = stored()
        api.refresh = { throw httpError(401, "INVALID_REFRESH_TOKEN") }
        val tokens = tokens()
        val expired = async { tokens.expired.first() }
        runCurrent()

        assertNull(tokens.refreshAfterRejection("access-0"))

        assertEquals(AuthState.SignedOut, tokens.state.value)
        assertNull(store.stored)
        assertEquals(Unit, expired.await())
    }

    @Test
    fun refresh_withoutAnAnswer_keepsTheTokens() = runTest {
        store.stored = stored()
        api.refresh = { throw IOException("offline") }
        val tokens = tokens()

        assertNull(tokens.refreshAfterRejection("access-0"))

        assertEquals("refresh-0", store.stored?.refreshToken)
        assertTrue(tokens.state.value is AuthState.SignedIn)
    }

    @Test
    fun refresh_answeredWith503_keepsTheTokens() = runTest {
        store.stored = stored()
        api.refresh = { throw httpError(503, "UNAVAILABLE") }
        val tokens = tokens()

        assertNull(tokens.refreshAfterRejection("access-0"))

        assertTrue(tokens.state.value is AuthState.SignedIn)
    }

    @Test
    fun freshAccessToken_signedOut_isNullWithoutARefresh() = runTest {
        assertNull(tokens().freshAccessToken())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun update_ofTheSameUser_keepsTheTokensAndChangesTheAccount() = runTest {
        store.stored = stored()
        val tokens = tokens()

        tokens.update(accountDto(hasGoogle = true))

        assertEquals(true, (tokens.state.value as AuthState.SignedIn).account.hasGoogle)
        assertEquals("access-0", store.stored?.accessToken)
    }

    @Test
    fun update_ofAnotherUser_orAfterSigningOut_changesNothing() = runTest {
        store.stored = stored()
        val tokens = tokens()

        tokens.update(accountDto(id = "u_other"))
        assertEquals("u_1", (tokens.state.value as AuthState.SignedIn).account.userId)

        tokens.clear()
        tokens.update(accountDto())
        assertEquals(AuthState.SignedOut, tokens.state.value)
        assertNull(store.stored)
    }

    @Test
    fun reload_ofAnAccountChangedElsewhere_keepsTheTokensAndChangesTheAccount() = runTest {
        store.stored = stored()
        val tokens = tokens()

        tokens.reload { accountDto().copy(displayName = "New Name") }

        assertEquals("New Name", (tokens.state.value as AuthState.SignedIn).account.displayName)
        assertEquals("New Name", store.stored?.user?.displayName)
        assertEquals("access-0", store.stored?.accessToken)
    }

    @Test
    fun reload_ofTheSameAccount_writesNothing() = runTest {
        store.stored = stored()
        val tokens = tokens()

        tokens.reload { accountDto() }

        assertEquals(0, store.saves)
    }

    @Test
    fun reload_overtakenByAChangeOnThisPhone_keepsThatChange() = runTest {
        store.stored = stored()
        val tokens = tokens()
        val answer = CompletableDeferred<Unit>()

        val read = async { tokens.reload { accountDto().also { answer.await() } } }
        runCurrent()
        tokens.update(accountDto().copy(displayName = "New Name"))
        answer.complete(Unit)
        read.await()

        assertEquals("New Name", (tokens.state.value as AuthState.SignedIn).account.displayName)
        assertEquals("New Name", store.stored?.user?.displayName)
    }

    @Test
    fun reload_overtakenByAChangeThatLeftTheAccountAsItWas_keepsThatAnswer() = runTest {
        store.stored = stored()
        val tokens = tokens()
        val answer = CompletableDeferred<Unit>()

        // The read sees a name another phone set, then this phone saves its own name again.
        val read = async { tokens.reload { accountDto().copy(displayName = "New Name").also { answer.await() } } }
        runCurrent()
        tokens.update(accountDto())
        answer.complete(Unit)
        read.await()

        assertEquals("Test Account 1", (tokens.state.value as AuthState.SignedIn).account.displayName)
        assertEquals("Test Account 1", store.stored?.user?.displayName)
    }

    @Test
    fun reload_withATokenRefreshOnTheWay_stillChangesTheAccount() = runTest {
        store.stored = stored()
        val tokens = tokens()

        tokens.reload {
            tokens.refreshAfterRejection("access-0")
            accountDto().copy(displayName = "New Name")
        }

        assertEquals("New Name", (tokens.state.value as AuthState.SignedIn).account.displayName)
        assertEquals(listOf(RefreshBody("refresh-0")), refreshes)
    }

    @Test
    fun reload_ofAnotherUser_orAfterSigningOut_changesNothing() = runTest {
        store.stored = stored()
        val tokens = tokens()

        tokens.reload { accountDto(id = "u_other") }
        assertEquals("u_1", (tokens.state.value as AuthState.SignedIn).account.userId)

        tokens.reload {
            tokens.clear()
            accountDto()
        }
        assertEquals(AuthState.SignedOut, tokens.state.value)
        assertNull(store.stored)
    }
}
