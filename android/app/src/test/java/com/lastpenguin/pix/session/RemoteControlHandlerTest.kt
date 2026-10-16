// AI-generated with Claude Code, 2026-10-16, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlHandlerTest {

    private val session = FakeSessionManager()
    private val camera = FakeCameraController(exposureRange = -4..4, hasFlash = true)
    private val guides = InMemoryGuideRepository()
    private var now = 0L
    private val attributions = mutableListOf<Role?>()
    private val handler = RemoteControlHandler(session, camera, guides, attributions::add, clock = { now })
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

        handler.handle(SessionMessage.ZoomSet(2f, final = true))
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

        handler.handle(SessionMessage.ZoomSet(2f, final = true))
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
    fun `pinch steps are applied and echoed, and only the final value makes a notice`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()

        handler.handle(SessionMessage.ZoomSet(1.5f))
        runCurrent()
        handler.handle(SessionMessage.ZoomSet(1.8f))
        runCurrent()
        assertEquals(listOf(1.5f, 1.8f), camera.requests)
        assertEquals(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = false), echoes().last())
        assertTrue(actions.isEmpty())

        advanceTimeBy(600)
        runCurrent()
        assertTrue(actions.isEmpty())
        assertEquals(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = true), lastFinal())

        // The pinch ends on the value the camera already shows: no new request, one notice, one final echo.
        session.sent.clear()
        handler.handle(SessionMessage.ZoomSet(1.8f, final = true))
        runCurrent()
        assertEquals(listOf(1.5f, 1.8f), camera.requests)
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(1.8f)), actions)
        assertEquals(listOf(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = true)), echoes())
        handler.stop()
    }

    @Test
    fun `a step the camera does not apply is not answered but the final value is`() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()
        camera.applies = false

        handler.handle(SessionMessage.ZoomSet(2f))
        advanceTimeBy(700)
        runCurrent()
        assertTrue(echoes().isEmpty())

        handler.handle(SessionMessage.ZoomSet(2.5f, final = true))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true)), echoes())
        handler.stop()
    }

    @Test
    fun `a final request the camera applies late is still the subject's`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()
        camera.applies = false

        handler.handle(SessionMessage.ZoomSet(2f, final = true))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true)), echoes())
        assertTrue(actions.isEmpty())

        // The camera finishes saving a photo and applies the request it held.
        camera.applyLocally(2f)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(2f)), actions)
        assertEquals(SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = true), lastFinal())
        handler.stop()
    }

    @Test
    fun `no stale answer while the camera opens another lens`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()
        camera.applies = false
        camera.setStatus(CameraStatus.STARTING)

        handler.handle(SessionMessage.ZoomSet(0.6f, final = true))
        advanceTimeBy(900)
        runCurrent()
        assertTrue(echoes().isEmpty())

        camera.applyLocally(0.6f)
        camera.setStatus(CameraStatus.READY)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(0.6f)), actions)
        assertEquals(
            listOf(SessionMessage.CameraStateUpdate(0.6f, Role.SUBJECT, final = true)),
            echoes().filter {
                it.final
            },
        )
        handler.stop()
    }

    @Test
    fun `a newer request cancels the final echo still due for the previous step`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()

        handler.handle(SessionMessage.ZoomSet(1.8f))
        runCurrent()
        assertEquals(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = false), echoes().last())

        // Within the 100 ms final delay, the pinch ends on a value that needs another lens.
        camera.applies = false
        camera.setStatus(CameraStatus.STARTING)
        handler.handle(SessionMessage.ZoomSet(0.6f, final = true))
        advanceTimeBy(300)
        runCurrent()
        assertTrue(echoes().none { it.final })

        camera.applyLocally(0.6f)
        camera.setStatus(CameraStatus.READY)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(
            listOf(SessionMessage.CameraStateUpdate(0.6f, Role.SUBJECT, final = true)),
            echoes().filter {
                it.final
            },
        )
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(0.6f)), actions)
        handler.stop()
    }

    @Test
    fun `brightness steps are applied and echoed as steps, and the final value makes one notice`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        handler.handle(SessionMessage.ExposureSet(2))
        runCurrent()
        assertEquals(listOf(2), camera.exposureRequests)
        assertEquals(2, echoes().last().exposure)
        assertEquals(Role.SUBJECT, echoes().last().by)
        assertFalse(echoes().last().final)
        assertTrue(actions.isEmpty())

        handler.handle(SessionMessage.ExposureSet(3, final = true))
        runCurrent()
        val last = echoes().last()
        assertEquals(3, last.exposure)
        assertEquals(Role.SUBJECT, last.by)
        assertTrue(last.final)
        assertEquals(listOf<RemoteAction>(RemoteAction.Brightness(3)), actions)
        handler.stop()
    }

    @Test
    fun `a brightness request is clamped to the range, and one already applied is confirmed at once`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        handler.handle(SessionMessage.ExposureSet(9, final = true))
        runCurrent()
        assertEquals(listOf(4), camera.exposureRequests)
        assertEquals(4, echoes().last().exposure)
        assertEquals(listOf<RemoteAction>(RemoteAction.Brightness(4)), actions)

        session.sent.clear()
        actions.clear()
        handler.handle(SessionMessage.ExposureSet(4, final = true))
        runCurrent()
        assertEquals(listOf(4), camera.exposureRequests)
        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 4), echoes().single())
        assertEquals(listOf<RemoteAction>(RemoteAction.Brightness(4)), actions)
        handler.stop()
    }

    @Test
    fun `without an exposure range or a flash, a request is answered with the unchanged state`() = runTest {
        val plain = FakeCameraController()
        val handler = RemoteControlHandler(session, plain, guides, clock = { now })
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        handler.handle(SessionMessage.ExposureSet(2, final = true))
        handler.handle(SessionMessage.FlashSet(FlashMode.ON))
        runCurrent()

        assertTrue(plain.exposureRequests.isEmpty())
        assertTrue(plain.flashRequests.isEmpty())
        assertEquals(2, echoes().size)
        assertTrue(echoes().all { it == SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true) })
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `a flash request is applied, echoed as the subject's, and reported`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        handler.handle(SessionMessage.FlashSet(FlashMode.AUTO))
        runCurrent()

        assertEquals(listOf(FlashMode.AUTO), camera.flashRequests)
        assertEquals(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, flash = FlashMode.AUTO),
            echoes().single(),
        )
        assertEquals(listOf<RemoteAction>(RemoteAction.Flash(FlashMode.AUTO)), actions)
        handler.stop()
    }

    @Test
    fun `a guide move is clamped by the repository, attributed to the subject, and reported once per gesture`() =
        runTest {
            guides.setGuide(fakeGuide("g1"))
            backgroundScope.launch { handler.actions.collect { actions += it } }
            handler.start(this)
            advanceTimeBy(200)
            runCurrent()

            handler.handle(SessionMessage.GuideMove(0.3f, 0.4f, 0.9f))
            runCurrent()
            assertEquals(0.3f, guides.state.value.cx, EPSILON)
            assertEquals(0.4f, guides.state.value.cy, EPSILON)
            assertEquals(0.9f, guides.state.value.height, EPSILON)
            assertEquals(listOf<Role?>(Role.SUBJECT), attributions)
            assertTrue(actions.isEmpty())

            // The same limits as the photographer's own gestures (FR-3.2, FR-3.3).
            handler.handle(SessionMessage.GuideMove(5f, 5f, 9f, final = true))
            runCurrent()
            assertEquals(GuideState.MAX_HEIGHT, guides.state.value.height, EPSILON)
            assertTrue(guides.state.value.cx < 5f)
            assertEquals(listOf<RemoteAction>(RemoteAction.GuideMove), actions)
            handler.stop()
        }

    @Test
    fun `a guide move without a visible guide is dropped`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.GuideMove(0.3f, 0.4f, 0.9f, final = true))
        runCurrent()
        assertEquals(GuideState(), guides.state.value)

        guides.setGuide(fakeGuide("g1"))
        guides.update(final = true) { it.copy(visible = false) }
        handler.handle(SessionMessage.GuideMove(0.3f, 0.4f, 0.9f, final = true))
        runCurrent()
        assertEquals(0.5f, guides.state.value.cx, EPSILON)
        assertTrue(attributions.isEmpty())
        assertTrue(actions.isEmpty())
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

    private companion object {
        const val EPSILON = 0.0001f
    }
}
