// AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [PersonSignals] from ML Kit's Pose Detection (base model) and Face Detection, both bundled with the app, so the
 * check works offline and at its first use (Design 2.6.3). The models run on ML Kit's own threads; the clients are
 * created on first use and live as long as the app.
 * Owner: Server/AI/Sync (#7).
 */
class MlKitPersonSignals : PersonSignals {

    private val poses: PoseDetector by lazy {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build(),
        )
    }

    private val faces: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                // A person far away has a small face; the default, a tenth of the photo's width, missed them.
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override suspend fun poseLikelihoods(scene: Bitmap): List<Float> =
        poses.process(InputImage.fromBitmap(scene, 0)).await().allPoseLandmarks.map { it.inFrameLikelihood }

    override suspend fun faceCount(scene: Bitmap): Int = faces.process(InputImage.fromBitmap(scene, 0)).await().size

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        /** The smallest face looked for, as a share of the photo's width. */
        const val MIN_FACE_SIZE = 0.02f
    }
}
