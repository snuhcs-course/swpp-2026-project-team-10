package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import com.lastpenguin.pix.core.Timings
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ReferenceGuideMaker] on the device: load, segment, crop, outline (Design 2.6.1).
 * Owner: PM (#5).
 */
class MlKitReferenceGuideMaker(
    private val context: Context,
    private val segmenter: SubjectSegmenter,
    private val outlineExtractor: OutlineExtractor,
) : ReferenceGuideMaker {

    override suspend fun make(source: Uri): Result<ReferenceGuide> =
        timed(GuideSource.GALLERY) { build(decode(source), GuideSource.GALLERY) }

    override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> =
        timed(source) { build(bitmap, source) }

    /** `seg.start` and `seg.end` around the whole step, from the picked photo to the guide (NFR-3). */
    private suspend fun timed(source: GuideSource, block: suspend () -> ReferenceGuide): Result<ReferenceGuide> =
        withContext(Dispatchers.Default) {
            Timings.mark("seg.start", source.name)
            val result = try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            Timings.mark(
                "seg.end",
                result.fold(
                    onSuccess = { "${it.cutout.width}x${it.cutout.height}" },
                    onFailure = { it::class.simpleName },
                ),
            )
            result
        }

    /** Step 1: decodes with the EXIF rotation applied, subsampled close to the 1280 px long side. */
    private fun decode(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            // ML Kit and getPixels need a software bitmap.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(decodeSampleSize(info.size.width, info.size.height))
        }

    /** Steps 2–5 on an upright image: segment, crop to the person plus 2%, and outline. */
    private suspend fun build(input: Bitmap, source: GuideSource): ReferenceGuide {
        val bitmap = prepare(input)
        val segmentation = segmenter.segment(bitmap).getOrThrow()
        val box = segmentation.bounds
        val crop = cropWithMargin(PixelRect(box.left, box.top, box.right, box.bottom), bitmap.width, bitmap.height)
        val cutout = Bitmap.createBitmap(segmentation.foreground, crop.left, crop.top, crop.width, crop.height)
        val mask = Bitmap.createBitmap(segmentation.mask, crop.left, crop.top, crop.width, crop.height)
        return ReferenceGuide(
            id = UUID.randomUUID().toString(),
            cutout = cutout,
            outline = outlineExtractor.extract(mask, outlineStrokePx(crop.height)),
            aspect = crop.width.toFloat() / crop.height,
            source = source,
        )
    }

    /** ARGB_8888 in memory with the long side at most 1280 px; pose candidates can arrive in any config. */
    private fun prepare(bitmap: Bitmap): Bitmap {
        val software = when (bitmap.config) {
            Bitmap.Config.ARGB_8888 -> bitmap
            else -> bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val size = fitLongSide(software.width, software.height)
        return if (size.width == software.width && size.height == software.height) {
            software
        } else {
            software.scale(size.width, size.height)
        }
    }
}
