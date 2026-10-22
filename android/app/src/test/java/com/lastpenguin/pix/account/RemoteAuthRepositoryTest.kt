// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.GoogleTokenBody
import com.lastpenguin.pix.core.network.LogInBody
import com.lastpenguin.pix.core.network.PixIdCheckBody
import com.lastpenguin.pix.core.network.SignUpBody
import com.lastpenguin.pix.core.network.UpdateMeBody
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for signing up, in, and out (FR-5.2, FR-5.11–5.15; Design 2.5.3). Sign-up: accepted, PIX_ID_TAKEN,
 * PASSWORD_INVALID with the rule. Check: free, taken (sent as typed, without signing in), PIX_ID_INVALID with the
 * rule, no answer (OFFLINE). Log-in: accepted with capitals and spaces around the Pix ID, INVALID_CREDENTIALS.
 * Google: a registered account, NOT_REGISTERED (404, no sign-in), a new account without a Pix ID. Account changes:
 * addGoogle GOOGLE_IN_USE, setPixId (the account changes, the tokens stay), the display name trimmed. Any call without
 * an answer: OFFLINE. Sign-out: answered, and without an answer (the phone signs out either way).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteAuthRepositoryTest {
    private val authApi = FakeAuthApi()
    private val accountApi = FakeAccountApi()
    private val store = FakeSignInStore()

    private fun TestScope.repository(): RemoteAuthRepository =
        RemoteAuthRepository(authApi, accountApi, AuthTokens(store, authApi, backgroundScope)).also { runCurrent() }

    private fun Result<*>.error(): AccountError? = (exceptionOrNull() as? AccountException)?.error

    @Test
    fun signUp_accepted_signsIn() = runTest {
        val repository = repository()

        val account = repository.signUp("test1", "sunset2026", "  Test Account 1 ").getOrThrow()

        assertEquals(SignUpBody("test1", "sunset2026", "Test Account 1"), authApi.calls.single())
        assertEquals(AuthState.SignedIn(account), repository.state.value)
        assertEquals("access-1", store.stored?.accessToken)
    }

    @Test
    fun signUp_taken_failsWithoutSigningIn() = runTest {
        authApi.signUp = { throw httpError(409, "PIX_ID_TAKEN", "This Pix ID is taken") }
        val repository = repository()

        val result = repository.signUp("test1", "sunset2026", "Test Account 1")

        assertEquals(AccountError.PIX_ID_TAKEN, result.error())
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun signUp_refusedPassword_carriesTheRuleTheServerNames() = runTest {
        authApi.signUp =
            { throw httpError(400, "PASSWORD_INVALID", "A password needs at least one letter and one digit") }

        val error = repository().signUp("test1", "abcdefgh", "Test Account 1").exceptionOrNull() as AccountException

        assertEquals(AccountError.PASSWORD_INVALID, error.error)
        assertEquals("A password needs at least one letter and one digit", error.serverMessage)
    }

    @Test
    fun isPixIdAvailable_answersForThePixIdAsTypedWithoutSigningIn() = runTest {
        val repository = repository()

        assertEquals(true, repository.isPixIdAvailable("test5").getOrThrow())
        assertEquals(false, repository.isPixIdAvailable("test1").getOrThrow())
        assertEquals(listOf(PixIdCheckBody("test5"), PixIdCheckBody("test1")), authApi.calls)
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun isPixIdAvailable_brokenRule_carriesTheRuleTheServerNames() = runTest {
        authApi.checkPixId = { throw httpError(400, "PIX_ID_INVALID", "A Pix ID has at least 4 characters") }

        val error = repository().isPixIdAvailable("abc").exceptionOrNull() as AccountException

        assertEquals(AccountError.PIX_ID_INVALID, error.error)
        assertEquals("A Pix ID has at least 4 characters", error.serverMessage)
    }

    @Test
    fun isPixIdAvailable_withoutAnAnswer_isOffline() = runTest {
        authApi.checkPixId = { throw IOException("no route") }

        assertEquals(AccountError.OFFLINE, repository().isPixIdAvailable("test5").error())
    }

    @Test
    fun logIn_sendsThePixIdWithoutCapitalsOrSpaces() = runTest {
        repository().logIn("  Test1 ", "sunset2026").getOrThrow()

        assertEquals(LogInBody("test1", "sunset2026"), authApi.calls.single())
    }

    @Test
    fun logIn_wrongPassword_isInvalidCredentials() = runTest {
        authApi.logIn = { throw httpError(401, "INVALID_CREDENTIALS") }

        assertEquals(AccountError.INVALID_CREDENTIALS, repository().logIn("test1", "wrong1234").error())
    }

    @Test
    fun anyCall_withoutAnAnswer_isOffline() = runTest {
        authApi.logIn = { throw IOException("no route") }

        assertEquals(AccountError.OFFLINE, repository().logIn("test1", "sunset2026").error())
    }

    @Test
    fun google_registered_signsIn() = runTest {
        val repository = repository()

        val result = repository.signInWithGoogle("id-token").getOrThrow()

        assertTrue(result is GoogleSignIn.SignedIn)
        assertEquals(GoogleTokenBody("id-token"), authApi.calls.single())
        assertTrue(repository.state.value is AuthState.SignedIn)
    }

    @Test
    fun google_notRegistered_asksWithoutSigningIn() = runTest {
        authApi.google = { throw httpError(404, "NOT_REGISTERED") }
        val repository = repository()

        assertEquals(GoogleSignIn.NotRegistered, repository.signInWithGoogle("id-token").getOrThrow())
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun googleSignUp_makesAnAccountWithoutAPixId() = runTest {
        val account = repository().signUpWithGoogle("id-token").getOrThrow()

        assertNull(account.pixId)
        assertTrue(account.hasGoogle)
    }

    @Test
    fun addGoogle_usedByAnotherAccount_failsAndKeepsTheAccount() = runTest {
        val repository = repository()
        repository.logIn("test1", "sunset2026")
        accountApi.failure = httpError(409, "GOOGLE_IN_USE")

        assertEquals(AccountError.GOOGLE_IN_USE, repository.addGoogle("id-token").error())
        assertEquals(false, (repository.state.value as AuthState.SignedIn).account.hasGoogle)
    }

    @Test
    fun setPixId_changesTheAccountAndKeepsTheTokens() = runTest {
        val repository = repository()
        repository.signUpWithGoogle("id-token")
        accountApi.me = accountDto(pixId = null, hasPassword = false, hasGoogle = true)

        repository.setPixId("test2").getOrThrow()

        assertEquals(UpdateMeBody(pixId = "test2"), accountApi.bodies.single())
        assertEquals("test2", (repository.state.value as AuthState.SignedIn).account.pixId)
        assertEquals("access-1", store.stored?.accessToken)
    }

    @Test
    fun setDisplayName_isTrimmed() = runTest {
        val repository = repository()
        repository.logIn("test1", "sunset2026")

        repository.setDisplayName("  Test User  ").getOrThrow()

        assertEquals(UpdateMeBody(displayName = "Test User"), accountApi.bodies.single())
    }

    @Test
    fun signOut_tellsTheServerAndForgetsTheSignIn() = runTest {
        val repository = repository()
        repository.logIn("test1", "sunset2026")

        repository.signOut()

        assertEquals(listOf("signOut"), accountApi.calls)
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertNull(store.stored)
    }

    @Test
    fun signOut_withoutAnAnswer_stillSignsOutThePhone() = runTest {
        val repository = repository()
        repository.logIn("test1", "sunset2026")
        accountApi.failure = IOException("offline")

        repository.signOut()

        assertEquals(AuthState.SignedOut, repository.state.value)
    }
}
