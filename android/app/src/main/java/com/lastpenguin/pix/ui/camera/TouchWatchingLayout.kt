// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * The Camera screen's root: it tells [onTouchEnd] when a touch anywhere on the screen ends, before the views under it
 * see the last event, and takes nothing from them, so the touch still does what it does there. At the end and not the
 * start, so whatever this closes does not move the screen's controls under a finger still down.
 */
class TouchWatchingLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {

    var onTouchEnd: (() -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) onTouchEnd?.invoke()
        return super.dispatchTouchEvent(event)
    }
}
