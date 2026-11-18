// AI-generated with Claude Code, 2026-11-16, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.ui.camera

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
