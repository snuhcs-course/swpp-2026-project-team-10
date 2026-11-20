// AI-generated with Claude Code, 2026-11-19, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.widget.TextViewCompat
import com.lastpenguin.pix.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Where a caption goes beside the control it points at. */
enum class CaptionSide { ABOVE, BELOW, START, END }

/**
 * Where a caption above or below a control lines up with it: centered, or at its start or end, for a control as wide as
 * the screen, whose arrow then comes straight down near that end.
 */
enum class CaptionAlign { CENTER, START, END }

/**
 * The first-use tutorial over a live screen (FR-10.4, Design 2.6.8): the screen dimmed, a dashed ring around each
 * control it explains, cut out of the dimming so the control shows, and beside each ring its caption with a dashed
 * arrow, as real text that TalkBack reads, Korean included. [locate] says where the controls are in this view, or null
 * for one the screen does not show, which then gets no ring or caption; the screen calls [refresh] as they move.
 * Each caption goes on its side of the control, farther away or on another side when it would cover another
 * caption, ring, or the title, and stays inside the system bars. A caption [Mark.onEdge] sits on its ring's edge
 * instead, as the label in an outlined field's border, with no arrow ([#210]), also on the seam where another ring
 * meets that edge, as two touching rows' rings do ([#243]). A tap anywhere closes it; the line that says so sits at the
 * bottom's middle on every screen and in every language, and the captions keep clear of it (#204).
 */
class CoachMarkLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /**
     * One control to explain: its caption, where that goes, and whether its arrow may pass over other controls. With
     * [onEdge], the caption sits on the ring's edge on [side] (above or below), lined up at [align], half over the
     * control, with the ring stopping on both sides of it and no arrow. Where another mark's ring meets that edge, as
     * a row's ring meets the one of the row above it, the caption sits on their seam, and both rings stop around it.
     * It keeps to one line, so it covers only the control's edge, and its text shrinks if it has no room there. Failing
     * that, it goes beside the control only where nothing is in the way, and otherwise the mark is left out, ring and
     * all, rather than cover other text.
     */
    data class Mark(
        val caption: CharSequence,
        val side: CaptionSide,
        val align: CaptionAlign = CaptionAlign.CENTER,
        val overControls: Boolean = false,
        val onEdge: Boolean = false,
    )

    /** A mark as placed: the ring around its control and its caption's side, both in this view; [onEdge] as above. */
    data class Placed(
        val ring: RectF,
        val round: Boolean,
        val caption: TextView,
        val side: CaptionSide,
        val onEdge: Boolean = false,
    )

    private val density = resources.displayMetrics.density
    private val margin = 16 * density
    private val ringPad = 5 * density
    private val baseGap = 28 * density
    private val stepGap = 20 * density
    private val clearance = 8 * density
    private val titleTop = 56 * density
    private val captionMaxWidth = 168 * density

    private val ink = ContextCompat.getColor(context, R.color.pix_text)
    private val scrimPaint = Paint().apply {
        color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.pix_ground), SCRIM_ALPHA)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ink
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 4.5f * density), 0f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ink
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(4.5f * density, 5.5f * density), 0f)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }

    private var marks: List<Mark> = emptyList()
    private val captions = mutableListOf<TextView>()

    /** Each caption's own text size, in pixels; one on its ring's edge may take a smaller one to fit there. */
    private val captionSizes = mutableListOf<Float>()
    private var titleView: TextView? = null
    private var bars: Insets = Insets.NONE
    private var located: List<RectF?> = emptyList()
    private val arrows = mutableListOf<Pair<Path, Path>>()

    /** Where each mark's control is in this view, in the order of the marks; null for one not shown. */
    var locate: () -> List<RectF?> = { emptyList() }

    /** The marks as last placed, in their order; null for a control not shown. */
    var placed: List<Placed?> = emptyList()
        private set

    init {
        setWillNotDraw(false)
        // Nothing under it takes a touch while it shows; a tap anywhere on it closes it (its click listener).
        isClickable = true
        isFocusable = false
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            requestLayout()
            insets
        }
    }

    /** The screen's tips: an optional [title] at the top, and a caption for each of [marks]. */
    fun show(title: CharSequence?, marks: List<Mark>) {
        captions.forEach(::removeView)
        captions.clear()
        captionSizes.clear()
        titleView?.let(::removeView)
        titleView = title?.let { text ->
            TextView(context).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Pix_SheetTitle)
                this.text = text
            }.also { addView(it, 0, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)) }
        }
        this.marks = marks
        for (mark in marks) {
            val caption = TextView(context).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Pix_Tutorial)
                text = mark.caption
                maxWidth = captionMaxWidth.toInt()
            }
            captions += caption
            captionSizes += caption.textSize
            addView(caption, childCount - 1, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        requestLayout()
    }

    /** Places everything again if a control has moved, appeared, or gone since the last time. */
    fun refresh() {
        val now = locate()
        if (now != located) requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        located = locate()
        place()
        invalidate()
    }

    /**
     * One way to place a caption: its side, its spot, the arrow's strip, and the width it was measured at; [clean] when
     * it covers nothing and its arrow crosses nothing it must not, and [onEdge] on its ring's edge, at [textScale] of
     * its text size.
     */
    private class Option(
        val side: CaptionSide,
        val spot: RectF,
        val corridor: RectF,
        val maxWidth: Int,
        val clean: Boolean = true,
        val onEdge: Boolean = false,
        val textScale: Float = 1f,
    )

    private fun place() {
        val safe = RectF(
            bars.left + margin,
            bars.top + margin,
            width - bars.right - margin,
            height - bars.bottom - margin,
        )
        val rings = separated(marks.indices.map { index -> located.getOrNull(index)?.let(::ringAround) })
        val ringRects = rings.mapNotNull { it?.first }

        var titleSpot: RectF? = null
        titleView?.let { title ->
            var y = max(safe.top, bars.top + titleTop)
            var spot = RectF(safe.left, y, safe.left + title.measuredWidth, y + title.measuredHeight)
            while (ringRects.any { it.overlaps(spot) } && spot.bottom + stepGap < safe.bottom) {
                y += stepGap / 2
                spot = RectF(safe.left, y, safe.left + title.measuredWidth, y + title.measuredHeight)
            }
            title.layout(spot)
            titleSpot = spot
        }

        // The closing line before the captions, always at the bottom's middle, so it stands on the same line on every
        // screen and in every language; the captions keep clear of it (#204).
        val hint = findViewById<View>(R.id.tutorialCloseHint)
        val hintSpot = hint?.let {
            val w = it.measuredWidth.toFloat()
            val h = it.measuredHeight.toFloat()
            // On the navigation bar's edge, not the captions' margin above it: the only room under the shutter's ring.
            val bottom = (height - bars.bottom).toFloat()
            RectF(safe.centerX() - w / 2, bottom - h, safe.centerX() + w / 2, bottom)
        }

        // A few passes, each knowing where the other arrows ran in the one before, so no caption sits on an arrow.
        var chosen = arrayOfNulls<Option>(marks.size)
        repeat(PASSES) {
            val previous = chosen
            val next = arrayOfNulls<Option>(marks.size)
            val texts = listOfNotNull(titleSpot, hintSpot).toMutableList()
            marks.forEachIndexed { index, mark ->
                val ring = rings[index]?.first ?: return@forEachIndexed
                val otherRings = rings.filterIndexed { other, it -> other != index && it != null }.map { it!!.first }
                val otherArrows = previous.filterIndexed { other, it -> other != index && it != null }
                    .map { it!!.corridor }
                val crossed = if (mark.overControls) texts else texts + otherRings
                scaleCaption(index, 1f)
                // A caption on its ring's edge may cross the ring that meets that edge, which stops around it too.
                val seam = if (mark.onEdge) seamRing(ring, mark.side, otherRings) else null
                val edgeBlocked = otherRings.filter { it !== seam } + texts + otherArrows
                val option = edgeOption(mark, index, ring, safe, edgeBlocked)
                    ?: bestOption(mark, index, ring, safe, ringRects + texts + otherArrows, crossed)
                        .takeIf { it.clean || !mark.onEdge }
                    ?: return@forEachIndexed
                next[index] = option
                texts += option.spot
            }
            chosen = next
        }

        val result = MutableList<Placed?>(marks.size) { null }
        marks.indices.forEach { index ->
            val caption = captions[index]
            val option = chosen[index]
            caption.isGone = option == null
            if (option == null) return@forEach
            scaleCaption(index, option.textScale)
            measureCaption(caption, option.maxWidth)
            caption.layout(option.spot)
            result[index] = Placed(rings[index]!!.first, rings[index]!!.second, caption, option.side, option.onEdge)
        }
        placed = result

        if (hint != null && hintSpot != null) hint.layout(hintSpot)

        arrows.clear()
        for ((index, mark) in marks.withIndex()) {
            val placedMark = result[index] ?: continue
            if (!placedMark.onEdge) arrows += arrow(placedMark, mark.align)
        }
    }

    /**
     * Rings of controls stacked close, such as two list rows, meet no closer than [RING_GAP_DP]: each gives up half of
     * what they would share, so their dashes never overlap, and as much again on its other side, so it stays centered
     * on its control.
     */
    private fun separated(rings: List<Pair<RectF, Boolean>?>): List<Pair<RectF, Boolean>?> {
        val gap = RING_GAP_DP * density
        val out = rings.map { it?.let { (ring, round) -> RectF(ring) to round } }
        for (a in out.filterNotNull()) {
            for (b in out.filterNotNull()) {
                val (upper, lower) = a.first to b.first
                val side = upper.left < lower.right && lower.left < upper.right
                val shared = upper.bottom - lower.top
                val stacked = upper.centerY() < lower.centerY() && shared > -gap &&
                    shared < min(upper.height(), lower.height()) / 2
                if (a.second || b.second || !side || !stacked) continue
                val middle = (upper.bottom + lower.top) / 2
                val upperShrink = upper.bottom - (middle - gap / 2)
                val lowerShrink = (middle + gap / 2) - lower.top
                upper.top += upperShrink
                upper.bottom -= upperShrink
                lower.top += lowerShrink
                lower.bottom -= lowerShrink
            }
        }
        return out
    }

    /**
     * The one of [others] that meets [ring] at its [side] edge, if any: a ring beside it no farther away than the
     * [RING_GAP_DP] that [separated] leaves between the rings of two rows that touch, such as the gallery's and My
     * library's on *Add a pose guide* ([#243]).
     */
    private fun seamRing(ring: RectF, side: CaptionSide, others: List<RectF>): RectF? {
        val reach = (RING_GAP_DP + 1f) * density
        return others.firstOrNull { other ->
            val beside = other.left < ring.right && ring.left < other.right
            val apart = when (side) {
                CaptionSide.ABOVE -> ring.top - other.bottom
                CaptionSide.BELOW -> other.top - ring.bottom
                CaptionSide.START, CaptionSide.END -> -1f
            }
            beside && apart in 0f..reach
        }
    }

    /**
     * [mark]'s caption on its [ring]'s edge, when it asks for that: centered on the top or bottom edge, [EDGE_INSET_DP]
     * in from the end it lines up with, in one line, its text shrunk to [EDGE_SCALES]' smallest at most so it fits and
     * the gap it opens in the ring covers none of [blocked] and stays on the screen; none when that fails.
     */
    private fun edgeOption(mark: Mark, index: Int, ring: RectF, safe: RectF, blocked: List<RectF>): Option? {
        if (!mark.onEdge || (mark.side != CaptionSide.ABOVE && mark.side != CaptionSide.BELOW)) return null
        val caption = captions[index]
        val inset = EDGE_INSET_DP * density
        val padX = EDGE_GAP_X_DP * density
        val maxWidth = max((ring.width() - 2 * (inset + padX)).toInt(), 1)
        val edge = if (mark.side == CaptionSide.ABOVE) ring.top else ring.bottom
        for (scale in EDGE_SCALES) {
            scaleCaption(index, scale)
            measureCaption(caption, maxWidth)
            if (caption.lineCount > 1) continue
            val w = caption.measuredWidth.toFloat()
            val h = caption.measuredHeight.toFloat()
            val left = when (mark.align) {
                CaptionAlign.START -> ring.left + inset + padX
                CaptionAlign.END -> ring.right - inset - padX - w
                CaptionAlign.CENTER -> ring.centerX() - w / 2
            }
            val spot = RectF(left, edge - h / 2, left + w, edge + h / 2)
            val gap = edgeGap(spot)
            if (safe.contains(gap) && blocked.none { it.overlaps(gap) }) {
                val noArrow = RectF(spot.left, spot.top, spot.left, spot.top)
                return Option(mark.side, spot, noArrow, maxWidth, onEdge = true, textScale = scale)
            }
        }
        scaleCaption(index, 1f)
        return null
    }

    /** The gap a caption at [spot] on its ring's edge opens in the ring: the caption and a little room on each side. */
    private fun edgeGap(spot: RectF): RectF = RectF(spot).apply {
        inset(-EDGE_GAP_X_DP * density, -EDGE_GAP_Y_DP * density)
    }

    private fun scaleCaption(index: Int, scale: Float) {
        captions[index].setTextSize(TypedValue.COMPLEX_UNIT_PX, captionSizes[index] * scale)
    }

    /**
     * The ring around a control: a circle for a near-square one up to the shutter's size, a capsule otherwise, a little
     * outside it; a circle around a larger one, such as a tile, would spill over the controls beside it, and one around a
     * portrait photo would cut its corners off (#204).
     */
    private fun ringAround(target: RectF): Pair<RectF, Boolean> {
        val ratio = target.width() / max(target.height(), 1f)
        return if (ratio in 0.8f..1.25f && max(target.width(), target.height()) <= MAX_ROUND_DP * density) {
            val radius = max(target.width(), target.height()) / 2 + ringPad
            RectF(
                target.centerX() - radius,
                target.centerY() - radius,
                target.centerX() + radius,
                target.centerY() + radius,
            ) to true
        } else {
            RectF(target).apply { inset(-ringPad, -ringPad) } to false
        }
    }

    private fun measureCaption(caption: TextView, maxWidth: Int) {
        caption.maxWidth = maxWidth
        caption.measure(
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
    }

    /**
     * The nearest place for [mark]'s caption on its side, then the opposite one, then the others: farther away, shifted
     * or narrower as needed, where it covers none of [blocked] and its arrow crosses none of [crossed]; failing that,
     * the nearest where it covers none of [blocked], its arrow crossing what it must, as with large text on a screen
     * full of tips.
     */
    private fun bestOption(
        mark: Mark,
        index: Int,
        ring: RectF,
        safe: RectF,
        blocked: List<RectF>,
        crossed: List<RectF>,
    ): Option {
        val caption = captions[index]
        val sizes = WIDTHS.map { factor ->
            val maxWidth = (captionMaxWidth * factor).toInt()
            measureCaption(caption, maxWidth)
            Triple(maxWidth, caption.measuredWidth.toFloat(), caption.measuredHeight.toFloat())
        }
        val sides = listOf(mark.side, opposite(mark.side)) +
            CaptionSide.entries.filter { it != mark.side && it != opposite(mark.side) }
        var first: Option? = null
        var crossing: Option? = null
        for (side in sides) {
            for (step in 0..MAX_STEPS) {
                val gap = baseGap + step * stepGap
                for ((maxWidth, w, h) in sizes) {
                    for (spot in spotsBeside(side, mark.align, ring, w, h, gap, safe)) {
                        val option = Option(side, spot, corridor(side, mark.align, ring, spot), maxWidth)
                        if (first == null) first = option
                        if (!safe.contains(spot)) continue
                        if (blocked.none { it.overlaps(spot) }) {
                            if (crossed.none { RectF.intersects(it, option.corridor) }) return option
                            if (crossing == null) crossing = option
                        }
                    }
                }
            }
        }
        crossing?.let { return Option(it.side, it.spot, it.corridor, it.maxWidth, clean = false) }
        // Nowhere free: the nearest place on its own side, kept on the screen.
        val near = first!!
        val dx = near.spot.left.coerceIn(safe.left, max(safe.left, safe.right - near.spot.width())) - near.spot.left
        val dy = near.spot.top.coerceIn(safe.top, max(safe.top, safe.bottom - near.spot.height())) - near.spot.top
        val spot = RectF(near.spot).apply { offset(dx, dy) }
        return Option(near.side, spot, corridor(near.side, mark.align, ring, spot), near.maxWidth, clean = false)
    }

    /**
     * Where a caption of [w] by [h] may go [gap] away on [side]: lined up as [align] says, or shifted along it, and kept
     * inside [safe] along that side.
     */
    private fun spotsBeside(
        side: CaptionSide,
        align: CaptionAlign,
        ring: RectF,
        w: Float,
        h: Float,
        gap: Float,
        safe: RectF,
    ): List<RectF> {
        val reach = 20 * density
        return when (side) {
            CaptionSide.ABOVE, CaptionSide.BELOW -> {
                val xs = when (align) {
                    CaptionAlign.CENTER -> listOf(
                        ring.centerX() - w / 2,
                        ring.centerX() - reach,
                        ring.centerX() + reach - w,
                    )

                    CaptionAlign.START -> listOf(ring.left)

                    CaptionAlign.END -> listOf(ring.right - w)
                }
                val top = if (side == CaptionSide.ABOVE) ring.top - gap - h else ring.bottom + gap
                xs.map { it.coerceIn(safe.left, max(safe.left, safe.right - w)) }.distinct()
                    .map { x -> RectF(x, top, x + w, top + h) }
            }

            CaptionSide.START, CaptionSide.END -> {
                val left = if (side == CaptionSide.START) ring.left - gap - w else ring.right + gap
                listOf(ring.centerY() - h / 2, ring.centerY() - reach, ring.centerY() + reach - h)
                    .map { it.coerceIn(safe.top, max(safe.top, safe.bottom - h)) }.distinct()
                    .map { y -> RectF(left, y, left + w, y + h) }
            }
        }
    }

    /** Where the arrow from a caption at [spot] to [ring] starts and ends. */
    private class Course(val start: PointF, val end: PointF)

    private fun course(side: CaptionSide, align: CaptionAlign, ring: RectF, spot: RectF): Course {
        val near = 4 * density
        val vertical = side == CaptionSide.ABOVE || side == CaptionSide.BELOW
        val down = side == CaptionSide.ABOVE
        if (vertical && align != CaptionAlign.CENTER) {
            // Straight down (or up) to the control near its aligned end.
            val inset = min(ring.width() / 2, 28 * density)
            val x = (if (align == CaptionAlign.END) ring.right - inset else ring.left + inset)
                .coerceIn(min(spot.left + 12 * density, spot.centerX()), max(spot.centerX(), spot.right - 12 * density))
            return Course(
                PointF(x, if (down) spot.bottom + near else spot.top - near),
                PointF(x, if (down) ring.top - near else ring.bottom + near),
            )
        }
        // Within the caption, a little in from its ends; at its middle for one too small for that.
        val left = spot.left + 12 * density
        val top = spot.top + 8 * density
        val startX = ring.centerX().coerceIn(min(left, spot.centerX()), max(spot.centerX(), spot.right - 12 * density))
        val startY = ring.centerY().coerceIn(min(top, spot.centerY()), max(spot.centerY(), spot.bottom - 8 * density))
        val start = when (side) {
            CaptionSide.ABOVE -> PointF(startX, spot.bottom + near)
            CaptionSide.BELOW -> PointF(startX, spot.top - near)
            CaptionSide.START -> PointF(spot.right + near, startY)
            CaptionSide.END -> PointF(spot.left - near, startY)
        }
        val end = when (side) {
            CaptionSide.ABOVE -> PointF(ring.centerX(), ring.top - near)
            CaptionSide.BELOW -> PointF(ring.centerX(), ring.bottom + near)
            CaptionSide.START -> PointF(ring.left - near, ring.centerY())
            CaptionSide.END -> PointF(ring.right + near, ring.centerY())
        }
        return Course(start, end)
    }

    /** The strip the arrow from [spot] to [ring] runs through, which no other caption may cover. */
    private fun corridor(side: CaptionSide, align: CaptionAlign, ring: RectF, spot: RectF): RectF {
        val course = course(side, align, ring, spot)
        val half = 6 * density
        val xs = listOf(course.start.x, course.end.x)
        val strip = RectF(
            xs.min() - half,
            min(course.start.y, course.end.y),
            xs.max() + half,
            max(course.start.y, course.end.y),
        )
        if (side == CaptionSide.START || side == CaptionSide.END) strip.inset(0f, -half)
        return strip
    }

    /** A dashed arrow from the caption to its ring, and its head. */
    private fun arrow(mark: Placed, align: CaptionAlign): Pair<Path, Path> {
        val spot = RectF(
            mark.caption.left.toFloat(),
            mark.caption.top.toFloat(),
            mark.caption.right.toFloat(),
            mark.caption.bottom.toFloat(),
        )
        val course = course(mark.side, align, mark.ring, spot)
        val start = course.start
        val end = course.end
        // A gentle bend: the middle pushed sideways by a sixth of the length.
        val length = max(hypot(end.x - start.x, end.y - start.y), 1f)
        val bend = length / 6
        val control = PointF(
            (start.x + end.x) / 2 + (end.y - start.y) / length * bend,
            (start.y + end.y) / 2 - (end.x - start.x) / length * bend,
        )
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadTo(control.x, control.y, end.x, end.y)
        }
        return path to arrowHead(control, end)
    }

    private fun arrowHead(from: PointF, tip: PointF): Path {
        val length = max(hypot(tip.x - from.x, tip.y - from.y), 1f)
        val ux = (tip.x - from.x) / length
        val uy = (tip.y - from.y) / length
        val back = 7 * density
        val half = 3.5f * density
        return Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - ux * back - uy * half, tip.y - uy * back + ux * half)
            lineTo(tip.x - ux * back + uy * half, tip.y - uy * back - ux * half)
            close()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        for (mark in placed) mark?.let { drawRing(canvas, it, clearPaint) }
        canvas.restoreToCount(layer)
        // A caption on its ring's edge: the ring stops on both sides of it, as an outlined field's border does, and so
        // does the ring that meets it on a seam; it crosses no other ring.
        val save = canvas.save()
        for (mark in placed) if (mark?.onEdge == true) canvas.clipOutRect(edgeGap(captionRect(mark)))
        for (mark in placed) mark?.let { drawRing(canvas, it, ringPaint) }
        canvas.restoreToCount(save)
        for ((path, head) in arrows) {
            canvas.drawPath(path, arrowPaint)
            canvas.drawPath(head, headPaint)
        }
        super.dispatchDraw(canvas)
    }

    private fun captionRect(mark: Placed) = RectF(
        mark.caption.left.toFloat(),
        mark.caption.top.toFloat(),
        mark.caption.right.toFloat(),
        mark.caption.bottom.toFloat(),
    )

    private fun drawRing(canvas: Canvas, mark: Placed, paint: Paint) {
        val ring = mark.ring
        if (mark.round) {
            canvas.drawCircle(ring.centerX(), ring.centerY(), ring.width() / 2, paint)
        } else {
            val radius = min(ring.height() / 2, CORNER_DP * density)
            canvas.drawRoundRect(ring, radius, radius, paint)
        }
    }

    private fun RectF.overlaps(other: RectF): Boolean = RectF(this).apply { inset(-clearance, -clearance) }
        .let { it.left < other.right && other.left < it.right && it.top < other.bottom && other.top < it.bottom }

    private fun View.layout(spot: RectF) {
        layout(
            spot.left.toInt(),
            spot.top.toInt(),
            spot.left.toInt() + measuredWidth,
            spot.top.toInt() + measuredHeight,
        )
    }

    private fun opposite(side: CaptionSide) = when (side) {
        CaptionSide.ABOVE -> CaptionSide.BELOW
        CaptionSide.BELOW -> CaptionSide.ABOVE
        CaptionSide.START -> CaptionSide.END
        CaptionSide.END -> CaptionSide.START
    }

    private companion object {
        /** The dimming's alpha: 80 %. */
        const val SCRIM_ALPHA = 204
        const val MAX_STEPS = 24
        const val PASSES = 3

        /** The widths a caption may take, of its widest. */
        val WIDTHS = listOf(1f, 0.75f, 0.55f)
        const val CORNER_DP = 24f

        /** The longest side of a control a circle goes around: the shutter's 72 dp, and a little more. */
        const val MAX_ROUND_DP = 80f

        /** The least room between the rings of two controls stacked close. */
        const val RING_GAP_DP = 6f

        /** How far in from its ring's end a caption on the ring's edge starts, past the ring's corner. */
        const val EDGE_INSET_DP = 16f

        /** The room the ring leaves on each side of a caption on its edge, and above and below it. */
        const val EDGE_GAP_X_DP = 8f
        const val EDGE_GAP_Y_DP = 3f

        /** The text sizes a caption on its ring's edge may take, of its own, to find room there. */
        val EDGE_SCALES = listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f)
    }
}
