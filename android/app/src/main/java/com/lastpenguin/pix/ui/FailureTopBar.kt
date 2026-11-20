// AI-generated with Claude Code, 2026-11-10, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui

import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import kotlin.math.roundToInt

/**
 * A failure screen of the pose and guide steps with its top bar (R&S 6.2, 6.3; #199): Back at the top left with
 * [onBack], and [action] at the top right with [onAction], or no action when the failure has no retry. The two buttons
 * at the bottom go; the session's failure screens keep them.
 */
internal fun FragmentFailureBinding.showFailureTopBar(
    @StringRes action: Int?,
    onBack: () -> Unit,
    onAction: () -> Unit = {},
) {
    failureTopBar.isVisible = true
    failureCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = (CARD_UNDER_TOP_BAR_DP * root.resources.displayMetrics.density).roundToInt()
    }
    backButton.setOnClickListener { onBack() }
    topActionButton.isVisible = action != null
    if (action != null) {
        topActionButton.setText(action)
        topActionButton.setOnClickListener { onAction() }
    }
    primaryButton.isVisible = false
    secondaryButton.isVisible = false
}

/** The card starts 8 dp under the 56 dp top bar, at 72 dp like every screen's content (#199). */
private const val CARD_UNDER_TOP_BAR_DP = 8
