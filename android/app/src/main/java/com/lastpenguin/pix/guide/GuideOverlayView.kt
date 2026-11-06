// AI-generated with ChatGPT Codex and Claude Code, 2026-11-05, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.guide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.animation.doOnEnd
import com.lastpenguin.pix.ui.Motion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws the guide over the camera preview or the subject's live view (Design 2.6.2, 2.6.4).
 * Owner: Camera/Overlay (#6). [editable] is the screen's guide-edit mode (AD-17, #48): while it is on, every touch
 * that starts inside the camera image edits the guide, so the camera never zooms by mistake; while it is off, every
 * touch passes through to the preview or live video below.
 * Changes of the guide itself are animated in drawing (Iter3-A13), apart from the view's own alpha, which the screens
 * fade with the grid: a new guide fades in as it grows from 95 %, a replaced one fades out meanwhile, and Hide, its
 * return, and Remove fade. A move, a resize, and the opacity are drawn at once, also during these, and the first
 * guide this view renders, such as the one already there when the screen opens, comes without any.
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

    /** The guide's outline at the settings' thickness, drawn instead of [ReferenceGuide.outline] while it is that guide's. */
    private var restyled: Pair<ReferenceGuide, Bitmap>? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var ownsTouch = false
    private var gesture: TouchGesture? = null

    /** Whether [render] has run: the first call shows what is there, without an animation. */
    private var rendered = false

    /** The guide drawn last, or hidden since: one that comes again is shown again, not a new guide. */
    private var lastId: String? = null

    /** The drawn guide's fade in and growth, 0 to 1; null while none runs. */
    private var appear: ValueAnimator? = null
    private var appearGrows = false

    /** The guide that went, drawn as it was while it fades out; null while none does. */
    private var leaving: Leaving? = null

    init {
        // Only a touch starting inside the camera image while editable is consumed; the video below owns the rest.
        isClickable = false
        isFocusable = false
    }

    /** Guide-edit mode: drag and pinch are handled only while true, and then anywhere inside the camera image. */
    var editable: Boolean = true
        set(value) {
            field = value
            if (!value) finishGesture()
        }

    /** The frame's width / height (AD-10): 3:4 by default, 9:16, or 1:1. The guide is placed in that image. */
    var frameAspect: Float = 3f / 4f
        set(value) {
            if (field == value || !value.isFinite() || value <= 0f) return
            field = value
            // A drag measured against the old image would land elsewhere in the new one.
            finishGesture()
            updateDrawing()
            postInvalidateOnAnimation()
        }

    /**
     * Called for each drag or pinch step. [dx] and [dy] are in frame coordinates, [scale] is the pinch factor,
     * and [final] is true when the gesture ends.
     */
    var onGesture: ((dx: Float, dy: Float, scale: Float, final: Boolean) -> Unit)? = null

    /**
     * The line's color from the settings (FR-3.10). The outline is drawn white, so another color tints it, and the
     * opacity still applies.
     */
    var lineColor: Int = Color.WHITE
        set(value) {
            if (field == value) return
            field = value
            paint.colorFilter = if (value == Color.WHITE) null else PorterDuffColorFilter(value, PorterDuff.Mode.SRC_IN)
            postInvalidateOnAnimation()
        }

    /**
     * Draws [outline] for [guide] instead of its own outline: the same mask traced at the settings' thickness
     * ([GuideOutlines]). Ignored for any other guide, so a late result never draws over a newer guide.
     */
    fun restyle(guide: ReferenceGuide?, outline: Bitmap?) {
        val next = if (guide != null && outline != null) guide to outline else null
        if (restyled?.first === next?.first && restyled?.second === next?.second) return
        restyled = next
        updateDrawing()
        postInvalidateOnAnimation()
    }

    /** Shows [guide] at [state]; null hides it. */
    fun render(guide: ReferenceGuide?, state: GuideState) {
        if (rendered && this.guide === guide && this.state == state) return
        if (this.guide !== guide || state.guideId != this.state.guideId || !state.visible) {
            // A replacement is already committed in the repository. Never send an old gesture to the new image.
            finishGesture(sendFinal = this.guide === guide && this.state.guideId == state.guideId)
        }
        val before = drawnId()
        val going = if (before !=
            null
        ) {
            Leaving(bitmap!!, RectF(guideBounds), RectF(frameBounds), drawnAlpha())
        } else {
            null
        }
        val replaced = this.guide !== guide
        this.guide = guide
        this.state = state
        updateDrawing()
        val after = drawnId()
        if (!rendered) {
            rendered = true
            lastId = after ?: guide?.id
        } else if (after != before) {
            // The state comes first and the animation follows it, so a tap meanwhile acts on what the state says.
            if (going != null) leave(going, if (replaced && guide != null) Motion.GUIDE_MS else Motion.HIDE_MS)
            if (after != null) {
                val comesBack = after == lastId
                show(grows = !comesBack, ms = if (comesBack) Motion.HIDE_MS else Motion.GUIDE_MS)
            } else {
                stopAppearing()
            }
        }
        if (after != null) lastId = after
        if (!canEdit()) finishGesture()
        postInvalidateOnAnimation()
    }

    /** The id of the guide drawn now, or null while none is. */
    private fun drawnId(): String? = guide?.id?.takeIf { bitmap != null }

    /** The drawn guide's alpha, 0 to 255, as it shows now, within its fade in. */
    private fun drawnAlpha(): Int = (paint.alpha * appearing).roundToInt()

    /** How far the drawn guide has come in, 0 to 1; 1 while it is not coming in (a test reads it). */
    internal val appearing: Float
        get() = appear?.animatedValue as? Float ?: 1f

    /** The drawn guide's coming in, while it runs: a test sets its fraction, since paused frames catch up at once. */
    internal val appearance: ValueAnimator?
        get() = appear

    /** Whether a guide that went is still fading out (a test reads it). */
    internal val fadingOut: Boolean
        get() = leaving != null

    private fun show(grows: Boolean, ms: Long) {
        stopAppearing()
        if (!Motion.on()) return
        appearGrows = grows
        appear = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = Motion.STANDARD
            addUpdateListener { postInvalidateOnAnimation() }
            doOnEnd { if (appear === it) appear = null }
            start()
        }
    }

    private fun stopAppearing() {
        appear?.let {
            appear = null
            it.cancel()
        }
    }

    private fun leave(going: Leaving, ms: Long) {
        stopLeaving()
        if (!Motion.on() || going.alpha == 0) return
        leaving = going
        going.fade = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = ms
            interpolator = Motion.STANDARD
            addUpdateListener { postInvalidateOnAnimation() }
            doOnEnd {
                if (leaving === going) leaving = null
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopLeaving() {
        leaving?.let {
            leaving = null
            it.fade?.cancel()
        }
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
        stopAppearing()
        stopLeaving()
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
            // In edit mode the whole image belongs to the guide, so it is easy to grab even by its transparent parts.
            if (!canEdit() || !frameBounds.contains(event.x, event.y)) return false
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

    /** A guide that went: its outline, where it was drawn, and its alpha then, which [fade] takes down to nothing. */
    private class Leaving(val bitmap: Bitmap, val bounds: RectF, val frame: RectF, val alpha: Int) {
        var fade: ValueAnimator? = null
    }

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

        val frame = GuideFrameTransform.fit(frameAspect, 1f, width.toFloat(), height.toFloat())
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
        // The guide is an outline only; the reference's pixels are never drawn (FR-2.2, AD-15).
        bitmap = restyled?.takeIf { it.first === guide }?.second ?: guide.outline
    }

    /** Where the guide is drawn in this view, inside the camera image, or null while none is (the tutorial's ring). */
    fun drawnGuideBounds(): RectF? {
        if (bitmap == null) return null
        val drawn = RectF(guideBounds)
        return drawn.takeIf { it.intersect(frameBounds) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        leaving?.let { going ->
            val fade = going.fade?.animatedValue as? Float ?: 0f
            draw(canvas, going.bitmap, going.bounds, going.frame, (going.alpha * fade).roundToInt(), scale = 1f)
        }
        val image = bitmap ?: return
        val coming = appearing
        val scale = if (appearGrows) START_SCALE + (1f - START_SCALE) * coming else 1f
        draw(canvas, image, guideBounds, frameBounds, (paint.alpha * coming).roundToInt(), scale)
    }

    private fun draw(canvas: Canvas, image: Bitmap, bounds: RectF, frame: RectF, alpha: Int, scale: Float) {
        if (image.isRecycled || alpha <= 0) return
        val opacity = paint.alpha
        val saved = canvas.save()
        try {
            // Keep the full destination rectangle: intersecting it with the frame would squeeze the whole image.
            canvas.clipRect(frame)
            canvas.scale(scale, scale, bounds.centerX(), bounds.centerY())
            paint.alpha = alpha
            canvas.drawBitmap(image, null, bounds, paint)
        } finally {
            paint.alpha = opacity
            canvas.restoreToCount(saved)
        }
    }

    private companion object {
        /** How large a new guide starts, before it grows to its size. */
        const val START_SCALE = 0.95f
    }
}
