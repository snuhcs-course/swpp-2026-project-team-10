// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.protocol.ChannelRouter.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRouterTest {

    private val router = ChannelRouter()

    @Test
    fun `gesture steps and zoom requests go on the realtime channel`() {
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.GuideStateUpdate(GuideState(), final = false)))
        assertEquals(
            Channel.REALTIME,
            router.channelFor(SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = false)),
        )
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.ZoomSet(2f)))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.ZoomSet(2f, final = true)))
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.Ping(0)))
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.Pong(0)))
    }

    @Test
    fun `final values, images, and commands go on the reliable channel`() {
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.GuideStateUpdate(GuideState(), final = true)))
        assertEquals(
            Channel.RELIABLE,
            router.channelFor(SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = true)),
        )
        assertEquals(
            Channel.RELIABLE,
            router.channelFor(SessionMessage.Hello(appVersion = "0.1.0", role = Role.SUBJECT, name = "J")),
        )
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.GuideImageChunk("g", 0, "AA==")))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.GuideClear))
        // The guide's tip and its end, in order with each other (#213).
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.GuideTip("g", "Step back.")))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.GuideTipClear))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.Leave(EndReason.LEFT)))
        // The slots and a failure come in order with the set's other messages (Iter3-A13).
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.PosePlanned("set1", listOf("pose-1"))))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.PoseCandidateFailed("set1", "pose-1")))
        // Who holds the zoom, each change in order (#236).
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER)))
        assertFalse(router.latestOnly(SessionMessage.ZoomHoldUpdate()))
        // The cancel of a held-back pinch goes like its final value, and makes its earlier steps stale (#236).
        val cancel = SessionMessage.ZoomSet(1.4f, final = true, cancel = true)
        assertEquals(Channel.RELIABLE, router.channelFor(cancel))
        assertTrue(router.latestOnly(cancel))
    }

    @Test
    fun `the subject's requests follow the same rules as zoom`() {
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.GuideMove("g", 0.4f, 0.5f, 0.8f)))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.GuideMove("g", 0.4f, 0.5f, 0.8f, final = true)))
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.ExposureSet(3)))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.ExposureSet(3, final = true)))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.FlashSet(FlashMode.AUTO)))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.FocusSet(0.6f, 0.4f)))
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.ControlPermission(false)))
        assertTrue(router.latestOnly(SessionMessage.GuideMove("g", 0.4f, 0.5f, 0.8f)))
        assertTrue(router.latestOnly(SessionMessage.ExposureSet(3)))
        assertFalse(router.latestOnly(SessionMessage.FlashSet(FlashMode.ON)))
    }

    @Test
    fun `a dropped photo goes on the reliable channel, since a lost one keeps the subject waiting`() {
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.PhotoDropped("shot-1")))
        assertFalse(router.latestOnly(SessionMessage.PhotoDropped("shot-1")))
    }

    @Test
    fun `the shutter goes on the realtime channel, as a moment that is wrong late, and is never dropped as stale`() {
        assertEquals(Channel.REALTIME, router.channelFor(SessionMessage.PhotoShutter("shot-1")))
        assertFalse(router.latestOnly(SessionMessage.PhotoShutter("shot-1")))
    }

    @Test
    fun `only continuous values drop stale seq`() {
        assertTrue(router.latestOnly(SessionMessage.GuideStateUpdate(GuideState(), final = true)))
        assertTrue(router.latestOnly(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = false)))
        assertTrue(router.latestOnly(SessionMessage.ZoomSet(1f)))
        assertFalse(router.latestOnly(SessionMessage.GuideImageChunk("g", 3, "AA==")))
        assertFalse(router.latestOnly(SessionMessage.Ping(0)))
    }
}
