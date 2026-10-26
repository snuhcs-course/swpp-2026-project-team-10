// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.ReferenceGuide
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Partitions (Testing Documentation 2.1.6). When the tip arrives: before or after its guide goes on Camera. Its
// result: a tip, or a failure. The guide on Camera: the prepared one, another (from the gallery or saved), none, and
// the same id again after it was removed. Asking again: a newer guide prepared while the one on Camera keeps its tip,
// and while an older one is still being asked for.
@OptIn(ExperimentalCoroutinesApi::class)
class PoseTipsTest {
    private val tipper = RecordingTipper()
    private val onCamera = MutableStateFlow<ReferenceGuide?>(null)
    private val scene = testBitmap()
    private val candidate = testBitmap()

    private fun TestScope.tips() = PoseTips(tipper, onCamera, backgroundScope)

    @Test
    fun aPreparedGuideBringsItsTipToCamera_preparingUntilItArrives() = runTest {
        val tips = tips()
        val guide = guide("g1")

        tips.prepare(guide, candidate, TipSource(scene, POSE))
        runCurrent()
        onCamera.value = guide
        runCurrent()
        assertEquals(TipState.Preparing, tips.onCamera.value)

        tipper.requests.single().answer(TEXT)
        runCurrent()

        assertEquals(TipState.Ready(PoseTip(TEXT)), tips.onCamera.value)
        val request = tipper.requests.single()
        assertSame(scene, request.scene)
        assertSame(candidate, request.candidate)
        assertEquals(POSE, request.pose)
    }

    @Test
    fun aTipThatArrivedBeforeItsGuideIsThereWhenTheGuideGoesOnCamera() = runTest {
        val tips = tips()
        val guide = guide("g1")
        tips.prepare(guide, candidate, TipSource(scene, POSE))
        runCurrent()
        tipper.requests.single().answer(TEXT)
        runCurrent()
        assertNull(tips.onCamera.value)

        onCamera.value = guide
        runCurrent()

        assertEquals(TipState.Ready(PoseTip(TEXT)), tips.onCamera.value)
    }

    @Test
    fun aTipThatCannotBeHadIsAFailureOnCamera() = runTest {
        val tips = tips()
        val guide = guide("g1")
        tips.prepare(guide, candidate, TipSource(scene, POSE))
        onCamera.value = guide
        runCurrent()

        tipper.requests.single().fail()
        runCurrent()

        assertEquals(TipState.Failed, tips.onCamera.value)
    }

    @Test
    fun aGuideFromTheGalleryHasNoTip_thoughATipWasPreparedForAnother() = runTest {
        val tips = tips()
        tips.prepare(guide("g1"), candidate, TipSource(scene, POSE))
        runCurrent()
        tipper.requests.single().answer(TEXT)

        // Reference confirm left without using it; a photo from the gallery goes on Camera instead.
        onCamera.value = guide("gallery", source = GuideSource.GALLERY)
        runCurrent()

        assertNull(tips.onCamera.value)
    }

    @Test
    fun aRemovedGuideLosesItsTip_evenWhenItComesBackFromTheSavedGuides() = runTest {
        val tips = tips()
        val generated = guide("g1")
        tips.prepare(generated, candidate, TipSource(scene, POSE))
        onCamera.value = generated
        runCurrent()
        tipper.requests.single().answer(TEXT)
        runCurrent()

        onCamera.value = null
        runCurrent()
        assertNull(tips.onCamera.value)
        // A saved guide keeps its id (FR-3.8), but not its tip, which was for the scene it was made in.
        onCamera.value = generated
        runCurrent()
        assertNull(tips.onCamera.value)
    }

    @Test
    fun theGuideOnCameraKeepsItsTipWhileANewerOneIsPrepared_untilTheNewerTakesItsPlace() = runTest {
        val tips = tips()
        val first = guide("g1")
        tips.prepare(first, candidate, TipSource(scene, POSE))
        onCamera.value = first
        runCurrent()
        tipper.requests[0].answer(TEXT)
        runCurrent()

        val second = guide("g2")
        tips.prepare(second, candidate, TipSource(scene, "Sit on the step"))
        runCurrent()
        assertEquals(TipState.Ready(PoseTip(TEXT)), tips.onCamera.value)

        onCamera.value = second
        runCurrent()
        assertEquals(TipState.Preparing, tips.onCamera.value)
        tipper.requests[1].answer("Crouch until the phone is at their eye level and keep it level.")
        runCurrent()
        assertEquals(
            TipState.Ready(PoseTip("Crouch until the phone is at their eye level and keep it level.")),
            tips.onCamera.value,
        )
    }

    @Test
    fun aNewerGuideStopsTheAskForAnOlderOne() = runTest {
        val tips = tips()
        val older = guide("g1")
        tips.prepare(older, candidate, TipSource(scene, POSE))
        runCurrent()
        val newer = guide("g2")

        tips.prepare(newer, candidate, TipSource(scene, POSE))
        runCurrent()

        assertTrue(tipper.requests[0].cancelled)
        onCamera.value = older
        runCurrent()
        assertNull(tips.onCamera.value)
    }

    private fun guide(id: String, source: GuideSource = GuideSource.GENERATED) =
        ReferenceGuide(id = id, cutout = testBitmap(), outline = testBitmap(), aspect = 0.5f, source = source)

    private class RecordingTipper : PoseTipper {
        val requests = mutableListOf<Request>()

        override suspend fun tip(scene: Bitmap, candidate: Bitmap, pose: String): Result<PoseTip> {
            val request = Request(scene, candidate, pose).also(requests::add)
            try {
                return request.result.await()
            } catch (error: CancellationException) {
                request.cancelled = true
                throw error
            }
        }

        class Request(val scene: Bitmap, val candidate: Bitmap, val pose: String) {
            val result = CompletableDeferred<Result<PoseTip>>()
            var cancelled = false

            fun answer(text: String) = result.complete(Result.success(PoseTip(text)))

            fun fail() = result.complete(Result.failure(IOException("No tip")))
        }
    }

    private companion object {
        const val POSE = "Walk slowly past the railing, look off into the distance"
        const val TEXT = "Zoom in or take a step closer, hold the phone at your chest and keep it level."
    }
}
