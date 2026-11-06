// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.lastpenguin.pix.guide.GuideLook

/**
 * A person's outline in the chosen look over a sky-to-ground gradient, so white and black lines both show, at the
 * line's size on Camera: 2 dp for the default thickness (Design 2.6.1).
 */
class GuideLookPreview @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bounds = RectF()
    private val person = Path()

    var look: GuideLook = GuideLook()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        background.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), SKY, GROUND, Shader.TileMode.CLAMP)
        person.reset()
        val cx = w / 2f
        val headRadius = h * 0.13f
        val headCenter = h * 0.12f + headRadius
        val shoulders = headCenter + headRadius + h * 0.07f
        val half = h * 0.26f
        person.addCircle(cx, headCenter, headRadius, Path.Direction.CW)
        person.moveTo(cx - half, h.toFloat())
        person.lineTo(cx - half, shoulders + h * 0.12f)
        person.quadTo(cx - half, shoulders, cx - half * 0.55f, shoulders)
        person.lineTo(cx + half * 0.55f, shoulders)
        person.quadTo(cx + half, shoulders, cx + half, shoulders + h * 0.12f)
        person.lineTo(cx + half, h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val radius = CORNER_DP * density
        canvas.drawRoundRect(bounds, radius, radius, background)
        line.color = look.color.argb
        line.strokeWidth = LINE_DP * density * look.thickness.scale
        canvas.drawPath(person, line)
    }

    private companion object {
        const val LINE_DP = 2f
        const val CORNER_DP = 14f
        const val SKY = 0xFFAFC3D6.toInt()
        const val GROUND = 0xFF33373E.toInt()
    }
}
