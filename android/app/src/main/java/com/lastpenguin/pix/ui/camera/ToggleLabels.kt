// AI-generated with Claude Code, 2026-11-05, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.lastpenguin.pix.R
import kotlin.math.roundToInt

/** The size of a label's icon, as the top bar's small icons (Design 2.8). */
private const val LABEL_ICON_DP = 14f

/**
 * The label over the guide-edit mode toggle on both camera screens (Design 2.8): *Guide* in the guide's color while
 * drags and pinches move the guide, *Camera* while a pinch zooms; TalkBack reads the whole sentence.
 */
internal fun showPinchModeLabel(chip: TextView, guide: Boolean) {
    val context = chip.context
    chip.setText(if (guide) R.string.pinch_mode_guide else R.string.pinch_mode_camera)
    chip.setTextColor(context.getColor(if (guide) R.color.pix_guide else R.color.pix_text))
    chip.contentDescription =
        context.getString(if (guide) R.string.pinch_mode_guide_description else R.string.pinch_mode_camera_description)
    chip.isVisible = true
}

/** Sets [icon] in [tint] at the start of a label, or removes it for 0. */
internal fun setLabelIcon(chip: TextView, @DrawableRes icon: Int, @ColorRes tint: Int = R.color.pix_text) {
    val context = chip.context
    val size = (LABEL_ICON_DP * context.resources.displayMetrics.density).roundToInt()
    val drawable = if (icon == 0) null else AppCompatResources.getDrawable(context, icon)?.mutate()
    drawable?.setBounds(0, 0, size, size)
    chip.setCompoundDrawablesRelative(drawable, null, null, null)
    TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(context.getColor(tint)))
}

/** The bookmark of a *Save guide* notice: filled once saved, empty once removed. */
@DrawableRes
internal fun guideSaveIcon(notice: GuideSaveNotice): Int = when (notice) {
    GuideSaveNotice.SAVED, GuideSaveNotice.SAVED_OLDEST_REMOVED -> R.drawable.ic_bookmark_filled
    GuideSaveNotice.REMOVED -> R.drawable.ic_bookmark
    GuideSaveNotice.FAILED -> R.drawable.ic_alert_small
}

@ColorRes
internal fun guideSaveTint(notice: GuideSaveNotice): Int = when (notice) {
    GuideSaveNotice.SAVED, GuideSaveNotice.SAVED_OLDEST_REMOVED -> R.color.pix_accent
    GuideSaveNotice.REMOVED -> R.color.pix_text
    GuideSaveNotice.FAILED -> R.color.pix_danger
}
