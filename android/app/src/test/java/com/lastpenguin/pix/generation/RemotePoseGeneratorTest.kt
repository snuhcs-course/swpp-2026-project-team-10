package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.core.network.PoseResponseDto
import com.lastpenguin.pix.core.network.PoseTemplateDto
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RemotePoseGeneratorTest {
    private val api = FakeApi()
    private val images = FakeImages()

    private fun TestScope.generator() = RemotePoseGenerator(
        api,
        timeoutMs = 30_000,
        images = images,
        imageDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun templatesAreFetchedOnce() = runTest {
        val generator = generator()

        assertEquals(FOUR_TEMPLATES, generator.templates())
        assertEquals(FOUR_TEMPLATES, generator.templates())

        assertEquals(1, api.templateFetches)
    }

    @Test
    fun templatesThatDoNotArriveInTimeFailAndAreAskedAgain() = runTest {
        val generator = generator()
        api.templates = { awaitCancellation() }

        val error = runCatching { generator.templates() }.exceptionOrNull()
        assertTrue(error is SocketTimeoutException)
        assertEquals(30_000, currentTime)

        api.templates = { FOUR_TEMPLATES.map { PoseTemplateDto(it.id, it.label) } }
        assertEquals(FOUR_TEMPLATES, generator.templates())
        assertEquals(2, api.templateFetches)
    }

    @Test
    fun everyTemplateIsRequestedWithTheSceneEncodedOnce() = runTest {
        val scene = testBitmap()

        val events = generator().generate(scene, FOUR_TEMPLATES, seed = 7).toList()

        assertEquals(listOf(scene), images.encoded)
        assertEquals(FOUR_TEMPLATES.map { it.id }, api.requests.map { it.templateId })
        for (request in api.requests) {
            assertArrayEquals(SCENE_JPEG, request.image)
            assertEquals("image/jpeg", request.imageType)
            assertEquals("form-data; name=\"image\"; filename=\"scene.jpg\"", request.imageDisposition)
        }
        assertEquals(FOUR_TEMPLATES.map { it.id }.toSet(), events.map { it.templateId }.toSet())
        assertTrue(events.all { it is CandidateEvent.Ready })
        assertEquals(FOUR_TEMPLATES.map { "candidate-${it.id}" }.toSet(), images.decoded.toSet())
    }

    @Test
    fun theSetSeedGivesEachTemplateItsOwnSeed() = runTest {
        val generator = generator()

        generator.generate(testBitmap(), FOUR_TEMPLATES, seed = 7).toList()
        val first = api.requests.map { it.seed }
        api.requests.clear()
        generator.generate(testBitmap(), FOUR_TEMPLATES, seed = 7).toList()
        val again = api.requests.map { it.seed }
        api.requests.clear()
        generator.generate(testBitmap(), FOUR_TEMPLATES, seed = 8).toList()
        val other = api.requests.map { it.seed }

        assertEquals(4, first.toSet().size)
        assertTrue(first.all { it >= 0 })
        assertEquals(first, again)
        assertNotEquals(first, other)
    }

    @Test
    fun candidatesAreReportedAsEachOneArrives() = runTest {
        api.answers["hands_on_hips"] = {
            delay(3_000)
            answer("hands_on_hips")
        }
        api.answers["wave"] = {
            delay(1_000)
            answer("wave")
        }
        api.answers["walking"] = {
            delay(2_000)
            answer("walking")
        }

        val events = generator().generate(testBitmap(), FOUR_TEMPLATES, seed = 7).toList()

        assertEquals(listOf("arms_crossed", "wave", "walking", "hands_on_hips"), events.map { it.templateId })
        // In parallel: the set takes as long as its slowest request.
        assertEquals(3_000, currentTime)
    }

    @Test
    fun failuresBecomeTheErrorTheScreensKnow() = runTest {
        val generator = generator()
        val wave = FOUR_TEMPLATES.filter { it.id == "wave" }

        suspend fun failsWith(expected: GenerationError, answer: suspend () -> PoseResponseDto) {
            api.answers["wave"] = answer
            val events = generator.generate(testBitmap(), wave, seed = 7).toList()
            assertEquals(listOf(CandidateEvent.Failed("wave", expected)), events)
        }

        failsWith(GenerationError.RATE_LIMITED) { throw http(429, "RATE_LIMITED") }
        failsWith(GenerationError.REJECTED) { throw http(422, "REJECTED") }
        failsWith(GenerationError.TIMEOUT) { throw http(504, "UPSTREAM_TIMEOUT") }
        failsWith(GenerationError.SERVER) { throw http(502, "UPSTREAM_ERROR") }
        // A photo the server rejects is the app's mistake, not something the user can fix.
        failsWith(GenerationError.SERVER) { throw http(400, "INVALID_IMAGE") }
        failsWith(GenerationError.SERVER) {
            throw HttpException(Response.error<PoseResponseDto>(413, "Content Too Large".toResponseBody(null)))
        }
        failsWith(GenerationError.OFFLINE) { throw UnknownHostException("pix.example") }
        failsWith(GenerationError.OFFLINE) { throw IOException("Connection reset") }
        failsWith(GenerationError.TIMEOUT) { throw SocketTimeoutException("timeout") }
        failsWith(GenerationError.SERVER) { PoseResponseDto("wave", "not base64!", elapsedMs = 1) }
        failsWith(GenerationError.SERVER) { answer("wave", image = "not an image") }
    }

    @Test
    fun aRequestWithoutAnAnswerTimesOutWhileTheOthersArrive() = runTest {
        api.answers["walking"] = { awaitCancellation() }

        val events = generator().generate(testBitmap(), FOUR_TEMPLATES, seed = 7).toList()

        assertEquals(CandidateEvent.Failed("walking", GenerationError.TIMEOUT), events.last())
        assertEquals(3, events.count { it is CandidateEvent.Ready })
        assertEquals(30_000, currentTime)
        assertEquals(listOf("walking"), api.cancelled)
    }

    @Test
    fun cancellingTheCollectorCancelsTheRequests() = runTest {
        for (template in FOUR_TEMPLATES) api.answers[template.id] = { awaitCancellation() }
        val events = mutableListOf<CandidateEvent>()

        val collecting = launch { generator().generate(testBitmap(), FOUR_TEMPLATES, seed = 7).toList(events) }
        runCurrent()
        assertEquals(4, api.requests.size)
        collecting.cancel()
        runCurrent()

        assertEquals(FOUR_TEMPLATES.map { it.id }.toSet(), api.cancelled.toSet())
        assertTrue(events.isEmpty())
    }

    private class Request(
        val templateId: String,
        val seed: Long,
        val image: ByteArray,
        val imageType: String?,
        val imageDisposition: String?,
    )

    private class FakeApi : PixApi {
        var templateFetches = 0
        var templates: suspend () -> List<PoseTemplateDto> = { FOUR_TEMPLATES.map { PoseTemplateDto(it.id, it.label) } }
        val requests = mutableListOf<Request>()

        /** What a template's request does; without an entry it answers at once with a valid image. */
        val answers = mutableMapOf<String, suspend () -> PoseResponseDto>()
        val cancelled = mutableListOf<String>()

        override suspend fun poseTemplates(): List<PoseTemplateDto> {
            templateFetches++
            return templates()
        }

        override suspend fun createPose(
            image: MultipartBody.Part,
            templateId: RequestBody,
            seed: RequestBody,
        ): PoseResponseDto {
            val id = templateId.text()
            requests += Request(
                templateId = id,
                seed = seed.text().toLong(),
                image = Buffer().also(image.body::writeTo).readByteArray(),
                imageType = image.body.contentType()?.toString(),
                imageDisposition = image.headers?.get("Content-Disposition"),
            )
            try {
                return answers[id]?.invoke() ?: answer(id)
            } catch (error: CancellationException) {
                cancelled += id
                throw error
            }
        }

        private fun RequestBody.text(): String = Buffer().also(::writeTo).readUtf8()
    }

    private class FakeImages : PoseImageCodec {
        val encoded = mutableListOf<Bitmap>()
        val decoded = mutableListOf<String>()

        override fun encodeScene(scene: Bitmap): ByteArray {
            encoded += scene
            return SCENE_JPEG
        }

        override fun decodeCandidate(image: ByteArray): Bitmap? {
            val text = String(image)
            if (text == "not an image") return null
            decoded += text
            return testBitmap()
        }
    }

    private companion object {
        val SCENE_JPEG = byteArrayOf(-1, -40, -1, 1, 2, 3)

        fun answer(templateId: String, image: String = "candidate-$templateId") =
            PoseResponseDto(templateId, Base64.getEncoder().encodeToString(image.toByteArray()), elapsedMs = 1_000)

        fun http(status: Int, code: String): HttpException {
            val body = """{"error":{"code":"$code","message":"From the server"}}"""
            return HttpException(
                Response.error<PoseResponseDto>(status, body.toResponseBody("application/json".toMediaType())),
            )
        }
    }
}
