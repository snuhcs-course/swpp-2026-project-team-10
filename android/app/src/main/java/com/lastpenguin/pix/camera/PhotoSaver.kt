package com.lastpenguin.pix.camera

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where photos are saved: a new JPEG in Pictures/Pix through MediaStore (FR-1.5). No storage permission is
 * needed on Android 10 and later for an app's own entries in the Pictures collection.
 * Owner: Camera/Overlay (#3).
 */
class PhotoSaver(private val context: Context) {

    fun outputOptions(): ImageCapture.OutputFileOptions {
        val name = "PIX_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
        }
        return ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .build()
    }

    private companion object {
        const val ALBUM = "Pix"
    }
}
