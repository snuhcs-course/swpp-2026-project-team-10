// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import com.google.android.material.imageview.ShapeableImageView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion
import kotlin.math.abs
import kotlin.math.roundToInt

/** The reference thumbnail's long side while small, and its margin in the preview's corners (FR-3.9). */
internal const val REFERENCE_SMALL_DP = 72f
internal const val REFERENCE_MARGIN_DP = 12f

/** How much of a hidden thumbnail stays in the preview, and how wide the handle over that strip is (#237). */
internal const val REFERENCE_PEEK_DP = 16f
internal const val REFERENCE_HANDLE_DP = 48f

/** How fast a drag toward the side must end to hide the thumbnail, in dp a second: a flick, not a slow move. */
internal const val REFERENCE_FLING_DP = 800f

/** The smallest scale a thumbnail's corners and line are kept for; below it they would outgrow the view. */
private const val MIN_DRAWN_SCALE = 0.05f

/** TalkBack's way to move the reference thumbnail: an action id and its label for each corner. */
private val REFERENCE_MOVES = mapOf(
    ReferenceCorner.TOP_LEFT to (R.id.move_reference_top_left to R.string.move_reference_top_left),
    ReferenceCorner.TOP_RIGHT to (R.id.move_reference_top_right to R.string.move_reference_top_right),
    ReferenceCorner.BOTTOM_LEFT to (R.id.move_reference_bottom_left to R.string.move_reference_bottom_left),
    ReferenceCorner.BOTTOM_RIGHT to (R.id.move_reference_bottom_right to R.string.move_reference_bottom_right),
)

/** A thumbnail's glide under way: where it ends, whether as a mirror, and the animator that takes it there. */
private class ReferenceGlide(val target: ReferencePlacement, val mirrored: Boolean, val animator: ValueAnimator)

/** This thumbnail's glide while one is under way, which a test reads the middle of; null at rest. */
internal val View.referenceGlide: ValueAnimator?
    get() = (getTag(R.id.reference_glide) as? ReferenceGlide)?.animator

/** Stops this thumbnail where it is drawn now, as a finger that takes it does. */
internal fun View.stopReferenceGlide() {
    referenceGlide?.cancel()
}

/**
 * Draws this thumbnail's corners and line as at rest while the view is drawn at [scale] (#237): a view drawn smaller
 * than it is laid out would show them smaller too, and they would jump at the start of every glide. The radius and
 * the line's width at rest are read once, before any glide changes them.
 */
private fun ImageView.keepShapeAt(scale: Float) {
    val image = this as? ShapeableImageView ?: return
    val rest = getTag(R.id.reference_shape) as? FloatArray ?: run {
        val box = RectF(0f, 0f, layoutParams.width.toFloat(), layoutParams.height.toFloat())
        floatArrayOf(image.shapeAppearanceModel.topLeftCornerSize.getCornerSize(box), image.strokeWidth)
            .also { setTag(R.id.reference_shape, it) }
    }
    val drawn = scale.coerceAtLeast(MIN_DRAWN_SCALE)
    val line = rest[1] / drawn
    if (image.strokeWidth == line) return
    image.shapeAppearanceModel = image.shapeAppearanceModel.withCornerSize(rest[0] / drawn)
    image.strokeWidth = line
}

/**
 * Shows [photo] in this thumbnail where [place] says (FR-3.9), or hides it for null; [place] returns null while the
 * thumbnail must not move (a drag, or a screen not laid out yet). [mirrored], it is flipped left to right, as Subject
 * view shows the rear camera (#201). The image and the size are set only when they change, as each would lay the
 * screen out again.
 *
 * A thumbnail already shown glides into its new place, size, and alpha from where the finger or the last change left
 * it, in [Motion.THUMBNAIL_MS] (#237). Laying it out at every size on the way would lay the screen out every frame,
 * so it is laid out once, at the larger of the two sizes, and drawn smaller by its scale: growing, at its new size
 * from the start, and shrinking, at its old size until the end. So the photo is never a small one blown up, and its
 * corners and line stay as at rest ([keepShapeAt]). It scales about its middle, which is also what the flip turns
 * about; so this alone sets its scale. A call for the place a glide is already going to leaves that glide alone, or
 * every render of the screen would start it anew. With animations off, when it first shows, and with another photo,
 * it is there at once.
 */
