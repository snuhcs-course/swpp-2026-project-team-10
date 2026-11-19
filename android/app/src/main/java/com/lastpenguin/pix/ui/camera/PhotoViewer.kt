// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemPhotoPageBinding
import com.lastpenguin.pix.databinding.ItemPhotoThumbBinding
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.fadeTo
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A thumbnail's photos in a viewer over the screen, inside Pix, as a gallery app shows them (FR-1.6, FR-6.14; Design
 * 2.6.5): over Camera, a session's or Pix's album, and over Subject view, the session's, so a session and its video go
 * on under it, where the phone's gallery would show one photo and put Pix in the background. One photo a page, read off
 * the main thread through [load] and opened at the newest; a pinch or a double tap zooms it ([ZoomablePhotoView]). A
 * strip of their thumbnails, read through [thumbnail], is under them: the one on screen selected, and a tap on one
 * shows its photo. A tap on the photo hides the header and the strip, and another shows them; a drag down closes the
 * viewer as Back does ([onBack]). Share sends the photo on screen through the phone's share sheet, and Delete asks
 * first, then hands it to [onDelete]. A photo that comes while it is open is one more page, and the page on screen
 * stays. It fades in and out, and while it is open TalkBack reads only it. Its pages go once it is closed. It is built
 * in [holder] when it first opens: a screen that never opens it builds no pager, whose RecyclerView would cost every
 * Camera screen.
 */
