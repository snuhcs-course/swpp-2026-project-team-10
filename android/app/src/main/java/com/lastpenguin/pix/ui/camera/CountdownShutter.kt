// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion

/**
 * The part of the timer's ring filled with [left] of [total] seconds to go (FR-1.17): empty as the countdown starts, a
 * third more at each second of a 3 s timer, and full at the photo. A [total] shorter than [left], such as a number that
 * comes before the timer's own length is known, counts from [left].
 */
internal fun countdownFill(left: Int, total: Int): Float {
    val whole = maxOf(total, left, 1)
    return ((whole - left).toFloat() / whole).coerceIn(0f, 1f)
}

/**
 * The shutter while the timer counts down (FR-1.17), on Camera and on Subject view: a stop square inside a ring that fills
 * clockwise from the top in the accent, so the shutter says that a tap stops the countdown, and how much of it is left.
 * The ring stands at a fill, or fills on from it to full over a time ([fillFrom]); it reads the clock as it draws and
 * draws again until full, so its pace is the clock's whatever the phone's animation speed. The square is the shutter's
 * white, dimmer under a finger; disabled, as Subject view's while a press waits for its answer, the whole of it is dim,
 * as the shutter is.
 */
internal class CountdownShutterDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val stroke = RING_DP * density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = context.getColor(R.color.camera_countdown_track)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.pix_accent)
    }
    private val white = context.getColor(R.color.camera_shutter)
    private val pressedWhite = context.getColor(R.color.camera_shutter_pressed)
    private val square = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white }
    private val ring = RectF()
    private val box = RectF()
    private var drawAlpha = OPAQUE
    private var enabled = true

    // Where the ring starts, when, and over how long it fills on to full; 0 stands still.
    private var from = 0f
    private var startMs = 0L
    private var runMs = 0L

    /** How much of the ring is filled now, from 0 at the countdown's start to 1 at the photo. */
    val fill: Float get() = fillAt(SystemClock.uptimeMillis())

    /** Whether the ring is still filling on. */
    val filling: Boolean get() = runMs > 0L && SystemClock.uptimeMillis() - startMs < runMs

    /** The square's color now, which shows the press (for tests). */
    @get:VisibleForTesting
    internal val squareColor: Int get() = square.color

    /** Whether the drawable is dimmed, as a disabled shutter is (for tests). */
    @get:VisibleForTesting
    internal val dimmed: Boolean get() = !enabled

    /** Stands at [fill] (held to 0..1), and with [runMs] fills on from it to full over that time, at an even pace. */
    fun fillFrom(fill: Float, runMs: Long = 0L) {
        from = fill.coerceIn(0f, 1f)
        startMs = SystemClock.uptimeMillis()
        this.runMs = runMs.coerceAtLeast(0L)
        invalidateSelf()
    }

    private fun fillAt(now: Long): Float {
        if (runMs <= 0L) return from
        val done = ((now - startMs).toFloat() / runMs).coerceIn(0f, 1f)
        return from + (1f - from) * done
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        if (size <= 0f) return
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val radius = size / 2f - stroke / 2f
        ring.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val shown = if (enabled) drawAlpha else (drawAlpha * DISABLED_ALPHA).toInt()
        val half = SQUARE_DP * density / 2f
        box.set(centerX - half, centerY - half, centerX + half, centerY + half)
        val corner = CORNER_DP * density
        val now = fill
        withAlpha(track, shown) { canvas.drawOval(ring, it) }
        if (now > 0f) withAlpha(arc, shown) { canvas.drawArc(ring, START_DEGREES, SWEEP_DEGREES * now, false, it) }
        withAlpha(square, shown) { canvas.drawRoundRect(box, corner, corner, it) }
        // The next frame draws it a little fuller, until it is full.
        if (filling) invalidateSelf()
    }

    /** Draws with [paint] at [alpha] of its own alpha, then sets its own back. */
    private inline fun withAlpha(paint: Paint, alpha: Int, draw: (Paint) -> Unit) {
        val own = paint.alpha
        paint.alpha = own * alpha / OPAQUE
        draw(paint)
        paint.alpha = own
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val pressed = android.R.attr.state_pressed in state
        val nowEnabled = android.R.attr.state_enabled in state
        val color = if (pressed) pressedWhite else white
        if (color == square.color && nowEnabled == enabled) return false
        square.color = color
        enabled = nowEnabled
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        drawAlpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = drawAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        listOf(track, arc, square).forEach { it.colorFilter = colorFilter }
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** As wide as the shutter's own white ring (bg_shutter). */
        const val RING_DP = 4f
        const val SQUARE_DP = 24f
        const val CORNER_DP = 6f
        const val START_DEGREES = -90f
        const val SWEEP_DEGREES = 360f
        const val OPAQUE = 255

        /** As camera_shutter_disabled dims the white. */
        const val DISABLED_ALPHA = 0.35f
    }
}

/**
 * Runs [drawable]'s ring through a countdown (FR-1.17). At each second's number it starts again from where that number
 * stands ([countdownFill]) and fills on, at an even pace, to the end in the seconds left, so a second that comes late
 * or not at all, as on the subject's phone, moves it only a little. With animations off it stands at each second's
 * place.
 */
internal class CountdownRing(
    context: Context,
    val drawable: CountdownShutterDrawable = CountdownShutterDrawable(context),
) {
    private var shown: Pair<Int, Int>? = null

    /** [left] of [total] seconds to go; nothing changes while the same number shows. */
    fun show(left: Int, total: Int) {
        if (shown == left to total) return
        shown = left to total
        val run = if (Motion.on()) left * CameraViewModel.COUNTDOWN_STEP_MS else 0L
        drawable.fillFrom(countdownFill(left, total), run)
    }

    /** The countdown ended or stopped: the ring stands empty for the next one. */
    fun stop() {
        shown = null
        drawable.fillFrom(0f)
    }
}
