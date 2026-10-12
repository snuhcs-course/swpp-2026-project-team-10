// AI-generated with Claude Code, 2026-10-06, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.OutlineExtractor
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.outlineStrokePx
import com.lastpenguin.pix.session.protocol.SessionMessage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.roundToInt

/**
 * The guide image as it travels: the cutout only, as WebP with alpha, at most [WebpGuideImageCodec.MAX_HEIGHT] tall
 * (Design 2.6.1, step 6), already split into the messages of Design 2.5.1.
 * Owner: Real-time (#9).
 */
class EncodedGuideImage(
    val guideId: String,
    val format: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
) {
    val crc32: Long = GuideImageChunks.crc32(bytes)
    val chunks: List<String> = GuideImageChunks.split(bytes)

    fun begin() = SessionMessage.GuideImageBegin(guideId, format, width, height, bytes.size, chunks.size)

    fun chunk(index: Int) = SessionMessage.GuideImageChunk(guideId, index, chunks[index])

    fun end() = SessionMessage.GuideImageEnd(guideId, crc32)
}

/** Splitting and checking, with no Android dependency. `data` is Base64 of at most 12 KiB per message (Design 2.5.1). */
object GuideImageChunks {
    /** 9 KiB of image per chunk becomes 12 KiB of Base64, so one message stays under 16 KiB. */
    const val CHUNK_BYTES = 9 * 1024
    const val MAX_CHUNK_CHARS = 12 * 1024

    /** Far above a 720 px WebP cutout; a `begin` claiming more is refused. */
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

    fun split(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        val encoder = Base64.getEncoder()
        return (bytes.indices step CHUNK_BYTES).map { start ->
            encoder.encodeToString(bytes.copyOfRange(start, minOf(start + CHUNK_BYTES, bytes.size)))
        }
    }

    fun chunkCount(bytes: Int): Int = (bytes + CHUNK_BYTES - 1) / CHUNK_BYTES

    fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value
}

/** Subject: collects the chunks of one image in order and checks the whole at the end. */
class GuideImageAssembler private constructor(val begin: SessionMessage.GuideImageBegin) {
    private val out = ByteArrayOutputStream(begin.bytes)
    private var next = 0

    /** False when the chunk is not the next one of this image: the transfer is broken and is dropped. */
    fun add(chunk: SessionMessage.GuideImageChunk): Boolean {
        if (chunk.guideId != begin.guideId || chunk.index != next ||
            chunk.data.length > GuideImageChunks.MAX_CHUNK_CHARS
        ) {
            return false
        }
        val decoded = try {
            Base64.getDecoder().decode(chunk.data)
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (out.size() + decoded.size > begin.bytes) return false
        out.write(decoded)
        next++
        return true
    }

    /** The image bytes, or null when a chunk is missing, the size differs, or the CRC does not match. */
    fun finish(end: SessionMessage.GuideImageEnd): ByteArray? {
        if (end.guideId != begin.guideId || next != begin.chunks) return null
        val bytes = out.toByteArray()
        if (bytes.size != begin.bytes || GuideImageChunks.crc32(bytes) != end.crc32) return null
        return bytes
    }

    companion object {
        /** Null for a `begin` that cannot describe a real image. */
        fun start(begin: SessionMessage.GuideImageBegin): GuideImageAssembler? {
            val plausible = begin.bytes in 1..GuideImageChunks.MAX_IMAGE_BYTES &&
                begin.chunks == GuideImageChunks.chunkCount(begin.bytes) &&
                begin.width > 0 && begin.height > 0
            return if (plausible) GuideImageAssembler(begin) else null
        }
    }
}

/** Bitmap ⇄ bytes, the only part of guide sync that needs Android graphics. Faked in tests. */
interface GuideImageCodec {
    fun encode(guide: ReferenceGuide): EncodedGuideImage

    /** A guide from received bytes, with the outline rebuilt from the cutout's alpha (Design 2.6.1, step 5); null if undecodable. */
    fun decode(guideId: String, format: String, bytes: ByteArray): ReferenceGuide?
}

class WebpGuideImageCodec(private val outlines: OutlineExtractor = OutlineExtractor()) : GuideImageCodec {

    override fun encode(guide: ReferenceGuide): EncodedGuideImage {
        val cutout = guide.cutout
        val scaled = if (cutout.height > MAX_HEIGHT) {
            val width = maxOf(1, (cutout.width.toFloat() * MAX_HEIGHT / cutout.height).roundToInt())
            Bitmap.createScaledBitmap(cutout, width, MAX_HEIGHT, true)
        } else {
            cutout
        }
        val out = ByteArrayOutputStream()
        check(scaled.compress(compressFormat(), QUALITY, out)) { "WebP encoding failed" }
        return EncodedGuideImage(guide.id, FORMAT, scaled.width, scaled.height, out.toByteArray())
    }

    override fun decode(guideId: String, format: String, bytes: ByteArray): ReferenceGuide? {
        if (format != FORMAT) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val cutout = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val outline = outlines.extract(cutout, outlineStrokePx(cutout.height))
        // The subject only draws the guide; where the photographer got it from does not travel.
        return ReferenceGuide(guideId, cutout, outline, cutout.width.toFloat() / cutout.height, GuideSource.GALLERY)
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    companion object {
        const val FORMAT = "webp"
        const val MAX_HEIGHT = 720

        /** Lossy WebP keeps the alpha channel; at 90 a 540×720 cutout is a few tens of KB. */
        const val QUALITY = 90
    }
}
