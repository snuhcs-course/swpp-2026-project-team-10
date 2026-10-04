package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter as MlKitSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

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

    /** The model comes from Google Play services; the first use waits for its download (Design 2.6.1, step 2). */
    private suspend fun ensureModule() {
        val installer = ModuleInstall.getClient(context)
        if (installer.areModulesAvailable(client).await().areModulesAvailable()) return
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: InstallStatusListener

            // The status listener and the request's own result can both report the end; the first one wins.
            // Both arrive on the main thread, so checking isActive is enough.
            fun finish(error: Throwable?) {
                installer.unregisterListener(listener)
                if (!continuation.isActive) return
                if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
            }
            listener = InstallStatusListener { update: ModuleInstallStatusUpdate ->
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> finish(null)

                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED ->
                        finish(IllegalStateException("The segmentation model was not installed"))

                    else -> Unit
                }
            }
            val request = ModuleInstallRequest.newBuilder().addApi(client).setListener(listener).build()
            installer.installModules(request)
                .addOnSuccessListener { response -> if (response.areModulesAlreadyInstalled()) finish(null) }
                .addOnFailureListener { error -> finish(error) }
            continuation.invokeOnCancellation { installer.unregisterListener(listener) }
        }
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
        const val MIN_PERSON_FRACTION = 0.02f
    }
}
