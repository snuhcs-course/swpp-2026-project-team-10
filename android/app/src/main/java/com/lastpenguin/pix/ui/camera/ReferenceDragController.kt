// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

/** The moves of a drag that say how fast it ended: those of its last [FLING_WINDOW_MS]. */
private const val FLING_WINDOW_MS = 100L

/**
 * Touch input for the reference thumbnail on the Camera screen (FR-3.9). A tap goes to [onTap], which enlarges the
 * thumbnail or makes it small again, or shows a hidden one. A drag past the touch slop moves it with the finger:
 * inside [area]'s height, and sideways out of the preview until only its peek shows ([referencePeek]), fading as it
 * goes (#237). On release it reports to [onDrop] the nearest corner and whether it is hidden there ([dropReference]),
 * so the thumbnail never stays anywhere else; a cancelled drag reports null, and the caller puts the thumbnail back
 * where it was. The view is placed by its translation.
 */
internal class ReferenceDragController(
    private val view: View,
    /** Where the thumbnail may be, in its parent's pixels; read at every step. */
    private val area: () -> ReferenceArea,
    /** The preview's width, whose sides a hidden thumbnail waits at; read at every step. */
    private val previewWidth: () -> Int,
    /** How much of a hidden thumbnail stays inside the preview, in pixels. */
    private val peek: Int,
    /** How fast a drag toward the side must end to hide the thumbnail, in pixels a second. */
    private val flingSpeed: Float,
    private val onTap: () -> Unit,
    private val onDrop: (ReferenceDrop?) -> Unit,
) {
    /** While true, the finger places the thumbnail and the screen must not move it. */
    var isDragging: Boolean = false
        private set
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    /** The drawn box's left and top when the drag began. */
    private var startX = 0f
    private var startY = 0f

    /** Where the finger was and when, for the speed the drag ends with: time in ms, then x and y. */
    private val moves = ArrayDeque<Triple<Long, Float, Float>>()

    init {
        view.setOnClickListener { onTap() }
        listenOn(view)
    }

    /**
     * Takes [source]'s touches as the thumbnail's own: the thumbnail itself, and the wider handle that lies over a
     * hidden thumbnail's strip, which alone is too narrow for a finger.
     */
    @SuppressLint("ClickableViewAccessibility") // A tap still goes through performClick, as TalkBack's does.
    fun listenOn(source: View) {
        source.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isDragging = false
                    moves.clear()
                    remember(event)
                }

                MotionEvent.ACTION_MOVE -> {
                    remember(event)
                    move(event.rawX - downX, event.rawY - downY)
                }

                MotionEvent.ACTION_UP -> if (lift(event)) {
                    isDragging = false
                    val (velocityX, velocityY) = speedAt(event.eventTime)
                    onDrop(
                        dropReference(
                            // The view scales about its middle, so this is the drawn box's middle too.
                            centerX = view.translationX + view.width / 2f,
                            centerY = view.translationY + view.height / 2f,
                            velocityX = velocityX,
                            velocityY = velocityY,
                            area = area(),
                            previewWidth = previewWidth(),
                            flingSpeed = flingSpeed,
                        ),
                    )
                } else {
                    view.performClick()
                }

                MotionEvent.ACTION_CANCEL -> if (isDragging) {
                    isDragging = false
                    onDrop(null)
                }
            }
            true
        }
    }

    /**
     * The lift may carry the last of the move, which no move event brought: it counts for the thumbnail's place and
     * for the speed, and a lift far from the touch is a drag even with no move between. True for a drag.
     */
    private fun lift(event: MotionEvent): Boolean {
        remember(event)
        move(event.rawX - downX, event.rawY - downY)
        return isDragging
    }

    private fun remember(event: MotionEvent) {
        moves.addLast(Triple(event.eventTime, event.rawX, event.rawY))
        while (moves.first().first < event.eventTime - FLING_WINDOW_MS) moves.removeFirst()
    }

    /** The finger's speed over its last moves before [time], in pixels a second; none after a pause. */
    private fun speedAt(time: Long): Pair<Float, Float> {
        val recent = moves.filter { it.first >= time - FLING_WINDOW_MS }
        val first = recent.firstOrNull() ?: return 0f to 0f
        val last = recent.last()
        val seconds = (last.first - first.first) / 1000f
        if (seconds <= 0f) return 0f to 0f
        return (last.second - first.second) / seconds to (last.third - first.third) / seconds
    }

    private fun move(dx: Float, dy: Float) {
        if (!isDragging) {
            if (dx * dx + dy * dy <= slop.toFloat() * slop) return
            // The drag begins. A thumbnail still gliding to its place stops only now, and goes with the finger from
            // where it is: a tap leaves the glide running, or a second tap would hold it still while it is pressed.
            view.stopReferenceGlide()
            startX = view.translationX + insetX
            startY = view.translationY + insetY
            isDragging = true
        }
        val bounds = area()
        val width = previewWidth()
        val wide = drawnWidth
        val maxY = maxOf(bounds.top.toFloat(), bounds.bottom - drawnHeight)
        // Sideways it may leave the preview, as far as a hidden thumbnail is out.
        val inside = referencePeek(wide.roundToInt(), peek)
        val minX = minOf(bounds.left.toFloat(), inside - wide)
        val maxX = maxOf(bounds.left.toFloat(), bounds.right - wide, (width - inside).toFloat())
        val left = (startX + dx).coerceIn(minX, maxX)
        view.translationX = left - insetX
        view.translationY = (startY + dy).coerceIn(bounds.top.toFloat(), maxY) - insetY
        view.alpha = referenceAlpha(left, wide.roundToInt(), width, peek)
    }

    // The thumbnail's box as drawn. A glide the drag stopped leaves the view drawn smaller than it is laid out, about
    // its middle, and the finger holds what is drawn: its place, how far it may go, and its fade follow that box.
    private val drawnWidth: Float get() = view.width * abs(view.scaleX)
    private val drawnHeight: Float get() = view.height * view.scaleY

    /** How far the drawn box's left and top are inside the view's own, which the translation places. */
    private val insetX: Float get() = (view.width - drawnWidth) / 2f
    private val insetY: Float get() = (view.height - drawnHeight) / 2f
}
