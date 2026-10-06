package com.lastpenguin.pix.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.ChannelRouter
import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import com.lastpenguin.pix.session.protocol.MessageCodec
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.signaling.SessionDescription
import com.lastpenguin.pix.session.signaling.SignalMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/**
 * The ping lines that align the two phones' `Timings` clocks for cross-phone latencies (NFR-7).
 * Robolectric records logcat, which plain JVM tests cannot read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RtcSessionManagerTimingsTest {

    private val signaling = FakeSignalingClient()
    private val peers = FakePeerFactory()

    /** Encodes what the other phone would send, with its own seq counters. */
    private val otherPhone = MessageCodec()
    private val reader = MessageCodec()

    /** This phone's wall clock, which stamps its pings. */
    private var now = 1_000L

    @Test
    fun `a ping from the other phone is logged with its ts and answered`() = runTest {
        val (manager, peer) = connected()

        peer.deliver(otherPhone.encode(SessionMessage.Ping(1_759_212_360_000)), Channel.REALTIME)
        runCurrent()

        assertEquals(listOf("ping.received ts=1759212360000"), timings("ping.received"))
        assertEquals(SessionMessage.Pong(1_759_212_360_000), peer.sentMessages().last())
        manager.leave(EndReason.LEFT)
    }

    @Test
    fun `a pong logs the round trip with the ts of the ping it answers`() = runTest {
        val (manager, peer) = connected()

        advanceTimeBy(2_100)
        runCurrent()
        val ping = peer.sentMessages().filterIsInstance<SessionMessage.Ping>().single()
        assertEquals(1_000L, ping.ts)
        now = 1_042L
        peer.deliver(otherPhone.encode(SessionMessage.Pong(ping.ts)), Channel.REALTIME)
        runCurrent()

        assertEquals(listOf("rtt 42 ms ts=1000"), timings("rtt"))
        manager.leave(EndReason.LEFT)
    }

    /** This phone's `PixTimings` lines named [name], without the leading timestamp, for example `rtt 42 ms ts=1000`. */
    private fun timings(name: String): List<String> = ShadowLog.getLogsForTag(Timings.TAG)
        .map { it.msg.substringAfter(' ') }
        .filter { it.substringBefore(' ') == name }

    private fun FakePeerConnectionClient.sentMessages(): List<SessionMessage> =
        sent.mapNotNull { reader.decode(it.second)?.message }

    /** A photographer with a connected subject. Each test must `leave`, or the ping loop keeps runTest from ending. */
    private suspend fun TestScope.connected(): Pair<RtcSessionManager, FakePeerConnectionClient> {
        val manager = RtcSessionManager(
            signaling = signaling,
            peers = peers,
            frameSource = CameraFrameSource(),
            codec = MessageCodec(),
            router = ChannelRouter(),
            serverUrl = "http://10.0.2.2:8000/",
            identity = SessionIdentity("Dongje", "0.1.0"),
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = { now },
        )
        manager.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER)
        runCurrent()
        signaling.push(SignalEvent.Opened)
        signaling.receive(SignalMessage.RoomCreated("482915", "s_1"))
        signaling.receive(SignalMessage.PeerJoined())
        runCurrent()
        val peer = peers.created.last()
        signaling.receive(SignalMessage.Signal(sdp = SessionDescription("answer", "v=0 answer")))
        peer.open()
        peer.deliver(
            otherPhone.encode(SessionMessage.Hello(appVersion = "0.1.0", role = Role.SUBJECT, name = "Junhyeong")),
        )
        runCurrent()
        assertTrue(manager.state.value is SessionState.Connected)
        return manager to peer
    }
}
