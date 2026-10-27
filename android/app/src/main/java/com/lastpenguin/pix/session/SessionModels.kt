// AI-generated with Claude Code, 2026-10-26, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import kotlinx.serialization.Serializable

@Serializable
enum class Role { PHOTOGRAPHER, SUBJECT }

/** How a session starts (Design AD-8). Only the entry point differs between iterations. */
sealed interface SessionEntry {
    /** Photographer asks the server for a new room code (Iteration 1). */
    data object NewRoom : SessionEntry

    /** Subject joins with a code (Iteration 1). */
    data class RoomCode(val code: String) : SessionEntry
}

@Serializable
data class PeerInfo(
    val displayName: String,
    /** Subject only: the guide image it already has, from its `hello`; the photographer skips sending that one. */
    val haveGuideId: String? = null,
)

/**
 * Why a session ended. [RATE_LIMITED] (too many wrong codes from this phone's network, Design 2.5.2) only ends a
 * join on this phone and is never sent in `session.leave`.
 */
@Serializable
enum class EndReason { LEFT, PEER_LEFT, CONNECTION_LOST, NOT_FOUND, EXPIRED, CANCELLED, ERROR, RATE_LIMITED }

/** Session states (Design 2.4). */
sealed interface SessionState {
    data object Idle : SessionState

    /** Photographer: the room code from the server is shown, waiting for the subject. */
    data class Waiting(val code: String) : SessionState

    data class Connecting(val sessionId: String?) : SessionState

    data class Connected(val sessionId: String, val peer: PeerInfo, val role: Role) : SessionState

    data class Ended(val reason: EndReason) : SessionState
}
