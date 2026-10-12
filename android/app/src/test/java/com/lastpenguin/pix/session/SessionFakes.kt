// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.signaling.IceCandidate
import com.lastpenguin.pix.session.signaling.SessionDescription
import com.lastpenguin.pix.session.signaling.SignalMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.EglBase
import org.webrtc.VideoSink

/** Records what the manager sends and lets a test play the server. */
class FakeSignalingClient : SignalingClient {
    private val _events = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SignalEvent> = _events.asSharedFlow()

    val sent = mutableListOf<SignalMessage>()
    val connectedUrls = mutableListOf<String>()
    var closeCount = 0

    override fun connect(url: String) {
        connectedUrls += url
    }

    override fun send(signal: SignalMessage) {
        sent += signal
    }

    override fun close() {
        closeCount++
    }

    fun push(event: SignalEvent) = check(_events.tryEmit(event))

    fun receive(message: SignalMessage) = push(SignalEvent.Received(message))
}

/** Records what the manager does with the peer connection and lets a test play the other phone. */
class FakePeerConnectionClient(val config: PeerConfig) : PeerConnectionClient {
    private val _events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<PeerEvent> = _events.asSharedFlow()

    val sent = mutableListOf<Pair<Channel, String>>()
    val remote = mutableListOf<SessionDescription>()
    val candidates = mutableListOf<IceCandidate>()
    val sinks = mutableListOf<VideoSink>()
    var buffered = 0L
    var closed = false
    private var offers = 0
    private var answers = 0

    override suspend fun createOffer() = SessionDescription("offer", "v=0 offer ${++offers}")

    override suspend fun createAnswer() = SessionDescription("answer", "v=0 answer ${++answers}")

    override suspend fun applyRemote(sdp: SessionDescription) {
        remote += sdp
    }

    override fun addIceCandidate(candidate: IceCandidate) {
        candidates += candidate
    }

    override fun send(channel: Channel, text: String): Boolean {
        sent += channel to text
        return true
    }

    override fun bufferedAmount(channel: Channel): Long = buffered

    var stats: LinkStats? = null
    var statsRequests = 0

    override suspend fun stats(): LinkStats? {
        statsRequests++
        return stats
    }

    override fun addVideoSink(sink: VideoSink) {
        sinks += sink
    }

    override fun removeVideoSink(sink: VideoSink) {
        sinks -= sink
    }

    override fun close() {
        closed = true
    }

    fun push(event: PeerEvent) = check(_events.tryEmit(event))

    /** Both data channels open. */
    fun open() {
        push(PeerEvent.Link(LinkState.CONNECTED))
        push(PeerEvent.ChannelOpen(Channel.REALTIME))
        push(PeerEvent.ChannelOpen(Channel.RELIABLE))
    }

    fun deliver(text: String, channel: Channel = Channel.RELIABLE) = push(PeerEvent.Message(channel, text))
}

class FakePeerFactory : PeerConnectionClient.Factory {
    val created = mutableListOf<FakePeerConnectionClient>()

    override val eglContext: EglBase.Context = object : EglBase.Context {
        override fun getNativeEglContext(): Long = 0L
    }

    override fun create(config: PeerConfig): PeerConnectionClient = FakePeerConnectionClient(config).also {
        created +=
            it
    }
}
