// AI-generated with Claude Code, 2026-10-28, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideSyncerTest {

    private val session = FakeSessionManager()
    private val guides = InMemoryGuideRepository()
    private val mirror = InMemoryGuideRepository()
    private val codec = FakeGuideImageCodec()
    private val referenceCodec = FakeGuideReferenceCodec()
    private val mirrorReference = MirrorReference()

    private fun TestScope.syncer() =
        GuideSyncer(
            session,
            guides,
            mirror,
            codec,
            referenceCodec,
            mirrorReference,
            StandardTestDispatcher(testScheduler),
        )

    private fun sentTypes(): List<String> = session.sent.map { message ->
        when (message) {
            is SessionMessage.GuideImageBegin -> "begin"
            is SessionMessage.GuideImageChunk -> "chunk"
            is SessionMessage.GuideImageEnd -> "end"
            is SessionMessage.GuideReferenceBegin -> "ref.begin"
            is SessionMessage.GuideReferenceChunk -> "ref.chunk"
            is SessionMessage.GuideReferenceEnd -> "ref.end"
            is SessionMessage.GuideStateUpdate -> if (message.final) "state.final" else "state"
            SessionMessage.GuideClear -> "clear"
            else -> "other"
        }
    }

    private fun states(): List<SessionMessage.GuideStateUpdate> =
        session.sent.filterIsInstance<SessionMessage.GuideStateUpdate>()

    private fun connected(haveGuideId: String? = null, haveReferenceId: String? = null) {
        session.setState(
            SessionState.Connected("s_1", PeerInfo("Junhyeong", haveGuideId, haveReferenceId), Role.PHOTOGRAPHER),
        )
    }

    /** A guide with its reference photo, which the fake codec sends as two chunks. */
    private fun withReference(id: String) = fakeGuide(id).copy(reference = fakeBitmap())

    private val image = listOf("begin", "chunk", "chunk", "chunk", "end")
    private val reference = listOf("ref.begin", "ref.chunk", "ref.chunk", "ref.end")

    // ---- sender --------------------------------------------------------------------------------------------------

    @Test
    fun `after an attribution the states are the subject's until the final one is sent`() = runTest {
        guides.setGuide(fakeGuide("g1"))
        connected()
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()

        syncer.attribute(Role.SUBJECT)
        guides.update(final = false) { it.copy(cx = 0.3f) }
        runCurrent()
        guides.update(final = true) { it.copy(cx = 0.35f) }
        runCurrent()
        assertEquals(listOf(Role.SUBJECT, Role.SUBJECT), states().map { it.by })

        guides.update(final = true) { it.copy(cx = 0.4f) }
        runCurrent()
        assertEquals(Role.PHOTOGRAPHER, states().last().by)
    }

    @Test
    fun `on connect the image goes first, then the state`() = runTest {
        guides.setGuide(fakeGuide("g1"))
        guides.update(final = true) { it.copy(cx = 0.3f) }
        connected()

        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("begin", "chunk", "chunk", "chunk", "end", "state.final"), sentTypes())
        val begin = session.sent.first() as SessionMessage.GuideImageBegin
        assertEquals("g1", begin.guideId)
        assertEquals(3, begin.chunks)
        assertEquals(0.3f, states().single().state.cx, 0f)
        assertEquals(listOf("g1"), codec.encoded)
    }

    @Test
    fun `an image the subject already has is not sent again`() = runTest {
        guides.setGuide(fakeGuide("g1"))
        connected(haveGuideId = "g1")

        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("state.final"), sentTypes())
        assertTrue(codec.encoded.isEmpty())
    }

    @Test
    fun `no guide on connect clears the subject's`() = runTest {
        connected(haveGuideId = "old")

        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("clear"), sentTypes())
    }

    @Test
    fun `a new guide sends its image once, then its changes`() = runTest {
        connected()
        syncer().startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()

        guides.setGuide(fakeGuide("g2"))
        runCurrent()
        assertEquals(listOf("begin", "chunk", "chunk", "chunk", "end", "state.final"), sentTypes())

        session.sent.clear()
        guides.update(final = true) { it.copy(visible = false) }
        runCurrent()
        assertEquals(listOf("state.final"), sentTypes())
        assertEquals(false, states().single().state.visible)
    }

    @Test
    fun `gesture steps go at most every 50 ms, the newest wins, and the final value goes at once`() = runTest {
        guides.setGuide(fakeGuide("g1"))
        connected(haveGuideId = "g1")
        syncer().startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()

        guides.update(final = false) { it.copy(cx = 0.41f) }
        guides.update(final = false) { it.copy(cx = 0.42f) }
        guides.update(final = false) { it.copy(cx = 0.43f) }
        runCurrent()
        assertEquals(listOf(0.41f), states().map { it.state.cx })

        advanceTimeBy(60)
        assertEquals(listOf(0.41f, 0.43f), states().map { it.state.cx })

        guides.update(final = false) { it.copy(cx = 0.44f) }
        guides.update(final = true) { it.copy(cx = 0.45f) }
        runCurrent()
        assertEquals(listOf("state", "state", "state.final"), sentTypes())
        assertEquals(0.45f, states().last().state.cx, 0f)

        advanceTimeBy(200)
        assertEquals(3, states().size)
    }

    @Test
    fun `removing the guide sends clear`() = runTest {
        guides.setGuide(fakeGuide("g1"))
        connected(haveGuideId = "g1")
        syncer().startAsSender(backgroundScope)
        runCurrent()
        session.sent.clear()

        guides.setGuide(null)
        runCurrent()

        assertEquals(listOf("clear"), sentTypes())
    }

    // ---- the reference photo (FR-3.9) ---------------------------------------------------------------------------

    @Test
    fun `the reference photo goes after the guide's image and state, once per guide`() = runTest {
        guides.setGuide(withReference("g1"))
        connected()
        syncer().startAsSender(backgroundScope)
        runCurrent()
        assertEquals(image + "state.final" + reference, sentTypes())
        assertEquals("g1", (session.sent[6] as SessionMessage.GuideReferenceBegin).guideId)

        session.sent.clear()
        guides.update(final = true) { it.copy(cx = 0.4f) }
        runCurrent()
        assertEquals(listOf("state.final"), sentTypes())
        assertEquals(listOf("g1"), referenceCodec.encoded)
    }

    @Test
    fun `a guide removed while its reference is encoded on connect is cleared on the subject's phone`() = runTest {
        guides.setGuide(withReference("g1"))
        connected()
        referenceCodec.onEncode = { guides.setGuide(null) }
        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(image + "state.final" + reference + "clear", sentTypes())
    }

    @Test
    fun `a guide without a reference photo sends none`() = runTest {
        guides.setGuide(fakeGuide("g1"))
        connected()
        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(image + "state.final", sentTypes())
    }

    @Test
    fun `a subject that has the guide and its reference gets only the state`() = runTest {
        guides.setGuide(withReference("g1"))
        connected(haveGuideId = "g1", haveReferenceId = "g1")
        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("state.final"), sentTypes())
        assertTrue(referenceCodec.encoded.isEmpty())
    }

    @Test
    fun `a subject that has the guide but not its reference gets the reference`() = runTest {
        guides.setGuide(withReference("g1"))
        connected(haveGuideId = "g1", haveReferenceId = "old")
        syncer().startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("state.final") + reference, sentTypes())
    }

    @Test
    fun `a new guide sends its reference after its state, and a removed one sends it again when it comes back`() =
        runTest {
            connected()
            syncer().startAsSender(backgroundScope)
            runCurrent()
            session.sent.clear()

            guides.setGuide(withReference("g2"))
            runCurrent()
            assertEquals(image + "state.final" + reference, sentTypes())

            session.sent.clear()
            guides.setGuide(null)
            runCurrent()
            guides.setGuide(withReference("g2"))
            runCurrent()
            assertEquals(listOf("clear") + image + "state.final" + reference, sentTypes())
            // Encoded once: the second time comes from the cache.
            assertEquals(listOf("g2"), referenceCodec.encoded)
        }

    private fun deliver(reference: EncodedGuideReference, skip: Int? = null) {
        session.deliver(reference.begin())
        for (i in reference.chunks.indices) if (i != skip) session.deliver(reference.chunk(i))
        session.deliver(reference.end())
    }

    private fun deliver(image: EncodedGuideImage) {
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
    }

    @Test
    fun `the subject keeps the reference for its guide, and drops it with the guide`() = runTest {
        syncer().startAsReceiver(backgroundScope)
        runCurrent()

        deliver(codec.encode(fakeGuide("g1")))
        deliver(referenceCodec.encode(withReference("g1"))!!)
        runCurrent()
        assertEquals("g1", mirrorReference.reference.value?.guideId)
        assertEquals(listOf("reference:g1"), referenceCodec.decoded)

        // Another guide's image: the old reference is not its own.
        deliver(codec.encode(fakeGuide("g2")))
        runCurrent()
        assertNull(mirrorReference.reference.value)

        deliver(referenceCodec.encode(withReference("g2"))!!)
        runCurrent()
        assertEquals("g2", mirrorReference.reference.value?.guideId)
        session.deliver(SessionMessage.GuideClear)
        runCurrent()
        assertNull(mirrorReference.reference.value)
    }

    @Test
    fun `a reference that comes before its guide's image stays, and a broken one is dropped`() = runTest {
        syncer().startAsReceiver(backgroundScope)
        runCurrent()

        deliver(referenceCodec.encode(withReference("g1"))!!)
        runCurrent()
        deliver(codec.encode(fakeGuide("g1")))
        runCurrent()
        assertEquals("g1", mirrorReference.reference.value?.guideId)

        deliver(referenceCodec.encode(withReference("g2"))!!, skip = 1)
        runCurrent()
        assertEquals("g1", mirrorReference.reference.value?.guideId)
        assertEquals("g1", mirror.guide.value?.id)
    }

    // ---- receiver ------------------------------------------------------------------------------------------------

    private fun image(id: String): EncodedGuideImage = codec.encode(fakeGuide(id))

    @Test
    fun `the image is rebuilt, checked, and shown with its state`() = runTest {
        syncer().startAsReceiver(backgroundScope)
        runCurrent()
        val image = image("g1")

        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
        runCurrent()
        assertEquals("g1", mirror.guide.value?.id)
        assertEquals(listOf("g1"), codec.decoded)

        session.deliver(SessionMessage.GuideStateUpdate(GuideState("g1", cx = 0.3f, height = 0.5f), final = true))
        runCurrent()
        assertEquals(0.3f, mirror.state.value.cx, 0f)
        assertEquals(0.5f, mirror.state.value.height, 0f)
    }

    @Test
    fun `a state that arrives before its image is applied once the image is there`() = runTest {
        syncer().startAsReceiver(backgroundScope)
        runCurrent()
        val image = image("g1")

        session.deliver(SessionMessage.GuideStateUpdate(GuideState("g1", cx = 0.2f), final = false))
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("g1", cx = 0.25f), final = true))
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        runCurrent()
        assertNull(mirror.guide.value)

        session.deliver(image.end())
        runCurrent()
        assertEquals("g1", mirror.guide.value?.id)
        assertEquals(0.25f, mirror.state.value.cx, 0f)
    }

    @Test
    fun `a broken image is dropped and the mirror keeps what it had`() = runTest {
        syncer().startAsReceiver(backgroundScope)
        runCurrent()
        val old = image("g0")
        session.deliver(old.begin())
        for (i in old.chunks.indices) session.deliver(old.chunk(i))
        session.deliver(old.end())
        runCurrent()
        assertEquals("g0", mirror.guide.value?.id)

        val image = image("g1")
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(SessionMessage.GuideImageEnd("g1", image.crc32 + 1))
        runCurrent()
        assertEquals("g0", mirror.guide.value?.id)

        // A chunk for an image that was never begun, or out of order, is ignored too.
        session.deliver(image.chunk(0))
        session.deliver(image.begin())
        session.deliver(image.chunk(1))
        session.deliver(image.end())
        runCurrent()
        assertEquals("g0", mirror.guide.value?.id)
        assertEquals(listOf("g0"), codec.decoded)
    }

    @Test
    fun `clear removes the guide and forgets a state waiting for its image`() = runTest {
        syncer().startAsReceiver(backgroundScope)
        runCurrent()
        val image = image("g1")
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
        session.deliver(SessionMessage.GuideStateUpdate(GuideState("g2", cx = 0.1f), final = true))
        runCurrent()
        assertEquals("g1", mirror.guide.value?.id)

        session.deliver(SessionMessage.GuideClear)
        runCurrent()
        assertNull(mirror.guide.value)

        val next = image("g2")
        session.deliver(next.begin())
        for (i in next.chunks.indices) session.deliver(next.chunk(i))
        session.deliver(next.end())
        runCurrent()
        assertEquals("g2", mirror.guide.value?.id)
        assertEquals(0.5f, mirror.state.value.cx, 0f)
    }
}
