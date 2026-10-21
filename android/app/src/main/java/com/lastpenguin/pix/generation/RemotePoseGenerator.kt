// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import android.util.Log
import com.lastpenguin.pix.core.network.ApiErrorBody
import com.lastpenguin.pix.core.network.PixApi
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * [PoseGenerator] through the Pix server (Design 2.6.3): `POST /pose-suggestions` for the poses, which are four
 * suggested ones or, for a description, the description and three variations of it; then one `POST /poses` per pose,
 * in parallel, each with the pose as its description. The description's own request goes out at once, before its
 * variations are known.
 * Owner: Server/AI/Sync (#7).
 */
class RemotePoseGenerator(
    private val api: PixApi,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val images: PoseImageCodec = AndroidPoseImageCodec,
    /** Image encoding and decoding run here, off the main thread (Design 2.8, Concurrency). */
    private val imageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PoseGenerator {

    override fun generate(scene: Bitmap, request: PoseRequest, seed: Long): Flow<GenerationEvent> = channelFlow {
        // Encoded once and sent with every request.
        val photo = withContext(imageDispatcher) { images.encodeScene(scene) }
        val imagePart = MultipartBody.Part.createFormData("image", "scene.jpg", photo.toRequestBody(JPEG))
        // One seed for the set gives each pose a seed of its own, in the order of the poses (Design 2.6.3, step 2).
        val seeds = Random(seed)
        var written: Deferred<CandidateEvent>? = null
        val texts = when (request) {
            is PoseRequest.Described -> {
                // The description as written goes out at once, so its candidate does not wait for the variations.
                val writtenSeed = seeds.nextLong(0, Long.MAX_VALUE)
                written = async { request(imagePart, PlannedPose("pose-1", request.text), writtenSeed) }
                listOf(request.text) + vary(imagePart, request.text)
            }

            PoseRequest.Suggested -> suggest(imagePart)
        }
        val poses = texts.mapIndexed { index, text -> PlannedPose("pose-${index + 1}", text) }
        send(GenerationEvent.Planned(poses))
        // Each request reports as soon as it ends, but never before the poses are known; cancelling the collector
        // cancels the HTTP calls.
        written?.let { launch { send(it.await()) } }
        for (pose in poses.drop(if (written != null) 1 else 0)) {
            val poseSeed = seeds.nextLong(0, Long.MAX_VALUE)
            launch { send(request(imagePart, pose, poseSeed)) }
        }
    }

    /**
     * The server's poses for the scene; it answers with its default poses when its model fails, so a failure here is
     * the connection or the server itself. Throws an [IOException] or an [HttpException].
     */
    private suspend fun suggest(image: MultipartBody.Part): List<String> {
        val answer = try {
            withTimeoutOrNull(timeoutMs) { api.suggestPoses(image, null) }
                ?: throw SocketTimeoutException("The server did not suggest poses in time")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "suggestions: ${describe(error)}")
            throw error
        }
        val poses = answer.poses.filter { it.isNotBlank() }.take(POSE_COUNT)
        if (poses.isEmpty()) throw IOException("The server suggested no poses")
        return poses
    }

    /**
     * Three variations of [description] for the scene: the server's answer after the description itself. The
     * description's own request is already on its way, so nothing here ends the run: for any variation the server
     * does not give in [VARIATION_TIMEOUT_MS], or at all, the description is sent again.
     */
    private suspend fun vary(image: MultipartBody.Part, description: String): List<String> {
        val wanted = POSE_COUNT - 1
        val variations = try {
            val answer = withTimeoutOrNull(VARIATION_TIMEOUT_MS) {
                api.suggestPoses(image, description.toRequestBody(TEXT))
            }
            if (answer == null) Log.w(TAG, "variations: no answer within $VARIATION_TIMEOUT_MS ms")
            answer?.poses.orEmpty().drop(1).filter { it.isNotBlank() }.take(wanted)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "variations: ${describe(error)}")
            emptyList()
        }
        return variations + List(wanted - variations.size) { description }
    }

    private suspend fun request(image: MultipartBody.Part, pose: PlannedPose, seed: Long): CandidateEvent {
        val event = withTimeoutOrNull(timeoutMs) {
            try {
                val answer = api.createPose(image, pose.text.toRequestBody(TEXT), seed.toString().toRequestBody(TEXT))
                val candidate = withContext(imageDispatcher) {
                    images.decodeCandidate(Base64.getDecoder().decode(answer.image))
                }
                if (candidate != null) {
                    CandidateEvent.Ready(pose.id, candidate)
                } else {
                    Log.w(TAG, "${pose.id}: the server's image could not be decoded")
                    CandidateEvent.Failed(pose.id, GenerationError.SERVER)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The pose itself stays out of the log: it can be the user's own words.
                Log.w(TAG, "${pose.id}: ${describe(error)}")
                CandidateEvent.Failed(pose.id, errorOf(error))
            }
        }
        return event ?: CandidateEvent.Failed(pose.id, GenerationError.TIMEOUT).also {
            Log.w(TAG, "${pose.id}: no answer within $timeoutMs ms")
        }
    }

    /** The statuses of Design 2.5.3; anything else from the server, such as a rejected photo, is a server error. */
    private fun errorOf(error: Exception): GenerationError = when (error) {
        is HttpException -> when (error.code()) {
            429 -> GenerationError.RATE_LIMITED
            422 -> GenerationError.REJECTED
            504 -> GenerationError.TIMEOUT
            else -> GenerationError.SERVER
        }

        is SocketTimeoutException -> GenerationError.TIMEOUT

        is IOException -> GenerationError.OFFLINE

        // An answer that is not the agreed JSON or Base64.
        else -> GenerationError.SERVER
    }

    /** For logcat: the status with the server's error code and message, which name a rejected photo's problem. */
    private fun describe(error: Exception): String {
        if (error !is HttpException) return error.toString()
        val body = runCatching {
            error.response()?.errorBody()?.string()?.let { errorJson.decodeFromString<ApiErrorBody>(it) }
        }.getOrNull()
        return listOfNotNull("HTTP ${error.code()}", body?.error?.code, body?.error?.message).joinToString(" ")
    }

    companion object {
        /** Requests stop at 30 s (NFR-4). The HTTP client must wait longer than this, so that this limit decides. */
        const val TIMEOUT_MS = 30_000L

        /** The server gives up on its model after 8 s and answers anyway; past this the description is repeated. */
        const val VARIATION_TIMEOUT_MS = 10_000L

        private const val TAG = "PixPoses"
        private val JPEG = "image/jpeg".toMediaType()
        private val TEXT = "text/plain".toMediaType()
        private val errorJson = Json { ignoreUnknownKeys = true }
    }
}
