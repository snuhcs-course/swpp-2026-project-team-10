// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.PhotographerAction
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.toAction
import com.lastpenguin.pix.session.toMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Partitions for the subject's requests of Design 2.5.1 (#57): the Design's JSON example of each message decodes;
 * a round trip keeps every field; `final` left out means a step; `guide.state` from a phone without `by` means the
 * photographer, and the echo of a `guide.move` carries `by: SUBJECT`; `camera.zoom.hold` naming a holder, null, or no
 * holder at all, the last two meaning a free zoom, and a `camera.zoom.set` with `cancel`, which is false when left out (#236).
 * The subject's shutter (#314): the press with its number, and its answer, taken or not, naming that number; a press
 * that cancels the photographer's timer, the answer of a press the timer took, and both fields left out.
 * The subject's start of AI poses (#276): the situation's name, and the answer, begun or not.
 * The photographer's countdown (`camera.timer`, FR-1.17): the Design's example decodes, and a round trip keeps it.
 * The timer set from the subject's phone (#318): `camera.timer.set`, the timer in `camera.state` (none from a phone of
 * before it, which is off), and the photographer's own change as a `camera.did` of each value; seconds this Pix does
 * not offer are no action.
 * The guide's panel from either phone (FR-7.2, FR-7.4): the subject's `guide.visible.set`, `guide.opacity.set` (a
 * step when `final` is left out), and `guide.remove`, and the photographer's own hide, show, opacity, and removal as a
 * `camera.did`; an opacity of no number or 0 is no action.
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
    fun zoomHold_designExample_decodes() {
        val text = """{ "v": 3, "t": "camera.zoom.hold", "seq": 4, "ts": 1759212352290,
            "b": { "by": "PHOTOGRAPHER" } }"""

        assertEquals(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER), decode(text))
    }

    @Test
    fun zoomHold_nullOrNoHolder_meansAFreeZoom() {
        val none = """{ "v": 3, "t": "camera.zoom.hold", "seq": 5, "ts": 1759212352400, "b": { "by": null } }"""
        val missing = """{ "v": 3, "t": "camera.zoom.hold", "seq": 6, "ts": 1759212352500, "b": { } }"""

        assertEquals(SessionMessage.ZoomHoldUpdate(null), decode(none))
        assertEquals(SessionMessage.ZoomHoldUpdate(null), decode(missing))
    }

    @Test
    fun zoomCancel_designExample_decodes() {
        val text = """{ "v": 3, "t": "camera.zoom.set", "seq": 32, "ts": 1759212352340,
            "b": { "ratio": 1.4, "final": true, "cancel": true } }"""
        val older = """{ "v": 3, "t": "camera.zoom.set", "seq": 33, "ts": 1759212352400, "b": { "ratio": 2.0 } }"""

        assertEquals(SessionMessage.ZoomSet(1.4f, final = true, cancel = true), decode(text))
        assertEquals(SessionMessage.ZoomSet(2f), decode(older))
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
    fun thePhotographersFocus_designExample_decodes() {
        val focused = """{ "v": 3, "t": "camera.focus", "seq": 7, "ts": 1759212355000,
            "b": { "x": 0.38, "y": 0.4 } }"""

        assertEquals(SessionMessage.Focused(0.38f, 0.4f), decode(focused))
    }

    @Test
    fun whatThePhotographerDid_designExamples_decode() {
        val zoom = """{ "v": 3, "t": "camera.did", "seq": 8, "ts": 1759212355000,
            "b": { "action": "zoom", "zoom": 2.0 } }"""
        val moved = """{ "v": 3, "t": "camera.did", "seq": 9, "ts": 1759212355000, "b": { "action": "guide.move" } }"""

        assertEquals(SessionMessage.PhotographerDid("zoom", zoom = 2f), decode(zoom))
        assertEquals(SessionMessage.PhotographerDid("guide.move"), decode(moved))
    }

    @Test
    fun whatThePhotographerDid_goesAsItsNameAndValue_andComesBackTheSame() {
        val actions = listOf(
            PhotographerAction.Zoom(2f),
            PhotographerAction.Brightness(-3),
            PhotographerAction.Flash(FlashMode.AUTO),
            PhotographerAction.Timer(ShutterTimer.TEN),
            PhotographerAction.Timer(ShutterTimer.OFF),
            PhotographerAction.GuideMove,
            PhotographerAction.Guide,
            PhotographerAction.GuideVisible(false),
            PhotographerAction.GuideVisible(true),
            PhotographerAction.GuideOpacity(0.7f),
            PhotographerAction.GuideRemove,
        )

        assertEquals(
            listOf(
                "zoom", "brightness", "flash", "timer", "timer", "guide.move", "guide",
                "guide.hide", "guide.show", "guide.opacity", "guide.remove",
            ),
            actions.map { it.toMessage().action },
        )
        for (action in actions) {
            val received = decode(codec.encode(action.toMessage())) as SessionMessage.PhotographerDid
            assertEquals(action, received.toAction())
        }
    }

    @Test
    fun thePhotographersCountdown_designExample_decodes() {
        val text = """{ "v": 3, "t": "camera.timer", "seq": 10, "ts": 1759212355000, "b": { "left": 3 } }"""

        assertEquals(SessionMessage.Countdown(3), decode(text))
    }

    @Test
    fun anActionThisPhoneDoesNotKnow_isNoAction_andStillDecodes() {
        val newer = """{ "v": 3, "t": "camera.did", "seq": 8, "ts": 1759212355000,
            "b": { "action": "lens", "lens": "tele" } }"""

        val received = decode(newer) as SessionMessage.PhotographerDid

        assertNull(received.toAction())
        // A timer of seconds this Pix does not offer, as a later one may: no line either.
        assertNull(SessionMessage.PhotographerDid("timer", seconds = 5).toAction())
    }

    @Test
    fun theTimer_setFromTheSubjectsPhone_designExamples_decode() {
        val set = """{ "v": 3, "t": "camera.timer.set", "seq": 13, "ts": 1759212355000, "b": { "seconds": 3 } }"""
        val state = """{ "v": 3, "t": "camera.state", "seq": 21, "ts": 1759212355100,
            "b": { "zoom": 1.0, "by": "SUBJECT", "final": true, "timer": 3 } }"""
        val did = """{ "v": 3, "t": "camera.did", "seq": 22, "ts": 1759212356000,
            "b": { "action": "timer", "seconds": 10 } }"""

        assertEquals(SessionMessage.TimerSet(3), decode(set))
        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, timer = 3), decode(state))
        assertEquals(
            PhotographerAction.Timer(ShutterTimer.TEN),
            (decode(did) as SessionMessage.PhotographerDid).toAction(),
        )
        // A photographer's phone of before the timer's state says none, which is off.
        val before = """{ "v": 3, "t": "camera.state", "seq": 2, "ts": 1759212355100,
            "b": { "zoom": 1.0, "by": "PHOTOGRAPHER", "final": true } }"""
        assertEquals(0, (decode(before) as SessionMessage.CameraStateUpdate).timer)
    }

    @Test
    fun theSubjectsShutter_andItsAnswer_designExamples_decode() {
        val press = """{ "v": 3, "t": "camera.shutter", "seq": 9, "ts": 1759212355000, "b": { "press": 7 } }"""
        val taken = """{ "v": 3, "t": "camera.shutter.answer", "seq": 4, "ts": 1759212355100,
            "b": { "taken": true, "press": 7 } }"""
        val refused = """{ "v": 3, "t": "camera.shutter.answer", "seq": 5, "ts": 1759212355100,
            "b": { "taken": false, "press": 8 } }"""

        assertEquals(SessionMessage.ShutterPress(press = 7), decode(press))
        assertEquals(SessionMessage.ShutterAnswer(taken = true, press = 7), decode(taken))
        assertEquals(SessionMessage.ShutterAnswer(taken = false, press = 8), decode(refused))
    }

    @Test
    fun theSubjectsShutter_withThePhotographersTimer_designExamples_decode() {
        val cancel = """{ "v": 3, "t": "camera.shutter", "seq": 11, "ts": 1759212357000,
            "b": { "press": 9, "cancel": true } }"""
        val timer = """{ "v": 3, "t": "camera.shutter.answer", "seq": 6, "ts": 1759212357100,
            "b": { "taken": false, "press": 9, "timer": true } }"""

        assertEquals(SessionMessage.ShutterPress(press = 9, cancel = true), decode(cancel))
        assertEquals(SessionMessage.ShutterAnswer(taken = false, press = 9, timer = true), decode(timer))
        // A phone of before the timer sends neither field: a press for a photo, and an answer about one.
        val before = """{ "v": 3, "t": "camera.shutter.answer", "seq": 2, "ts": 1759212355100,
            "b": { "taken": false, "press": 1 } }"""
        assertEquals(SessionMessage.ShutterAnswer(taken = false, press = 1, timer = false), decode(before))
    }

    @Test
    fun theSubjectsStartOfPoses_andItsAnswer_designExamples_decode() {
        val start = """{ "v": 3, "t": "pose.start", "seq": 12, "ts": 1759212355000,
            "b": { "situation": "full_body" } }"""
        val answer = """{ "v": 3, "t": "pose.start.answer", "seq": 6, "ts": 1759212355100,
            "b": { "started": true } }"""

        assertEquals(SessionMessage.PoseStart("full_body"), decode(start))
        assertEquals(SessionMessage.PoseStartAnswer(started = true), decode(answer))
    }

    @Test
    fun theGuidePanel_designExamples_decode() {
        val hide = """{ "v": 3, "t": "guide.visible.set", "seq": 3, "ts": 1759212356000,
            "b": { "guideId": "5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17", "visible": false } }"""
        val opacity = """{ "v": 3, "t": "guide.opacity.set", "seq": 14, "ts": 1759212356200,
            "b": { "guideId": "5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17", "opacity": 0.7, "final": true } }"""
        val remove = """{ "v": 3, "t": "guide.remove", "seq": 1, "ts": 1759212356400,
            "b": { "guideId": "5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17" } }"""
        val did = """{ "v": 3, "t": "camera.did", "seq": 23, "ts": 1759212356600,
            "b": { "action": "guide.opacity", "opacity": 0.3 } }"""

        assertEquals(SessionMessage.GuideVisibleSet("5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17", false), decode(hide))
        assertEquals(
            SessionMessage.GuideOpacitySet("5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17", 0.7f, final = true),
            decode(opacity),
        )
        assertEquals(SessionMessage.GuideRemove("5f0c2b9e-7a41-4d3c-9b1e-2f6a8d0c4e17"), decode(remove))
        assertEquals(
            PhotographerAction.GuideOpacity(0.3f),
            (decode(did) as SessionMessage.PhotographerDid).toAction(),
        )
    }

    @Test
    fun anOpacityStep_withoutFinal_isAStep_andAnOpacityOfNoValueIsNoAction() {
        val step = """{"v":3,"t":"guide.opacity.set","seq":1,"ts":0,"b":{"guideId":"g","opacity":0.4}}"""

        assertFalse((decode(step) as SessionMessage.GuideOpacitySet).final)
        assertNull(SessionMessage.PhotographerDid("guide.opacity").toAction())
        assertNull(SessionMessage.PhotographerDid("guide.opacity", opacity = Float.NaN).toAction())
    }

    @Test
    fun requests_roundTrip_keepEveryField() {
        val messages = listOf(
            SessionMessage.GuideMove("g", 0.1f, 0.2f, 0.3f, final = true),
            SessionMessage.ExposureSet(-2),
            SessionMessage.FlashSet(FlashMode.ON),
            SessionMessage.FocusSet(0.62f, 0.4f),
            SessionMessage.Focused(0.38f, 0.4f),
            SessionMessage.Countdown(3),
            SessionMessage.TimerSet(10),
            SessionMessage.ControlPermission(true),
            SessionMessage.ShutterPress(press = 7),
            SessionMessage.ShutterAnswer(taken = true, press = 7),
            SessionMessage.ShutterAnswer(taken = false, press = 8),
            SessionMessage.ShutterPress(press = 9, cancel = true),
            SessionMessage.ShutterAnswer(taken = false, press = 9, timer = true),
            SessionMessage.PoseStart("selfie"),
            SessionMessage.PoseStartAnswer(started = true),
            SessionMessage.PoseStartAnswer(started = false),
            SessionMessage.GuideVisibleSet("g", visible = true),
            SessionMessage.GuideOpacitySet("g", 0.25f),
            SessionMessage.GuideOpacitySet("g", 0.9f, final = true),
            SessionMessage.GuideRemove("g"),
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
