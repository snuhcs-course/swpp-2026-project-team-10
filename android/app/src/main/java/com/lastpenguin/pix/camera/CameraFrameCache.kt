package com.lastpenguin.pix.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy

/**
 * One bounded, reusable copy of the analysis planes. No ImageProxy or camera-owned buffer is retained.
 * Bitmaps and independent plane copies are allocated only when grabFrame is requested.
 */
internal class CameraFrameCache {
    private var planes: List<YuvPlane> = emptyList()
    private var crop = Rect()
    private var rotation = 0

    @Synchronized
    fun update(image: ImageProxy) {
        planes = image.planes.mapIndexed { index, plane ->
            val source = plane.buffer.duplicate().apply { rewind() }
            val previous = planes.getOrNull(index)
            val bytes = previous?.bytes?.takeIf { it.size == source.remaining() } ?: ByteArray(source.remaining())
            source.get(bytes)
            if (previous != null && previous.bytes === bytes &&
                previous.rowStride == plane.rowStride && previous.pixelStride == plane.pixelStride
            ) {
                previous
            } else {
                YuvPlane(bytes, plane.rowStride, plane.pixelStride)
            }
        }
        crop.set(image.cropRect)
        rotation = image.imageInfo.rotationDegrees
    }

    @Synchronized
    fun hasFrame(): Boolean = planes.size == 3 && !crop.isEmpty

    @Synchronized
    fun snapshot(): CameraFrameSnapshot? {
        if (!hasFrame()) return null
        return CameraFrameSnapshot(
            planes.map { it.copy(bytes = it.bytes.copyOf()) },
            crop.left,
            crop.top,
            crop.width(),
            crop.height(),
            rotation,
        )
    }

    @Synchronized
    fun clear() {
        planes = emptyList()
        crop.setEmpty()
    }
}

internal data class CameraFrameSnapshot(
    val planes: List<YuvPlane>,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val rotation: Int,
) {
    fun toUprightBitmap(): Bitmap {
        val pixels = yuvToArgb(planes, left, top, width, height)
        val cropped = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        if (rotation == 0) return cropped
        try {
            val transform = Matrix().apply { postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(cropped, 0, 0, width, height, transform, true)
        } finally {
            cropped.recycle()
        }
    }
}
