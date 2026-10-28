// AI-generated with Claude Code, 2026-10-27, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
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

/** The reference thumbnail's long side while small, and its margin in the preview's corners (FR-3.9). */
internal const val REFERENCE_SMALL_DP = 72f
internal const val REFERENCE_MARGIN_DP = 12f
internal const val REFERENCE_MOVE_MS = 150L

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
 * change, as each would lay the screen out again, and a thumbnail already shown glides to its new place.
 */
internal fun ImageView.showReference(photo: Bitmap?, place: (Bitmap) -> ReferencePlacement?) {
    val shown = isVisible
    isVisible = photo != null
    if (photo == null) {
        setImageDrawable(null)
        return
    }
    if ((drawable as? BitmapDrawable)?.bitmap !== photo) setImageBitmap(photo)
    val placement = place(photo) ?: return
    val params = layoutParams
    if (params.width != placement.width || params.height != placement.height) {
        params.width = placement.width
        params.height = placement.height
        layoutParams = params
    }
    val x = placement.x.toFloat()
    val y = placement.y.toFloat()
    if (!shown) {
        animate().cancel()
        translationX = x
        translationY = y
    } else if (translationX != x || translationY != y) {
        animate().translationX(x).translationY(y).setDuration(REFERENCE_MOVE_MS).start()
    }
}

/**
 * TalkBack's way to use the thumbnail without a drag: the tap enlarges it or makes it small, by its [expanded] state,
 * and an action for each corner but its own [corner] calls [moveTo].
 */
internal fun View.setReferenceActions(
    expanded: () -> Boolean,
    corner: () -> ReferenceCorner,
    moveTo: (ReferenceCorner) -> Unit,
) {
    ViewCompat.setAccessibilityDelegate(
        this,
        object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val label = if (expanded()) R.string.shrink_reference else R.string.enlarge_reference
                info.addAction(
                    AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK, host.context.getString(label)),
                )
                val current = corner()
                for ((each, move) in REFERENCE_MOVES) {
                    if (each == current) continue
                    info.addAction(AccessibilityActionCompat(move.first, host.context.getString(move.second)))
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val target = REFERENCE_MOVES.entries.firstOrNull { it.value.first == action }?.key
                    ?: return super.performAccessibilityAction(host, action, args)
                moveTo(target)
                return true
            }
        },
    )
}
