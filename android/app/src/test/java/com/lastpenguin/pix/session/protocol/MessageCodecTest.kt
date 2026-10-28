// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MessageCodecTest {

    private val codec = MessageCodec(clock = { 1_759_212_345_678L })

    @Test
    fun `hello is wrapped in the envelope of Design 2_5_1`() {
        val text = codec.encode(SessionMessage.Hello(appVersion = "0.1.0", role = Role.PHOTOGRAPHER, name = "Dongje"))

        val envelope = Json.parseToJsonElement(text).jsonObject
        assertEquals(3, envelope.getValue("v").jsonPrimitive.int)
        assertEquals("hello", envelope.getValue("t").jsonPrimitive.content)
        assertEquals(1, envelope.getValue("seq").jsonPrimitive.int)
        assertEquals(1_759_212_345_678L, envelope.getValue("ts").jsonPrimitive.long)
        val body = envelope.getValue("b").jsonObject
        assertEquals(3, body.getValue("protocol").jsonPrimitive.int)
        assertEquals("0.1.0", body.getValue("appVersion").jsonPrimitive.content)
        assertEquals("PHOTOGRAPHER", body.getValue("role").jsonPrimitive.content)
        assertEquals("Dongje", body.getValue("name").jsonPrimitive.content)
        assertEquals(JsonNull, body.getValue("haveGuideId"))
        assertFalse("t" in body)
    }

    @Test
    fun `every message type survives a round trip`() {
        val messages = listOf(
            SessionMessage.Hello(appVersion = "0.1.0", role = Role.SUBJECT, name = "Junhyeong", haveGuideId = "g1"),
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f, 3f))),
            SessionMessage.GuideImageBegin("g1", "mask-png", 540, 720, 9120, 1, 0.75f),
            SessionMessage.GuideImageChunk("g1", 0, "UklGRl68AABXRUJQ"),
            SessionMessage.GuideImageEnd("g1", 3735928559L),
            SessionMessage.GuideStateUpdate(GuideState("g1", 0.32f, 0.55f, 0.7f, 0.5f, false), false),
            SessionMessage.GuideClear,
            SessionMessage.ZoomSet(2f),
            SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, true),
            SessionMessage.Leave(EndReason.LEFT),
            SessionMessage.Ping(1_759_212_360_000L),
            SessionMessage.Pong(1_759_212_360_000L),
        )

        for (message in messages) {
            val decoded = codec.decode(codec.encode(message))
            assertNotNull("$message", decoded)
            assertEquals(message, decoded!!.message)
        }
    }

    @Test
    fun `seq counts per type`() {
        val first = codec.decode(codec.encode(SessionMessage.Ping(1)))!!
        val second = codec.decode(codec.encode(SessionMessage.Ping(2)))!!
        val other = codec.decode(codec.encode(SessionMessage.Pong(1)))!!

        assertEquals(1L, first.seq)
        assertEquals(2L, second.seq)
        assertEquals(1L, other.seq)
        assertEquals("ping", second.type)
        assertEquals("pong", other.type)
    }

    @Test
    fun `only the subject's guide image carries an anchor and a source`() {
        val anchor = NormalizedRect(0.31f, 0.21f, 0.69f, 0.95f)
        val fromSubject =
            SessionMessage.GuideImageBegin("s1", "mask-png", 270, 720, 8560, 1, 0.375f, anchor, GuideSource.GALLERY)
        val body = Json.parseToJsonElement(codec.encode(fromSubject)).jsonObject.getValue("b").jsonObject
        assertEquals(0.31f, body.getValue("anchor").jsonObject.getValue("left").jsonPrimitive.content.toFloat(), 0f)
        assertEquals("GALLERY", body.getValue("source").jsonPrimitive.content)
        assertEquals(fromSubject, codec.decode(codec.encode(fromSubject))!!.message)

        val fromPhotographer = SessionMessage.GuideImageBegin("g1", "mask-png", 540, 720, 9120, 1, 0.75f)
        val plain = Json.parseToJsonElement(codec.encode(fromPhotographer)).jsonObject.getValue("b").jsonObject
        assertFalse("anchor" in plain)
        assertFalse("source" in plain)
        assertEquals(fromPhotographer, codec.decode(codec.encode(fromPhotographer))!!.message)
    }

    @Test
    fun `guide clear has an empty body`() {
        val envelope = Json.parseToJsonElement(codec.encode(SessionMessage.GuideClear)).jsonObject
        assertEquals("guide.clear", envelope.getValue("t").jsonPrimitive.content)
        assertEquals(0, envelope.getValue("b").jsonObject.size)
    }

    @Test
    fun `decodes the guide state example from the design`() {
        val text = """
            {"v": 3, "t": "guide.state", "seq": 412, "ts": 1759212349120,
             "b": {"state": {"guideId": "5f0c2b9e", "cx": 0.32, "cy": 0.55, "height": 0.7,
                             "opacity": 0.5, "style": "OUTLINE", "visible": true}, "final": false}}
        """.trimIndent()

        val envelope = codec.decode(text)!!
        assertEquals(412L, envelope.seq)
        assertEquals(1_759_212_349_120L, envelope.ts)
        val update = envelope.message as SessionMessage.GuideStateUpdate
        assertEquals("5f0c2b9e", update.state.guideId)
        assertEquals(0.32f, update.state.cx, 0f)
        // The style an Iteration 1 phone still sends is ignored: the guide is an outline only (FR-2.2).
        assertEquals(true, update.state.visible)
        assertFalse(update.final)
    }

    @Test
    fun `unknown types, unknown fields, and garbage are tolerated`() {
        // A type a later protocol may add; camera.flash.set, the earlier example here, exists since #57.
        assertNull(codec.decode("""{"v":1,"t":"camera.timer.set","seq":1,"ts":0,"b":{"seconds":3}}"""))
        assertNull(codec.decode("not json"))
        assertNull(codec.decode("""{"v":1}"""))
        val withExtra = codec.decode("""{"v":1,"t":"ping","seq":1,"ts":0,"b":{"ts":5,"extra":true}}""")
        assertEquals(SessionMessage.Ping(5), withExtra!!.message)
    }
}