internal fun ImageView.showReference(
    photo: Bitmap?,
    mirrored: Boolean = false,
    place: (Bitmap) -> ReferencePlacement?,
) {
    val shown = isVisible
    isVisible = photo != null
    val flip = if (mirrored) -1f else 1f
    if (photo == null) {
        stopReferenceGlide()
        setImageDrawable(null)
        scaleX = flip
        scaleY = 1f
        return
    }
    val samePhoto = (drawable as? BitmapDrawable)?.bitmap === photo
    if (!samePhoto) setImageBitmap(photo)
    val placement = place(photo)
    if (placement == null) {
        scaleX = flip * abs(scaleX)
        return
    }
    val glide = getTag(R.id.reference_glide) as? ReferenceGlide
    if (glide != null && glide.target == placement && glide.mirrored == mirrored) return
    glide?.animator?.cancel()

    // As drawn now: a glide just stopped left it between two places and sizes.
    val params = layoutParams
    val fromWidth = params.width * abs(scaleX)
    val fromHeight = params.height * scaleY
    val fromLeft = translationX + (params.width - fromWidth) / 2f
    val fromTop = translationY + (params.height - fromHeight) / 2f
    val fromAlpha = alpha
    val toLeft = placement.x.toFloat()
    val toTop = placement.y.toFloat()
    val toWidth = placement.width.toFloat()
    val toHeight = placement.height.toFloat()

    /** Draws the thumbnail with its box at ([left], [top]), [width] by [height], in the size it is laid out at. */
    fun draw(left: Float, top: Float, width: Float, height: Float, alpha: Float) {
        val laidOutWidth = params.width.coerceAtLeast(1)
        val laidOutHeight = params.height.coerceAtLeast(1)
        scaleX = flip * width / laidOutWidth
        scaleY = height / laidOutHeight
        translationX = left - (laidOutWidth - width) / 2f
        translationY = top - (laidOutHeight - height) / 2f
        this.alpha = alpha
        keepShapeAt(scaleY)
    }

    /** Lays the thumbnail out at its new size, where a glide's size differs from it. */
    fun layOut() {
        if (params.width == placement.width && params.height == placement.height) return
        params.width = placement.width
        params.height = placement.height
        layoutParams = params
    }

    /** At its place, laid out at its own size. */
    fun rest() {
        layOut()
        draw(toLeft, toTop, toWidth, toHeight, placement.alpha)
    }
    val there = fromLeft == toLeft && fromTop == toTop && fromWidth == toWidth && fromHeight == toHeight &&
        fromAlpha == placement.alpha
    if (!shown || !samePhoto || !Motion.on() || fromWidth <= 0f || fromHeight <= 0f || there) {
        rest()
        return
    }
    if (placement.width > params.width) layOut()
    val animator = ValueAnimator.ofFloat(0f, 1f)
    animator.duration = Motion.THUMBNAIL_MS
    animator.interpolator = Motion.STANDARD
    animator.addUpdateListener {
        val along = it.animatedValue as Float
        fun between(from: Float, to: Float) = from * (1f - along) + to * along
        draw(
            left = between(fromLeft, toLeft),
            top = between(fromTop, toTop),
            width = between(fromWidth, toWidth),
            height = between(fromHeight, toHeight),
            alpha = between(fromAlpha, placement.alpha),
        )
    }
    animator.addListener(
        object : AnimatorListenerAdapter() {
            private var stopped = false

            override fun onAnimationCancel(animation: Animator) {
                stopped = true
            }

            // At its end, and when a finger or another change stops it where it is.
            override fun onAnimationEnd(animation: Animator) {
                if (referenceGlide === animation) setTag(R.id.reference_glide, null)
                if (!stopped) rest()
            }
        },
    )
    setTag(R.id.reference_glide, ReferenceGlide(placement, mirrored, animator))
    draw(fromLeft, fromTop, fromWidth, fromHeight, fromAlpha)
    animator.start()
}

/**
 * Lays this handle over the strip of a thumbnail hidden at [corner]'s side of a preview [previewWidth] wide (#237),
 * or takes it away while the thumbnail is not hidden ([placement] null). It is a touch target wide and at least as
 * tall, centered on the strip but inside [area]: over a strip shorter than a touch target, as a wide photo's is, its
 * extra height reaches toward the preview's middle, not over the controls below or out of the preview. The system's
 * back swipe from that edge leaves it alone, so the strip can be pulled.
 */
internal fun View.showReferenceHandle(
    placement: ReferencePlacement?,
    corner: ReferenceCorner,
    previewWidth: Int,
    area: ReferenceArea,
) {
    isVisible = placement != null
    if (placement == null) return
    val touch = (REFERENCE_HANDLE_DP * resources.displayMetrics.density).roundToInt()
    val height = maxOf(placement.height, touch)
    val params = layoutParams
    if (params.width != touch || params.height != height) {
        params.width = touch
        params.height = height
        layoutParams = params
    }
    translationX = if (corner.isLeft) 0f else (previewWidth - touch).toFloat()
    val lowest = maxOf(area.top, area.bottom - height).toFloat()
    translationY = (placement.y - (height - placement.height) / 2f).coerceIn(area.top.toFloat(), lowest)
    ViewCompat.setSystemGestureExclusionRects(this, listOf(Rect(0, 0, touch, height)))
}

/**
 * TalkBack's way to use the thumbnail without a drag: the tap enlarges it or makes it small, by its [expanded] state,
 * an action for each corner but its own [corner] calls [moveTo], and *Hide at the side* calls [setHidden]. A [hidden]
 * thumbnail says so, and its tap shows it again (#237).
 */
internal fun View.setReferenceActions(
    expanded: () -> Boolean,
    corner: () -> ReferenceCorner,
    hidden: () -> Boolean,
    moveTo: (ReferenceCorner) -> Unit,
    setHidden: (Boolean) -> Unit,
) {
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val context = host.context
                if (hidden()) {
                    info.stateDescription = context.getString(R.string.reference_hidden)
                    info.addAction(
                        AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            context.getString(R.string.show_reference),
                        ),
                    )
                    return
                }
                val label = if (expanded()) R.string.shrink_reference else R.string.enlarge_reference
                info.addAction(
                    AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK, context.getString(label)),
                )
                val current = corner()
                for ((each, move) in REFERENCE_MOVES) {
                    if (each == current) continue
                    info.addAction(AccessibilityActionCompat(move.first, context.getString(move.second)))
                }
                info.addAction(
                    AccessibilityActionCompat(R.id.hide_reference, context.getString(R.string.hide_reference)),
                )
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == R.id.hide_reference) {
                    setHidden(true)
                    return true
                }
                val target = REFERENCE_MOVES.entries.firstOrNull { it.value.first == action }?.key
                    ?: return super.performAccessibilityAction(host, action, args)
                moveTo(target)
                return true
            }
        },
    )
}
