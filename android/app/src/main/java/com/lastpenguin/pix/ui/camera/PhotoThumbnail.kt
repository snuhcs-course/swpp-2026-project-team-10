// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Size
import android.view.View
import android.widget.ImageView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.Motion
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The size asked of MediaStore for a 40 dp thumbnail, enough at the densest screens. */
private const val THUMBNAIL_PX = 144

/** How small a new photo's thumbnail starts as it grows in (FR-1.6). */
private const val THUMBNAIL_START_SCALE = 0.6f

/** A saved photo's thumbnail, from MediaStore; off the main thread. */
internal fun Context.photoThumbnail(uri: Uri): Bitmap =
    contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null)

/**
 * A square of a saved photo for the viewer's grid, [px] on each side, cut from its middle (FR-1.6); off the main
 * thread. MediaStore is asked for twice the size, since it shrinks its own thumbnail by whole steps down to the size
 * asked for, and the asked square of a wide photo would come smaller than the square cut from it; the cut then keeps
 * only the pixels the square shows.
 */
internal fun Context.photoCell(uri: Uri, px: Int): Bitmap {
    val thumbnail = contentResolver.loadThumbnail(uri, Size(px * 2, px * 2), null)
    return ThumbnailUtils.extractThumbnail(thumbnail, px, px, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
}

/**
 * A saved photo at most as large as the screen's long side, turned as its EXIF says, for a viewer to show whole; off
 * the main thread. A 12 MP photo at full size would take about 48 MB.
 */
internal fun Context.photoToView(uri: Uri): Bitmap {
    val metrics = resources.displayMetrics
    val longest = maxOf(metrics.widthPixels, metrics.heightPixels)
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
        val scale = longest.toFloat() / maxOf(info.size.width, info.size.height)
        if (scale < 1f) {
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

/**
 * Shows each of [photos] in [thumbnail], read through [load] off the main thread (FR-1.6, FR-6.14): the placeholder
 * without one, or when it cannot be read; the full photo can still be opened then. A photo that comes while the
 * screen is open grows in; the one there as it opens just shows. Each of [edits], a photo changed in place under the
 * same URI (`PhonePhotos.edits`), reads the one shown again, which just shows. Runs until cancelled, as the screen's
 * collectors do.
 */
internal suspend fun showPhotoThumbnails(
    photos: Flow<Uri?>,
    thumbnail: ImageView,
    edits: Flow<Int> = flowOf(0),
    load: (Uri) -> Bitmap?,
): Unit = coroutineScope {
    var shown: Uri? = null

    suspend fun show(uri: Uri?, grow: Boolean) {
        val picture = uri?.let {
            withContext(Dispatchers.IO) {
                try {
                    load(it)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        }
        // An edit's read of a photo since replaced, finishing after the new one's, shows nothing.
        if (uri != shown) return
        if (picture == null) {
            thumbnail.setImageResource(R.drawable.gallery_placeholder)
        } else {
            thumbnail.setImageBitmap(picture)
            if (grow) thumbnail.growIn()
        }
    }

    // A photo edited in place: the one shown, read again, just shown.
    launch { edits.distinctUntilChanged().drop(1).collectLatest { show(shown, grow = false) } }
    var opening = true
    photos.distinctUntilChanged().collectLatest { uri ->
        val taken = !opening
        opening = false
        shown = uri
        show(uri, taken)
    }
}

/** A new photo's thumbnail grows in from [THUMBNAIL_START_SCALE] (FR-1.6); nothing while animations are off. */
private fun View.growIn() {
    if (!Motion.on()) return
    animate().cancel()
    scaleX = THUMBNAIL_START_SCALE
    scaleY = THUMBNAIL_START_SCALE
    alpha = 0f
    animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(Motion.FADE_MS).setInterpolator(Motion.STANDARD).start()
}
