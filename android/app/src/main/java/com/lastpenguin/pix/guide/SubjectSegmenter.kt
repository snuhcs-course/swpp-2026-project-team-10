// AI-generated with Claude Code, 2026-10-05, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter as MlKitSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.lastpenguin.pix.core.Timings
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** The segmentation model from Google Play services did not arrive in time, for example without a network. */
class SegmentationModelUnavailableException : Exception("The segmentation model is not downloaded yet")

/** What the segmenter found in one photo (Design 2.6.1). */
class Segmentation(
    /** The person with a transparent background, same size as the input. */
    val foreground: Bitmap,
    /** Foreground confidence as alpha (ALPHA_8), same size as the input. */
    val mask: Bitmap,
    /** Bounding box of the pixels with confidence ≥ 0.5. */
    val bounds: Rect,
)

/**
 * ML Kit subject segmentation (Design 2.6.1, steps 2–3).
 * Owner: PM (#5).
 */
class SubjectSegmenter(private val context: Context) {

    private val client: MlKitSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .enableForegroundBitmap()
                .build(),
        )
    }

    /** Fails with [NoPersonFoundException] if fewer than 2% of the pixels have confidence ≥ 0.5. */
    suspend fun segment(bitmap: Bitmap): Result<Segmentation> = try {
        ensureModule()
        val result = client.process(InputImage.fromBitmap(bitmap, 0)).await()
        val width = bitmap.width
        val height = bitmap.height
        val confidence = FloatArray(width * height)
        checkNotNull(result.foregroundConfidenceMask) { "No confidence mask" }.apply {
            rewind()
            get(confidence)
        }
        val foreground = checkNotNull(result.foregroundBitmap) { "No foreground bitmap" }
        val area = foregroundBounds(confidence, width, height)
        if (area == null || area.count < MIN_PERSON_FRACTION * width * height) throw NoPersonFoundException()
        Result.success(
            Segmentation(
                foreground = foreground,
                mask = alphaMask(confidence, width, height),
                bounds = Rect(area.left, area.top, area.right, area.bottom),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * The model comes from Google Play services; the first use waits for its download (Design 2.6.1, step 2).
     * On the Galaxy S22 the install status reported an end while Play services was still downloading, so this asks
     * for the install and then checks until the model is really there, or gives up after [MODEL_WAIT_MS].
     */
    private suspend fun ensureModule() {
        val installer = ModuleInstall.getClient(context)
        suspend fun available() = installer.areModulesAvailable(client).await().areModulesAvailable()
        if (available()) return
        Timings.mark("seg.model", "downloading")
        try {
            installer.installModules(ModuleInstallRequest.newBuilder().addApi(client).build()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play services may already be downloading it for the segmenter itself; keep checking.
            Log.w(TAG, "Install request for the segmentation model failed", e)
        }
        withTimeoutOrNull(MODEL_WAIT_MS) {
            while (!available()) delay(MODEL_CHECK_MS)
        } ?: throw SegmentationModelUnavailableException()
        Timings.mark("seg.model", "ready")
    }

    private fun alphaMask(confidence: FloatArray, width: Int, height: Int): Bitmap {
        val mask = createBitmap(width, height, Bitmap.Config.ALPHA_8)
        mask.copyPixelsFromBuffer(ByteBuffer.wrap(confidenceToAlpha(confidence, width, height, mask.rowBytes)))
        return mask
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val TAG = "PixSegmenter"
        const val MIN_PERSON_FRACTION = 0.02f
        const val MODEL_WAIT_MS = 60_000L
        const val MODEL_CHECK_MS = 500L
    }
}
