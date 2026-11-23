// AI-generated with Claude Code, 2026-11-23, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * A guide made on the subject's phone (Design 2.5.1, 2.6.5; Iter3-P9), with the fakes of ModuleFakes.kt.
 * Photographer, adoption (equivalence partitioning): an image from the subject becomes the guide at its anchor, with
 * no image sent back and its first state by the subject, and is reported once; it replaces a guide already there; a
 * missing source counts as GALLERY and GENERATED is kept; an image that cannot be decoded leaves the guide as it was;
 * a guide chosen after the adoption sends its own image once. Its reference photo (#234): the one sent before its
 * image is kept with it and not sent back; one of another guide, or a broken one, is not.
 * Boundary of the anchor (usable): 0 and 1, just outside, not finite, and without area; an unusable one starts the
 * guide centered.
 * Subject, the offered guide (state-based): drawn at once at its anchor and sent with anchor and source, after its
 * reference photo when it has one; kept when a
 * state names it, while the photographer's own messages wait; dropped after the deadline, which draws the
 * photographer's guide and state again and reports it, also the guide a reconnecting subject held from before; still
 * adopted after a clear that came first; an answer after the deadline still finds its image; an offer while not
 * connected sends nothing and is reported; a reference of the photographer's earlier guide that arrives after the
 * answer leaves the offered guide's own thumbnail.
 * Photographer, while its first guide is still being sent: a guide adopted from the subject gets no image back and
 * nothing of the first guide after its state; a guide replaced on this phone gets its image before its state, and the
 * first guide's state and reference are not sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideFromSubjectTest {

    private val session = FakeSessionManager()
    private val guides = InMemoryGuideRepository()
    private val mirror = InMemoryGuideRepository()
    private val codec = FakeGuideImageCodec()
    private val references = FakeGuideReferenceCodec()
    private val mirrorReference = MirrorReference()
    private val held = HeldGuide()

    private val box = NormalizedRect(0.3f, 0.2f, 0.7f, 0.9f)

    private fun TestScope.syncer() =
        GuideSyncer(
            session,
            guides,
            mirror,
            codec,
            references,
            mirrorReference,
            StandardTestDispatcher(testScheduler),
            held,
        )

    private fun connectedAs(role: Role, haveGuideId: String? = null) {
        session.setState(SessionState.Connected("s_1", PeerInfo("Joonhyung", haveGuideId), role))
    }

    /** The subject's image of [id] as the photographer receives it. */
    private fun deliverFromSubject(id: String, anchor: NormalizedRect? = box, source: GuideSource? = null) {
        val image = codec.encode(fakeGuide(id))
        // Encoded on the subject's phone, not this one.
        codec.encoded.removeAt(codec.encoded.lastIndex)
        session.deliver(image.begin(anchor, source))
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
    }

    /** The subject's reference photo of [id] as the photographer receives it, before the guide's image. */
    private fun deliverReferenceFromSubject(id: String, broken: Boolean = false) {
        val reference = FakeGuideReferenceCodec().encode(fakeGuide(id).copy(reference = fakeBitmap()))!!
        session.deliver(reference.begin())
        val chunks = if (broken) reference.chunks.indices.drop(1) else reference.chunks.indices
        for (i in chunks) session.deliver(reference.chunk(i))
        session.deliver(reference.end())
    }

    /** The photographer's own image of [id] as the subject receives it. */
    private fun deliverFromPhotographer(id: String) {
        val image = codec.encode(fakeGuide(id))
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
    }

    private fun mine(id: String): ReferenceGuide = fakeGuide(id).copy(anchor = box, source = GuideSource.GENERATED)

    private fun sentImages(): List<String> =
        session.sent.filterIsInstance<SessionMessage.GuideImageBegin>().map { it.guideId }

    private fun states(): List<SessionMessage.GuideStateUpdate> =
        session.sent.filterIsInstance<SessionMessage.GuideStateUpdate>()

    // ---- photographer ---------------------------------------------------------------------------------------------

    @Test
    fun `an image from the subject becomes the guide at its anchor and is not sent back`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val adopted = mutableListOf<String>()
        backgroundScope.launch { syncer.adopted.collect { adopted += it } }
        syncer.startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()
        codec.encoded.clear()

        deliverFromSubject("s1", source = GuideSource.GENERATED)
        runCurrent()

        val guide = guides.guide.value
        assertEquals("s1", guide?.id)
        assertEquals(box, guide?.anchor)
        assertEquals(GuideSource.GENERATED, guide?.source)
        assertEquals(0.5f, guides.state.value.cx, 0.001f)
        assertEquals(0.55f, guides.state.value.cy, 0.001f)
        assertEquals(0.7f, guides.state.value.height, 0.001f)
        assertTrue("no image goes back", sentImages().isEmpty())
        assertTrue(codec.encoded.isEmpty())
        assertEquals(listOf("s1" to Role.SUBJECT), states().map { it.state.guideId to it.by })
        assertTrue(states().single().final)
        assertEquals(listOf("s1"), adopted)

        guides.update(final = true) { it.copy(cx = 0.4f) }
        runCurrent()
        assertEquals(Role.PHOTOGRAPHER, states().last().by)
    }

    @Test
    fun `a guide from the subject replaces the photographer's, and a later guide sends its own image`() = runTest {
        guides.setGuide(fakeGuide("p1"))
        connectedAs(Role.PHOTOGRAPHER)
        syncer().startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()

        deliverFromSubject("s1", source = null)
        runCurrent()
        assertEquals("s1", guides.guide.value?.id)
        assertEquals(GuideSource.GALLERY, guides.guide.value?.source)

        guides.setGuide(fakeGuide("p2"))
        runCurrent()
        assertEquals(listOf("p2"), sentImages())
    }

    @Test
    fun `the reference photo sent before the subject's image stays with the guide and is not sent back`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        syncer().startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()

        deliverReferenceFromSubject("s1")
        deliverFromSubject("s1")
        runCurrent()

        assertEquals("s1", guides.guide.value?.id)
        assertNotNull("the Camera screen's thumbnail", guides.guide.value?.reference)
        assertEquals(listOf("reference:s1"), references.decoded)
        assertTrue("no reference goes back", session.sent.none { it is SessionMessage.GuideReferenceBegin })
        assertTrue(references.encoded.isEmpty())
    }

    @Test
    fun `a reference photo of another guide or a broken one does not go with the subject's guide`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        syncer().startAsSender(backgroundScope)
        runCurrent()

        deliverReferenceFromSubject("s0")
        deliverFromSubject("s1")
        runCurrent()
        assertEquals("s1", guides.guide.value?.id)
        assertNull(guides.guide.value?.reference)

        deliverReferenceFromSubject("s2", broken = true)
        deliverFromSubject("s2")
        runCurrent()
        assertEquals("s2", guides.guide.value?.id)
        assertNull(guides.guide.value?.reference)

        // A reference goes with its own image only, not with the next.
        deliverReferenceFromSubject("s3")
        deliverFromSubject("s3")
        runCurrent()
        assertNotNull(guides.guide.value?.reference)
        deliverFromSubject("s4")
        runCurrent()
        assertEquals("s4", guides.guide.value?.id)
        assertNull(guides.guide.value?.reference)
    }

    @Test
    fun `an image that cannot be decoded leaves the photographer's guide`() = runTest {
        guides.setGuide(fakeGuide("p1"))
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val adopted = mutableListOf<String>()
        backgroundScope.launch { syncer.adopted.collect { adopted += it } }
        syncer.startAsSender(backgroundScope)
        runCurrent()
        codec.decodes = false

        deliverFromSubject("s1")
        runCurrent()

        assertEquals("p1", guides.guide.value?.id)
        assertTrue(adopted.isEmpty())
    }

    @Test
    fun `an anchor that cannot place a guide starts it centered`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        syncer().startAsSender(backgroundScope)
        runCurrent()

        deliverFromSubject("s1", anchor = NormalizedRect(0.3f, 0.2f, 0.3f, 0.9f))
        runCurrent()

        assertNull(guides.guide.value?.anchor)
        assertEquals(0.5f, guides.state.value.cx, 0.001f)
        assertEquals(0.5f, guides.state.value.cy, 0.001f)
        assertEquals(GuideState.DEFAULT_HEIGHT, guides.state.value.height, 0.001f)
    }

    @Test
    fun `an anchor is usable from 0 to 1 with an area, and not just outside or without numbers`() {
        assertTrue(GuideSyncer.usable(NormalizedRect(0f, 0f, 1f, 1f)))
        assertTrue(GuideSyncer.usable(box))
        assertFalse(GuideSyncer.usable(NormalizedRect(-0.01f, 0f, 1f, 1f)))
        assertFalse(GuideSyncer.usable(NormalizedRect(0f, 0f, 1.01f, 1f)))
        assertFalse(GuideSyncer.usable(NormalizedRect(0f, Float.NaN, 1f, 1f)))
        assertFalse(GuideSyncer.usable(NormalizedRect(0f, 0f, Float.POSITIVE_INFINITY, 1f)))
        assertFalse(GuideSyncer.usable(NormalizedRect(0.5f, 0f, 0.5f, 1f)))
        assertFalse(GuideSyncer.usable(NormalizedRect(0f, 0.6f, 1f, 0.4f)))
    }

    @Test
    fun `a guide adopted while the first guide goes out is not sent back`() = runTest {
        guides.setGuide(fakeGuide("p1").copy(reference = fakeBitmap()))
        connectedAs(Role.PHOTOGRAPHER)
        var arrived = false
        codec.onEncode = { guide ->
            if (guide.id == "p1" && !arrived) {
                arrived = true
                deliverReferenceFromSubject("s1")
                deliverFromSubject("s1")
            }
        }
        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals("s1", guides.guide.value?.id)
        assertNotNull(guides.guide.value?.reference)
        assertEquals(listOf("p1"), sentImages())
        assertEquals("s1" to Role.SUBJECT, states().last().let { it.state.guideId to it.by })
        // Nothing of the first guide follows the adopted guide's state, and the subject's photo does not go back.
        val adopted = session.sent.indexOfFirst { it is SessionMessage.GuideStateUpdate && it.state.guideId == "s1" }
        assertTrue(session.sent.drop(adopted).none { it is SessionMessage.GuideReferenceBegin && it.guideId == "p1" })
        assertTrue(session.sent.none { it is SessionMessage.GuideReferenceBegin && it.guideId == "s1" })
    }

    @Test
    fun `a guide adopted while the first guide's reference is encoded does not send its photo back`() = runTest {
        guides.setGuide(fakeGuide("p1").copy(reference = fakeBitmap()))
        connectedAs(Role.PHOTOGRAPHER)
        references.onEncode = { guide ->
            if (guide.id == "p1") {
                deliverReferenceFromSubject("s1")
                deliverFromSubject("s1")
            }
        }
        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals("s1", guides.guide.value?.id)
        assertNotNull(guides.guide.value?.reference)
        assertEquals(listOf("p1"), references.encoded)
        assertTrue(session.sent.none { it is SessionMessage.GuideReferenceBegin && it.guideId == "s1" })
    }

    @Test
    fun `a guide replaced while the first one goes out sends no state or reference of the wrong guide`() = runTest {
        guides.setGuide(fakeGuide("p1").copy(reference = fakeBitmap()))
        connectedAs(Role.PHOTOGRAPHER)
        codec.onEncode =
            { guide -> if (guide.id == "p1") guides.setGuide(fakeGuide("p2").copy(reference = fakeBitmap())) }
        syncer().startAsSender(backgroundScope)
        runCurrent()

        val order = session.sent.mapNotNull { message ->
            when (message) {
                is SessionMessage.GuideImageBegin -> "image ${message.guideId}"
                is SessionMessage.GuideStateUpdate -> "state ${message.state.guideId}"
                is SessionMessage.GuideReferenceBegin -> "reference ${message.guideId}"
                else -> null
            }
        }
        assertEquals(listOf("image p1", "image p2", "state p2", "reference p2"), order)
    }

    // ---- subject --------------------------------------------------------------------------------------------------

    @Test
    fun `a late reference of the photographer's earlier guide leaves the offered guide's thumbnail`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        syncer.offer(mine("s1").copy(reference = fakeBitmap()))
        runCurrent()
        deliverFromPhotographer("p1")
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("s1"), final = true, by = Role.SUBJECT))
        val reference = FakeGuideReferenceCodec().encode(fakeGuide("p1").copy(reference = fakeBitmap()))!!
        session.deliver(reference.begin())
        for (i in reference.chunks.indices) session.deliver(reference.chunk(i))
        session.deliver(reference.end())
        runCurrent()

        assertEquals("s1", mirror.guide.value?.id)
        assertEquals("s1", mirrorReference.reference.value?.guideId)
    }

    @Test
    fun `an offered guide is drawn at once and sent with its anchor and source`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        syncer.offer(mine("s1"))
        assertEquals("s1", mirror.guide.value?.id)
        assertEquals(0.55f, mirror.state.value.cy, 0.001f)
        runCurrent()

        val begin = session.sent.first() as SessionMessage.GuideImageBegin
        assertEquals("s1", begin.guideId)
        assertEquals(box, begin.anchor)
        assertEquals(GuideSource.GENERATED, begin.source)
        assertTrue(session.sent.last() is SessionMessage.GuideImageEnd)
    }

    @Test
    fun `an offered guide's reference photo goes before its image`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        syncer.offer(mine("s1").copy(reference = fakeBitmap()))
        runCurrent()

        val order = session.sent.mapNotNull { message ->
            when (message) {
                is SessionMessage.GuideReferenceBegin -> "reference ${message.guideId}"
                is SessionMessage.GuideReferenceEnd -> "reference end"
                is SessionMessage.GuideImageBegin -> "image ${message.guideId}"
                is SessionMessage.GuideImageEnd -> "image end"
                else -> null
            }
        }
        assertEquals(listOf("reference s1", "reference end", "image s1", "image end"), order)
        assertEquals("s1", mirrorReference.reference.value?.guideId)
    }

    @Test
    fun `a state that names the offered guide keeps it, and the photographer's earlier messages wait`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.offerFailed.collect { failed += it } }
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        syncer.offer(mine("s1"))
        runCurrent()
        // Sent by the photographer before it had the subject's guide.
        deliverFromPhotographer("p1")
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("p1", cx = 0.2f), final = true))
        runCurrent()
        assertEquals("s1", mirror.guide.value?.id)

        session.deliver(SessionMessage.GuideStateUpdate(GuideState("s1", cx = 0.4f), final = true, by = Role.SUBJECT))
        runCurrent()
        advanceTimeBy(GuideSyncer.OFFER_TIMEOUT_MS + 1)
        runCurrent()

        assertEquals("s1", mirror.guide.value?.id)
        assertEquals(0.4f, mirror.state.value.cx, 0f)
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `an offer no state names draws the photographer's guide again after the deadline`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.offerFailed.collect { failed += it } }
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverFromPhotographer("p1")
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("p1", cx = 0.2f), final = true))
        runCurrent()

        syncer.offer(mine("s1"))
        runCurrent()
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("p1", cx = 0.3f), final = true))
        runCurrent()
        assertEquals("s1", mirror.guide.value?.id)

        advanceTimeBy(GuideSyncer.OFFER_TIMEOUT_MS + 1)
        runCurrent()

        assertEquals("p1", mirror.guide.value?.id)
        assertEquals(0.3f, mirror.state.value.cx, 0f)
        assertEquals(listOf("s1"), failed)
    }

    @Test
    fun `after a reconnect an offer no state names draws the guide held from before`() = runTest {
        // From before the reconnect: hello names this guide, so only its state comes again.
        held.guide = fakeGuide("p1")
        held.reference = GuideReference("p1", fakeBitmap())
        connectedAs(Role.SUBJECT, haveGuideId = "p1")
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("p1", cx = 0.3f), final = true))
        runCurrent()

        syncer.offer(mine("s1"))
        runCurrent()
        advanceTimeBy(GuideSyncer.OFFER_TIMEOUT_MS + 1)
        runCurrent()

        assertEquals("p1", mirror.guide.value?.id)
        assertEquals(0.3f, mirror.state.value.cx, 0f)
        assertEquals("p1", mirrorReference.reference.value?.guideId)
    }

    @Test
    fun `a clear before the answer keeps the offered guide, which the answer then confirms`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverFromPhotographer("p1")
        runCurrent()

        syncer.offer(mine("s1"))
        runCurrent()
        session.deliver(SessionMessage.GuideClear)
        runCurrent()
        assertEquals("s1", mirror.guide.value?.id)

        session.deliver(SessionMessage.GuideStateUpdate(GuideState("s1"), final = true, by = Role.SUBJECT))
        runCurrent()
        assertEquals("s1", mirror.guide.value?.id)
    }

    @Test
    fun `an answer after the deadline still finds the offered guide's image`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        syncer.offer(mine("s1"))
        runCurrent()
        advanceTimeBy(GuideSyncer.OFFER_TIMEOUT_MS + 1)
        runCurrent()
        assertNull(mirror.guide.value)

        session.deliver(SessionMessage.GuideStateUpdate(GuideState("s1", cx = 0.6f), final = true, by = Role.SUBJECT))
        runCurrent()
        assertEquals("s1", mirror.guide.value?.id)
        assertEquals(0.6f, mirror.state.value.cx, 0f)
    }

    @Test
    fun `an offer while not connected sends nothing and is reported`() = runTest {
        val syncer = syncer()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.offerFailed.collect { failed += it } }
        runCurrent()

        syncer.offer(mine("s1"))
        runCurrent()

        assertTrue(session.sent.isEmpty())
        assertNull(mirror.guide.value)
        assertEquals(listOf("s1"), failed)
    }
}
