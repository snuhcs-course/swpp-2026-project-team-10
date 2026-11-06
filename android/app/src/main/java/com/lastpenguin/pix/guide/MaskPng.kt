// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.abs

/** A guide's smoothed mask as row-major values 0..255 (255 = the person), [width] × [height]. */
class MaskPixels(val width: Int, val height: Int, val alpha: IntArray) {
    init {
        require(width > 0 && height > 0 && alpha.size == width * height) { "${alpha.size} values for ${width}x$height" }
    }

    /** At most [maxHeight] tall, each value the mean of the pixels it covers; a smaller mask is not scaled up. */
    fun fitHeight(maxHeight: Int): MaskPixels {
        if (height <= maxHeight) return this
        val outHeight = maxHeight
        val outWidth = maxOf(1, Math.round(width.toFloat() * maxHeight / height))
        val out = IntArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val top = y * height / outHeight
            val bottom = maxOf(top + 1, (y + 1) * height / outHeight)
            for (x in 0 until outWidth) {
                val left = x * width / outWidth
                val right = maxOf(left + 1, (x + 1) * width / outWidth)
                var sum = 0
                for (sy in top until bottom) for (sx in left until right) sum += alpha[sy * width + sx]
                val count = (bottom - top) * (right - left)
                out[y * outWidth + x] = (sum + count / 2) / count
            }
        }
        return MaskPixels(outWidth, outHeight, out)
    }

    override fun equals(other: Any?): Boolean =
        other is MaskPixels && other.width == width && other.height == height && other.alpha.contentEquals(alpha)

    override fun hashCode(): Int = 31 * (31 * width + height) + alpha.contentHashCode()
}

/**
 * The mask as it travels and as saved guides keep it (Design 2.5.1, `mask-png`; 2.8): an 8-bit grayscale PNG whose
 * gray value is the mask. Written and read here rather than through `Bitmap.compress`, which has no grayscale
 * config, so the bytes would depend on how the platform encodes `ALPHA_8`. Plain Kotlin, so it runs in JVM tests;
 * it reads only the PNGs it writes (8-bit grayscale, not interlaced).
 * Owner: Guide sync (#113).
 */
object MaskPng {
    /** Far above a 720 px mask; a PNG that claims more is refused before it is inflated. */
    const val MAX_PIXELS = 2048 * 2048

    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val COLOR_GRAY = 0

