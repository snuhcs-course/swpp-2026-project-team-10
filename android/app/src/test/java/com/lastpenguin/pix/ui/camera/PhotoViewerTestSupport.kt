// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding

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

/** What the counter reads, or null while it is not shown. */
internal fun ViewPhotoViewerBinding.counterReads(): String? =
    photoViewerCounter.takeIf { it.isShown }?.text?.toString()
