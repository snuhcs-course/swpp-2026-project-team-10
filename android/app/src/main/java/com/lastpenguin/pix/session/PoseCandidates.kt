// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.lastpenguin.pix.session.protocol.SessionMessage
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * A copy of one of the photographer's candidates as it travels to the subject, for the subject's *Pick a pose* only
 * (FR-6.15): at most [JpegPoseCandidateCodec.MAX_LONG_SIDE] on its long side, split into the `pose.candidate.*`
 * messages of Design 2.5.1 like the guide image. It is never drawn as the guide, never saved, and never goes through
 * the server.
 * Owner: Shared steps (Iter3-P16).
 */
class EncodedPoseCandidate(
    val poseId: String,
    val format: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
) {
    val crc32: Long = GuideImageChunks.crc32(bytes)
    val chunks: List<String> = GuideImageChunks.split(bytes)

    fun begin(setId: String) =
        SessionMessage.PoseCandidateBegin(setId, poseId, format, width, height, bytes.size, chunks.size)

    fun chunk(setId: String, index: Int) = SessionMessage.PoseCandidateChunk(setId, poseId, index, chunks[index])

    fun end(setId: String) = SessionMessage.PoseCandidateEnd(setId, poseId, crc32)
}

/** Subject: collects one candidate's chunks with the guide image's checks ([GuideImageAssembler]). */
class PoseCandidateAssembler private constructor(
    val begin: SessionMessage.PoseCandidateBegin,
    private val image: GuideImageAssembler,
) {
    /** False when the chunk is not the next one of this candidate: the transfer is broken and is dropped. */
    fun add(chunk: SessionMessage.PoseCandidateChunk): Boolean = chunk.setId == begin.setId &&
        image.add(SessionMessage.GuideImageChunk(chunk.poseId, chunk.index, chunk.data))

    /** The copy's bytes, or null when a chunk is missing, the size differs, or the CRC does not match. */
    fun finish(end: SessionMessage.PoseCandidateEnd): ByteArray? {
        if (end.setId != begin.setId) return null
        return image.finish(SessionMessage.GuideImageEnd(end.poseId, end.crc32))
    }

    companion object {
        /** A 1024 px JPEG at 85 is a few hundred KB; a `begin` claiming more is refused. */
        const val MAX_BYTES = 1024 * 1024

        /** Null for a `begin` that cannot describe a copy the photographer sends, refused before any chunk. */
        fun start(begin: SessionMessage.PoseCandidateBegin): PoseCandidateAssembler? {
            val limit = JpegPoseCandidateCodec.MAX_LONG_SIDE
            if (begin.bytes > MAX_BYTES || begin.width > limit || begin.height > limit) return null
            val image = SessionMessage.GuideImageBegin(
                begin.poseId,
                begin.format,
                begin.width,
                begin.height,
                begin.bytes,
                begin.chunks,
            )
            return GuideImageAssembler.start(image)?.let { PoseCandidateAssembler(begin, it) }
        }
    }
}

/** Bitmap ⇄ bytes for the candidates' copies, apart from the guide's codecs. Faked in tests. */
interface PoseCandidateCodec {
    /** The candidate [image] ready to send; null when it cannot be encoded. */
    fun encode(poseId: String, image: Bitmap): EncodedPoseCandidate?

    /** The copy from received bytes; null if undecodable or larger than may be sent. */
    fun decode(format: String, bytes: ByteArray): Bitmap?
}

/**
 * A copy as JPEG at most [MAX_LONG_SIDE] on its long side, at [QUALITY] or, for a detailed image that would not fit
 * the receiver's [maxBytes], at a lower quality; one that does not fit at the lowest is left out (null), so the sender
 * never announces a copy the subject must refuse.
 */
class JpegPoseCandidateCodec(private val maxBytes: Int = PoseCandidateAssembler.MAX_BYTES) : PoseCandidateCodec {

    override fun encode(poseId: String, image: Bitmap): EncodedPoseCandidate? {
        val long = maxOf(image.width, image.height)
        val scaled = if (long > MAX_LONG_SIDE) {
            val scale = MAX_LONG_SIDE.toFloat() / long
            image.scale(maxOf(1, (image.width * scale).roundToInt()), maxOf(1, (image.height * scale).roundToInt()))
        } else {
            image
        }
        for (quality in QUALITIES) {
            val out = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
            if (out.size() <= maxBytes) {
                return EncodedPoseCandidate(poseId, FORMAT, scaled.width, scaled.height, out.toByteArray())
            }
        }
        return null
    }

    override fun decode(format: String, bytes: ByteArray): Bitmap? {
        if (format != FORMAT) return null
        // The size first, without the pixels: a copy larger than the photographer sends is refused before it can take
        // the memory of a full decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_LONG_SIDE || bounds.outHeight !in 1..MAX_LONG_SIDE) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    companion object {
        const val FORMAT = "jpeg"

        /**
         * *Pick a pose* shows one candidate as large as the screen allows, about 1000 px wide on a phone, so the guide
         * reference's 512 px would look soft; the scene photo is prepared to the same long side (Design 2.6.3).
         */
        const val MAX_LONG_SIDE = 1024

        /** As the scene photo and the reference copy. */
        const val QUALITY = 85

        /** What a copy over the receiver's limit is tried at next; noise-like detail still fits at the last. */
        private val QUALITIES = listOf(QUALITY, 70, 55)
    }
}
