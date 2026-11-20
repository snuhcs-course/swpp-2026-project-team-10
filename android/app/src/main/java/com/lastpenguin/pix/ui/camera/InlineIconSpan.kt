// AI-generated with Claude Code, 2026-11-13, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan

/**
 * [icon] in a line of text, centered on the letters, with [gap] px after it. It stays beside the first word when the
 * text wraps to centered lines, where a compound drawable stays at the box's edge (#204). Unlike an ImageSpan, it
 * leaves the line as tall as its text.
 */
class InlineIconSpan(private val icon: Drawable, private val gap: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        icon.bounds.width() + gap

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val letters = paint.fontMetrics
        val middle = y + (letters.ascent + letters.descent) / 2
        canvas.save()
        canvas.translate(x, middle - icon.bounds.height() / 2f)
        icon.draw(canvas)
        canvas.restore()
    }
}
