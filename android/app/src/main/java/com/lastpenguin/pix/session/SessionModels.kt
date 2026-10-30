// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.signaling.IceServer
import kotlinx.serialization.Serializable

@Serializable
enum class Role { PHOTOGRAPHER, SUBJECT }

/** How a session starts (Design AD-8). Only the entry point differs between iterations. */
sealed interface SessionEntry {
    /** Photographer asks the server for a new room code (Iteration 1). */
    data object NewRoom : SessionEntry

    /** Subject joins with a code (Iteration 1). */
    data class RoomCode(val code: String) : SessionEntry

    /**
     * Subject: *Reconnect* after the connection was lost (FR-6.11). Takes this phone's place in the same session again
     * with its rejoin token while the photographer still holds it, or joins [code] again when it has no token, as with
     * a server that gives none (Design 2.6.7).
     */
    data class Rejoin(val code: String) : SessionEntry

    /** Photographer: the place an invitation opened when it was sent (Design 2.6.6); `Inviting` until the friend comes. */
    data class Invite(val invitationId: String, val place: SessionPlace) : SessionEntry

    /** Subject: the place accepting an invitation gave this phone. */
    data class Invitation(val invitationId: String, val place: SessionPlace) : SessionEntry
}

/**
 * A place in a session that the server opened over REST, for an invitation (Design 2.6.6): taken with `session.join`,
 * with [rejoinToken] proving it, and connected with [iceServers], as a room code's would be.
 */
data class SessionPlace(val sessionId: String, val rejoinToken: String, val iceServers: List<IceServer>)

@Serializable
data class PeerInfo(
    val displayName: String,
    /** Subject only: the guide image it already has, from its `hello`; the photographer skips sending that one. */
    val haveGuideId: String? = null,
    /** Subject only: the guide whose reference photo it already has, from its `hello` (FR-3.9). */
    val haveReferenceId: String? = null,
)

/**
 * Why a session ended. [RATE_LIMITED] (too many wrong codes from this phone's network, Design 2.5.2) only ends a
 * join on this phone, and [DECLINED] (the friend declined the invitation) only the photographer's invitation; neither is
 * sent in `session.leave`. [EXPIRED] also ends an invitation nobody accepted in time.
 */
@Serializable
enum class EndReason { LEFT, PEER_LEFT, CONNECTION_LOST, NOT_FOUND, EXPIRED, CANCELLED, ERROR, RATE_LIMITED, DECLINED }

/** Session states (Design 2.4). */
sealed interface SessionState {
    data object Idle : SessionState

    /** Photographer: the room code from the server is shown, waiting for the subject. */
    data class Waiting(val code: String) : SessionState

    /** Photographer: an invitation is sent and this phone has its place; waiting for the friend (Design 2.6.6). */
    data class Inviting(val invitationId: String) : SessionState

    data class Connecting(val sessionId: String?) : SessionState

    data class Connected(val sessionId: String, val peer: PeerInfo, val role: Role) : SessionState

    /**
     * Both phones: the link dropped while connected, and the session tries to restore it for up to 10 s from [since],
     * a time on the session's clock (Design 2.4, 2.6.7; NFR-11).
     */
    data class Reconnecting(val sessionId: String, val since: Long) : SessionState

    data class Ended(val reason: EndReason) : SessionState
}
