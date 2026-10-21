// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.core.network.PixApi
import com.lastpenguin.pix.core.network.PoseResponseDto
import com.lastpenguin.pix.core.network.PoseSuggestionsDto
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

// Partitions (Testing Documentation 2.1.6). Request: a description (Korean), and suggestions. Suggestions: four, more
// than four, some blank, none, a failing status, no connection, no answer in time. Variations of a description: three,
// more, fewer, blank, none, a failing status, no connection, no answer in time, and slower than the description's own
// request. Each pose: an image, each failing status, no connection, no answer in time, an answer that is not an
// image. The collector cancelled.
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
    fun aDescriptionIsSentWithThreeVariationsAndTheSceneEncodedOnce() = runTest {
        val scene = testBitmap()

        val events = generator().generate(scene, PoseRequest.Described(DESCRIPTION), seed = 7).toList()

        assertEquals(listOf(scene), images.encoded)
        assertEquals(listOf<String?>(DESCRIPTION), api.suggestionDescriptions)
        assertEquals("text/plain; charset=utf-8", api.suggestionDescriptionType)
        assertArrayEquals(SCENE_JPEG, api.suggestionImage)
        assertEquals(GenerationEvent.Planned(planned(listOf(DESCRIPTION) + VARIATIONS)), events.first())
        assertEquals(listOf(DESCRIPTION) + VARIATIONS, api.requests.map { it.description })
        for (request in api.requests) {
            assertArrayEquals(SCENE_JPEG, request.image)
            assertEquals("image/jpeg", request.imageType)
            assertEquals("form-data; name=\"image\"; filename=\"scene.jpg\"", request.imageDisposition)
            // Korean reaches the server as UTF-8.
            assertEquals("text/plain; charset=utf-8", request.descriptionType)
        }
        val candidates = events.drop(1)
        assertEquals(
            setOf("pose-1", "pose-2", "pose-3", "pose-4"),
            candidates.map {
                (it as CandidateEvent).poseId
            }.toSet(),
        )
        assertTrue(candidates.all { it is CandidateEvent.Ready })
    }

    @Test
    fun theDescriptionGoesOutAtOnceAndIsReportedOnlyOnceThePosesAreKnown() = runTest {
        api.suggestions = {
            delay(2_000)
            PoseSuggestionsDto(listOf(DESCRIPTION) + VARIATIONS)
        }
        val events = mutableListOf<GenerationEvent>()

        val collecting = launch {
            generator().generate(testBitmap(), PoseRequest.Described(DESCRIPTION), seed = 7).toList(events)
        }
        runCurrent()
        // Its candidate is back already, but the screens need the poses first.
        assertEquals(listOf(DESCRIPTION), api.requests.map { it.description })
        assertTrue(events.isEmpty())
        advanceUntilIdle()
        collecting.join()

        assertEquals(GenerationEvent.Planned(planned(listOf(DESCRIPTION) + VARIATIONS)), events.first())
        assertEquals(4, events.count { it is CandidateEvent.Ready })
        assertEquals(2_000, currentTime)
    }

    @Test
    fun withoutVariationsTheDescriptionIsSentAgain() = runTest {
        val generator = generator()

        suspend fun repeats(answer: suspend () -> PoseSuggestionsDto, kept: List<String> = emptyList()) {
            api.suggestions = { answer() }
            api.requests.clear()
            val events = generator.generate(testBitmap(), PoseRequest.Described(DESCRIPTION), seed = 7).toList()
            val texts = listOf(DESCRIPTION) + kept + List(3 - kept.size) { DESCRIPTION }
            assertEquals(GenerationEvent.Planned(planned(texts)), events.first())
            assertEquals(texts, api.requests.map { it.description })
            assertEquals(4, events.count { it is CandidateEvent.Ready })
        }

        repeats({ throw http(429, "RATE_LIMITED") })
        repeats({ throw UnknownHostException("pix.example") })
        repeats({ PoseSuggestionsDto(emptyList()) })
        repeats({ PoseSuggestionsDto(listOf(DESCRIPTION)) })
        repeats({ PoseSuggestionsDto(listOf(DESCRIPTION, " ", VARIATIONS[0], "")) }, kept = VARIATIONS.take(1))
        repeats({ PoseSuggestionsDto(listOf(DESCRIPTION) + VARIATIONS + "Jump") }, kept = VARIATIONS)
    }

    @Test
    fun variationsWithoutAnAnswerInTimeAreReplacedByTheDescription() = runTest {
        api.suggestions = { awaitCancellation() }

        val events = generator().generate(testBitmap(), PoseRequest.Described(DESCRIPTION), seed = 7).toList()

        assertEquals(GenerationEvent.Planned(planned(List(4) { DESCRIPTION })), events.first())
        assertEquals(RemotePoseGenerator.VARIATION_TIMEOUT_MS, currentTime)
        assertEquals(4, events.count { it is CandidateEvent.Ready })
    }

    @Test
    fun withoutADescriptionTheServerSuggestsThePosesFirst() = runTest {
        val events = generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList()

        assertEquals(listOf<String?>(null), api.suggestionDescriptions)
        assertArrayEquals(SCENE_JPEG, api.suggestionImage)
        assertEquals(1, images.encoded.size)
        assertEquals(GenerationEvent.Planned(planned(SUGGESTED)), events.first())
        assertEquals(SUGGESTED, api.requests.map { it.description })
        assertEquals(SUGGESTED.map { "candidate-$it" }.toSet(), images.decoded.toSet())
    }

    @Test
    fun onlyTheFirstFourSuggestionsThatAreNotBlankAreUsed() = runTest {
        api.suggestions =
            { PoseSuggestionsDto(listOf(" ", SUGGESTED[0], "", *SUGGESTED.drop(1).toTypedArray(), "Jump")) }

        val events = generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList()

        assertEquals(GenerationEvent.Planned(planned(SUGGESTED)), events.first())
    }

    @Test
    fun fewerSuggestionsMakeASmallerSet() = runTest {
        api.suggestions = { PoseSuggestionsDto(SUGGESTED.take(2)) }

        val events = generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList()

        assertEquals(GenerationEvent.Planned(planned(SUGGESTED.take(2))), events.first())
        assertEquals(2, api.requests.size)
    }

    @Test
    fun suggestionsThatCannotBeHadEndTheRunWithoutAPoseRequest() = runTest {
        val generator = generator()

        suspend fun failsWith(expected: Class<out Exception>, answer: suspend () -> PoseSuggestionsDto) {
            api.suggestions = { answer() }
            val error = runCatching { generator.generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList() }
                .exceptionOrNull()
            assertTrue("$error", expected.isInstance(error))
        }

        failsWith(HttpException::class.java) { throw http(429, "RATE_LIMITED") }
        failsWith(HttpException::class.java) { throw http(400, "INVALID_IMAGE") }
        failsWith(UnknownHostException::class.java) { throw UnknownHostException("pix.example") }
        failsWith(IOException::class.java) { PoseSuggestionsDto(emptyList()) }
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun suggestionsWithoutAnAnswerInTimeTimeOut() = runTest {
        api.suggestions = { awaitCancellation() }

        val error = runCatching { generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList() }
            .exceptionOrNull()

        assertTrue("$error", error is SocketTimeoutException)
        assertEquals(30_000, currentTime)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun theSetSeedGivesEachPoseItsOwnSeed() = runTest {
        val generator = generator()
        val request = PoseRequest.Described(DESCRIPTION)

        generator.generate(testBitmap(), request, seed = 7).toList()
        val first = api.requests.map { it.seed }
        api.requests.clear()
        generator.generate(testBitmap(), request, seed = 7).toList()
        val again = api.requests.map { it.seed }
        api.requests.clear()
        generator.generate(testBitmap(), request, seed = 8).toList()
        val other = api.requests.map { it.seed }

        assertEquals(4, first.toSet().size)
        assertTrue(first.all { it >= 0 })
        assertEquals(first, again)
        assertNotEquals(first, other)
    }

    @Test
    fun candidatesAreReportedAsEachOneArrives() = runTest {
        api.answers[SUGGESTED[0]] = {
            delay(3_000)
            answer(SUGGESTED[0])
        }
        api.answers[SUGGESTED[1]] = {
            delay(1_000)
            answer(SUGGESTED[1])
        }
        api.answers[SUGGESTED[2]] = {
            delay(2_000)
            answer(SUGGESTED[2])
        }

        val events = generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList()

        val order = events.filterIsInstance<CandidateEvent>().map { it.poseId }
        assertEquals(listOf("pose-4", "pose-2", "pose-3", "pose-1"), order)
        // In parallel: the set takes as long as its slowest request.
        assertEquals(3_000, currentTime)
    }

    @Test
    fun failuresBecomeTheErrorTheScreensKnow() = runTest {
        val generator = generator()
        // Without variations all four requests send the description, so each answers as set below.
        api.suggestions = { PoseSuggestionsDto(emptyList()) }

        suspend fun failsWith(expected: GenerationError, answer: suspend () -> PoseResponseDto) {
            api.answers[DESCRIPTION] = answer
            val events = generator.generate(testBitmap(), PoseRequest.Described(DESCRIPTION), seed = 7).toList()
            val errors = events.filterIsInstance<CandidateEvent.Failed>().map { it.error }
            assertEquals(List(4) { expected }, errors)
        }

        failsWith(GenerationError.RATE_LIMITED) { throw http(429, "RATE_LIMITED") }
        failsWith(GenerationError.REJECTED) { throw http(422, "REJECTED") }
        failsWith(GenerationError.TIMEOUT) { throw http(504, "UPSTREAM_TIMEOUT") }
        failsWith(GenerationError.SERVER) { throw http(502, "UPSTREAM_ERROR") }
        // A photo or a description the server refuses is the app's mistake, not something the user can fix.
        failsWith(GenerationError.SERVER) { throw http(400, "INVALID_IMAGE") }
        failsWith(GenerationError.SERVER) { throw http(400, "INVALID_REQUEST") }
        failsWith(GenerationError.SERVER) {
            throw HttpException(Response.error<PoseResponseDto>(413, "Content Too Large".toResponseBody(null)))
        }
        failsWith(GenerationError.OFFLINE) { throw UnknownHostException("pix.example") }
        failsWith(GenerationError.OFFLINE) { throw IOException("Connection reset") }
        failsWith(GenerationError.TIMEOUT) { throw SocketTimeoutException("timeout") }
        failsWith(GenerationError.SERVER) { PoseResponseDto(null, "not base64!", elapsedMs = 1) }
        failsWith(GenerationError.SERVER) { answer(DESCRIPTION, image = "not an image") }
    }

    @Test
    fun aRequestWithoutAnAnswerTimesOutWhileTheOthersArrive() = runTest {
        api.answers[SUGGESTED[2]] = { awaitCancellation() }

        val events = generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList()

        assertEquals(CandidateEvent.Failed("pose-3", GenerationError.TIMEOUT), events.last())
        assertEquals(3, events.count { it is CandidateEvent.Ready })
        assertEquals(30_000, currentTime)
        assertEquals(listOf(SUGGESTED[2]), api.cancelled)
    }

    @Test
    fun cancellingTheCollectorCancelsTheRequests() = runTest {
        for (pose in SUGGESTED) api.answers[pose] = { awaitCancellation() }
        val events = mutableListOf<GenerationEvent>()

        val collecting = launch { generator().generate(testBitmap(), PoseRequest.Suggested, seed = 7).toList(events) }
        runCurrent()
        assertEquals(4, api.requests.size)
        collecting.cancel()
        runCurrent()

        assertEquals(SUGGESTED.toSet(), api.cancelled.toSet())
        assertEquals(listOf(GenerationEvent.Planned(planned(SUGGESTED))), events)
    }

    private class Request(
        val description: String,
        val descriptionType: String?,
        val seed: Long,
        val image: ByteArray,
        val imageType: String?,
        val imageDisposition: String?,
    )

    private class FakeApi : PixApi {
        /** The server's answer, by the description sent: suggested poses, or the description and its variations. */
        var suggestions: suspend (String?) -> PoseSuggestionsDto = { description ->
            PoseSuggestionsDto(if (description == null) SUGGESTED else listOf(description) + VARIATIONS)
        }
        val suggestionDescriptions = mutableListOf<String?>()
        var suggestionDescriptionType: String? = null
        var suggestionImage: ByteArray? = null
        val requests = mutableListOf<Request>()

        /** What a pose's request does, by its description; without an entry it answers at once with an image. */
        val answers = mutableMapOf<String, suspend () -> PoseResponseDto>()
        val cancelled = mutableListOf<String>()

        override suspend fun suggestPoses(image: MultipartBody.Part, description: RequestBody?): PoseSuggestionsDto {
            val text = description?.text()
            suggestionDescriptions += text
            suggestionDescriptionType = description?.contentType()?.toString()
            suggestionImage = Buffer().also(image.body::writeTo).readByteArray()
            return suggestions(text)
        }

        override suspend fun createPose(
            image: MultipartBody.Part,
            description: RequestBody,
            seed: RequestBody,
        ): PoseResponseDto {
            val text = description.text()
            requests += Request(
                description = text,
                descriptionType = description.contentType()?.toString(),
                seed = seed.text().toLong(),
                image = Buffer().also(image.body::writeTo).readByteArray(),
                imageType = image.body.contentType()?.toString(),
                imageDisposition = image.headers?.get("Content-Disposition"),
            )
            try {
                return answers[text]?.invoke() ?: answer(text)
            } catch (error: CancellationException) {
                cancelled += text
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
        const val DESCRIPTION = "벽에 기대서 카메라 보며 살짝 웃기"
        val VARIATIONS = listOf(
            "Lean on the wall, arms crossed, look at the camera, smile slightly",
            "Shoulder on the wall, one hand in a pocket, slight smile at the camera",
            "Back to the wall, hands behind it, look at the camera with a slight smile",
        )
        val SCENE_JPEG = byteArrayOf(-1, -40, -1, 1, 2, 3)

        fun answer(description: String, image: String = "candidate-$description") =
            PoseResponseDto(null, Base64.getEncoder().encodeToString(image.toByteArray()), elapsedMs = 1_000)

        fun http(status: Int, code: String): HttpException {
            val body = """{"error":{"code":"$code","message":"From the server"}}"""
            return HttpException(
                Response.error<PoseResponseDto>(status, body.toResponseBody("application/json".toMediaType())),
            )
        }
    }
}
