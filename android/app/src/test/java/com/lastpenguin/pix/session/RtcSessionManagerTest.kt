// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.signaling.IceCandidate
import com.lastpenguin.pix.session.signaling.IceServer
import com.lastpenguin.pix.session.signaling.SessionDescription
import com.lastpenguin.pix.session.signaling.SignalMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

@OptIn(ExperimentalCoroutinesApi::class)
class RtcSessionManagerTest {

    private val signaling = FakeSignalingClient()
    private val peers = FakePeerFactory()

    /** Encodes what the other phone would send, with its own seq counters. */
    private val otherPhone = MessageCodec()
    private val reader = MessageCodec()

    private val incoming = mutableListOf<SessionMessage>()

    private fun TestScope.manager(
        haveGuideId: () -> String? = { null },
        haveReferenceId: () -> String? = { null },
    ): RtcSessionManager {
        val manager = RtcSessionManager(
            signaling = signaling,
            peers = peers,
            frameSource = CameraFrameSource(),
            codec = MessageCodec(),
            router = ChannelRouter(),
            serverUrl = "http://10.0.2.2:8000/",
            identity = SessionIdentity("Dongje", "0.1.0"),
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = { 0L },
            haveGuideId = haveGuideId,
            haveReferenceId = haveReferenceId,
        )
        backgroundScope.launch { manager.incoming.collect { incoming += it } }
        runCurrent()
        return manager
    }

    private fun FakePeerConnectionClient.sentTypes(): List<String> = sent.mapNotNull { reader.decode(it.second)?.type }

    private fun FakePeerConnectionClient.sentMessages(): List<SessionMessage> =
        sent.mapNotNull { reader.decode(it.second)?.message }

    private fun hello(role: Role, name: String, haveGuideId: String? = null, haveReferenceId: String? = null) =
        otherPhone.encode(
            SessionMessage.Hello(
                appVersion = "0.1.0",
                role = role,
                name = name,
                haveGuideId = haveGuideId,
                haveReferenceId = haveReferenceId,
            ),
        )

