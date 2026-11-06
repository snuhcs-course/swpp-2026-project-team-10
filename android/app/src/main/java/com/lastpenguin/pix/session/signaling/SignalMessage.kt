// AI-generated with Claude Code, 2026-10-27, reviewed by Sungmin Jo
package com.lastpenguin.pix.session.signaling

import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionDescription(val type: String, val sdp: String)

@Serializable
data class IceCandidate(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String)

@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

/**
 * WebSocket signaling messages (Design 2.5.2). JSON form: `{"type": "room.join", "code": "482915"}`.
 */
@Serializable
sealed interface SignalMessage {

    // ---- client -> server --------------------------------------------------

    @Serializable
    @SerialName("room.create")
    data object RoomCreate : SignalMessage

    @Serializable
    @SerialName("room.join")
    data class RoomJoin(val code: String) : SignalMessage

    /**
     * Takes this phone's place in [sessionId] again with the token from `room.created` or `session.joined` (Design
     * 2.6.7). [rejoin] keeps the peer connection, so the other phone hears `peer.rejoined` and the photographer restarts
     * ICE; without it the other phone hears `peer.joined` and the photographer starts a new connection.
     */
    @Serializable
    @SerialName("session.join")
    data class SessionJoin(val sessionId: String, val rejoin: Boolean, val rejoinToken: String) : SignalMessage

    @Serializable
    @SerialName("leave")
    data object Leave : SignalMessage

    // ---- both directions (relayed unchanged to the other peer) ---------------

    @Serializable
    @SerialName("signal")
    data class Signal(
        val sdp: SessionDescription? = null,
        val candidate: IceCandidate? = null,
    ) : SignalMessage

    // ---- server -> client --------------------------------------------------

    /** [rejoinToken] lets this phone take its place again after its socket dropped; null from an older server. */
    @Serializable
    @SerialName("room.created")
    data class RoomCreated(
        val code: String,
        val sessionId: String,
        val iceServers: List<IceServer> = emptyList(),
        val rejoinToken: String? = null,
    ) : SignalMessage

    /**
     * [peer] is empty in Iteration 1; the other phone's name arrives in `hello`. The answer to a `session.join` has no
     * [iceServers] and no [rejoinToken]: the phone keeps the ones it got first.
     */
    @Serializable
    @SerialName("session.joined")
    data class SessionJoined(
        val sessionId: String,
        val role: Role,
        val peer: PeerInfo? = null,
        val iceServers: List<IceServer> = emptyList(),
        val rejoinToken: String? = null,
    ) : SignalMessage

    @Serializable
    @SerialName("peer.joined")
    data class PeerJoined(val peer: PeerInfo? = null) : SignalMessage

    /** The other phone took its place again and keeps its peer connection: the photographer restarts ICE. */
    @Serializable
    @SerialName("peer.rejoined")
    data object PeerRejoined : SignalMessage

    @Serializable
    @SerialName("peer.left")
    data class PeerLeft(val reason: EndReason = EndReason.PEER_LEFT) : SignalMessage

    /**
     * code: NOT_FOUND, FULL, EXPIRED, RATE_LIMITED (too many wrong codes from this network), FORBIDDEN (a rejoin token
     * with no place in the session), PEER_UNAVAILABLE, INVALID_STATE, UNKNOWN_TYPE.
     */
    @Serializable
    @SerialName("error")
    data class ServerError(val code: String, val message: String = "") : SignalMessage
}
