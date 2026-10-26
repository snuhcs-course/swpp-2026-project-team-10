// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.lastpenguin.pix.R

/** Viewfinder-only composition aids. Never consumes gestures or enters captured/streamed frames. */
class CameraCompositionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(135, 255, 255, 255)
        strokeWidth = density * 0.75f
    }
    private val gridOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 0, 0, 0)
        strokeWidth = density * 1.5f
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density * 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val barOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 0, 0)
        strokeWidth = density * 4.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val dim = Paint().apply { color = Color.argb(DIM_ALPHA, 0, 0, 0) }
    private val levelColor = context.getColor(R.color.camera_level_aligned)
    private var frame = ViewfinderFrame(0f, 0f, 0f, 0f)
    private var level: CameraLevel? = null

    /** The frame's width / height (AD-10); the grid divides that image, not the letterbox around it (FR-1.9). */
    var frameAspect: Float = 3f / 4f
        set(value) {
            if (field == value) return
            field = value
            frame = fitViewfinderFrame(width.toFloat(), height.toFloat(), value)
            invalidate()
        }

    /** Darkens the camera's image outside the frame, as 1:1 shows inside the 4:3 area (R&S 6.2). */
    var dimOutside: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        // A non-clickable sibling lets touches reach PreviewView (or the editable guide) below it.
        isClickable = false
        isFocusable = false
        updateDescription()
    }

    fun setLevel(value: CameraLevel?) {
        if (level == value) return
        val previousStatus = level?.isLevel
        level = value
        if (previousStatus != value?.isLevel) updateDescription()
        postInvalidateOnAnimation()
    }

    private fun updateDescription() {
        contentDescription = context.getString(
            when (level?.isLevel) {
                true -> R.string.camera_level_aligned
                false -> R.string.camera_level_tilted
                null -> R.string.camera_grid_description
            },
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        frame = fitViewfinderFrame(w.toFloat(), h.toFloat(), frameAspect)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frame.width == 0f || frame.height == 0f) return
        if (dimOutside) {
            canvas.drawRect(0f, 0f, width.toFloat(), frame.top, dim)
            canvas.drawRect(0f, frame.bottom, width.toFloat(), height.toFloat(), dim)
            canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dim)
            canvas.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, dim)
        }
        val clipped = canvas.save()
        canvas.clipRect(frame.left, frame.top, frame.right, frame.bottom)
        // Two lines in each direction create nine identically sized cells inside the image only.
        for (division in 1..2) {
            val x = frame.left + frame.width * division / 3f
            val y = frame.top + frame.height * division / 3f
            canvas.drawLine(x, frame.top, x, frame.bottom, gridOutline)
            canvas.drawLine(x, frame.top, x, frame.bottom, grid)
            canvas.drawLine(frame.left, y, frame.right, y, gridOutline)
            canvas.drawLine(frame.left, y, frame.right, y, grid)
        }
        level?.let { drawLevel(canvas, it) }
        canvas.restoreToCount(clipped)
    }

    private fun drawLevel(canvas: Canvas, value: CameraLevel) {
        val cx = frame.centerX
        val cy = frame.centerY
        val cellWidth = frame.width / 3f
        bar.color = if (value.isLevel) levelColor else Color.WHITE
        // Fixed short wings mark the target horizon; the middle segment follows gravity.
        drawBar(canvas, cx - cellWidth * 0.44f, cy, cx - cellWidth * 0.34f, cy)
        drawBar(canvas, cx + cellWidth * 0.34f, cy, cx + cellWidth * 0.44f, cy)
        val rotated = canvas.save()
        canvas.rotate(if (value.isLevel) 0f else value.angleDegrees, cx, cy)
        drawBar(canvas, cx - cellWidth * 0.30f, cy, cx + cellWidth * 0.30f, cy)
        canvas.restoreToCount(rotated)
    }

    private fun drawBar(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, barOutline)
        canvas.drawLine(x1, y1, x2, y2, bar)
    }

    private companion object {
        const val DIM_ALPHA = 153
    }
}
