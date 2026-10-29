// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.PoseSetStep
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
 * The photographer's candidates on the subject's Pick a pose (Design 2.5.1, 2.6.5; Iter3-P16), with the fakes of
 * ModuleFakes.kt.
 * Photographer, the set (state-based): GENERATING, each candidate as it arrives, then PICKING with its order, then
 * CHOSEN with whose pick; CLOSED from GENERATING or PICKING; one end per set, so a close after a choice sends nothing;
 * a candidate or a step after the end is not sent; a candidate that cannot be encoded is left out.
 * Photographer, the connection: nothing goes while no subject is connected; a sender started later (a reconnect)
 * sends the open set again, its candidates first; a set that ended meanwhile goes as its end alone; a set that ends
 * while a copy is encoded sends no more copies, and no PICKING it has moved past.
 * Photographer, the subject's picks (equivalence partitioning): taken while its set is PICKING; dropped for another
 * set, while generating, after a choice, and after a close.
 * Subject, the copy (state-based): built from GENERATING, the copies, and PICKING, in the photographer's order with
 * missing copies left out; dropped at its end, which is reported with whether Pick a pose had it, at another set's
 * start, at another set's end without a notice, and when the session ends; kept through a reconnect, where the copies
 * sent again are not read again.
 * Subject, a copy (fault injection, boundary): a broken transfer, a header over 1024 px or 1 MiB (both at the limit
 * read), a copy that does not decode, and a copy of a set not shown are let go.
 * Subject, a pick (boundary of the deadline): sent and waiting; answered by its set's end; reported just after 5 s
 * and not just before; a late answer still ends the set; no pick without a set, a candidate, or a session, or while
 * the link reconnects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoseSyncerTest {

    private val session = FakeSessionManager()
    private val codec = FakePoseCandidateCodec()

    private fun TestScope.syncer() = PoseSyncer(session, codec, StandardTestDispatcher(testScheduler))

    private fun connectedAs(role: Role) {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje", null), role))
    }

    private fun steps(): List<SessionMessage.PoseStep> = session.sent.filterIsInstance<SessionMessage.PoseStep>()

    private fun sentCandidates(): List<String> =
        session.sent.filterIsInstance<SessionMessage.PoseCandidateBegin>().map { it.poseId }

    /** What went out, in order: steps by name, candidates by id. */
    private fun sentOrder(): List<String> = session.sent.mapNotNull {
        when (it) {
            is SessionMessage.PoseStep -> it.step.name
            is SessionMessage.PoseCandidateBegin -> it.poseId
            else -> null
        }
    }

    /** The photographer's copy of [poseId] of [setId] as the subject receives it. */
    private fun deliverCandidate(setId: String, poseId: String) {
        val copy = checkNotNull(codec.encode(poseId, fakeBitmap()))
        codec.encoded.removeAt(codec.encoded.lastIndex)
        session.deliver(copy.begin(setId))
        for (i in copy.chunks.indices) session.deliver(copy.chunk(setId, i))
        session.deliver(copy.end(setId))
    }

    /**
     * A copy of [bytes] bytes and [width] × [height], as a photographer's phone could claim it; delivered one message at
     * a time, so a large one does not overrun the fake session's buffer.
     */
    private fun TestScope.deliverRaw(setId: String, poseId: String, width: Int, height: Int, bytes: Int) {
        val copy = EncodedPoseCandidate(poseId, "fake", width, height, ByteArray(bytes))
        session.deliver(copy.begin(setId))
        for (i in copy.chunks.indices) {
            session.deliver(copy.chunk(setId, i))
            runCurrent()
        }
        session.deliver(copy.end(setId))
    }

    private fun deliverStep(setId: String, step: PoseSetStep, poseIds: List<String> = emptyList(), by: Role? = null) {
        val poseId = if (step == PoseSetStep.CHOSEN) poseIds.firstOrNull() else null
        session.deliver(
            SessionMessage.PoseStep(setId, step, if (step == PoseSetStep.PICKING) poseIds else emptyList(), poseId, by),
        )
    }

    // ---- photographer: the set ----------------------------------------------------------------------------------

    @Test
    fun `the photographer's set goes out in order, each candidate as it arrives`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()

        syncer.open("set1")
        runCurrent()
        assertEquals(listOf("GENERATING"), sentOrder())

        syncer.candidate("set1", "pose-2", fakeBitmap())
        runCurrent()
        syncer.candidate("set1", "pose-1", fakeBitmap())
        runCurrent()
        syncer.picking("set1", listOf("pose-1", "pose-2"))
        runCurrent()

        assertEquals(listOf("GENERATING", "pose-2", "pose-1", "PICKING"), sentOrder())
        assertEquals(listOf("pose-1", "pose-2"), steps().last().poseIds)
        // Every chunk of each copy is sent before the next message.
        val chunks = session.sent.filterIsInstance<SessionMessage.PoseCandidateChunk>()
        assertEquals(setOf("pose-1", "pose-2"), chunks.map { it.poseId }.toSet())
        assertTrue(chunks.all { it.setId == "set1" })
    }

    @Test
    fun `a choice ends the set once with whose pick it was`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.picking("set1", listOf("pose-1"))
        runCurrent()

        syncer.chosen("set1", "pose-1", Role.SUBJECT)
        runCurrent()
        syncer.close("set1")
        syncer.chosen("set1", "pose-1", Role.PHOTOGRAPHER)
        runCurrent()

        val end = steps().last()
        assertEquals(PoseSetStep.CHOSEN, end.step)
        assertEquals("pose-1", end.poseId)
        assertEquals(Role.SUBJECT, end.by)
        assertEquals(1, steps().count { it.step == PoseSetStep.CHOSEN || it.step == PoseSetStep.CLOSED })
    }

    @Test
    fun `a set closed while generating sends no later candidate`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        syncer.open("set1")
        runCurrent()

        syncer.close("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.picking("set1", listOf("pose-1"))
        runCurrent()

        assertEquals(listOf("GENERATING", "CLOSED"), sentOrder())
    }

    @Test
    fun `a candidate that cannot be encoded is left out`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        syncer.open("set1")
        codec.encodes = false
        syncer.candidate("set1", "pose-1", fakeBitmap())
        runCurrent()
        codec.encodes = true
        syncer.candidate("set1", "pose-2", fakeBitmap())
        syncer.picking("set1", listOf("pose-1", "pose-2"))
        runCurrent()

        assertEquals(listOf("GENERATING", "pose-2", "PICKING"), sentOrder())
    }

    // ---- photographer: the connection ---------------------------------------------------------------------------

    @Test
    fun `nothing goes before a subject is connected, and then the open set goes whole`() = runTest {
        val syncer = syncer()
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.candidate("set1", "pose-3", fakeBitmap())
        syncer.picking("set1", listOf("pose-1", "pose-3"))
        runCurrent()
        assertTrue(session.sent.isEmpty())

        connectedAs(Role.PHOTOGRAPHER)
        syncer.startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("GENERATING", "pose-1", "pose-3", "PICKING"), sentOrder())
    }

    @Test
    fun `after a reconnect the open set is sent again`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val first = Job()
        syncer.startAsSender(TestScope(testScheduler + first))
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        runCurrent()
        first.cancel()
        session.sent.clear()

        syncer.startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("GENERATING", "pose-1"), sentOrder())
    }

    @Test
    fun `a set that ended before the sender heard of it goes as its end alone`() = runTest {
        val syncer = syncer()
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.picking("set1", listOf("pose-1"))
        syncer.chosen("set1", "pose-1", Role.PHOTOGRAPHER)
        connectedAs(Role.PHOTOGRAPHER)

        syncer.startAsSender(backgroundScope)
        runCurrent()

        assertEquals(
            listOf(SessionMessage.PoseStep("set1", PoseSetStep.CHOSEN, poseId = "pose-1", by = Role.PHOTOGRAPHER)),
            session.sent,
        )
    }

    @Test
    fun `a set that ends while a copy is encoded sends no more copies`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.candidate("set1", "pose-2", fakeBitmap())
        codec.onEncode = { syncer.close("set1") }

        syncer.startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("GENERATING", "pose-1", "CLOSED"), sentOrder())
    }

    @Test
    fun `a set closed while its last copy is encoded goes from the copy to its end, without PICKING`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.picking("set1", listOf("pose-1"))
        codec.onEncode = { syncer.close("set1") }

        syncer.startAsSender(backgroundScope)
        runCurrent()

        assertEquals(listOf("GENERATING", "pose-1", "CLOSED"), sentOrder())
    }

    // ---- photographer: the subject's picks ----------------------------------------------------------------------

    @Test
    fun `a pick of the set being picked from is passed on`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val picks = mutableListOf<SubjectPick>()
        backgroundScope.launch { syncer.picks.collect { picks += it } }
        syncer.startAsSender(backgroundScope)
        syncer.open("set1")
        syncer.candidate("set1", "pose-1", fakeBitmap())
        syncer.picking("set1", listOf("pose-1"))
        runCurrent()

        session.deliver(SessionMessage.PosePick("set1", "pose-1"))
        runCurrent()

        assertEquals(listOf(SubjectPick("set1", "pose-1")), picks)
    }

    @Test
    fun `a pick for another set, while generating, after a choice, or after a close is dropped`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val picks = mutableListOf<SubjectPick>()
        backgroundScope.launch { syncer.picks.collect { picks += it } }
        syncer.startAsSender(backgroundScope)
        syncer.open("set1")
        runCurrent()

        session.deliver(SessionMessage.PosePick("set1", "pose-1"))
        runCurrent()
        syncer.picking("set1", listOf("pose-1"))
        session.deliver(SessionMessage.PosePick("set0", "pose-1"))
        runCurrent()
        syncer.chosen("set1", "pose-1", Role.PHOTOGRAPHER)
        session.deliver(SessionMessage.PosePick("set1", "pose-1"))
        runCurrent()
        syncer.open("set2")
        syncer.picking("set2", listOf("pose-1"))
        syncer.close("set2")
        session.deliver(SessionMessage.PosePick("set2", "pose-1"))
        runCurrent()

        assertTrue(picks.isEmpty())
    }

    // ---- subject: the copy of the set ---------------------------------------------------------------------------

    @Test
    fun `the subject builds the set in the photographer's order, missing copies left out`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        deliverStep("set1", PoseSetStep.GENERATING)
        deliverCandidate("set1", "pose-3")
        deliverCandidate("set1", "pose-1")
        runCurrent()
        assertEquals(PoseSetStep.GENERATING, syncer.mirror.value?.step)

        deliverStep("set1", PoseSetStep.PICKING, listOf("pose-1", "pose-2", "pose-3"))
        runCurrent()

        val set = syncer.mirror.value
        assertEquals(PoseSetStep.PICKING, set?.step)
        assertEquals(listOf("pose-1", "pose-3"), set?.ready?.map { it.first })
    }

    @Test
    fun `the set is dropped at its end, which says whether Pick a pose had it`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val ends = mutableListOf<PoseSetEnd>()
        backgroundScope.launch { syncer.ended.collect { ends += it } }
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverStep("set1", PoseSetStep.CLOSED)
        deliverStep("set2", PoseSetStep.GENERATING)
        deliverCandidate("set2", "pose-1")
        deliverStep("set2", PoseSetStep.PICKING, listOf("pose-1"))
        deliverStep("set2", PoseSetStep.CHOSEN, listOf("pose-1"), by = Role.PHOTOGRAPHER)
        runCurrent()

        assertNull(syncer.mirror.value)
        assertEquals(
            listOf(
                PoseSetEnd("set1", PoseSetStep.CLOSED, null, wasPicking = false),
                PoseSetEnd("set2", PoseSetStep.CHOSEN, Role.PHOTOGRAPHER, wasPicking = true),
            ),
            ends,
        )
    }

    @Test
    fun `another set's start replaces the set, and another set's end drops it without a notice`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val ends = mutableListOf<PoseSetEnd>()
        backgroundScope.launch { syncer.ended.collect { ends += it } }
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverCandidate("set1", "pose-1")
        deliverStep("set2", PoseSetStep.GENERATING)
        runCurrent()

        assertEquals("set2", syncer.mirror.value?.setId)
        assertTrue(syncer.mirror.value?.candidates.orEmpty().isEmpty())

        // As after a drop in which set2 ended and set3 came and went: set3's end is all the photographer sends.
        deliverStep("set3", PoseSetStep.CLOSED)
        runCurrent()

        assertNull(syncer.mirror.value)
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `the set stays through a reconnect, and the copies sent again are not read again`() = runTest {
        val syncer = picking()
        assertEquals(2, codec.decoded.size)

        session.setState(SessionState.Reconnecting("s_1", since = 0))
        runCurrent()
        assertEquals(PoseSetStep.PICKING, syncer.mirror.value?.step)

        connectedAs(Role.SUBJECT)
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverCandidate("set1", "pose-1")
        deliverCandidate("set1", "pose-2")
        deliverStep("set1", PoseSetStep.PICKING, listOf("pose-1", "pose-2"))
        runCurrent()

        assertEquals(2, codec.decoded.size)
        assertEquals(PoseSetStep.PICKING, syncer.mirror.value?.step)
        assertEquals(listOf("pose-1", "pose-2"), syncer.mirror.value?.ready?.map { it.first })
    }

    @Test
    fun `the set goes when the session ends`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val receiving = Job()
        syncer.startAsReceiver(TestScope(testScheduler + receiving))
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        runCurrent()
        assertEquals("set1", syncer.mirror.value?.setId)

        receiving.cancel()
        runCurrent()

        assertNull(syncer.mirror.value)
    }

    // ---- subject: a copy ----------------------------------------------------------------------------------------

    @Test
    fun `a broken transfer or a copy that does not decode is let go`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        val copy = checkNotNull(codec.encode("pose-1", fakeBitmap()))
        session.deliver(copy.begin("set1"))
        session.deliver(copy.chunk("set1", 1))
        session.deliver(copy.end("set1"))
        codec.decodes = false
        deliverCandidate("set1", "pose-2")
        runCurrent()
        codec.decodes = true
        deliverCandidate("set1", "pose-3")
        runCurrent()

        assertEquals(setOf("pose-3"), syncer.mirror.value?.candidates?.keys)
    }

    @Test
    fun `a copy over 1024 px is refused at its header, and one at 1024 px is read`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverRaw("set1", "pose-1", width = 1025, height = 800, bytes = 100)
        deliverRaw("set1", "pose-2", width = 768, height = 1025, bytes = 100)
        deliverRaw("set1", "pose-3", width = 1024, height = 1024, bytes = 100)
        runCurrent()

        assertEquals(setOf("pose-3"), syncer.mirror.value?.candidates?.keys)
        assertEquals(1, codec.decoded.size)
    }

    @Test
    fun `a copy over 1 MiB is refused at its header, and one of 1 MiB is read`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverRaw("set1", "pose-1", width = 768, height = 1024, bytes = PoseCandidateAssembler.MAX_BYTES + 1)
        runCurrent()
        deliverRaw("set1", "pose-2", width = 768, height = 1024, bytes = PoseCandidateAssembler.MAX_BYTES)
        runCurrent()

        assertEquals(setOf("pose-2"), syncer.mirror.value?.candidates?.keys)
    }

    @Test
    fun `a copy of a set not shown is let go`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set2", PoseSetStep.GENERATING)
        deliverCandidate("set1", "pose-1")
        runCurrent()

        assertTrue(syncer.mirror.value?.candidates.orEmpty().isEmpty())
        assertTrue(codec.decoded.isEmpty())
    }

    // ---- subject: a pick ----------------------------------------------------------------------------------------

    /** A subject's phone whose Pick a pose shows set1 with pose-1 and pose-2. */
    private fun TestScope.picking(): PoseSyncer {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverCandidate("set1", "pose-1")
        deliverCandidate("set1", "pose-2")
        deliverStep("set1", PoseSetStep.PICKING, listOf("pose-1", "pose-2"))
        runCurrent()
        return syncer
    }

    @Test
    fun `a pick is sent and waits until its set ends`() = runTest {
        val syncer = picking()
        val failures = mutableListOf<String>()
        backgroundScope.launch { syncer.pickFailed.collect { failures += it } }

        assertTrue(syncer.pick("pose-2"))
        assertFalse("one pick at a time", syncer.pick("pose-1"))
        assertEquals(listOf(SessionMessage.PosePick("set1", "pose-2")), session.sent)
        assertEquals(SubjectPick("set1", "pose-2"), syncer.pending.value)

        deliverStep("set1", PoseSetStep.CHOSEN, listOf("pose-2"), by = Role.SUBJECT)
        runCurrent()
        advanceTimeBy(PoseSyncer.PICK_TIMEOUT_MS + 1)
        runCurrent()

        assertNull(syncer.pending.value)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `a pick no step answers is reported just after the deadline and not before`() = runTest {
        val syncer = picking()
        val failures = mutableListOf<String>()
        backgroundScope.launch { syncer.pickFailed.collect { failures += it } }
        syncer.pick("pose-1")

        advanceTimeBy(PoseSyncer.PICK_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(failures.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("pose-1"), failures)
        assertNull(syncer.pending.value)
        assertTrue("it can be tapped again", syncer.pick("pose-1"))
    }

    @Test
    fun `a late answer still ends the set`() = runTest {
        val syncer = picking()
        val ends = mutableListOf<PoseSetEnd>()
        backgroundScope.launch { syncer.ended.collect { ends += it } }
        syncer.pick("pose-1")
        advanceTimeBy(PoseSyncer.PICK_TIMEOUT_MS + 1)
        runCurrent()

        deliverStep("set1", PoseSetStep.CHOSEN, listOf("pose-1"), by = Role.SUBJECT)
        runCurrent()

        assertNull(syncer.mirror.value)
        assertEquals(Role.SUBJECT, ends.single().by)
    }

    @Test
    fun `no pick goes without a set being picked, its candidate, or a session`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val failures = mutableListOf<String>()
        backgroundScope.launch { syncer.pickFailed.collect { failures += it } }
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliverStep("set1", PoseSetStep.GENERATING)
        deliverCandidate("set1", "pose-1")
        runCurrent()

        assertFalse("still generating", syncer.pick("pose-1"))
        deliverStep("set1", PoseSetStep.PICKING, listOf("pose-1"))
        runCurrent()
        assertFalse("no such candidate", syncer.pick("pose-9"))
        session.setState(SessionState.Reconnecting("s_1", since = 0))
        assertFalse("reconnecting", syncer.pick("pose-1"))
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        assertFalse("no session", syncer.pick("pose-1"))
        runCurrent()

        assertTrue(session.sent.isEmpty())
        assertEquals(listOf("pose-1", "pose-9", "pose-1", "pose-1"), failures)
    }
}
