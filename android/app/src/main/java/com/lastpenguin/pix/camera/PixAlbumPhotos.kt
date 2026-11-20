// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.camera

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Deletes one of Pix's photos from this phone, from the photo viewer (FR-1.6). */
fun interface PhotoDeleter {
    /** True once [uri] is gone from the phone, also when it already was; false when it could not be deleted. */
    suspend fun delete(uri: Uri): Boolean
}

/**
 * The photos in Pix's album on this phone, taken here or received in a session, for Camera's thumbnail, the newest,
 * and its viewer, all of them (FR-1.6; Design 2.6.5). They are read from MediaStore when the app starts and again
 * whenever MediaStore changes, so a photo deleted in the gallery leaves them. Without a storage permission an app sees
 * only the photos it saved, so after a reinstall the album's older photos are not Pix's any more, and there are none
 * until the next. [delete] needs no permission either, for the same reason: every photo it lists is Pix's own.
 * Owner: Camera/Overlay (#3).
 */
class PixAlbumPhotos(
    private val resolver: ContentResolver,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PhotoDeleter {
    /** Oldest first, the newest last; empty while the album has none, and until it was first read. */
    val photos: StateFlow<List<Uri>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        // A burst of changes, such as a photo's row and then its bytes, is read once.
        .conflate()
        .map { read() }
        .flowOn(dispatcher)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Deletes [uri] from MediaStore; [photos] lose it once MediaStore tells of the change. */
    override suspend fun delete(uri: Uri): Boolean = withContext(dispatcher) {
        try {
            // 0 rows: the photo was already gone, deleted in the gallery since.
            resolver.delete(uri, null, null)
            true
        } catch (error: Exception) {
            // Another app's photo, or a provider that refuses: it stays, and the viewer says so.
            Log.w(TAG, "The photo could not be deleted", error)
            false
        }
    }

    private fun read(): List<Uri> = try {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            // MediaStore keeps the path with a slash at the end. A photo still pending has no bytes to show yet;
            // MediaStore leaves it out by default, and this says so for any provider that does not.
            "${MediaStore.Images.Media.RELATIVE_PATH} IN (?, ?) AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf("$PIX_ALBUM_PATH/", PIX_ALBUM_PATH),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { rows ->
            val newestFirst = ArrayList<Uri>(rows.count)
            while (rows.moveToNext()) {
                newestFirst += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows.getLong(0))
            }
            newestFirst.reversed()
        }.orEmpty()
    } catch (error: Exception) {
        // A provider that refuses or is gone leaves the placeholder, not a crash; the next change reads again.
        Log.w(TAG, "The album could not be read", error)
        emptyList()
    }

    private companion object {
        const val TAG = "PixAlbum"
    }
}
