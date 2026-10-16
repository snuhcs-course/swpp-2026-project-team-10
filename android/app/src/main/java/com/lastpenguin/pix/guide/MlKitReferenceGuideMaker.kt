// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.lastpenguin.pix.core.Timings
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ReferenceGuideMaker] on the device: load, segment, smooth, crop, outline (Design 2.6.1).
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
                // No person is an expected answer; anything else is worth the stack trace in logcat.
                if (e !is NoPersonFoundException) Log.w(TAG, "Could not make a guide from the reference", e)
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

    /**
     * Steps 2–5 on an upright image: segment, smooth the mask, crop to the person plus 2% and keep where that crop
     * lies as the anchor (FR-3.1), and outline. The cutout's alpha is the smoothed mask, so the subject, which
     * rebuilds the outline from it, draws the same line (FR-2.9).
     */
    private suspend fun build(input: Bitmap, source: GuideSource): ReferenceGuide {
        val bitmap = prepare(input)
        val segmentation = segmenter.segment(bitmap).getOrThrow()
        val width = segmentation.width
        val height = segmentation.height
        Timings.mark("smooth.start")
        val radius = smoothRadius(subjectHeight(segmentation.confidence, width, height))
        val field = smoothMask(segmentation.confidence, width, height, radius)
        // Smoothing drops specks, so a photo with nothing but specks has no person after all.
        val area = foregroundBounds(field, width, height) ?: throw NoPersonFoundException()
        val crop = cropWithMargin(PixelRect(area.left, area.top, area.right, area.bottom), width, height)
        val colors = IntArray(crop.width * crop.height)
        bitmap.getPixels(colors, 0, crop.width, crop.left, crop.top, crop.width, crop.height)
        val alpha = IntArray(colors.size)
        for (y in 0 until crop.height) {
            for (x in 0 until crop.width) {
                val k = y * crop.width + x
                alpha[k] = smoothedAlpha(field[(crop.top + y) * width + crop.left + x], radius)
                colors[k] = (colors[k] and 0x00FFFFFF) or (alpha[k] shl 24)
            }
        }
        val cutout = createBitmap(crop.width, crop.height).apply {
            setPixels(colors, 0, crop.width, 0, 0, crop.width, crop.height)
        }
        val outline = outlineExtractor.extract(alpha, crop.width, crop.height, outlineStrokePx(crop.height))
        Timings.mark("smooth.end", "r=$radius")
        return ReferenceGuide(
            id = UUID.randomUUID().toString(),
            cutout = cutout,
            outline = outline,
            aspect = crop.width.toFloat() / crop.height,
            source = source,
            anchor = crop.relativeTo(width, height),
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

    private companion object {
        const val TAG = "PixGuideMaker"
    }
}
