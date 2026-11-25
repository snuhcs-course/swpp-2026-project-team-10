// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The photographer's side of remote control (Design 2.6.5; Testing Documentation 2.1.6), plain JVM on the fakes: the
 * subject's zoom, brightness, flash, and guide moves applied, clamped, echoed, and reported once, with the cases the
 * camera does not apply or applies late, and one phone zooming at a time. The tap to focus (FR-7.7, #273), by
 * partition: a camera that focuses on a point / one with a brightness range only (no focus action); the brightness away
 * from the base (set again, and its state echoed as the subject's) / at the base already, or no range at all
 * (answered at once, nothing set); a point inside the frame, at its edges (0 and 1), just outside, and no number; a camera that is not ready; a
 * drag's request still outstanding, which keeps its own answer; the tap's answer marked as such, and no other echo
 * of the subject's (a brightness set after the tap); a base the camera does not apply, answered with the current
 * state after the wait and then forgotten, so a later change to that value is the photographer's. The photographer's
 * own focus (`camera.focus`): told
 * to the subject while the session runs, and to no one before it starts or after it stops.
 * The subject's shutter (#314): taken by the Camera screen (answered, one action); refused by it; no Camera screen in
 * front; two presses in a row, each answered.
 * The subject's start of AI poses (#276): begun by the Camera screen (answered, one action); not begun by it; no Camera
 * screen in front; a situation this phone does not know; the answer that waits for the photographer's agreement, and one
 * that comes after the session ended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlHandlerTest {

    private val session = FakeSessionManager()
    private val camera = FakeCameraController(exposureRange = -4..4, hasFlash = true)
    private val guides = InMemoryGuideRepository()
    private var now = 0L
    private val attributions = mutableListOf<Role?>()
    private val handler = RemoteControlHandler(session, camera, guides, attributions::add, clock = { now })
    private val actions = mutableListOf<RemoteAction>()

    /** The photographer's base brightness from Settings, in EV, and a camera that focuses on a point (FR-7.7). */
    private val baseBrightness = MutableStateFlow(0f)
    private val focusCamera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
    private val focusHandler =
        RemoteControlHandler(session, focusCamera, guides, clock = { now }, baseBrightness = baseBrightness)

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
        // The steps are not final, so the camera holds its lens through the subject's pinch (Design 2.6.5).
        assertEquals(listOf(false, false), camera.requestFinals)
        assertEquals(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = false), echoes().last())
        assertTrue(actions.isEmpty())

        advanceTimeBy(600)
        runCurrent()
        assertTrue(actions.isEmpty())
        assertEquals(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = true), lastFinal())

        // The pinch ends on the value the camera already shows: the camera only learns that the pinch ended, and
        // there is one notice and one final echo.
        session.sent.clear()
        handler.handle(SessionMessage.ZoomSet(1.8f, final = true))
        runCurrent()
        assertEquals(listOf(1.5f, 1.8f, 1.8f), camera.requests)
        assertEquals(listOf(false, false, true), camera.requestFinals)
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(1.8f)), actions)
        assertEquals(listOf(SessionMessage.CameraStateUpdate(1.8f, Role.SUBJECT, final = true)), echoes())
        handler.stop()
    }

    @Test
    fun `a last value the held lens does not show waits for the camera`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        session.sent.clear()
        // The pinch's steps asked for less than 1×, but the camera held the primary, which shows 1.0×.
        camera.applies = false

        handler.handle(SessionMessage.ZoomSet(0.995f, final = true))
        runCurrent()
        // 0.005 off the shown zoom is not "already applied": the camera gets the value, and nothing is confirmed yet.
        assertEquals(listOf(0.995f), camera.requests)
        assertEquals(listOf(true), camera.requestFinals)
        assertTrue(actions.isEmpty())
        assertTrue(echoes().none { it.by == Role.SUBJECT })

        // The ultrawide takes over and shows it: now it is the subject's.
        camera.applyLocally(0.995f)
        runCurrent()
        assertEquals(listOf<RemoteAction>(RemoteAction.Zoom(0.995f)), actions)
        assertEquals(SessionMessage.CameraStateUpdate(0.995f, Role.SUBJECT, final = false), echoes().last())
        handler.stop()
    }

    @Test
    fun `a pinch whose last value never came is ended when the handler stops`() = runTest {
        handler.start(this)
        advanceTimeBy(200)

        handler.handle(SessionMessage.ZoomSet(1.04f))
        runCurrent()
        assertEquals(listOf(false), camera.requestFinals)

        // The link dropped mid-pinch: the camera is told the pinch ended where it is, so it can settle its lens.
        handler.stop()
        assertEquals(listOf(1.04f, 1.04f), camera.requests)
        assertEquals(listOf(false, true), camera.requestFinals)

        // A pinch that ended normally needs nothing more.
        handler.start(this)
        handler.handle(SessionMessage.ZoomSet(2f, final = true))
        runCurrent()
        handler.stop()
        assertEquals(listOf(false, true, true), camera.requestFinals)
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
    fun `a final brightness or flash request the camera does not apply is answered with the current state`() =
        runTest {
            camera.applies = false
            backgroundScope.launch { handler.actions.collect { actions += it } }
            handler.start(this)
            advanceTimeBy(200)
            runCurrent()
            session.sent.clear()

            handler.handle(SessionMessage.ExposureSet(3, final = true))
            runCurrent()
            assertTrue(echoes().isEmpty())
            advanceTimeBy(600)
            runCurrent()
            assertEquals(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true), echoes().single())

            session.sent.clear()
            handler.handle(SessionMessage.FlashSet(FlashMode.ON))
            runCurrent()
            assertTrue(echoes().isEmpty())
            advanceTimeBy(600)
            runCurrent()
            assertEquals(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true), echoes().single())
            assertTrue(actions.isEmpty())
            handler.stop()
        }

    @Test
    fun `a brightness request applied late is still the subject's`() = runTest {
        camera.applies = false
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        handler.handle(SessionMessage.ExposureSet(2, final = true))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(Role.PHOTOGRAPHER, echoes().single().by)

        camera.state.value = camera.state.value.copy(exposure = 2)
        runCurrent()
        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 2), echoes().last())
        assertEquals(listOf<RemoteAction>(RemoteAction.Brightness(2)), actions)
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

            handler.handle(SessionMessage.GuideMove("g1", 0.3f, 0.4f, 0.9f))
            runCurrent()
            assertEquals(0.3f, guides.state.value.cx, EPSILON)
            assertEquals(0.4f, guides.state.value.cy, EPSILON)
            assertEquals(0.9f, guides.state.value.height, EPSILON)
            assertEquals(listOf<Role?>(Role.SUBJECT), attributions)
            assertTrue(actions.isEmpty())

            // The same limits as the photographer's own gestures (FR-3.2, FR-3.3).
            handler.handle(SessionMessage.GuideMove("g1", 5f, 5f, 9f, final = true))
            runCurrent()
            assertEquals(GuideState.MAX_HEIGHT, guides.state.value.height, EPSILON)
            assertTrue(guides.state.value.cx < 5f)
            assertEquals(listOf<RemoteAction>(RemoteAction.GuideMove), actions)
            handler.stop()
        }

    @Test
    fun `a final value the camera holds still supersedes an outstanding request, and is then confirmed`() =
        runTest {
            camera.applies = false // The step stays outstanding, as a slow CameraX future would.
            backgroundScope.launch { handler.actions.collect { actions += it } }
            handler.start(this)
            advanceTimeBy(200)
            runCurrent()
            session.sent.clear()

            handler.handle(SessionMessage.ExposureSet(2))
            handler.handle(SessionMessage.ExposureSet(0, final = true))
            runCurrent()
            assertEquals(listOf(2, 0), camera.exposureRequests)
            assertTrue(echoes().isEmpty())
            advanceTimeBy(600)
            runCurrent()
            // The camera holds 0 and reported no change: the final request is the subject's, with its notice.
            assertEquals(
                SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 0),
                echoes().single(),
            )
            assertEquals(listOf<RemoteAction>(RemoteAction.Brightness(0)), actions)

            session.sent.clear()
            actions.clear()
            handler.handle(SessionMessage.FlashSet(FlashMode.AUTO))
            handler.handle(SessionMessage.FlashSet(FlashMode.OFF))
            runCurrent()
            assertEquals(listOf(FlashMode.AUTO, FlashMode.OFF), camera.flashRequests)
            advanceTimeBy(600)
            runCurrent()
            assertEquals(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true), echoes().single())
            assertEquals(listOf<RemoteAction>(RemoteAction.Flash(FlashMode.OFF)), actions)
            handler.stop()
        }

    @Test
    fun `a guide move for a guide that was replaced is dropped`() = runTest {
        guides.setGuide(fakeGuide("g2"))
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.GuideMove("g1", 0.3f, 0.4f, 0.9f, final = true))
        runCurrent()

        assertEquals(0.5f, guides.state.value.cx, EPSILON)
        assertTrue(attributions.isEmpty())
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `a guide move without a visible guide is dropped`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.GuideMove("g1", 0.3f, 0.4f, 0.9f, final = true))
        runCurrent()
        assertEquals(GuideState(), guides.state.value)

        guides.setGuide(fakeGuide("g1"))
        guides.update(final = true) { it.copy(visible = false) }
        handler.handle(SessionMessage.GuideMove("g1", 0.3f, 0.4f, 0.9f, final = true))
        runCurrent()
        assertEquals(0.5f, guides.state.value.cx, EPSILON)
        assertTrue(attributions.isEmpty())
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    // ---- The subject's shutter (FR-7.8, #314) ---------------------------------------------------------------------

    private fun answers(): List<SessionMessage.ShutterAnswer> =
        session.sent.filterIsInstance<SessionMessage.ShutterAnswer>()

    @Test
    fun `the subject's shutter takes a photo through the Camera screen, and is answered and reported once`() = runTest {
        var presses = 0
        handler.shutter = {
            presses++
            true
        }
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.ShutterPress)
        runCurrent()

        assertEquals(1, presses)
        assertEquals(listOf(SessionMessage.ShutterAnswer(taken = true)), answers())
        assertEquals(listOf<RemoteAction>(RemoteAction.Shutter), actions)
        handler.stop()
    }

    @Test
    fun `a press the Camera screen takes no photo for is answered so, and is no action`() = runTest {
        // The scene photo is being framed, or the camera still takes the photo before.
        handler.shutter = { false }
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.ShutterPress)
        runCurrent()

        assertEquals(listOf(SessionMessage.ShutterAnswer(taken = false)), answers())
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `a press while the Camera screen is not in front is answered as not taken`() = runTest {
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.ShutterPress)
        runCurrent()

        assertEquals(listOf(SessionMessage.ShutterAnswer(taken = false)), answers())
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `two presses in a row are each asked of the Camera screen and each answered`() = runTest {
        // The camera takes the first and is busy for the second, as with two taps on its own shutter.
        val outcomes = ArrayDeque(listOf(true, false))
        handler.shutter = { outcomes.removeFirst() }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.ShutterPress)
        handler.handle(SessionMessage.ShutterPress)
        runCurrent()

        assertEquals(
            listOf(SessionMessage.ShutterAnswer(taken = true), SessionMessage.ShutterAnswer(taken = false)),
            answers(),
        )
        handler.stop()
    }

    // ---- The subject's start of AI poses (FR-6.15, #276) ----------------------------------------------------------

    private fun startAnswers(): List<SessionMessage.PoseStartAnswer> =
        session.sent.filterIsInstance<SessionMessage.PoseStartAnswer>()

    @Test
    fun `the subject's situation begins the scene photo through the Camera screen, answered and reported once`() =
        runTest {
            val asked = mutableListOf<Situation>()
            handler.poseStarter = { situation, answer ->
                asked += situation
                answer(true)
            }
            backgroundScope.launch { handler.actions.collect { actions += it } }
            handler.start(this)
            advanceTimeBy(200)
            runCurrent()

            handler.handle(SessionMessage.PoseStart("full_body"))
            runCurrent()

            assertEquals(listOf(Situation.FULL_BODY), asked)
            assertEquals(listOf(SessionMessage.PoseStartAnswer(started = true)), startAnswers())
            assertEquals(listOf<RemoteAction>(RemoteAction.PoseStart), actions)
            handler.stop()
        }

    @Test
    fun `a start the Camera screen cannot begin is answered so, and is no action`() = runTest {
        // A step of the photographer's own already runs, or the phone has no front camera for a selfie.
        handler.poseStarter = { _, answer -> answer(false) }
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.PoseStart("selfie"))
        runCurrent()

        assertEquals(listOf(SessionMessage.PoseStartAnswer(started = false)), startAnswers())
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `a start while the Camera screen is not in front, or for a situation this phone does not know, is refused`() =
        runTest {
            handler.start(this)
            advanceTimeBy(200)
            runCurrent()

            handler.handle(SessionMessage.PoseStart("full_body"))
            runCurrent()
            assertEquals(listOf(SessionMessage.PoseStartAnswer(started = false)), startAnswers())

            var asked = 0
            handler.poseStarter = { _, answer ->
                asked++
                answer(true)
            }
            handler.handle(SessionMessage.PoseStart("a situation of a later Pix"))
            runCurrent()
            assertEquals(0, asked)
            assertEquals(2, startAnswers().count { !it.started })
            handler.stop()
        }

    @Test
    fun `a start that waits for the photographer's agreement is answered when they answer, once`() = runTest {
        var later: ((Boolean) -> Unit)? = null
        handler.poseStarter = { _, answer -> later = answer }
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        handler.handle(SessionMessage.PoseStart("default"))
        runCurrent()
        // The AI notice is up on the photographer's phone: no answer yet.
        assertTrue(startAnswers().isEmpty())

        checkNotNull(later)(true)
        runCurrent()
        assertEquals(listOf(SessionMessage.PoseStartAnswer(started = true)), startAnswers())
        assertEquals(listOf<RemoteAction>(RemoteAction.PoseStart), actions)
        handler.stop()
    }

    @Test
    fun `an agreement answered after the session ended tells no one`() = runTest {
        var later: ((Boolean) -> Unit)? = null
        handler.poseStarter = { _, answer -> later = answer }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        handler.handle(SessionMessage.PoseStart("default"))
        runCurrent()

        handler.stop()
        checkNotNull(later)(true)
        runCurrent()

        assertTrue(startAnswers().isEmpty())
    }

    @Test
    fun `a focus request focuses there, puts the brightness back to the base, and is reported as one action`() =
        runTest {
            // +1 EV at 0.5 EV per index is index 2; a drag before the tap left the camera at 3.
            baseBrightness.value = 1f
            focusCamera.state.value = focusCamera.state.value.copy(exposure = 3)
            backgroundScope.launch { focusHandler.actions.collect { actions += it } }
            focusHandler.start(this)
            advanceTimeBy(200)
            runCurrent()
            session.sent.clear()

            focusHandler.handle(SessionMessage.FocusSet(0.62f, 0.4f))
            runCurrent()

            assertEquals(listOf(0.62f to 0.4f), focusCamera.focusPoints)
            assertEquals(listOf(2), focusCamera.exposureRequests)
            // The tap's answer: the state with the base, as the subject's, and no brightness notice.
            assertEquals(
                SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 2, focused = true),
                echoes().single(),
            )
            assertEquals(listOf<RemoteAction>(RemoteAction.Focus(0.62f, 0.4f)), actions)
            focusHandler.stop()
        }

    @Test
    fun `a focus request at the base brightness already is answered with the unchanged state`() = runTest {
        backgroundScope.launch { focusHandler.actions.collect { actions += it } }
        focusHandler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        focusHandler.handle(SessionMessage.FocusSet(0f, 1f))
        runCurrent()

        assertEquals(listOf(0f to 1f), focusCamera.focusPoints)
        // The camera will not report a brightness it already has, and the subject's bar waits for where it starts.
        assertTrue(focusCamera.exposureRequests.isEmpty())
        assertEquals(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, focused = true),
            echoes().single(),
        )
        assertEquals(listOf<RemoteAction>(RemoteAction.Focus(0f, 1f)), actions)
        focusHandler.stop()
    }

    @Test
    fun `a focus request outside the frame, or while the camera is not ready, changes nothing`() = runTest {
        focusCamera.state.value = focusCamera.state.value.copy(exposure = 3)
        backgroundScope.launch { focusHandler.actions.collect { actions += it } }
        focusHandler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        focusHandler.handle(SessionMessage.FocusSet(1.2f, 0.5f))
        focusHandler.handle(SessionMessage.FocusSet(0.5f, -0.1f))
        focusHandler.handle(SessionMessage.FocusSet(Float.NaN, 0.5f))
        focusCamera.setStatus(CameraStatus.STARTING)
        focusHandler.handle(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()

        assertTrue(focusCamera.focusPoints.isEmpty())
        assertTrue(focusCamera.exposureRequests.isEmpty())
        assertTrue(echoes().isEmpty())
        assertTrue(actions.isEmpty())
        focusHandler.stop()
    }

    @Test
    fun `on a camera without point focus the tap only puts the brightness back, and is no focus action`() = runTest {
        // The class's camera: a brightness range, no point focus. Its handler has the default base, 0 EV.
        camera.state.value = camera.state.value.copy(exposure = 2)
        backgroundScope.launch { handler.actions.collect { actions += it } }
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        handler.handle(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()

        assertTrue(camera.focusPoints.isEmpty())
        assertEquals(listOf(0), camera.exposureRequests)
        assertEquals(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, focused = true),
            echoes().single(),
        )
        assertTrue(actions.isEmpty())
        handler.stop()
    }

    @Test
    fun `on a camera with point focus and no brightness range the tap focuses and is answered at once`() = runTest {
        val plain = FakeCameraController(supportsFocusPoint = true)
        val plainHandler = RemoteControlHandler(session, plain, guides, clock = { now })
        backgroundScope.launch { plainHandler.actions.collect { actions += it } }
        plainHandler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        plainHandler.handle(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()

        assertEquals(listOf(0.5f to 0.5f), plain.focusPoints)
        assertTrue(plain.exposureRequests.isEmpty())
        assertEquals(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, focused = true),
            echoes().single(),
        )
        assertEquals(listOf<RemoteAction>(RemoteAction.Focus(0.5f, 0.5f)), actions)
        plainHandler.stop()
    }

    @Test
    fun `only the tap's answer is marked as it, not the subject's later echoes`() = runTest {
        focusCamera.state.value = focusCamera.state.value.copy(exposure = 3)
        focusHandler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()

        focusHandler.handle(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()
        focusHandler.handle(SessionMessage.ExposureSet(2, final = true))
        runCurrent()

        assertEquals(listOf(true, false), echoes().map { it.focused })
        assertEquals(listOf(Role.SUBJECT, Role.SUBJECT), echoes().map { it.by })
        focusHandler.stop()
    }

    @Test
    fun `a base the camera does not apply is answered with the current state, and then forgotten`() = runTest {
        focusCamera.state.value = focusCamera.state.value.copy(exposure = 3)
        focusHandler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()
        focusCamera.applies = false

        focusHandler.handle(SessionMessage.FocusSet(0.5f, 0.5f))
        advanceTimeBy(400)
        runCurrent()
        assertTrue(echoes().isEmpty())

        advanceTimeBy(200)
        runCurrent()
        // Where the camera is, as the tap's answer: the subject's bar starts from there.
        assertEquals(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3, focused = true),
            echoes().single(),
        )

        // The photographer's own change to the base, later, is not the subject's.
        session.sent.clear()
        focusCamera.state.value = focusCamera.state.value.copy(exposure = 0)
        runCurrent()
        assertEquals(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true), echoes().single())
        focusHandler.stop()
    }

    @Test
    fun `a focus request leaves a drag's outstanding brightness request to be answered`() = runTest {
        focusHandler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()
        // The camera takes neither the drag's last value nor the base that the tap asks for.
        focusCamera.applies = false

        focusHandler.handle(SessionMessage.ExposureSet(3, final = true))
        focusHandler.handle(SessionMessage.FocusSet(0.5f, 0.5f))
        advanceTimeBy(600)
        runCurrent()

        assertEquals(listOf(3, 0), focusCamera.exposureRequests)
        // The subject's bar still learns where the camera is: the drag's answer, then the tap's.
        assertEquals(
            listOf(
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true),
                SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, focused = true),
            ),
            echoes(),
        )
        focusHandler.stop()
    }

    @Test
    fun `the subject's quick step glides the camera, and a pinch's steps are applied as they come`() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        // One final value, as a tap on Subject view's readout sends.
        handler.handle(SessionMessage.ZoomSet(2f, final = true))
        runCurrent()
        assertEquals(listOf(2f), camera.glideRequests)

        handler.handle(SessionMessage.ZoomSet(2.4f, final = false))
        handler.handle(SessionMessage.ZoomSet(2.8f, final = false))
        runCurrent()
        assertEquals(listOf(2f), camera.glideRequests)
        assertEquals(listOf(false, false), camera.requestFinals.takeLast(2))

        // The pinch's last value settles the same way; the camera shows it at once where the picture already is.
        handler.handle(SessionMessage.ZoomSet(3f, final = true))
        runCurrent()
        assertEquals(listOf(2f, 3f), camera.glideRequests)

        handler.stop()
    }

    @Test
    fun `what the photographer did is told to the subject only while the session runs`() = runTest {
        handler.didHere(PhotographerAction.Zoom(2f))
        assertTrue(session.sent.isEmpty())

        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()
        handler.didHere(PhotographerAction.Zoom(2f))
        handler.didHere(PhotographerAction.Flash(FlashMode.ON))
        assertEquals(
            listOf<SessionMessage>(
                SessionMessage.PhotographerDid("zoom", zoom = 2f),
                SessionMessage.PhotographerDid("flash", flash = FlashMode.ON),
            ),
            session.sent.toList(),
        )

        handler.stop()
        session.sent.clear()
        handler.didHere(PhotographerAction.GuideMove)
        assertTrue(session.sent.isEmpty())
    }

    @Test
    fun `the photographer's own focus is told to the subject only while the session runs`() = runTest {
        handler.focusedHere(0.2f, 0.3f)
        assertTrue(session.sent.isEmpty())

        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        session.sent.clear()
        handler.focusedHere(0.2f, 0.3f)
        assertEquals(listOf<SessionMessage>(SessionMessage.Focused(0.2f, 0.3f)), session.sent.toList())

        handler.stop()
        session.sent.clear()
        handler.focusedHere(0.5f, 0.5f)
        assertTrue(session.sent.isEmpty())
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
