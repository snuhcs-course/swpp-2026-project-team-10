package com.lastpenguin.pix.guide

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * Draws the guide over the camera preview or the subject's live view (Design 2.6.2, 2.6.4).
 * Owner: Camera/Overlay (#6). The subject's phone uses it with [editable] = false (#9).
 */
class GuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Drag and pinch are handled only when true: on the photographer's camera. */
    var editable: Boolean = true

    /**
     * Called for each drag or pinch step. [dx] and [dy] are in frame coordinates, [scale] is the pinch factor,
     * and [final] is true when the gesture ends.
     */
    var onGesture: ((dx: Float, dy: Float, scale: Float, final: Boolean) -> Unit)? = null

    /** Shows [guide] at [state]; null hides it. */
    fun render(guide: ReferenceGuide?, state: GuideState) {
        // TODO(#6): keep the values, invalidate(), and draw the cutout or outline in onDraw.
    }
}
