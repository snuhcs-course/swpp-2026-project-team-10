// AI-generated with ChatGPT Codex, 2026-10-04, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class YuvFrameTest {
    @Test
    fun neutralChromaConvertsVideoRangeBlackAndWhite() {
        val planes = listOf(
            YuvPlane(byteArrayOf(16, 235.toByte(), 16, 235.toByte()), 2, 1),
            YuvPlane(byteArrayOf(128.toByte()), 1, 1),
            YuvPlane(byteArrayOf(128.toByte()), 1, 1),
        )
        assertArrayEquals(
            intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xff000000.toInt(), 0xffffffff.toInt()),
            yuvToArgb(planes, 0, 0, 2, 2),
        )
    }

    @Test
    fun respectsRowPaddingAndInterleavedChromaDuringCropping() {
        // 4x4 image, with six bytes per Y row and stride-2 chroma. The bottom right block is red.
        val y = ByteArray(24) { 16 }
        y[2 * 6 + 2] = 81
        y[2 * 6 + 3] = 81
        y[3 * 6 + 2] = 81
        y[3 * 6 + 3] = 81
        val u = byteArrayOf(128.toByte(), 0, 128.toByte(), 0, 0, 0, 128.toByte(), 0, 90, 0)
        val v = byteArrayOf(128.toByte(), 0, 128.toByte(), 0, 0, 0, 128.toByte(), 0, 240.toByte(), 0)
        val planes = listOf(YuvPlane(y, 6, 1), YuvPlane(u, 6, 2), YuvPlane(v, 6, 2))
        val red = 0xffff0000.toInt()
        assertArrayEquals(intArrayOf(red, red, red, red), yuvToArgb(planes, 2, 2, 2, 2))
        assertEquals(0xff000000.toInt(), yuvToArgb(planes, 0, 0, 1, 1).single())
    }

    @Test
    fun oddCropOriginsUseTheCorrespondingChromaSample() {
        val y = YuvPlane(ByteArray(16) { 81 }, 4, 1)
        val u = YuvPlane(byteArrayOf(90, 128.toByte(), 128.toByte(), 128.toByte()), 2, 1)
        val v = YuvPlane(byteArrayOf(240.toByte(), 128.toByte(), 128.toByte(), 128.toByte()), 2, 1)
        val result = yuvToArgb(listOf(y, u, v), 1, 1, 2, 1)
        assertEquals(0xffff0000.toInt(), result[0])
        assertEquals(0xff4c4c4c.toInt(), result[1])
    }
}
