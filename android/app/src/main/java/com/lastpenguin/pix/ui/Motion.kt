// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui

import android.animation.Animator
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import com.lastpenguin.pix.R

/**
 * How long Pix's animations last and whether they run (Design 2.6.2, Iter3-A12). Each lasts 300 ms or less and
 * plays while Pix waits anyway, such as for the camera to rebind, so none makes anything slower. None runs while the
 * phone's animations are off: each change then shows its end at once.
 */
internal object Motion {
    /** A frame's edges from one ratio to the next. */
    const val MOVE_MS = 250L

    /** The held frame turning over, top over bottom, as the other camera opens. */
    const val FLIP_MS = 300L

    /** Something coming or going: a held frame, a dim, the grid and the guide. */
    const val FADE_MS = 200L

    /** The image dimming while it waits. */
    const val DIM_MS = 150L

    /** The image's blink at the shutter, down and back. */
    const val BLINK_MS = 120L

    /** A new guide coming in as it grows from 95 %, and a replaced one going (Iter3-A13). */
    const val GUIDE_MS = 250L

    /** A guide hidden, shown again, or removed. */
    const val HIDE_MS = 150L

    /** A ♥ growing and settling once as it saves a pose. */
    const val POP_MS = 200L

    /** Material's standard curve: quick to start, slow to settle. */
    val STANDARD = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** False while the phone's animations are off (Remove animations, or an animator duration scale of 0). */
    fun on(): Boolean = ValueAnimator.areAnimatorsEnabled()
}

/**
 * Fades this view to [alpha] in [ms], replacing a fade under way, and runs [end] once it is there; while animations
 * are off, at once.
 */
internal fun View.fadeTo(alpha: Float, ms: Long, end: (() -> Unit)? = null) {
    animate().cancel()
    if (!Motion.on()) {
        this.alpha = alpha
        end?.invoke()
        return
    }
    val fade = animate().alpha(alpha).setDuration(ms).setInterpolator(Motion.STANDARD)
    if (end != null) fade.withEndAction(end)
    fade.start()
}

/** Grows this view to [POP_SCALE] and back once, in [Motion.POP_MS]; nothing while animations are off. */
internal fun View.pop() {
    unpop()
    if (!Motion.on()) return
    val grow = arrayOf(Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(POP_PEAK, POP_SCALE), Keyframe.ofFloat(1f, 1f))
    val animator = ObjectAnimator.ofPropertyValuesHolder(
        this,
        PropertyValuesHolder.ofKeyframe(View.SCALE_X, *grow),
        PropertyValuesHolder.ofKeyframe(View.SCALE_Y, *grow.map { it.clone() }.toTypedArray()),
    )
    animator.duration = Motion.POP_MS
    animator.interpolator = Motion.STANDARD
    setTag(R.id.pop, animator)
    animator.start()
}

/** Stops a [pop] under way, the view at its own size, as when it is bound again. */
internal fun View.unpop() {
    (getTag(R.id.pop) as? Animator)?.cancel()
    setTag(R.id.pop, null)
    scaleX = 1f
    scaleY = 1f
}

/** How large a ♥ grows, and how far into its pop it is largest. */
private const val POP_SCALE = 1.25f
private const val POP_PEAK = 0.4f
