// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.lastpenguin.pix.R

/**
 * Where a screen is in the pose flow (R&S 6.3, #210): a thin bar under the top bar, filled to the screen's [step] of
 * [STEPS], the scene photo, its review, Generate poses, the set being generated, and Pick a pose. The accent, dimmed,
 * on a dark track, filled from the start edge. Each screen's title already says where it is, so TalkBack skips it.
 * Owner: Pose generation (#210).
 */
class FlowProgressView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val track = Paint().apply { color = ContextCompat.getColor(context, R.color.pix_flow_track) }
    private val fill = Paint().apply { color = ContextCompat.getColor(context, R.color.pix_flow_progress) }

    /** The screen's place in the flow, 1 to [STEPS]; 0 fills nothing. */
    var step: Int = 0
        set(value) {
            val kept = value.coerceIn(0, STEPS)
            if (field == kept) return
            field = kept
            invalidate()
        }

    /** How much of the bar is filled, 0 to 1. */
    val fraction: Float get() = step.toFloat() / STEPS

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        context.withStyledAttributes(attrs, R.styleable.FlowProgressView) {
            step = getInt(R.styleable.FlowProgressView_flowStep, 0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, track)
        val filled = w * fraction
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            canvas.drawRect(w - filled, 0f, w, h, fill)
        } else {
            canvas.drawRect(0f, 0f, filled, h, fill)
        }
    }

    companion object {
        /** The screens of the pose flow: the scene photo, its review, Generate poses, generating, and Pick a pose. */
        const val STEPS = 5
    }
}
