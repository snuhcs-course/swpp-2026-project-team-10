// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter as MlKitSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.lastpenguin.pix.core.Timings
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
    /** Foreground confidence 0..1 per pixel, row-major, the same size as the input. */
    val confidence: FloatArray,
    val width: Int,
    val height: Int,
)

/**
 * ML Kit subject segmentation (Design 2.6.1, steps 2–3). Open so that a test can stand in for the model with a
 * confidence field of its own (MlKitReferenceGuideMakerTest); the client is created on first use only.
 * Owner: PM (#5).
 */
open class SubjectSegmenter(private val context: Context) {

    private val client: MlKitSegmenter by lazy {
        SubjectSegmentation.getClient(
            // Only the confidence: the guide is built from the smoothed mask, not from ML Kit's foreground bitmap.
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build(),
        )
    }

    /** Fails with [NoPersonFoundException] if fewer than 2% of the pixels have confidence ≥ 0.5. */
    open suspend fun segment(bitmap: Bitmap): Result<Segmentation> = try {
        ensureModule()
        val result = client.process(InputImage.fromBitmap(bitmap, 0)).await()
        val width = bitmap.width
        val height = bitmap.height
        val confidence = FloatArray(width * height)
        checkNotNull(result.foregroundConfidenceMask) { "No confidence mask" }.apply {
            rewind()
            get(confidence)
        }
        val area = foregroundBounds(confidence, width, height)
        if (area == null || area.count < MIN_PERSON_FRACTION * width * height) throw NoPersonFoundException()
        Result.success(
            Segmentation(confidence, width, height),
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
