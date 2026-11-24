// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemSituationTileBinding
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.ui.Motion

/**
 * How the situations show (FR-4.8): their names, their tiles' pictures, and what the scene photo's step asks to
 * frame. *Add a pose guide* offers all six in this order, two rows of three: the person's
 * framings, then the place's; Generate poses names the one the photo was taken for.
 * Owner: Server/AI/Sync (Iter4-P13).
 */
internal val SHEET_SITUATIONS = listOf(
    Situation.DEFAULT,
    Situation.HALF_BODY,
    Situation.FULL_BODY,
    Situation.BACKGROUND,
    Situation.LANDMARK,
    Situation.SELFIE,
)

/** The situation *Add a pose guide* recommends, marked on its tile (#243): *Auto*, whose prompts suit any scene. */
internal val RECOMMENDED_SITUATION = Situation.DEFAULT

@get:StringRes
internal val Situation.title: Int
    get() = when (this) {
        Situation.DEFAULT -> R.string.situation_default
        Situation.BACKGROUND -> R.string.situation_background
        Situation.HALF_BODY -> R.string.situation_half_body
        Situation.FULL_BODY -> R.string.situation_full_body
        Situation.LANDMARK -> R.string.situation_landmark
        Situation.SELFIE -> R.string.situation_selfie
    }

@get:DrawableRes
internal val Situation.drawing: Int
    get() = when (this) {
        Situation.DEFAULT -> R.drawable.situation_default
        Situation.BACKGROUND -> R.drawable.situation_background
        Situation.HALF_BODY -> R.drawable.situation_half_body
        Situation.FULL_BODY -> R.drawable.situation_full_body
        Situation.LANDMARK -> R.drawable.situation_landmark
        Situation.SELFIE -> R.drawable.situation_selfie
    }

/**
 * The options Generate poses offers under its field (FR-4.15, [#274]): poses often taken in the situation, each a few
 * words that go into the description as they are (PoseOptions). A fixed list on the phone, so they cost no request and
 * show at once; *Auto*'s serve a photo without a situation, as from an older photographer. Each was tried once as a
 * description (Design 2.6.3): "facing the landmark" turned the view around instead of the person, and is not offered.
 */
internal val Situation.poseOptions: List<Int>
    get() = when (this) {
        Situation.DEFAULT -> listOf(
            R.string.pose_option_peace_sign,
            R.string.pose_option_waving,
            R.string.pose_option_hand_in_pocket,
            R.string.pose_option_arms_crossed,
            R.string.pose_option_looking_back,
            R.string.pose_option_arms_up,
        )

        Situation.HALF_BODY -> listOf(
            R.string.pose_option_peace_sign,
            R.string.pose_option_cheek_heart,
            R.string.pose_option_chin_on_hand,
            R.string.pose_option_arms_crossed,
            R.string.pose_option_hand_through_hair,
            R.string.pose_option_waving,
        )

        Situation.FULL_BODY -> listOf(
            R.string.pose_option_peace_sign,
            R.string.pose_option_hand_in_pocket,
            R.string.pose_option_looking_back,
            R.string.pose_option_walking,
            R.string.pose_option_arms_up,
            R.string.pose_option_thumbs_up,
        )

        Situation.BACKGROUND -> listOf(
            R.string.pose_option_back_view,
            R.string.pose_option_looking_far,
            R.string.pose_option_walking_away,
            R.string.pose_option_arms_wide,
            R.string.pose_option_sitting_looking,
        )

        Situation.LANDMARK -> listOf(
            R.string.pose_option_pointing,
            R.string.pose_option_arms_wide,
            R.string.pose_option_on_the_palm,
            R.string.pose_option_peace_sign,
            R.string.pose_option_arms_up,
        )

        Situation.SELFIE -> listOf(
            R.string.pose_option_peace_sign,
            R.string.pose_option_cheek_heart,
            R.string.pose_option_wink,
            R.string.pose_option_chin_in_hands,
            R.string.pose_option_finger_on_cheek,
            R.string.pose_option_waving,
        )
    }

/**
 * What the scene photo's step asks to frame (#204): the landmark for a landmark's poses, which crop inside the photo,
 * and the whole person for full body, whose candidates otherwise guess the legs (FR-4.8, Design 2.6.3).
 */
