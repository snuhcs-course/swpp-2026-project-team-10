// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.lastpenguin.pix.R

/**
 * The red light at the top right of the camera's image while a session streams it, on both phones (FR-6.8): a dot
 * that blinks while it is shown, and stays lit when the system turns animations off. It takes no touches; its
 * content description says who is connected.
 */
class LiveDotView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.pix_recording)
        alpha = GLOW_ALPHA
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.pix_recording) }
    private val blink = ObjectAnimator.ofFloat(this, ALPHA, 1f, DIM_ALPHA).apply {
        duration = BLINK_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    /** Puts the dot [MARGIN_DP] inside the top right corner of the image of [frameAspect] fitted in [parent]. */
    fun placeIn(parent: View, frameAspect: Float) {
        val frame = fitViewfinderFrame(parent.width.toFloat(), parent.height.toFloat(), frameAspect)
        val margin = MARGIN_DP * resources.displayMetrics.density
        translationX = frame.right - margin - layoutParams.width
        translationY = frame.top + margin
    }

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, glow)
        canvas.drawCircle(width / 2f, height / 2f, radius * DOT_SHARE, dot)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && ValueAnimator.areAnimatorsEnabled()) {
            if (!blink.isStarted) blink.start()
        } else {
            blink.cancel()
            alpha = 1f
        }
    }

    override fun onDetachedFromWindow() {
        blink.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val BLINK_MS = 700L
        const val DIM_ALPHA = 0.2f
        const val GLOW_ALPHA = 90
        const val DOT_SHARE = 0.55f
        const val MARGIN_DP = 12f
    }
}
