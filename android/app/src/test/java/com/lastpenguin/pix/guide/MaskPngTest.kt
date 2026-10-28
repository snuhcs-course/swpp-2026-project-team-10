// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import java.util.zip.CRC32
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * MaskPng and MaskPixels.fitHeight on the plain JVM (Testing Documentation 2.1.6).
 * Property: decode(encode(m)) == m for random masks of many shapes, 1×1 and a single row or column included, flat,
 * noisy, and with soft edges, so every row filter is used. Format: an 8-bit grayscale PNG with a valid signature and
 * CRCs. Refused (fault injection): another signature, a damaged CRC, a truncated file, another color type or depth,
 * a size claim above MAX_PIXELS, and image data of the wrong length. fitHeight: below, at, and above the limit,
 * the aspect kept, and the mean of the covered pixels. Limits: a mask at the width and height limits is read, one
 * pixel wider or taller is refused.
 */
class MaskPngTest {

    private fun random(width: Int, height: Int, seed: Int, levels: Int = 256): MaskPixels {
        val random = Random(seed)
        return MaskPixels(width, height, IntArray(width * height) { random.nextInt(levels) * (255 / (levels - 1)) })
    }

    private fun softEllipse(width: Int, height: Int): MaskPixels = MaskPixels(
        width,
        height,
        IntArray(width * height) { k ->
            val dx = (k % width - width / 2f) / (width * 0.3f)
            val dy = (k / width - height / 2f) / (height * 0.4f)
            ((1.5f - (dx * dx + dy * dy)) * 255f).toInt().coerceIn(0, 255)
        },
    )

    @Test
    fun `every mask comes back exactly`() {
        val sizes = listOf(1 to 1, 1 to 50, 50 to 1, 7 to 13, 64 to 64, 300 to 720)
        for ((index, size) in sizes.withIndex()) {
            val (width, height) = size
            for (mask in listOf(
                random(width, height, index),
                random(width, height, index, levels = 2),
                softEllipse(width, height),
            )) {
                assertEquals("${width}x$height", mask, MaskPng.decode(MaskPng.encode(mask)))
            }
        }
        val flat = MaskPixels(30, 40, IntArray(1200) { 255 })
        assertEquals(flat, MaskPng.decode(MaskPng.encode(flat)))
    }

    @Test
    fun `the file is an 8-bit grayscale PNG`() {
        val bytes = MaskPng.encode(softEllipse(12, 34))

        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.copyOf(8))
        assertEquals("IHDR", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals(12, int(bytes, 16))
        assertEquals(34, int(bytes, 20))
        assertEquals(8, bytes[24].toInt())
        assertEquals(0, bytes[25].toInt())
        assertEquals("IEND", String(bytes, bytes.size - 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun `damaged or foreign files are refused`() {
        val good = MaskPng.encode(softEllipse(20, 30))

        assertNull(MaskPng.decode(ByteArray(0)))
        assertNull(MaskPng.decode(good.copyOf(good.size - 1)))
        assertNull(MaskPng.decode(good.copyOf().also { it[1] = 0 }))
        // A changed pixel byte in IDAT no longer matches its CRC.
        assertNull(MaskPng.decode(good.copyOf().also { it[45] = (it[45] + 1).toByte() }))
        assertNull(MaskPng.decode(withHeader(good) { it[25] = 2 }))
        assertNull(MaskPng.decode(withHeader(good) { it[24] = 16 }))
        assertNull(
            MaskPng.decode(
                withHeader(good) {
                    putInt(it, 16, 4096)
                    putInt(it, 20, 4096)
                },
            ),
        )
        // Well-formed chunks, but the image data is one row short of the header's size.
        assertNull(MaskPng.decode(withHeader(good) { putInt(it, 20, 31) }))
    }

    @Test
    fun `a mask at the limits is read and one pixel more is refused`() {
        val bytes = MaskPng.encode(softEllipse(40, 30))

        assertEquals(40 to 30, MaskPng.decode(bytes, maxWidth = 40, maxHeight = 30)?.let { it.width to it.height })
        assertNull(MaskPng.decode(bytes, maxWidth = 39, maxHeight = 30))
        assertNull(MaskPng.decode(bytes, maxWidth = 40, maxHeight = 29))
    }

    @Test
    fun `fitHeight keeps a mask that fits and averages one that does not`() {
        val fits = softEllipse(30, 720)
        assertEquals(fits, fits.fitHeight(720))

        val tall = MaskPixels(2, 4, intArrayOf(0, 255, 255, 255, 100, 100, 0, 0))
        val half = tall.fitHeight(2)
        assertEquals(1 to 2, half.width to half.height)
        assertArrayEquals(intArrayOf(191, 50), half.alpha)

        val scaled = softEllipse(300, 900).fitHeight(720)
        assertEquals(240 to 720, scaled.width to scaled.height)
    }

    /** [bytes] with its IHDR body changed by [change] and its CRC made valid again, so only the change is wrong. */
    private fun withHeader(bytes: ByteArray, change: (ByteArray) -> Unit): ByteArray {
        val copy = bytes.copyOf()
        change(copy)
        val crc = CRC32().apply { update(copy, 12, 17) }.value
        putInt(copy, 29, crc.toInt())
        return copy
    }

    private fun int(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF shl 24) or (bytes[at + 1].toInt() and 0xFF shl 16) or
            (bytes[at + 2].toInt() and 0xFF shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun putInt(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0..3) bytes[at + i] = (value ushr (24 - 8 * i)).toByte()
    }
}
