// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemPhotoPageBinding
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.fadeTo
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The session's photos in a viewer over the screen, inside Pix (FR-1.6, FR-6.14; Design 2.6.5): over Camera on the
 * photographer's phone and over Subject view on the subject's, so the session and its video go on under it, where the
 * phone's gallery would put Pix in the background. One photo a page, read off the main thread through [load], opened at
 * the newest; a photo that comes while it is open is one more page, and the page on screen stays. It fades in and out,
 * and while it is open TalkBack reads only it. Its pages go once it is closed. It is built in [holder] when it first
 * opens: a screen that never opens it builds no pager, whose RecyclerView would cost every Camera screen.
 */
internal class PhotoViewer(
    private val holder: ViewGroup,
    scope: CoroutineScope,
    load: (Uri) -> Bitmap?,
    private val onBack: () -> Unit,
) {
    private val pages = PhotoPages(scope, load)

    /** The viewer once built; null until it first opens. */
    private var built: ViewPhotoViewerBinding? = null

    /** What TalkBack read of the screen's other views before the viewer opened; null while it is closed. */
    private var hidden: Map<View, Int>? = null

    private fun build(): ViewPhotoViewerBinding {
        built?.let { return it }
        val binding = ViewPhotoViewerBinding.inflate(LayoutInflater.from(holder.context), holder, true)
        built = binding
        val pager = binding.photoViewerPages
        pager.adapter = pages
        pager.offscreenPageLimit = 1
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = showCounter(binding)
            },
        )
        binding.photoViewerBack.setOnClickListener { onBack() }
        return binding
    }

    /** Shows [photos] while [open], under Back alone; each opening starts at the newest. */
    fun render(photos: List<Uri>, open: Boolean) {
        if (!open && built == null) return
        val binding = build()
        val viewer = binding.root
        hideScreen(open)
        val shown = viewer.isVisible && viewer.tag != CLOSING
        if (open) {
            pages.show(photos)
            if (!shown) binding.photoViewerPages.setCurrentItem(photos.lastIndex, false)
            showCounter(binding)
            if (shown) return
            viewer.tag = null
            if (!viewer.isVisible) viewer.alpha = 0f
            viewer.isVisible = true
            viewer.fadeTo(1f, Motion.FADE_MS)
        } else if (shown) {
            viewer.tag = CLOSING
            viewer.fadeTo(0f, Motion.FADE_MS) {
                viewer.isVisible = false
                viewer.tag = null
                pages.show(emptyList())
            }
        }
    }

    /** While the viewer is open, TalkBack skips the screen under it; once it closes, the screen is as it was. */
    private fun hideScreen(open: Boolean) {
        val screen = holder.parent as? ViewGroup ?: return
        if (open && hidden == null) {
            val others = screen.children.filter { it !== holder }.toList()
            hidden = others.associateWith { it.importantForAccessibility }
            others.forEach { it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        } else if (!open) {
            hidden?.forEach { (view, importance) -> view.importantForAccessibility = importance }
            hidden = null
        }
    }

    /** Which photo of how many, once there are two. */
    private fun showCounter(binding: ViewPhotoViewerBinding) {
        val counter = binding.photoViewerCounter
        val context = counter.context
        val count = pages.itemCount
        val position = binding.photoViewerPages.currentItem + 1
        counter.isInvisible = count <= 1
        counter.text = context.getString(R.string.photo_page_counter, position, count)
        counter.contentDescription = context.getString(R.string.photo_page_counter_description, position, count)
    }

    private companion object {
        /** The viewer's tag while it fades out. */
        const val CLOSING = "closing"
    }
}

/** The viewer's pages: each photo whole, or why it is not there, such as a photo deleted from the album since. */
private class PhotoPages(
    private val scope: CoroutineScope,
    private val load: (Uri) -> Bitmap?,
) : RecyclerView.Adapter<PhotoPages.Page>() {

    class Page(val binding: ItemPhotoPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var reading: Job? = null
    }

    private var photos: List<Uri> = emptyList()

    fun show(next: List<Uri>) {
        val before = photos
        if (next == before) return
        photos = next
        DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = before.size

                override fun getNewListSize() = next.size

                override fun areItemsTheSame(old: Int, new: Int) = before[old] == next[new]

                override fun areContentsTheSame(old: Int, new: Int) = true
            },
        ).dispatchUpdatesTo(this)
    }

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Page(ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(page: Page, position: Int) {
        val uri = photos[position]
        val image = page.binding.photoPageImage
        val message = page.binding.photoPageMessage
        page.reading?.cancel()
        image.setImageDrawable(null)
        // No title on screen; TalkBack says "Photo", and the counter which one of how many.
        image.contentDescription = image.context.getString(R.string.session_photo)
        message.isVisible = false
        page.reading = scope.launch {
            val picture = withContext(Dispatchers.IO) {
                try {
                    load(uri)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
            image.setImageBitmap(picture)
            message.isVisible = picture == null
        }
    }

    override fun onViewRecycled(page: Page) {
        page.reading?.cancel()
        page.binding.photoPageImage.setImageDrawable(null)
    }
}
