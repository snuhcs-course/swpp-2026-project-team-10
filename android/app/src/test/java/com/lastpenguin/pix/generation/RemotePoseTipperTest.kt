// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.core.network.PoseResponseDto
import com.lastpenguin.pix.core.network.PoseSuggestionsDto
import com.lastpenguin.pix.core.network.PoseTipDto
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

// Partitions (Testing Documentation 2.1.6). Answer: a tip, one with spaces around it, a blank one, a failing status,
// no connection, no answer in time. The caller cancelled.
@OptIn(ExperimentalCoroutinesApi::class)
class RemotePoseTipperTest {
    private val api = FakeApi()
    private val images = FakeImages()
    private val scene = testBitmap()
    private val candidate = testBitmap()

    private fun TestScope.tipper() = RemotePoseTipper(
        api,
        timeoutMs = RemotePoseTipper.TIMEOUT_MS,
        images = images,
        imageDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun theSceneTheCandidateAndThePoseGoOut_andTheSentenceComesBack() = runTest {
        val tip = tipper().tip(scene, candidate, POSE)

        assertEquals(Result.success(PoseTip(TEXT)), tip)
        assertEquals(listOf(scene), images.scenes)
        assertEquals(listOf(candidate), images.candidates)
        val request = api.requests.single()
        assertArrayEquals(SCENE_JPEG, request.scene)
        assertEquals("form-data; name=\"scene\"; filename=\"scene.jpg\"", request.sceneDisposition)
        assertEquals("image/jpeg", request.sceneType)
        assertArrayEquals(CANDIDATE_JPEG, request.candidate)
        assertEquals("form-data; name=\"candidate\"; filename=\"candidate.jpg\"", request.candidateDisposition)
        assertEquals(POSE, request.pose)
    }

    @Test
    fun spacesAroundTheSentenceAreLeftOut() = runTest {
        api.answer = { tipDto("  $TEXT \n") }

        assertEquals(PoseTip(TEXT), tipper().tip(scene, candidate, POSE).getOrThrow())
    }

    @Test
    fun noUsableAnswerIsAFailure() = runTest {
        suspend fun failsWith(expected: Class<out Exception>, answer: suspend () -> PoseTipDto) {
            api.answer = answer
            val error = tipper().tip(scene, candidate, POSE).exceptionOrNull()
            assertTrue("$error", expected.isInstance(error))
        }

        failsWith(IOException::class.java) { tipDto(" ") }
        failsWith(HttpException::class.java) { throw http(502, "UPSTREAM_ERROR") }
        failsWith(HttpException::class.java) { throw http(429, "RATE_LIMITED") }
        failsWith(UnknownHostException::class.java) { throw UnknownHostException("pix.example") }
    }

    @Test
    fun noAnswerInTimeIsAFailureAtTheLimit() = runTest {
        api.answer = { awaitCancellation() }

        val error = tipper().tip(scene, candidate, POSE).exceptionOrNull()

        assertTrue("$error", error is SocketTimeoutException)
        assertEquals(RemotePoseTipper.TIMEOUT_MS, currentTime)
    }

    @Test
    fun cancellingTheCallerCancelsTheRequest() = runTest {
        api.answer = { awaitCancellation() }
        val call = async { tipper().tip(scene, candidate, POSE) }
        runCurrent() // not advanceUntilIdle, which would also run the clock to the time limit

        call.cancel()
        advanceUntilIdle()

        assertTrue(call.isCancelled)
        assertEquals(1, api.requests.size)
    }

    private class Request(
        val scene: ByteArray,
        val sceneType: String?,
        val sceneDisposition: String?,
        val candidate: ByteArray,
        val candidateDisposition: String?,
        val pose: String,
    )

    private class FakeApi : PixApi {
        var answer: suspend () -> PoseTipDto = { tipDto(TEXT) }
        val requests = mutableListOf<Request>()

        override suspend fun poseTip(
            scene: MultipartBody.Part,
            candidate: MultipartBody.Part,
            pose: RequestBody,
        ): PoseTipDto {
            requests += Request(
                scene = scene.bytes(),
                sceneType = scene.body.contentType()?.toString(),
                sceneDisposition = scene.headers?.get("Content-Disposition"),
                candidate = candidate.bytes(),
                candidateDisposition = candidate.headers?.get("Content-Disposition"),
                pose = pose.text(),
            )
            return answer()
        }

        override suspend fun suggestPoses(image: MultipartBody.Part, description: RequestBody?): PoseSuggestionsDto =
            error("A tip never asks for poses")

        override suspend fun createPose(
            image: MultipartBody.Part,
            description: RequestBody,
            seed: RequestBody,
        ): PoseResponseDto = error("A tip never asks for a candidate")

        private fun MultipartBody.Part.bytes(): ByteArray = Buffer().also(body::writeTo).readByteArray()

        private fun RequestBody.text(): String = Buffer().also(::writeTo).readUtf8()
    }

    private class FakeImages : PoseImageCodec {
        val scenes = mutableListOf<Bitmap>()
        val candidates = mutableListOf<Bitmap>()

        override fun encodeScene(scene: Bitmap): ByteArray {
            scenes += scene
            return SCENE_JPEG
        }

        override fun encodeTipCandidate(candidate: Bitmap): ByteArray {
            candidates += candidate
            return CANDIDATE_JPEG
        }

        override fun decodeCandidate(image: ByteArray): Bitmap? = error("A tip decodes no image")
    }

    private companion object {
        const val POSE = "Walk slowly past the railing, look off into the distance"
        const val TEXT = "Zoom in or take a step closer, hold the phone at your chest and keep it level - " +
            "the body keeps its proportions."
        val SCENE_JPEG = byteArrayOf(-1, -40, -1, 1, 2, 3)
        val CANDIDATE_JPEG = byteArrayOf(-1, -40, -1, 4, 5, 6)

        fun tipDto(text: String) = PoseTipDto(
            text,
            framing = "closer",
            hold = "chest",
            height = "keep",
            angle = "level",
        )

        fun http(status: Int, code: String): HttpException {
            val body = """{"error":{"code":"$code","message":"From the server"}}"""
            return HttpException(
                Response.error<PoseTipDto>(status, body.toResponseBody("application/json".toMediaType())),
            )
        }
    }
}
