// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.PhotoCrop
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.ui.Motion

/**
 * Reference confirm's picked image turning into its guide (FR-2.3, FR-2.10, R&S 6.2, #295), over the outline's view
 * ([outline]): a screenshot's cut first, its photo framed while the rest dims and then filling the card; a photo used
 * whole starts filling it. The outline is then drawn along the person on the photo, in the outline's own look, and the
 * photo goes small to the bottom right corner, where it stays, while the outline goes to its place; there [outline]
 * takes over (PhotoToGuide). Longer than Motion's lengths, but nothing waits for it: *Use* works from its first frame.
 * Gone for a guide without a picked image, a generated pose's.
 * Owner: PM; the screenshot's cut #174; every photo Iter5-A4 (#295).
 */
class PhotoToGuideView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    /** The outline's view, which shows the outline once this one has drawn it in its place. */
    var outline: ImageView? = null

    /** 0 the picked image as it comes … 1 the photo in the corner and the outline in its place. */
    var progress: Float = 1f
        private set

    private var guide: ReferenceGuide? = null
    private var picked: PhotoCrop? = null

    /** The outline's lines in the mask's pixels, each with its length, once traced (GuideOutlines.lines). */
    private var lines: List<Pair<PathMeasure, Float>> = emptyList()
    private var animator: ValueAnimator? = null

    /** What plays, for a test to look at its middle (Testing Documentation 2.1.8); null once it ends. */
    internal val playing: ValueAnimator? get() = animator
    private val density = resources.displayMetrics.density
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** In the accent color, which shows on a white app's pale photo as on a dark one. */
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.pix_accent)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ContextCompat.getColor(context, R.color.pix_glass_stroke)
    }

    /** The part of the lines drawn so far, wide enough to keep the outline's drawing whole along it. */
    private val revealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = REVEAL_DP * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Lays [revealPaint]'s strokes over the outline's drawing as a mask: a layer composited as a whole, so the drawing
     * goes everywhere the strokes are not, which a stroke drawn with the mode itself would leave.
     */
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val source = Rect()
    private val target = RectF()
    private val rounded = Path()
    private val drawn = Path()
    private val toBox = Matrix()
    private val maskRect = RectF()

    /** A copy of the outline view's backing for a dark line (`showOutline`), which comes in under the line. */
    private var backing: Drawable? = null
    private var backingOf: Drawable? = null

    /**
     * Shows [guide]'s picked image in the corner, after turning it into the guide when [play]: once, when its person
     * was just found, and while the phone's animations are on. Returns whether it plays, so its lines are wanted
     * ([drawLines]). A guide without a picked image, or none, hides the view and gives back the outline, leaving any
     * other fade of it to its screen.
     */
    fun show(guide: ReferenceGuide?, play: Boolean): Boolean {
        animator?.cancel()
        val had = picked != null
        this.guide = guide
        picked = guide?.picked()
        lines = emptyList()
        isVisible = picked != null
        if (picked == null) {
            if (had) outline?.alpha = 1f
            return false
        }
        if (!play || !Motion.on()) {
            moveTo(1f)
            return false
        }
        moveTo(0f)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PhotoToGuide.durationMs(cut = guide?.crop != null)
            interpolator = LinearInterpolator()
            addUpdateListener { moveTo(it.animatedValue as Float) }
            doOnEnd { animator = null }
            start()
        }
        return true
    }

    /** [guide]'s outline as lines in its mask's pixels; nothing if another guide came since. */
    fun drawLines(guide: ReferenceGuide, lines: List<Path>) {
        if (guide !== this.guide) return
        this.lines = lines.map { line -> PathMeasure(line, false).let { it to it.length } }
        invalidate()
    }

    private fun moveTo(value: Float) {
        progress = value
        // The outline's view takes over the line as it stops moving, where this view drew it last.
        outline?.alpha = if (value >= 1f) 1f else 0f
        invalidate()
    }

    private fun timeline(guide: ReferenceGuide, picked: PhotoCrop): PhotoToGuide? {
        if (width == 0 || height == 0) return null
        return PhotoToGuide(
            picked.original.width,
            picked.original.height,
            picked.photo,
            guide.anchor,
            guide.aspect,
            cut = guide.crop != null,
            width = width.toFloat(),
            height = height.toFloat(),
            padding = PADDING_DP * density,
            corner = CORNER_DP * density,
            margin = MARGIN_DP * density,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        moveTo(progress)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val guide = guide ?: return
        val picked = picked ?: return
        val timeline = timeline(guide, picked) ?: return
        val frame = timeline.at(progress)
        val moving = progress < 1f
        if (moving && frame.line > 0f) drawBacking(canvas, frame.line)
        drawPhoto(canvas, picked, timeline, frame)
        if (moving && frame.line > 0f) drawLine(canvas, frame)
    }

    private fun drawBacking(canvas: Canvas, alpha: Float) {
        val background = outline?.background ?: return
        if (background !== backingOf) {
            backingOf = background
            backing = background.constantState?.newDrawable(resources)?.mutate()
        }
        val backing = backing ?: return
        backing.setBounds(0, 0, width, height)
        backing.alpha = (255 * alpha).toInt()
        backing.draw(canvas)
    }

    private fun drawPhoto(canvas: Canvas, picked: PhotoCrop, timeline: PhotoToGuide, frame: GuideFrame) {
        val image = picked.original
        if (frame.outside > 0f) {
            photoPaint.alpha = (255 * frame.outside).toInt()
            target.set(frame.screen.left, frame.screen.top, frame.screen.right, frame.screen.bottom)
            canvas.drawBitmap(image, null, target, photoPaint)
        }
        val photo = timeline.photoIn(frame.screen)
        source.set(
            (picked.photo.left * image.width).toInt(),
            (picked.photo.top * image.height).toInt(),
            (picked.photo.right * image.width).toInt(),
            (picked.photo.bottom * image.height).toInt(),
        )
        target.set(photo.left, photo.top, photo.right, photo.bottom)
        val radius = RADIUS_DP * density * frame.corner
        rounded.reset()
        rounded.addRoundRect(target, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(rounded)
        photoPaint.alpha = (255 * frame.photo).toInt()
        canvas.drawBitmap(image, source, target, photoPaint)
        canvas.restore()
        if (frame.frame > 0f) {
            framePaint.alpha = (255 * frame.frame).toInt()
            canvas.drawRect(target, framePaint)
        }
        if (frame.corner > 0f) {
            edgePaint.alpha = (255 * frame.corner).toInt()
            canvas.drawPath(rounded, edgePaint)
        }
    }

    /**
     * The outline's own drawing (its color, its thickness) at [GuideFrame.lineBox], kept only along the part of its
     * lines drawn so far; faded in instead while the lines are not traced yet.
     */
    private fun drawLine(canvas: Canvas, frame: GuideFrame) {
        val drawable = outline?.drawable ?: return
        val mask = guide?.mask ?: return
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        if (drawableWidth <= 0 || drawableHeight <= 0) return
        val box = frame.lineBox
        target.set(box.left, box.top, box.right, box.bottom)
        val right = width.toFloat()
        val bottom = height.toFloat()
        canvas.saveLayerAlpha(0f, 0f, right, bottom, if (lines.isEmpty()) (255 * frame.line).toInt() else 255)
        canvas.save()
        canvas.translate(target.left, target.top)
        canvas.scale(target.width() / drawableWidth, target.height() / drawableHeight)
        // The outline view's own bounds for it (fitCenter), so setting them changes nothing there.
        drawable.setBounds(0, 0, drawableWidth, drawableHeight)
        drawable.draw(canvas)
        canvas.restore()
        if (frame.line < 1f && lines.isNotEmpty()) {
            drawn.reset()
            for ((measure, length) in lines) measure.getSegment(0f, length * frame.line, drawn, true)
            maskRect.set(0f, 0f, mask.width.toFloat(), mask.height.toFloat())
            toBox.setRectToRect(maskRect, target, Matrix.ScaleToFit.FILL)
            drawn.transform(toBox)
            canvas.saveLayer(0f, 0f, right, bottom, maskPaint)
            canvas.drawPath(drawn, revealPaint)
            canvas.restore()
        }
        canvas.restore()
    }

    private companion object {
        /** The outline's padding in the card, so the photo fills the card where the outline will. */
        const val PADDING_DP = 20f
        const val CORNER_DP = 72f
        const val MARGIN_DP = 14f
        const val RADIUS_DP = 8f

        /** Wide enough to keep the thickest line whole where it is drawn. */
        const val REVEAL_DP = 12f

        val WHOLE = NormalizedRect(0f, 0f, 1f, 1f)

        /** The image Reference confirm shows turning into [this] guide: a screenshot and its photo, or a photo whole. */
        fun ReferenceGuide.picked(): PhotoCrop? = crop ?: photo?.let { PhotoCrop(it, WHOLE) }
    }
}
