// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.doOnNextLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.PhoneAlbum
import com.lastpenguin.pix.camera.PhotoAccess
import com.lastpenguin.pix.databinding.ItemPhotoAlbumBinding
import com.lastpenguin.pix.databinding.ItemPhotoCellBinding
import com.lastpenguin.pix.databinding.ItemPhotoPageBinding
import com.lastpenguin.pix.databinding.ItemPhotoThumbBinding
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.fadeTo
import java.text.NumberFormat
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
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
 * stays. It fades in and out, and while it is open TalkBack reads only it. Its pages go once it is closed. It turns
 * with the phone ([turnTo], #207): held sideways, it is a landscape screen, the photo over all of it, so a landscape
 * photo fills the screen. The phone's photos open as a grid of them all first, as a gallery app's do: a tap on one
 * hands it to [onPick], and the screen shows its page, from which Back shows the grid again. Tabs in the header's
 * middle choose through [onTab] between that grid, at each opening, and the phone's albums, each a cover, its name,
 * and its count, whose tap opens its grid under its name through [onOpenAlbum]. While Pix may not read every photo, a
 * line above them says so, with [onAccess] to change it. The grid's squares and the albums' covers are read at their
 * own size through [cell]. Delete asks first only where [asksFirst] says so: Android asks itself before another app's
 * photo goes. It is built in [holder] when it first opens: a screen that
 * never opens it builds no pager, whose RecyclerView would cost every Camera screen.
 */
internal class PhotoViewer(
    private val holder: ViewGroup,
    scope: CoroutineScope,
    load: (Uri) -> Bitmap?,
    thumbnail: (Uri) -> Bitmap?,
    private val onBack: () -> Unit,
    private val onDelete: (Uri) -> Unit,
    private val onPick: (Uri) -> Unit = {},
    private val asksFirst: (Uri) -> Boolean = { true },
    private val onAccess: () -> Unit = {},
    /** A square of a photo, cut from its middle, the [Int] px on each side, for the grid and the albums' covers. */
    cell: (Uri, Int) -> Bitmap? = { uri, _ -> thumbnail(uri) },
    private val onTab: (GalleryTab) -> Unit = {},
    private val onOpenAlbum: (String) -> Unit = {},
    /** The thumbnail the viewer grows out of as it opens and shrinks back into as it closes; null: it only fades. */
    private val opensFrom: View? = null,
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
    private val stripReader = ThumbnailReader(scope, STRIP_CACHE_BYTES, thumbnail)
    private val strip = PhotoStrip(stripReader) { position ->
        built?.photoViewerPages?.setCurrentItem(position, false)
    }

    /**
     * The grid's square in px: a fifth of the screen's short side, which a row sideways keeps about too; read at that
     * size, a square shows its photo as sharp as the screen can.
     */
    private val cellPx = holder.resources.displayMetrics.let { screen ->
        (minOf(screen.widthPixels, screen.heightPixels) + GRID_COLUMNS - 1) / GRID_COLUMNS
    }
    private val cellReader = ThumbnailReader(scope, CELL_CACHE_BYTES) { uri -> cell(uri, cellPx) }
    private val cells = PhotoCells(cellReader) { uri -> onPick(uri) }

    /**
     * An album's cover in px: one of the Albums tab's two columns, less the list's and the card's pads (14 dp and
     * 6 dp a side, view_photo_viewer).
     */
    private val coverPx = holder.resources.displayMetrics.let { screen ->
        ((minOf(screen.widthPixels, screen.heightPixels) - ALBUMS_PAD_DP * screen.density) / ALBUM_COLUMNS).roundToInt()
    }
    private val coverReader = ThumbnailReader(scope, COVER_CACHE_BYTES) { uri -> cell(uri, coverPx) }
    private val albumCards = AlbumCards(coverReader) { id -> onOpenAlbum(id) }

    /** The gallery's tab and the album opened from it at the last render, so another starts at its top. */
    private var shownView: Pair<GalleryTab, String?>? = null

    /** The gallery shows, a grid or the albums, over the pages; false while they show, and while it is closed. */
    private var showsGallery = false

    /** The viewer once built; null until it first opens. */
    private var built: ViewPhotoViewerBinding? = null

    /** What TalkBack read of the screen's other views before the viewer opened; null while it is closed. */
    private var hidden: Map<View, Int>? = null

    /** The header and the strip show; a tap on the photo hides them, and each opening shows them. */
    private var chromeShown = true

    /** Delete's question, while it is open. */
    private var asking: Dialog? = null

    /** How the phone is held (`PhoneTurns`), which the viewer turns with once built. */
    private var turn = 0
    private var turning: TurningIcons? = null

    /** The space between the pages and the strip while the viewer stands upright. */
    private var pagesMargin = 0

    private fun build(): ViewPhotoViewerBinding {
        built?.let { return it }
        val binding = ViewPhotoViewerBinding.inflate(LayoutInflater.from(holder.context), holder, true)
        built = binding
        // Its own background, which a drag down fades.
        binding.root.background?.mutate()
        val pager = binding.photoViewerPages
        pager.adapter = pages
        pager.offscreenPageLimit = 1
        // A list that changed while the pages were hidden would otherwise play its moves as they show: a fast scroll
        // through other photos before the one tapped.
        (pager.getChildAt(0) as? RecyclerView)?.itemAnimator = null
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
        binding.photoViewerGrid.layoutManager = GridLayoutManager(holder.context, GRID_COLUMNS)
        binding.photoViewerGrid.adapter = cells
        // Its photos change only while a page covers it, where the list's moves would play unseen.
        binding.photoViewerGrid.itemAnimator = null
        binding.photoViewerAlbums.layoutManager = GridLayoutManager(holder.context, ALBUM_COLUMNS)
        binding.photoViewerAlbums.adapter = albumCards
        binding.photoViewerAlbums.itemAnimator = null
        pagesMargin = (pager.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        binding.root.onSideways = { sideways -> layOut(binding, sideways) }
        // The first turn puts it there at once, as it is held when it first opens.
        turning = TurningIcons(controls = listOf(binding.root), views = emptyList()).also { it.turnTo(turn) }
        binding.photoViewerBack.setOnClickListener { onBack() }
        binding.photoViewerShare.setOnClickListener { pages.photoAt(pager.currentItem)?.let(::share) }
        binding.photoViewerDelete.setOnClickListener {
            pages.photoAt(pager.currentItem)?.let { photo ->
                if (asksFirst(photo)) confirmDelete(photo) else onDelete(photo)
            }
        }
        binding.photoViewerAccessButton.setOnClickListener { onAccess() }
        binding.photoViewerTabPhotos.setOnClickListener { onTab(GalleryTab.PHOTOS) }
        binding.photoViewerTabAlbums.setOnClickListener { onTab(GalleryTab.ALBUMS) }
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

    /**
     * A photo shown was changed in place, as an app's edit saved over it under the same URI (`PhonePhotos.edits`): the
     * pictures kept by URI go, and what shows is read again.
     */
    fun forgetPictures() {
        for (reader in listOf(stripReader, cellReader, coverReader)) reader.forget()
        for (shown in listOf(strip, cells, albumCards, pages)) shown.notifyItemRangeChanged(0, shown.itemCount)
    }

    /** Turns the viewer upright for a phone held at [turn], as the screen's controls turn (FR-1.16). */
    fun turnTo(turn: Int) {
        this.turn = turn
        turning?.turnTo(turn)
    }

    /** Stops a turn under way, as the screen's views go. */
    fun stop() {
        turning?.stop()
    }

    /**
     * Shows [photos] while [open]: all of them in the grid while [grid], and otherwise their pages, from [at], or from
     * the newest when it is null or gone. Each opening starts at the grid's top or at the newest page, with the header
     * and the strip. A page opened from the grid starts with them too, and the grid shown again from a page has that
     * page's photo in sight. Outside a session [gallery] is its tabs: on the Albums tab with no album opened, the phone's
     * albums show instead of a grid, and another tab or album starts at its top. Over either, a line says which photos
     * show while [access] is not all of them.
     */
    fun render(
        photos: List<Uri>,
        open: Boolean,
        grid: Boolean = false,
        at: Uri? = null,
        access: PhotoAccess = PhotoAccess.ALL,
        gallery: Gallery? = null,
    ) {
        if (!open && built == null) return
        val binding = build()
        val viewer = binding.root
        hideScreen(open)
        val shown = viewer.isVisible && viewer.tag != CLOSING
        if (open) {
            val albumsShown = gallery != null && gallery.tab == GalleryTab.ALBUMS && gallery.album == null
            val layer = grid || albumsShown
            showAccess(binding, access)
            val view = gallery?.let { it.tab to it.album?.id }
            val before = shownView
            shownView = view
            // Within the gallery, the Albums tab and an album lie forward, to the right, of all photos and the albums.
            val slide = shown && layer && showsGallery && before != null && view != null && view != before
            val forward = view != null && before != null &&
                ((before.second == null && view.second != null) || view.first.ordinal > before.first.ordinal)
            showGallery(binding, gallery, layer, albumsShown, slide = slide, forward = forward)
            // The grid keeps what it shows while the albums show, or one sliding away would turn to other photos as it
            // goes; it gets its photos again before it comes back.
            if (!albumsShown) cells.show(photos)
            // The pages read their photos only once they show, so a grid opened alone reads none at full size.
            if (!layer) {
                val onScreen = pages.photoAt(binding.photoViewerPages.currentItem).takeIf { shown && !showsGallery }
                pages.show(photos)
                strip.show(photos)
                // A whole new list keeps the pager's place, not its photo.
                val now = onScreen?.let(photos::indexOf) ?: -1
                if (now >= 0 && now != binding.photoViewerPages.currentItem) openPage(binding, now)
            }
            val turned = shown && layer != showsGallery
            showsGallery = layer
            if (!shown || turned) {
                chromeShown = true
                // A drag that closed it, or that went back to the grid, left the background faded.
                followDrag(binding, 0f)
                when {
                    !layer -> openPage(binding, photos.indexOf(at).takeIf { it >= 0 } ?: photos.lastIndex)

                    turned -> pageInGrid(binding)

                    else -> {
                        binding.photoViewerGrid.scrollToPosition(0)
                        binding.photoViewerAlbums.scrollToPosition(0)
                    }
                }
            } else if (layer && view != before) {
                // Another tab or album starts at its top; Back from an album finds the albums where they were.
                if (!albumsShown) {
                    binding.photoViewerGrid.scrollToPosition(0)
                } else if (before?.second == null) {
                    binding.photoViewerAlbums.scrollToPosition(0)
                }
            }
            showLayer(binding, layer, fade = shown)
            showChrome(binding, fade = shown)
            if (!layer) select(binding, binding.photoViewerPages.currentItem)
            // A photo picked from the grid grows out of its square, and goes back into it.
            if (turned) zoomPage(binding, into = !layer, photo = if (layer) pageOnScreen(binding) else at)
            if (shown) return
            viewer.tag = null
            if (!viewer.isVisible) {
                viewer.alpha = 0f
                fromThumbnail(viewer)
            }
            viewer.isVisible = true
            viewer.animate().cancel()
            if (!Motion.on()) {
                viewer.alpha = 1f
                viewer.scaleX = 1f
                viewer.scaleY = 1f
                return
            }
            viewer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(openMs()).setInterpolator(Motion.STANDARD)
                .start()
        } else if (shown) {
            asking?.dismiss()
            viewer.tag = CLOSING
            val closed = {
                viewer.isVisible = false
                viewer.tag = null
                viewer.scaleX = 1f
                viewer.scaleY = 1f
                pages.show(emptyList())
                strip.show(emptyList())
                cells.show(emptyList())
                albumCards.show(emptyList())
            }
            viewer.animate().cancel()
            if (!Motion.on()) {
                viewer.alpha = 0f
                closed()
                return
            }
            fromThumbnail(viewer, keepScale = true)
            viewer.animate().alpha(0f).scaleX(GROWN_FROM).scaleY(GROWN_FROM).setDuration(Motion.FADE_MS)
                .setInterpolator(Motion.STANDARD).withEndAction(closed).start()
        }
    }

    /** As long as the viewer takes to open: longer while it grows out of its thumbnail than while it only fades. */
    private fun openMs() = if (opensFrom != null) Motion.MOVE_MS else Motion.FADE_MS

    /**
     * The viewer's scale grows from, and shrinks to, the middle of [opensFrom]: [viewer] starts there a little smaller
     * unless [keepScale]. Without a thumbnail, the viewer's own middle, and no change of size.
     */
    private fun fromThumbnail(viewer: View, keepScale: Boolean = false) {
        val from = opensFrom?.takeIf { Motion.on() && it.isAttachedToWindow } ?: return
        val at = IntArray(2).also(from::getLocationInWindow)
        val origin = IntArray(2).also(holder::getLocationInWindow)
        viewer.pivotX = at[0] + from.width / 2f - origin[0]
        viewer.pivotY = at[1] + from.height / 2f - origin[1]
        if (keepScale) return
        viewer.scaleX = GROWN_FROM
        viewer.scaleY = GROWN_FROM
    }

    /** The photo of the page on screen, which Back takes back into the grid. */
    private fun pageOnScreen(
        binding: ViewPhotoViewerBinding,
    ): Uri? = pages.photoAt(binding.photoViewerPages.currentItem)

    /**
     * The page grows out of [photo]'s square in the grid, [into] it, as the grid fades away under it, or shrinks back
     * into the square as the grid comes back under it; once both are laid out. Nothing while animations are off, or
     * when the square is out of sight: the grid's own fade is the change then.
     */
    private fun zoomPage(binding: ViewPhotoViewerBinding, into: Boolean, photo: Uri?) {
        val pager = binding.photoViewerPages
        settlePage(pager)
        if (!Motion.on() || photo == null) return
        val grid = binding.photoViewerGrid
        grid.doOnPreDraw {
            val position = cells.positionOf(photo) ?: return@doOnPreDraw
            val cell = grid.findViewHolderForAdapterPosition(position)?.itemView ?: return@doOnPreDraw
            val square = Rect(0, 0, cell.width, cell.height)
            binding.photoViewerContent.offsetDescendantRectToMyCoords(cell, square)
            if (pager.width == 0 || square.isEmpty) return@doOnPreDraw
            // One scale for both sides, the page's middle on the square's.
            val scale = square.width().toFloat() / pager.width
            val dx = square.exactCenterX() - (pager.left + pager.width * scale / 2f)
            val dy = square.exactCenterY() - (pager.top + pager.height * scale / 2f)
            pager.pivotX = 0f
            pager.pivotY = 0f
            pager.translationZ = ZOOM_Z_DP * pager.resources.displayMetrics.density
            val small = { view: View ->
                view.scaleX = scale
                view.scaleY = scale
                view.translationX = dx
                view.translationY = dy
            }
            if (into) {
                small(pager)
                pager.alpha = 0f
                pager.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).alpha(1f)
                    .setDuration(Motion.MOVE_MS).setInterpolator(Motion.STANDARD)
                    .withEndAction { settlePage(pager) }.start()
            } else {
                pager.animate().scaleX(scale).scaleY(scale).translationX(dx).translationY(dy).alpha(0f)
                    .setDuration(Motion.FADE_MS).setInterpolator(Motion.STANDARD)
                    .withEndAction { settlePage(pager) }.start()
            }
        }
    }

    /** The pages back in their place, at their size, under the grid: a zoom ended, or cut short. */
    private fun settlePage(pager: View) {
        pager.animate().cancel()
        pager.scaleX = 1f
        pager.scaleY = 1f
        pager.translationX = 0f
        pager.translationY = 0f
        pager.translationZ = 0f
        pager.alpha = 1f
    }

    /** The line over the grid while Pix may not read every photo: which photos show, and what changes it. */
    private fun showAccess(binding: ViewPhotoViewerBinding, access: PhotoAccess) {
        binding.photoViewerAccess.isVisible = access != PhotoAccess.ALL
        val (says, action) = when (access) {
            PhotoAccess.ALL -> return
            PhotoAccess.CHOSEN -> R.string.photos_access_chosen to R.string.photos_access_change
            PhotoAccess.OWN -> R.string.photos_access_own to R.string.photos_access_allow
        }
        binding.photoViewerAccessText.setText(says)
        binding.photoViewerAccessButton.setText(action)
    }

    /**
     * The gallery's header and list (FR-1.6): the tabs over either tab, the tab shown selected; an album's name and
     * count over its grid; the Albums tab's albums in place of the grid. While [slide], the header's change fades in,
     * and the grid and the albums trade places ([slideGallery]), [forward] to an album or to the Albums tab.
     */
    private fun showGallery(
        binding: ViewPhotoViewerBinding,
        gallery: Gallery?,
        layer: Boolean,
        albumsShown: Boolean,
        slide: Boolean,
        forward: Boolean,
    ) {
        val album = gallery?.album.takeIf { layer }
        val tabs = layer && gallery != null && album == null
        val header = listOf(binding.photoViewerTabs, binding.photoViewerAlbumName, binding.photoViewerAlbumCount)
        val headerBefore = header.map { it.isVisible }
        binding.photoViewerTabs.isVisible = tabs
        binding.photoViewerTabPhotos.isSelected = gallery?.tab == GalleryTab.PHOTOS
        binding.photoViewerTabAlbums.isSelected = gallery?.tab == GalleryTab.ALBUMS
        binding.photoViewerAlbumName.isVisible = album != null
        binding.photoViewerAlbumCount.isVisible = album != null
        if (album != null) {
            binding.photoViewerAlbumName.text = album.title(binding.root.context)
            binding.photoViewerAlbumCount.text = NumberFormat.getIntegerInstance().format(album.photos.size)
        }
        if (slide) {
            header.zip(headerBefore).filter { (view, was) -> view.isVisible && !was }.forEach { (view, _) ->
                view.alpha = 0f
                view.fadeTo(1f, Motion.FADE_MS)
            }
        }
        albumCards.show(gallery?.albums.orEmpty())
        val showing = if (binding.photoViewerAlbums.isVisible) binding.photoViewerAlbums else binding.photoViewerGrid
        val coming = if (albumsShown) binding.photoViewerAlbums else binding.photoViewerGrid
        if (slide && showing !== coming) {
            slideGallery(binding, coming, showing, forward)
        } else {
            settleList(binding.photoViewerGrid, !albumsShown)
            settleList(binding.photoViewerAlbums, albumsShown)
        }
    }

    /**
     * The grid and the albums trade places (FR-1.6): [coming] slides in from the side it lies on, from the right when
     * [forward], over [going], which slides the other way as it fades, as Pix's screens move; at once while animations
     * are off.
     */
    private fun slideGallery(binding: ViewPhotoViewerBinding, coming: View, going: View, forward: Boolean) {
        val shift = binding.photoViewerAlbum.width * SLIDE_SHARE * if (forward) 1 else -1
        if (!Motion.on() || shift == 0f) {
            settleList(going, false)
            settleList(coming, true)
            return
        }
        coming.animate().cancel()
        going.animate().cancel()
        coming.isVisible = true
        coming.alpha = 0f
        coming.translationX = shift
        coming.animate().alpha(1f).translationX(0f).setDuration(Motion.MOVE_MS).setInterpolator(Motion.STANDARD)
            .start()
        going.animate().alpha(0f).translationX(-shift).setDuration(Motion.MOVE_MS).setInterpolator(Motion.STANDARD)
            .withEndAction { settleList(going, false) }.start()
    }

    /** [list] shown or not, in its place, with no slide under way. */
    private fun settleList(list: View, shown: Boolean) {
        list.animate().cancel()
        list.alpha = 1f
        list.translationX = 0f
        list.isVisible = shown
    }

    /** The page of the photo at [position] on screen, at once. */
    private fun openPage(binding: ViewPhotoViewerBinding, position: Int) {
        val pager = binding.photoViewerPages
        pager.setCurrentItem(position, false)
        // A pager emptied at its last closing still names the page it showed then, and would not move to it again.
        (pager.getChildAt(0) as? RecyclerView)?.scrollToPosition(position)
    }

    /** Back from a page to the grid: the grid scrolls to that page's photo, if it is out of sight. */
    private fun pageInGrid(binding: ViewPhotoViewerBinding) {
        val photo = pages.photoAt(binding.photoViewerPages.currentItem) ?: return
        cells.positionOf(photo)?.let(binding.photoViewerGrid::scrollToPosition)
    }

    /**
     * The gallery over the pages, a grid or the albums, or the pages alone, with Share and Delete over a page. While [fade], the grid fades in over the page, which goes, whole again, once the grid covers it, or fades
     * out from over the page, which shows at once under it; otherwise, and while animations are off, it is at once.
     */
    private fun showLayer(binding: ViewPhotoViewerBinding, grid: Boolean, fade: Boolean) {
        binding.photoViewerShare.isVisible = !grid
        binding.photoViewerDelete.isVisible = !grid
        val pager = binding.photoViewerPages
        val cells = binding.photoViewerAlbum
        val showing = cells.isVisible && cells.tag != HIDING
        if (!grid) pager.isVisible = true
        if (showing == grid) return
        if (!fade) cells.animate().cancel()
        if (grid) {
            cells.tag = null
            // Once the grid covers them, the pages and the strip go, so a photo tapped next opens on a list of its own.
            val covered = {
                settlePage(pager)
                pager.isVisible = false
                pages.show(emptyList())
                strip.show(emptyList())
            }
            if (!fade) {
                cells.alpha = 1f
                cells.isVisible = true
                covered()
            } else {
                if (!cells.isVisible) cells.alpha = 0f
                cells.isVisible = true
                cells.fadeTo(1f, Motion.FADE_MS, covered)
            }
        } else if (!fade) {
            cells.tag = null
            cells.isVisible = false
            cells.alpha = 1f
        } else {
            cells.tag = HIDING
            cells.fadeTo(0f, Motion.FADE_MS) {
                cells.isVisible = false
                cells.tag = null
                cells.alpha = 1f
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

    /**
     * Upright, the photo lies between the header and the strip. Sideways, on a landscape screen, it takes all of it,
     * under them, as a gallery app's does; between them it would be smaller than on the upright screen.
     */
    private fun layOut(binding: ViewPhotoViewerBinding, sideways: Boolean) {
        val pager = binding.photoViewerPages
        // The pages' list lays out anew from the page on screen: left to itself, it would keep the pixels it was
        // scrolled by, and those of the new size fall on another page.
        (pager.getChildAt(0) as? RecyclerView)?.scrollToPosition(pager.currentItem)
        pager.updateLayoutParams<ConstraintLayout.LayoutParams> {
            val parent = ConstraintLayout.LayoutParams.PARENT_ID
            val unset = ConstraintLayout.LayoutParams.UNSET
            topToTop = if (sideways) parent else unset
            topToBottom = if (sideways) unset else R.id.photoViewerHeader
            bottomToBottom = if (sideways) parent else unset
            bottomToTop = if (sideways) unset else R.id.photoViewerStrip
            bottomMargin = if (sideways) 0 else pagesMargin
        }
        // Over the photo, the header's icons need a scrim behind them on a light photo.
        binding.photoViewerHeader.setBackgroundResource(if (sideways) R.drawable.bg_scrim_top else 0)
        // Sideways the grid's photos keep about their upright size, so a row holds more of them.
        val upright = minOf(holder.width, holder.height)
        fun columns(upright: Int) = if (sideways && upright > 0) {
            (upright * maxOf(holder.width, holder.height).toFloat() / minOf(holder.width, holder.height)).roundToInt()
        } else {
            upright
        }
        (binding.photoViewerGrid.layoutManager as? GridLayoutManager)?.spanCount = columns(GRID_COLUMNS)
        (binding.photoViewerAlbums.layoutManager as? GridLayoutManager)?.spanCount = columns(ALBUM_COLUMNS)
        // The strip's middle moves with its width.
        binding.photoViewerStrip.doOnNextLayout { select(binding, binding.photoViewerPages.currentItem) }
    }

    /**
     * The header while [chromeShown], and over a page the strip too once there are two photos; over the grid the
     * header always shows. [fade] fades a change.
     */
    private fun showChrome(binding: ViewPhotoViewerBinding, fade: Boolean) {
        place(binding.photoViewerHeader, chromeShown || showsGallery, fade)
        place(binding.photoViewerStrip, chromeShown && !showsGallery && pages.itemCount > 1, fade)
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
        // The screen's height as the user sees it, which is its width while the viewer is turned sideways.
        val way = binding.photoViewerContent.height / DRAG_FADE_PARTS
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

    /**
     * Every page but the one at [position] shows its whole photo again, as a gallery app does after a swipe; with
     * [RecyclerView.NO_POSITION], every page.
     */
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
            // Another app's photo may be a PNG or a HEIC, where Pix's are JPEGs.
            .setType(typeOf(uri))
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

    /** What MediaStore says [uri] is, or any image when it cannot say, as for a photo deleted since. */
    private fun typeOf(uri: Uri): String = try {
        holder.context.contentResolver.getType(uri)
    } catch (_: Exception) {
        null
    } ?: PHOTO_TYPE

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

        /** A photo whose type MediaStore does not say. */
        const val PHOTO_TYPE = "image/*"

        /** How large the viewer starts as it grows out of its thumbnail, and ends as it shrinks back into it. */
        const val GROWN_FROM = 0.9f

        /** How much of its width the grid and the albums slide as they trade places. */
        const val SLIDE_SHARE = 0.25f

        /** Over the grid while a page zooms out of its square, or back into it. */
        const val ZOOM_Z_DP = 3f

        /** The grid has five photos a row on the upright screen. */
        const val GRID_COLUMNS = 5

        /** The strip's 40 dp thumbnails, some 90 KB each: 8 MB keeps the latest 90 or so. */
        const val STRIP_CACHE_BYTES = 8 * 1024 * 1024

        /** The grid's squares, some 190 KB each on a 1080 px wide screen: 24 MB keeps about two screens of them. */
        const val CELL_CACHE_BYTES = 24 * 1024 * 1024

        /** The Albums tab has two albums a row on the upright screen. */
        const val ALBUM_COLUMNS = 2

        /** The Albums tab's pads across the screen: the list's 14 dp and each card's 6 dp, on both sides. */
        const val ALBUMS_PAD_DP = 52

        /** The albums' covers, some 850 KB each on a 1080 px wide screen: 12 MB keeps about two screens of them. */
        const val COVER_CACHE_BYTES = 12 * 1024 * 1024
    }
}

/** The viewer's tabs outside a session (FR-1.6): the [tab] shown, the phone's [albums], and the [album] opened from them. */
internal class Gallery(val tab: GalleryTab, val albums: List<PhoneAlbum>, val album: PhoneAlbum?)

/** The album's name on screen: the phone's own folders in the screens' language, any other as its folder is named. */
internal fun PhoneAlbum.title(context: Context): String = when (path?.trimEnd('/')) {
    "DCIM/Camera" -> context.getString(R.string.album_camera)
    "DCIM/Screenshots", "Pictures/Screenshots" -> context.getString(R.string.album_screenshots)
    "Download" -> context.getString(R.string.album_downloads)
    else -> name
}

/**
 * Tells this adapter how its list changed from [before] to [next], photo by photo through [same] (old place, new
 * place). DiffUtil compares the two on the main thread, which takes too long for thousands of photos changed at once,
 * such as the whole phone's after the permission: such a change, or one from or to none, is a new list.
 */
@SuppressLint("NotifyDataSetChanged")
private fun RecyclerView.Adapter<*>.showChange(before: List<Uri>, next: List<Uri>, same: (Int, Int) -> Boolean) {
    when {
        before.isEmpty() -> notifyItemRangeInserted(0, next.size)

        next.isEmpty() -> notifyItemRangeRemoved(0, before.size)

        abs(next.size - before.size) > WHOLE_CHANGE -> notifyDataSetChanged()

        else -> DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = before.size

                override fun getNewListSize() = next.size

                override fun areItemsTheSame(old: Int, new: Int) = same(old, new)

                override fun areContentsTheSame(old: Int, new: Int) = true
            },
            // Photos never move: each keeps its place in time.
            false,
        ).dispatchUpdatesTo(this)
    }
}

/** More photos come or go than this, and the list is shown as a new one ([showChange]). */
private const val WHOLE_CHANGE = 200

/**
 * The viewer's thumbnails, for its strip or its grid: each read off the main thread through [load], and the latest
 * kept by their bytes, so a thumbnail that comes back into sight shows at once.
 */
private class ThumbnailReader(
    private val scope: CoroutineScope,
    /** How many bytes of thumbnails it keeps. */
    bytes: Int,
    private val load: (Uri) -> Bitmap?,
) {
    private val cache = object : LruCache<Uri, Bitmap>(bytes) {
        override fun sizeOf(key: Uri, value: Bitmap) = value.allocationByteCount
    }

    /** Drops every thumbnail kept, so each is read again when it shows. */
    fun forget() = cache.evictAll()

    /** Shows [uri]'s thumbnail in [image]: at once when it is kept, or by the job returned, once it is read. */
    fun show(image: ImageView, uri: Uri): Job? {
        val kept = cache.get(uri)
        image.setImageBitmap(kept)
        if (kept != null) return null
        return scope.launch {
            val picture = withContext(READS) {
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

    private companion object {
        /**
         * A few reads at a time: a fling through the grid binds a cell many times a second, and each read still runs
         * once its cell has moved on, so the newest wait less behind them.
         */
        val READS = Dispatchers.IO.limitedParallelism(4)
    }
}

/**
 * The viewer's grid of Pix's album: a square of each photo, the newest first, read through [thumbnails]; a tap on one
 * picks its photo through [onPick]. TalkBack reads each as its page, which photo of how many.
 */
private class PhotoCells(
    private val thumbnails: ThumbnailReader,
    private val onPick: (Uri) -> Unit,
) : RecyclerView.Adapter<PhotoCells.Cell>() {

    class Cell(val binding: ItemPhotoCellBinding) : RecyclerView.ViewHolder(binding.root) {
        var reading: Job? = null
    }

    /** Oldest first, as the pages have them; the grid shows them the other way round. */
    private var photos: List<Uri> = emptyList()

    /** Where [uri] is in the grid, or null when it is not there. */
    fun positionOf(uri: Uri): Int? = photos.indexOf(uri).takeIf { it >= 0 }?.let { photos.lastIndex - it }

    fun show(next: List<Uri>) {
        val before = photos
        if (next == before) return
        photos = next
        showChange(before, next) { old, new -> before[before.lastIndex - old] == next[next.lastIndex - new] }
        // Each says which one of how many, so a new count changes them all.
        if (before.size != next.size && next.isNotEmpty()) notifyItemRangeChanged(0, next.size, COUNT)
    }

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Cell(ItemPhotoCellBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(cell: Cell, position: Int, payloads: List<Any>) {
        if (COUNT in payloads) describe(cell, position) else onBindViewHolder(cell, position)
    }

    override fun onBindViewHolder(cell: Cell, position: Int) {
        val uri = photos[photos.lastIndex - position]
        describe(cell, position)
        // The photo it was bound with, which a photo added before it does not change.
        cell.binding.root.setOnClickListener { onPick(uri) }
        cell.reading?.cancel()
        cell.reading = thumbnails.show(cell.binding.photoCellImage, uri)
    }

    /** "Photo 3 of 12", as its page reads, counted from the oldest. */
    private fun describe(cell: Cell, position: Int) {
        val item = cell.binding.root
        item.contentDescription =
            item.context.getString(R.string.photo_page_counter_description, photos.size - position, photos.size)
    }

    override fun onViewRecycled(cell: Cell) {
        cell.reading?.cancel()
        cell.binding.photoCellImage.setImageDrawable(null)
    }

    private companion object {
        /** A payload that changes only what TalkBack reads of the count. */
        const val COUNT = "count"
    }
}

/**
 * The viewer's strip: a thumbnail of each photo, read through [thumbnails], the photo on screen [selected], outlined
 * and bright, the others dimmed; a tap on one picks its photo through [onPick].
 */
private class PhotoStrip(
    private val thumbnails: ThumbnailReader,
    private val onPick: (Int) -> Unit,
) : RecyclerView.Adapter<PhotoStrip.Thumb>() {

    class Thumb(val binding: ItemPhotoThumbBinding) : RecyclerView.ViewHolder(binding.root) {
        var reading: Job? = null
    }

    private var photos: List<Uri> = emptyList()

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
        showChange(before, next) { old, new -> before[old] == next[new] }
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
        showState(thumb, position)
        thumb.reading?.cancel()
        thumb.reading = thumbnails.show(thumb.binding.photoThumbImage, photos[position])
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
        const val DIMMED = 0.55f

        /** A payload that changes only the selection and the count. */
        const val STATE = "state"
    }
}

/**
 * The Albums tab's list (FR-1.6): each album's newest photo as its cover, read through [covers], its name, and how many
 * photos it has; a tap opens it through [onOpen]. TalkBack reads its name and its count.
 */
private class AlbumCards(
    private val covers: ThumbnailReader,
    private val onOpen: (String) -> Unit,
) : RecyclerView.Adapter<AlbumCards.Card>() {

    class Card(val binding: ItemPhotoAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        var reading: Job? = null
    }

    private var albums: List<PhoneAlbum> = emptyList()

    fun show(next: List<PhoneAlbum>) {
        val before = albums
        if (next == before) return
        albums = next
        DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = before.size

                override fun getNewListSize() = next.size

                override fun areItemsTheSame(old: Int, new: Int) = before[old].id == next[new].id

                // What a card shows: its name, its count, and its newest photo.
                override fun areContentsTheSame(old: Int, new: Int) = before[old].name == next[new].name &&
                    before[old].photos.size == next[new].photos.size &&
                    before[old].photos.lastOrNull() == next[new].photos.lastOrNull()
            },
        ).dispatchUpdatesTo(this)
    }

    override fun getItemCount() = albums.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Card(ItemPhotoAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(card: Card, position: Int) {
        val album = albums[position]
        val binding = card.binding
        val context = binding.root.context
        val name = album.title(context)
        val count = album.photos.size
        binding.photoAlbumName.text = name
        binding.photoAlbumCount.text = NumberFormat.getIntegerInstance().format(count)
        binding.root.contentDescription =
            context.resources.getQuantityString(R.plurals.photo_album_description, count, name, count)
        binding.root.setOnClickListener { onOpen(album.id) }
        card.reading?.cancel()
        card.reading = album.photos.lastOrNull()?.let { covers.show(binding.photoAlbumCover, it) }
    }

    override fun onViewRecycled(card: Card) {
        card.reading?.cancel()
        card.binding.photoAlbumCover.setImageDrawable(null)
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
        showChange(before, next) { old, new -> before[old] == next[new] }
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
