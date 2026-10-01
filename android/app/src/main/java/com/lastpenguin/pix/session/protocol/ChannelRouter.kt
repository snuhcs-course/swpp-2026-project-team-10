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

    fun channelFor(message: SessionMessage): Channel =
        TODO("#8")
}
