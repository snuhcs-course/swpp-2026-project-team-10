// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.session.protocol

import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Checked in `hello`: a phone that speaks another version ends the session. 3 since the guide image is the smoothed
 * mask alone (`mask-png`, Iter3-P9), which a version-2 phone, expecting a WebP cutout, could not draw. 2 was the
 * subject's requests of Iteration 2 (`guide.move`, `camera.exposure.set`, `camera.flash.set`, Iter2-P17).
 */
const val PROTOCOL_VERSION = 3

/**
 * Data channel messages between the two phones (Design 2.5.1).
 * The @SerialName goes into `t`, the class discriminator; the other fields form the body `b` of the
 * JSON envelope in Design 2.5.1. Only the photographer takes photos; the subject sends requests (zoom, brightness,
 * flash, guide moves), which the photographer applies and echoes (AD-7).
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
        /** Subject only: the guide whose reference photo it already has; the photographer skips sending that one. */
        val haveReferenceId: String? = null,
    ) : SessionMessage

    @Serializable
    @SerialName("camera.capabilities")
    data class Capabilities(val capabilities: CameraCapabilities) : SessionMessage

    /**
     * [format] is `mask-png` (Design 2.5.1); [aspect] is the mask's width / height. [anchor] and [source] come only
     * from the subject, for a guide made on its phone, which the photographer starts at [anchor] (Iter3-P9).
     */
    @Serializable
    @SerialName("guide.image.begin")
    data class GuideImageBegin(
        val guideId: String,
        val format: String,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val chunks: Int,
        val aspect: Float? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val anchor: NormalizedRect? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val source: GuideSource? = null,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, so one message stays under 16 KiB. */
    @Serializable
    @SerialName("guide.image.chunk")
    data class GuideImageChunk(val guideId: String, val index: Int, val data: String) : SessionMessage

    @Serializable
    @SerialName("guide.image.end")
    data class GuideImageEnd(val guideId: String, val crc32: Long) : SessionMessage

    /**
     * The guide's reference photo, for the subject's thumbnail only (FR-3.9): a JPEG at most 512 px on its long side,
     * sent once per guide after its image and state, in chunks like the image. Never drawn as the guide or saved.
     */
    @Serializable
    @SerialName("guide.reference.begin")
    data class GuideReferenceBegin(
        val guideId: String,
        val format: String,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val chunks: Int,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, as for the guide image. */
    @Serializable
    @SerialName("guide.reference.chunk")
    data class GuideReferenceChunk(val guideId: String, val index: Int, val data: String) : SessionMessage

    @Serializable
    @SerialName("guide.reference.end")
    data class GuideReferenceEnd(val guideId: String, val crc32: Long) : SessionMessage

    /**
     * Realtime while a gesture runs; reliable when [final] (gesture ended). [by] is the subject when the state is
     * the echo of its `guide.move` (FR-7.2); a phone without the field means the photographer.
     */
    @Serializable
    @SerialName("guide.state")
    data class GuideStateUpdate(
        val state: GuideState,
        val final: Boolean,
        val by: Role = Role.PHOTOGRAPHER,
    ) : SessionMessage

    /**
     * The subject's drag or pinch on the guide [guideId], in frame coordinates (FR-7.2): the center and height it
     * asks for, realtime while the gesture runs and reliable when [final]. The photographer clamps and applies it,
     * and the echo is a `guide.state` with `by: SUBJECT`; a move for a guide it no longer shows is dropped, so a
     * gesture never crosses guide identities (Design 2.6.2).
     */
    @Serializable
    @SerialName("guide.move")
    data class GuideMove(
        val guideId: String,
        val cx: Float,
        val cy: Float,
        val height: Float,
        val final: Boolean = false,
    ) : SessionMessage

    @Serializable
    @SerialName("guide.clear")
    data object GuideClear : SessionMessage

    @Serializable
    @SerialName("camera.zoom.set")
    data class ZoomSet(
        val ratio: Float,
        /** True for the last value of a pinch (or a single tap). Only it travels reliably and gets a notice. */
        val final: Boolean = false,
    ) : SessionMessage

    /** A step of the subject's brightness slider, an exposure-compensation index, or its final value (FR-7.3). */
    @Serializable
    @SerialName("camera.exposure.set")
    data class ExposureSet(val index: Int, val final: Boolean = false) : SessionMessage

    /** The subject's flash choice (FR-7.3). Ignored, with an unchanged echo, when the camera has no flash. */
    @Serializable
    @SerialName("camera.flash.set")
    data class FlashSet(val mode: FlashMode) : SessionMessage

    /** The subject's focus point in frame coordinates (FR-7.7, Could). No echo: focus has no lasting state. */
    @Serializable
    @SerialName("camera.focus.set")
    data class FocusSet(val x: Float, val y: Float) : SessionMessage

    /** Whether the photographer allows remote control (FR-7.5, Could). */
    @Serializable
    @SerialName("control.permission")
    data class ControlPermission(val allowed: Boolean) : SessionMessage

    /**
     * The camera values actually applied, and who changed them. Iteration 2 adds [exposure], [flash], and [aspect],
     * so the subject always has the whole camera state and fits its video, grid, and guide to [aspect] (AD-10).
     */
    @Serializable
    @SerialName("camera.state")
    data class CameraStateUpdate(
        val zoom: Float,
        val by: Role,
        val final: Boolean,
        val exposure: Int = 0,
        val flash: FlashMode = FlashMode.OFF,
        val aspect: AspectRatio = AspectRatio.R4_3,
    ) : SessionMessage

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
