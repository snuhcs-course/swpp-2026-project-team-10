// AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import android.util.Log
import com.lastpenguin.pix.core.network.PixApi
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * [PoseTipper] through the Pix server's `POST /pose-tips` (Design 2.5.3, 2.6.3): the scene as `POST /poses` got it,
 * the candidate at a long side of 768 px, the pose, and the language the screens show at the time of the call.
 * Owner: Pose tips.
 */
class RemotePoseTipper(
    private val api: PixApi,
    /** `en` or `ko` ([screenLanguage]), read for each tip, so a language changed while Pix runs is followed. */
    private val language: () -> String = { ENGLISH },
    private val timeoutMs: Long = TIMEOUT_MS,
    private val images: PoseImageCodec = AndroidPoseImageCodec,
    /** Image encoding runs here, off the main thread (Design 2.8, Concurrency). */
    private val imageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PoseTipper {

    override suspend fun tip(scene: Bitmap, candidate: Bitmap, pose: String): Result<PoseTip> =
        try {
            val (scenePhoto, candidatePhoto) = withContext(imageDispatcher) {
                images.encodeScene(scene) to images.encodeTipCandidate(candidate)
            }
            val answer = withTimeoutOrNull(timeoutMs) {
                api.poseTip(
                    MultipartBody.Part.createFormData("scene", "scene.jpg", scenePhoto.toRequestBody(JPEG)),
                    MultipartBody.Part.createFormData("candidate", "candidate.jpg", candidatePhoto.toRequestBody(JPEG)),
                    pose.toRequestBody(TEXT),
                    language().toRequestBody(TEXT),
                )
            } ?: throw SocketTimeoutException("The server gave no tip within $timeoutMs ms")
            if (answer.text.isBlank()) throw IOException("The server gave an empty tip")
            Result.success(PoseTip(answer.text.trim()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The pose and the tip stay out of the log: the pose can be the user's own words.
            Log.w(TAG, "no tip: $error")
            Result.failure(error)
        }

    companion object {
        /** The server waits 10 s for its model; this leaves time for the two uploads. */
        const val TIMEOUT_MS = 15_000L
        private const val TAG = "PixTips"
        private val JPEG = "image/jpeg".toMediaType()
        private val TEXT = "text/plain".toMediaType()
    }
}
