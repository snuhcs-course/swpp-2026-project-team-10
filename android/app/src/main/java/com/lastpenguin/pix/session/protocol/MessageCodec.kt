package com.lastpenguin.pix.session.protocol

/**
 * Turns [SessionMessage] into the data channel envelope `{"v","t","seq","ts","b"}` and back (Design 2.5.1).
 * Owner: Real-time (#8).
 */
class MessageCodec {
    fun encode(message: SessionMessage): String =
        TODO("#8: keep t at the top and move the other fields into b")

    fun decode(text: String): SessionMessage =
        TODO("#8")
}
