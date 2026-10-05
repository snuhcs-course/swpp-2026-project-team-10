package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * Draws the guide over the camera preview or the subject's live view (Design 2.6.2, 2.6.4).
 * Owner: Camera/Overlay (#6). The subject's phone uses it with [editable] = false (#9).
 */
class GuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frameBounds = RectF()
    private val guideBounds = RectF()
    private var guide: ReferenceGuide? = null
    private var state = GuideState()
    private var bitmap: Bitmap? = null

    init {
        // Static rendering and the subject's read-only overlay let camera zoom gestures reach the video below.
        isClickable = false
        isFocusable = false
    }

    /** Drag and pinch are handled only when true: on the photographer's camera. */
    var editable: Boolean = true

    /**
     * Called for each drag or pinch step. [dx] and [dy] are in frame coordinates, [scale] is the pinch factor,
     * and [final] is true when the gesture ends.
     */
    var onGesture: ((dx: Float, dy: Float, scale: Float, final: Boolean) -> Unit)? = null

    /** Shows [guide] at [state]; null hides it. */
    fun render(guide: ReferenceGuide?, state: GuideState) {
        if (this.guide === guide && this.state == state) return
        this.guide = guide
        this.state = state
        updateDrawing()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDrawing()
        postInvalidateOnAnimation()
    }

    private fun updateDrawing() {
        bitmap = null
        val guide = guide ?: return
        // The repository publishes the image and state separately. Do not draw one image at another image's state.
        if (!state.visible || state.guideId != guide.id || width <= 0 || height <= 0) return
        if (!guide.aspect.isFinite() || guide.aspect <= 0f ||
            !state.cx.isFinite() || !state.cy.isFinite() ||
            !state.height.isFinite() || state.height <= 0f || !state.opacity.isFinite()
        ) {
            return
        }

        val frame = GuideFrameTransform.fit(3f, 4f, width.toFloat(), height.toFloat())
        val center = frame.frameToView(state.cx, state.cy)
        val guideHeight = state.height * frame.height
        val guideWidth = guideHeight * guide.aspect
        val left = center.x - guideWidth / 2f
        val top = center.y - guideHeight / 2f
        val right = center.x + guideWidth / 2f
        val bottom = center.y + guideHeight / 2f
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return

        frameBounds.set(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height)
        guideBounds.set(left, top, right, bottom)
        paint.alpha = (state.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        bitmap = when (state.style) {
            GuideStyle.CUTOUT -> guide.cutout
            GuideStyle.OUTLINE -> guide.outline
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        if (image.isRecycled) return
        val saved = canvas.save()
        try {
            // Keep the full destination rectangle: intersecting it with the frame would squeeze the whole image.
            canvas.clipRect(frameBounds)
            canvas.drawBitmap(image, null, guideBounds, paint)
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
