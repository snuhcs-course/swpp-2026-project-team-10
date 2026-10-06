package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryGuideRepositoryTest {
    private val repository = InMemoryGuideRepository()

    @Test
    fun startsEmptyAndIgnoresEditsWithoutAnImage() = runTest {
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()

        repository.update(final = true) { error("An absent guide must not be edited") }
        runCurrent()

        assertNull(repository.guide.value)
        assertEquals(GuideState(), repository.state.value)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun settingAnImagePublishesDefaultStateAndAFinalChange() = runTest {
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()
        val guide = guide("first")

        repository.setGuide(guide)
        runCurrent()

        assertSame(guide, repository.guide.value)
        val initial = GuideState(guideId = "first")
        assertEquals(initial, repository.state.value)
        assertEquals(listOf(GuideChange(initial, final = true)), changes)
    }

    @Test
    fun replacingOrReapplyingAnImageResetsAllDisplayState() {
        val first = guide("first")
        val second = guide("second", aspect = 1.5f)
        repository.setGuide(first)

        for (next in listOf(first, second)) {
            repository.update(final = true) {
                it.copy(cx = 0.2f, cy = 0.8f, height = 1.2f, opacity = 0.8f, style = GuideStyle.CUTOUT, visible = false)
            }
            repository.setGuide(next)

            assertSame(next, repository.guide.value)
            assertEquals(GuideState(guideId = next.id), repository.state.value)
        }
    }

    @Test
    fun clearingDropsTheImageAndEmitsAFinalResetEvenWhenAlreadyEmpty() = runTest {
        repository.setGuide(guide("first"))
        repository.update(final = true) { it.copy(style = GuideStyle.CUTOUT, opacity = 0.9f) }
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()

        repository.setGuide(null)
        repository.update(final = true) { error("An edit must not resurrect a removed guide") }
        repository.setGuide(null)
        runCurrent()

        assertNull(repository.guide.value)
        assertEquals(GuideState(), repository.state.value)
        assertEquals(List(2) { GuideChange(GuideState(), final = true) }, changes)
    }

    @Test
    fun updatesClampWithTheCurrentImagesAspectAndPublishTheAppliedState() = runTest {
        repository.setGuide(guide("wide", aspect = 1.5f))
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()

        repository.update(final = false) { it.copy(cx = 100f, cy = -100f, height = 100f, opacity = -1f) }
        runCurrent()

        val applied = repository.state.value
        assertEquals(2.1f, applied.height, EPSILON)
        // At aspect 1.5, height 2.1 occupies 4.2 frame widths, so the rightmost center is 2.26.
        assertEquals(2.26f, applied.cx, EPSILON)
        assertEquals(-0.63f, applied.cy, EPSILON)
        assertEquals(0.1f, applied.opacity, EPSILON)
        assertEquals(listOf(GuideChange(applied, final = false)), changes)
    }

    @Test
    fun successiveEditsUseTheLatestStateAndKeepTheSameBitmap() {
        val guide = guide("first")
        repository.setGuide(guide)
        repository.update(final = false) { it.copy(cx = it.cx + 0.1f, height = it.height * 2f) }
        repository.update(final = true) { it.copy(cx = it.cx + 0.2f, style = GuideStyle.CUTOUT, visible = false) }

        assertSame(guide, repository.guide.value)
        assertEquals(0.8f, repository.state.value.cx, EPSILON)
        assertEquals(1.4f, repository.state.value.height, EPSILON)
        assertEquals(GuideStyle.CUTOUT, repository.state.value.style)
        assertFalse(repository.state.value.visible)
    }

    @Test
    fun onlySetGuideCanChangeImageIdentity() {
        repository.setGuide(guide("stored"))
        for (id in listOf("unavailable", null)) {
            repository.update(final = true) { it.copy(guideId = id, opacity = 0.8f) }
            assertEquals("stored", repository.state.value.guideId)
            assertEquals(0.8f, repository.state.value.opacity, EPSILON)
        }
    }

    @Test
    fun anUnchangedClampedValueStillPublishesTheGestureEnd() = runTest {
        repository.setGuide(guide("first"))
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()

        repository.update(final = false) { it.copy(cx = 100f) }
        val atEdge = repository.state.value
        repository.update(final = true) { it.copy(cx = 100f) }
        runCurrent()

        assertEquals(atEdge, repository.state.value)
        assertEquals(listOf(GuideChange(atEdge, false), GuideChange(atEdge, true)), changes)
    }

    @Test
    fun aSlowCollectorKeepsAllChangesAndFinalValuesBeyondTheOldBufferLimit() = runTest {
        val changes = mutableListOf<GuideChange>()
        val resume = CompletableDeferred<Unit>()
        backgroundScope.launch {
            repository.changes.collect {
                changes += it
                if (changes.size == 1) resume.await()
            }
        }
        runCurrent()
        repository.setGuide(guide("first"))
        runCurrent()

        val expected = mutableListOf(GuideChange(repository.state.value, true))
        repeat(200) { index ->
            repository.update(final = false) { it.copy(cx = index / 200f) }
            expected += GuideChange(repository.state.value, false)
        }
        repository.update(final = true) { it }
        expected += GuideChange(repository.state.value, true)
        repository.setGuide(null)
        expected += GuideChange(GuideState(), true)

        resume.complete(Unit)
        runCurrent()
        assertEquals(expected, changes)
    }

    @Test
    fun subscribersSeeFutureEventsWhileLateSubscribersReadTheCurrentSnapshot() = runTest {
        repository.setGuide(guide("first"))
        repository.update(final = true) { it.copy(opacity = 0.8f) }
        val first = mutableListOf<GuideChange>()
        val second = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { first += it } }
        backgroundScope.launch { repository.changes.collect { second += it } }
        runCurrent()

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals("first", repository.guide.value?.id)
        assertEquals(0.8f, repository.state.value.opacity, EPSILON)

        repository.update(final = true) { it.copy(style = GuideStyle.CUTOUT) }
        runCurrent()
        assertEquals(listOf(GuideChange(repository.state.value, true)), first)
        assertEquals(first, second)
    }

    @Test
    fun invalidReplacementAndFailedEditLeaveTheExistingGuideAndEventsUnchanged() = runTest {
        val original = guide("first")
        repository.setGuide(original)
        repository.update(final = true) { it.copy(cx = 0.8f, style = GuideStyle.CUTOUT) }
        val previous = repository.state.value
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()

        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { repository.setGuide(guide("invalid", aspect)) }
        }
        assertThrows(IllegalStateException::class.java) { repository.update(final = true) { error("Failed edit") } }
        runCurrent()

        assertSame(original, repository.guide.value)
        assertEquals(previous, repository.state.value)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun nonFiniteEditsAreRecoveredThroughGeometry() {
        repository.setGuide(guide("first"))
        repository.update(final = true) {
            it.copy(cx = Float.NaN, cy = Float.POSITIVE_INFINITY, height = Float.NaN, opacity = Float.NEGATIVE_INFINITY)
        }
        assertEquals(GuideState(guideId = "first"), repository.state.value)
    }

    @Test
    fun photographerAndMirrorInstancesDoNotShareState() {
        val mirror = InMemoryGuideRepository()
        val guide = guide("shared-image")
        repository.setGuide(guide)
        assertNull(mirror.guide.value)
        mirror.setGuide(guide)

        repository.update(final = true) { it.copy(cx = 0.8f) }
        assertEquals(0.5f, mirror.state.value.cx, EPSILON)
        mirror.setGuide(null)
        assertSame(guide, repository.guide.value)
    }

    @Test
    fun concurrentWritersDoNotLoseEditsOrReorderEvents() = runTest {
        repository.setGuide(guide("first"))
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { repository.changes.collect { changes += it } }
        runCurrent()
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val workers = List(4) {
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(100) { repository.update(final = false) { it.copy(cx = it.cx + 0.001f) } }
                }
            }
            start.countDown()
            workers.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        runCurrent()

        assertEquals(0.9f, repository.state.value.cx, EPSILON)
        assertEquals(400, changes.size)
        assertTrue(changes.zipWithNext().all { (previous, next) -> next.state.cx > previous.state.cx })
        assertEquals(repository.state.value, changes.last().state)
    }

    private fun guide(id: String, aspect: Float = 0.5f): ReferenceGuide {
        // Android's JVM stubs cannot create a Bitmap; the repository only stores these references.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val bitmap = unsafeClass.getMethod(
            "allocateInstance",
            Class::class.java,
        ).invoke(unsafe, Bitmap::class.java) as Bitmap
        return ReferenceGuide(id, bitmap, bitmap, aspect, GuideSource.GALLERY)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
