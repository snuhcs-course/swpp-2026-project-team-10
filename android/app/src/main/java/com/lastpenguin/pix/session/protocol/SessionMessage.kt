package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

const val PROTOCOL_VERSION = 1

/**
 * Data channel messages between the two phones (Design 2.5.1).
 * The @SerialName goes into `t`, the class discriminator; the other fields form the body `b` of the
 * JSON envelope in Design 2.5.1. Only the photographer takes photos; the subject sends zoom requests.
 */
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("t")
@Serializable
sealed interface SessionMessage {

    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocol: Int = PROTOCOL_VERSION,
        val appVersion: String,
        val role: Role,
        val name: String,
        val haveGuideId: String? = null,
    ) : SessionMessage

    @Serializable
    @SerialName("camera.capabilities")
    data class Capabilities(val capabilities: CameraCapabilities) : SessionMessage

    @Serializable
    @SerialName("guide.image.begin")
    data class GuideImageBegin(
        val guideId: String,
        val format: String,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val chunks: Int,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, so one message stays under 16 KiB. */
    @Serializable
    @SerialName("guide.image.chunk")
    data class GuideImageChunk(val guideId: String, val index: Int, val data: String) : SessionMessage

    @Serializable
    @SerialName("guide.image.end")
    data class GuideImageEnd(val guideId: String, val crc32: Long) : SessionMessage

    /** Realtime while a gesture runs; reliable when [final] (gesture ended). */
    @Serializable
    @SerialName("guide.state")
    data class GuideStateUpdate(val state: GuideState, val final: Boolean) : SessionMessage

    @Serializable
    @SerialName("guide.clear")
    data object GuideClear : SessionMessage

    @Serializable
    @SerialName("camera.zoom.set")
    data class ZoomSet(val ratio: Float) : SessionMessage

    /** The zoom actually applied, and who changed it. */
    @Serializable
    @SerialName("camera.state")
    data class CameraStateUpdate(val zoom: Float, val by: Role, val final: Boolean) : SessionMessage

    @Serializable
    @SerialName("session.leave")
    data class Leave(val reason: EndReason) : SessionMessage

    @Serializable
    @SerialName("ping")
    data class Ping(val ts: Long) : SessionMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val ts: Long) : SessionMessage
}
