// AI-generated with Claude Code, 2026-10-16, reviewed by Sungmin Jo
package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/*
 * Partitions for the subject's requests of Design 2.5.1 (#57): the Design's JSON example of each message decodes;
 * a round trip keeps every field; `final` left out means a step; `guide.state` from a phone without `by` means the
 * photographer, and the echo of a `guide.move` carries `by: SUBJECT`.
 */
class RemoteControlMessagesTest {
    private val codec = MessageCodec(clock = { 1_759_212_345_678L })

    private fun decode(text: String): SessionMessage = codec.decode(text)!!.message

    @Test
    fun guideMove_designExample_decodes() {
        val text = """{ "v": 2, "t": "guide.move", "seq": 21, "ts": 1759212352100,
            "b": { "guideId": "5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17", "cx": 0.41, "cy": 0.52, "height": 0.82, "final": false } }"""

        assertEquals(
            SessionMessage.GuideMove("5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17", 0.41f, 0.52f, 0.82f, final = false),
            decode(text),
        )
    }

    @Test
    fun exposureSet_designExample_decodes() {
        val text = """{ "v": 2, "t": "camera.exposure.set", "seq": 7, "ts": 1759212353000,
            "b": { "index": 3, "final": true } }"""

        assertEquals(SessionMessage.ExposureSet(3, final = true), decode(text))
    }

    @Test
    fun flashSet_designExample_decodes() {
        val text = """{ "v": 2, "t": "camera.flash.set", "seq": 2, "ts": 1759212354000, "b": { "mode": "AUTO" } }"""

        assertEquals(SessionMessage.FlashSet(FlashMode.AUTO), decode(text))
    }

    @Test
    fun focusSetAndPermission_designExamples_decode() {
        val focus = """{ "v": 2, "t": "camera.focus.set", "seq": 1, "ts": 1759212355000,
            "b": { "x": 0.62, "y": 0.4 } }"""
        val permission = """{ "v": 2, "t": "control.permission", "seq": 1, "ts": 1759212340000,
            "b": { "allowed": false } }"""

        assertEquals(SessionMessage.FocusSet(0.62f, 0.4f), decode(focus))
        assertEquals(SessionMessage.ControlPermission(false), decode(permission))
    }

    @Test
    fun requests_roundTrip_keepEveryField() {
        val messages = listOf(
            SessionMessage.GuideMove("g", 0.1f, 0.2f, 0.3f, final = true),
            SessionMessage.ExposureSet(-2),
            SessionMessage.FlashSet(FlashMode.ON),
            SessionMessage.FocusSet(0.62f, 0.4f),
            SessionMessage.ControlPermission(true),
        )

        for (message in messages) assertEquals(message, decode(codec.encode(message)))
    }

    @Test
    fun step_withoutFinal_isAStep() {
        val move = """{"v":2,"t":"guide.move","seq":1,"ts":0,"b":{"guideId":"g","cx":0.5,"cy":0.5,"height":0.7}}"""
        val exposure = """{"v":2,"t":"camera.exposure.set","seq":1,"ts":0,"b":{"index":2}}"""

        assertFalse((decode(move) as SessionMessage.GuideMove).final)
        assertFalse((decode(exposure) as SessionMessage.ExposureSet).final)
    }

    @Test
    fun guideState_withoutBy_isThePhotographers_andTheEchoOfAMoveCarriesTheSubject() {
        val older = """{"v":1,"t":"guide.state","seq":412,"ts":0,"b":{"state":{"guideId":"g","cx":0.32,"cy":0.55,
            "height":0.7,"opacity":0.5,"visible":true},"final":false}}"""
        assertEquals(Role.PHOTOGRAPHER, (decode(older) as SessionMessage.GuideStateUpdate).by)

        val echo = SessionMessage.GuideStateUpdate(
            GuideState(guideId = "g", cx = 0.41f),
            final = true,
            by = Role.SUBJECT,
        )
        val text = codec.encode(echo)
        val body = Json.parseToJsonElement(text).jsonObject.getValue("b").jsonObject
        assertEquals("SUBJECT", body.getValue("by").jsonPrimitive.content)
        assertEquals(echo, decode(text))
    }
}
