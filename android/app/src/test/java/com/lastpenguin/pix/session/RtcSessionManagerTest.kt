// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.signaling.IceCandidate
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

    private fun TestScope.manager(haveGuideId: () -> String? = { null }): RtcSessionManager {
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
        )
        backgroundScope.launch { manager.incoming.collect { incoming += it } }
        runCurrent()
        return manager
    }

    private fun FakePeerConnectionClient.sentTypes(): List<String> = sent.mapNotNull { reader.decode(it.second)?.type }

    private fun FakePeerConnectionClient.sentMessages(): List<SessionMessage> =
        sent.mapNotNull { reader.decode(it.second)?.message }

    private fun hello(role: Role, name: String, haveGuideId: String? = null) =
        otherPhone.encode(
            SessionMessage.Hello(appVersion = "0.1.0", role = role, name = name, haveGuideId = haveGuideId),
        )

    /**
     * Photographer: start, get a code, and have a subject join and connect. A test that ends while connected must
     * call `leave`, because runTest drains the virtual clock at the end and the ping loop would never let it finish.
     */
    private suspend fun TestScope.connectedPhotographer(): Pair<RtcSessionManager, FakePeerConnectionClient> {
        val manager = manager()
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_1"))
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

    @Test
    fun `signaling url is derived from the server url`() {
        assertEquals("ws://10.0.2.2:8000/ws", signalingUrl("http://10.0.2.2:8000/"))
        assertEquals("ws://192.168.0.10:8000/ws", signalingUrl("http://192.168.0.10:8000"))
        assertEquals("wss://pix.example.com/ws", signalingUrl("https://pix.example.com/"))
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
    fun `the subject says which guide image it already has, and the photographer learns it`() = runTest {
        val subject = manager(haveGuideId = { "g1" })
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
        subject.leave(EndReason.LEFT)
        runCurrent()

        // The photographer reads it from the subject's hello.
        peers.created.clear()
        signaling.sent.clear()
        val photographer = manager(haveGuideId = { "ignored on the photographer" })
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
        assertEquals(null, (photographerPeer.sentMessages().first() as SessionMessage.Hello).haveGuideId)
        photographerPeer.deliver(hello(Role.SUBJECT, "Junhyeong", haveGuideId = "g1"))
        runCurrent()
        assertEquals(
            SessionState.Connected("s_2", PeerInfo("Junhyeong", haveGuideId = "g1"), Role.PHOTOGRAPHER),
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
        assertEquals(SessionState.Waiting("482915"), manager.state.value)
        assertTrue(peer.closed)
        assertEquals(SessionMessage.Leave(EndReason.CONNECTION_LOST), incoming.last())
        assertEquals(0, signaling.closeCount)

        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()
        assertEquals(SessionState.Connecting("s_1"), manager.state.value)
        assertEquals(2, peers.created.size)
        assertEquals(2, signaling.sent.filterIsInstance<SignalMessage.Signal>().count { it.sdp?.type == "offer" })
    }

    @Test
    fun `a short disconnect is tolerated, a long one is not`() = runTest {
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
    fun `losing the signaling socket does not end a running session`() = runTest {
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
}
