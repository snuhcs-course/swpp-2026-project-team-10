package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlHandlerTest {

    private val session = FakeSessionManager()
    private val camera = FakeCameraController()
    private var now = 0L
    private val handler = RemoteControlHandler(session, camera, clock = { now })
    private val actions = mutableListOf<RemoteAction>()

    private fun echoes(): List<SessionMessage.CameraStateUpdate> =
        session.sent.filterIsInstance<SessionMessage.CameraStateUpdate>()

    private fun lastFinal(): SessionMessage.CameraStateUpdate = echoes().last { it.final }

    @Test
    fun `starting echoes the current zoom as the photographer's`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = false), echoes().first())
        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true), lastFinal())
        handler.stop()
    }

    @Test
    fun `a zoom request is applied, echoed as the subject's, and reported as an action`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()

        handler.handle(SessionMessage.ZoomSet(2f))
        runCurrent()
        assertEquals(listOf(2f), camera.requests)
        assertEquals(SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = false), echoes().first())

        advanceTimeBy(200)
        runCurrent()
        assertEquals(SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = true), lastFinal())
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(2f)), actions)
        handler.stop()
    }

    @Test
    fun `requests are clamped to the camera's range`() = runTest {
        handler.start(this)
        advanceTimeBy(200)

        handler.handle(SessionMessage.ZoomSet(50f))
        handler.handle(SessionMessage.ZoomSet(0.1f))
        runCurrent()

        assertEquals(listOf(10f, 0.6f), camera.requests)
        handler.stop()
    }

    @Test
    fun `the photographer's own zoom change is echoed as the photographer's`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()

        camera.applyLocally(3f)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(SessionMessage.CameraStateUpdate(3f, Role.PHOTOGRAPHER, final = true), lastFinal())
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `a request the camera does not apply is answered with the current zoom`() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()
        camera.applies = false

        handler.handle(SessionMessage.ZoomSet(2f))
        advanceTimeBy(400)
        runCurrent()
        assertTrue(echoes().isEmpty())

        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true)), echoes())
        handler.stop()
    }

    @Test
    fun `when both change the zoom, the later change wins on both phones`() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()

        handler.handle(SessionMessage.ZoomSet(2f))
        runCurrent()
        camera.applyLocally(3f)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(3f, camera.zoom.value, 0f)
        assertEquals(SessionMessage.CameraStateUpdate(3f, Role.PHOTOGRAPHER, final = true), lastFinal())
        handler.stop()
    }

    @Test
    fun `other messages are ignored`() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()

        handler.handle(SessionMessage.Ping(1))
        runCurrent()

        assertTrue(camera.requests.isEmpty())
        assertTrue(session.sent.isEmpty())
        handler.stop()
    }
}
