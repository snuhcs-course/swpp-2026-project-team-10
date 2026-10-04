package com.lastpenguin.pix.session.signaling

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON for the signaling WebSocket (Design 2.5.2), for example `{"type": "room.join", "code": "482915"}`. */
object SignalCodec {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(message: SignalMessage): String = json.encodeToString(SignalMessage.serializer(), message)

    /** null when [text] is not a known signaling message. */
    fun decode(text: String): SignalMessage? = try {
        json.decodeFromString(SignalMessage.serializer(), text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
