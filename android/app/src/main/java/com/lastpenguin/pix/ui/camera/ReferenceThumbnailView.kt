// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion
import kotlin.math.roundToInt

/** The reference thumbnail's long side while small, and its margin in the preview's corners (FR-3.9). */
internal const val REFERENCE_SMALL_DP = 72f
internal const val REFERENCE_MARGIN_DP = 12f

/** How much of a hidden thumbnail stays in the preview, and how wide the handle over that strip is (#237). */
internal const val REFERENCE_PEEK_DP = 16f
internal const val REFERENCE_HANDLE_DP = 48f

/** How fast a drag toward the side must end to hide the thumbnail, in dp a second: a flick, not a slow move. */
internal const val REFERENCE_FLING_DP = 800f

/** TalkBack's way to move the reference thumbnail: an action id and its label for each corner. */
private val REFERENCE_MOVES = mapOf(
    ReferenceCorner.TOP_LEFT to (R.id.move_reference_top_left to R.string.move_reference_top_left),
    ReferenceCorner.TOP_RIGHT to (R.id.move_reference_top_right to R.string.move_reference_top_right),
    ReferenceCorner.BOTTOM_LEFT to (R.id.move_reference_bottom_left to R.string.move_reference_bottom_left),
    ReferenceCorner.BOTTOM_RIGHT to (R.id.move_reference_bottom_right to R.string.move_reference_bottom_right),
)

/**
 * Shows [photo] in this thumbnail where [place] says (FR-3.9), or hides it for null; [place] returns null while the
 * thumbnail must not move (a drag, or a screen not laid out yet). The image and the size are set only when they
 * change, as each would lay the screen out again. A thumbnail already shown settles into its new place, size, and
 * alpha from where the finger or the last change left it, in [Motion.THUMBNAIL_MS] (#237): the size is laid out
 * once, and its scale grows or shrinks it from what it was. With animations off, and when it first shows, it is
 * there at once.
 */
internal fun ImageView.showReference(photo: Bitmap?, place: (Bitmap) -> ReferencePlacement?) {
    val shown = isVisible
    isVisible = photo != null
    if (photo == null) {
        animate().cancel()
        setImageDrawable(null)
        return
    }
    if ((drawable as? BitmapDrawable)?.bitmap !== photo) setImageBitmap(photo)
    val placement = place(photo) ?: return
    // Its size as drawn now, which a change under way has between two sizes.
    val params = layoutParams
    val fromWidth = params.width * scaleX
    val fromHeight = params.height * scaleY
    val resized = params.width != placement.width || params.height != placement.height
    if (resized) {
        params.width = placement.width
        params.height = placement.height
        layoutParams = params
    }
    val x = placement.x.toFloat()
    val y = placement.y.toFloat()
    animate().cancel()
    pivotX = 0f
    pivotY = 0f
    if (!shown || !Motion.on() || fromWidth <= 0f || fromHeight <= 0f) {
        scaleX = 1f
        scaleY = 1f
        translationX = x
        translationY = y
        alpha = placement.alpha
        return
    }
    scaleX = fromWidth / placement.width
    scaleY = fromHeight / placement.height
    val there = translationX == x && translationY == y && scaleX == 1f && scaleY == 1f && alpha == placement.alpha
    if (there) return
    animate()
        .translationX(x)
        .translationY(y)
        .scaleX(1f)
        .scaleY(1f)
        .alpha(placement.alpha)
        .setDuration(Motion.THUMBNAIL_MS)
        .setInterpolator(Motion.STANDARD)
        .start()
}

/**
 * Lays this handle over the strip of a thumbnail hidden at [corner]'s side of a preview [previewWidth] wide (#237),
 * or takes it away while the thumbnail is not hidden ([placement] null). It is a touch target wide and at least as
 * tall, centered on the strip, and the system's back swipe from that edge leaves it alone, so the strip can be
 * pulled.
 */
internal fun View.showReferenceHandle(placement: ReferencePlacement?, corner: ReferenceCorner, previewWidth: Int) {
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
    translationY = placement.y - (height - placement.height) / 2f
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
