// AI-generated with Claude Code, 2026-10-27, reviewed by Dongje Park
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The guide's reference photo as it travels to the subject, for Subject view's thumbnail only (FR-3.9, AD-6): a copy
 * at most [JpegGuideReferenceCodec.MAX_LONG_SIDE] on its long side, split into the `guide.reference.*` messages of
 * Design 2.5.1 like the guide image. It is never drawn as the guide, never saved, and never goes through the server.
 * Owner: Real-time (#9), with the reference from #132.
 */
class EncodedGuideReference(
    val guideId: String,
    val format: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
) {
    val crc32: Long = GuideImageChunks.crc32(bytes)
    val chunks: List<String> = GuideImageChunks.split(bytes)

    fun begin() = SessionMessage.GuideReferenceBegin(guideId, format, width, height, bytes.size, chunks.size)

    fun chunk(index: Int) = SessionMessage.GuideReferenceChunk(guideId, index, chunks[index])

    fun end() = SessionMessage.GuideReferenceEnd(guideId, crc32)
}

/** Subject: collects one reference's chunks with the guide image's checks ([GuideImageAssembler]). */
class GuideReferenceAssembler private constructor(
    val begin: SessionMessage.GuideReferenceBegin,
    private val image: GuideImageAssembler,
) {
    /** False when the chunk is not the next one of this reference: the transfer is broken and is dropped. */
    fun add(chunk: SessionMessage.GuideReferenceChunk): Boolean =
        image.add(SessionMessage.GuideImageChunk(chunk.guideId, chunk.index, chunk.data))

    /** The photo's bytes, or null when a chunk is missing, the size differs, or the CRC does not match. */
    fun finish(end: SessionMessage.GuideReferenceEnd): ByteArray? =
        image.finish(SessionMessage.GuideImageEnd(end.guideId, end.crc32))

    companion object {
        /** Null for a `begin` that cannot describe a real photo. */
        fun start(begin: SessionMessage.GuideReferenceBegin): GuideReferenceAssembler? {
            val image = SessionMessage.GuideImageBegin(
                begin.guideId,
                begin.format,
                begin.width,
                begin.height,
                begin.bytes,
                begin.chunks,
            )
            return GuideImageAssembler.start(image)?.let { GuideReferenceAssembler(begin, it) }
        }
    }
}

/** Bitmap ⇄ bytes for the reference photo, apart from the guide image's codec. Faked in tests. */
interface GuideReferenceCodec {
    /** The guide's reference photo, ready to send; null when the guide has none or it cannot be encoded. */
    fun encode(guide: ReferenceGuide): EncodedGuideReference?

    /** The photo from received bytes; null if undecodable. */
    fun decode(format: String, bytes: ByteArray): Bitmap?
}

class JpegGuideReferenceCodec : GuideReferenceCodec {

    override fun encode(guide: ReferenceGuide): EncodedGuideReference? {
        val photo = guide.reference ?: return null
        val long = maxOf(photo.width, photo.height)
        val scaled = if (long > MAX_LONG_SIDE) {
            val scale = MAX_LONG_SIDE.toFloat() / long
            photo.scale(maxOf(1, (photo.width * scale).roundToInt()), maxOf(1, (photo.height * scale).roundToInt()))
        } else {
            photo
        }
        val out = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) return null
        return EncodedGuideReference(guide.id, FORMAT, scaled.width, scaled.height, out.toByteArray())
    }

    override fun decode(format: String, bytes: ByteArray): Bitmap? {
        if (format != FORMAT) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    companion object {
        const val FORMAT = "jpeg"

        /** The reference on the photographer's phone is already this small (Design 2.3, `ReferenceGuide.reference`). */
        const val MAX_LONG_SIDE = 512

        /** A 512 px photo at 85 is a few tens of KB, a handful of chunks. */
        const val QUALITY = 85
    }
}

/** The reference photo of the guide [guideId], on the subject's phone. */
class GuideReference(val guideId: String, val photo: Bitmap)

/**
 * The subject's copy of the photographer's reference photo (FR-3.9), in memory only beside the mirror's guide.
 * [GuideSyncer] writes it, and drops it when another guide arrives or the guide is cleared; Subject view shows it
 * while it belongs to the mirror's guide.
 */
class MirrorReference {
    private val _reference = MutableStateFlow<GuideReference?>(null)
    val reference: StateFlow<GuideReference?> = _reference.asStateFlow()

    fun set(reference: GuideReference?) {
        _reference.value = reference
    }
}
