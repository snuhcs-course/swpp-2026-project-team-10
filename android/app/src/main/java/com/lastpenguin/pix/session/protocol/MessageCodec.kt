// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One decoded data channel message with its envelope fields (Design 2.5.1). */
data class Envelope(val type: String, val seq: Long, val ts: Long, val message: SessionMessage)

/**
 * Turns [SessionMessage] into the data channel envelope `{"v","t","seq","ts","b"}` and back (Design 2.5.1).
 * `seq` counts per message type, so each receiver can drop a stale realtime value.
 * Owner: Real-time (#8).
 */
class MessageCodec(private val clock: () -> Long = System::currentTimeMillis) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val seqByType = HashMap<String, Long>()

    fun encode(message: SessionMessage): String {
        val flat = json.encodeToJsonElement(SessionMessage.serializer(), message).jsonObject
        val type = flat.getValue(TYPE).jsonPrimitive.content
        val seq = (seqByType[type] ?: 0L) + 1
        seqByType[type] = seq
        val envelope = JsonObject(
            mapOf(
                "v" to JsonPrimitive(PROTOCOL_VERSION),
                TYPE to JsonPrimitive(type),
                "seq" to JsonPrimitive(seq),
                "ts" to JsonPrimitive(clock()),
                "b" to JsonObject(flat.filterKeys { it != TYPE }),
            ),
        )
        return json.encodeToString(JsonObject.serializer(), envelope)
    }

    /** null when [text] is not an envelope or its type is unknown; receivers ignore those (Design 2.5.1). */
    fun decode(text: String): Envelope? {
        val envelope = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = (envelope[TYPE] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val body = envelope["b"] as? JsonObject ?: JsonObject(emptyMap())
        val message = try {
            json.decodeFromJsonElement(SessionMessage.serializer(), JsonObject(body + (TYPE to JsonPrimitive(type))))
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return Envelope(
            type = type,
            seq = (envelope["seq"] as? JsonPrimitive)?.longOrNull ?: 0L,
            ts = (envelope["ts"] as? JsonPrimitive)?.longOrNull ?: 0L,
            message = message,
        )
    }

    private companion object {
        const val TYPE = "t"
    }
}
