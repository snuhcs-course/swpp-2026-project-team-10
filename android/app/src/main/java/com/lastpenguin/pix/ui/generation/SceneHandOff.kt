// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import kotlin.math.roundToInt

/**
 * How the scene photo step looked on Camera the moment it handed the photo to Generate poses (#210), in the window's
 * pixels: where Back's arrow was and how large, the step's title as drawn and where, the top of the flow's bar, and the
 * held photo's frame. Generate poses starts as that and unfolds from it ([HeaderUnfold]).
 */
internal class SceneHandOff(
    val back: PointF,
    val backIcon: Float,
    val title: Bitmap,
    val titleBox: RectF,
    val titleSize: Float,
    val progressTop: Float,
    val photo: Rect,
) {
    companion object {
        /** What Camera handed over for the Generate poses it opens next, which that screen takes once. */
        var pending: SceneHandOff? = null

        fun take(): SceneHandOff? = pending.also { pending = null }
    }
}

/**
 * Generate poses unfolding from the camera's scene photo step (#210), in step with the photo shrinking into its place
 * ([showArriving] from [photo]): the camera's bar grows into this screen's header, Back's arrow and the title from the
 * camera's size and place to their own, the camera's title fading into this one's, the flow's bar sliding down to its
 * place, and the step's button and everything under the bar fading in. Set up before [showArriving], so the photo is
 * drawn over the rest, and while this screen is laid out and has not scrolled.
 */
internal class HeaderUnfold(binding: FragmentDescribePoseBinding, private val from: SceneHandOff) {
    private val root = binding.root
    private val back = binding.backButton
    private val title = binding.describeTitle
    private val action = binding.generateButton
    private val bar = binding.flowProgress
    private val origin = root.windowAt()

    /** The camera's held photo, where the photo starts on this screen. */
    val photo = Rect(from.photo).apply { offset(-origin.x.roundToInt(), -origin.y.roundToInt()) }

    private val backAt = back.windowAt(back.width / 2f, back.height / 2f)
    private val backScale = from.backIcon / (back.drawable?.intrinsicWidth ?: back.width).coerceAtLeast(1)
    private val titleAt = title.windowAt(0f, title.height / 2f)
    private val titleScale = from.titleSize / title.textSize
    private val barShift = from.progressTop - bar.windowAt().y

    /** This screen as it is under the bar while the camera's look fades from it. */
    private val scrim = ContextCompat.getColor(root.context, R.color.pix_ground).toDrawable()
    private val cameraTitle = from.title.toDrawable(root.resources)

    init {
        root.overlay.add(scrim)
        root.overlay.add(cameraTitle)
        step(0f)
    }

    /** The views and drawings at [t] of the way, 0 as the camera looked to 1 as this screen is. */
    fun step(t: Float) {
        val left = 1f - t
        back.translationX = (from.back.x - backAt.x) * left
        back.translationY = (from.back.y - backAt.y) * left
        (backScale + (1f - backScale) * t).let {
            back.scaleX = it
            back.scaleY = it
        }
        // The title grows from its left edge on its line, as a header's title starts there.
        val scale = titleScale + (1f - titleScale) * t
        title.pivotX = 0f
        title.pivotY = title.height / 2f
        title.scaleX = scale
        title.scaleY = scale
        title.translationX = (from.titleBox.left - titleAt.x) * left
        title.translationY = (from.titleBox.centerY() - titleAt.y) * left
        title.alpha = t
        // The camera's title in the same place and at the same size, fading out as this one fades in.
        val size = scale / titleScale
        val width = from.title.width * size
        val height = from.title.height * size
        val x = titleAt.x - origin.x + title.translationX
        val y = titleAt.y - origin.y + title.translationY
        cameraTitle.setBounds(
            x.roundToInt(),
            (y - height / 2).roundToInt(),
            (x + width).roundToInt(),
            (y + height / 2).roundToInt(),
        )
        cameraTitle.alpha = (OPAQUE * left).roundToInt()
        action.alpha = t
        bar.translationY = barShift * left
        val under = (bar.windowAt().y - origin.y + bar.height).roundToInt()
        scrim.setBounds(0, under, root.width, root.height)
        scrim.alpha = (OPAQUE * left).roundToInt()
        root.invalidate()
    }

    /** Unfolds as [arrival] moves its photo, and is this screen once it stops. */
    fun follow(arrival: SceneArrival) {
        arrival.animator.addUpdateListener { step(arrival.moved) }
        arrival.animator.doOnEnd { finish() }
    }

    /** This screen as it is, the camera's look gone. */
    fun finish() {
        step(1f)
        root.overlay.remove(scrim)
        root.overlay.remove(cameraTitle)
    }

    private fun View.windowAt(x: Float = 0f, y: Float = 0f): PointF {
        val at = IntArray(2).also(::getLocationInWindow)
        return PointF(at[0] + x, at[1] + y)
    }

    private companion object {
        const val OPAQUE = 255f
    }
}
