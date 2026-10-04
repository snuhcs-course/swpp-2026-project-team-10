package com.lastpenguin.pix.session.signaling

import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The JSON must match the server (server/README.md) and Design 2.5.2 exactly. */
class SignalCodecTest {

    @Test
    fun `client messages encode as the server expects`() {
        assertEquals("""{"type":"room.create"}""", SignalCodec.encode(SignalMessage.RoomCreate))
        assertEquals("""{"type":"room.join","code":"482915"}""", SignalCodec.encode(SignalMessage.RoomJoin("482915")))
        assertEquals("""{"type":"leave"}""", SignalCodec.encode(SignalMessage.Leave))
    }

    @Test
    fun `a signal carries exactly one of sdp or candidate`() {
        val offer = SignalCodec.encode(SignalMessage.Signal(sdp = SessionDescription("offer", "v=0\r\n")))
        assertEquals("""{"type":"signal","sdp":{"type":"offer","sdp":"v=0\r\n"}}""", offer)

        val candidate = SignalCodec.encode(
            SignalMessage.Signal(candidate = IceCandidate(sdpMid = null, sdpMLineIndex = 0, candidate = "candidate:1")),
        )
        assertEquals(
            """{"type":"signal","candidate":{"sdpMid":null,"sdpMLineIndex":0,"candidate":"candidate:1"}}""",
            candidate,
        )
    }

    @Test
    fun `server messages decode`() {
        assertEquals(
            SignalMessage.RoomCreated("482915", "s_7f3a92c1"),
            SignalCodec.decode("""{"type":"room.created","code":"482915","sessionId":"s_7f3a92c1","iceServers":[]}"""),
        )
        assertEquals(
            SignalMessage.SessionJoined("s_7f3a92c1", Role.SUBJECT),
            SignalCodec.decode(
                """{"type":"session.joined","sessionId":"s_7f3a92c1","role":"SUBJECT","iceServers":[]}""",
            ),
        )
        assertEquals(SignalMessage.PeerJoined(), SignalCodec.decode("""{"type":"peer.joined"}"""))
        assertEquals(
            SignalMessage.PeerLeft(EndReason.CONNECTION_LOST),
            SignalCodec.decode("""{"type":"peer.left","reason":"CONNECTION_LOST"}"""),
        )
        assertEquals(
            SignalMessage.ServerError("NOT_FOUND", "No active room with this code"),
            SignalCodec.decode("""{"type":"error","code":"NOT_FOUND","message":"No active room with this code"}"""),
        )
        assertEquals(
            SignalMessage.Signal(
                candidate = IceCandidate("0", 0, "candidate:1 1 udp 2122260223 192.168.0.12 50000 typ host"),
            ),
            SignalCodec.decode(
                """{"type":"signal","candidate":{"sdpMid":"0","sdpMLineIndex":0,"candidate":"candidate:1 1 udp 2122260223 192.168.0.12 50000 typ host"}}""",
            ),
        )
    }

    @Test
    fun `ice servers with credentials decode for later iterations`() {
        val decoded = SignalCodec.decode(
            """{"type":"room.created","code":"000001","sessionId":"s_1","iceServers":[{"urls":["turn:pix.example.com:3478"],"username":"u","credential":"c"}]}""",
        ) as SignalMessage.RoomCreated
        assertEquals(listOf(IceServer(listOf("turn:pix.example.com:3478"), "u", "c")), decoded.iceServers)
    }

    @Test
    fun `unknown and malformed messages decode to null`() {
        assertNull(SignalCodec.decode("""{"type":"room.open"}"""))
        assertNull(SignalCodec.decode("""{"type":"room.join"}"""))
        assertNull(SignalCodec.decode("nope"))
    }
}
