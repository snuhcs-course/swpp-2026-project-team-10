// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemSituationTileBinding
import com.lastpenguin.pix.generation.Situation

/**
 * How the situations show (FR-4.8): their names, their tiles' pictures and the occasions they suit, and what the scene
 * photo's step asks to frame. *Add a pose guide* offers all six in this order, two rows of three: the person's
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

/** The occasions a situation suits, under its name on its tile (FR-4.8). */
@get:StringRes
internal val Situation.example: Int
    get() = when (this) {
        Situation.DEFAULT -> R.string.situation_default_example
        Situation.BACKGROUND -> R.string.situation_background_example
        Situation.HALF_BODY -> R.string.situation_half_body_example
        Situation.FULL_BODY -> R.string.situation_full_body_example
        Situation.LANDMARK -> R.string.situation_landmark_example
        Situation.SELFIE -> R.string.situation_selfie_example
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

/** The six tiles of *Add a pose guide*, in [top] and [bottom]; a tap chooses one, which [render] marks with a check. */
internal class SituationTiles(top: LinearLayout, bottom: LinearLayout, onChoose: (Situation) -> Unit) {
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
            tile.situationExample.setText(situation.example)
            tile.root.contentDescription =
                row.context.getString(situation.title) + ", " + row.context.getString(situation.example)
            tile.root.setOnClickListener { onChoose(situation) }
            row.addView(tile.root)
            situation to tile
        }

    fun render(chosen: Situation) {
        tiles.forEach { (situation, tile) ->
            tile.root.isSelected = situation == chosen
            tile.situationCheck.isVisible = situation == chosen
        }
    }
}
