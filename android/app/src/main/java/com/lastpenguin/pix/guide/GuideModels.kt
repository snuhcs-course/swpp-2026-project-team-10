package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

enum class GuideStyle { CUTOUT, OUTLINE }

enum class GuideSource { GALLERY, GENERATED }

/** The person taken from a reference photo (Design 2.3). */
data class ReferenceGuide(
    /** UUID; the subject uses it to tell whether it already has the image. */
    val id: String,
    /** ARGB_8888, person only, transparent background, cropped to the person. */
    val cutout: Bitmap,
    /** Same size as [cutout]; 2–3 px light stroke. */
    val outline: Bitmap,
    /** cutout width / height. */
    val aspect: Float,
    val source: GuideSource,
)

/** Everything needed to place the guide, in frame coordinates (Design 2.3, 2.6.2). */
@Serializable
data class GuideState(
    /** null = no guide. */
    val guideId: String? = null,
    /** Guide center, 0..1 of the 3:4 portrait frame. */
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    /** Guide height / frame height. */
    val height: Float = DEFAULT_HEIGHT,
    val opacity: Float = 0.5f,
    val style: GuideStyle = GuideStyle.OUTLINE,
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
