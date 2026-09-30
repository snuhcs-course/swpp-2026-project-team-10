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

    /** Either side after an invitation is accepted (Iteration 2). */
    data class Invitation(val invitationId: String) : SessionEntry
}

@Serializable
data class PeerInfo(val displayName: String, val pixId: String? = null)

@Serializable
enum class EndReason { LEFT, PEER_LEFT, CONNECTION_LOST, NOT_FOUND, DECLINED, EXPIRED, CANCELLED, ERROR }

/** Figure 9 in the Design document. */
sealed interface SessionState {
    data object Idle : SessionState

    /** Photographer: code shown or invitation sent. */
    data class Waiting(val entry: SessionEntry) : SessionState

    data class Connecting(val sessionId: String?) : SessionState

    data class Connected(val sessionId: String, val peer: PeerInfo, val role: Role) : SessionState

    /** Iteration 2 (FR-6.12). */
    data class Reconnecting(val sessionId: String, val deadlineMs: Long) : SessionState

    data class Ended(val reason: EndReason) : SessionState
}
