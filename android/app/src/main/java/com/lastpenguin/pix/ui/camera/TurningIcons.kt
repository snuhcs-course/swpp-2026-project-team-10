// AI-generated with Claude Code, 2026-11-19, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.RotateDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.withRotation
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.ui.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The angle, in degrees clockwise on the screen, at which the icons stand upright to a user holding the phone at
 * [turn] (`PhoneTurns`, clockwise as the user sees it): the same quarter the other way, between -180 and 180.
 */
internal fun iconAngleFor(turn: Int): Float = upright(-turn.toFloat())

/**
 * Where icons standing at [from] degrees go for a phone held at [turn]: the shortest way, or clockwise for a half
 * turn, so a new turn during a turn goes on from where they are.
 */
internal fun iconAngleTowards(from: Float, turn: Int): Float {
    var step = (iconAngleFor(turn) - from) % FULL_TURN
    if (step > HALF_TURN) step -= FULL_TURN
    if (step <= -HALF_TURN) step += FULL_TURN
    return from + step
}

/** [degrees] as the same angle between -180 (left out) and 180. */
private fun upright(degrees: Float): Float {
    val angle = degrees % FULL_TURN
    return when {
        angle > HALF_TURN -> angle - FULL_TURN
        angle <= -HALF_TURN -> angle + FULL_TURN
        else -> angle
    } + 0f // -0 as 0, so a turned-back icon reads as upright
}

/** A control that turns what it draws, and not itself, with the phone (FR-1.15). */
interface Turnable {
    /** How far it is turned, in degrees clockwise on the screen. */
    var turn: Float
}

/**
 * A [MaterialButton] whose icon, and with [turnsLabel] its label, turns with the phone while its background and its
 * tap area stay (FR-1.15, #207). Every icon and label it is given turns the same way, so a new one, such as the
 * flash's next icon or the next ratio, keeps the turn.
 */
class TurningButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr),
    Turnable {

    override var turn: Float = 0f
        set(value) {
            field = value
            (icon as? RotateDrawable)?.level = levelFor(value)
            if (turnsLabel) invalidate()
        }

    /** Null while MaterialButton's constructor sets the first label, which then stays as it is. */
    private var labelTurns: Boolean? = null

    /** Whether the label turns as one word, as the ratio's does; false unless the screen asks. */
    var turnsLabel: Boolean
        get() = labelTurns == true
        set(value) {
            labelTurns = value
            text = text?.toString()
        }

    /** The icon as it was set, without its turn. */
    val plainIcon: Drawable?
        get() = (icon as? RotateDrawable)?.drawable ?: icon

    init {
        icon?.let(::setIcon)
    }

    override fun setIcon(icon: Drawable?) {
        val turned = when (icon) {
            null, is RotateDrawable -> icon

            else -> RotateDrawable().apply {
                drawable = icon
                fromDegrees = 0f
                toDegrees = FULL_TURN
            }
        }
        // Read before this class's fields are set when MaterialButton's constructor sets the first icon: 0 then.
        turned?.level = levelFor(turn)
        super.setIcon(turned)
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(if (labelTurns == true) turnedLabel(text) { turn } else text, type)
    }

    /** The level at which the icon's [RotateDrawable], from 0° to 360°, draws [degrees]. */
    private fun levelFor(degrees: Float): Int = Math.floorMod((degrees * MAX_LEVEL / FULL_TURN).roundToInt(), MAX_LEVEL)

    private companion object {
        /** A [Drawable]'s highest level. */
        const val MAX_LEVEL = 10_000
    }
}

/**
 * A text whose words turn with the phone as one, in their place, while its background, such as the zoom readout's
 * pill, and its tap area stay (FR-1.15, #207). Every text it is given turns the same way.
 */
class TurningLabel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr),
    Turnable {

    override var turn: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun setText(text: CharSequence?, type: BufferType?) {
        // Its angle is read as it draws, so this also holds while TextView's constructor sets the first text.
        super.setText(turnedLabel(text) { turn }, type)
    }
}

