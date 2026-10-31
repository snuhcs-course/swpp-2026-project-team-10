// AI-generated with ChatGPT Codex and Claude Code, 2026-10-31, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** The gallery's "Pix" album, for a photo that comes from the other phone (FR-6.14). */
fun interface PhotoAlbum {
    /** Saves the JPEG [file], the photo [photoId] taken at [takenAt]. [file] stays where it is. */
    fun save(file: File, photoId: String, takenAt: Long): Result<Uri>
}

/**
 * Where photos are saved: a new JPEG in Pictures/Pix through MediaStore (FR-1.5).
 * Owner: Camera/Overlay (#3).
 */
class PhotoSaver(private val context: Context) : PhotoAlbum {
    /** [mirrored]: saved flipped left to right, as the front camera's preview shows the scene (FR-1.14). */
    fun outputOptions(mirrored: Boolean = false): ImageCapture.OutputFileOptions {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName(Date(), UUID.randomUUID().toString()))
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_PATH)
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

    override fun save(file: File, photoId: String, takenAt: Long): Result<Uri> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName(Date(takenAt), photoId))
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        var row: Uri? = null
        return try {
            // Pending until it is whole, so that the gallery never shows half a photo.
            val inserted = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("The gallery has no row for the photo")
            row = inserted
            val output = resolver.openOutputStream(inserted) ?: throw IOException("The gallery gave no stream")
            output.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            resolver.update(inserted, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            Result.success(inserted)
        } catch (error: Exception) {
            // A full disk or a refused row is a message on the phone, not a crash.
            row?.let { resolver.delete(it, null, null) }
            Result.failure(error)
        }
    }

    private fun displayName(date: Date, id: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(date)
        return "Pix_${timestamp}_$id.jpg"
    }

    private companion object {
        const val MIME_TYPE = "image/jpeg"
        val ALBUM_PATH = "${Environment.DIRECTORY_PICTURES}/Pix"
    }
}
