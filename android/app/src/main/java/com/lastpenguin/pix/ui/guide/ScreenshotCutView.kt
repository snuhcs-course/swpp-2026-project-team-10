// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.lastpenguin.pix.R
import com.lastpenguin.pix.guide.PhotoCrop

/**
 * Reference confirm's cut of a screenshot (FR-2.10, R&S 6.2), over the outline: the screenshot whole, its photo
 * framed while the rest dims, the photo filling the card, and then the photo small in the bottom right corner, where
 * it stays, while [outline] appears (ScreenshotCut). Gone for a guide made from a whole image.
 * Owner: the screenshot's cut (#174).
 */
class ScreenshotCutView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    /** The view whose alpha follows the cut: the outline, which shows once the photo goes to the corner. */
    var outline: View? = null

    /** 0 the screenshot whole … 1 the photo in the corner. */
    var progress: Float = 1f
        private set

    private var crop: PhotoCrop? = null
    private var animator: ValueAnimator? = null
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
    private val source = Rect()
    private val target = RectF()
    private val rounded = Path()

    /**
     * Shows [crop] in the corner, after playing the cut when [play]: once, when its person was just found. Null hides
     * the view and gives back the outline a cut faded, leaving any other fade of it to its screen.
     */
    fun show(crop: PhotoCrop?, play: Boolean) {
        animator?.cancel()
        val hadCrop = this.crop != null
        this.crop = crop
        isVisible = crop != null
        if (crop == null) {
            if (hadCrop) outline?.alpha = 1f
            return
        }
        if (!play) {
            moveTo(1f)
            return
        }
        moveTo(0f)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { moveTo(it.animatedValue as Float) }
            start()
        }
    }

    private fun moveTo(value: Float) {
        progress = value
        outline?.alpha = ScreenshotCut.outlineAt(value)
        invalidate()
    }

    private fun cut(): ScreenshotCut? {
        val crop = crop ?: return null
        if (width == 0 || height == 0) return null
        return ScreenshotCut(
            crop.original.width,
            crop.original.height,
            crop.photo,
            width.toFloat(),
            height.toFloat(),
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
        val crop = crop ?: return
        val cut = cut() ?: return
        val frame = cut.at(progress)
        val photo = cut.photoIn(frame.screen)
        val image = crop.original
        if (frame.outside > 0f) {
            photoPaint.alpha = (255 * frame.outside).toInt()
            target.set(frame.screen.left, frame.screen.top, frame.screen.right, frame.screen.bottom)
            canvas.drawBitmap(image, null, target, photoPaint)
        }
        source.set(
            (crop.photo.left * image.width).toInt(),
            (crop.photo.top * image.height).toInt(),
            (crop.photo.right * image.width).toInt(),
            (crop.photo.bottom * image.height).toInt(),
        )
        target.set(photo.left, photo.top, photo.right, photo.bottom)
        val radius = RADIUS_DP * density * frame.corner
        rounded.reset()
        rounded.addRoundRect(target, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(rounded)
        photoPaint.alpha = 255
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

    private companion object {
        const val DURATION_MS = 2000L

        /** The outline's padding in the card, so the photo fills the card where the outline will. */
        const val PADDING_DP = 20f
        const val CORNER_DP = 72f
        const val MARGIN_DP = 14f
        const val RADIUS_DP = 8f
    }
}
