// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.lastpenguin.pix.R

/**
 * Where a screen is in the pose flow (R&S 6.3, #210): a thin bar under the top bar, filled to the screen's [step] of
 * [STEPS], the scene photo, Generate poses, the set being generated, and Pick a pose. The accent, dimmed,
 * on a dark track, filled from the start edge; [moveTo] moves the fill there in [Motion.MOVE_MS]. A screen of the flow
 * that comes in fills on from where the last one left the bar ([arrive]), so the steps read as one screen whose bar
 * fills. Each screen's title already says where it is, so TalkBack skips it.
 * Owner: Pose generation (#210).
 */
class FlowProgressView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val track = Paint().apply { color = ContextCompat.getColor(context, R.color.pix_flow_track) }
    private val fill = Paint().apply { color = ContextCompat.getColor(context, R.color.pix_flow_progress) }

    /** How much of the bar shows filled now, while [moveTo] moves it; [fraction] once it is there. */
    private var shown = 0f

    @VisibleForTesting
    internal val filled: Float get() = shown
    private var move: ValueAnimator? = null

    /** The screen's place in the flow, 1 to [STEPS]; 0 fills nothing. Shows at once. */
    var step: Int = 0
        set(value) {
            val kept = value.coerceIn(0, STEPS)
            if (field == kept) return
            field = kept
            move?.cancel()
            shown = fraction
            invalidate()
        }

    /** How much of the bar is filled, 0 to 1. */
    val fraction: Float get() = step.toFloat() / STEPS

    /** The screen's own step from its layout, which the bar arrives at; 0 for one its screen sets (Camera's). */
    private val ownStep: Int

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        context.withStyledAttributes(attrs, R.styleable.FlowProgressView) {
            step = getInt(R.styleable.FlowProgressView_flowStep, 0)
        }
        ownStep = step
    }

    /**
     * The screen comes in at [to]: the fill moves there from where the last screen of the flow left it ([shownStep]), or
     * is there at once when that was this step.
     */
    fun arrive(to: Int = ownStep) {
        val from = shownStep
        if (from != to && from in 0..STEPS) {
            step = from
            moveTo(to)
        } else {
            step = to
        }
        shownStep = this.step
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ownStep > 0) arrive()
    }

    /**
     * Sets [step] and moves the fill there from where it shows, in [Motion.MOVE_MS]; at once while animations are off.
     * Again with the same step, a move under way goes on.
     */
    fun moveTo(step: Int) {
        val from = shown
        val before = this.step
        this.step = step
        if (this.step == before || !Motion.on()) return
        val to = shown
        shown = from
        move = ValueAnimator.ofFloat(from, to).apply {
            duration = Motion.MOVE_MS
            interpolator = Motion.STANDARD
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        move?.cancel()
        shown = fraction
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, track)
        val filled = w * shown
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            canvas.drawRect(w - filled, 0f, w, h, fill)
        } else {
            canvas.drawRect(0f, 0f, filled, h, fill)
        }
    }

    companion object {
        /**
         * The screens of the pose flow: the scene photo, Generate poses, generating, and Pick a pose. Five until the
         * photo's review went (#296).
         */
        const val STEPS = 4

        /**
         * The step the flow's bar last arrived at, on any screen, for the next screen to fill on from (#210); 0 once
         * the flow starts again, and on Subject view, which starts the subject's steps.
         */
        var shownStep = 0
    }
}
