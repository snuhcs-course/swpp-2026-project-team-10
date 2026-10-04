package com.lastpenguin.pix.session

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class YuvToNv21Test {

    private val width = 6
    private val height = 4
    private val luma = ByteArray(width * height) { (it + 1).toByte() }
    private val chromaU = byteArrayOf(101, 102, 103, 104, 105, 106)
    private val chromaV = byteArrayOf(-1, -2, -3, -4, -5, -6)

    /** NV21: Y, then V and U interleaved, row by row of the 3×2 chroma grid. */
    private val expected = luma + byteArrayOf(-1, 101, -2, 102, -3, 103, -4, 104, -5, 105, -6, 106)

    @Test
    fun `planar chroma with tight rows`() {
        val out = convert(
            y = YuvPlane(ByteBuffer.wrap(luma), rowStride = width, pixelStride = 1),
            u = YuvPlane(ByteBuffer.wrap(chromaU), rowStride = 3, pixelStride = 1),
            v = YuvPlane(ByteBuffer.wrap(chromaV), rowStride = 3, pixelStride = 1),
        )
        assertArrayEquals(expected, out)
    }

    @Test
    fun `planar chroma with padded rows`() {
        val out = convert(
            y = YuvPlane(pad(luma, width, 8), rowStride = 8, pixelStride = 1),
            u = YuvPlane(pad(chromaU, 3, 5), rowStride = 5, pixelStride = 1),
            v = YuvPlane(pad(chromaV, 3, 5), rowStride = 5, pixelStride = 1),
        )
        assertArrayEquals(expected, out)
    }

    @Test
    fun `semi-planar chroma in NV21 memory order`() {
        // V first in memory: V0 U0 V1 U1 ... The U plane is the same memory from one byte later.
        val interleaved = ByteArray(12) { if (it % 2 == 0) chromaV[it / 2] else chromaU[it / 2] }
        val out = convert(
            y = YuvPlane(ByteBuffer.wrap(luma), rowStride = width, pixelStride = 1),
            u = YuvPlane(view(interleaved, 1), rowStride = 6, pixelStride = 2),
            v = YuvPlane(view(interleaved, 0), rowStride = 6, pixelStride = 2),
        )
        assertArrayEquals(expected, out)
    }

    @Test
    fun `semi-planar chroma in NV12 memory order`() {
        // U first in memory: U0 V0 U1 V1 ... The result must still be NV21.
        val interleaved = ByteArray(12) { if (it % 2 == 0) chromaU[it / 2] else chromaV[it / 2] }
        val out = convert(
            y = YuvPlane(ByteBuffer.wrap(luma), rowStride = width, pixelStride = 1),
            u = YuvPlane(view(interleaved, 0), rowStride = 6, pixelStride = 2),
            v = YuvPlane(view(interleaved, 1), rowStride = 6, pixelStride = 2),
        )
        assertArrayEquals(expected, out)
    }

    private fun convert(y: YuvPlane, u: YuvPlane, v: YuvPlane): ByteArray {
        val out = ByteArray(YuvToNv21.sizeFor(width, height))
        YuvToNv21.convert(width, height, y, u, v, out)
        return out
    }

    /** Lays [data], which has rows of [rowWidth], out with [rowStride] bytes per row; the last row is not padded. */
    private fun pad(data: ByteArray, rowWidth: Int, rowStride: Int): ByteBuffer {
        val rows = data.size / rowWidth
        val padded = ByteArray((rows - 1) * rowStride + rowWidth)
        for (row in 0 until rows) System.arraycopy(data, row * rowWidth, padded, row * rowStride, rowWidth)
        return ByteBuffer.wrap(padded)
    }

    /** The plane buffers a camera gives: views of one array that start at different offsets. */
    private fun view(
        data: ByteArray,
        offset: Int,
    ): ByteBuffer = ByteBuffer.wrap(data, offset, data.size - offset).slice()
}
