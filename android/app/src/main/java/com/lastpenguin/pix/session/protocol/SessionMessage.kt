// AI-generated with Claude Code, 2026-10-31, reviewed by Joonhyung Han, Sungmin Jo, and Dongje Park
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
     * [format] is `mask-png` (Design 2.5.1); [aspect] is the mask's width / height. [anchor] and [source] come from the
     * subject for a guide made on its phone, which the photographer starts at [anchor] (Iter3-P9), and from the
     * photographer for the subject's *Save guide*, whose guide its `guide.state` still places (Iter3-P16).
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

    /**
     * Where the photographer's set of candidates [setId] is (Design 2.5.1, 2.6.5, Iter3-P16): the subject's *Pick a
     * pose* follows it. [poseIds] are the created candidates in the photographer's order, with PICKING; [poseId] and
     * [by] say whose *Use this pose* decided, with CHOSEN. A set ends once, with CHOSEN or CLOSED.
     */
    @Serializable
    @SerialName("pose.step")
    data class PoseStep(
        val setId: String,
        val step: PoseSetStep,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val poseIds: List<String> = emptyList(),
        @EncodeDefault(EncodeDefault.Mode.NEVER) val poseId: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val by: Role? = null,
    ) : SessionMessage

    /**
     * A copy of the candidate [poseId] of the set [setId], for the subject's *Pick a pose* only: a JPEG at most 1024 px
     * on its long side, in chunks like the guide image. Never drawn as the guide or saved.
     */
    @Serializable
    @SerialName("pose.candidate.begin")
    data class PoseCandidateBegin(
        val setId: String,
        val poseId: String,
        val format: String,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val chunks: Int,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, as for the guide image. */
    @Serializable
    @SerialName("pose.candidate.chunk")
    data class PoseCandidateChunk(val setId: String, val poseId: String, val index: Int, val data: String) :
        SessionMessage

    @Serializable
    @SerialName("pose.candidate.end")
    data class PoseCandidateEnd(val setId: String, val poseId: String, val crc32: Long) : SessionMessage

    /**
     * The subject's *Use this pose* on the candidate [poseId] (FR-6.15), a request like its others (AD-7): the
     * photographer takes it only while the set [setId] is PICKING and nobody has chosen, and answers with `pose.step`.
     */
    @Serializable
    @SerialName("pose.pick")
    data class PosePick(val setId: String, val poseId: String) : SessionMessage

    /**
     * The photographer's *Generate poses* [descriptionId] as the subject's copy follows it (Design 2.5.1, 2.6.5,
     * Iter3-P16): with OPEN, the description's [text], the phone whose field holds it ([writer]; null while nobody
     * types), and whether the service refused the last set's description ([rejected]). It ends once, with STARTED
     * when a set starts from it, or CLOSED.
     */
    @Serializable
    @SerialName("pose.description")
    data class PoseDescription(
        val descriptionId: String,
        val step: DescriptionStep,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val text: String = "",
        @EncodeDefault(EncodeDefault.Mode.NEVER) val writer: Role? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val rejected: Boolean = false,
    ) : SessionMessage

    /**
     * A copy of the scene photo of *Generate poses* [descriptionId], for the subject's *Generate poses* only: a JPEG at
     * most 1024 px on its long side, like a candidate's copy, in chunks like the guide image. Never sent to the server
     * or saved.
     */
    @Serializable
    @SerialName("pose.scene.begin")
    data class PoseSceneBegin(
        val descriptionId: String,
        val format: String,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val chunks: Int,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, as for the guide image. */
    @Serializable
    @SerialName("pose.scene.chunk")
    data class PoseSceneChunk(val descriptionId: String, val index: Int, val data: String) : SessionMessage

    @Serializable
    @SerialName("pose.scene.end")
    data class PoseSceneEnd(val descriptionId: String, val crc32: Long) : SessionMessage

    /**
     * The subject's description field (FR-6.15), a request like its others (AD-7): [holding] while it types, with its
     * [text] so far, then false with its last text. The photographer takes it only while its own field is free, and
     * answers with `pose.description`.
     */
    @Serializable
    @SerialName("pose.description.edit")
    data class PoseDescriptionEdit(val descriptionId: String, val holding: Boolean, val text: String) : SessionMessage

    /**
     * The subject's *Generate 4 poses* with its [text], or *Skip* when [suggest]: the photographer starts the set from
     * *Generate poses* [descriptionId] unless its own field holds the description, and answers with `pose.description`
     * STARTED.
     */
    @Serializable
    @SerialName("pose.description.go")
    data class PoseDescriptionGo(val descriptionId: String, val text: String, val suggest: Boolean) : SessionMessage

    /**
     * A photo taken in a session, for the subject's "Pix" album (FR-6.14, Design 2.6.5): the JPEG as it was saved, of
     * [bytes] in [chunks] chunks, taken at [takenAt].
     */
    @Serializable
    @SerialName("photo.begin")
    data class PhotoBegin(
        val photoId: String,
        val format: String,
        val bytes: Long,
        val chunks: Int,
        val takenAt: Long,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, as for the guide image; the chunks come in order, one photo at a time. */
    @Serializable
    @SerialName("photo.chunk")
    data class PhotoChunk(val photoId: String, val index: Int, val data: String) : SessionMessage

    @Serializable
    @SerialName("photo.end")
    data class PhotoEnd(val photoId: String, val crc32: Long) : SessionMessage

    /** The subject expects the chunk [next] now, so the photographer may send on (Design 2.5.1). */
    @Serializable
    @SerialName("photo.ack")
    data class PhotoAck(val photoId: String, val next: Int) : SessionMessage

    /** Whether the photo is in the subject's album; without it, [reason] says why not. */
    @Serializable
    @SerialName("photo.saved")
    data class PhotoSaved(
        val photoId: String,
        val ok: Boolean,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val reason: PhotoRefusal? = null,
    ) : SessionMessage

    /**
     * Where the photographer's Reference confirm [confirmId] of a pose chosen in a session is (Design 2.5.1, 2.6.5,
     * Iter3-P16): the subject's *Your reference* follows it. [guideId] names the outline on screen, with READY, and the
     * guide used, with USED and [by] whose *Use this guide* it was. A confirm ends once, with USED or CLOSED.
     */
    @Serializable
    @SerialName("guide.confirm")
    data class GuideConfirm(
        val confirmId: String,
        val step: ConfirmStep,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val guideId: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val by: Role? = null,
    ) : SessionMessage

    /**
     * The mask of the outline [guideId] on Reference confirm [confirmId], for the subject's *Your reference* only:
     * `mask-png` like the guide image, from which the subject traces the outline, in chunks like it.
     */
    @Serializable
    @SerialName("guide.confirm.image.begin")
    data class GuideConfirmImageBegin(
        val confirmId: String,
        val guideId: String,
        val format: String,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val chunks: Int,
    ) : SessionMessage

    /** [data] is Base64 of at most 12 KiB, as for the guide image. */
    @Serializable
    @SerialName("guide.confirm.image.chunk")
    data class GuideConfirmImageChunk(val confirmId: String, val guideId: String, val index: Int, val data: String) :
        SessionMessage

    @Serializable
    @SerialName("guide.confirm.image.end")
    data class GuideConfirmImageEnd(val confirmId: String, val guideId: String, val crc32: Long) : SessionMessage

    /**
     * The subject's *Use this guide* on the outline [guideId] (FR-6.15), a request like its others (AD-7): the
     * photographer takes it only while its Reference confirm [confirmId] shows that outline, and answers with
     * `guide.confirm`.
     */
    @Serializable
    @SerialName("guide.confirm.use")
    data class GuideConfirmUse(val confirmId: String, val guideId: String) : SessionMessage

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

/**
 * The steps of the photographer's set of candidates (Design 2.6.5): GENERATING from *Generate 4 poses* or *Skip*,
 * PICKING once its *Pick a pose* opens, then CHOSEN by the first *Use this pose*, or CLOSED without a choice.
 */
@Serializable
enum class PoseSetStep { GENERATING, PICKING, CHOSEN, CLOSED }

/** Why a session photo is not in the subject's album (Design 2.5.1, `photo.saved`). */
@Serializable
enum class PhotoRefusal { TOO_LARGE, BROKEN, STORAGE }

/**
 * The steps of the photographer's *Generate poses* in a session (Design 2.6.5): OPEN while the description is written,
 * then STARTED by the first *Generate 4 poses* or *Skip*, or CLOSED without a set.
 */
@Serializable
enum class DescriptionStep { OPEN, STARTED, CLOSED }

/**
 * The steps of the photographer's Reference confirm of a pose chosen in a session (Design 2.6.5): FINDING while the
 * person is found, again after *Choose another photo*, READY with the outline, then USED by the first *Use this
 * guide*, or CLOSED without a guide.
 */
@Serializable
enum class ConfirmStep { FINDING, READY, USED, CLOSED }
