package com.lastpenguin.pix.camera

import android.content.Context
import androidx.camera.core.ImageCapture

/**
 * Where photos are saved: a new JPEG in Pictures/Pix through MediaStore (FR-1.5).
 * Owner: Camera/Overlay (#3).
 */
class PhotoSaver(private val context: Context) {
    fun outputOptions(): ImageCapture.OutputFileOptions =
        TODO("#3: MediaStore entry in Pictures/Pix")
}
