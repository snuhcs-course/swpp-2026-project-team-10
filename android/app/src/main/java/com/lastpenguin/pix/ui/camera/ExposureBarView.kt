// AI-generated with Claude Code, 2026-11-09, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import com.lastpenguin.pix.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The brightness beside the focus (FR-1.11, Design 2.8): a thin vertical line with a sun on it at the applied exposure
 * index. A drag anywhere on it moves the sun, up for brighter and down for darker, from where it was; each step is
 * reported as it comes and the last once more as final when the finger lifts, like a pinch zoom. A drag up or down on
 * the preview moves it the same way ([startDrag], [dragTo]). TalkBack reads it as a slider and moves it a step at a
 * time; while it has TalkBack's focus, it counts as touched, so the screen keeps it up.
 */
class ExposureBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val accent = context.getColor(R.color.pix_accent)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = LINE_ALPHA
        strokeWidth = LINE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private val lineOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.camera_exposure_outline)
        strokeWidth = LINE_OUTLINE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val rays = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        strokeWidth = LINE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private var range: IntRange = 0..0
    private var downIndex = 0

    /** The index the sun shows: the applied one, or the finger's while a drag runs. */
    var index: Int = 0
        private set

    /** A drag is moving the sun; the screen keeps the bar up and leaves the sun under the finger. */
    var isDragging: Boolean = false
        private set

    // TalkBack's focus is on the bar.
    private var focused = false

    /** A drag or TalkBack's focus is on the bar, so the screen keeps it up. */
    val isHeld: Boolean get() = isDragging || focused

    /** Each new index, and the last again with `final` when the drag ends. */
    var onChange: ((index: Int, final: Boolean) -> Unit)? = null

    /** A touch started (true) or ended (false), so the screen can hold the bar up while it is used. */
    var onTouching: ((Boolean) -> Unit)? = null

    /** What TalkBack reads for an index, such as "+0.7". */
    var describe: (Int) -> String = { it.toString() }

    init {
        contentDescription = context.getString(R.string.brightness)
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** The camera's [range] and the applied [value]; a drag in progress keeps its own value. */
    fun render(range: IntRange, value: Int, enabled: Boolean) {
        val moved = range != this.range
        this.range = range
        isEnabled = enabled && range.first < range.last
        if (isDragging) return
        val clamped = value.coerceIn(range)
        ViewCompat.setStateDescription(this, describe(clamped))
        if (clamped == index && !moved) return
        index = clamped
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = SUN_DP * density
        val cx = width / 2f
        val sunY = sunCenter(radius)
        val gap = radius + GAP_DP * density
        if (sunY - gap > radius) {
            canvas.drawLine(cx, radius, cx, sunY - gap, lineOutline)
            canvas.drawLine(cx, radius, cx, sunY - gap, line)
        }
        if (sunY + gap < height - radius) {
            canvas.drawLine(cx, sunY + gap, cx, height - radius, lineOutline)
            canvas.drawLine(cx, sunY + gap, cx, height - radius, line)
        }
        canvas.drawCircle(cx, sunY, radius * CORE, sun)
        for (ray in 0 until RAYS) {
            val angle = Math.PI * 2 * ray / RAYS
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            canvas.drawLine(
                cx + dx * radius * RAY_START,
                sunY + dy * radius * RAY_START,
                cx + dx * radius,
                sunY + dy * radius,
                rays,
            )
        }
    }

    /** The sun's center: the top for the brightest index, the bottom for the darkest. */
    private fun sunCenter(radius: Float): Float {
        val span = range.last - range.first
        val fraction = if (span <= 0) 0.5f else (index - range.first).toFloat() / span
        return radius + (1f - fraction) * (height - 2 * radius)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downY = event.y
                startDrag()
            }

            MotionEvent.ACTION_MOVE -> dragTo(downY - event.y)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        return true
    }

    private var downY = 0f

    /** A drag starts from the index shown, on the bar or on the preview. */
    fun startDrag() {
        if (!isEnabled || isDragging) return
        isDragging = true
        downIndex = index
        onTouching?.invoke(true)
    }

    /** The finger is [up] pixels above where the drag started: the sun moves as far, the bar's length the range. */
    fun dragTo(up: Float) {
        if (!isDragging) return
        // A drag on the preview can start before the bar, just shown, is laid out: its own height stands in then.
        val length = if (height > 0) height else layoutParams?.height ?: 0
        val travel = length - 2 * SUN_DP * density
        if (travel <= 0f) return
        val steps = up / travel * (range.last - range.first)
        val next = (downIndex + steps.roundToInt()).coerceIn(range)
        if (next != index) {
            index = next
            ViewCompat.setStateDescription(this, describe(index))
            invalidate()
            onChange?.invoke(index, false)
        }
    }

    /** Ends a drag in progress with its final value, as when the screen pauses under the finger. */
    fun finishDrag() {
        if (!isDragging) return
        isDragging = false
        onChange?.invoke(index, true)
        onTouching?.invoke(false)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            range.first.toFloat(),
            range.last.toFloat(),
            index.toFloat(),
        )
        if (isEnabled) {
            if (index < range.last) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            if (index > range.first) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        // TalkBack's focus holds the bar up as a finger on it would, until the focus moves on.
        when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                focused = true
                onTouching?.invoke(true)
            }

            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                focused = false
                onTouching?.invoke(false)
            }
        }
        if (!isEnabled) return super.performAccessibilityAction(action, arguments)
        val next = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> index + 1

            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> index - 1

            android.R.id.accessibilityActionSetProgress ->
                arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.roundToInt()

            else -> null
        } ?: return super.performAccessibilityAction(action, arguments)
        val clamped = next.coerceIn(range)
        if (clamped != index) {
            index = clamped
            ViewCompat.setStateDescription(this, describe(index))
            invalidate()
        }
        // TalkBack's change is final at once, as a slider's accessibility change is (Design 2.6.5).
        onChange?.invoke(index, true)
        return true
    }

    private companion object {
        const val LINE_DP = 1.5f
        const val LINE_OUTLINE_DP = 2.5f
        const val LINE_ALPHA = 204
        const val SUN_DP = 7f
        const val GAP_DP = 3f
        const val CORE = 0.4f
        const val RAY_START = 0.68f
        const val RAYS = 8
    }
}
