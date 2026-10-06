package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.protocol.PROTOCOL_VERSION
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.signaling.IceCandidate
import com.lastpenguin.pix.session.signaling.IceServer
import com.lastpenguin.pix.session.signaling.SignalMessage
import java.util.EnumSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.EglBase
import org.webrtc.VideoSink

/** `http://host:8000/` becomes `ws://host:8000/ws`; `https` becomes `wss` the same way (Design 2.5.2). */
internal fun signalingUrl(serverUrl: String): String =
    serverUrl.trim().replaceFirst(Regex("^http", RegexOption.IGNORE_CASE), "ws").trimEnd('/') + "/ws"

/**
 * [SessionManager] with room codes, WebSocket signaling, and WebRTC (Design 2.2, Figure 7; states in 2.4).
 *
 * Three phases, so that a later entry point (an invitation, Design AD-8) changes only the first:
 * 1. **Get a session.** `room.create` or `room.join` over signaling.
 * 2. **Connect the peers.** The photographer offers, the subject answers, ICE candidates are relayed.
 * 3. **Run the session.** hello, ping, messages on the two channels, and leaving.
 *
 * Every signaling and WebRTC callback hops onto one serial scope, so the fields below are only touched there
 * (Design 2.8, Concurrency). When the subject leaves or drops, the photographer keeps the room and goes back to
 * `Waiting(code)`, so the subject's *Reconnect* can join the same code again (FR-6.10, FR-6.11).
 * Owner: Real-time (#8).
 */
