// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han, Dongje Park, and Sungmin Jo
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
            SessionMessage.PoseStep("set1", PoseSetStep.GENERATING),
            SessionMessage.PoseStep("set1", PoseSetStep.PICKING, poseIds = listOf("pose-1", "pose-4")),
            SessionMessage.PoseStep("set1", PoseSetStep.CHOSEN, poseId = "pose-4", by = Role.SUBJECT),
            SessionMessage.PoseCandidateBegin("set1", "pose-4", "jpeg", 768, 1024, 151_320, 17),
            SessionMessage.PoseCandidateChunk("set1", "pose-4", 0, "/9j/4AAQSkZJRgABAQ"),
            SessionMessage.PoseCandidateEnd("set1", "pose-4", 3735928559L),
            SessionMessage.PosePick("set1", "pose-4"),
            SessionMessage.GuideConfirm("c1", ConfirmStep.FINDING),
            SessionMessage.GuideConfirm("c1", ConfirmStep.READY, guideId = "g1"),
            SessionMessage.GuideConfirm("c1", ConfirmStep.USED, guideId = "g1", by = Role.SUBJECT),
            SessionMessage.GuideConfirmImageBegin("c1", "g1", "mask-png", 480, 720, 18_204, 2),
            SessionMessage.GuideConfirmImageChunk("c1", "g1", 0, "iVBORw0KGgo"),
            SessionMessage.GuideConfirmImageEnd("c1", "g1", 3735928559L),
            SessionMessage.GuideConfirmUse("c1", "g1"),
            SessionMessage.PoseDescription("d1", DescriptionStep.OPEN, "wave", Role.SUBJECT, rejected = true),
            SessionMessage.PoseDescription("d1", DescriptionStep.STARTED),
            SessionMessage.PoseSceneBegin("d1", "jpeg", 768, 1024, 151_320, 17),
            SessionMessage.PoseSceneChunk("d1", 0, "/9j/4AAQSkZJRgABAQ"),
            SessionMessage.PoseSceneEnd("d1", 3735928559L),
            SessionMessage.PoseDescriptionEdit("d1", holding = true, text = "벽에 기대기"),
            SessionMessage.PoseDescriptionGo("d1", "", suggest = true),
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
    fun `a guide image carries an anchor and a source only when the guide has them`() {
        val anchor = NormalizedRect(0.31f, 0.21f, 0.69f, 0.95f)
        val placed =
            SessionMessage.GuideImageBegin("s1", "mask-png", 270, 720, 8560, 1, 0.375f, anchor, GuideSource.GALLERY)
        val body = Json.parseToJsonElement(codec.encode(placed)).jsonObject.getValue("b").jsonObject
        assertEquals(0.31f, body.getValue("anchor").jsonObject.getValue("left").jsonPrimitive.content.toFloat(), 0f)
        assertEquals("GALLERY", body.getValue("source").jsonPrimitive.content)
        assertEquals(placed, codec.decode(codec.encode(placed))!!.message)

        val unplaced = SessionMessage.GuideImageBegin("g1", "mask-png", 540, 720, 9120, 1, 0.75f)
        val plain = Json.parseToJsonElement(codec.encode(unplaced)).jsonObject.getValue("b").jsonObject
        assertFalse("anchor" in plain)
        assertFalse("source" in plain)
        assertEquals(unplaced, codec.decode(codec.encode(unplaced))!!.message)
    }

    @Test
    fun `a set's step sends only the fields of its step, as in the design`() {
        val picking = SessionMessage.PoseStep("set1", PoseSetStep.PICKING, poseIds = listOf("pose-1", "pose-2"))
        val body = Json.parseToJsonElement(codec.encode(picking)).jsonObject.getValue("b").jsonObject
        assertEquals("PICKING", body.getValue("step").jsonPrimitive.content)
        assertEquals(setOf("setId", "step", "poseIds"), body.keys)

        val generating = Json.parseToJsonElement(codec.encode(SessionMessage.PoseStep("set1", PoseSetStep.GENERATING)))
        assertEquals(setOf("setId", "step"), generating.jsonObject.getValue("b").jsonObject.keys)

        val example = codec.decode(
            """{"v":3,"t":"pose.step","seq":3,"ts":1,"b":{"setId":"set1","step":"CHOSEN","poseId":"pose-2","by":"SUBJECT"}}""",
        )
        assertEquals(
            SessionMessage.PoseStep("set1", PoseSetStep.CHOSEN, poseId = "pose-2", by = Role.SUBJECT),
            example!!.message,
        )
    }

    @Test
    fun `a set's step with an unknown step or without its set is dropped`() {
        assertNull(codec.decode("""{"v":3,"t":"pose.step","seq":1,"ts":0,"b":{"setId":"set1","step":"PAUSED"}}"""))
        assertNull(codec.decode("""{"v":3,"t":"pose.step","seq":1,"ts":0,"b":{"step":"PICKING"}}"""))
        assertNull(codec.decode("""{"v":3,"t":"pose.pick","seq":1,"ts":0,"b":{"setId":"set1","poseId":7}}"""))
    }

    @Test
    fun `a confirm's step sends only the fields of its step, and an unknown step is dropped`() {
        val finding = Json.parseToJsonElement(codec.encode(SessionMessage.GuideConfirm("c1", ConfirmStep.FINDING)))
        assertEquals("guide.confirm", finding.jsonObject.getValue("t").jsonPrimitive.content)
        assertEquals(setOf("confirmId", "step"), finding.jsonObject.getValue("b").jsonObject.keys)

        val example = codec.decode(
            """{"v":3,"t":"guide.confirm","seq":4,"ts":1,"b":{"confirmId":"c1","step":"USED","guideId":"g1","by":"PHOTOGRAPHER"}}""",
        )
        assertEquals(
            SessionMessage.GuideConfirm("c1", ConfirmStep.USED, guideId = "g1", by = Role.PHOTOGRAPHER),
            example!!.message,
        )
        assertNull(codec.decode("""{"v":3,"t":"guide.confirm","seq":1,"ts":0,"b":{"confirmId":"c1","step":"SHOWN"}}"""))
        assertNull(codec.decode("""{"v":3,"t":"guide.confirm.use","seq":1,"ts":0,"b":{"confirmId":"c1"}}"""))
    }

    @Test
    fun `a description sends its text, writer, and rejection only while open, and an unknown step is dropped`() {
        val ended = Json.parseToJsonElement(codec.encode(SessionMessage.PoseDescription("d1", DescriptionStep.CLOSED)))
        assertEquals("pose.description", ended.jsonObject.getValue("t").jsonPrimitive.content)
        assertEquals(setOf("descriptionId", "step"), ended.jsonObject.getValue("b").jsonObject.keys)

        val example = codec.decode(
            """{"v":3,"t":"pose.description","seq":2,"ts":1,"b":{"descriptionId":"d1","step":"OPEN","text":"wave","writer":"PHOTOGRAPHER"}}""",
        )
        assertEquals(
            SessionMessage.PoseDescription("d1", DescriptionStep.OPEN, "wave", Role.PHOTOGRAPHER),
            example!!.message,
        )
        val unknownStep = """{"v":3,"t":"pose.description","seq":1,"ts":0,"b":{"descriptionId":"d1","step":"TYPING"}}"""
        assertNull(codec.decode(unknownStep))
        // A go without its text and its choice.
        val incomplete = """{"v":3,"t":"pose.description.go","seq":1,"ts":0,"b":{"descriptionId":"d1"}}"""
        assertNull(codec.decode(incomplete))
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
