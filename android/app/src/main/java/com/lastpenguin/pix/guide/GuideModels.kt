// AI-generated with ChatGPT Codex and Claude Code, 2026-10-28, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

enum class GuideSource { GALLERY, GENERATED }

/** The person taken from a reference photo (Design 2.3). */
data class ReferenceGuide(
    /** UUID; the subject uses it to tell whether it already has the mask. */
    val id: String,
    /**
     * ALPHA_8, the smoothed mask (Design 2.6.1, step 4), cropped to the person: opaque is the person. It holds none of
     * the reference's pixels, and it is what the other phone receives (`mask-png`, AD-6, AD-15). Never drawn as the
     * guide, which is an outline only (FR-2.2).
     */
    val mask: Bitmap,
    /** Same size as [mask]; light stroke, about 8 px (3 dp) on screen. */
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
     * The reference photo itself, long side at most 512 px, for the Camera screen's corner thumbnail only (FR-3.9).
     * Never sent to the subject and never drawn as the guide; null on the subject.
     */
    val reference: Bitmap? = null,
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
