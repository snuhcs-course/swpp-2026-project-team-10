// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.session

import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

/**
 * The line right above the shutter in a session, on both phones (R&S 6.2, 6.6): a red dot while live, a bold title
 * that names the other person ("Dongje's camera" on Subject view, "Joonhyung" on Camera), "·", and the status.
 * [line] holds the dot and [title] in one row and [separator] and [status] in another, so that at a font too large
 * for one line the title stands over the status. The name can be long, such as "길동의 Galaxy S23 Ultra": one that
 * does not fit its line is cut short, so the rest of the title stays. [titleOf] gives the title for a name, null
 * being one not known yet.
 */
internal class SessionCaption(
    private val line: LinearLayout,
    private val dot: View,
    private val title: TextView,
    private val separator: TextView,
    private val status: TextView,
    private val titleOf: (String?) -> String,
) {
    /**
     * Fits the title for [name] and [statusText] into [width], the line's room: one line while both fit, the title
     * over the status otherwise. [live] shows the dot and the separator; without them the title stands alone.
     */
    fun fit(width: Int, name: String?, statusText: String, live: Boolean) {
        val gap = (dot.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
        val room = width - if (live) dot.layoutParams.width + gap else 0
        val full = titleOf(name)
        val rest = if (live) {
            separator.paint.measureText(separator.text.toString()) + status.paint.measureText(statusText) + 2 * gap
        } else {
            0f
        }
        // Before the first layout the width is not known: the whole name, fitted once it is.
        val oneLine = room <= 0 || title.paint.measureText(full) + rest <= room
        line.orientation = if (oneLine) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        separator.isVisible = live && oneLine
        val text = shortTitle(name, full, if (oneLine) room - rest else room.toFloat())
        if (title.text.toString() != text) title.text = text
    }

    /** [full], or with [name] cut short when it is wider than [width], so the rest of the title stays. */
    private fun shortTitle(name: String?, full: String, width: Float): String {
        if (name == null || width <= 0f) return full
        val paint = TextPaint(title.paint)
        if (paint.measureText(full) <= width) return full
        val rest = paint.measureText(titleOf(""))
        val short = TextUtils.ellipsize(name, paint, (width - rest).coerceAtLeast(0f), TextUtils.TruncateAt.END)
        return titleOf(short.toString())
    }
}
