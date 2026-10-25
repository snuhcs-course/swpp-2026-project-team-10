// AI-generated with Claude Code, 2026-10-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.lastpenguin.pix.guide.fitLongSide
import java.io.ByteArrayOutputStream

/** The server rejects a scene photo whose long side is above this (Design 2.5.3). */
internal const val MAX_SCENE_LONG_SIDE = 1024

/** A candidate goes to `POST /pose-tips` at this size: enough for the tip model, and half the upload. */
internal const val TIP_CANDIDATE_LONG_SIDE = 768

private const val SCENE_JPEG_QUALITY = 85

/**
 * The bitmap work of pose generation, behind an interface so that [RemotePoseGenerator] runs in JVM tests.
 * Owner: Server/AI/Sync (#7).
 */
interface PoseImageCodec {
    /**
     * The scene photo as the server requires it (Design 2.6.3, step 1): long side at most 1024 px, JPEG quality 85,
     * no metadata. The server checks the photo and does not repair it, so this is the only place it is prepared.
     */
    fun encodeScene(scene: Bitmap): ByteArray

    /** The candidate the user took, as `POST /pose-tips` gets it: long side at most 768 px, JPEG as for the scene. */
    fun encodeTipCandidate(candidate: Bitmap): ByteArray

    /** A candidate from the server, or null when the bytes are not an image. */
    fun decodeCandidate(image: ByteArray): Bitmap?
}

object AndroidPoseImageCodec : PoseImageCodec {

    override fun encodeScene(scene: Bitmap): ByteArray = encode(scene, MAX_SCENE_LONG_SIDE)

    override fun encodeTipCandidate(candidate: Bitmap): ByteArray = encode(candidate, TIP_CANDIDATE_LONG_SIDE)

    private fun encode(image: Bitmap, longSide: Int): ByteArray {
        val size = fitLongSide(image.width, image.height, longSide)
        val fits = size.width == image.width && size.height == image.height
        val scaled = if (fits) image else image.scale(size.width, size.height)
        val jpeg = ByteArrayOutputStream()
        // Bitmap.compress writes a plain JPEG without EXIF, so the location never leaves the phone (NFR-13).
        scaled.compress(Bitmap.CompressFormat.JPEG, SCENE_JPEG_QUALITY, jpeg)
        if (scaled !== image) scaled.recycle()
        return jpeg.toByteArray()
    }

    override fun decodeCandidate(image: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(image, 0, image.size)
}
