// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar

/** How long a notice stays: a Toast's two lengths, and longer for one that can be undone (#298). */
enum class NoticeLength(val ms: Int) {
    SHORT(2_000),
    LONG(3_500),

    /** A notice whose action takes back what was just done, such as Remove guide's Undo. */
    UNDO(5_000),
}

/** A notice's one action, such as Undo; [run] is called once, from the main thread. */
class NoticeAction(@StringRes val label: Int, val run: () -> Unit)

/**
 * A screen whose notices sit above one of its views, rather than at the bottom of the screen: the camera screens' zoom
 * row, so a notice never covers the shutter or the controls under the image.
 */
interface NoticeAnchor {
    val noticeAnchor: View?
}

/**
 * Shows [text] as a short notice (#298): a Snackbar in Pix's look (`Widget.Pix.Snackbar`), never a Toast, so a notice can
 * offer an action and follows the screens. It goes on the activity's content, not on this screen's view, so a notice
 * shown right before the screen leaves, such as "The session has ended." on the way back to Camera, stays on the
 * screen that follows; it waits for that screen and sits above its [NoticeAnchor]. In a sheet that stays open, use
 * [showNoticeInDialog].
 */
fun Fragment.showNotice(text: CharSequence, length: NoticeLength = NoticeLength.SHORT, action: NoticeAction? = null) {
    activity?.showNotice(text, length, action)
}

fun Fragment.showNotice(@StringRes text: Int, length: NoticeLength = NoticeLength.SHORT, action: NoticeAction? = null) {
    activity?.let { it.showNotice(it.getText(text), length, action) }
}

/** [Fragment.showNotice] from the activity itself, for notices that come on any screen, such as a session photo saved. */
fun FragmentActivity.showNotice(
    text: CharSequence,
    length: NoticeLength = NoticeLength.SHORT,
    action: NoticeAction? = null,
) {
    val content = findViewById<ViewGroup>(android.R.id.content) ?: return
    // After the navigation that may follow in the same turn, so the notice finds the screen it will show over.
    content.post {
        if (isFinishing || isDestroyed) return@post
        notice(content, (currentScreen() as? NoticeAnchor)?.noticeAnchor, text, length, action).show()
    }
}

/**
 * Shows [text] over this screen at once, above its [NoticeAnchor], for a notice whose action acts on this screen, such
 * as Undo. Returns the notice, so the screen can take it down when its action no longer applies.
 */
fun Fragment.showNoticeOnScreen(
    text: CharSequence,
    length: NoticeLength = NoticeLength.SHORT,
    action: NoticeAction? = null,
): Snackbar? {
    val content = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return null
    return notice(content, (this as? NoticeAnchor)?.noticeAnchor, text, length, action).also { it.show() }
}

/** Shows [text] inside this sheet's or dialog's own window, which would cover a notice on the activity. */
fun DialogFragment.showNoticeInDialog(@StringRes text: Int, length: NoticeLength = NoticeLength.SHORT) {
    val view = dialog?.window?.decorView?.findViewById<View>(android.R.id.content) ?: view ?: return
    notice(view, null, view.context.getText(text), length, null).show()
}

private fun notice(
    host: View,
    anchor: View?,
    text: CharSequence,
    length: NoticeLength,
    action: NoticeAction?,
): Snackbar = Snackbar.make(host, text, length.ms).apply {
    // An anchor the screen hides would put the notice in the middle of nowhere.
    if (anchor != null && anchor.isShown) setAnchorView(anchor)
    if (action != null) setAction(action.label) { action.run() }
}

/**
 * The screen shown now: the navigation host's current destination once its pending moves are done, or, without a host
 * (a screen test), the activity's last visible fragment.
 */
private fun FragmentActivity.currentScreen(): Fragment? {
    val manager = supportFragmentManager
    if (manager.isStateSaved) return null
    val host = manager.primaryNavigationFragment
    if (host != null) {
        val screens = host.childFragmentManager
        if (!screens.isStateSaved) screens.executePendingTransactions()
        return screens.primaryNavigationFragment
            ?: screens.fragments.lastOrNull { it.isVisible && it !is DialogFragment }
    }
    return manager.fragments.lastOrNull { it.isVisible && it !is DialogFragment }
}
