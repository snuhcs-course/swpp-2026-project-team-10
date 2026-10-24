// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Touch input for the reference thumbnail on the Camera screen (FR-3.9). A tap goes to [onTap], which enlarges the
 * thumbnail or makes it small again. A drag past the touch slop moves it with the finger, kept inside [area], and on
 * release reports the nearest corner to [onDrop], so the thumbnail never stays anywhere else; a cancelled drag
 * reports null, and the caller puts the thumbnail back in its corner. The view is placed by its translation.
 */
internal class ReferenceDragController(
    private val view: View,
    /** Where the thumbnail may be, in its parent's pixels; read at every step. */
    private val area: () -> ReferenceArea,
    private val onTap: () -> Unit,
    private val onDrop: (ReferenceCorner?) -> Unit,
) {
    /** While true, the finger places the thumbnail and the screen must not move it. */
    var isDragging: Boolean = false
        private set
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f

    init {
        view.setOnClickListener { onTap() }
        attach()
    }

    @SuppressLint("ClickableViewAccessibility") // A tap still goes through performClick, as TalkBack's does.
    private fun attach() {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // The thumbnail may still be gliding into a corner: it starts from where it is now.
                    view.animate().cancel()
                    downX = event.rawX
                    downY = event.rawY
                    startX = view.translationX
                    startY = view.translationY
                    isDragging = false
                }

                MotionEvent.ACTION_MOVE -> move(event.rawX - downX, event.rawY - downY)

                MotionEvent.ACTION_UP -> if (isDragging) {
                    isDragging = false
                    val centerX = view.translationX + view.width / 2f
                    val centerY = view.translationY + view.height / 2f
                    onDrop(nearestCorner(centerX, centerY, area()))
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

    private fun move(dx: Float, dy: Float) {
        if (!isDragging && dx * dx + dy * dy <= slop.toFloat() * slop) return
        isDragging = true
        val bounds = area()
        val maxX = maxOf(bounds.left, bounds.right - view.width).toFloat()
        val maxY = maxOf(bounds.top, bounds.bottom - view.height).toFloat()
        view.translationX = (startX + dx).coerceIn(bounds.left.toFloat(), maxX)
        view.translationY = (startY + dy).coerceIn(bounds.top.toFloat(), maxY)
    }
}
