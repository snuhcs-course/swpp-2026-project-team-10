// AI-generated with Claude Code, 2026-11-20, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for one phone zooming at a time, on the photographer's phone (FR-7.6, #236): state-based on who holds the
 * zoom, with the boundary of the hold's timeout.
 * - The subject's step while the zoom is free: applied, and the zoom is the subject's, which the subject hears.
 * - The subject's steps and final value while the photographer's pinch holds it: none applied, no notice, and the
 *   final one answered with the camera's zoom as the photographer's.
 * - The subject's final value after its steps: applied, and the zoom free again.
 * - No request for 1 ms less than the timeout: still the subject's; at the timeout: free, and the lens settles. A later
 *   step starts the timeout again.
 * - The photographer's pinch taking and freeing the zoom: the subject hears both.
 * - stop while the subject holds the zoom: free. start while the photographer holds it: the subject hears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteZoomHoldTest {
    private val session = FakeSessionManager()
    private val camera = FakeCameraController()
    private val handler = RemoteControlHandler(session, camera, InMemoryGuideRepository())
    private val hold = handler.zoomHold
    private val actions = mutableListOf<RemoteAction>()

    private fun holds(): List<Role?> = session.sent.filterIsInstance<SessionMessage.ZoomHoldUpdate>().map { it.by }

    private fun lastFinalEcho(): SessionMessage.CameraStateUpdate =
        session.sent.filterIsInstance<SessionMessage.CameraStateUpdate>().last { it.final }

    /** Starts the handler, lets its first echoes go, and forgets what it sent. */
    private fun TestScope.started() {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()
    }

    @Test
    fun subjectStep_zoomFree_isAppliedAndHoldsTheZoom() = runTest {
        started()

        handler.handle(SessionMessage.ZoomSet(2f))
        runCurrent()

        assertEquals(listOf(2f), camera.requests)
        assertEquals(Role.SUBJECT, hold.holder.value)
        assertEquals(listOf<Role?>(Role.SUBJECT), holds())
        handler.stop()
    }

    @Test
    fun subjectRequests_photographerHolds_areDroppedAndTheFinalOneAnswered() = runTest {
        started()
        hold.take(Role.PHOTOGRAPHER)
        runCurrent()

        handler.handle(SessionMessage.ZoomSet(2f))
        handler.handle(SessionMessage.ZoomSet(2.5f, final = true))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(emptyList<Float>(), camera.requests)
        assertTrue(actions.isEmpty())
        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true), lastFinalEcho())
        assertEquals(Role.PHOTOGRAPHER, hold.holder.value)
        handler.stop()
    }

    @Test
    fun subjectFinal_afterItsSteps_isAppliedAndFreesTheZoom() = runTest {
        started()

        handler.handle(SessionMessage.ZoomSet(2f))
        runCurrent()
        handler.handle(SessionMessage.ZoomSet(2.2f, final = true))
        runCurrent()

        assertEquals(listOf(2f, 2.2f), camera.requests)
        assertNull(hold.holder.value)
        assertEquals(listOf(Role.SUBJECT, null), holds())
        handler.stop()
    }

    @Test
    fun subjectHold_noRequestUntilTheTimeout_isFreedAndTheLensSettles() = runTest {
        started()
        handler.handle(SessionMessage.ZoomSet(2f))
        runCurrent()

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(Role.SUBJECT, hold.holder.value)
        assertEquals(listOf(false), camera.requestFinals)

        advanceTimeBy(1)
        runCurrent()
        assertNull(hold.holder.value)
        assertEquals(listOf(2f, 2f), camera.requests)
        assertEquals(listOf(false, true), camera.requestFinals)
        handler.stop()
    }

    @Test
    fun subjectHold_laterStep_startsTheTimeoutAgain() = runTest {
        started()
        handler.handle(SessionMessage.ZoomSet(2f))
        advanceTimeBy(1_500)
        handler.handle(SessionMessage.ZoomSet(2.4f))

        advanceTimeBy(1_999)
        runCurrent()

        assertEquals(Role.SUBJECT, hold.holder.value)
        handler.stop()
    }

    @Test
    fun photographerPinch_takingAndFreeingTheZoom_isHeardByTheSubject() = runTest {
        started()

        hold.take(Role.PHOTOGRAPHER)
        runCurrent()
        hold.release(Role.PHOTOGRAPHER)
        runCurrent()

        assertEquals(listOf(Role.PHOTOGRAPHER, null), holds())
        handler.stop()
    }

    @Test
    fun stop_subjectHolds_freesTheZoom() = runTest {
        started()
        handler.handle(SessionMessage.ZoomSet(2f))
        runCurrent()

        handler.stop()

        assertNull(hold.holder.value)
    }

    @Test
    fun start_photographerHolds_tellsTheSubjectFirst() = runTest {
        hold.take(Role.PHOTOGRAPHER)

        handler.start(this)
        runCurrent()

        assertEquals(listOf<Role?>(Role.PHOTOGRAPHER), holds())
        handler.stop()
    }
}
