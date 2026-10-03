package com.lastpenguin.pix.session

import java.nio.ByteBuffer

/** One plane of a YUV_420_888 image, as `ImageProxy.PlaneProxy` describes it. */
class YuvPlane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

/**
 * Packs the three planes of a YUV_420_888 image into one NV21 array: every Y byte, then V and U interleaved
 * (Design 2.6.4). WebRTC's `NV21Buffer` converts that to I420 natively. Handles planar (pixel stride 1) and
 * semi-planar (pixel stride 2) chroma, and padded rows, whichever the camera uses.
 */
object YuvToNv21 {

    fun sizeFor(width: Int, height: Int): Int = width * height + 2 * chroma(width) * chroma(height)

    fun convert(width: Int, height: Int, y: YuvPlane, u: YuvPlane, v: YuvPlane, out: ByteArray) {
        require(out.size >= sizeFor(width, height)) { "out holds ${out.size} bytes; ${width}x$height needs more" }
        copyLuma(y, width, height, out)
        val chromaWidth = chroma(width)
        val chromaHeight = chroma(height)
        val rowV = ByteArray((chromaWidth - 1) * v.pixelStride + 1)
        val rowU = ByteArray((chromaWidth - 1) * u.pixelStride + 1)
        val vBuffer = v.buffer.duplicate().also { it.clear() }
        val uBuffer = u.buffer.duplicate().also { it.clear() }
        var out1 = width * height
        for (row in 0 until chromaHeight) {
            read(vBuffer, row * v.rowStride, rowV, rowV.size)
            read(uBuffer, row * u.rowStride, rowU, rowU.size)
            for (col in 0 until chromaWidth) {
                out[out1++] = rowV[col * v.pixelStride]
                out[out1++] = rowU[col * u.pixelStride]
            }
        }
    }

    private fun copyLuma(y: YuvPlane, width: Int, height: Int, out: ByteArray) {
        val buffer = y.buffer.duplicate().also { it.clear() }
        when {
            y.pixelStride == 1 && y.rowStride == width -> read(buffer, 0, out, width * height)

            y.pixelStride == 1 -> for (row in 0 until height) {
                buffer.position(row * y.rowStride)
                buffer.get(out, row * width, width)
            }

            else -> {
                val rowY = ByteArray((width - 1) * y.pixelStride + 1)
                for (row in 0 until height) {
                    read(buffer, row * y.rowStride, rowY, rowY.size)
                    for (col in 0 until width) out[row * width + col] = rowY[col * y.pixelStride]
                }
            }
        }
    }

    private fun read(buffer: ByteBuffer, index: Int, into: ByteArray, length: Int) {
        buffer.position(index)
        buffer.get(into, 0, length)
    }

    private fun chroma(size: Int): Int = (size + 1) / 2
}
