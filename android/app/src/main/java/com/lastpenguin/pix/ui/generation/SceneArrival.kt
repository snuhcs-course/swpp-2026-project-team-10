// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.lastpenguin.pix.ui.Motion

/** The scene photo on its way into its place on Generate poses: what is drawn over the screen, and its move. */
internal class SceneArrival(val photo: Drawable, val animator: ValueAnimator) {
    companion object {
        /** How long the photo stays where the camera held it, so it is seen before it moves. */
        const val HOLD_MS = 300L

        /** How long it then takes to its place. */
        const val MOVE_MS = 500L
    }
}

/** The thumbnail's corners (ShapeAppearance.Pix.SmallThumbnail), which the photo takes on its way. */
private const val THUMBNAIL_RADIUS_DP = 12f

/**
 * Shows [scene], the scene photo just taken, coming from the camera into its [place], its thumbnail on this screen
 * (#296): drawn over the screen where the camera held it, as wide as the screen right [under] the header, or as high
 * as the room there for a photo taller than that. It moves as the photo of a screenshot's cut goes to its corner on
 * Reference confirm (ScreenshotCut), so loading a guide and a scene photo look alike: it holds for
 * [SceneArrival.HOLD_MS], then goes to the thumbnail in [SceneArrival.MOVE_MS], slow to start and slow to settle,
 * its corners rounding to the thumbnail's. A first try in 250 ms without the hold was too fast to follow on a phone.
 * Longer than Motion's other lengths, but nothing waits for it: the screen under it takes touches at once. The
 * thumbnail already shows the photo under it, so nothing is left to set when the move ends, and nothing plays, and null
 * is returned, while animations are off. This screen must be laid out.
 */
internal fun ViewGroup.showArriving(scene: Bitmap, place: View, under: View): SceneArrival? {
    if (!Motion.on()) return null
    val thumbnail = Rect(0, 0, place.width, place.height).also { offsetDescendantRectToMyCoords(place, it) }
    val top = Rect(0, 0, under.width, under.height).also { offsetDescendantRectToMyCoords(under, it) }.bottom
    val wide = minOf(width, (height - top) * scene.width / scene.height)
    val left = (width - wide) / 2
    val held = Rect(left, top, left + wide, top + wide * scene.height / scene.width)
    val photo = RoundedBitmapDrawableFactory.create(resources, scene).apply { bounds = held }
    val radius = THUMBNAIL_RADIUS_DP * resources.displayMetrics.density
    overlay.add(photo)
    val total = SceneArrival.HOLD_MS + SceneArrival.MOVE_MS
    val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(total)
    // The hold and the easing are in the frames themselves, as in the cut.
    animator.interpolator = LinearInterpolator()
    animator.addUpdateListener {
        val moved = ((it.animatedFraction * total - SceneArrival.HOLD_MS) / SceneArrival.MOVE_MS).coerceIn(0f, 1f)
        val t = moved * moved * (3f - 2f * moved)
        fun between(from: Int, to: Int) = (from + (to - from) * t).toInt()
        photo.setBounds(
            between(held.left, thumbnail.left),
            between(held.top, thumbnail.top),
            between(held.right, thumbnail.right),
            between(held.bottom, thumbnail.bottom),
        )
        photo.cornerRadius = radius * t
        invalidate()
    }
    animator.doOnEnd { overlay.remove(photo) }
    animator.start()
    return SceneArrival(photo, animator)
}
