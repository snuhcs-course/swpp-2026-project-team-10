// AI-generated with Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.session.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Partitions for the Iteration 2 fields of camera.capabilities and camera.state (Design 2.5.1).
 * Capabilities: the Design's example; a round trip; an older phone without the new fields (defaults); a malformed
 * exposureRange (one value, min above max, not an array).
 * State: the Design's example; a round trip with every field; an older phone without the new fields (defaults).
 */
class CameraMessagesTest {
    private val codec = MessageCodec(clock = { 1_759_212_345_678L })

    @Test
    fun capabilities_designExample_decodesEveryField() {
        val text = """{"v":2,"t":"camera.capabilities","seq":2,"ts":1759212345690,"b":{"capabilities":{
            "minZoom":0.6,"maxZoom":10.0,"zoomStops":[0.6,1.0,2.0,3.0],
            "exposureRange":[-12,12],"exposureStep":0.1667,"hasFlash":true,
            "aspectRatios":["R4_3","R16_9","R1_1"],"supportsFocusPoint":true}}}"""

        val capabilities = (codec.decode(text)!!.message as SessionMessage.Capabilities).capabilities

        assertEquals(-12..12, capabilities.exposureRange)
        assertEquals(0.1667f, capabilities.exposureStep, 0.0001f)
        assertEquals(true, capabilities.hasFlash)
        assertEquals(AspectRatio.entries, capabilities.aspectRatios)
        assertEquals(true, capabilities.supportsFocusPoint)
    }

    @Test
    fun capabilities_roundTrip_sendsTheExposureRangeAsMinAndMax() {
        val message = SessionMessage.Capabilities(
            CameraCapabilities(0.6f, 10f, listOf(1f), -20..20, 0.1f, false, listOf(AspectRatio.R4_3), false),
        )

        val text = codec.encode(message)

        val range = Json.parseToJsonElement(text).jsonObject["b"]!!.jsonObject["capabilities"]!!
            .jsonObject["exposureRange"]!!.jsonArray
        assertEquals("[-20,20]", range.toString())
        assertEquals(message, codec.decode(text)!!.message)
    }

    @Test
    fun capabilities_olderPhoneWithoutTheNewFields_meansNoControls() {
        val text = """{"v":1,"t":"camera.capabilities","seq":2,"ts":0,"b":{"capabilities":
            {"minZoom":1.0,"maxZoom":4.0,"zoomStops":[1.0,2.0]}}}"""

        val capabilities = (codec.decode(text)!!.message as SessionMessage.Capabilities).capabilities

        assertEquals(CameraCapabilities(1f, 4f, listOf(1f, 2f)), capabilities)
        assertEquals(0..0, capabilities.exposureRange)
        assertEquals(listOf(AspectRatio.R4_3), capabilities.aspectRatios)
    }

    @Test
    fun capabilities_malformedExposureRange_isRejected() {
        for (range in listOf("[3]", "[5,-5]", "\"-12..12\"")) {
            val text = """{"v":1,"t":"camera.capabilities","seq":2,"ts":0,"b":{"capabilities":
                {"minZoom":1.0,"maxZoom":4.0,"zoomStops":[],"exposureRange":$range}}}"""
            assertNull(range, codec.decode(text))
        }
    }

    @Test
    fun state_designExample_decodesExposureFlashAndRatio() {
        val text = """{ "v": 2, "t": "camera.state", "seq": 58, "ts": 1759212352342,
            "b": { "zoom": 2.0, "exposure": 3, "flash": "AUTO", "aspect": "R16_9", "by": "SUBJECT", "final": true } }"""

        assertEquals(
            SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, true, 3, FlashMode.AUTO, AspectRatio.R16_9),
            codec.decode(text)!!.message,
        )
    }

    @Test
    fun state_roundTrip_keepsEveryField() {
        val message = SessionMessage.CameraStateUpdate(
            0.6f,
            Role.PHOTOGRAPHER,
            false,
            -4,
            FlashMode.ON,
            AspectRatio.R1_1,
        )

        assertEquals(message, codec.decode(codec.encode(message))!!.message)
    }

    @Test
    fun state_olderPhoneWithoutTheNewFields_meansThe43FrameWithoutFlash() {
        val text = """{"v":1,"t":"camera.state","seq":5,"ts":0,"b":{"zoom":1.5,"by":"PHOTOGRAPHER","final":true}}"""

        assertEquals(SessionMessage.CameraStateUpdate(1.5f, Role.PHOTOGRAPHER, true), codec.decode(text)!!.message)
    }
}
