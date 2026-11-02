// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.lastpenguin.pix.R
import com.lastpenguin.pix.session.SessionState

/** Up to this delay the dot is green: the live view feels immediate. */
internal const val LATENCY_GOOD_MS = 150

/** Up to this delay the dot is yellow; above it, red: moves on one phone visibly lag on the other. */
internal const val LATENCY_FAIR_MS = 300

/**
 * The latency indicator (FR-10.3) on Camera and Subject view: while the setting is on and the session is connected or
 * reconnecting, "Delay 120 ms" with a green, yellow, or red dot, and a dash while there is no number yet or the link is
 * being restored.
 */
internal fun TextView.renderLatency(show: Boolean, session: SessionState, latencyMs: Int?) {
    val live = session is SessionState.Connected || session is SessionState.Reconnecting
    isVisible = show && live
    if (!isVisible) return
    val ms = latencyMs.takeIf { session is SessionState.Connected }
    text =
        if (ms != null) context.getString(R.string.latency_value, ms) else context.getString(R.string.latency_unknown)
    val color = when {
        ms == null -> R.color.pix_text_faint
        ms <= LATENCY_GOOD_MS -> R.color.pix_live
        ms <= LATENCY_FAIR_MS -> R.color.pix_accent
        else -> R.color.pix_danger
    }
    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, color)))
}
