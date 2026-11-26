// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.net.Uri
import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.generation.poseGuideId
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.protocol.PoseSetStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * What each phone keeps of the other's guides in a session (#297, FR-3.11), plain JVM on the in-memory store, the
 * real PoseLibrary over a maker that cuts at once, and the guide repositories.
 * Photographer: a guide adopted from the subject (kept with its reference photo); an id that is not the camera's guide
 * (nothing). Subject: a guide on its copy of the camera, its photo arriving after it (kept once the photo is here, not
 * before), one with the photo already (kept at once), one that leaves the video before a photo comes (kept as it was),
 * and another guide's photo (not taken for it). A guide already in the library (left as it is, favorite or not) and one
 * deleted after it was kept (not kept again). The photographer's candidates (each cut out and kept once, under the id
 * the photographer's phone gives it; a later copy of the set asks again only for its new candidate).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionGuideKeeperTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = SavedGuideStore.InMemory()
    private val maker = CuttingMaker()
    private val guides = InMemoryGuideRepository()
    private val adopted = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val mirror = InMemoryGuideRepository()
    private val references = MutableStateFlow<GuideReference?>(null)
    private val poses = MutableStateFlow<MirrorPoseSet?>(null)

    private fun TestScope.started() {
        val scope = CoroutineScope(dispatcher)
        SessionGuideKeeper(store, PoseLibrary(maker, store, scope), guides, adopted, mirror, references, poses)
            .start(backgroundScope)
        runCurrent()
    }

    private suspend fun kept(id: String): ReferenceGuide = store.load(id).getOrThrow().first

    @Test
    fun `the photographer keeps a guide adopted from the subject, with its reference photo`() = runTest(dispatcher) {
        started()
        val photo = fakeBitmap()
        guides.setGuide(fakeGuide("s1").copy(reference = photo))

        adopted.emit("other")
        runCurrent()
        assertTrue(store.saved.value.isEmpty())
        adopted.emit("s1")
        runCurrent()

        assertEquals(listOf("s1" to false), store.saved.value.map { it.id to it.favorite })
        assertSame(photo, kept("s1").reference)
    }

    @Test
    fun `the subject keeps the photographer's guide once its reference photo is here`() = runTest(dispatcher) {
        started()
        mirror.setGuide(fakeGuide("p1"))
        runCurrent()
        // Another guide's photo is not this one's.
        references.value = GuideReference("p0", fakeBitmap())
        runCurrent()
        assertTrue(store.saved.value.isEmpty())

        val photo = fakeBitmap()
        references.value = GuideReference("p1", photo)
        runCurrent()

        assertEquals(listOf("p1" to false), store.saved.value.map { it.id to it.favorite })
        assertSame(photo, kept("p1").reference)
    }

    @Test
    fun `a guide that leaves the subject's video before its photo comes is kept as it was`() = runTest(dispatcher) {
        started()
        mirror.setGuide(fakeGuide("p1"))
        runCurrent()

        mirror.setGuide(fakeGuide("p2").copy(reference = fakeBitmap()))
        runCurrent()

        assertEquals(setOf("p1", "p2"), store.saved.value.map { it.id }.toSet())
    }

    @Test
    fun `a guide already in the library stays as it is, and one deleted after it was kept is not kept again`() =
        runTest(dispatcher) {
            store.save(fakeGuide("p1"), GuideState(guideId = "p1"), favorite = true)
            started()
            mirror.setGuide(fakeGuide("p1").copy(reference = fakeBitmap()))
            runCurrent()
            assertEquals(listOf("p1" to true), store.saved.value.map { it.id to it.favorite })

            mirror.setGuide(fakeGuide("p2").copy(reference = fakeBitmap()))
            runCurrent()
            store.delete("p2")
            mirror.setGuide(null)
            mirror.setGuide(fakeGuide("p2").copy(reference = fakeBitmap()))
            runCurrent()

            assertFalse(store.saved.value.any { it.id == "p2" })
        }

    @Test
    fun `each of the photographer's candidates is cut out and kept once, under the photographer's id for it`() =
        runTest(dispatcher) {
            started()
            val set = MirrorPoseSet("set-1", PoseSetStep.GENERATING)
            poses.value = set.copy(candidates = mapOf("pose-1" to fakeBitmap()))
            runCurrent()
            poses.value = set.copy(candidates = mapOf("pose-1" to fakeBitmap(), "pose-2" to fakeBitmap()))
            runCurrent()

            assertEquals(2, maker.cuts)
            assertEquals(
                setOf(poseGuideId("set-1", "pose-1"), poseGuideId("set-1", "pose-2")),
                store.saved.value.map { it.id }.toSet(),
            )
            assertTrue(store.saved.value.none { it.favorite })
        }

    /** Cuts every image at once into a generated guide with an id of its own, as ML Kit's maker does. */
    private class CuttingMaker : ReferenceGuideMaker {
        var cuts = 0

        override suspend fun make(source: Uri, wholeImage: Boolean): Result<ReferenceGuide> =
            Result.success(fakeGuide("photo-${++cuts}"))

        override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> =
            Result.success(ReferenceGuide("cut-${++cuts}", fakeBitmap(), fakeBitmap(), 0.75f, source))
    }
}