    fun encode(mask: MaskPixels): ByteArray {
        val width = mask.width
        val raw = ByteArray(mask.height * (width + 1))
        val previous = IntArray(width)
        val current = IntArray(width)
        for (y in 0 until mask.height) {
            for (x in 0 until width) current[x] = mask.alpha[y * width + x]
            val (filter, row) = bestFilter(current, previous)
            val start = y * (width + 1)
            raw[start] = filter.toByte()
            System.arraycopy(row, 0, raw, start + 1, width)
            current.copyInto(previous)
        }
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        val header = ByteArrayOutputStream().apply {
            writeInt(width)
            writeInt(mask.height)
            write(8)
            write(COLOR_GRAY)
            write(0)
            write(0)
            write(0)
        }
        out.writeChunk("IHDR", header.toByteArray())
        out.writeChunk("IDAT", deflate(raw))
        out.writeChunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /**
     * The mask in [bytes], or null when they are not an 8-bit grayscale PNG of a plausible size. A header wider than
     * [maxWidth] or taller than [maxHeight] is refused before anything is inflated, so a mask from the other phone
     * cannot make this phone allocate more than the masks it makes itself.
     */
    fun decode(bytes: ByteArray, maxWidth: Int = Int.MAX_VALUE, maxHeight: Int = Int.MAX_VALUE): MaskPixels? {
        if (bytes.size < SIGNATURE.size || !bytes.copyOf(SIGNATURE.size).contentEquals(SIGNATURE)) return null
        var at = SIGNATURE.size
        var width = 0
        var height = 0
        val data = ByteArrayOutputStream()
        var ended = false
        while (!ended) {
            if (at + 8 > bytes.size) return null
            val length = bytes.readInt(at)
            if (length < 0 || at + 12L + length > bytes.size) return null
            val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
            val body = bytes.copyOfRange(at + 8, at + 8 + length)
            val crc = CRC32().apply { update(bytes, at + 4, 4 + length) }.value
            if (bytes.readInt(at + 8 + length).toLong() and 0xFFFFFFFFL != crc) return null
            at += 12 + length
            when (type) {
                "IHDR" -> {
                    if (length != 13) return null
                    width = body.readInt(0)
                    height = body.readInt(4)
                    val plain = body[8].toInt() == 8 && body[9].toInt() == COLOR_GRAY &&
                        body[10].toInt() == 0 && body[11].toInt() == 0 && body[12].toInt() == 0
                    if (!plain || width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) return null
                    if (width > maxWidth || height > maxHeight) return null
                }

                "IDAT" -> data.write(body)

                "IEND" -> ended = true
            }
        }
        if (width == 0) return null
        val raw = inflate(data.toByteArray(), height * (width + 1)) ?: return null
        return unfilter(raw, width, height)
    }

    /** Of the PNG specification's five row filters, the one whose filtered row sums to the least, and that row. */
    private fun bestFilter(current: IntArray, previous: IntArray): Pair<Int, ByteArray> {
        var best = 0
        var bestRow = ByteArray(0)
        var bestCost = Long.MAX_VALUE
        for (filter in 0..4) {
            val row = ByteArray(current.size)
            var cost = 0L
            for (x in current.indices) {
                val left = if (x > 0) current[x - 1] else 0
                val up = previous[x]
                val upLeft = if (x > 0) previous[x - 1] else 0
                val value = (current[x] - predict(filter, left, up, upLeft)) and 0xFF
                row[x] = value.toByte()
                cost += abs(value.toByte().toInt())
            }
            if (cost < bestCost) {
                best = filter
                bestRow = row
                bestCost = cost
            }
        }
        return best to bestRow
    }

    private fun unfilter(raw: ByteArray, width: Int, height: Int): MaskPixels? {
        val alpha = IntArray(width * height)
        for (y in 0 until height) {
            val filter = raw[y * (width + 1)].toInt()
            if (filter !in 0..4) return null
            for (x in 0 until width) {
                val left = if (x > 0) alpha[y * width + x - 1] else 0
                val up = if (y > 0) alpha[(y - 1) * width + x] else 0
                val upLeft = if (x > 0 && y > 0) alpha[(y - 1) * width + x - 1] else 0
                val stored = raw[y * (width + 1) + 1 + x].toInt() and 0xFF
                alpha[y * width + x] = (stored + predict(filter, left, up, upLeft)) and 0xFF
            }
        }
        return MaskPixels(width, height, alpha)
    }

    private fun predict(filter: Int, left: Int, up: Int, upLeft: Int): Int = when (filter) {
        1 -> left
        2 -> up
        3 -> (left + up) / 2
        4 -> paeth(left, up, upLeft)
        else -> 0
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) {
            a
        } else if (pb <= pc) {
            b
        } else {
            c
        }
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** Exactly [size] bytes, or null when the stream is damaged or holds another amount. */
    private fun inflate(data: ByteArray, size: Int): ByteArray? {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArray(size)
            var filled = 0
            while (filled < size) {
                val read = inflater.inflate(out, filled, size - filled)
                if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                filled += read
            }
            val extra = ByteArray(1)
            if (filled != size || inflater.inflate(extra) != 0 || !inflater.finished()) return null
            return out
        } catch (error: DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, body: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeInt(body.size)
        write(typeBytes)
        write(body)
        writeInt(CRC32().apply { update(typeBytes) }.apply { update(body) }.value.toInt())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }

    private fun ByteArray.readInt(at: Int): Int =
        (this[at].toInt() and 0xFF shl 24) or (this[at + 1].toInt() and 0xFF shl 16) or
            (this[at + 2].toInt() and 0xFF shl 8) or (this[at + 3].toInt() and 0xFF)
}