/** [text] with the span that turns it as one word by [angle], once; an empty text stays as it is. */
private fun turnedLabel(text: CharSequence?, angle: () -> Float): CharSequence? {
    if (text.isNullOrEmpty()) return text
    if (text is Spanned && text.getSpans(0, text.length, TurnedLabelSpan::class.java).isNotEmpty()) return text
    return SpannableString(text).apply { setSpan(TurnedLabelSpan(angle), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
}

/**
 * Draws a label turned by [angle] degrees about its middle, in the place and width it has upright, so its view keeps
 * its size.
 */
internal class TurnedLabelSpan(private val angle: () -> Float) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.let(paint::getFontMetricsInt)
        return paint.measureText(text, start, end).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val metrics = paint.fontMetrics
        val middle = y + (metrics.ascent + metrics.descent) / 2
        val width = paint.measureText(text, start, end)
        canvas.withRotation(angle(), x + width / 2, middle) { drawText(text, start, end, x, y.toFloat(), paint) }
    }
}

/**
 * Turns the icons of a screen's controls with the phone (FR-1.15, Design 2.6.2): each around its own center, all
 * together, the shortest way, in [Motion.TURN_MS]. A new turn during a turn goes on from where they are; the first
 * turn, as the screen opens, and every turn while the phone's animations are off, puts them there at once.
 * [controls] turn what they draw: a button's icon, and its label if it turns, or a text such as the zoom readout;
 * [views], each an icon, a thumbnail, or an icon with its label under it, turn whole, so nothing else moves.
 */
internal class TurningIcons(private val controls: List<Turnable>, private val views: List<View>) {
    /** Where the icons stand now, in degrees clockwise on the screen. */
    var angle = 0f
        private set

    private var placed = false

    /** The turn under way, if any, for a test that looks inside it. */
    var turning: ValueAnimator? = null
        private set

    /** Turns the icons upright for a phone held at [turn]. */
    fun turnTo(turn: Int) {
        turning?.cancel()
        val target = iconAngleTowards(angle, turn)
        if (!placed || !Motion.on() || target == angle) {
            placed = true
            show(upright(target))
            return
        }
        turning = ValueAnimator.ofFloat(angle, target).apply {
            duration = Motion.TURN_MS
            interpolator = Motion.STANDARD
            addUpdateListener { show(it.animatedValue as Float) }
            doOnEnd { animator ->
                if (turning !== animator) return@doOnEnd
                turning = null
                show(upright(angle))
            }
            start()
        }
    }

    /** Stops a turn under way where it is, as the screen's views go. */
    fun stop() {
        turning?.cancel()
    }

    private fun show(degrees: Float) {
        angle = degrees
        for (control in controls) control.turn = degrees
        for (view in views) view.rotation = degrees
    }
}

/**
 * A frame over the whole screen whose content stands upright to the user as the phone turns (FR-1.16, #207): the
 * photo viewer, which a user holding the phone sideways sees as a landscape screen. Its child turns by [turn] around
 * the middle; while the turn is nearer a quarter than upright or a half, the child is laid out with the frame's width
 * and height swapped, so it fills the screen the user sees, and [onSideways] tells of each change.
 */
internal class TurningFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs),
    Turnable {
    /** Told when the content starts or stops being laid out sideways, before its layout. */
    var onSideways: ((Boolean) -> Unit)? = null

    /** The content is laid out sideways: its width is the frame's height. */
    var sideways = false
        private set

    override var turn = 0f
        set(value) {
            field = value
            for (child in children) child.rotation = value
            // Halfway through a quarter turn, where the content's two shapes look alike.
            val next = abs(upright(value)) in SIDEWAYS
            if (next == sideways) return
            sideways = next
            onSideways?.invoke(next)
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val across = if (sideways) height else width
        val down = if (sideways) width else height
        for (child in children) {
            child.measure(
                MeasureSpec.makeMeasureSpec(across, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(down, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // In the middle, around which it turns.
        for (child in children) {
            val x = (right - left - child.measuredWidth) / 2
            val y = (bottom - top - child.measuredHeight) / 2
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    private companion object {
        /** The angles, from upright, at which the content is laid out sideways. */
        val SIDEWAYS = 45f..135f
    }
}

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
