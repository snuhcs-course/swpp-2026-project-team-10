// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.ui.ScreenTestSupport
import java.time.Duration
import org.robolectric.Shadows.shadowOf

/** The session photos' viewer on [screen], for the screen tests of Camera and Subject view, once it first opened. */
internal fun photoViewerOn(screen: View): ViewPhotoViewerBinding =
    ViewPhotoViewerBinding.bind(checkNotNull(screen.findViewById(R.id.photoViewer)) { "The viewer was never opened" })

/** The page of the viewer on screen. */
private fun ViewPhotoViewerBinding.pageOnScreen(): View? =
    (photoViewerPages.getChildAt(0) as RecyclerView)
        .findViewHolderForAdapterPosition(photoViewerPages.currentItem)?.itemView

/** The picture of the page on screen, or null while it has none. */
internal fun ViewPhotoViewerBinding.pageShows(): Bitmap? =
    (pageOnScreen()?.findViewById<ImageView>(R.id.photoPageImage)?.drawable as? BitmapDrawable)?.bitmap

/** Whether the page on screen says its photo is not there. */
internal fun ViewPhotoViewerBinding.pageSaysUnavailable(): Boolean =
    pageOnScreen()?.findViewById<View>(R.id.photoPageMessage)?.isShown == true

/** Every text the viewer shows, its pages' left out: none, since it has no title and no counter. */
internal fun ViewPhotoViewerBinding.textsShown(): List<String> {
    fun texts(view: View): List<String> = when {
        !view.isShown || view === photoViewerPages -> emptyList()
        view is TextView -> listOfNotNull(view.text?.toString()?.takeIf { it.isNotEmpty() })
        view is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    return texts(root)
}

/** The thumbnails of the strip under the photo, as laid out; none while it is not shown. */
private fun ViewPhotoViewerBinding.stripItems(): List<View> = when {
    !photoViewerStrip.isShown -> emptyList()
    else -> (0 until photoViewerStrip.childCount).map { photoViewerStrip.getChildAt(it) }
}

/** How many photos the strip under the photo has, or 0 while it is not shown. */
internal fun ViewPhotoViewerBinding.stripCount(): Int =
    if (photoViewerStrip.isShown) photoViewerStrip.adapter!!.itemCount else 0

/** Which of the strip's thumbnails is selected, by its place in the list; null when none is. */
internal fun ViewPhotoViewerBinding.stripSelected(): Int? =
    stripItems().firstOrNull { it.isSelected }?.let { photoViewerStrip.getChildAdapterPosition(it) }

/** Taps the strip's thumbnail of the photo at [position]. */
internal fun ViewPhotoViewerBinding.tapStrip(position: Int) {
    val thumbnail =
        checkNotNull(photoViewerStrip.findViewHolderForAdapterPosition(position)) { "No thumbnail $position" }
    thumbnail.itemView.performClick()
}

/** The photo of the page on screen, which zooms. */
internal fun ViewPhotoViewerBinding.pagePhoto(): ZoomablePhotoView =
    checkNotNull(pageOnScreen()?.findViewById(R.id.photoPageImage)) { "No page on screen" }

/** What TalkBack reads of the page on screen: which photo of how many. */
internal fun ViewPhotoViewerBinding.pageReads(): String? = pagePhoto().contentDescription?.toString()

/** Whether the header, with Back, Share, and Delete, shows; a tap on the photo hides it with the strip. */
internal fun ViewPhotoViewerBinding.headerShown(): Boolean = photoViewerHeader.isShown && photoViewerHeader.alpha == 1f

/** The middle of the page on screen, in the viewer's root. */
private fun ViewPhotoViewerBinding.photoMiddle(): Pair<Float, Float> {
    val page = pagePhoto()
    val at = IntArray(2).also(page::getLocationInWindow)
    val origin = IntArray(2).also(root::getLocationInWindow)
    return Pair(at[0] - origin[0] + page.width / 2f, at[1] - origin[1] + page.height / 2f)
}

/** One finger's [action] at ([x], [y]) through the viewer's root, as a finger on the screen, [at] ms after [down]. */
private fun ViewPhotoViewerBinding.finger(action: Int, down: Long, at: Long, x: Float, y: Float) {
    val event = MotionEvent.obtain(down, down + at, action, x, y, 0)
    try {
        root.dispatchTouchEvent(event)
    } finally {
        event.recycle()
    }
}

/** A tap on the middle of the photo, and the wait for a second tap, after which it counts as one. */
internal fun ViewPhotoViewerBinding.tapPhoto() {
    val (x, y) = photoMiddle()
    val down = SystemClock.uptimeMillis()
    finger(MotionEvent.ACTION_DOWN, down, 0, x, y)
    finger(MotionEvent.ACTION_UP, down, 50, x, y)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
}

/** Two quick taps on the photo, [dx] px right of its middle; the zoom's animation plays to its end. */
internal fun ViewPhotoViewerBinding.doubleTapPhoto(dx: Float = 0f) {
    val (middleX, y) = photoMiddle()
    val x = middleX + dx
    val first = SystemClock.uptimeMillis()
    finger(MotionEvent.ACTION_DOWN, first, 0, x, y)
    finger(MotionEvent.ACTION_UP, first, 50, x, y)
    val second = first + 150
    finger(MotionEvent.ACTION_DOWN, second, 0, x, y)
    finger(MotionEvent.ACTION_UP, second, 50, x, y)
    ScreenTestSupport.idle()
}

/**
 * A drag of the photo [px] down from its middle, then a lift: [flick] in 30 ms, a quick flick; otherwise in half a
 * second, held still before the lift, so it is no flick.
 */
internal fun ViewPhotoViewerBinding.dragPhotoDown(px: Float, flick: Boolean = false) {
    val (x, y) = photoMiddle()
    val down = SystemClock.uptimeMillis()
    val step = if (flick) 3L else 50L
    finger(MotionEvent.ACTION_DOWN, down, 0, x, y)
    for (i in 1..10) finger(MotionEvent.ACTION_MOVE, down, i * step, x, y + px * i / 10)
    val last = 10 * step + if (flick) 0L else 200L
    if (!flick) finger(MotionEvent.ACTION_MOVE, down, last, x, y + px)
    finger(MotionEvent.ACTION_UP, down, last + if (flick) 3L else 100L, x, y + px)
    ScreenTestSupport.idle()
}
