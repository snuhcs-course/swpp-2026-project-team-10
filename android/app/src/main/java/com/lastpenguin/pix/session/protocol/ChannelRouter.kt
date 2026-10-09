// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session.protocol

/**
 * Picks the data channel for each message, as listed in Design 2.5.1.
 * Owner: Real-time (#8).
 */
class ChannelRouter {

    enum class Channel {
        /** Unordered, no retransmits: gesture steps. */
        REALTIME,

        /** Ordered and reliable: images, final values, everything else. */
        RELIABLE,
    }

    fun channelFor(message: SessionMessage): Channel = when (message) {
        is SessionMessage.GuideStateUpdate -> if (message.final) Channel.RELIABLE else Channel.REALTIME
        is SessionMessage.CameraStateUpdate -> if (message.final) Channel.RELIABLE else Channel.REALTIME
        is SessionMessage.ZoomSet -> if (message.final) Channel.RELIABLE else Channel.REALTIME
        is SessionMessage.Ping, is SessionMessage.Pong -> Channel.REALTIME
        else -> Channel.RELIABLE
    }

    /**
     * True for messages where only the newest value matters. A receiver drops one whose `seq` is not newer than
     * the last one applied for that type, whichever channel it came on (Design 2.5.1).
     */
    fun latestOnly(message: SessionMessage): Boolean = when (message) {
        is SessionMessage.GuideStateUpdate, is SessionMessage.CameraStateUpdate, is SessionMessage.ZoomSet -> true
        else -> false
    }
}
