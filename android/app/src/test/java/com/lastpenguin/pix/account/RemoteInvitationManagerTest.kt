// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.InviteBody
import com.lastpenguin.pix.core.network.SessionPlaceDto
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.session.signaling.IceServer
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Partitions for invitations (Design 2.5.3, FR-6.1–6.4, FR-6.9). Invite: sent (the link and the photographer's place),
 * NOT_FRIENDS, BUSY, no connection. Open: each status, a status the app does not know (gone), NOT_FOUND. Accept: the
 * friend's place, INVITATION_GONE (410 with its code, and a 410 without one). Decline and cancel.
 */
class RemoteInvitationManagerTest {
    private val api = FakeAccountApi()
    private val invitations = RemoteInvitationManager(api)
    private val relay = listOf(IceServer(listOf("turn:turn.cloudflare.com:3478"), "user", "secret"))

    private fun failure(result: Result<*>): AccountError = (result.exceptionOrNull() as AccountException).error

    @Test
    fun invite_givesTheLinkAndThePhotographersPlace() = runTest {
        api.invited = api.invited.copy(iceServers = relay)

        val sent = invitations.invite("u_2").getOrThrow()

        assertEquals(listOf<Any>(InviteBody("u_2")), api.bodies)
        assertEquals(
            SentInvitation(
                "inv_1",
                PixUser("u_2", "test2", "Test Account 2"),
                expiresAt = 1_030_000,
                link = "http://pix.test/i/inv_1",
                place = SessionPlace("s_1", "photographer-token", relay),
            ),
            sent,
        )
    }

    @Test
    fun invite_failsWithTheServersReason() = runTest {
        api.failure = httpError(409, "BUSY", "Test Account 2 is in another session")
        assertEquals(AccountError.BUSY, failure(invitations.invite("u_2")))
        api.failure = httpError(403, "NOT_FRIENDS")
        assertEquals(AccountError.NOT_FRIENDS, failure(invitations.invite("u_3")))
        api.failure = IOException("no network")
        assertEquals(AccountError.OFFLINE, failure(invitations.invite("u_2")))
    }

    @Test
    fun open_readsEachStatus_andAnUnknownOneAsGone() = runTest {
        for (status in InvitationStatus.entries) {
            api.opened = api.opened.copy(status = status.name)
            assertEquals(status, invitations.open("inv_1").getOrThrow().status)
        }
        api.opened = api.opened.copy(status = "PAUSED")
        assertEquals(InvitationStatus.CANCELLED, invitations.open("inv_1").getOrThrow().status)
        assertEquals(PixUser("u_1", "test1", "Test Account 1"), invitations.open("inv_1").getOrThrow().from)
        api.failure = httpError(404, "NOT_FOUND")
        assertEquals(AccountError.NOT_FOUND, failure(invitations.open("inv_9")))
    }

    @Test
    fun accept_givesTheFriendsPlace_orGone() = runTest {
        api.place = SessionPlaceDto("s_1", "subject-token", relay)
        assertEquals(SessionPlace("s_1", "subject-token", relay), invitations.accept("inv_1").getOrThrow())
        assertEquals("acceptInvitation:inv_1", api.calls.last())

        api.failure = httpError(410, "INVITATION_GONE")
        assertEquals(AccountError.INVITATION_GONE, failure(invitations.accept("inv_1")))
    }

    @Test
    fun declineAndCancel_callTheirEndpoints() = runTest {
        invitations.decline("inv_1").getOrThrow()
        invitations.cancel("inv_2").getOrThrow()
        assertEquals(listOf("declineInvitation:inv_1", "cancelInvitation:inv_2"), api.calls)
    }
}