class RtcSessionManager(
    private val signaling: SignalingClient,
    private val peers: PeerConnectionClient.Factory,
    private val frameSource: CameraFrameSource,
    private val codec: MessageCodec,
    private val router: ChannelRouter,
    private val serverUrl: String,
    private val identity: SessionIdentity,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Subject: the id of the guide image this phone already holds, sent in `hello` (Design 2.5.1). */
    private val haveGuideId: () -> String? = { null },
) : SessionManager, RemoteVideo {

    private val serial = dispatcher.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SessionMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<SessionMessage> = _incoming.asSharedFlow()

    private val _peerCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val peerCapabilities: StateFlow<CameraCapabilities?> = _peerCapabilities.asStateFlow()

    private val _peerZoom = MutableStateFlow<Float?>(null)
    override val peerZoom: StateFlow<Float?> = _peerZoom.asStateFlow()

    override val eglContext: EglBase.Context
        get() = peers.eglContext

    // ---- Session fields, touched only on the serial scope ---------------------
    private var active = false
    private var role: Role? = null
    private var code: String? = null
    private var sessionId: String? = null
    private var iceServers: List<IceServer> = emptyList()
    private var signalingJob: Job? = null

    // ---- Peer connection fields, reset for every connection attempt -----------
    @Volatile
    private var peer: PeerConnectionClient? = null
    private var peerJob: Job? = null
    private var connectTimeout: Job? = null
    private var disconnectGrace: Job? = null
    private var pingJob: Job? = null
    private var remoteDescriptionSet = false
    private val pendingCandidates = ArrayList<IceCandidate>()
    private val openChannels = EnumSet.noneOf(Channel::class.java)
    private var helloSent = false
    private var peerHello: SessionMessage.Hello? = null
    private val lastSeq = HashMap<String, Long>()
    private val reliableQueue = ArrayDeque<String>()

    /** Renderers from the Subject view; guarded by itself, because the view attaches from the main thread. */
    private val sinks = LinkedHashSet<VideoSink>()

    // ---- SessionManager ---------------------------------------------------------

    override suspend fun start(entry: SessionEntry, role: Role) = withContext(serial) {
        if (active) {
            Log.w(TAG, "start ignored: a session is already active")
            return@withContext
        }
        val valid = when (entry) {
            SessionEntry.NewRoom -> role == Role.PHOTOGRAPHER
            is SessionEntry.RoomCode -> role == Role.SUBJECT
        }
        if (!valid) {
            Log.w(TAG, "start ignored: $entry does not fit the role $role")
            return@withContext
        }
        active = true
        this@RtcSessionManager.role = role
        code = (entry as? SessionEntry.RoomCode)?.code
        sessionId = null
        iceServers = emptyList()
        _peerCapabilities.value = null
        _peerZoom.value = null
        // The photographer stays Idle until the code arrives; the subject is connecting from the first moment.
        _state.value = if (role == Role.SUBJECT) SessionState.Connecting(null) else SessionState.Idle
        Timings.mark("session.start", role.name)
        val url = signalingUrl(serverUrl)
        signalingJob = scope.launch {
            try {
                signaling.events
                    .onSubscription { signaling.connect(url) }
                    .collect { event -> onSignal(event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Signaling failed", e)
                end(EndReason.ERROR)
            }
        }
    }

    override fun send(message: SessionMessage) {
        scope.launch { sendNow(message) }
    }

    override fun videoSink(): FrameSink = frameSource

    override suspend fun leave(reason: EndReason) = withContext(serial) {
        if (!active) return@withContext
        if (openChannels.size == 2) {
            sendNow(SessionMessage.Leave(reason))
            // Give the data channel a moment to flush, so the other phone shows "left", not "connection lost".
            delay(LEAVE_FLUSH_MS)
        }
        signaling.send(SignalMessage.Leave)
        end(reason)
    }

    // ---- RemoteVideo ------------------------------------------------------------

    override fun attach(sink: VideoSink) {
        synchronized(sinks) { sinks += sink }
        peer?.addVideoSink(sink)
    }

    override fun detach(sink: VideoSink) {
        synchronized(sinks) { sinks -= sink }
        peer?.removeVideoSink(sink)
    }

    // ---- Phase 1: signaling -------------------------------------------------------

    private suspend fun onSignal(event: SignalEvent) {
        if (!active) return
        when (event) {
            SignalEvent.Opened -> when (role) {
                Role.PHOTOGRAPHER -> signaling.send(SignalMessage.RoomCreate)
                Role.SUBJECT -> signaling.send(SignalMessage.RoomJoin(checkNotNull(code)))
                null -> Unit
            }

            is SignalEvent.Closed -> onSignalingClosed()

            is SignalEvent.Received -> when (val message = event.message) {
                is SignalMessage.RoomCreated -> {
                    code = message.code
                    sessionId = message.sessionId
                    iceServers = message.iceServers
                    _state.value = SessionState.Waiting(message.code)
                    Timings.mark("room.created")
                }

                is SignalMessage.PeerJoined -> onPeerJoined()

                is SignalMessage.SessionJoined -> {
                    sessionId = message.sessionId
                    iceServers = message.iceServers
                    _state.value = SessionState.Connecting(message.sessionId)
                    Timings.mark("room.joined")
                    openPeer()
                }

                is SignalMessage.Signal -> onRemoteSignal(message)

                is SignalMessage.PeerLeft -> onPeerGone(message.reason)

                is SignalMessage.ServerError -> onServerError(message)

                else -> Log.w(TAG, "Unexpected signaling message: $message")
            }
        }
    }

    private fun onSignalingClosed() {
        if (_state.value is SessionState.Connected) {
            // The media runs phone to phone; only "left" notifications from the server are lost now.
            Log.w(TAG, "Signaling closed during a session; the session continues")
            return
        }
        val midConnection = _state.value is SessionState.Connecting && sessionId != null
        end(if (midConnection) EndReason.CONNECTION_LOST else EndReason.ERROR)
    }

    private fun onServerError(error: SignalMessage.ServerError) {
        Log.w(TAG, "Server error ${error.code}: ${error.message}")
        when (error.code) {
            // FULL means the photographer already has a subject; "Session not found" is the closest screen.
            "NOT_FOUND", "FULL" -> if (role == Role.SUBJECT) end(EndReason.NOT_FOUND)

            "EXPIRED" -> end(EndReason.EXPIRED)

            else -> if (_state.value is SessionState.Connecting) fail()
        }
    }

    // ---- Phase 2: the peer connection -----------------------------------------------

    private fun onPeerJoined() {
        if (role != Role.PHOTOGRAPHER) return
        closePeer()
        _state.value = SessionState.Connecting(sessionId)
        Timings.mark("peer.joined")
        openPeer()
    }

    private fun openPeer() {
        val client = peers.create(PeerConfig(checkNotNull(role), iceServers))
        peer = client
        synchronized(sinks) { sinks.toList() }.forEach(client::addVideoSink)
        startConnectTimeout()
        peerJob = scope.launch {
            try {
                client.events
                    .onSubscription { if (role == Role.PHOTOGRAPHER) sendOffer(client) }
                    .collect { event -> onPeerEvent(client, event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (client === peer) {
                    Log.e(TAG, "Peer connection failed", e)
                    fail()
                }
            }
        }
    }

    private suspend fun sendOffer(client: PeerConnectionClient) {
        val offer = client.createOffer()
        signaling.send(SignalMessage.Signal(sdp = offer))
        Timings.mark("offer.sent")
    }

    private suspend fun onRemoteSignal(signal: SignalMessage.Signal) {
        val client = peer
        if (client == null) {
            Log.w(TAG, "Signal before the peer connection exists; ignored")
            return
        }
        try {
            signal.sdp?.let { sdp ->
                when {
                    sdp.type == "offer" && role == Role.SUBJECT -> {
                        client.applyRemote(sdp)
                        remoteDescriptionSet = true
                        flushCandidates(client)
                        val answer = client.createAnswer()
                        signaling.send(SignalMessage.Signal(sdp = answer))
                        Timings.mark("answer.sent")
                    }

                    sdp.type == "answer" && role == Role.PHOTOGRAPHER -> {
                        client.applyRemote(sdp)
                        remoteDescriptionSet = true
                        flushCandidates(client)
                        Timings.mark("answer.applied")
                    }

                    else -> Log.w(TAG, "Unexpected SDP ${sdp.type} for role $role")
                }
            }
            signal.candidate?.let { candidate ->
                if (remoteDescriptionSet) client.addIceCandidate(candidate) else pendingCandidates += candidate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not apply the remote signal", e)
            fail()
        }
    }

    private fun flushCandidates(client: PeerConnectionClient) {
        pendingCandidates.forEach(client::addIceCandidate)
        pendingCandidates.clear()
    }

    private fun onPeerEvent(client: PeerConnectionClient, event: PeerEvent) {
        if (client !== peer) return
        when (event) {
            is PeerEvent.LocalCandidate -> signaling.send(SignalMessage.Signal(candidate = event.candidate))

            is PeerEvent.Link -> when (event.state) {
                LinkState.CONNECTED -> {
                    disconnectGrace?.cancel()
                    disconnectGrace = null
                    Timings.mark("ice.connected")
                }

                LinkState.DISCONNECTED -> if (_state.value is SessionState.Connected) startDisconnectGrace()

                LinkState.FAILED, LinkState.CLOSED -> onLinkLost()

                LinkState.CONNECTING -> Unit
            }

            is PeerEvent.ChannelOpen -> {
                openChannels += event.channel
                if (openChannels.size == 2 && !helloSent) {
                    sendHello()
                    drainReliable()
                }
                maybeConnected()
            }

            is PeerEvent.ChannelClosed -> Log.d(TAG, "${event.channel} channel closed")

            is PeerEvent.Message -> onPeerMessage(event.text)

            is PeerEvent.BufferedAmount -> if (event.channel == Channel.RELIABLE) drainReliable()
        }
    }

    // ---- Phase 3: the running session ------------------------------------------------

    /** The first message on the reliable channel, ahead of anything queued while the channels were opening. */
    private fun sendHello() {
        helloSent = true
        val hello = SessionMessage.Hello(
            appVersion = identity.appVersion,
            role = checkNotNull(role),
            name = identity.name,
            haveGuideId = if (role == Role.SUBJECT) haveGuideId() else null,
        )
        peer?.send(Channel.RELIABLE, codec.encode(hello))
    }

    /** Connected once both channels are open and the other phone said hello, which carries its name (Design 2.4). */
    private fun maybeConnected() {
        val hello = peerHello ?: return
        if (_state.value !is SessionState.Connecting || openChannels.size != 2) return
        connectTimeout?.cancel()
        connectTimeout = null
        _state.value = SessionState.Connected(
            checkNotNull(sessionId),
            PeerInfo(hello.name, hello.haveGuideId),
            checkNotNull(role),
        )
        Timings.mark("session.connected")
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                sendNow(SessionMessage.Ping(clock()))
                logStats()
            }
        }
    }

    /** One `stats` line per ping: codec, size, fps, bitrate, and RTT, so NFR-5, 6, and 12 can be read from logcat. */
    private suspend fun logStats() {
        val client = peer ?: return
        val stats = runCatching { client.stats() }.getOrNull() ?: return
        Timings.mark("stats", stats.summary())
    }

    private fun onPeerMessage(text: String) {
        val envelope = codec.decode(text) ?: return
        if (router.latestOnly(envelope.message)) {
            val last = lastSeq[envelope.type]
            if (last != null && envelope.seq <= last) return
            lastSeq[envelope.type] = envelope.seq
        }
        when (val message = envelope.message) {
            is SessionMessage.Hello -> {
                if (message.protocol != PROTOCOL_VERSION) {
                    Log.e(TAG, "Protocol ${message.protocol} from the other phone; this app speaks $PROTOCOL_VERSION")
                    end(EndReason.ERROR)
                    return
                }
                peerHello = message
                _incoming.tryEmit(message)
                maybeConnected()
            }

            is SessionMessage.Ping -> sendNow(SessionMessage.Pong(message.ts))

            is SessionMessage.Pong -> Timings.mark("rtt", "${clock() - message.ts} ms")

            is SessionMessage.Leave -> {
                _incoming.tryEmit(message)
                onPeerGone(message.reason)
            }

            is SessionMessage.Capabilities -> {
                _peerCapabilities.value = message.capabilities
                _incoming.tryEmit(message)
            }

            is SessionMessage.CameraStateUpdate -> {
                _peerZoom.value = message.zoom
                _incoming.tryEmit(message)
            }

            else -> _incoming.tryEmit(message)
        }
    }

    /**
     * Realtime messages go out at once or not at all. Reliable messages wait in order until hello has gone out and
     * the channel's buffer is below the limit (Design 2.8, Backpressure).
     */
    private fun sendNow(message: SessionMessage): Boolean {
        val client = peer ?: return false
        val text = codec.encode(message)
        return when (router.channelFor(message)) {
            Channel.REALTIME -> client.send(Channel.REALTIME, text)

            Channel.RELIABLE -> {
                val wait = !helloSent || reliableQueue.isNotEmpty() ||
                    client.bufferedAmount(Channel.RELIABLE) >= BACKPRESSURE_BYTES
                if (wait) {
                    reliableQueue.addLast(text)
                    true
                } else {
                    client.send(Channel.RELIABLE, text)
                }
            }
        }
    }

    /** Sends queued reliable messages while the channel's buffer is below the limit (Design 2.8, Backpressure). */
    private fun drainReliable() {
        val client = peer ?: return
        if (!helloSent) return
        while (reliableQueue.isNotEmpty() && client.bufferedAmount(Channel.RELIABLE) < BACKPRESSURE_BYTES) {
            client.send(Channel.RELIABLE, reliableQueue.removeFirst())
        }
    }

    // ---- Leaving, losing, and ending ------------------------------------------------

    /** The other phone left on purpose, or the server reported it gone. */
    private fun onPeerGone(reason: EndReason) {
        val onPurpose = reason == EndReason.LEFT || reason == EndReason.PEER_LEFT || reason == EndReason.CANCELLED
        when (role) {
            Role.PHOTOGRAPHER -> {
                if (peer != null) {
                    // A `peer.left` from the server after the data channel `session.leave` must not notify twice.
                    if (reason !=
                        EndReason.LEFT
                    ) {
                        notifyPeerGone(if (onPurpose) EndReason.PEER_LEFT else EndReason.CONNECTION_LOST)
                    }
                    closePeer()
                }
                if (active) _state.value = SessionState.Waiting(checkNotNull(code))
            }

            Role.SUBJECT -> end(if (onPurpose) EndReason.PEER_LEFT else EndReason.CONNECTION_LOST)

            null -> Unit
        }
    }

    /** The peer connection failed, or the connecting or disconnect timer ran out. */
    private fun onLinkLost() {
        when (role) {
            Role.PHOTOGRAPHER -> {
                notifyPeerGone(EndReason.CONNECTION_LOST)
                closePeer()
                if (active) _state.value = SessionState.Waiting(checkNotNull(code))
            }

            Role.SUBJECT -> end(EndReason.CONNECTION_LOST)

            null -> Unit
        }
    }

    /** Negotiation failed. The photographer keeps the room for another try; the subject's join has failed. */
    private fun fail() {
        when (role) {
            Role.PHOTOGRAPHER -> onLinkLost()
            Role.SUBJECT -> end(EndReason.ERROR)
            null -> Unit
        }
    }

    /** Tells the photographer's screen that the subject is gone, as if the subject had sent `session.leave`. */
    private fun notifyPeerGone(reason: EndReason) {
        if (peerHello != null) _incoming.tryEmit(SessionMessage.Leave(reason))
    }

    private fun startConnectTimeout() {
        connectTimeout?.cancel()
        connectTimeout = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value is SessionState.Connecting) {
                Log.w(TAG, "No connection after ${CONNECT_TIMEOUT_MS / 1000} s")
                onLinkLost()
            }
        }
    }

    private fun startDisconnectGrace() {
        if (disconnectGrace?.isActive == true) return
        disconnectGrace = scope.launch {
            delay(DISCONNECT_GRACE_MS)
            if (active) {
                Log.w(TAG, "Not reconnected after ${DISCONNECT_GRACE_MS / 1000} s")
                onLinkLost()
            }
        }
    }

    private fun closePeer() {
        val client = peer ?: return
        peer = null
        peerJob?.cancel()
        peerJob = null
        pingJob?.cancel()
        pingJob = null
        connectTimeout?.cancel()
        connectTimeout = null
        disconnectGrace?.cancel()
        disconnectGrace = null
        remoteDescriptionSet = false
        pendingCandidates.clear()
        openChannels.clear()
        helloSent = false
        peerHello = null
        lastSeq.clear()
        reliableQueue.clear()
        client.close()
    }

    private fun end(reason: EndReason) {
        if (!active) return
        active = false
        closePeer()
        signalingJob?.cancel()
        signalingJob = null
        signaling.close()
        role = null
        code = null
        sessionId = null
        _state.value = SessionState.Ended(reason)
        Timings.mark("session.ended", reason.name)
    }

    private companion object {
        const val TAG = "PixSession"
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val DISCONNECT_GRACE_MS = 10_000L
        const val PING_INTERVAL_MS = 2_000L
        const val LEAVE_FLUSH_MS = 150L
        const val BACKPRESSURE_BYTES = 256L * 1024
    }
}
