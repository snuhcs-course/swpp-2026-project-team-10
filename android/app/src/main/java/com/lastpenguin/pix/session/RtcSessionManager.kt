// AI-generated with Claude Code, 2026-10-31, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
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
 * 1. **Get a session.** `room.create`, `room.join`, or `session.join` over signaling.
 * 2. **Connect the peers.** The photographer offers, the subject answers, ICE candidates are relayed.
 * 3. **Run the session.** hello, ping, messages on the two channels, reconnecting after a drop, and leaving.
 *
 * Every signaling and WebRTC callback hops onto one serial scope, so the fields below are only touched there
 * (Design 2.8, Concurrency). A link that drops while connected puts both phones in `Reconnecting` for up to 10 s:
 * each takes its place on the server again over a new socket with its rejoin token, and the photographer restarts ICE
 * on the same peer connection (Design 2.6.7). After that, or when the subject leaves, the photographer keeps a room
 * code's room and goes back to `Waiting(code)`, so the subject's *Reconnect* can take its place again (FR-6.10 to
 * FR-6.12); an invitation's session has no code to wait with, so it ends. An invitation's two phones take the places
 * the server opened over REST with `session.join`, the photographer waiting in `Inviting` (Design 2.6.6).
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
    /** Subject: the guide whose reference photo this phone already holds, sent in `hello` (FR-3.9). */
    private val haveReferenceId: () -> String? = { null },
    /** The signed-in user's access token for each socket, or null when nobody is signed in (Design 2.5.2). */
    private val accessToken: suspend () -> String? = { null },
) : SessionManager, RemoteVideo {

    private val serial = dispatcher.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SessionMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<SessionMessage> = _incoming.asSharedFlow()

    private val _peerCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val peerCapabilities: StateFlow<CameraCapabilities?> = _peerCapabilities.asStateFlow()

    private val _peerCamera = MutableStateFlow<CameraState?>(null)
    override val peerCamera: StateFlow<CameraState?> = _peerCamera.asStateFlow()

    private val _path = MutableStateFlow<IcePath?>(null)
    override val path: StateFlow<IcePath?> = _path.asStateFlow()

    override val eglContext: EglBase.Context
        get() = peers.eglContext

    // ---- Session fields, touched only on the serial scope ---------------------
    private var active = false
    private var role: Role? = null
    private var code: String? = null
    private var sessionId: String? = null
    private var iceServers: List<IceServer> = emptyList()
    private var signalingJob: Job? = null

    /** This phone's proof that a new socket may take its place again; null from a server that gives none. */
    private var rejoinToken: String? = null

    /** The first message of the session's first socket: `room.create`, `room.join`, or `session.join`. */
    private var greeting: SignalMessage? = null

    /** Whether the socket was opened again during the session, so that its first message is `session.join`. */
    private var reopened = false

    /** Whether the current socket holds this phone's place, so that the server relays its signals. */
    private var joined = false

    /** A `session.join` for a place this phone already had is waiting for its answer, which opens no new peer. */
    private var rejoining = false
    private var socketRetry: Job? = null
    private var socketFailures = 0

    /** Subject: the session lost last, which *Reconnect* takes the place in again (SessionEntry.Rejoin). */
    private var lost: LostSession? = null

    /** Subject: the ticket this *Reconnect* uses, kept for another try if it fails before the server refuses it. */
    private var ticket: LostSession? = null

    /** Photographer: ends an invitation nobody joined, should the server's own expiry never arrive. */
    private var inviteTimeout: Job? = null

    // ---- Peer connection fields, reset for every connection attempt -----------
    @Volatile
    private var peer: PeerConnectionClient? = null
    private var peerJob: Job? = null
    private var connectTimeout: Job? = null
    private var reconnectBudget: Job? = null
    private var pingJob: Job? = null
    private var remoteDescriptionSet = false
    private val pendingCandidates = ArrayList<IceCandidate>()

    /** Photographer: an offer has gone out and its answer has not come; an answer without one is stale. */
    private var offerPending = false

    /** Photographer: while active, the pending offer may still be answered, so a restart waits for that answer. */
    private var answerWait: Job? = null

    /** Photographer: a restart asked for during [answerWait], which goes once that answer is in or the wait is over. */
    private var restartQueued = false

    /** Local candidates wait while a description is being made, so none with new ICE credentials goes out before it. */
    private var holdingCandidates = false
    private val heldCandidates = ArrayList<IceCandidate>()
    private val openChannels = EnumSet.noneOf(Channel::class.java)

    /** The realtime channel closed during the session while the reliable one runs, which then carries its messages. */
    private var realtimeLost = false
    private var helloSent = false
    private var peerHello: SessionMessage.Hello? = null
    private val lastSeq = HashMap<String, Long>()
    private val reliableQueue = ArrayDeque<String>()

    /** Counts the changes of the reliable channel's buffer, each a chance for [sendBulk] to go on. */
    private val bulkTicks = MutableStateFlow(0L)

    /** Renderers from the Subject view; guarded by itself, because the view attaches from the main thread. */
    private val sinks = LinkedHashSet<VideoSink>()

    // ---- SessionManager ---------------------------------------------------------

    override suspend fun start(entry: SessionEntry, role: Role) = withContext(serial) {
        if (active) {
            Log.w(TAG, "start ignored: a session is already active")
            return@withContext
        }
        val valid = when (entry) {
            SessionEntry.NewRoom, is SessionEntry.Invite -> role == Role.PHOTOGRAPHER
            is SessionEntry.RoomCode, is SessionEntry.Rejoin, is SessionEntry.Invitation -> role == Role.SUBJECT
        }
        if (!valid) {
            Log.w(TAG, "start ignored: $entry does not fit the role $role")
            return@withContext
        }
        active = true
        this@RtcSessionManager.role = role
        // A Reconnect takes its place in the session it lost; without a token for it, it joins the code again.
        val ticket = lost?.takeIf { entry is SessionEntry.Rejoin && it.code == entry.code }
        this@RtcSessionManager.ticket = ticket
        lost = null
        // An invitation's place, opened by the server over REST; the phone takes it as a Reconnect takes its own.
        val place = when (entry) {
            is SessionEntry.Invite -> entry.place
            is SessionEntry.Invitation -> entry.place
            else -> null
        }
        code = when (entry) {
            is SessionEntry.RoomCode -> entry.code
            is SessionEntry.Rejoin -> entry.code
            else -> null
        }
        greeting = when {
            ticket != null -> SignalMessage.SessionJoin(ticket.sessionId, rejoin = false, rejoinToken = ticket.token)
            place != null -> SignalMessage.SessionJoin(place.sessionId, rejoin = false, rejoinToken = place.rejoinToken)
            entry is SessionEntry.RoomCode -> SignalMessage.RoomJoin(entry.code)
            entry is SessionEntry.Rejoin -> SignalMessage.RoomJoin(entry.code)
            else -> SignalMessage.RoomCreate
        }
        // Known from the start for an invitation, so a socket that drops before its answer takes the place again.
        sessionId = place?.sessionId
        iceServers = ticket?.iceServers ?: place?.iceServers ?: emptyList()
        rejoinToken = ticket?.token ?: place?.rejoinToken
        reopened = false
        joined = false
        rejoining = false
        socketFailures = 0
        _peerCapabilities.value = null
        _peerCamera.value = null
        // The photographer stays Idle until the code arrives, or waits for the friend of an invitation; the subject is
        // connecting from the first moment.
        _state.value = when {
            role == Role.SUBJECT -> SessionState.Connecting(null)
            entry is SessionEntry.Invite -> SessionState.Inviting(entry.invitationId)
            else -> SessionState.Idle
        }
        if (entry is SessionEntry.Invite) startInviteTimeout()
        Timings.mark("session.start", role.name)
        val url = signalingUrl(serverUrl)
        signalingJob = scope.launch {
            try {
                signaling.events
                    .onSubscription { signaling.connect(url, accessToken()) }
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

    override suspend fun sendBulk(message: SessionMessage): Boolean {
        while (true) {
            val seen = bulkTicks.value to _state.value
            withContext(serial) { trySendBulk(message) }?.let { return it }
            combine(bulkTicks, _state) { tick, state -> tick to state }.first { it != seen }
        }
    }

    /** null while the message has to wait; false once the session is over. */
    private fun trySendBulk(message: SessionMessage): Boolean? = when (_state.value) {
        is SessionState.Connected -> {
            val client = peer
            when {
                client == null -> false
                reliableQueue.isNotEmpty() || client.bufferedAmount(Channel.RELIABLE) >= BULK_BYTES -> null
                else -> client.send(Channel.RELIABLE, codec.encode(message))
            }
        }

        is SessionState.Reconnecting -> null

        else -> false
    }

    override fun videoSink(): FrameSink = frameSource

    override suspend fun leave(reason: EndReason) = withContext(serial) {
        if (!active) return@withContext
        if (Channel.RELIABLE in openChannels) {
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
            SignalEvent.Opened -> onSignalingOpened()

            is SignalEvent.Closed -> onSignalingClosed()

            is SignalEvent.Received -> when (val message = event.message) {
                is SignalMessage.RoomCreated -> {
                    code = message.code
                    sessionId = message.sessionId
                    iceServers = message.iceServers
                    rejoinToken = message.rejoinToken
                    joined = true
                    _state.value = SessionState.Waiting(message.code)
                    Timings.mark("room.created")
                }

                is SignalMessage.PeerJoined -> onPeerJoined()

                is SignalMessage.PeerRejoined -> onPeerRejoined()

                is SignalMessage.SessionJoined -> if (reopened || rejoining) onRejoined() else onJoined(message)

                is SignalMessage.Signal -> onRemoteSignal(message)

                is SignalMessage.PeerLeft -> onPeerLeft(message.reason)

                is SignalMessage.ServerError -> onServerError(message)

                else -> Log.w(TAG, "Unexpected signaling message: $message")
            }
        }
    }

    private fun onSignalingOpened() {
        val id = sessionId
        val token = rejoinToken
        if (reopened && id != null && token != null) {
            rejoining = true
            signaling.send(SignalMessage.SessionJoin(id, rejoin = true, rejoinToken = token))
        } else {
            greeting?.let(signaling::send)
        }
    }

    /**
     * Its place in the session: the subject's from `room.join` or a `session.join` (a Reconnect's, or an invitation's),
     * or an invitation's photographer's, which waits in `Inviting` for the friend's `peer.joined`.
     */
    private fun onJoined(message: SignalMessage.SessionJoined) {
        sessionId = message.sessionId
        // A `session.join`'s answer has neither; the credentials and the token this phone got first still hold.
        iceServers = message.iceServers.ifEmpty { iceServers }
        rejoinToken = message.rejoinToken ?: rejoinToken
        joined = true
        if (role == Role.PHOTOGRAPHER) {
            Timings.mark("invitation.joined")
            return
        }
        _state.value = SessionState.Connecting(message.sessionId)
        Timings.mark("room.joined")
        openPeer()
    }

    /** A socket opened again during the session has this phone's place back (Design 2.6.7). */
    private fun onRejoined() {
        rejoining = false
        joined = true
        socketFailures = 0
        Timings.mark("signaling.rejoined")
        // An offer sent before, over the old socket or while the subject was away, may never have arrived.
        if (role == Role.PHOTOGRAPHER && _state.value is SessionState.Reconnecting) restartIce()
    }

    private fun onSignalingClosed() {
        joined = false
        rejoining = false
        val state = _state.value
        val inSession = state is SessionState.Connected || state is SessionState.Reconnecting
        val waiting = state is SessionState.Waiting || state is SessionState.Inviting
        if (rejoinToken != null && (inSession || waiting)) {
            // A dropped socket, or a server that restarted: the place waits for this phone on the server (Design 2.6.7).
            // The tries while reconnecting do not count, so a session back or given up starts again at 1 s instead of
            // leaving the room off the server for 30 s after the network returned.
            val retry = if (state is SessionState.Reconnecting) {
                SOCKET_RETRY_MS
            } else {
                socketFailures++
                minOf(SOCKET_RETRY_MAX_MS, SOCKET_RETRY_MS shl minOf(socketFailures - 1, SOCKET_RETRY_DOUBLINGS))
            }
            reopenSignaling(retry)
            return
        }
        if (inSession) {
            // Without a token the phone cannot take its place again. The media runs phone to phone; only "left"
            // notifications from the server are lost now.
            Log.w(TAG, "Signaling closed during a session; the session continues")
            return
        }
        val midConnection = state is SessionState.Connecting && sessionId != null
        end(if (midConnection) EndReason.CONNECTION_LOST else EndReason.ERROR)
    }

    /** Opens a new socket after [delayMs]; its first message takes this phone's place again. */
    private fun reopenSignaling(delayMs: Long = 0) {
        socketRetry?.cancel()
        joined = false
        socketRetry = scope.launch {
            delay(delayMs)
            reopened = true
            signaling.connect(signalingUrl(serverUrl), accessToken())
        }
    }

    /** Signals go only over a socket that holds this phone's place; the server would refuse them on another. */
    private fun sendSignal(signal: SignalMessage.Signal) {
        if (joined) signaling.send(signal) else Log.w(TAG, "Signal dropped: the socket has no place in the session")
    }

    private fun onServerError(error: SignalMessage.ServerError) {
        Log.w(TAG, "Server error ${error.code}: ${error.message}")
        if (rejoining && error.code in REJOIN_REFUSALS) {
            onRejoinRefused()
            return
        }
        when (error.code) {
            // FULL means the photographer already has a subject, and FORBIDDEN that a Reconnect's place went to
            // another subject; "Session not found" is the closest screen. An invitation whose session is gone before
            // its photographer took its place was not accepted in time.
            "NOT_FOUND", "FULL", "FORBIDDEN" -> when {
                role == Role.SUBJECT -> end(EndReason.NOT_FOUND)
                _state.value is SessionState.Inviting -> end(EndReason.EXPIRED)
            }

            // A room nobody joined in time: a room code's, or an invitation's after 60 s (Design 2.6.6).
            "EXPIRED" -> end(EndReason.EXPIRED)

            // The friend declined the invitation; the server has ended its session.
            "DECLINED" -> end(EndReason.DECLINED)

            // Too many wrong codes from this phone's network address (Design 2.5.2, NFR-15); Join says so.
            "RATE_LIMITED" -> if (role == Role.SUBJECT) end(EndReason.RATE_LIMITED)

            else -> if (_state.value is SessionState.Connecting) fail()
        }
    }

    /**
     * The server no longer has this phone's place: the session ended while the socket was away (the photographer left,
     * or nobody was there for 60 s), or another subject took the place. A running session goes on phone to phone, as
     * it would without a token; one that is reconnecting cannot.
     */
    private fun onRejoinRefused() {
        rejoining = false
        when (_state.value) {
            is SessionState.Connected -> Log.w(TAG, "The session has no place on the server any more; it continues")

            is SessionState.Reconnecting -> when (role) {
                Role.PHOTOGRAPHER -> {
                    notifyPeerGone(EndReason.CONNECTION_LOST)
                    end(EndReason.CONNECTION_LOST)
                }

                Role.SUBJECT -> end(EndReason.NOT_FOUND)

                null -> Unit
            }

            // The invitation's session ended while this phone's socket was away.
            is SessionState.Inviting -> end(EndReason.EXPIRED)

            else -> end(EndReason.ERROR)
        }
    }

    // ---- Phase 2: the peer connection -----------------------------------------------

    private fun onPeerJoined() {
        if (role != Role.PHOTOGRAPHER) return
        inviteTimeout?.cancel()
        inviteTimeout = null
        closePeer()
        _state.value = SessionState.Connecting(sessionId)
        Timings.mark("peer.joined")
        openPeer()
    }

    /**
     * Photographer: the subject took its place again over a new socket and keeps its peer connection, so an ICE restart
     * is enough. Also while this phone's own link looks fine, because the subject may have seen a drop it did not; on a
     * working link a restart does not interrupt the video.
     *
     * Subject: the photographer is back on the server. A subject still waiting for an offer may have taken its place
     * while the photographer was away (a Reconnect during the photographer's own drop), and that `peer.joined` went
     * nowhere, so it takes its place again on this socket and the photographer now gets one (Design 2.6.7).
     */
    private fun onPeerRejoined() {
        val state = _state.value
        when (role) {
            Role.PHOTOGRAPHER -> if (state is SessionState.Connected || state is SessionState.Reconnecting) restartIce()

            Role.SUBJECT -> {
                val id = sessionId
                val token = rejoinToken
                if (state is SessionState.Connecting && !remoteDescriptionSet && id != null && token != null) {
                    rejoining = true
                    signaling.send(SignalMessage.SessionJoin(id, rejoin = false, rejoinToken = token))
                }
            }

            null -> Unit
        }
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

    private suspend fun sendOffer(client: PeerConnectionClient, iceRestart: Boolean = false) {
        holdingCandidates = true
        try {
            val offer = client.createOffer(iceRestart)
            offerPending = true
            sendSignal(SignalMessage.Signal(sdp = offer))
        } finally {
            releaseCandidates()
        }
        Timings.mark(if (iceRestart) "ice.restarted" else "offer.sent")
    }

    /**
     * Photographer: an offer with new ICE credentials on the same peer connection, so the channels, the video track,
     * and what is queued on them stay (Design 2.6.7). Both phones taking their places back can ask for two restarts
     * at once, and the subject answers every offer that reaches its socket, so a restart waits while the last offer
     * may still be answered: an answer always meets the offer it answers. An offer that went to the subject's old
     * socket is never answered, so the wait ends after [ANSWER_WAIT_MS].
     */
    private fun restartIce() {
        val client = peer ?: return
        if (offerPending && answerWait?.isActive == true) {
            restartQueued = true
            return
        }
        restartQueued = false
        // Pending from now, not from when the offer is made, so a second request meanwhile waits too.
        offerPending = true
        answerWait?.cancel()
        answerWait = scope.launch {
            delay(ANSWER_WAIT_MS)
            answerWait = null
            if (restartQueued) restartIce()
        }
        // The subject's candidates of the old answer must not meet the new offer; they wait for the new answer.
        remoteDescriptionSet = false
        scope.launch {
            try {
                sendOffer(client, iceRestart = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not restart ICE", e)
            }
        }
    }

    private fun releaseCandidates() {
        holdingCandidates = false
        heldCandidates.forEach { sendSignal(SignalMessage.Signal(candidate = it)) }
        heldCandidates.clear()
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
                        // An offer on a connection that already has one is the photographer's ICE restart.
                        val restart = remoteDescriptionSet
                        client.applyRemote(sdp)
                        remoteDescriptionSet = true
                        flushCandidates(client)
                        holdingCandidates = true
                        try {
                            sendSignal(SignalMessage.Signal(sdp = client.createAnswer()))
                        } finally {
                            releaseCandidates()
                        }
                        Timings.mark(if (restart) "ice.restarted" else "answer.sent")
                    }

                    sdp.type == "answer" && role == Role.PHOTOGRAPHER -> {
                        if (!offerPending) {
                            Log.w(TAG, "An answer to no pending offer; ignored")
                            return
                        }
                        offerPending = false
                        answerWait?.cancel()
                        answerWait = null
                        client.applyRemote(sdp)
                        remoteDescriptionSet = true
                        flushCandidates(client)
                        Timings.mark("answer.applied")
                        if (restartQueued) restartIce()
                    }

                    else -> Log.w(TAG, "Unexpected SDP ${sdp.type} for role $role")
                }
            }
            signal.candidate?.let { candidate ->
                if (remoteDescriptionSet) client.addIceCandidate(candidate) else pendingCandidates += candidate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not apply the remote signal", e)
            // A restart that fails leaves the link as it was: running, or reconnecting until the budget runs out.
            if (_state.value is SessionState.Connecting) fail()
        }
    }

    private fun flushCandidates(client: PeerConnectionClient) {
        pendingCandidates.forEach(client::addIceCandidate)
        pendingCandidates.clear()
    }

    private fun onPeerEvent(client: PeerConnectionClient, event: PeerEvent) {
        if (client !== peer) return
        when (event) {
            is PeerEvent.LocalCandidate ->
                if (holdingCandidates) {
                    heldCandidates += event.candidate
                } else {
                    sendSignal(SignalMessage.Signal(candidate = event.candidate))
                }

            is PeerEvent.Link -> when (event.state) {
                LinkState.CONNECTED -> {
                    Timings.mark("ice.connected")
                    maybeReconnected()
                }

                LinkState.DISCONNECTED, LinkState.FAILED -> {
                    if (event.state == LinkState.DISCONNECTED) Timings.mark("ice.disconnected")
                    when (_state.value) {
                        is SessionState.Connected -> startReconnecting()

                        // The budget decides; a restart can bring back a link that failed.
                        is SessionState.Reconnecting -> Unit

                        else -> if (event.state == LinkState.FAILED) onLinkLost()
                    }
                }

                LinkState.CLOSED -> onLinkLost()

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

            is PeerEvent.ChannelClosed -> {
                Log.d(TAG, "${event.channel} channel closed")
                openChannels -= event.channel
                val state = _state.value
                if (state !is SessionState.Connected && state !is SessionState.Reconnecting) return
                if (event.channel == Channel.RELIABLE) {
                    // The channels outlast a drop, so the reliable one closes when the other phone closed its
                    // connection: it gave up reconnecting, or its app went away. No restart can open it again.
                    onLinkLost()
                } else {
                    // The realtime channel can also close alone, on this phone (WebRTC closes a channel whose send
                    // failed). It carries only steps and pings, which go on the reliable channel from now on.
                    Log.w(TAG, "Realtime channel lost; its messages go on the reliable channel")
                    realtimeLost = true
                }
            }

            is PeerEvent.Message -> onPeerMessage(event.text)

            is PeerEvent.BufferedAmount -> if (event.channel == Channel.RELIABLE) {
                drainReliable()
                bulkTicks.update { it + 1 }
            }
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
            haveReferenceId = if (role == Role.SUBJECT) haveReferenceId() else null,
        )
        peer?.send(Channel.RELIABLE, codec.encode(hello))
    }

    /** Connected once both channels are open and the other phone said hello, which carries its name (Design 2.4). */
    private fun maybeConnected() {
        val hello = peerHello ?: return
        if (_state.value !is SessionState.Connecting || openChannels.size != 2) return
        connectTimeout?.cancel()
        connectTimeout = null
        _state.value = SessionState.Connected(checkNotNull(sessionId), peerInfo(hello), checkNotNull(role))
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

    /**
     * The link dropped while connected: both phones try for [RECONNECT_BUDGET_MS] to restore it (Design 2.6.7, NFR-11).
     * The socket is opened again even if it looks open, since one on a network without internet would take OkHttp's
     * pings longer than the budget to fail; its `session.join` answer starts the photographer's ICE restart.
     */
    private fun startReconnecting() {
        val id = sessionId ?: return
        val since = clock()
        _state.value = SessionState.Reconnecting(id, since)
        Timings.mark("session.reconnecting")
        reconnectBudget?.cancel()
        reconnectBudget = scope.launch {
            delay(RECONNECT_BUDGET_MS)
            Log.w(TAG, "Not reconnected after ${RECONNECT_BUDGET_MS / 1000} s")
            onLinkLost()
        }
        when {
            rejoinToken != null -> {
                socketFailures = 0
                reopenSignaling()
            }

            // A server without rejoin tokens: restart over the socket as it is.
            role == Role.PHOTOGRAPHER -> restartIce()
        }
    }

    /**
     * Reconnecting ends once the link is connected again with the reliable channel still open, as an ICE restart keeps
     * the channels; the realtime one may have been lost before (`realtimeLost`).
     */
    private fun maybeReconnected() {
        val state = _state.value as? SessionState.Reconnecting ?: return
        val hello = peerHello ?: return
        if (Channel.RELIABLE !in openChannels) return
        reconnectBudget?.cancel()
        reconnectBudget = null
        _state.value = SessionState.Connected(state.sessionId, peerInfo(hello), checkNotNull(role))
        Timings.mark("session.reconnected", "${clock() - state.since} ms")
    }

    private fun peerInfo(hello: SessionMessage.Hello) = PeerInfo(hello.name, hello.haveGuideId, hello.haveReferenceId)

    /**
     * One `stats` line per ping: codec, size, fps, bitrate, and RTT, so NFR-5, 6, and 12 can be read from logcat. An
     * `ice.pair` line whenever the candidate pair changes says whether the video goes direct or through the relay, and
     * over which network (Design 2.6.7, NFR-10).
     */
    private suspend fun logStats() {
        val client = peer ?: return
        val stats = runCatching { client.stats() }.getOrNull() ?: return
        Timings.mark("stats", stats.summary())
        val path = stats.path
        if (path != null && path != _path.value) {
            _path.value = path
            Timings.mark("ice.pair", path.summary())
        }
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

            // Each phone's Timings clock starts at its own boot. These two lines, matched by `ts` across the two
            // logs, give the receiver's clock minus the sender's: t(ping.received) − t(rtt) + rtt/2, within ±rtt/2.
            // Cross-phone latencies such as guide.sent → guide.applied (NFR-7) need that offset.
            is SessionMessage.Ping -> {
                Timings.mark("ping.received", "ts=${message.ts}")
                sendNow(SessionMessage.Pong(message.ts))
            }

            is SessionMessage.Pong -> Timings.mark("rtt", "${clock() - message.ts} ms ts=${message.ts}")

            is SessionMessage.Leave -> {
                _incoming.tryEmit(message)
                onPeerGone(message.reason)
            }

            is SessionMessage.Capabilities -> {
                _peerCapabilities.value = message.capabilities
                _incoming.tryEmit(message)
            }

            is SessionMessage.CameraStateUpdate -> {
                _peerCamera.value = CameraState(message.zoom, message.exposure, message.flash, message.aspect)
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
        val routed = router.channelFor(message)
        val channel = if (routed == Channel.REALTIME && realtimeLost) Channel.RELIABLE else routed
        return when (channel) {
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

    /**
     * The server says the other phone left, or that its socket dropped. While the link runs or is being restored, a
     * dropped socket is often only the other phone's old one going: the link decides (Design 2.6.7).
     */
    private fun onPeerLeft(reason: EndReason) {
        val state = _state.value
        if (reason == EndReason.CONNECTION_LOST &&
            (state is SessionState.Connected || state is SessionState.Reconnecting)
        ) {
            Log.d(TAG, "The other phone's socket dropped; the link decides")
            return
        }
        onPeerGone(reason)
    }

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
                withoutSubject(if (onPurpose) EndReason.PEER_LEFT else EndReason.CONNECTION_LOST)
            }

            Role.SUBJECT -> end(if (onPurpose) EndReason.PEER_LEFT else EndReason.CONNECTION_LOST)

            null -> Unit
        }
    }

    /** The peer connection failed or closed, or the connecting or reconnecting timer ran out. */
    private fun onLinkLost() {
        when (role) {
            Role.PHOTOGRAPHER -> {
                notifyPeerGone(EndReason.CONNECTION_LOST)
                closePeer()
                withoutSubject(EndReason.CONNECTION_LOST)
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

    /**
     * The photographer without a subject: a room code's room waits for one again with the same code (FR-6.10); an
     * invitation's session has no code anyone could join, so it leaves the server and ends (Design 2.4).
     */
    private fun withoutSubject(reason: EndReason) {
        if (!active) return
        val waitingCode = code
        if (waitingCode != null) {
            _state.value = SessionState.Waiting(waitingCode)
            return
        }
        signaling.send(SignalMessage.Leave)
        end(reason)
    }

    /** An invitation still waiting after the server's 60 s and the friend's time to join has expired (Design 2.6.6). */
    private fun startInviteTimeout() {
        inviteTimeout?.cancel()
        inviteTimeout = scope.launch {
            delay(INVITE_TIMEOUT_MS)
            if (_state.value is SessionState.Inviting) {
                Log.w(TAG, "No answer to the invitation after ${INVITE_TIMEOUT_MS / 1000} s")
                signaling.send(SignalMessage.Leave)
                end(EndReason.EXPIRED)
            }
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

    private fun closePeer() {
        val client = peer ?: return
        peer = null
        peerJob?.cancel()
        peerJob = null
        pingJob?.cancel()
        pingJob = null
        connectTimeout?.cancel()
        connectTimeout = null
        reconnectBudget?.cancel()
        reconnectBudget = null
        remoteDescriptionSet = false
        pendingCandidates.clear()
        offerPending = false
        answerWait?.cancel()
        answerWait = null
        restartQueued = false
        holdingCandidates = false
        heldCandidates.clear()
        openChannels.clear()
        realtimeLost = false
        helloSent = false
        peerHello = null
        lastSeq.clear()
        reliableQueue.clear()
        _path.value = null
        client.close()
    }

    private fun end(reason: EndReason) {
        if (!active) return
        active = false
        // What the subject's Reconnect needs to take its place again while the photographer still holds it.
        val id = sessionId
        val token = rejoinToken
        val lostCode = code
        lost = when {
            reason == EndReason.CONNECTION_LOST && role == Role.SUBJECT && id != null && token != null &&
                lostCode != null -> LostSession(lostCode, id, token, iceServers)

            // A Reconnect that failed without an answer, such as one tapped before the network is back, keeps its
            // ticket: the next try still takes the place with the token instead of joining the code, which a server
            // still holding the old socket would refuse as FULL. A refusal (NOT_FOUND) or Leave drops it.
            reason == EndReason.ERROR -> ticket

            else -> null
        }
        ticket = null
        inviteTimeout?.cancel()
        inviteTimeout = null
        closePeer()
        signalingJob?.cancel()
        signalingJob = null
        socketRetry?.cancel()
        socketRetry = null
        signaling.close()
        role = null
        code = null
        sessionId = null
        rejoinToken = null
        greeting = null
        _state.value = SessionState.Ended(reason)
        Timings.mark("session.ended", reason.name)
    }

    /** Subject: a session it lost by its connection, with what a Reconnect needs to take its place again. */
    private class LostSession(
        val code: String,
        val sessionId: String,
        val token: String,
        val iceServers: List<IceServer>,
    )

    private companion object {
        const val TAG = "PixSession"
        const val CONNECT_TIMEOUT_MS = 15_000L

        /** The server's 60 s for an answer, its 15 s for an accepting friend to join, and a margin (Design 2.6.6). */
        const val INVITE_TIMEOUT_MS = 80_000L

        /** How long a restart waits for the answer to the last offer, about ten round trips through the server. */
        const val ANSWER_WAIT_MS = 2_000L

        /** How long both phones try to restore a dropped link before giving up (FR-6.12, NFR-11). */
        const val RECONNECT_BUDGET_MS = 10_000L

        /** A dropped socket is opened again after 1 s, every second while reconnecting, and otherwise doubling to 30 s. */
        const val SOCKET_RETRY_MS = 1_000L
        const val SOCKET_RETRY_MAX_MS = 30_000L
        const val SOCKET_RETRY_DOUBLINGS = 5

        /** The answers to `session.join` that mean the place is gone. */
        val REJOIN_REFUSALS = setOf("NOT_FOUND", "FORBIDDEN")
        const val PING_INTERVAL_MS = 2_000L
        const val LEAVE_FLUSH_MS = 150L
        const val BACKPRESSURE_BYTES = 256L * 1024

        /** A bulk chunk waits below this, so that a control message sent next waits for this much at most. */
        const val BULK_BYTES = 64L * 1024
    }
}
