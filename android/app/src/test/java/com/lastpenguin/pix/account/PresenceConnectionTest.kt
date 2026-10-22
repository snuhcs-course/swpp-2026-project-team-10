// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for presence (FR-5.9, Design 2.5.2). Signed in × in front: both (a socket opens with the access token),
 * signed out, in the background. Changes while open: the app goes to the background, the user signs out, another user
 * signs in (the socket closes; for the new user a new one opens). A socket that closes: opens again after 1 s, then 2 s,
 * … up to 30 s while it keeps failing, and after 1 s again once one had opened. No token (refresh failed): no socket,
 * tried again later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenceConnectionTest {
    private val auth = FakeAuthRepository()
    private val foreground = MutableStateFlow(true)

    /** Each `hold` waits until the test closes it; [opens] records the token, [open] is the socket being held. */
    private class Sockets : PresenceSockets {
        val opens = mutableListOf<String>()
        var open: CompletableDeferred<Boolean>? = null
        var cancelled = 0

        override suspend fun hold(accessToken: String): Boolean {
            opens += accessToken
            val socket = CompletableDeferred<Boolean>()
            open = socket
            try {
                return socket.await()
            } catch (cancelled: CancellationException) {
                this.cancelled++
                throw cancelled
            }
        }

        /** The server closes the socket; [wasOpen] tells whether it had opened first. */
        fun close(wasOpen: Boolean) {
            open?.complete(wasOpen)
        }
    }

    private val sockets = Sockets()

    private fun TestScope.start() {
        PresenceConnection(auth, foreground, sockets, backgroundScope).start()
        runCurrent()
    }

    @Test
    fun signedInAndInFront_opensASocketWithTheAccessToken() = runTest {
        auth.signInAs(auth.account)
        start()

        assertEquals(listOf("access-1"), sockets.opens)
    }

    @Test
    fun signedOut_orInTheBackground_opensNothing() = runTest {
        start()
        auth.signInAs(auth.account)
        foreground.value = false
        runCurrent()

        assertTrue(sockets.opens.isEmpty())
    }

    @Test
    fun goingToTheBackground_closesTheSocket() = runTest {
        auth.signInAs(auth.account)
        start()

        foreground.value = false
        runCurrent()

        assertEquals(1, sockets.cancelled)
        advanceTimeBy(60_000)
        assertEquals(1, sockets.opens.size)
    }

    @Test
    fun signingOut_closesTheSocket() = runTest {
        auth.signInAs(auth.account)
        start()

        auth.signOut()
        runCurrent()

        assertEquals(1, sockets.cancelled)
    }

    @Test
    fun anotherUserSigningIn_opensANewSocket() = runTest {
        auth.signInAs(auth.account)
        start()

        auth.token = "access-other"
        auth.signInAs(auth.account.copy(userId = "u_other"))
        runCurrent()

        assertEquals(1, sockets.cancelled)
        assertEquals(listOf("access-1", "access-other"), sockets.opens)
    }

    @Test
    fun aSocketThatKeepsFailing_waitsLongerEachTime() = runTest {
        auth.signInAs(auth.account)
        start()

        val waits = mutableListOf<Long>()
        repeat(7) {
            val before = sockets.opens.size
            val start = testScheduler.currentTime
            sockets.close(wasOpen = false)
            while (sockets.opens.size == before) advanceTimeBy(100)
            waits += testScheduler.currentTime - start
        }

        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000), waits.map { it - it % 1_000 })
    }

    @Test
    fun aSocketThatHadOpened_triesAgainAfterOneSecond() = runTest {
        auth.signInAs(auth.account)
        start()
        sockets.close(wasOpen = false)
        advanceTimeBy(1_100)
        sockets.close(wasOpen = false)
        advanceTimeBy(2_100)
        assertEquals(3, sockets.opens.size)

        sockets.close(wasOpen = true)
        advanceTimeBy(1_100)

        assertEquals(4, sockets.opens.size)
    }

    @Test
    fun noToken_opensNothingAndTriesAgainLater() = runTest {
        auth.token = null
        auth.signInAs(auth.account)
        start()
        assertTrue(sockets.opens.isEmpty())

        auth.token = "access-2"
        advanceTimeBy(1_100)

        assertEquals(listOf("access-2"), sockets.opens)
        assertFalse(sockets.cancelled > 0)
    }
}
