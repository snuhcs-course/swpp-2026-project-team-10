// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.InviteBody
import com.lastpenguin.pix.core.network.OpenedInvitationDto
import com.lastpenguin.pix.core.network.PixAccountApi
import com.lastpenguin.pix.core.network.SentInvitationDto
import com.lastpenguin.pix.core.network.SessionPlaceDto
import com.lastpenguin.pix.session.SessionPlace

/**
 * Invitations to shoot together (Design 2.1, 2.6.6; FR-6.1–6.4, FR-6.9). Sending opens the session on the server with
 * the photographer's place, and accepting gives the friend theirs; the session itself starts in [SessionManager]
 * with `SessionEntry.Invite` or `SessionEntry.Invitation`. Failures are [AccountException]: NOT_FRIENDS, BUSY,
 * INVITATION_GONE, NOT_FOUND, or the phone's own (OFFLINE, SIGNED_OUT). Owner: Real-time (#119).
 *
 * @see com.lastpenguin.pix.session.SessionManager
 */
interface InvitationManager {
    /** Invites a friend: BUSY while the friend is in another session (FR-6.9). */
    suspend fun invite(friendId: String): Result<SentInvitation>

    /** The invitation a push or a link names, without a sign-in; NOT_FOUND for an unknown one. */
    suspend fun open(invitationId: String): Result<OpenedInvitation>

    /** The friend accepts: their place in the session. INVITATION_GONE once it was answered or expired. */
    suspend fun accept(invitationId: String): Result<SessionPlace>

    suspend fun decline(invitationId: String): Result<Unit>

    /** The photographer withdraws it; the friend's Invitation screen then says it is no longer available. */
    suspend fun cancel(invitationId: String): Result<Unit>
}

/** [InvitationManager] on the Pix server's `/invitations` endpoints (Design 2.5.3). */
class RemoteInvitationManager(private val api: PixAccountApi) : InvitationManager {

    override suspend fun invite(friendId: String): Result<SentInvitation> =
        accountCall { api.invite(InviteBody(friendId)).toSentInvitation() }

    override suspend fun open(invitationId: String): Result<OpenedInvitation> =
        accountCall { api.invitation(invitationId).toOpenedInvitation() }

    override suspend fun accept(invitationId: String): Result<SessionPlace> =
        accountCall { api.acceptInvitation(invitationId).toPlace() }

    override suspend fun decline(invitationId: String): Result<Unit> =
        accountCall { api.declineInvitation(invitationId) }

    override suspend fun cancel(invitationId: String): Result<Unit> = accountCall { api.cancelInvitation(invitationId) }
}

internal fun SentInvitationDto.toSentInvitation() =
    SentInvitation(id, to.toPixUser(), expiresAt, link, SessionPlace(sessionId, rejoinToken, iceServers))

/** A status this app does not know yet counts as gone, so it is never offered for an answer. */
internal fun OpenedInvitationDto.toOpenedInvitation() = OpenedInvitation(
    id,
    from.toPixUser(),
    InvitationStatus.entries.firstOrNull { it.name == status } ?: InvitationStatus.CANCELLED,
    expiresAt,
)

internal fun SessionPlaceDto.toPlace() = SessionPlace(sessionId, rejoinToken, iceServers)
