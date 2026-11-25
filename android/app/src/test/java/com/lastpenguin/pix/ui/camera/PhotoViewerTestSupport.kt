// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Joonhyung Han
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
import com.lastpenguin.pix.ui.settings.boundsOnScreen
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin
import org.robolectric.Shadows.shadowOf

/** The session photos' viewer on [screen], for the screen tests of Camera and Subject view, once it first opened. */
internal fun photoViewerOn(screen: View): ViewPhotoViewerBinding =
    ViewPhotoViewerBinding.bind(checkNotNull(screen.findViewById(R.id.photoViewer)) { "The viewer was never opened" })

/** Whether the grid of the phone's photos shows, faded in over the pages. */
internal fun ViewPhotoViewerBinding.gridShown(): Boolean = photoViewerGrid.isShown && photoViewerAlbum.alpha == 1f

/** How many photos the grid has, or 0 while it is not shown. */
internal fun ViewPhotoViewerBinding.gridCount(): Int =
    if (photoViewerGrid.isShown) photoViewerGrid.adapter!!.itemCount else 0

/** The grid's photo at [position], counted from the top left, as laid out. */
private fun ViewPhotoViewerBinding.cell(position: Int): View =
    checkNotNull(photoViewerGrid.findViewHolderForAdapterPosition(position)) { "No photo $position in the grid" }
        .itemView

/** The thumbnail the grid's photo at [position] shows, or null while it has none. */
internal fun ViewPhotoViewerBinding.gridShows(position: Int): Bitmap? =
    (cell(position).findViewById<ImageView>(R.id.photoCellImage).drawable as? BitmapDrawable)?.bitmap

/** What TalkBack reads of the grid's photo at [position]: which photo of how many. */
internal fun ViewPhotoViewerBinding.gridReads(position: Int): String? = cell(position).contentDescription?.toString()

/** Taps the grid's photo at [position], counted from the top left. */
internal fun ViewPhotoViewerBinding.tapGrid(position: Int) {
    cell(position).performClick()
}

/** How many albums the Albums tab lists, or 0 while it is not shown. */
internal fun ViewPhotoViewerBinding.albumsCount(): Int =
    if (photoViewerAlbums.isShown) photoViewerAlbums.adapter!!.itemCount else 0

/** The Albums tab's album at [position], as laid out. */
private fun ViewPhotoViewerBinding.albumCard(position: Int): View =
    checkNotNull(photoViewerAlbums.findViewHolderForAdapterPosition(position)) { "No album $position" }.itemView

/** The name and the count the Albums tab's album at [position] shows. */
internal fun ViewPhotoViewerBinding.albumShows(position: Int): Pair<String, String> {
    val card = albumCard(position)
    return card.findViewById<TextView>(R.id.photoAlbumName).text.toString() to
        card.findViewById<TextView>(R.id.photoAlbumCount).text.toString()
}

/** What TalkBack reads of the Albums tab's album at [position]: its name and how many photos. */
internal fun ViewPhotoViewerBinding.albumReads(
    position: Int,
): String? = albumCard(position).contentDescription?.toString()

/** Taps the Albums tab's album at [position]. */
internal fun ViewPhotoViewerBinding.tapAlbum(position: Int) {
    albumCard(position).performClick()
}

/** The page of the viewer on screen. */
private fun ViewPhotoViewerBinding.pageOnScreen(): View? =
    (photoViewerPages.getChildAt(0) as RecyclerView)
        .findViewHolderForAdapterPosition(photoViewerPages.currentItem)?.itemView

/** The picture of the page on screen, or null while it has none. */
internal fun ViewPhotoViewerBinding.pageShows(): Bitmap? =
    (pageOnScreen()?.findViewById<ImageView>(R.id.photoPageImage)?.drawable as? BitmapDrawable)?.bitmap

/** Whether the pages show, under no grid. */
internal fun ViewPhotoViewerBinding.pagesShown(): Boolean = photoViewerPages.isShown && !photoViewerAlbum.isShown

/** Whether the page on screen says its photo is not there. */
internal fun ViewPhotoViewerBinding.pageSaysUnavailable(): Boolean =
    pageOnScreen()?.findViewById<View>(R.id.photoPageMessage)?.isShown == true

/** Every text the viewer shows, its pages' left out: none over a page, which has no title and no counter. */
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

/** The middle of the page on screen, in the viewer's root, also while the viewer stands turned with the phone. */
private fun ViewPhotoViewerBinding.photoMiddle(): Pair<Float, Float> {
    val page = checkNotNull(boundsOnScreen(pagePhoto())) { "The page is not shown" }
    val origin = IntArray(2).also(root::getLocationOnScreen)
    return Pair(page.exactCenterX() - origin[0], page.exactCenterY() - origin[1])
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
 * second, held still before the lift, so it is no flick. Down is the photo's, as the user sees it: while the viewer
 * stands turned with the phone, another way on the screen.
 */
internal fun ViewPhotoViewerBinding.dragPhotoDown(px: Float, flick: Boolean = false) {
    val (x, y) = photoMiddle()
    val turned = Math.toRadians(photoViewerContent.rotation.toDouble())
    val dx = -sin(turned).toFloat() * px
    val dy = cos(turned).toFloat() * px
    val down = SystemClock.uptimeMillis()
    val step = if (flick) 3L else 50L
    finger(MotionEvent.ACTION_DOWN, down, 0, x, y)
    for (i in 1..10) finger(MotionEvent.ACTION_MOVE, down, i * step, x + dx * i / 10, y + dy * i / 10)
    val last = 10 * step + if (flick) 0L else 200L
    if (!flick) finger(MotionEvent.ACTION_MOVE, down, last, x + dx, y + dy)
    finger(MotionEvent.ACTION_UP, down, last + if (flick) 3L else 100L, x + dx, y + dy)
    ScreenTestSupport.idle()
}
