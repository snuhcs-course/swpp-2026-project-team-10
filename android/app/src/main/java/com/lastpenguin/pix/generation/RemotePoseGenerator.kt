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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * [PoseGenerator] through the Pix server: one `POST /poses` per template, in parallel (Design 2.6.3).
 * Owner: Server/AI/Sync (#7).
 */
class RemotePoseGenerator(
    private val api: PixApi,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val images: PoseImageCodec = AndroidPoseImageCodec,
    /** Image encoding and decoding run here, off the main thread (Design 2.8, Concurrency). */
    private val imageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PoseGenerator {

    private val templatesLock = Mutex()
    private var cachedTemplates: List<PoseTemplate>? = null

    /** Asked once per app process (Design 2.8, Caching). Throws an [IOException] when the server cannot be reached. */
    override suspend fun templates(): List<PoseTemplate> = templatesLock.withLock {
        cachedTemplates ?: fetchTemplates().also { cachedTemplates = it }
    }

    private suspend fun fetchTemplates(): List<PoseTemplate> {
        val answer = withTimeoutOrNull(timeoutMs) { api.poseTemplates() }
            ?: throw SocketTimeoutException("The server did not send the pose templates in time")
        return answer.map { PoseTemplate(it.id, it.label) }
    }

    override fun generate(scene: Bitmap, templates: List<PoseTemplate>, seed: Long): Flow<CandidateEvent> =
        channelFlow {
            // Encoded once and sent with every request.
            val photo = withContext(imageDispatcher) { images.encodeScene(scene) }
            val imagePart = MultipartBody.Part.createFormData("image", "scene.jpg", photo.toRequestBody(JPEG))
            // One seed for the set gives each template a seed of its own (Design 2.6.3, step 2).
            val seeds = Random(seed)
            for (template in templates) {
                val templateSeed = seeds.nextLong(0, Long.MAX_VALUE)
                // Each request reports as soon as it ends; cancelling the collector cancels the HTTP calls.
                launch { send(request(imagePart, template, templateSeed)) }
            }
        }

    private suspend fun request(image: MultipartBody.Part, template: PoseTemplate, seed: Long): CandidateEvent {
        val event = withTimeoutOrNull(timeoutMs) {
            try {
                val answer = api.createPose(image, template.id.toRequestBody(TEXT), seed.toString().toRequestBody(TEXT))
                val candidate = withContext(imageDispatcher) {
                    images.decodeCandidate(Base64.getDecoder().decode(answer.image))
                }
                if (candidate != null) {
                    CandidateEvent.Ready(template.id, candidate)
                } else {
                    Log.w(TAG, "${template.id}: the server's image could not be decoded")
                    CandidateEvent.Failed(template.id, GenerationError.SERVER)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "${template.id}: ${describe(error)}")
                CandidateEvent.Failed(template.id, errorOf(error))
            }
        }
        return event ?: CandidateEvent.Failed(template.id, GenerationError.TIMEOUT).also {
            Log.w(TAG, "${template.id}: no answer within $timeoutMs ms")
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

        private const val TAG = "PixPoses"
        private val JPEG = "image/jpeg".toMediaType()
        private val TEXT = "text/plain".toMediaType()
        private val errorJson = Json { ignoreUnknownKeys = true }
    }
}
