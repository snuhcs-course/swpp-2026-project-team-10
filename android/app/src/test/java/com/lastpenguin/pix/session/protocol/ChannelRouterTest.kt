// AI-generated with Claude Code, 2026-10-16, reviewed by Sungmin Jo
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
        assertEquals(Channel.RELIABLE, router.channelFor(SessionMessage.Leave(EndReason.LEFT)))
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
    fun `only continuous values drop stale seq`() {
        assertTrue(router.latestOnly(SessionMessage.GuideStateUpdate(GuideState(), final = true)))
        assertTrue(router.latestOnly(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = false)))
        assertTrue(router.latestOnly(SessionMessage.ZoomSet(1f)))
        assertFalse(router.latestOnly(SessionMessage.GuideImageChunk("g", 3, "AA==")))
        assertFalse(router.latestOnly(SessionMessage.Ping(0)))
    }
}
