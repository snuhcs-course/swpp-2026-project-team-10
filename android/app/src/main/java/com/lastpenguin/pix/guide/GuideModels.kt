// AI-generated with ChatGPT Codex and Claude Code, 2026-11-25, reviewed by Jaewan Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import com.lastpenguin.pix.generation.Situation
import kotlinx.serialization.Serializable

enum class GuideSource { GALLERY, GENERATED }

/** The person taken from a reference photo (Design 2.3). */
data class ReferenceGuide(
    /** UUID; the subject uses it to tell whether it already has the mask. */
    val id: String,
    /**
     * ALPHA_8, the smoothed mask (Design 2.6.1, step 4), cropped to the person: opaque is the person. It holds none of
     * the reference's pixels, and it is what the other phone receives (`mask-png`, AD-6, AD-15). Never drawn as the
     * guide, which is an outline (FR-2.2); guide-edit mode tints its inside in one flat color (#271).
     */
    val mask: Bitmap,
    /** Same size as [mask]; light stroke, about 5.3 px (2 dp) on screen. */
    val outline: Bitmap,
    /** mask width / height. */
    val aspect: Float,
    val source: GuideSource,
    /**
     * The mask's box in the reference photo, where a new guide starts (FR-3.1). The subject's mirror gets it with the
     * image for its *Save guide* (FR-6.16), but places the guide from the photographer's `guide.state`.
     */
    val anchor: NormalizedRect? = null,
    /**
     * The reference photo itself, long side at most 512 px, for the corner thumbnail only (FR-3.9). It goes to the
     * other phone apart from the mask (`guide.reference.*`, Design 2.5.1) and is never drawn as the guide; a guide
     * adopted from the subject has the subject's, and the subject keeps the photographer's in `MirrorReference`.
     */
    val reference: Bitmap? = null,
    /**
     * The picked image this guide was cut from, and where (FR-2.10): a screenshot of a feed post cut to its photo, which
     * [mask], [anchor], and [reference] are made from. Null when the image was used whole, and for a generated pose.
     * Stays on this phone: never sent and not saved, and dropped once the guide is used.
     */
    val crop: PhotoCrop? = null,
    /**
     * The picked photo, used whole, long side at most 1024 px, which Reference confirm shows turning into this guide
     * (FR-2.3, #295). Null for a screenshot cut to its photo, whose [crop] holds it, and for a generated pose. Stays on
     * this phone like [crop].
     */
    val photo: Bitmap? = null,
    /**
     * The situation a generated pose was made for (FR-3.11, FR-4.8), which the library keeps and filters by; null for
     * a photo guide, and for a pose from an older photographer that did not send it (#270).
     */
    val situation: Situation? = null,
)

/**
 * This guide for [situation], or this very guide when that changes nothing: the screens tell a guide from its copy by
 * instance, as `PoseTips` does, so a guide without a situation must stay the one that was made.
 */
fun ReferenceGuide.forSituation(situation: Situation?): ReferenceGuide =
    if (situation == this.situation) this else copy(situation = situation)

/**
 * This guide without the picked image Reference confirm showed ([ReferenceGuide.crop], [ReferenceGuide.photo]), as it
 * goes on the camera, so the camera does not hold it; this very guide when it has none, like [forSituation].
 */
fun ReferenceGuide.withoutPicked(): ReferenceGuide =
    if (crop == null && photo == null) this else copy(crop = null, photo = null)

/** A picked image and the part of it a guide was made from (FR-2.10). */
data class PhotoCrop(
    /** The whole picked image, long side at most 1024 px. */
    val original: Bitmap,
    /** The part kept, as fractions of [original]. */
    val photo: NormalizedRect,
)

/** A box as fractions of an image's width and height, right and bottom exclusive (FR-3.1). */
@Serializable
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** Everything needed to place the guide, in frame coordinates (Design 2.3, 2.6.2). */
@Serializable
data class GuideState(
    /** null = no guide. */
    val guideId: String? = null,
    /** Guide center relative to the frame (AD-10); may extend outside 0..1 while part of the guide remains visible. */
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    /** Guide height / frame height. */
    val height: Float = DEFAULT_HEIGHT,
    val opacity: Float = 0.5f,
    val visible: Boolean = true,
) {
    companion object {
        const val DEFAULT_HEIGHT = 0.7f // FR-3.1
        const val MIN_HEIGHT = 0.21f // 30 % of the start (FR-3.3)
        const val MAX_HEIGHT = 2.1f // 300 % of the start
        const val MIN_OPACITY = 0.1f // FR-3.4
        const val MAX_OPACITY = 0.9f
    }
}
