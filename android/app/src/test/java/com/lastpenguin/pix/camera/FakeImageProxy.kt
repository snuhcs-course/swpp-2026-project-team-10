// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import java.nio.ByteBuffer

/**
 * A YUV_420_888 frame for the JVM, as CameraX's analysis hands it over: three planes with their strides, a crop,
 * and a rotation. [closed] records that the frame was given back, which every sink must do.
 */
class FakeImageProxy(
    private val width: Int,
    private val height: Int,
    yBytes: ByteArray,
    uBytes: ByteArray,
    vBytes: ByteArray,
    yRowStride: Int = width,
    chromaRowStride: Int = width / 2,
    chromaPixelStride: Int = 1,
    private var crop: Rect = Rect(0, 0, width, height),
    private val rotation: Int = 0,
    private val format: Int = ImageFormat.YUV_420_888,
) : ImageProxy {
    var closed = false
        private set

    private val planes = arrayOf(
        Plane(yBytes, yRowStride, 1),
        Plane(uBytes, chromaRowStride, chromaPixelStride),
        Plane(vBytes, chromaRowStride, chromaPixelStride),
    )

    override fun close() {
        closed = true
    }

    override fun getCropRect(): Rect = Rect(crop)

    override fun setCropRect(rect: Rect?) {
        crop = rect?.let(::Rect) ?: Rect(0, 0, width, height)
    }

    override fun getFormat(): Int = format

    override fun getHeight(): Int = height

    override fun getWidth(): Int = width

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(*planes)

    override fun getImageInfo(): ImageInfo = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()

        override fun getTimestamp(): Long = 0L

        override fun getRotationDegrees(): Int = rotation

        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }

    override fun getImage(): Image? = null

    private class Plane(private val bytes: ByteArray, private val rowStride: Int, private val pixelStride: Int) :
        ImageProxy.PlaneProxy {
        override fun getRowStride(): Int = rowStride

        override fun getPixelStride(): Int = pixelStride

        override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
    }

    companion object {
        /** A [width]×[height] frame of one video-range luma [luma] with neutral chroma; chroma planes packed. */
        fun flat(width: Int, height: Int, luma: Int, crop: Rect? = null, rotation: Int = 0): FakeImageProxy {
            val chroma = ByteArray((width / 2) * (height / 2)) { 128.toByte() }
            return FakeImageProxy(
                width,
                height,
                ByteArray(width * height) { luma.toByte() },
                chroma,
                chroma.copyOf(),
                crop = crop ?: Rect(0, 0, width, height),
                rotation = rotation,
            )
        }
    }
}