internal class PhotoViewer(
    private val holder: ViewGroup,
    scope: CoroutineScope,
    load: (Uri) -> Bitmap?,
    thumbnail: (Uri) -> Bitmap?,
    private val onBack: () -> Unit,
    private val onDelete: (Uri) -> Unit,
) {
    private val pages = PhotoPages(
        scope,
        load,
        object : ZoomablePhotoView.Listener {
            override fun onTap() {
                val binding = built ?: return
                chromeShown = !chromeShown
                showChrome(binding, fade = true)
            }

            override fun onDrag(offset: Float) {
                built?.let { followDrag(it, offset) }
            }

            override fun onDismiss() = onBack()
        },
    )
    private val strip =
        PhotoStrip(scope, thumbnail) { position -> built?.photoViewerPages?.setCurrentItem(position, false) }

    /** The viewer once built; null until it first opens. */
    private var built: ViewPhotoViewerBinding? = null

    /** What TalkBack read of the screen's other views before the viewer opened; null while it is closed. */
    private var hidden: Map<View, Int>? = null

    /** The header and the strip show; a tap on the photo hides them, and each opening shows them. */
    private var chromeShown = true

    /** Delete's question, while it is open. */
    private var asking: Dialog? = null

    private fun build(): ViewPhotoViewerBinding {
        built?.let { return it }
        val binding = ViewPhotoViewerBinding.inflate(LayoutInflater.from(holder.context), holder, true)
        built = binding
        // Its own background, which a drag down fades.
        binding.root.background?.mutate()
        val pager = binding.photoViewerPages
        pager.adapter = pages
        pager.offscreenPageLimit = 1
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    select(binding, position)
                    wholeAgain(binding, position)
                }
            },
        )
        binding.photoViewerStrip.adapter = strip
        // A selected thumbnail changes at once, without the list's fade.
        binding.photoViewerStrip.itemAnimator = null
        binding.photoViewerBack.setOnClickListener { onBack() }
        binding.photoViewerShare.setOnClickListener { pages.photoAt(pager.currentItem)?.let(::share) }
        binding.photoViewerDelete.setOnClickListener { pages.photoAt(pager.currentItem)?.let(::confirmDelete) }
        // A question left open as the screen goes would leak its window.
        holder.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    asking?.dismiss()
                }
            },
        )
        return binding
    }

    /** Shows [photos] while [open]; each opening starts at the newest, with the header and the strip. */
    fun render(photos: List<Uri>, open: Boolean) {
        if (!open && built == null) return
        val binding = build()
        val viewer = binding.root
        hideScreen(open)
        val shown = viewer.isVisible && viewer.tag != CLOSING
        if (open) {
            pages.show(photos)
            strip.show(photos)
            if (!shown) {
                chromeShown = true
                // A drag that closed it left the background faded.
                followDrag(binding, 0f)
                binding.photoViewerPages.setCurrentItem(photos.lastIndex, false)
            }
            showChrome(binding, fade = shown)
            select(binding, binding.photoViewerPages.currentItem)
            if (shown) return
            viewer.tag = null
            if (!viewer.isVisible) viewer.alpha = 0f
            viewer.isVisible = true
            viewer.fadeTo(1f, Motion.FADE_MS)
        } else if (shown) {
            asking?.dismiss()
            viewer.tag = CLOSING
            viewer.fadeTo(0f, Motion.FADE_MS) {
                viewer.isVisible = false
                viewer.tag = null
                pages.show(emptyList())
                strip.show(emptyList())
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

    /** The header while [chromeShown], and the strip too once there are two photos; [fade] fades a change. */
    private fun showChrome(binding: ViewPhotoViewerBinding, fade: Boolean) {
        place(binding.photoViewerHeader, chromeShown, fade)
        place(binding.photoViewerStrip, chromeShown && pages.itemCount > 1, fade)
    }

    /**
     * Shows or hides [view], fading it while [fade]; a view already on its way there is left alone, so a render that
     * changes nothing keeps a fade going. Hidden, it keeps its place and takes no taps.
     */
    private fun place(view: View, shown: Boolean, fade: Boolean) {
        val showing = view.isVisible && view.tag != HIDING
        if (showing == shown) return
        if (!fade || !Motion.on()) {
            view.animate().cancel()
            view.tag = null
            view.alpha = 1f
            view.isInvisible = !shown
        } else if (shown) {
            view.tag = null
            if (!view.isVisible) {
                view.alpha = 0f
                view.isVisible = true
            }
            view.fadeTo(1f, Motion.HIDE_MS)
        } else {
            view.tag = HIDING
            view.fadeTo(0f, Motion.HIDE_MS) {
                view.isInvisible = true
                view.tag = null
                view.alpha = 1f
            }
        }
    }

    /**
     * The photo is [offset] px down on its way to closing: the background fades with it, and the header and the strip
     * three times as fast, nearly gone at the line that closes, so they never lie over the screen showing through.
     */
    private fun followDrag(binding: ViewPhotoViewerBinding, offset: Float) {
        val way = binding.root.height / DRAG_FADE_PARTS
        val gone = if (way > 0f) (offset / way).coerceIn(0f, 1f) else 0f
        binding.root.background?.alpha = ((1f - gone) * OPAQUE).roundToInt()
        val chrome = 1f - (gone * CHROME_FADE_SPEED).coerceAtMost(1f)
        binding.photoViewerHeader.alpha = chrome
        binding.photoViewerStrip.alpha = chrome
    }

    /** The strip's thumbnail of the photo at [position] selected, and in the middle of the strip when it scrolls. */
    private fun select(binding: ViewPhotoViewerBinding, position: Int) {
        strip.selected = position
        val list = binding.photoViewerStrip
        val layout = list.layoutManager as? LinearLayoutManager ?: return
        val item = list.resources.getDimensionPixelSize(R.dimen.photo_thumb_touch)
        layout.scrollToPositionWithOffset(position, (list.width - list.paddingStart - list.paddingEnd - item) / 2)
    }

    /** Every page but the one at [position] shows its whole photo again, as a gallery app does after a swipe. */
    private fun wholeAgain(binding: ViewPhotoViewerBinding, position: Int) {
        val list = binding.photoViewerPages.getChildAt(0) as? RecyclerView ?: return
        for (page in list.children) {
            if (list.getChildAdapterPosition(page) == position) continue
            page.findViewById<ZoomablePhotoView>(R.id.photoPageImage)?.reset()
        }
    }

    /** The photo through the phone's share sheet, which may read it; Pix stays as it is under the sheet. */
    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(PHOTO_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The sheet's preview of the photo; the chooser passes it and the leave to read it on.
        send.clipData = ClipData.newRawUri(null, uri)
        try {
            holder.context.startActivity(Intent.createChooser(send, null))
        } catch (_: ActivityNotFoundException) {
            // A phone without a share sheet: there is nowhere to send it.
        }
    }

    /** Delete asks first, as Pix does for a saved guide (FR-3.8); the photo goes only with its Delete. */
    private fun confirmDelete(uri: Uri) {
        asking?.dismiss()
        val question = MaterialAlertDialogBuilder(holder.context)
            .setTitle(R.string.delete_photo_title)
            .setMessage(R.string.delete_photo_message)
            .setPositiveButton(R.string.delete) { _, _ -> onDelete(uri) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        question.setOnDismissListener { if (asking === question) asking = null }
        asking = question
        question.show()
    }

    private companion object {
        /** The viewer's tag while it fades out. */
        const val CLOSING = "closing"

        /** The header's or the strip's tag while it fades out. */
        const val HIDING = "hiding"

        /** A drag down a third of the viewer fades its background out. */
        const val DRAG_FADE_PARTS = 3f

        /** How much faster than the background the header and the strip fade in a drag down. */
        const val CHROME_FADE_SPEED = 3f
        const val OPAQUE = 255

        /** Every photo of Pix's album is a JPEG, as PhotoSaver saves it and the subject's album keeps it. */
        const val PHOTO_TYPE = "image/jpeg"
    }
}

/**
 * The viewer's strip: a thumbnail of each photo, read off the main thread and kept for the latest few, the photo on
 * screen [selected], outlined and bright, the others dimmed; a tap on one picks its photo through [onPick].
 */
private class PhotoStrip(
    private val scope: CoroutineScope,
    private val load: (Uri) -> Bitmap?,
    private val onPick: (Int) -> Unit,
) : RecyclerView.Adapter<PhotoStrip.Thumb>() {

    class Thumb(val binding: ItemPhotoThumbBinding) : RecyclerView.ViewHolder(binding.root) {
        var reading: Job? = null
    }

    private var photos: List<Uri> = emptyList()
    private val cache = LruCache<Uri, Bitmap>(CACHED)

    var selected: Int = RecyclerView.NO_POSITION
        set(value) {
            if (field == value) return
            val before = field
            field = value
            if (before in photos.indices) notifyItemChanged(before, STATE)
            if (value in photos.indices) notifyItemChanged(value, STATE)
        }

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
        // Each says which one of how many, so a new count changes them all.
        if (before.size != next.size && next.isNotEmpty()) notifyItemRangeChanged(0, next.size, STATE)
    }

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Thumb(ItemPhotoThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(thumb: Thumb, position: Int, payloads: List<Any>) {
        if (STATE in payloads) showState(thumb, position) else onBindViewHolder(thumb, position)
    }

    override fun onBindViewHolder(thumb: Thumb, position: Int) {
        val uri = photos[position]
        val image = thumb.binding.photoThumbImage
        showState(thumb, position)
        thumb.reading?.cancel()
        val kept = cache.get(uri)
        image.setImageBitmap(kept)
        if (kept != null) return
        thumb.reading = scope.launch {
            val picture = withContext(Dispatchers.IO) {
                try {
                    load(uri)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
            if (picture != null) cache.put(uri, picture)
            image.setImageBitmap(picture)
        }
    }

    private fun showState(thumb: Thumb, position: Int) {
        val item = thumb.binding.root
        val chosen = position == selected
        item.isSelected = chosen
        thumb.binding.photoThumbImage.alpha = if (chosen) 1f else DIMMED
        item.contentDescription =
            item.context.getString(R.string.photo_page_counter_description, position + 1, photos.size)
        // Bound again with every change of the count, so the place it was bound with stays right.
        item.setOnClickListener { onPick(position) }
    }

    override fun onViewRecycled(thumb: Thumb) {
        thumb.reading?.cancel()
        thumb.binding.photoThumbImage.setImageDrawable(null)
    }

    private companion object {
        /** A thumbnail is about 80 KB at 144 px: the latest 60 are under 5 MB. */
        const val CACHED = 60
        const val DIMMED = 0.55f

        /** A payload that changes only the selection and the count. */
        const val STATE = "state"
    }
}

/**
 * The viewer's pages: each photo, whole until zoomed, with its [gestures]; or why it is not there, such as a photo
 * deleted from the album since. TalkBack reads which photo of how many.
 */
private class PhotoPages(
    private val scope: CoroutineScope,
    private val load: (Uri) -> Bitmap?,
    private val gestures: ZoomablePhotoView.Listener,
) : RecyclerView.Adapter<PhotoPages.Page>() {

    class Page(val binding: ItemPhotoPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var reading: Job? = null
    }

    private var photos: List<Uri> = emptyList()

    /** The photo at [position], or null when there is none there. */
    fun photoAt(position: Int): Uri? = photos.getOrNull(position)

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
        // Each says which one of how many, so a new count changes them all.
        if (before.size != next.size && next.isNotEmpty()) notifyItemRangeChanged(0, next.size, COUNT)
    }

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Page(ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(page: Page, position: Int, payloads: List<Any>) {
        if (COUNT in payloads) describe(page.binding.photoPageImage, position) else onBindViewHolder(page, position)
    }

    override fun onBindViewHolder(page: Page, position: Int) {
        val uri = photos[position]
        val image = page.binding.photoPageImage
        val message = page.binding.photoPageMessage
        page.reading?.cancel()
        image.setImageDrawable(null)
        image.reset()
        image.listener = gestures
        describe(image, position)
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

    /** No title or counter on screen: TalkBack says "Photo", and which one of how many once there are two. */
    private fun describe(image: View, position: Int) {
        val context = image.context
        image.contentDescription = if (photos.size <= 1) {
            context.getString(R.string.session_photo)
        } else {
            context.getString(R.string.photo_page_counter_description, position + 1, photos.size)
        }
    }

    override fun onViewRecycled(page: Page) {
        page.reading?.cancel()
        page.binding.photoPageImage.setImageDrawable(null)
    }

    private companion object {
        /** A payload that changes only what TalkBack reads of the count. */
        const val COUNT = "count"
    }
}
