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

    @Serializable
    @SerialName("room.created")
    data class RoomCreated(
        val code: String,
        val sessionId: String,
        val iceServers: List<IceServer> = emptyList(),
    ) : SignalMessage

    /** [peer] is empty in Iteration 1; the other phone's name arrives in `hello`. */
    @Serializable
    @SerialName("session.joined")
    data class SessionJoined(
        val sessionId: String,
        val role: Role,
        val peer: PeerInfo? = null,
        val iceServers: List<IceServer> = emptyList(),
    ) : SignalMessage

    @Serializable
    @SerialName("peer.joined")
    data class PeerJoined(val peer: PeerInfo? = null) : SignalMessage

    @Serializable
    @SerialName("peer.left")
    data class PeerLeft(val reason: EndReason = EndReason.PEER_LEFT) : SignalMessage

    /** code: NOT_FOUND, FULL, EXPIRED, UNKNOWN_TYPE. */
    @Serializable
    @SerialName("error")
    data class ServerError(val code: String, val message: String = "") : SignalMessage
}
