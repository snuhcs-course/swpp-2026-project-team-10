package com.lastpenguin.pix.session.protocol

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
    fun `only continuous values drop stale seq`() {
        assertTrue(router.latestOnly(SessionMessage.GuideStateUpdate(GuideState(), final = true)))
        assertTrue(router.latestOnly(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = false)))
        assertTrue(router.latestOnly(SessionMessage.ZoomSet(1f)))
        assertFalse(router.latestOnly(SessionMessage.GuideImageChunk("g", 3, "AA==")))
        assertFalse(router.latestOnly(SessionMessage.Ping(0)))
    }
}
