// AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Partitions (Testing Documentation 2.1.6). When the tip arrives: before or after its guide goes on Camera. Its
// result: a tip, or a failure. The guide on Camera: the prepared one, another (from the gallery or saved), none, the
// same id again after it was removed, and a saved copy with the same id in its place. Asking again: a newer guide
// prepared while the one on Camera keeps its tip, and while an older one is still being asked for. Discarding: the
// prepared guide before it goes on Camera, the guide already on Camera, and an older guide. The screens' language
// changing (FR-10.2): with the tip on Camera ready, with the one prepared still being asked for (its older answer
// arriving late), and with only a guide that has no tip. A newer guide prepared, then discarded, while the tip on
// Camera is still being asked for.
@OptIn(ExperimentalCoroutinesApi::class)
class PoseTipsTest {
    private val tipper = RecordingTipper()
    private val onCamera = MutableStateFlow<ReferenceGuide?>(null)
    private val scene = testBitmap()
    private val candidate = testBitmap()

    private val language = MutableStateFlow(ENGLISH)

    private fun TestScope.tips() = PoseTips(tipper, onCamera, backgroundScope, language)

    @Test
    fun aPreparedGuideBringsItsTipToCamera_preparingUntilItArrives() = runTest {
        val tips = tips()
        val guide = guide("g1")

        tips.prepare(guide, candidate, TipSource(scene, POSE))
        runCurrent()
        onCamera.value = guide
        runCurrent()
        assertEquals(GuideTip("g1", TipState.Preparing), tips.onCamera.value)

        tipper.requests.single().answer(TEXT)
        runCurrent()

        assertEquals(GuideTip("g1", TipState.Ready(PoseTip(TEXT))), tips.onCamera.value)
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

        assertEquals(GuideTip("g1", TipState.Ready(PoseTip(TEXT))), tips.onCamera.value)
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

        assertEquals(GuideTip("g1", TipState.Failed), tips.onCamera.value)
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
        assertEquals(GuideTip("g1", TipState.Ready(PoseTip(TEXT))), tips.onCamera.value)

        onCamera.value = second
        runCurrent()
        assertEquals(GuideTip("g2", TipState.Preparing), tips.onCamera.value)
        tipper.requests[1].answer("Crouch until the phone is at their eye level and keep it level.")
        runCurrent()
        assertEquals(
            GuideTip("g2", TipState.Ready(PoseTip("Crouch until the phone is at their eye level and keep it level."))),
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

    @Test
    fun aSavedCopyOfTheGuideOnCameraHasNoTip_thoughItKeepsTheId() = runTest {
        val tips = tips()
        val generated = guide("g1")
        tips.prepare(generated, candidate, TipSource(scene, POSE))
        onCamera.value = generated
        runCurrent()
        tipper.requests.single().answer(TEXT)
        runCurrent()

        // Saved guides on Camera's sheet: the guide is loaded again with its own id (FR-3.8).
        onCamera.value = guide("g1")
        runCurrent()

        assertNull(tips.onCamera.value)
    }

    @Test
    fun discardingAPreparedGuideStopsItsAsk_andItBringsNoTipToCamera() = runTest {
        val tips = tips()
        val guide = guide("g1")
        tips.prepare(guide, candidate, TipSource(scene, POSE))
        runCurrent()

        tips.discard("g1")
        runCurrent()

        assertTrue(tipper.requests.single().cancelled)
        onCamera.value = guide
        runCurrent()
        assertNull(tips.onCamera.value)
    }

    @Test
    fun discardingTheGuideOnCamera_orAnOlderOne_changesNothing() = runTest {
        val tips = tips()
        val guide = guide("g1")
        tips.prepare(guide, candidate, TipSource(scene, POSE))
        onCamera.value = guide
        runCurrent()

        tips.discard("g1")
        tips.discard("g0")
        runCurrent()

        assertFalse(tipper.requests.single().cancelled)
        assertEquals(GuideTip("g1", TipState.Preparing), tips.onCamera.value)
    }

    private fun guide(id: String, source: GuideSource = GuideSource.GENERATED) =
        ReferenceGuide(id = id, mask = testBitmap(), outline = testBitmap(), aspect = 0.5f, source = source)

    @Test
    fun theTipOnCameraIsAskedAgainWhenTheLanguageChanges() = runTest {
        val tips = tips()
        val guide = guide("g1")
        tips.prepare(guide, candidate, TipSource(scene, POSE))
        onCamera.value = guide
        runCurrent()
        tipper.requests.single().answer(TEXT)
        runCurrent()

        language.value = KOREAN
        runCurrent()

        assertEquals(GuideTip("g1", TipState.Preparing), tips.onCamera.value)
        val again = tipper.requests.last()
        assertEquals(2, tipper.requests.size)
        assertSame(scene, again.scene)
        assertSame(candidate, again.candidate)
        assertEquals(POSE, again.pose)
        again.answer(KOREAN_TEXT)
        runCurrent()
        assertEquals(GuideTip("g1", TipState.Ready(PoseTip(KOREAN_TEXT))), tips.onCamera.value)
    }

    @Test
    fun aTipStillBeingAskedForIsAskedAgain_andItsOlderAnswerIsDropped() = runTest {
        val tips = tips()
        val guide = guide("g1")
        tips.prepare(guide, candidate, TipSource(scene, POSE))
        runCurrent()
        val first = tipper.requests.single()

        language.value = KOREAN
        runCurrent()
        assertTrue(first.cancelled)
        first.answer(TEXT)
        onCamera.value = guide
        runCurrent()
        assertEquals(GuideTip("g1", TipState.Preparing), tips.onCamera.value)

        tipper.requests.last().answer(KOREAN_TEXT)
        runCurrent()
        assertEquals(GuideTip("g1", TipState.Ready(PoseTip(KOREAN_TEXT))), tips.onCamera.value)
    }

    @Test
    fun aGuideWithoutATipAsksNothingWhenTheLanguageChanges() = runTest {
        val tips = tips()
        onCamera.value = guide("g1", GuideSource.GALLERY)
        runCurrent()

        language.value = KOREAN
        runCurrent()

        assertEquals(emptyList<RecordingTipper.Request>(), tipper.requests)
        assertNull(tips.onCamera.value)
    }

    @Test
    fun aNewerGuideKeepsTheAskForTheGuideOnCamera_soItsTipStillArrivesWhenTheNewerIsNotUsed() = runTest {
        val tips = tips()
        val current = guide("g1")
        tips.prepare(current, candidate, TipSource(scene, POSE))
        onCamera.value = current
        runCurrent()
        tipper.requests.single().answer(TEXT)
        runCurrent()
        language.value = KOREAN
        runCurrent()
        val again = tipper.requests.last()

        val newer = guide("g2")
        tips.prepare(newer, candidate, TipSource(scene, POSE))
        runCurrent()
        tips.discard(newer.id)
        runCurrent()

        assertFalse(again.cancelled)
        again.answer(KOREAN_TEXT)
        runCurrent()
        assertEquals(GuideTip("g1", TipState.Ready(PoseTip(KOREAN_TEXT))), tips.onCamera.value)
    }

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
        const val KOREAN_TEXT = "줌을 당기거나 조금 다가가서 가슴 높이에서 수평으로 드세요 - 신체 비율이 유지돼요."
    }
}
