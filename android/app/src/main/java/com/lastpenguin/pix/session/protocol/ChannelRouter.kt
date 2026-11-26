// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.session.protocol

/**
 * Picks the data channel for each message, as listed in Design 2.5.1.
 * Owner: Real-time (#8).
 */
class ChannelRouter {

    enum class Channel {
        /** Unordered, no retransmits: gesture steps, and moments that are wrong late. */
        REALTIME,

        /** Ordered and reliable: images, final values, everything else. */
        RELIABLE,
    }

    fun channelFor(message: SessionMessage): Channel = when (message) {
        is SessionMessage.GuideStateUpdate -> if (message.final) Channel.RELIABLE else Channel.REALTIME

        is SessionMessage.CameraStateUpdate -> if (message.final) Channel.RELIABLE else Channel.REALTIME

        is SessionMessage.ZoomSet -> if (message.final) Channel.RELIABLE else Channel.REALTIME

        is SessionMessage.GuideMove -> if (message.final) Channel.RELIABLE else Channel.REALTIME

        is SessionMessage.ExposureSet -> if (message.final) Channel.RELIABLE else Channel.REALTIME

        is SessionMessage.Ping, is SessionMessage.Pong, is SessionMessage.PhotoShutter -> Channel.REALTIME

        // The subject's press of the shutter is a moment (#314): kept behind a full buffer or a drop, it would take a
        // photo once the subject no longer waits for one. Sent at once or not at all, a lost one only asks again.
        is SessionMessage.ShutterPress -> Channel.REALTIME

        // So is a start of AI poses (#276): kept back, it would open the scene photo's step on the photographer's
        // phone once the subject's phone gave it up and said that nothing started.
        is SessionMessage.PoseStart -> Channel.REALTIME

        else -> Channel.RELIABLE
    }

    /**
     * True for a moment that is wrong late: the subject's press of the shutter and their start of AI poses, each given
     * up by the subject's phone after 2 s (#314, #276). On the realtime channel such a message goes at once or not at
     * all. With that channel lost the reliable one carries the realtime messages (Design 2.6.7), and there this one is
     * sent only while nothing waits ahead of it, never queued: kept back, it would take a photo, or open the scene
     * photo's step, once the subject was told that nothing happened.
     */
    fun neverLate(message: SessionMessage): Boolean =
        message is SessionMessage.ShutterPress || message is SessionMessage.PoseStart

    /**
     * True for messages where only the newest value matters. A receiver drops one whose `seq` is not newer than
     * the last one applied for that type, whichever channel it came on (Design 2.5.1).
     */
    fun latestOnly(message: SessionMessage): Boolean = when (message) {
        is SessionMessage.GuideStateUpdate, is SessionMessage.CameraStateUpdate, is SessionMessage.ZoomSet,
        is SessionMessage.GuideMove, is SessionMessage.ExposureSet,
        -> true

        else -> false
    }
}
