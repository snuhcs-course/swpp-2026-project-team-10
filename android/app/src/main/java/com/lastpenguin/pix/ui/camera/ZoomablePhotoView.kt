// AI-generated with Claude Code, 2026-11-19, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.animation.doOnEnd
import com.lastpenguin.pix.ui.Motion
import kotlin.math.abs
import kotlin.math.max

/**
 * A photo of the photo viewer's pages (FR-1.6; Design 2.6.5), handled as a gallery app handles one. It shows the whole
 * photo at first. A pinch zooms it up to [MAX_ZOOM] times around the fingers, and a double tap zooms in on where it
 * lands, or out again. A drag pans it while zoomed, never past its edges. While zoomed it keeps every touch from its
 * parents, so a sideways drag pans the photo and does not turn the page. While whole, a downward drag moves it with the
 * finger: past [DISMISS_DP] or with a quick flick it closes the viewer ([Listener.onDismiss]), and otherwise it goes
 * back. A tap is [Listener.onTap]. Down and sideways are the photo's own, also while the viewer stands turned with the
 * phone (#207).
 */
internal class ZoomablePhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    /** What the viewer does with the photo's gestures. */
    interface Listener {
        /** A tap on the photo, not one of a double tap. */
        fun onTap()

        /** The photo is [offset] px below its place on its way to closing the viewer; 0 once it is back. */
        fun onDrag(offset: Float)

        /** The photo was dragged down far enough, or flicked down: the viewer closes. */
        fun onDismiss()
    }

    var listener: Listener? = null

    /** How far in: 1 shows the whole photo. */
    var zoom = 1f
        private set

    // The photo is drawn fit in the middle ([fit]), then zoomed by [zoom] around the view's corner, then moved by pan.
    private var panX = 0f
    private var panY = 0f
    private val fit = Matrix()
    private val fitted = RectF()
    private val drawn = Matrix()

    /** False while ImageView's constructor sets a drawable, before this class has its fields. */
    private var ready = false

    /** A downward drag to close the viewer is under way. */
    private var dragging = false

    /** How fast the finger moves on the screen, for a flick that closes; the photo's own coordinates move with it. */
    private var speed: VelocityTracker? = null

    /** How far the photo stands turned on the screen at the touch's start, in degrees clockwise, with its parents. */
    private var turned = 0f

    /** A pinch, or a double tap's drag, zoomed during this gesture: its last lift is no double tap. */
    private var scaled = false
    private var settling: ValueAnimator? = null
    private val density = resources.displayMetrics.density

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastX = 0f
            private var lastY = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (drawable == null) return false
                settling?.cancel()
                if (dragging) dropDrag()
                scaled = true
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = detector.focusX
                lastY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAround(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
                // The photo follows the fingers as they move together.
                panX += detector.focusX - lastX
                panY += detector.focusY - lastY
                lastX = detector.focusX
                lastY = detector.focusY
                keepInside()
                show()
                return true
            }
        },
    )

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                // At the second lift, unless the second touch zoomed by dragging.
                if (e.actionMasked != MotionEvent.ACTION_UP || scaled || drawable == null) return false
                if (zoom > 1f) settleTo(1f, 0f, 0f) else settleAround(DOUBLE_TAP_ZOOM, e.x, e.y)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (scaler.isInProgress || e2.pointerCount > 1) return false
                val start = e1 ?: return false
                // On the screen: the photo moves under the finger while it is dragged, and its own coordinates with it.
                val (across, down) = upright(e2.rawX - start.rawX, e2.rawY - start.rawY)
                when {
                    zoom > 1f -> {
                        panX -= distanceX
                        panY -= distanceY
                        keepInside()
                        show()
                    }

                    dragging -> drag(down)

                    // A drag that starts downward closes; one that starts sideways is the pages'.
                    down > abs(across) -> {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        drag(down)
                    }
                }
                return true
            }
        },
    )

    init {
        scaleType = ScaleType.MATRIX
        ready = true
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        if (!ready) return
        zoom = 1f
        panX = 0f
        panY = 0f
        fitPhoto()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitPhoto()
    }

    /** The whole photo again, in its place: for a page swiped away, and for one shown anew. */
    fun reset() {
        settling?.cancel()
        dragging = false
        translationY = 0f
        zoom = 1f
        panX = 0f
        panY = 0f
        keepInside()
        show()
    }

    // A tap goes through performClick, which TalkBack and lint expect of a view that reads its own touches.
    override fun performClick(): Boolean {
        super.performClick()
        listener?.onTap()
        return true
    }

    @Suppress("ClickableViewAccessibility") // performClick is called for a tap, in onSingleTapConfirmed.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A touch takes the photo where an animation was taking it.
                settling?.end()
                turned = turnOnScreen()
                scaled = false
                speed?.recycle()
                speed = VelocityTracker.obtain()
                // Zoomed, a sideways drag pans the photo, not the pages.
                if (zoom > 1f) parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger: a pinch, which the pages must not take, and no drag to close.
                parent?.requestDisallowInterceptTouchEvent(true)
                if (dragging) dropDrag()
            }
        }
        trackSpeed(event)
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    if (flicked() || translationY > DISMISS_DP * density) listener?.onDismiss() else springBack()
                }
                speed?.recycle()
                speed = null
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) dropDrag()
                speed?.recycle()
                speed = null
            }
        }
        return true
    }

    private fun trackSpeed(event: MotionEvent) {
        val tracker = speed ?: return
        val onScreen = MotionEvent.obtain(event)
        onScreen.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker.addMovement(onScreen)
        onScreen.recycle()
    }

    /** The drag to close ends with a quick flick down. */
    private fun flicked(): Boolean {
        val tracker = speed ?: return false
        tracker.computeCurrentVelocity(MS_PER_S)
        return upright(tracker.xVelocity, tracker.yVelocity).second > FLING_DP_PER_S * density
    }

    /** The sum of this view's and its parents' turns: the viewer's, while it stands turned with the phone. */
    private fun turnOnScreen(): Float =
        generateSequence<View>(this) { it.parent as? View }.sumOf { it.rotation.toDouble() }.toFloat()

    /** A move of ([x], [y]) on the screen, as across and down the photo, which may stand [turned]. */
    private fun upright(x: Float, y: Float): Pair<Float, Float> {
        if (turned % FULL_TURN == 0f) return x to y
        val move = floatArrayOf(x, y)
        Matrix().apply { setRotate(-turned) }.mapVectors(move)
        return move[0] to move[1]
    }

    private fun drag(offset: Float) {
        translationY = max(0f, offset)
        listener?.onDrag(translationY)
    }

    /** The drag to close stops, and the photo goes back to its place. */
    private fun dropDrag() {
        dragging = false
        springBack()
    }

    private fun springBack() {
        play(Motion.MOVE_MS, translationY, 0f) { offset -> drag(offset) }
    }

    /** Zooms to [target] around ([x], [y]) at once, within 1 and [MAX_ZOOM]. */
    private fun zoomAround(target: Float, x: Float, y: Float) {
        val next = target.coerceIn(1f, MAX_ZOOM)
        val factor = next / zoom
        // The point under (x, y) stays there.
        panX = factor * (panX - x) + x
        panY = factor * (panY - y) + y
        zoom = next
    }

    /** Zooms to [target] around ([x], [y]), as far as the photo's edges let it, in an animation. */
    private fun settleAround(target: Float, x: Float, y: Float) {
        val from = Triple(zoom, panX, panY)
        zoomAround(target, x, y)
        keepInside()
        val to = Triple(zoom, panX, panY)
        zoom = from.first
        panX = from.second
        panY = from.third
        settleTo(to.first, to.second, to.third)
    }

    private fun settleTo(targetZoom: Float, targetX: Float, targetY: Float) {
        val fromZoom = zoom
        val fromX = panX
        val fromY = panY
        play(Motion.MOVE_MS, 0f, 1f) { f ->
            zoom = fromZoom + (targetZoom - fromZoom) * f
            panX = fromX + (targetX - fromX) * f
            panY = fromY + (targetY - fromY) * f
            if (f == 1f) keepInside()
            show()
        }
    }

    /** Runs [step] from [from] to [to] in [ms], or at once while animations are off. */
    private fun play(ms: Long, from: Float, to: Float, step: (Float) -> Unit) {
        settling?.cancel()
        settling = null
        if (!Motion.on() || from == to) {
            step(to)
            return
        }
        val animation = ValueAnimator.ofFloat(from, to)
        animation.duration = ms
        animation.interpolator = Motion.STANDARD
        animation.addUpdateListener { step(it.animatedValue as Float) }
        // Forgotten once over: end() on an animation already over would play its last step again.
        animation.doOnEnd { if (settling === animation) settling = null }
        settling = animation
        animation.start()
    }

    /** The whole photo fit in the middle of the view, as a fitCenter ImageView draws it. */
    private fun fitPhoto() {
        val picture = drawable
        if (picture == null || width == 0 || height == 0 || picture.intrinsicWidth <= 0) {
            fit.reset()
            fitted.setEmpty()
        } else {
            val source = RectF(0f, 0f, picture.intrinsicWidth.toFloat(), picture.intrinsicHeight.toFloat())
            fit.setRectToRect(source, RectF(0f, 0f, width.toFloat(), height.toFloat()), Matrix.ScaleToFit.CENTER)
            fit.mapRect(fitted, source)
        }
        keepInside()
        show()
    }

    /** No gap at an edge the zoomed photo could fill, and in the middle along a side it does not fill. */
    private fun keepInside() {
        panX = inside(panX, fitted.left, fitted.right, width.toFloat())
        panY = inside(panY, fitted.top, fitted.bottom, height.toFloat())
    }

    private fun inside(pan: Float, start: Float, end: Float, size: Float): Float {
        val length = zoom * (end - start)
        if (length <= size) return (size - length) / 2 - zoom * start
        return pan.coerceIn(size - zoom * end, -zoom * start)
    }

    private fun show() {
        drawn.set(fit)
        drawn.postScale(zoom, zoom)
        drawn.postTranslate(panX, panY)
        imageMatrix = drawn
    }

    /** Where the photo is drawn in the view, as zoomed and moved; empty without one. */
    fun shownRect(): RectF = if (fitted.isEmpty) {
        RectF()
    } else {
        RectF(
            zoom * fitted.left + panX,
            zoom * fitted.top + panY,
            zoom * fitted.right + panX,
            zoom * fitted.bottom + panY,
        )
    }

    companion object {
        /** The furthest a pinch zooms in. */
        const val MAX_ZOOM = 4f

        /** How far a double tap zooms in. */
        const val DOUBLE_TAP_ZOOM = 2.5f

        /** How far down the photo is dragged before a lift closes the viewer. */
        const val DISMISS_DP = 96f

        /** How fast a flick down closes the viewer, however short. */
        const val FLING_DP_PER_S = 1000f
        private const val MS_PER_S = 1000
        private const val FULL_TURN = 360f
    }
}
