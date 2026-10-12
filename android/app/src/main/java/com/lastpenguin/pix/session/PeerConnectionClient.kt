// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.signaling.IceCandidate
import com.lastpenguin.pix.session.signaling.IceServer
import com.lastpenguin.pix.session.signaling.SessionDescription
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharedFlow
import org.webrtc.EglBase
import org.webrtc.VideoSink

/** What one peer connection needs to start: the role, and the ICE servers from `room.created` or `session.joined`. */
data class PeerConfig(val role: Role, val iceServers: List<IceServer>)

/** The peer connection's own state, reduced to what the session cares about. */
enum class LinkState { CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED }

/** A snapshot of the video link from WebRTC's statistics, for the `stats` line in the Timings log (NFR-5, 6, 12). */
data class LinkStats(
    val codec: String?,
    val width: Int?,
    val height: Int?,
    val framesPerSecond: Double?,
    val bitrateKbps: Double?,
    val roundTripMs: Double?,
) {
    fun summary(): String = listOfNotNull(
        codec?.let { "codec=$it" },
        if (width != null && height != null) "${width}x$height" else null,
        framesPerSecond?.let { String.format(Locale.US, "%.1ffps", it) },
        bitrateKbps?.let { "${it.roundToInt()}kbps" },
        roundTripMs?.let { "rtt=${it.roundToInt()}ms" },
    ).joinToString(" ")
}

/** What happens on one peer connection. */
sealed interface PeerEvent {
    /** A local ICE candidate to relay through signaling. */
    data class LocalCandidate(val candidate: IceCandidate) : PeerEvent

    data class Link(val state: LinkState) : PeerEvent

    data class ChannelOpen(val channel: Channel) : PeerEvent

    data class ChannelClosed(val channel: Channel) : PeerEvent

    /** A text message from the other phone, still encoded as an envelope. */
    data class Message(val channel: Channel, val text: String) : PeerEvent

    /** The channel's send buffer changed; used for backpressure on the reliable channel (Design 2.8). */
    data class BufferedAmount(val channel: Channel, val bytes: Long) : PeerEvent
}

/**
 * One WebRTC peer connection: the photographer's video track and the two data channels, realtime and reliable
 * (Design 2.5.1, 2.6.4). [WebRtcPeerConnectionClient] is the real one; tests use a fake.
 * Owner: Real-time (#8).
 */
interface PeerConnectionClient {
    val events: SharedFlow<PeerEvent>

    /** Photographer: creates the offer and applies it as the local description. */
    suspend fun createOffer(): SessionDescription

    /** Subject, after [applyRemote] with the photographer's offer: creates and applies the answer. */
    suspend fun createAnswer(): SessionDescription

    suspend fun applyRemote(sdp: SessionDescription)

    fun addIceCandidate(candidate: IceCandidate)

    /** False when the channel is not open. */
    fun send(channel: Channel, text: String): Boolean

    fun bufferedAmount(channel: Channel): Long

    /** The current link statistics, or null when none are available yet. */
    suspend fun stats(): LinkStats?

    /** Subject: receives the photographer's video once the track arrives. */
    fun addVideoSink(sink: VideoSink)

    fun removeVideoSink(sink: VideoSink)

    /** Releases the native objects. Safe to call twice. */
    fun close()

    /** Creates one client per connection attempt, so a reconnect always starts from a clean peer connection. */
    interface Factory {
        /** For the subject's renderer; it shares the context of the decoder (Design 2.6.4). */
        val eglContext: EglBase.Context

        fun create(config: PeerConfig): PeerConnectionClient
    }
}
