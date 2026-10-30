// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.DescriptionStep
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The photographer's Generate poses on the subject's Generate poses (Design 2.5.1, 2.6.5; Iter3-P16), with the fakes of
 * ModuleFakes.kt.
 * Photographer, the description (state-based): OPEN with its text, then the scene photo's copy, then each change of the
 * text or the writer, then its single end, STARTED or CLOSED; a call for another description or after the end sends
 * nothing; a photo that cannot be encoded is left out.
 * Photographer, the connection: nothing goes while no subject is connected; a sender started later (a reconnect) sends
 * the description as it is now with its photo, or only the end of one that ended meanwhile; a description that ends
 * while its photo is encoded sends no photo; a sender that stops frees a field the subject held (Gone).
 * Photographer, the subject's requests (equivalence partitioning): an edit and a Generate 4 poses or Skip of the open
 * description are passed on; any of another description or after the end is dropped.
 * Subject, the copy (state-based): built from OPEN and the photo, updated by each OPEN, dropped at its end, which is
 * reported with whether a set started, at another description's start, at another one's end without a notice, and when
 * the session ends; kept through a reconnect, where the photo sent again is not read again.
 * Subject, the photo (fault injection, boundary): a broken transfer, a header over 1024 px or 1 MiB (at the limit read),
 * and a photo that does not decode are let go.
 * Subject, the requests (boundary of the deadline): an edit sent only with a description and a session; a Generate 4
 * poses or Skip sent and waiting, answered by the end, reported just after 5 s and not just before, and refused
 * without a description, while the photographer's field holds it, or without a session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DescriptionSyncerTest {

    private val session = FakeSessionManager()
    private val codec = FakePoseCandidateCodec()

    private fun TestScope.syncer() = DescriptionSyncer(session, codec, StandardTestDispatcher(testScheduler))

    private fun connectedAs(role: Role) {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje", null), role))
    }

    /** What went out, in order: descriptions by step, text, and writer, and the photo. */
    private fun sentOrder(): List<String> = session.sent.mapNotNull {
        when (it) {
            is SessionMessage.PoseDescription -> listOfNotNull(it.step.name, it.text.ifEmpty { null }, it.writer?.name)
                .joinToString(" ")

            is SessionMessage.PoseSceneBegin -> "scene"

            else -> null
        }
    }

    private fun deliver(step: DescriptionStep, text: String = "", writer: Role? = null, id: String = "d1") {
        session.deliver(SessionMessage.PoseDescription(id, step, text, writer))
    }

    /** The photographer's photo of [id] as the subject receives it. */
    private fun deliverScene(id: String = "d1") {
        val copy = checkNotNull(codec.encode(id, fakeBitmap()))
        codec.encoded.removeAt(codec.encoded.lastIndex)
        session.deliver(copy.sceneBegin(id))
        for (i in copy.chunks.indices) session.deliver(copy.sceneChunk(id, i))
        session.deliver(copy.sceneEnd(id))
    }

    /** A photo of [bytes] bytes and [width] × [height], as a photographer's phone could claim it. */
    private fun TestScope.deliverRawScene(width: Int, height: Int, bytes: Int) {
        val copy = EncodedPoseCandidate("d1", "fake", width, height, ByteArray(bytes))
        session.deliver(copy.sceneBegin("d1"))
        for (i in copy.chunks.indices) {
            session.deliver(copy.sceneChunk("d1", i))
            runCurrent()
        }
        session.deliver(copy.sceneEnd("d1"))
        runCurrent()
    }

    /** A subject's syncer holding d1 with its photo. */
    private fun TestScope.open(): DescriptionSyncer {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliver(DescriptionStep.OPEN, "wave")
        deliverScene()
        runCurrent()
        return syncer
    }

    // ---- photographer: the description --------------------------------------------------------------------------

    @Test
    fun `the photographer's description goes out in order, its photo after the first step`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()

        syncer.open("d1", fakeBitmap(), "wave", rejected = false)
        runCurrent()
        syncer.update("d1", "wave hello", Role.PHOTOGRAPHER)
        runCurrent()
        syncer.update("d1", "wave hello", null)
        runCurrent()
        syncer.end("d1", started = true)
        runCurrent()

        assertEquals(
            listOf("OPEN wave", "scene", "OPEN wave hello PHOTOGRAPHER", "OPEN wave hello", "STARTED"),
            sentOrder(),
        )
        assertEquals(listOf("d1"), codec.encoded)
    }

    @Test
    fun `a description ends once, and a call for another description changes nothing`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        syncer.open("d1", fakeBitmap(), "", rejected = true)
        runCurrent()

        syncer.update("d0", "other", Role.SUBJECT)
        syncer.end("d0", started = true)
        runCurrent()
        syncer.end("d1", started = false)
        runCurrent()
        syncer.end("d1", started = true)
        syncer.update("d1", "late", null)
        runCurrent()

        assertEquals(listOf("OPEN", "scene", "CLOSED"), sentOrder())
        assertTrue(session.sent.filterIsInstance<SessionMessage.PoseDescription>().first().rejected)
    }

    @Test
    fun `a photo that cannot be encoded is left out`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        codec.encodes = false
        syncer.open("d1", fakeBitmap(), "wave", rejected = false)
        runCurrent()
        syncer.update("d1", "waves", null)
        runCurrent()

        assertEquals(listOf("OPEN wave", "OPEN waves"), sentOrder())
    }

    // ---- photographer: the connection ---------------------------------------------------------------------------

    @Test
    fun `nothing goes before a subject is connected, and a later sender sends the description as it is now`() =
        runTest {
            val syncer = syncer()
            syncer.open("d1", fakeBitmap(), "wave", rejected = false)
            syncer.update("d1", "wave twice", Role.PHOTOGRAPHER)
            runCurrent()
            assertTrue(session.sent.isEmpty())

            connectedAs(Role.PHOTOGRAPHER)
            val first = Job()
            syncer.startAsSender(TestScope(testScheduler + first))
            runCurrent()
            assertEquals(listOf("OPEN wave twice PHOTOGRAPHER", "scene"), sentOrder())

            first.cancel()
            session.sent.clear()
            syncer.end("d1", started = false)
            syncer.startAsSender(backgroundScope)
            runCurrent()
            assertEquals(listOf(SessionMessage.PoseDescription("d1", DescriptionStep.CLOSED)), session.sent)
        }

    @Test
    fun `a description that ends while its photo is encoded sends no photo`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        syncer.startAsSender(backgroundScope)
        runCurrent()
        codec.onEncode = { syncer.end("d1", started = true) }

        syncer.open("d1", fakeBitmap(), "wave", rejected = false)
        runCurrent()

        assertEquals(listOf("OPEN wave", "STARTED"), sentOrder())
    }

    @Test
    fun `a sender that stops frees a field the subject held`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val requests = mutableListOf<DescriptionRequest>()
        backgroundScope.launch { syncer.requests.collect { requests += it } }
        val sending = Job()
        syncer.startAsSender(TestScope(testScheduler + sending))
        runCurrent()

        sending.cancel()
        runCurrent()

        assertEquals(listOf(DescriptionRequest.Gone), requests)
    }

    // ---- photographer: the subject's requests -------------------------------------------------------------------

    @Test
    fun `the subject's edits and its go for the open description are passed on, any other is dropped`() = runTest {
        connectedAs(Role.PHOTOGRAPHER)
        val syncer = syncer()
        val requests = mutableListOf<DescriptionRequest>()
        backgroundScope.launch { syncer.requests.collect { requests += it } }
        syncer.startAsSender(backgroundScope)
        syncer.open("d1", fakeBitmap(), "", rejected = false)
        runCurrent()

        session.deliver(SessionMessage.PoseDescriptionEdit("d1", holding = true, text = "jump"))
        session.deliver(SessionMessage.PoseDescriptionEdit("d0", holding = true, text = "other"))
        session.deliver(SessionMessage.PoseDescriptionGo("d1", "jump", suggest = false))
        runCurrent()
        syncer.end("d1", started = true)
        session.deliver(SessionMessage.PoseDescriptionGo("d1", "jump", suggest = true))
        session.deliver(SessionMessage.PoseDescriptionEdit("d1", holding = false, text = "jump"))
        runCurrent()

        assertEquals(
            listOf(
                DescriptionRequest.Edit("d1", holding = true, text = "jump"),
                DescriptionRequest.Go("d1", "jump", suggest = false),
            ),
            requests,
        )
    }

    // ---- subject: the copy --------------------------------------------------------------------------------------

    @Test
    fun `the subject builds the description with its photo, and each step updates it`() = runTest {
        val syncer = open()
        assertEquals("wave", syncer.mirror.value?.text)
        assertNotNull(syncer.mirror.value?.scene)

        deliver(DescriptionStep.OPEN, "wave hi", Role.PHOTOGRAPHER)
        runCurrent()

        val scene = syncer.mirror.value?.scene
        assertEquals(MirrorDescription("d1", "wave hi", Role.PHOTOGRAPHER, scene = scene), syncer.mirror.value)
    }

    @Test
    fun `the end drops the description and is reported with whether a set started`() = runTest {
        val syncer = open()
        val ends = mutableListOf<DescriptionEnd>()
        backgroundScope.launch { syncer.ended.collect { ends += it } }

        deliver(DescriptionStep.STARTED)
        runCurrent()
        deliver(DescriptionStep.OPEN, id = "d2")
        deliver(DescriptionStep.CLOSED, id = "d2")
        runCurrent()

        assertNull(syncer.mirror.value)
        assertEquals(listOf(DescriptionEnd("d1", started = true), DescriptionEnd("d2", started = false)), ends)
    }

    @Test
    fun `another description's start replaces it, and another one's end drops it without a notice`() = runTest {
        val syncer = open()
        val ends = mutableListOf<DescriptionEnd>()
        backgroundScope.launch { syncer.ended.collect { ends += it } }

        deliver(DescriptionStep.OPEN, "jump", id = "d2")
        runCurrent()
        assertEquals("d2", syncer.mirror.value?.descriptionId)
        assertNull(syncer.mirror.value?.scene)

        deliver(DescriptionStep.CLOSED, id = "d3")
        runCurrent()
        assertNull(syncer.mirror.value)
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `the description stays through a reconnect without reading its photo again, and goes with the session`() =
        runTest {
            connectedAs(Role.SUBJECT)
            val syncer = syncer()
            val receiving = Job()
            syncer.startAsReceiver(TestScope(testScheduler + receiving))
            runCurrent()
            deliver(DescriptionStep.OPEN, "wave")
            deliverScene()
            runCurrent()

            session.setState(SessionState.Reconnecting("s_1", 0L))
            connectedAs(Role.SUBJECT)
            deliver(DescriptionStep.OPEN, "wave")
            deliverScene()
            runCurrent()
            assertEquals(listOf("candidate:d1"), codec.decoded)
            assertNotNull(syncer.mirror.value?.scene)

            receiving.cancel()
            runCurrent()
            assertNull(syncer.mirror.value)
        }

    // ---- subject: the photo -------------------------------------------------------------------------------------

    @Test
    fun `a broken, oversized, or undecodable photo is let go, and one at the limits is read`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        deliver(DescriptionStep.OPEN)
        runCurrent()

        val copy = checkNotNull(codec.encode("d1", fakeBitmap()))
        session.deliver(copy.sceneBegin("d1"))
        session.deliver(copy.sceneChunk("d1", 1))
        session.deliver(copy.sceneEnd("d1"))
        runCurrent()
        deliverRawScene(1025, 768, 1000)
        deliverRawScene(768, 1025, 1000)
        deliverRawScene(768, 1024, PoseCandidateAssembler.MAX_BYTES + 1)
        codec.decodes = false
        deliverScene()
        runCurrent()
        assertNull(syncer.mirror.value?.scene)

        codec.decodes = true
        deliverRawScene(1024, 1024, PoseCandidateAssembler.MAX_BYTES)
        assertNotNull(syncer.mirror.value?.scene)
    }

    // ---- subject: the requests ----------------------------------------------------------------------------------

    @Test
    fun `an edit is sent only with a description and a session`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        syncer.startAsReceiver(backgroundScope)
        runCurrent()
        assertFalse(syncer.edit(holding = true, text = "jump"))

        deliver(DescriptionStep.OPEN)
        runCurrent()
        assertTrue(syncer.edit(holding = true, text = "jump"))
        session.setState(SessionState.Reconnecting("s_1", 0L))
        assertFalse(syncer.edit(holding = false, text = "jump"))

        assertEquals(
            listOf(SessionMessage.PoseDescriptionEdit("d1", holding = true, text = "jump")),
            session.sent.filterIsInstance<SessionMessage.PoseDescriptionEdit>(),
        )
    }

    @Test
    fun `a go is sent and waits until the description's end answers it`() = runTest {
        val syncer = open()

        assertTrue(syncer.go("jump", suggest = false))
        assertEquals(SessionMessage.PoseDescriptionGo("d1", "jump", suggest = false), session.sent.last())
        assertEquals("d1", syncer.going.value)
        assertFalse(syncer.go("jump", suggest = true))

        deliver(DescriptionStep.STARTED)
        runCurrent()
        assertNull(syncer.going.value)
    }

    @Test
    fun `a go no end answers is reported just after 5 s and not just before`() = runTest {
        val syncer = open()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.goFailed.collect { failed += it } }
        syncer.go("", suggest = true)

        advanceTimeBy(DescriptionSyncer.GO_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(failed.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("d1"), failed)
        assertNull(syncer.going.value)
    }

    @Test
    fun `no go without a description or a session, or while the photographer's field holds it`() = runTest {
        connectedAs(Role.SUBJECT)
        val syncer = syncer()
        val failed = mutableListOf<String>()
        backgroundScope.launch { syncer.goFailed.collect { failed += it } }
        syncer.startAsReceiver(backgroundScope)
        runCurrent()

        assertFalse(syncer.go("jump", suggest = false))
        deliver(DescriptionStep.OPEN, "wave", Role.PHOTOGRAPHER)
        runCurrent()
        assertFalse(syncer.go("jump", suggest = false))
        deliver(DescriptionStep.OPEN, "wave")
        runCurrent()
        session.setState(SessionState.Idle)
        assertFalse(syncer.go("jump", suggest = false))
        runCurrent()

        assertEquals(3, failed.size)
        assertTrue(session.sent.none { it is SessionMessage.PoseDescriptionGo })
    }
}
