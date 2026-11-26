// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui

import android.annotation.SuppressLint
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.R as materialR
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import kotlin.math.abs

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
 * A screen whose notices sit above one of its views, rather than at the bottom of the screen: on the camera screens the
 * line the zoom row stands on, so a notice takes the zoom row's place, over the bottom of the image, and never covers the
 * shutter or the controls under the image.
 */
interface NoticeAnchor {
    val noticeAnchor: View?
}

/**
 * Shows [text] as a short notice (#298): a Snackbar in Pix's look (`Widget.Pix.Snackbar`), never a Toast, so a notice can
 * offer an action and follows the screens. It goes on the activity's content, not on this screen's view, so a notice
 * shown right before the screen leaves, such as "The session has ended." on the way back to Camera, stays on the
 * screen that follows; it waits for that screen and sits above its [NoticeAnchor]. In a sheet that stays open, use
 * [showNoticeInDialog]. Every notice goes by itself after its [length], and earlier with a swipe down
 * ([SwipeDownToDismiss]), either way over `notice_out_ms` (`ThemeOverlay.Pix.Notice`).
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
): Snackbar = Snackbar.make(noticeContext(host), host, text, length.ms).apply {
    // An anchor the screen hides would put the notice in the middle of nowhere.
    if (anchor != null && anchor.isShown) setAnchorView(anchor)
    if (action != null) setAction(action.label) { action.run() }
    SwipeDownToDismiss(this).attach()
}

/** [host]'s theme with the notice's own: Material's fade at the end of its time lasts `notice_out_ms`. */
private fun noticeContext(host: View) = ContextThemeWrapper(host.context, R.style.ThemeOverlay_Pix_Notice)

/**
 * A notice that a swipe down takes away, on every screen (Design 2.8): it follows the finger down and fades, and goes
 * once dragged past half its height or flung down; a shorter, slower drag springs it back. Only down: a drag up or
 * sideways leaves it, also one that wanders down a little on its way sideways, since the drag starts only while the
 * finger has gone more down than sideways, and a fling counts only when mostly down. The drag may start on the action, such as Undo, which is then not pressed; a tap there still runs
 * it. Material's own swipe works only in a CoordinatorLayout and only sideways, and Pix's notices go on the activity's
 * content or a sheet's window, so this replaces the notice's touch handling, which otherwise only keeps touches from
 * the views under it.
 */
@VisibleForTesting
internal class SwipeDownToDismiss(private val notice: Snackbar) : View.OnTouchListener {
    private val view = notice.view
    private val config = ViewConfiguration.get(view.context)
    private val slop = config.scaledTouchSlop
    private val minimumFling = config.scaledMinimumFlingVelocity.toFloat()
    private val maximumFling = config.scaledMaximumFlingVelocity.toFloat()
    private var startX = 0f
    private var startY = 0f
    private var dragging = false

    // The touch went sideways first: it is no swipe down, whatever it does after.
    private var passed = false
    private var velocity: VelocityTracker? = null

    fun attach() {
        view.setOnTouchListener(this)
        view.findViewById<View>(materialR.id.snackbar_action)?.setOnTouchListener(this)
    }

    // The action's own touch handling sees every event until a drag starts, so its tap still goes through performClick.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(touched: View, event: MotionEvent): Boolean {
        // On the action, events it does not take for a drag go on to the button; on the notice, every one is taken.
        val onAction = touched !== view
        track(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                dragging = false
                passed = false
                view.animate().cancel()
                // A sheet under a notice in its window must not take the drag for its own.
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val down = event.rawY - startY
                val sideways = abs(event.rawX - startX)
                if (!dragging && sideways > slop && sideways >= down) passed = true
                if (!dragging && !passed && down > slop && down > sideways) {
                    dragging = true
                    // The action was pressed at the touch's start: it lets go without a tap.
                    if (onAction) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        touched.onTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                if (dragging) {
                    follow(down)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    release(event.rawY - startY)
                    end()
                    return true
                }
                end()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) settle(away = false)
                end()
            }
        }
        return !onAction || dragging
    }

    /** The notice [down] px below where it was, fading as it goes: half gone at half its height. */
    private fun follow(down: Float) {
        val moved = down.coerceAtLeast(0f)
        view.translationY = moved
        view.alpha = 1f - (moved / view.height.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun release(down: Float) {
        val tracker = velocity
        tracker?.computeCurrentVelocity(1_000, maximumFling)
        val speed = tracker?.yVelocity ?: 0f
        val across = abs(tracker?.xVelocity ?: 0f)
        // A fling away is mostly down: a fast diagonal one is not.
        settle(away = down > view.height / 2f || (speed >= minimumFling && speed > across && down > 0f))
    }

    /** Away: on down and out, then the notice is dismissed; otherwise back where it was. */
    private fun settle(away: Boolean) {
        if (!away) {
            if (!Motion.on()) {
                view.translationY = 0f
                view.alpha = 1f
            } else {
                view.animate().translationY(0f).alpha(1f).setDuration(Motion.FADE_MS).setInterpolator(Motion.STANDARD)
                    .start()
            }
            return
        }
        // Hidden first, so the dismissal does not play the notice's own exit from where it showed.
        val gone = {
            view.visibility = View.INVISIBLE
            notice.dismiss()
        }
        if (!Motion.on()) {
            gone()
            return
        }
        val ms = view.resources.getInteger(R.integer.notice_out_ms).toLong()
        view.animate().translationY(view.height.toFloat()).alpha(0f).setDuration(ms)
            .setInterpolator(Motion.STANDARD).withEndAction(gone).start()
    }

    /** Adds [event] to the drag's speed in the screen's coordinates, which stay put as the notice moves. */
    private fun track(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
        }
        val onScreen = MotionEvent.obtain(event).apply { setLocation(event.rawX, event.rawY) }
        velocity?.addMovement(onScreen)
        onScreen.recycle()
    }

    private fun end() {
        dragging = false
        passed = false
        velocity?.recycle()
        velocity = null
    }
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