@get:StringRes
internal val Situation.framingHint: Int
    get() = when (this) {
        Situation.DEFAULT -> R.string.scene_framing_hint
        Situation.BACKGROUND -> R.string.scene_framing_hint_background
        Situation.HALF_BODY -> R.string.scene_framing_hint_half_body
        Situation.FULL_BODY -> R.string.scene_framing_hint_full_body
        Situation.LANDMARK -> R.string.scene_framing_hint_landmark
        Situation.SELFIE -> R.string.scene_framing_hint_selfie
    }

/**
 * The six tiles of *Add a pose guide*, in [top] and [bottom]; a tile is the button that starts its situation's poses,
 * and [RECOMMENDED_SITUATION]'s carries a badge. A touch dims a tile and shrinks it to [PRESSED_SCALE] in
 * [Motion.PRESS_MS] at once, not after the 100 ms a view in a scrolling sheet waits to look pressed, and a touch that
 * leaves the tile undoes it (#243). The tap goes on at release: [onTap] says whether the sheet goes on with it, so the
 * tile stays pressed while the sheet leaves, or is itself again, as under the consent notice. While [blocked], a touch
 * presses nothing.
 */
internal class SituationTiles(
    top: LinearLayout,
    bottom: LinearLayout,
    private val blocked: () -> Boolean,
    private val onTap: (Situation) -> Boolean,
) {
    private val touchSlop = ViewConfiguration.get(top.context).scaledTouchSlop

    private val tiles: Map<Situation, ItemSituationTileBinding> =
        SHEET_SITUATIONS.withIndex().associate { (i, situation) ->
            val row = if (i < SHEET_SITUATIONS.size / 2) top else bottom
            val tile = ItemSituationTileBinding.inflate(LayoutInflater.from(row.context), row, false)
            if (row.childCount > 0) {
                tile.root.updateLayoutParams<LinearLayout.LayoutParams> {
                    marginStart = row.resources.getDimensionPixelSize(R.dimen.situation_tile_gap)
                }
            }
            tile.situationImage.setImageResource(situation.drawing)
            tile.situationName.setText(situation.title)
            val name = row.context.getString(situation.title)
            val recommended = situation == RECOMMENDED_SITUATION
            tile.situationBadge.isVisible = recommended
            tile.root.contentDescription =
                if (recommended) row.context.getString(R.string.situation_recommended_description, name) else name
            tile.root.setOnTouchListener { view, event ->
                follow(tile, view, event)
                false
            }
            tile.root.setOnClickListener {
                if (!blocked()) tile.showPressed(onTap(situation))
            }
            row.addView(tile.root)
            situation to tile
        }

    /** The pressed look follows the finger: on at touch down, off once it leaves the tile or the touch is cancelled. */
    private fun follow(tile: ItemSituationTileBinding, view: View, event: MotionEvent) {
        if (blocked()) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tile.showPressed(true)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (!view.holds(event)) tile.showPressed(false)
            MotionEvent.ACTION_CANCEL -> tile.showPressed(false)
        }
    }

    /** Whether [event] is on this view, or within the touch slop around it, where a release still taps it. */
    private fun View.holds(event: MotionEvent): Boolean =
        event.x >= -touchSlop && event.y >= -touchSlop && event.x < width + touchSlop && event.y < height + touchSlop

    /** Dims and shrinks the tile, or brings it back, in [Motion.PRESS_MS]; at once while animations are off. */
    private fun ItemSituationTileBinding.showPressed(pressed: Boolean) {
        val scale = if (pressed) PRESSED_SCALE else 1f
        val dim = if (pressed) 1f else 0f
        root.animate().cancel()
        situationDim.animate().cancel()
        if (!Motion.on()) {
            root.scaleX = scale
            root.scaleY = scale
            situationDim.alpha = dim
            return
        }
        root.animate().scaleX(scale).scaleY(scale).setDuration(Motion.PRESS_MS).setInterpolator(Motion.STANDARD)
            .start()
        situationDim.animate().alpha(dim).setDuration(Motion.PRESS_MS).setInterpolator(Motion.STANDARD).start()
    }

    /** Without a front camera ([selfie] false), *Selfie*'s tile is off and dimmed (FR-4.8). */
    fun render(selfie: Boolean) {
        tiles.forEach { (situation, tile) ->
            val usable = situation != Situation.SELFIE || selfie
            tile.root.isEnabled = usable
            tile.root.alpha = if (usable) 1f else OFF_TILE_ALPHA
        }
    }
}

/** A tile that cannot be chosen, as Material dims a disabled control. */
private const val OFF_TILE_ALPHA = 0.38f

/** A tile's size while pressed (#243). */
private const val PRESSED_SCALE = 0.97f
