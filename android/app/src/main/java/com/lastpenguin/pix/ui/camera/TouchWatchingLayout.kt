// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * The root of the Camera screen and of Subject view: it tells [onTouchEnd] when a touch anywhere on the screen ends, with its last event, before
 * the views under it see that event, and takes nothing from them, so the touch still does what it does there. At the
 * end and not the start, so whatever this closes does not move the screen's controls under a finger still down.
 */
class TouchWatchingLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {

    var onTouchEnd: ((MotionEvent) -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) onTouchEnd?.invoke(event)
        return super.dispatchTouchEvent(event)
    }
}

/** Whether [event], in [root]'s coordinates, is on this view as it shows. */
internal fun View.isUnder(event: MotionEvent, root: View): Boolean {
    if (!isShown) return false
    val at = IntArray(2).also(::getLocationInWindow)
    val origin = IntArray(2).also(root::getLocationInWindow)
    val x = event.x + origin[0] - at[0]
    val y = event.y + origin[1] - at[1]
    return x >= 0f && x < width && y >= 0f && y < height
}

/**
 * Keeps a touch on this view from reaching the image under it, without making the view a button: a tap on it does
 * nothing, and TalkBack offers it no action. For a panel over the image that a tap must not focus through, such as the
 * open pose tip, which only its own button closes (FR-4.10).
 */
@SuppressLint("ClickableViewAccessibility") // It takes no tap, so there is no click to perform.
internal fun View.keepTouches() {
    setOnTouchListener { _, _ -> true }
}
