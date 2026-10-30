// AI-generated with ChatGPT Codex and Claude Code, 2026-10-29, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.camera

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Where photos are saved: a new JPEG in Pictures/Pix through MediaStore (FR-1.5).
 * Owner: Camera/Overlay (#3).
 */
class PhotoSaver(private val context: Context) {
    /** [mirrored]: saved flipped left to right, as the front camera's preview shows the scene (FR-1.14). */
    fun outputOptions(mirrored: Boolean = false): ImageCapture.OutputFileOptions {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Pix_${timestamp}_${UUID.randomUUID()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Pix")
        }
        // CameraX manages the pending MediaStore row and removes it if writing fails.
        // API 29+ permits writing app-owned images without a storage permission.
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        )
            // CameraX mirrors the front camera's preview but not its photos; the EXIF flip makes them match.
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = mirrored })
            .build()
    }
}
