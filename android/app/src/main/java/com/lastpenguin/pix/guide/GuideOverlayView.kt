package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
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
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var ownsTouch = false
    private var gesture: TouchGesture? = null

    init {
        // Only a touch starting on an editable guide is consumed; the video below owns other gestures.
        isClickable = false
        isFocusable = false
    }

    /** Drag and pinch are handled only when true: on the photographer's camera. */
    var editable: Boolean = true
        set(value) {
            field = value
            if (!value) finishGesture()
        }

    /**
     * Called for each drag or pinch step. [dx] and [dy] are in frame coordinates, [scale] is the pinch factor,
     * and [final] is true when the gesture ends.
     */
    var onGesture: ((dx: Float, dy: Float, scale: Float, final: Boolean) -> Unit)? = null

    /** Shows [guide] at [state]; null hides it. */
    fun render(guide: ReferenceGuide?, state: GuideState) {
        if (this.guide === guide && this.state == state) return
        if (this.guide !== guide || state.guideId != this.state.guideId || !state.visible) {
            // A replacement is already committed in the repository. Never send an old gesture to the new image.
            finishGesture(sendFinal = this.guide === guide && this.state.guideId == state.guideId)
        }
        this.guide = guide
        this.state = state
        updateDrawing()
        if (!canEdit()) finishGesture()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        finishGesture()
        updateDrawing()
        postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) finishGesture()
    }

    override fun onDetachedFromWindow() {
        finishGesture()
        releaseTouch()
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) finishGesture()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            finishGesture()
            releaseTouch()
            if (!canEdit() || !frameBounds.contains(event.x, event.y) || !guideBounds.contains(event.x, event.y)) {
                return false
            }
            ownsTouch = true
            gesture = TouchGesture(event.getPointerId(0), position = TouchPosition(event.x, event.y, 0f))
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        if (!ownsTouch) return false
        if (!canEdit()) finishGesture()
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> moveGesture(event)

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Pointer changes can carry a last movement too; all old IDs are still in this event.
                moveGesture(event)
                rebasePointers(event)
            }

            MotionEvent.ACTION_UP -> {
                val wasTap = gesture?.moved == false
                moveGesture(event)
                val moved = gesture?.moved == true
                finishGesture()
                releaseTouch()
                if (wasTap && !moved) performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                finishGesture()
                releaseTouch()
            }
        }
        // Even if editing stops mid-stream, do not hand the remaining pointers to camera zoom.
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun canEdit(): Boolean =
        editable && isEnabled && visibility == VISIBLE && bitmap?.isRecycled == false && onGesture != null

    private fun moveGesture(event: MotionEvent) {
        val gesture = gesture ?: return
        val position = touchPosition(event, gesture) ?: return
        val previous = gesture.position
        val dx = position.x - previous.x
        val dy = position.y - previous.y
        val scale = if (previous.span > 0f && position.span > 0f) position.span / previous.span else 1f
        if (!dx.isFinite() || !dy.isFinite() || !scale.isFinite() || scale <= 0f) return
        if (!gesture.moved && hypot(dx, dy) <= touchSlop && abs(position.span - previous.span) <= touchSlop) return
        gesture.position = position
        if (dx == 0f && dy == 0f && scale == 1f) return
        gesture.moved = true
        // Offsets cancel when converting a delta: divide by the fitted image, never the letterboxed View.
        onGesture?.invoke(dx / frameBounds.width(), dy / frameBounds.height(), scale, false)
    }

    private fun rebasePointers(event: MotionEvent) {
        val gesture = gesture ?: return
        val lifted = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        var first = -1
        var second = -1
        for (index in 0 until event.pointerCount) {
            if (index == lifted) continue
            if (first == -1) {
                first = index
            } else if (second == -1) {
                second = index
            }
        }
        if (first == -1) return
        gesture.primaryId = event.getPointerId(first)
        gesture.secondaryId = if (second == -1) -1 else event.getPointerId(second)
        touchPosition(event, gesture)?.let { gesture.position = it }
    }

    private fun touchPosition(event: MotionEvent, gesture: TouchGesture): TouchPosition? {
        val first = event.findPointerIndex(gesture.primaryId)
        if (first == -1) return null
        val x = event.getX(first)
        val y = event.getY(first)
        if (gesture.secondaryId == -1) return TouchPosition(x, y, 0f)
        val second = event.findPointerIndex(gesture.secondaryId)
        if (second == -1) return null
        val otherX = event.getX(second)
        val otherY = event.getY(second)
        return TouchPosition((x + otherX) / 2f, (y + otherY) / 2f, hypot(otherX - x, otherY - y))
    }

    private fun finishGesture(sendFinal: Boolean = true) {
        val finished = gesture
        gesture = null
        if (sendFinal && finished?.moved == true) onGesture?.invoke(0f, 0f, 1f, true)
    }

    private fun releaseTouch() {
        ownsTouch = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private data class TouchPosition(val x: Float, val y: Float, val span: Float)

    private data class TouchGesture(
        var primaryId: Int,
        var secondaryId: Int = -1,
        var position: TouchPosition,
        var moved: Boolean = false,
    )

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
