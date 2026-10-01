package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SessionManager] with room codes, WebSocket signaling, and WebRTC (Design 2.2, Figure 7; states in 2.4).
 * Owner: Real-time (#8).
 */
class RtcSessionManager(
    private val signaling: SignalingClient,
    private val peer: PeerConnectionClient,
    private val frameSource: CameraFrameSource,
    private val codec: MessageCodec,
    private val router: ChannelRouter,
) : SessionManager {

    /** Every WebRTC and WebSocket callback hops onto this one serial scope (Design 2.8, Concurrency). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SessionMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<SessionMessage> = _incoming.asSharedFlow()

    override suspend fun start(entry: SessionEntry, role: Role) {
        TODO("#8: room.create or room.join, then offer/answer and ICE; 15 s connecting timeout")
    }

    override fun send(message: SessionMessage) {
        TODO("#8: codec.encode, then the channel from router.channelFor")
    }

    override fun videoSink(): FrameSink = frameSource

    override suspend fun leave(reason: EndReason) {
        TODO("#8: session.leave to the peer, leave to the server, close, state = Ended(reason)")
    }
}