    /**
     * Photographer: start, get a code, and have a subject join and connect. A test that ends while connected must
     * call `leave`, because runTest drains the virtual clock at the end and the ping loop would never let it finish.
     * Without a [token] the server is one that gives none.
     */
    private suspend fun TestScope.connectedPhotographer(
        token: String? = null,
    ): Pair<RtcSessionManager, FakePeerConnectionClient> {
        val manager = manager()
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_1", rejoinToken = token))
        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()
        val peer = peers.created.last()
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer")))
        peer.open()
        peer.deliver(hello(Role.SUBJECT, "Junhyeong"))
        runCurrent()
        assertEquals(SessionState.Connected("s_1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER), manager.state.value)
        return manager to peer
    }

    /** Subject: join with the code, answer the offer, and connect; [token] as for [connectedPhotographer]. */
    private suspend fun TestScope.connectedSubject(
        token: String? = null,
    ): Pair<RtcSessionManager, FakePeerConnectionClient> {
        val manager = manager()
        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT, iceServers = relay, rejoinToken = token))
        runCurrent()
        val peer = peers.created.last()
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("offer", "v=0 offer")))
        peer.open()
        peer.deliver(hello(Role.PHOTOGRAPHER, "Dongje"))
        runCurrent()
        assertEquals(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT), manager.state.value)
        return manager to peer
    }

    /** The `session.join` of a socket opened again during the session. */
    private fun rejoin(token: String) = SignalMessage.SessionJoin("s_1", rejoin = true, rejoinToken = token)

    /** The SDP types of the signals sent so far, with `candidate` for a candidate. */
    private fun sentSignals(): List<String> =
        signaling.sent.filterIsInstance<SignalMessage.Signal>().map { it.sdp?.type ?: "candidate" }

    /** The link drops, and the new socket takes the place back: its `session.join` is answered. */
    private fun TestScope.dropAndRejoin(peer: FakePeerConnectionClient, role: Role) {
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", role))
        runCurrent()
    }

    @Test
    fun `signaling url is derived from the server url`() {
        assertEquals("ws://10.0.2.2:8000/ws", signalingUrl("http://10.0.2.2:8000/"))
        assertEquals("ws://192.168.0.10:8000/ws", signalingUrl("http://192.168.0.10:8000"))
        assertEquals("wss://pix.example.com/ws", signalingUrl("https://pix.example.com/"))
    }

    private val relay = listOf(
        IceServer(listOf("stun:stun.cloudflare.com:3478")),
        IceServer(listOf("turn:turn.cloudflare.com:3478?transport=udp"), "user", "secret"),
    )

    @Test
    fun `photographer's peer connection gets the ice servers of room created`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_1", relay))
        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()

        assertEquals(PeerConfig(Role.PHOTOGRAPHER, relay), peers.created.single().config)
        manager.leave(EndReason.LEFT)
        runCurrent()
    }

    @Test
    fun `too many wrong codes from this network end the subject's join with RATE_LIMITED`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.ServerError("RATE_LIMITED", "Too many wrong codes from this network"))
        runCurrent()

        assertEquals(SessionState.Ended(EndReason.RATE_LIMITED), manager.state.value)
        assertTrue(peers.created.isEmpty())
    }

    @Test
    fun `path follows the link's statistics and is cleared when the session ends`() = runTest {
        val (manager, peer) = connectedPhotographer()
        assertNull(manager.path.value)

        val relayed = IcePath("relay", "srflx", "cellular", "udp")
        peer.stats = LinkStats("video/H264", 960, 720, 30.0, 2000.0, 40.0, relayed)
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(relayed, manager.path.value)

        manager.leave(EndReason.LEFT)
        runCurrent()
        assertNull(manager.path.value)
    }

    @Test
    fun `subject's peer connection gets the ice servers of session joined`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT, iceServers = relay))
        runCurrent()

        assertEquals(PeerConfig(Role.SUBJECT, relay), peers.created.single().config)
        manager.leave(EndReason.LEFT)
        runCurrent()
    }

    @Test
    fun `photographer creates a room, offers, says hello, and ends the session`() = runTest {
        val manager = manager()

        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        assertEquals(listOf("ws://10.0.2.2:8000/ws"), signaling.connectedUrls)
        assertEquals(SessionState.Idle, manager.state.value)

        signaling.push(SignalEvent.Opened)
        runCurrent()
        assertEquals(listOf<SignalMessage>(SignalMessage.RoomCreate), signaling.sent)

        signaling.receive(SignalMessage.RoomCreated("482915", "s_1"))
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
        assertTrue(peers.created.isEmpty())

        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()
        assertEquals(SessionState.Connecting("s_1"), manager.state.value)
        val peer = peers.created.single()
        assertEquals(Role.PHOTOGRAPHER, peer.config.role)
        val offer = signaling.sent.filterIsInstance<SignalMessage.Signal>().single().sdp
        assertEquals("offer", offer?.type)

        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer")))
        runCurrent()
        assertEquals("answer", peer.remote.single().type)

        peer.push(PeerEvent.LocalCandidate(IceCandidate("0", 0, "candidate:local")))
        runCurrent()
        assertTrue(
            signaling.sent.filterIsInstance<SignalMessage.Signal>().any {
                it.candidate?.candidate ==
                    "candidate:local"
            },
        )

        // A reliable message sent before the channels open waits, and goes out right after hello.
        manager.send(SessionMessage.GuideClear)
        runCurrent()
        assertTrue(peer.sent.isEmpty())

        peer.open()
        runCurrent()
        assertEquals(listOf("hello", "guide.clear"), peer.sentTypes())
        val hello = peer.sentMessages().first() as SessionMessage.Hello
        assertEquals(Channel.RELIABLE, peer.sent.first().first)
        assertEquals("Dongje", hello.name)
        assertEquals(Role.PHOTOGRAPHER, hello.role)
        assertEquals(SessionState.Connecting("s_1"), manager.state.value)

        peer.deliver(hello(Role.SUBJECT, "Junhyeong"))
        runCurrent()
        assertEquals(SessionState.Connected("s_1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER), manager.state.value)

        peer.stats = LinkStats("video/H264", 720, 960, 29.8, 2100.0, 12.0)
        advanceTimeBy(2_100)
        runCurrent()
        assertTrue("ping" in peer.sentTypes())
        assertEquals(1, peer.statsRequests)

        manager.leave(EndReason.LEFT)
        runCurrent()
        assertEquals(SessionMessage.Leave(EndReason.LEFT), peer.sentMessages().last())
        assertEquals(SignalMessage.Leave, signaling.sent.last())
        assertEquals(SessionState.Ended(EndReason.LEFT), manager.state.value)
        assertTrue(peer.closed)
        assertEquals(1, signaling.closeCount)
    }

    @Test
    fun `subject joins, answers the offer, connects, and sees the photographer leave`() = runTest {
        val manager = manager()

        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        runCurrent()
        assertEquals(SessionState.Connecting(null), manager.state.value)

        signaling.push(SignalEvent.Opened)
        runCurrent()
        assertEquals(listOf<SignalMessage>(SignalMessage.RoomJoin("482915")), signaling.sent)

        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT))
        runCurrent()
        assertEquals(SessionState.Connecting("s_1"), manager.state.value)
        val peer = peers.created.single()
        assertEquals(Role.SUBJECT, peer.config.role)

        // A candidate that arrives before the offer waits for the remote description.
        signaling.receive(SignalMessage.Signal(candidate = IceCandidate("0", 0, "candidate:early")))
        runCurrent()
        assertTrue(peer.candidates.isEmpty())

        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("offer", "v=0 offer")))
        runCurrent()
        assertEquals("offer", peer.remote.single().type)
        assertEquals("candidate:early", peer.candidates.single().candidate)
        assertEquals("answer", signaling.sent.filterIsInstance<SignalMessage.Signal>().single().sdp?.type)

        val sink = VideoSink { _: VideoFrame -> }
        manager.attach(sink)
        assertEquals(listOf<VideoSink>(sink), peer.sinks)

        peer.open()
        peer.deliver(hello(Role.PHOTOGRAPHER, "Dongje"))
        runCurrent()
        assertEquals(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT), manager.state.value)
        assertTrue(incoming.any { it is SessionMessage.Hello })

        peer.deliver(otherPhone.encode(SessionMessage.Leave(EndReason.LEFT)))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.PEER_LEFT), manager.state.value)
        assertTrue(peer.closed)
        assertEquals(1, signaling.closeCount)
        assertEquals(SessionMessage.Leave(EndReason.LEFT), incoming.last())
    }

    @Test
    fun `the subject says which guide image and reference it has, and the photographer learns both`() = runTest {
        val subject = manager(haveGuideId = { "g1" }, haveReferenceId = { "g1" })
        subject.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT))
        runCurrent()
        val peer = peers.created.single()
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("offer", "v=0 offer")))
        runCurrent()
        peer.open()
        runCurrent()
        val sent = peer.sentMessages().first() as SessionMessage.Hello
        assertEquals("g1", sent.haveGuideId)
        assertEquals("g1", sent.haveReferenceId)
        subject.leave(EndReason.LEFT)
        runCurrent()

        // The photographer reads it from the subject's hello.
        peers.created.clear()
        signaling.sent.clear()
        val photographer = manager(
            haveGuideId = { "ignored on the photographer" },
            haveReferenceId = { "ignored on the photographer" },
        )
        photographer.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_2"))
        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()
        val photographerPeer = peers.created.last()
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer")))
        photographerPeer.open()
        runCurrent()
        val photographerHello = photographerPeer.sentMessages().first() as SessionMessage.Hello
        assertEquals(null, photographerHello.haveGuideId)
        assertEquals(null, photographerHello.haveReferenceId)
        photographerPeer.deliver(hello(Role.SUBJECT, "Junhyeong", haveGuideId = "g1", haveReferenceId = "g1"))
        runCurrent()
        assertEquals(
            SessionState.Connected(
                "s_2",
                PeerInfo("Junhyeong", haveGuideId = "g1", haveReferenceId = "g1"),
                Role.PHOTOGRAPHER,
            ),
            photographer.state.value,
        )
        photographer.leave(EndReason.LEFT)
    }

    @Test
    fun `an unknown code ends with NOT_FOUND`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.RoomCode("000000"), Role.SUBJECT)
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.ServerError("NOT_FOUND", "No active room with this code"))
        runCurrent()

        assertEquals(SessionState.Ended(EndReason.NOT_FOUND), manager.state.value)
        assertEquals(1, signaling.closeCount)
        assertTrue(peers.created.isEmpty())
    }

    @Test
    fun `a signaling failure before joining ends with ERROR`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        signaling.push(SignalEvent.Closed(RuntimeException("connection refused")))
        runCurrent()

        assertEquals(SessionState.Ended(EndReason.ERROR), manager.state.value)
    }

    @Test
    fun `the subject gives up after the connecting timeout`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT))
        runCurrent()

        advanceTimeBy(14_000)
        runCurrent()
        assertEquals(SessionState.Connecting("s_1"), manager.state.value)

        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.CONNECTION_LOST), manager.state.value)
        assertTrue(peers.created.single().closed)
    }

    @Test
    fun `the photographer keeps the room when the subject drops, and a new subject can join`() = runTest {
        val (manager, peer) = connectedPhotographer()

        peer.push(PeerEvent.Link(LinkState.FAILED))
        runCurrent()
        assertTrue(manager.state.value is SessionState.Reconnecting)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
        assertTrue(peer.closed)
        assertEquals(SessionMessage.Leave(EndReason.CONNECTION_LOST), incoming.last())
        assertEquals(0, signaling.closeCount)

        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()
        assertEquals(SessionState.Connecting("s_1"), manager.state.value)
        assertEquals(2, peers.created.size)
        assertEquals(0, peers.created.last().restarts)
        assertEquals("offer", sentSignals().last())
    }

    @Test
    fun `a link back within 10 s ends Reconnecting, and one that stays down ends the session`() = runTest {
        val (manager, peer) = connectedPhotographer()

        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        advanceTimeBy(5_000)
        runCurrent()
        peer.push(PeerEvent.Link(LinkState.CONNECTED))
        advanceTimeBy(6_000)
        runCurrent()
        assertTrue(manager.state.value is SessionState.Connected)

        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        advanceTimeBy(10_100)
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
    }

    @Test
    fun `the server telling the photographer that the subject left is enough`() = runTest {
        val (manager, peer) = connectedPhotographer()

        signaling.receive(SignalMessage.PeerLeft(EndReason.PEER_LEFT))
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
        assertTrue(peer.closed)
        assertEquals(SessionMessage.Leave(EndReason.PEER_LEFT), incoming.last())
    }

    @Test
    fun `a subject leaving on the data channel is reported once`() = runTest {
        val (manager, peer) = connectedPhotographer()

        peer.deliver(otherPhone.encode(SessionMessage.Leave(EndReason.LEFT)))
        signaling.receive(SignalMessage.PeerLeft(EndReason.PEER_LEFT))
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
        assertEquals(1, incoming.count { it is SessionMessage.Leave })
    }

    @Test
    fun `stale realtime values are dropped`() = runTest {
        val (manager, peer) = connectedPhotographer()
        val first = otherPhone.encode(SessionMessage.ZoomSet(1f))
        val second = otherPhone.encode(SessionMessage.ZoomSet(2f))
        val third = otherPhone.encode(SessionMessage.ZoomSet(3f))

        peer.deliver(first, Channel.REALTIME)
        peer.deliver(third, Channel.REALTIME)
        peer.deliver(second, Channel.REALTIME)
        runCurrent()

        assertEquals(listOf(1f, 3f), incoming.filterIsInstance<SessionMessage.ZoomSet>().map { it.ratio })
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `pings are answered with pongs`() = runTest {
        val (manager, peer) = connectedPhotographer()

        peer.deliver(otherPhone.encode(SessionMessage.Ping(123)), Channel.REALTIME)
        runCurrent()

        assertEquals(SessionMessage.Pong(123), peer.sentMessages().last())
        assertEquals(Channel.REALTIME, peer.sent.last().first)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `reliable messages wait while the channel buffer is full`() = runTest {
        val (manager, peer) = connectedPhotographer()
        peer.buffered = 300L * 1024

        manager.send(SessionMessage.GuideClear)
        runCurrent()
        assertFalse("guide.clear" in peer.sentTypes())

        peer.buffered = 0
        peer.push(PeerEvent.BufferedAmount(Channel.RELIABLE, 0))
        runCurrent()
        assertTrue("guide.clear" in peer.sentTypes())
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `without a rejoin token, losing the signaling socket does not end a running session`() = runTest {
        val (manager, _) = connectedPhotographer()

        signaling.push(SignalEvent.Closed(null))
        runCurrent()

        assertTrue(manager.state.value is SessionState.Connected)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `cancelling while waiting leaves the room`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_1"))
        runCurrent()

        manager.leave(EndReason.CANCELLED)
        runCurrent()

        assertEquals(SignalMessage.Leave, signaling.sent.last())
        assertEquals(SessionState.Ended(EndReason.CANCELLED), manager.state.value)
        assertEquals(1, signaling.closeCount)
    }

    @Test
    fun `a second start is ignored while a session is active, and allowed after it ended`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        assertEquals(1, signaling.connectedUrls.size)

        manager.leave(EndReason.CANCELLED)
        manager.start(SessionEntry.RoomCode("482915"), Role.SUBJECT)
        runCurrent()
        assertEquals(2, signaling.connectedUrls.size)
        assertEquals(SessionState.Connecting(null), manager.state.value)
    }

    // ---- Reconnection (Design 2.6.7) -----------------------------------------------------------------------------

    @Test
    fun `a dropped link puts the photographer in Reconnecting, takes its place again, and restarts ICE`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")

        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        assertEquals(SessionState.Reconnecting("s_1", 0L), manager.state.value)
        // A new socket, even though the old one looked open.
        assertEquals(2, signaling.connectedUrls.size)

        signaling.push(SignalEvent.Opened)
        runCurrent()
        assertEquals(rejoin("photographer-token"), signaling.sent.last())
        assertEquals(0, peer.restarts)

        signaling.receive(SignalMessage.SessionJoined("s_1", Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(1, peer.restarts)
        assertEquals("offer", sentSignals().last())

        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 restart answer")))
        peer.push(PeerEvent.Link(LinkState.CONNECTED))
        runCurrent()
        assertEquals("v=0 restart answer", peer.remote.last().sdp)
        assertEquals(SessionState.Connected("s_1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER), manager.state.value)
        // The same connection: its channels and video track stayed.
        assertEquals(1, peers.created.size)
        assertFalse(peer.closed)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `the subject coming back gets an ICE restart, also when this phone saw no drop`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")

        signaling.receive(SignalMessage.PeerRejoined)
        runCurrent()
        assertEquals(1, peer.restarts)
        assertTrue(manager.state.value is SessionState.Connected)
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer 1")))

        dropAndRejoin(peer, Role.PHOTOGRAPHER)
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer 2")))
        signaling.receive(SignalMessage.PeerRejoined)
        runCurrent()
        // One for its own place back, one for the subject's.
        assertEquals(3, peer.restarts)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `a restart asked for while an offer waits goes once it is answered, or after 2 s`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")
        dropAndRejoin(peer, Role.PHOTOGRAPHER)
        assertEquals(1, peer.restarts)

        // The subject's place is back too before that offer is answered: the second restart waits for the answer,
        // which would otherwise be taken for its own and leave the phones with different ICE credentials.
        signaling.receive(SignalMessage.PeerRejoined)
        runCurrent()
        assertEquals(1, peer.restarts)
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer 1")))
        runCurrent()
        assertEquals("v=0 answer 1", peer.remote.last().sdp)
        assertEquals(2, peer.restarts)
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer 2")))
        runCurrent()
        assertEquals("v=0 answer 2", peer.remote.last().sdp)

        // An offer never answered, as one sent to the subject's old socket, holds the next restart for 2 s only.
        signaling.receive(SignalMessage.PeerRejoined)
        runCurrent()
        assertEquals(3, peer.restarts)
        signaling.receive(SignalMessage.PeerRejoined)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(3, peer.restarts)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(4, peer.restarts)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `the subject takes its place again and answers the restart on the same connection`() = runTest {
        val (manager, peer) = connectedSubject(token = "subject-token")

        dropAndRejoin(peer, Role.SUBJECT)
        assertEquals(rejoin("subject-token"), signaling.sent.last())
        assertTrue(manager.state.value is SessionState.Reconnecting)

        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("offer", "v=0 restart offer")))
        runCurrent()
        assertEquals("v=0 restart offer", peer.remote.last().sdp)
        assertEquals(listOf("answer", "answer"), sentSignals())

        peer.push(PeerEvent.Link(LinkState.CONNECTED))
        runCurrent()
        assertEquals(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT), manager.state.value)
        assertEquals(1, peers.created.size)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `the other phone's dropped socket does not end a running or reconnecting session`() = runTest {
        val (manager, peer) = connectedSubject(token = "subject-token")

        signaling.receive(SignalMessage.PeerLeft(EndReason.CONNECTION_LOST))
        runCurrent()
        assertTrue(manager.state.value is SessionState.Connected)

        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        signaling.receive(SignalMessage.PeerLeft(EndReason.CONNECTION_LOST))
        runCurrent()
        assertTrue(manager.state.value is SessionState.Reconnecting)
        assertFalse(peer.closed)

        // Leaving on purpose still ends it.
        signaling.receive(SignalMessage.PeerLeft(EndReason.PEER_LEFT))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.PEER_LEFT), manager.state.value)
    }

    @Test
    fun `the photographer gives up after 10 s of reconnecting and keeps the room`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")

        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        advanceTimeBy(9_999)
        runCurrent()
        assertTrue(manager.state.value is SessionState.Reconnecting)
        assertTrue(incoming.none { it is SessionMessage.Leave })

        advanceTimeBy(2)
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
        assertTrue(peer.closed)
        // "Junhyeong disconnected" comes only now.
        assertEquals(SessionMessage.Leave(EndReason.CONNECTION_LOST), incoming.last())
    }

    @Test
    fun `after 10 s the subject's Reconnect takes its place again with its token and the first credentials`() =
        runTest {
            val (manager, peer) = connectedSubject(token = "subject-token")
            peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
            advanceTimeBy(10_001)
            runCurrent()
            assertEquals(SessionState.Ended(EndReason.CONNECTION_LOST), manager.state.value)

            manager.start(SessionEntry.Rejoin("482915"), Role.SUBJECT)
            runCurrent()
            assertEquals(SessionState.Connecting(null), manager.state.value)
            signaling.push(SignalEvent.Opened)
            runCurrent()
            assertEquals(
                SignalMessage.SessionJoin("s_1", rejoin = false, rejoinToken = "subject-token"),
                signaling.sent.last(),
            )

            signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT))
            runCurrent()
            assertEquals(SessionState.Connecting("s_1"), manager.state.value)
            assertEquals(PeerConfig(Role.SUBJECT, relay), peers.created.last().config)
            manager.leave(EndReason.LEFT)
        }

    @Test
    fun `a Reconnect that fails before the server answers keeps its token for the next one`() = runTest {
        val (manager, peer) = connectedSubject(token = "subject-token")
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        advanceTimeBy(10_001)
        runCurrent()

        // Tapped before the network is back: the socket fails before it opens.
        manager.start(SessionEntry.Rejoin("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Closed(RuntimeException("no network")))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.ERROR), manager.state.value)

        manager.start(SessionEntry.Rejoin("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        runCurrent()
        assertEquals(
            SignalMessage.SessionJoin("s_1", rejoin = false, rejoinToken = "subject-token"),
            signaling.sent.last(),
        )

        // A refusal drops it: the next try joins the code.
        signaling.receive(SignalMessage.ServerError("FORBIDDEN", "This phone has no place in the session"))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.NOT_FOUND), manager.state.value)
        manager.start(SessionEntry.Rejoin("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        runCurrent()
        assertEquals(SignalMessage.RoomJoin("482915"), signaling.sent.last())
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `a subject waiting for an offer takes its place again when the photographer is back`() = runTest {
        val (manager, peer) = connectedSubject(token = "subject-token")
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        advanceTimeBy(10_001)
        runCurrent()
        manager.start(SessionEntry.Rejoin("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT))
        runCurrent()
        val created = peers.created.size
        val sentBefore = signaling.sent.size

        // The photographer was off the server, so the `peer.joined` for this Reconnect went nowhere and no offer comes.
        signaling.receive(SignalMessage.PeerRejoined)
        runCurrent()
        assertEquals(
            listOf(SignalMessage.SessionJoin("s_1", rejoin = false, rejoinToken = "subject-token")),
            signaling.sent.drop(sentBefore),
        )
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.SUBJECT))
        runCurrent()
        assertEquals(created, peers.created.size)
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("offer", "v=0 offer")))
        runCurrent()
        assertEquals("answer", sentSignals().last())

        // With the offer in, the photographer's next return needs nothing from this phone.
        val sent = signaling.sent.size
        signaling.receive(SignalMessage.PeerRejoined)
        runCurrent()
        assertEquals(sent, signaling.sent.size)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `a Reconnect without a token joins the code again`() = runTest {
        val (manager, peer) = connectedSubject()
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        // Without a token there is no place to take again, so no new socket.
        assertEquals(1, signaling.connectedUrls.size)
        advanceTimeBy(10_001)
        runCurrent()

        manager.start(SessionEntry.Rejoin("482915"), Role.SUBJECT)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        runCurrent()
        assertEquals(SignalMessage.RoomJoin("482915"), signaling.sent.last())
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `a closed data channel means the other phone closed its connection`() = runTest {
        val (manager, peer) = connectedSubject(token = "subject-token")

        peer.push(PeerEvent.ChannelClosed(Channel.RELIABLE))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.CONNECTION_LOST), manager.state.value)
        assertTrue(peer.closed)
    }

    @Test
    fun `a realtime channel that closes alone keeps the session, and its messages go reliably`() = runTest {
        val (manager, peer) = connectedSubject(token = "subject-token")

        peer.push(PeerEvent.ChannelClosed(Channel.REALTIME))
        runCurrent()
        assertTrue(manager.state.value is SessionState.Connected)
        assertFalse(peer.closed)
        manager.send(SessionMessage.ZoomSet(2f))
        runCurrent()
        assertEquals(Channel.RELIABLE, peer.sent.last().first)
        assertEquals(SessionMessage.ZoomSet(2f), peer.sentMessages().last())

        // A drop after that still ends once the link is back, with the reliable channel alone.
        dropAndRejoin(peer, Role.SUBJECT)
        assertTrue(manager.state.value is SessionState.Reconnecting)
        peer.push(PeerEvent.Link(LinkState.CONNECTED))
        runCurrent()
        assertTrue(manager.state.value is SessionState.Connected)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `an answer to no pending offer is ignored`() = runTest {
        val (manager, peer) = connectedPhotographer()

        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 stale answer")))
        runCurrent()
        assertEquals(1, peer.remote.size)
        assertTrue(manager.state.value is SessionState.Connected)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `candidates of a restart go out after its offer, and the subject's wait for its answer`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        // While the new socket has no place, nothing is signaled.
        peer.push(PeerEvent.LocalCandidate(IceCandidate("0", 0, "candidate:too early")))
        runCurrent()
        assertTrue(signaling.sent.filterIsInstance<SignalMessage.Signal>().none { it.candidate != null })

        peer.candidatesWhileCreating = listOf(IceCandidate("0", 0, "candidate:new"))
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(listOf("offer", "offer", "candidate"), sentSignals())

        signaling.receive(SignalMessage.Signal(candidate = IceCandidate("0", 0, "candidate:subject")))
        runCurrent()
        assertTrue(peer.candidates.none { it.candidate == "candidate:subject" })
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 restart answer")))
        runCurrent()
        assertEquals("candidate:subject", peer.candidates.last().candidate)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `a refused rejoin ends a reconnecting session`() = runTest {
        val (subject, subjectPeer) = connectedSubject(token = "subject-token")
        subjectPeer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.ServerError("FORBIDDEN", "This phone has no place in the session"))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.NOT_FOUND), subject.state.value)

        val (photographer, peer) = connectedPhotographer(token = "photographer-token")
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.ServerError("NOT_FOUND", "This session has ended"))
        runCurrent()
        assertEquals(SessionState.Ended(EndReason.CONNECTION_LOST), photographer.state.value)
        assertEquals(SessionMessage.Leave(EndReason.CONNECTION_LOST), incoming.last())
    }

    @Test
    fun `a socket that drops while connected is opened again, sooner while reconnecting`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")

        signaling.push(SignalEvent.Closed(RuntimeException("server restarted")))
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, signaling.connectedUrls.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, signaling.connectedUrls.size)

        // Failing again waits twice as long while the session runs.
        signaling.push(SignalEvent.Closed(RuntimeException("still down")))
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, signaling.connectedUrls.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(3, signaling.connectedUrls.size)

        // Back in its place while the link runs: no ICE restart.
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(0, peer.restarts)
        assertTrue(manager.state.value is SessionState.Connected)

        // While reconnecting, every second.
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        assertEquals(4, signaling.connectedUrls.size)
        signaling.push(SignalEvent.Closed(RuntimeException("no network")))
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(5, signaling.connectedUrls.size)
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `after giving up, the socket is tried again from 1 s, not after the tries while reconnecting`() = runTest {
        val (manager, peer) = connectedPhotographer(token = "photographer-token")
        peer.push(PeerEvent.Link(LinkState.DISCONNECTED))
        runCurrent()
        // No network: five more sockets fail while it reconnects.
        repeat(5) {
            signaling.push(SignalEvent.Closed(RuntimeException("no network")))
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertEquals(7, signaling.connectedUrls.size)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(SessionState.Waiting("482915"), manager.state.value)

        signaling.push(SignalEvent.Closed(RuntimeException("no network")))
        advanceTimeBy(999)
        runCurrent()
        assertEquals(7, signaling.connectedUrls.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(8, signaling.connectedUrls.size)
        signaling.push(SignalEvent.Closed(RuntimeException("no network")))
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(9, signaling.connectedUrls.size)
    }

    @Test
    fun `a waiting room keeps its code when its socket drops`() = runTest {
        val manager = manager()
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_1", rejoinToken = "photographer-token"))
        runCurrent()

        signaling.push(SignalEvent.Closed(null))
        advanceTimeBy(1_001)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.SessionJoined("s_1", Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(rejoin("photographer-token"), signaling.sent.last())
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
    }
}
