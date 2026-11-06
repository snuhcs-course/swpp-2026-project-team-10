// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.ConfirmStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The photographer's Reference confirm of a chosen pose on the subject's Your reference (Design 2.5.1, 2.6.5;
 * Iter3-P16), with the fakes of ModuleFakes.kt.
 * Photographer, the confirm (state-based): FINDING, then READY with its guide followed by the outline's mask, FINDING
 * again for another photo and READY with its outline; USED with the guide and whose use it was; CLOSED from FINDING or
 * READY; one end per confirm, so a close after a use sends nothing; a step for another confirm does nothing.
 * Photographer, the connection: nothing goes while no subject is connected; a sender started later (a reconnect)
 * sends the step the confirm is at, with its outline; a confirm that ended meanwhile goes as its end alone; a confirm
 * that moves on while the mask is encoded sends no outline.
 * Photographer, the subject's uses (equivalence partitioning): taken while the outline they name is on screen;
 * dropped for another outline or confirm, while finding, after a use, and after a close.
 * Subject, the copy (state-based): FINDING, then READY shown once its outline is here; another photo hides it until
 * its own outline comes; dropped at its end, which is reported with whose use it was, at another confirm's start, at
 * another confirm's end without a notice, and when the session ends; kept through a reconnect, where the outline sent
 * again is not read again.
 * Subject, an outline (fault injection, boundary): a broken transfer, a header over 1280 × 720 (at the limit read), a
 * mask that does not decode, and the outline of a guide not on screen are let go.
 * Subject, a use (boundary of the deadline): sent and waiting; answered by its confirm's end or by another photo;
 * reported just after 5 s and not just before; no use without the outline on screen or a session, or while the link
 * reconnects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmSyncerTest {

    private val session = FakeSessionManager()
    private val codec = FakeGuideImageCodec()

    private fun TestScope.syncer() = ConfirmSyncer(session, codec, StandardTestDispatcher(testScheduler))

    private fun connectedAs(role: Role) {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje", null), role))
    }

    /** What went out, in order: steps by name and guide, outlines by guide. */
    private fun sentOrder(): List<String> = session.sent.mapNotNull {
        when (it) {
            is SessionMessage.GuideConfirm -> listOfNotNull(it.step.name, it.guideId).joinToString(" ")
            is SessionMessage.GuideConfirmImageBegin -> "outline ${it.guideId}"
            else -> null
        }
    }

    /** The photographer's outline of [guideId] on [confirmId] as the subject receives it. */
    private fun deliverOutline(confirmId: String, guideId: String) {
        val image = codec.encode(fakeGuide(guideId))
        codec.encoded.removeAt(codec.encoded.lastIndex)
        session.deliver(image.confirmBegin(confirmId))
        for (i in image.chunks.indices) session.deliver(image.confirmChunk(confirmId, i))
        session.deliver(image.confirmEnd(confirmId))
    }

    private fun deliverStep(confirmId: String, step: ConfirmStep, guideId: String? = null, by: Role? = null) {
        session.deliver(SessionMessage.GuideConfirm(confirmId, step, guideId, by))
    }

    /** A subject's syncer holding c1 READY with g1's outline. */
    private fun TestScope.ready(): ConfirmSyncer {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("c1", ConfirmStep.FINDING)
        deliverStep("c1", ConfirmStep.READY, "g1")
        deliverOutline("c1", "g1")
        runCurrent()
        return syncer
    }

    // ---- photographer: the confirm ------------------------------------------------------------------------------

    @Test
    fun `the photographer's confirm goes out in order, its outline after READY`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()

        syncer.open("c1")
        runCurrent()
        assertEquals(listOf("FINDING"), sentOrder())

        syncer.ready("c1", fakeGuide("g1"))
        runCurrent()
        syncer.finding("c1")
        runCurrent()
        syncer.ready("c1", fakeGuide("g2"))
        runCurrent()

        assertEquals(listOf("FINDING", "READY g1", "outline g1", "FINDING", "READY g2", "outline g2"), sentOrder())
        // Every chunk of the outline is sent before the next message, as the mask the guide sends (mask-png here).
        val chunks = session.sent.filterIsInstance<SessionMessage.GuideConfirmImageChunk>()
        assertEquals(setOf("g1", "g2"), chunks.map { it.guideId }.toSet())
        assertTrue(chunks.all { it.confirmId == "c1" })
        assertEquals(3, chunks.count { it.guideId == "g1" })
    }

    @Test
    fun `a use ends the confirm once with whose use it was`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        syncer.open("c1")
        runCurrent()
        syncer.ready("c1", fakeGuide("g1"))
        runCurrent()

        syncer.used("c1", "g1", Role.SUBJECT)
        runCurrent()
        syncer.close("c1")
        syncer.used("c1", "g1", Role.PHOTOGRAPHER)
        syncer.finding("c1")
        runCurrent()

        val end = session.sent.filterIsInstance<SessionMessage.GuideConfirm>().last()
        assertEquals(SessionMessage.GuideConfirm("c1", ConfirmStep.USED, "g1", Role.SUBJECT), end)
        assertEquals(listOf("FINDING", "READY g1", "outline g1", "USED g1"), sentOrder())
    }

    @Test
    fun `a close ends a confirm that is finding or ready, and another confirm's steps change nothing`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        syncer.open("c1")
        runCurrent()
        syncer.close("c1")
        runCurrent()
        syncer.open("c2")
        runCurrent()
        syncer.ready("c2", fakeGuide("g2"))
        runCurrent()
        syncer.close("c1")
        syncer.used("c1", "g2", Role.PHOTOGRAPHER)
        syncer.ready("c1", fakeGuide("g3"))
        runCurrent()
        syncer.close("c2")
        runCurrent()

        assertEquals(listOf("FINDING", "CLOSED", "FINDING", "READY g2", "outline g2", "CLOSED"), sentOrder())
    }

    // ---- photographer: the connection ---------------------------------------------------------------------------

    @Test
    fun `nothing goes before a subject is connected, and then the confirm goes at its step with its outline`() =
        runTest {
            val syncer = syncer()
            syncer.open("c1")
            syncer.ready("c1", fakeGuide("g1"))
            runCurrent()
            assertTrue(session.sent.isEmpty())

            connectedAs(Role.PHOTOGRAPHER)
            syncer.startAsSender(backgroundScope)
            runCurrent()

            assertEquals(listOf("READY g1", "outline g1"), sentOrder())
        }

    @Test
    fun `after a reconnect the open confirm is sent again, and an ended one as its end alone`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val first = Job()
        syncer.startAsSender(TestScope(testScheduler + first))
        syncer.open("c1")
        syncer.ready("c1", fakeGuide("g1"))
        runCurrent()
        first.cancel()
        session.sent.clear()

        val second = Job()
        syncer.startAsSender(TestScope(testScheduler + second))
        runCurrent()
        assertEquals(listOf("READY g1", "outline g1"), sentOrder())

        second.cancel()
        session.sent.clear()
        syncer.used("c1", "g1", Role.PHOTOGRAPHER)
        syncer.startAsSender(backgroundScope)
        runCurrent()
        assertEquals(listOf(SessionMessage.GuideConfirm("c1", ConfirmStep.USED, "g1", Role.PHOTOGRAPHER)), session.sent)
    }

    @Test
    fun `a confirm that moves on while its mask is encoded sends no outline`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        syncer.open("c1")
        runCurrent()
        codec.onEncode = { syncer.used("c1", "g1", Role.PHOTOGRAPHER) }

        syncer.ready("c1", fakeGuide("g1"))
        runCurrent()

        assertEquals(listOf("FINDING", "READY g1", "USED g1"), sentOrder())
    }

    // ---- photographer: the subject's uses -----------------------------------------------------------------------

    @Test
    fun `a use of the outline on screen is passed on`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val uses = mutableListOf<SubjectUse>()
        backgroundScope.launch { syncer.uses.collect { uses += it } }
        syncer.startAsSender(backgroundScope)
        syncer.open("c1")
        syncer.ready("c1", fakeGuide("g1"))
        runCurrent()

        session.deliver(SessionMessage.GuideConfirmUse("c1", "g1"))
        runCurrent()

        assertEquals(listOf(SubjectUse("c1", "g1")), uses)
    }

    @Test
    fun `a use of another outline or confirm, while finding, after a use, or after a close is dropped`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val uses = mutableListOf<SubjectUse>()
        backgroundScope.launch { syncer.uses.collect { uses += it } }
        syncer.startAsSender(backgroundScope)
        syncer.open("c1")
        runCurrent()

        session.deliver(SessionMessage.GuideConfirmUse("c1", "g1"))
        runCurrent()
        syncer.ready("c1", fakeGuide("g1"))
        session.deliver(SessionMessage.GuideConfirmUse("c1", "g0"))
        session.deliver(SessionMessage.GuideConfirmUse("c0", "g1"))
        runCurrent()
        syncer.used("c1", "g1", Role.PHOTOGRAPHER)
        session.deliver(SessionMessage.GuideConfirmUse("c1", "g1"))
        runCurrent()
        syncer.open("c2")
        syncer.ready("c2", fakeGuide("g2"))
        syncer.close("c2")
        session.deliver(SessionMessage.GuideConfirmUse("c2", "g2"))
        runCurrent()

        assertTrue(uses.isEmpty())
    }

    // ---- subject: the copy of the confirm -----------------------------------------------------------------------

    @Test
    fun `the subject shows the outline once READY and its outline are both here`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        deliverStep("c1", ConfirmStep.FINDING)
        runCurrent()
        assertEquals(ConfirmStep.FINDING, syncer.mirror.value?.step)
        assertNull(syncer.mirror.value?.ready)

        deliverStep("c1", ConfirmStep.READY, "g1")
        runCurrent()
        assertNull(syncer.mirror.value?.ready)

        deliverOutline("c1", "g1")
        runCurrent()
        assertEquals("g1", syncer.mirror.value?.ready?.id)
        assertEquals(listOf("g1"), codec.decoded)
    }

    @Test
    fun `another photo hides the outline until its own comes`() = runTest {
        val syncer = ready()

        deliverStep("c1", ConfirmStep.FINDING)
        runCurrent()
        assertEquals(ConfirmStep.FINDING, syncer.mirror.value?.step)
        assertNull(syncer.mirror.value?.ready)

        deliverStep("c1", ConfirmStep.READY, "g2")
        deliverOutline("c1", "g2")
        runCurrent()
        assertEquals("g2", syncer.mirror.value?.ready?.id)
    }

    @Test
    fun `the confirm's end drops it and is reported with whose use it was`() = runTest {
        val syncer = ready()
        val ends = mutableListOf<ConfirmEnd>()
        backgroundScope.launch { syncer.ended.collect { ends += it } }

        deliverStep("c1", ConfirmStep.USED, "g1", Role.PHOTOGRAPHER)
        runCurrent()

        assertNull(syncer.mirror.value)
        assertEquals(listOf(ConfirmEnd("c1", ConfirmStep.USED, Role.PHOTOGRAPHER)), ends)
    }

    @Test
    fun `another confirm's start replaces the confirm, and another confirm's end drops it without a notice`() =
        runTest {
            val syncer = ready()
            val ends = mutableListOf<ConfirmEnd>()
            backgroundScope.launch { syncer.ended.collect { ends += it } }

            deliverStep("c2", ConfirmStep.FINDING)
            runCurrent()
            assertEquals("c2", syncer.mirror.value?.confirmId)
            assertNull(syncer.mirror.value?.received)

            deliverStep("c3", ConfirmStep.CLOSED)
            runCurrent()
            assertNull(syncer.mirror.value)
            assertTrue(ends.isEmpty())
        }

    @Test
    fun `the confirm stays through a reconnect without reading its outline again, and goes with the session`() =
        runTest {
            connectedAs(Role.SUBJECT)
            val syncer = syncer()
            val receiving = Job()
            syncer.startAsReceiver(TestScope(testScheduler + receiving))
            runCurrent()
            deliverStep("c1", ConfirmStep.READY, "g1")
            deliverOutline("c1", "g1")
            runCurrent()

            session.setState(SessionState.Reconnecting("s_1", 0L))
            runCurrent()
            connectedAs(Role.SUBJECT)
            deliverStep("c1", ConfirmStep.READY, "g1")
            deliverOutline("c1", "g1")
            runCurrent()
            assertEquals("g1", syncer.mirror.value?.ready?.id)
            assertEquals(listOf("g1"), codec.decoded)

            receiving.cancel()
            runCurrent()
            assertNull(syncer.mirror.value)
        }

    // ---- subject: an outline ------------------------------------------------------------------------------------

    @Test
    fun `a broken, oversized, undecodable, or unwanted outline is let go`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("c1", ConfirmStep.READY, "g1")
        runCurrent()

        // Broken: a chunk missing.
        val image = codec.encode(fakeGuide("g1"))
        session.deliver(image.confirmBegin("c1"))
        session.deliver(image.confirmChunk("c1", 0))
        session.deliver(image.confirmChunk("c1", 2))
        session.deliver(image.confirmEnd("c1"))
        runCurrent()
        // Over the mask's 1280 × 720 at the header, by one pixel each way.
        for ((width, height) in listOf(1281 to 720, 1280 to 721)) {
            val wide = EncodedGuideImage("g1", "fake", width, height, ByteArray(100))
            session.deliver(wide.confirmBegin("c1"))
            for (i in wide.chunks.indices) session.deliver(wide.confirmChunk("c1", i))
            session.deliver(wide.confirmEnd("c1"))
            runCurrent()
        }
        // The outline of a guide not on screen.
        deliverOutline("c1", "g0")
        runCurrent()
        // Does not decode.
        codec.decodes = false
        deliverOutline("c1", "g1")
        runCurrent()
        assertNull(syncer.mirror.value?.ready)
        assertEquals(listOf("g1"), codec.decoded)

        // At the limit, it is read.
        codec.decodes = true
        val limit = EncodedGuideImage("g1", "fake", 1280, 720, ByteArray(100))
        session.deliver(limit.confirmBegin("c1"))
        for (i in limit.chunks.indices) session.deliver(limit.confirmChunk("c1", i))
        session.deliver(limit.confirmEnd("c1"))
        runCurrent()
        assertEquals("g1", syncer.mirror.value?.ready?.id)
    }

    // ---- subject: a use -----------------------------------------------------------------------------------------

    @Test
    fun `a use is sent and waits until the confirm's end answers it`() = runTest {
        val syncer = ready()

        assertTrue(syncer.use("g1"))
        assertEquals(SessionMessage.GuideConfirmUse("c1", "g1"), session.sent.last())
        assertEquals(SubjectUse("c1", "g1"), syncer.pending.value)
        assertFalse(syncer.use("g1"))

        deliverStep("c1", ConfirmStep.USED, "g1", Role.SUBJECT)
        runCurrent()
        assertNull(syncer.pending.value)
    }

    @Test
    fun `a use goes when the photographer finds another photo, without a failure`() = runTest {
        val syncer = ready()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.useFailed.collect { failed += it } }
        syncer.use("g1")

        deliverStep("c1", ConfirmStep.FINDING)
        runCurrent()
        advanceTimeBy(ConfirmSyncer.USE_TIMEOUT_MS + 1)
        runCurrent()

        assertNull(syncer.pending.value)
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `a use no step answers is reported just after 5 s and not just before`() = runTest {
        val syncer = ready()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.useFailed.collect { failed += it } }
        syncer.use("g1")

        advanceTimeBy(ConfirmSyncer.USE_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(failed.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("g1"), failed)
        assertNull(syncer.pending.value)
    }

    @Test
    fun `no use without the outline on screen or a session, or while the link reconnects`() = runTest {
        val syncer = ready()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.useFailed.collect { failed += it } }

        assertFalse(syncer.use("g0"))
        session.setState(SessionState.Reconnecting("s_1", 0L))
        assertFalse(syncer.use("g1"))
        session.setState(SessionState.Idle)
        assertFalse(syncer.use("g1"))
        runCurrent()

        assertEquals(listOf("g0", "g1", "g1"), failed)
        assertTrue(session.sent.none { it is SessionMessage.GuideConfirmUse })
    }
}
