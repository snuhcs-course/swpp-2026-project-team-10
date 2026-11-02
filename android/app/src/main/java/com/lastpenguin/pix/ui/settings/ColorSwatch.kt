// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import com.lastpenguin.pix.R

/**
 * One guide color on the settings screen: a 28 dp dot in a 48 dp touch area (NFR-16), with a ring while it is the chosen
 * one ([isSelected]). TalkBack reads it as a radio button with its color's name.
 */
class ColorSwatch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ContextCompat.getColor(context, R.color.pix_outline)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.pix_text)
    }

    var color: Int = 0
        set(value) {
            field = value
            fill.color = value
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        minimumWidth = (TOUCH_DP * density).toInt()
        minimumHeight = (TOUCH_DP * density).toInt()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (TOUCH_DP * density).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = DOT_DP * density / 2
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, edge)
        if (isSelected) canvas.drawCircle(cx, cy, radius + RING_GAP_DP * density, ring)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = RadioButton::class.java.name
        info.isCheckable = true
        info.isChecked = isSelected
    }

    private companion object {
        const val TOUCH_DP = 48f
        const val DOT_DP = 28f
        const val RING_GAP_DP = 4f
    }
}
