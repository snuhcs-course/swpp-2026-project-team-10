// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.lastpenguin.pix.ui.Motion
import kotlin.math.abs

/** The scene photo on its way into its place on Generate poses: what is drawn over the screen, and its move. */
internal class SceneArrival(val photo: ArrivingPhoto, val animator: ValueAnimator) {
    companion object {
        /** How long the photo stays where the camera held it, so it is seen before it moves. */
        const val HOLD_MS = 300L

        /** How long it then takes to its place. */
        const val MOVE_MS = 500L
    }
}

/**
 * The scene photo as it is drawn on its way: in its box, with round corners, turned by [turn] around the box's middle.
 * A photo taken with the phone held sideways is kept upright (`GenerationUiState.scene`), while the camera showed it
 * as framed, a quarter turn away; turned back by that much it looks as the camera held it, and at no turn as its
 * thumbnail shows it.
 */
internal class ArrivingPhoto(private val picture: RoundedBitmapDrawable) : Drawable() {
    /** How far the photo is turned, clockwise, in degrees. */
    var turn = 0f

    var cornerRadius: Float
        get() = picture.cornerRadius
        set(value) {
            picture.cornerRadius = value
        }

    /** The part of the screen the turned photo covers: its box, with the sides swapped at a quarter turn. */
    fun covers(): Rect {
        val box = bounds
        if (abs(turn) != QUARTER) return Rect(box)
        val left = (box.left + box.right - box.height()) / 2
        val top = (box.top + box.bottom - box.width()) / 2
        return Rect(left, top, left + box.height(), top + box.width())
    }

    override fun draw(canvas: Canvas) {
        picture.bounds = bounds
        canvas.save()
        canvas.rotate(turn, bounds.exactCenterX(), bounds.exactCenterY())
        picture.draw(canvas)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        picture.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        picture.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val QUARTER = 90f
    }
}

/** The thumbnail's corners (ShapeAppearance.Pix.SmallThumbnail), which the photo takes on its way. */
private const val THUMBNAIL_RADIUS_DP = 12f
private const val HALF_TURN = 180
private const val FULL_TURN = 360

/**
 * Shows [scene], the scene photo just taken, coming from the camera into its [place], its thumbnail on this screen
 * (#296): drawn over the screen where the camera held it, as wide as the screen right [under] the header, or as high
 * as the room there for a frame taller than that. It moves as the photo of a screenshot's cut goes to its corner on
 * Reference confirm (ScreenshotCut), so loading a guide and a scene photo look alike: it holds for
 * [SceneArrival.HOLD_MS], then goes to the thumbnail in [SceneArrival.MOVE_MS], slow to start and slow to settle,
 * its corners rounding to the thumbnail's. A first try in 250 ms without the hold was too fast to follow on a phone.
 * Longer than Motion's other lengths, but nothing waits for it: the screen under it takes touches at once.
 *
 * [turn] is how far [scene] was turned clockwise from the frame the camera showed, to be upright as the phone was held
 * (`GenerationUiState.sceneTurn`). The photo starts in the frame's shape, turned back by that much, so nothing changes
 * at the moment this screen takes it from the camera, and turns upright on its way.
 *
 * The thumbnail already shows the photo under it, so nothing is left to set when the move ends, and nothing plays, and
 * null is returned, while animations are off. This screen must be laid out.
 */
internal fun ViewGroup.showArriving(scene: Bitmap, place: View, under: View, turn: Int = 0): SceneArrival? {
    if (!Motion.on()) return null
    val thumbnail = Rect(0, 0, place.width, place.height).also { offsetDescendantRectToMyCoords(place, it) }
    val top = Rect(0, 0, under.width, under.height).also { offsetDescendantRectToMyCoords(under, it) }.bottom
    // The frame as the camera held it, which a quarter turn away has the photo's sides the other way round.
    val sideways = turn % HALF_TURN != 0
    val frameWidth = if (sideways) scene.height else scene.width
    val frameHeight = if (sideways) scene.width else scene.height
    val wide = minOf(width, (height - top) * frameWidth / frameHeight)
    val left = (width - wide) / 2
    val frame = Rect(left, top, left + wide, top + wide * frameHeight / frameWidth)
    // The photo's own box while it is drawn into that frame turned back.
    val held = if (sideways) {
        val boxLeft = (frame.left + frame.right - frame.height()) / 2
        val boxTop = (frame.top + frame.bottom - frame.width()) / 2
        Rect(boxLeft, boxTop, boxLeft + frame.height(), boxTop + frame.width())
    } else {
        frame
    }
    // Back by the turn, the shorter way round.
    val back = (-turn % FULL_TURN).let { if (it <= -HALF_TURN) it + FULL_TURN else it }.toFloat()
    val photo = ArrivingPhoto(RoundedBitmapDrawableFactory.create(resources, scene)).apply {
        bounds = held
        this.turn = back
    }
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
        photo.turn = back * (1f - t)
        photo.cornerRadius = radius * t
        invalidate()
    }
    animator.doOnEnd { overlay.remove(photo) }
    animator.start()
    return SceneArrival(photo, animator)
}
