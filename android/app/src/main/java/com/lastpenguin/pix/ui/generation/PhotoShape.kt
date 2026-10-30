// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.lastpenguin.pix.guide.GuideGeometry
import kotlin.math.roundToInt

/*
 * The shape of the photos on the generation screens. Every candidate is made in the scene photo's ratio, 3:4, 9:16, or
 * 1:1 (FR-4.3, AD-10), so the scene and the candidates show whole in their own shape, never cut to another one.
 */

/** Width / height of [image], or of the default 3:4 frame while there is none. */
internal fun aspectOf(image: Bitmap?): Float =
    if (image == null || image.width <= 0 || image.height <= 0) {
        GuideGeometry.DEFAULT_FRAME_ASPECT
    } else {
        image.width.toFloat() / image.height
    }

/** Gives this image, inside a ConstraintLayout, the shape of [aspect] (width / height). */
internal fun ImageView.keepAspect(aspect: Float) {
    val ratio = aspect.toString()
    if ((layoutParams as ConstraintLayout.LayoutParams).dimensionRatio != ratio) {
        updateLayoutParams<ConstraintLayout.LayoutParams> { dimensionRatio = ratio }
    }
}

/** The scene photo's thumbnail at its layout height, as wide as the photo's shape needs. */
internal fun ImageView.showScene(scene: Bitmap?) {
    setImageBitmap(scene)
    val width = (layoutParams.height * aspectOf(scene)).roundToInt()
    if (layoutParams.width != width) updateLayoutParams { this.width = width }
}
