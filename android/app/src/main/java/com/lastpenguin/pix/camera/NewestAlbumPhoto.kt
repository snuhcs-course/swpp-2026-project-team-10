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

/**
 * The newest photo in Pix's album on this phone, taken here or received in a session, for Camera's thumbnail (FR-1.6;
 * Design 2.6.5). It is read from MediaStore when the app starts and again whenever MediaStore changes, so a photo
 * deleted in the gallery leaves the one before. Without a storage permission an app sees only the photos it saved, so
 * after a reinstall the album's older photos are not Pix's any more, and there is none until the next.
 * Owner: Camera/Overlay (#3).
 */
class NewestAlbumPhoto(
    private val resolver: ContentResolver,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** null while the album has none, and until it was first read. */
    val newest: StateFlow<Uri?> = callbackFlow {
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
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun read(): Uri? = try {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            // MediaStore keeps the path with a slash at the end.
            "${MediaStore.Images.Media.RELATIVE_PATH} IN (?, ?)",
            arrayOf("$PIX_ALBUM_PATH/", PIX_ALBUM_PATH),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { rows ->
            if (!rows.moveToFirst()) return@use null
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rows.getLong(0))
        }
    } catch (error: Exception) {
        // A provider that refuses or is gone leaves the placeholder, not a crash; the next change reads again.
        Log.w(TAG, "The album could not be read", error)
        null
    }

    private companion object {
        const val TAG = "PixAlbum"
    }
}
