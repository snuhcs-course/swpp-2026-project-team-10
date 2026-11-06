// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.DeviceBody
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for the push token (Design 2.6.6). Auth: signed in at the start (the kept sign-in), signing in later,
 * signed out (nothing), signed out and in again (again), the same account changed (not again), another account
 * (again). Firebase: a token, none. Rotation: while signed in (registered), while signed out (not). The server failing
 * (logged, and the next sign-in tries again).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRegistrarTest {
    private val api = FakeAccountApi()
    private val account = accountDto().toAccount()
    private val auth = MutableStateFlow<AuthState>(AuthState.Loading)
    private var token: String? = "fcm-1"

    private fun TestScope.registrar() =
        PushTokenRegistrar(api, auth, { token }, backgroundScope).also {
            it.start()
            runCurrent()
        }

    private fun registered(): List<String> = api.bodies.filterIsInstance<DeviceBody>().map {
        assertEquals("android", it.platform)
        it.fcmToken
    }

    @Test
    fun eachSignInRegistersThePhone_theKeptOneAtStartIncluded() = runTest {
        registrar()
        assertTrue(registered().isEmpty())
        auth.value = AuthState.SignedIn(account)
        runCurrent()
        assertEquals(listOf("fcm-1"), registered())

        // A name changed elsewhere is the same sign-in.
        auth.value = AuthState.SignedIn(account.copy(displayName = "Renamed"))
        runCurrent()
        assertEquals(listOf("fcm-1"), registered())

        auth.value = AuthState.SignedOut
        runCurrent()
        auth.value = AuthState.SignedIn(account)
        runCurrent()
        auth.value = AuthState.SignedIn(account.copy(userId = "u_9"))
        runCurrent()
        assertEquals(listOf("fcm-1", "fcm-1", "fcm-1"), registered())
    }

    @Test
    fun aRotatedTokenIsRegisteredOnlyWhileSignedIn() = runTest {
        val registrar = registrar()
        registrar.onNewToken("fcm-2")
        runCurrent()
        assertTrue(registered().isEmpty())

        auth.value = AuthState.SignedIn(account)
        runCurrent()
        registrar.onNewToken("fcm-3")
        runCurrent()
        assertEquals(listOf("fcm-1", "fcm-3"), registered())
    }

    @Test
    fun noTokenFromFirebase_registersNothing() = runTest {
        token = null
        registrar()
        auth.value = AuthState.SignedIn(account)
        runCurrent()
        assertTrue(registered().isEmpty())
    }

    @Test
    fun aFailedRegistration_isTriedAtTheNextSignIn() = runTest {
        registrar()
        api.failure = IOException("no network")
        auth.value = AuthState.SignedIn(account)
        runCurrent()
        api.failure = null
        auth.value = AuthState.SignedOut
        runCurrent()
        auth.value = AuthState.SignedIn(account)
        runCurrent()
        // The failed call is recorded too, so the token appears for both tries.
        assertEquals(listOf("fcm-1", "fcm-1"), registered())
    }
}
