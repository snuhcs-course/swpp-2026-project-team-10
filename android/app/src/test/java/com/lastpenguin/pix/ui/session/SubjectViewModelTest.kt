// AI-generated with ChatGPT Codex and Claude Code, 2026-11-26, reviewed by Jaewan Park, Joonhyung Han, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.session

import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeGuideRepository
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.GuideReference
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.PhotographerAction
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.fakeBitmap
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.camera.GuideSaveNotice
import com.lastpenguin.pix.ui.camera.ReferenceCorner
import com.lastpenguin.pix.ui.camera.ReferenceDrop
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Subject view's ViewModel (Design 2.6.5; Testing Documentation 2.1.6), plain JVM on the fakes: each control as a
 * request to the photographer, shown at once, sent in steps, and settled by the echo, with the guide, *Save guide*, the
 * tip, and the photographer's pose steps. The tap to focus (FR-7.7, #273), by partition: the rear camera's mirror (x
 * turned back) / the front camera (x as seen); a point inside the frame, just outside it, and no number; before the
 * session is connected, in guide-edit mode (which also takes the bar down), and after a drop; a camera with point
 * focus, with a brightness range only, and with neither; the tap's answer awaited, answered by the subject's state and
 * not by the photographer's own or another echo of this phone's, timed out (1 ms before 1 s / after it), answered after the photographer's own wait of 500 ms (still taken), and
 * ended by a drop. The photographer's
 * own focus (`camera.focus`): shown where this phone has that point, on the rear camera's mirror and on the front
 * camera, and a point outside the frame dropped. The bar when the photographer's frame changes: another ratio and
 * another camera take it down, a state of the same frame does not. What the photographer did (`camera.did`, #292):
 * each action a line, and none for an action this phone does not know or a ratio that is no number; the
 * photographer's own focus a line at the first tap, not at a tap while it shows or in the quiet time after it (just
 * before its end / at its end), with its box at every tap; the photographer's pinch holding the zoom (zooming here),
 * free again, and this phone's own pinch holding it (not the photographer's). The photographer's countdown
 * (`camera.timer`, FR-1.17): each second shown, 0 ending it, a second whose next never comes gone after its wait (1 ms
 * before / at it), and the session dropping; the flash while it runs (nothing sent) and after its 0. The camera menu:
 * open and closed.
 * This phone's shutter (FR-7.8, #314; state-based, boundary): a press asks once and holds the shutter; a second press
 * meanwhile asks nothing; the answer frees it, taken (no line) or not (a line); no answer frees it after
 * SHUTTER_WAIT_MS with a line (1 ms before / at it); a press before the session is connected asks nothing; a drop
 * frees a waiting press without a line; an answer to a press given up before, which answers no later press. With
 * the photographer's timer (FR-1.17): a press while the countdown shows asks to cancel it; a press the timer took up
 * is no refusal; a cancel refused or not answered says nothing. The timer set here too (#318): the photographer's
 * state, with seconds this Pix does not offer shown as off; the menu's Timer cycling, sent each time, confirmed or
 * reverted by the echo; one set before this screen existed (shown at once); a request without an answer (put back
 * after TIMER_WAIT_MS, 1 ms before / at it) and one answered in time; waiting during the countdown and without a link;
 * the photographer's change as a line.
 * The guide's panel (FR-7.2): Hide and Show, each a request for the other state that the echo settles; the opacity
 * shown at once, sent in steps, kept over an echo during the drag and settled by the first echo after it, clamped to
 * 10–90 %; ✕ a request; none sent without a guide or a connection, no opacity for a hidden guide, and none for a
 * guide this phone offered that the photographer has not adopted yet, which would drop it (then sent once it has);
 * neither guide-edit mode nor a move for it meanwhile.
 * Undo (FR-7.2): this phone's ✕ said once its guide is gone, not when the photographer's ✕ takes it, nor when another
 * guide replaces it; Undo asks for the last guide shown, only without a guide on the video and while connected. The
 * photographer's hide, show, opacity, and removal each a line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubjectViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val session = FakeSessionManager()
    private val mirror = FakeGuideRepository()
    private val references = MirrorReference()
    private lateinit var viewModel: SubjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SubjectViewModel(session, mirror, references, FakeRemoteVideo())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun connect() {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        session.deliver(SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f, 3f))))
    }

    /** A photographer whose camera also has a brightness range and a flash (FR-7.3). */
    private fun connectWithControls() {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        session.deliver(
            SessionMessage.Capabilities(
                CameraCapabilities(
                    0.6f,
                    10f,
                    listOf(1f, 2f),
                    exposureRange = -4..4,
                    exposureStep = 0.5f,
                    hasFlash = true,
                ),
            ),
        )
    }

    /** A photographer whose camera focuses on a point and has a brightness range (FR-7.7). */
    private fun connectWithFocus() {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        session.deliver(
            SessionMessage.Capabilities(
                CameraCapabilities(
                    0.6f,
                    10f,
                    listOf(1f, 2f),
                    exposureRange = -4..4,
                    exposureStep = 0.5f,
                    supportsFocusPoint = true,
                ),
            ),
        )
    }

    private fun focuses(): List<SessionMessage.FocusSet> = session.sent.filterIsInstance<SessionMessage.FocusSet>()

    private fun requests(): List<SessionMessage.ZoomSet> = session.sent.filterIsInstance<SessionMessage.ZoomSet>()

    private fun moves(): List<SessionMessage.GuideMove> = session.sent.filterIsInstance<SessionMessage.GuideMove>()

    private fun exposures(): List<SessionMessage.ExposureSet> =
        session.sent.filterIsInstance<SessionMessage.ExposureSet>()

    // ---- The photographer's countdown (FR-1.17) ----------------------------------------------------------------

    @Test
    fun `the photographer's countdown shows each second, and 0 ends it`() = runTest(dispatcher) {
        connect()
        runCurrent()

        val shown = listOf(3, 2, 1, 0).map { left ->
            session.deliver(SessionMessage.Countdown(left))
            runCurrent()
            viewModel.uiState.value.countdown
        }

        assertEquals(listOf(3, 2, 1, null), shown)
    }

    @Test
    fun `a second of the countdown whose next never comes goes after its wait`() = runTest(dispatcher) {
        connect()
        runCurrent()
        session.deliver(SessionMessage.Countdown(2))
        runCurrent()

        advanceTimeBy(SubjectViewModel.COUNTDOWN_STALE_MS - 1)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.countdown)
        advanceTimeBy(1)
        runCurrent()

        assertNull(viewModel.uiState.value.countdown)
    }

    @Test
    fun `the countdown goes when the session drops`() = runTest(dispatcher) {
        connect()
        runCurrent()
        session.deliver(SessionMessage.Countdown(3))
        runCurrent()

        session.setState(SessionState.Reconnecting("s_1", since = 0L))
        runCurrent()

        assertNull(viewModel.uiState.value.countdown)
    }

    @Test
    fun `the camera menu opens and closes`() = runTest(dispatcher) {
        connect()
        runCurrent()

        viewModel.setMenuOpen(true)
        val open = viewModel.uiState.value.menuOpen
        viewModel.setMenuOpen(false)

        assertTrue(open)
        assertFalse(viewModel.uiState.value.menuOpen)
    }

    @Test
    fun `connecting fills the name and the zoom range, and enables remote zoom`() = runTest(dispatcher) {
        runCurrent()
        assertFalse(viewModel.uiState.value.canZoom)

        connect()
        runCurrent()

        val ui = viewModel.uiState.value
        assertEquals("Dongje", ui.photographerName)
        assertEquals(0.6f, ui.minZoom, 0f)
        assertEquals(10f, ui.maxZoom, 0f)
        assertTrue(ui.connected)
        assertTrue(ui.canZoom)
    }

    @Test
    fun `a tap on the readout asks for the next quick step of the photographer's camera, as a final value`() =
        runTest(dispatcher) {
            connect()
            runCurrent()

            viewModel.onZoomTap()
            viewModel.onZoomTap()

            // From 1×: 2×, then back to the widest, 0.6×.
            assertEquals(0.6f, viewModel.uiState.value.zoom, 0f)
            assertEquals(
                listOf(SessionMessage.ZoomSet(2f, final = true), SessionMessage.ZoomSet(0.6f, final = true)),
                requests(),
            )
        }

    @Test
    fun `the reference photo shows while it is its guide's, a new one starts small, and the corner stays here`() =
        runTest(dispatcher) {
            connect()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()
            val photo = fakeBitmap()
            references.set(GuideReference("g1", photo))
            runCurrent()
            assertSame(photo, viewModel.uiState.value.reference)

            viewModel.setReferenceExpanded(true)
            assertTrue(viewModel.uiState.value.referenceExpanded)
            references.set(GuideReference("g1", fakeBitmap()))
            runCurrent()
            assertFalse(viewModel.uiState.value.referenceExpanded)

            // Another guide's photo is not shown, and with no photo there is nothing to enlarge.
            references.set(GuideReference("g2", fakeBitmap()))
            runCurrent()
            assertNull(viewModel.uiState.value.reference)
            viewModel.setReferenceExpanded(true)
            assertFalse(viewModel.uiState.value.referenceExpanded)

            val sent = session.sent.size
            viewModel.setReferenceCorner(ReferenceCorner.BOTTOM_LEFT)
            assertEquals(ReferenceCorner.BOTTOM_LEFT, viewModel.uiState.value.referenceCorner)
            assertEquals(sent, session.sent.size)
        }

    @Test
    fun `the reference thumbnail hides and shows on this phone only, and another guide shows it again`() =
        runTest(dispatcher) {
            connect()
            // Nothing to hide before the photo has come.
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()
            viewModel.setReferenceHidden(true)
            assertFalse(viewModel.uiState.value.referenceHidden)
            references.set(GuideReference("g1", fakeBitmap()))
            runCurrent()
            val sent = session.sent.size

            viewModel.onReferenceDropped(ReferenceDrop(ReferenceCorner.TOP_RIGHT, hidden = true))
            assertTrue(viewModel.uiState.value.referenceHidden)
            assertEquals(ReferenceCorner.TOP_RIGHT, viewModel.uiState.value.referenceCorner)

            // A tap on its strip brings it back.
            viewModel.onReferenceTap()
            assertFalse(viewModel.uiState.value.referenceHidden)
            assertFalse(viewModel.uiState.value.referenceExpanded)
            viewModel.onReferenceTap()
            assertTrue(viewModel.uiState.value.referenceExpanded)

            // Hidden while enlarged, it is small, and small when it is back; nothing enlarges it where it is hidden.
            viewModel.onReferenceDropped(ReferenceDrop(ReferenceCorner.TOP_RIGHT, hidden = true))
            assertFalse(viewModel.uiState.value.referenceExpanded)
            viewModel.setReferenceExpanded(true)
            assertFalse(viewModel.uiState.value.referenceExpanded)
            viewModel.onReferenceTap()
            assertFalse(viewModel.uiState.value.referenceHidden)
            assertFalse(viewModel.uiState.value.referenceExpanded)
            viewModel.setReferenceExpanded(true)

            // The same guide's photo sent again, as after a reconnect, is still the hidden one.
            viewModel.setReferenceHidden(true)
            assertFalse(viewModel.uiState.value.referenceExpanded)
            references.set(GuideReference("g1", fakeBitmap()))
            runCurrent()
            assertTrue(viewModel.uiState.value.referenceHidden)
            // Hidden is this phone's alone, like the size and the corner: nothing was sent.
            assertEquals(sent, session.sent.size)

            mirror.setGuide(fakeGuide("g2"))
            runCurrent()
            references.set(GuideReference("g2", fakeBitmap()))
            runCurrent()
            assertFalse(viewModel.uiState.value.referenceHidden)
        }

    @Test
    fun `a late echo of an earlier quick step neither takes the readout back nor the next tap`() = runTest(dispatcher) {
        connect()
        runCurrent()
        fun echo(zoom: Float, by: Role = Role.SUBJECT) {
            session.deliver(SessionMessage.CameraStateUpdate(zoom, by, final = true))
            runCurrent()
        }

        viewModel.onZoomTap()
        viewModel.onZoomTap()
        echo(2f)
        assertEquals(0.6f, viewModel.uiState.value.zoom, 0f)

        // The third tap goes on from 0.6×, not from the late 2×.
        viewModel.onZoomTap()
        assertEquals(1f, requests().last().ratio, 0f)
        echo(0.6f)
        assertEquals(1f, viewModel.uiState.value.zoom, 0f)
        echo(1f)
        assertEquals(1f, viewModel.uiState.value.zoom, 0f)

        // With no step waiting, every echo shows again; while one waits, the photographer's own change shows too.
        echo(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.zoom, 0f)
        viewModel.onZoomTap()
        echo(3f, by = Role.PHOTOGRAPHER)
        assertEquals(3f, viewModel.uiState.value.zoom, 0f)
    }

    @Test
    fun `a slider step shows at once and asks the photographer`() = runTest(dispatcher) {
        connect()
        runCurrent()

        viewModel.onZoomGesture(2f, final = false)

        assertEquals(2f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf(SessionMessage.ZoomSet(2f, final = false)), requests())
    }

    @Test
    fun `slider steps are sent at most every 50 ms, newest first, and the final value at once`() = runTest(dispatcher) {
        connect()
        runCurrent()

        viewModel.onZoomGesture(1.1f, final = false)
        viewModel.onZoomGesture(1.2f, final = false)
        viewModel.onZoomGesture(1.3f, final = false)
        assertEquals(listOf(SessionMessage.ZoomSet(1.1f)), requests())
        assertEquals(1.3f, viewModel.uiState.value.zoom, 0f)

        advanceTimeBy(60)
        assertEquals(listOf(SessionMessage.ZoomSet(1.1f), SessionMessage.ZoomSet(1.3f)), requests())

        viewModel.onZoomGesture(1.4f, final = false)
        viewModel.onZoomGesture(1.5f, final = true)
        assertEquals(SessionMessage.ZoomSet(1.5f, final = true), requests().last())
        assertEquals(3, requests().size)

        advanceTimeBy(200)
        assertEquals(3, requests().size)
    }

    @Test
    fun `steps are clamped to the photographer's range`() = runTest(dispatcher) {
        connect()
        runCurrent()

        viewModel.onZoomGesture(50f, final = true)

        assertEquals(10f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf(SessionMessage.ZoomSet(10f, final = true)), requests())
    }

    @Test
    fun `echoes wait until the slider interaction ends, then win`() = runTest(dispatcher) {
        connect()
        runCurrent()

        viewModel.onZoomGesture(2f, final = false)
        session.deliver(SessionMessage.CameraStateUpdate(1.5f, Role.SUBJECT, final = false))
        runCurrent()
        assertEquals(2f, viewModel.uiState.value.zoom, 0f)

        viewModel.onZoomGesture(2.2f, final = true)
        session.deliver(SessionMessage.CameraStateUpdate(2f, Role.PHOTOGRAPHER, final = true))
        runCurrent()
        assertEquals(2f, viewModel.uiState.value.zoom, 0f)
    }

    @Test
    fun `slider changes do nothing before the session is connected`() = runTest(dispatcher) {
        runCurrent()

        viewModel.onZoomGesture(2f, final = false)
        viewModel.onZoomGesture(2f, final = true)

        assertTrue(session.sent.isEmpty())
        assertEquals(1f, viewModel.uiState.value.zoom, 0f)
    }

    @Test
    fun `a zoom echoed before the screen existed is the starting zoom`() = runTest(dispatcher) {
        connect()
        session.deliver(SessionMessage.CameraStateUpdate(2.5f, Role.PHOTOGRAPHER, final = true))
        runCurrent()

        val late = SubjectViewModel(session, FakeGuideRepository(), MirrorReference(), FakeRemoteVideo())
        runCurrent()

        assertEquals(2.5f, late.uiState.value.zoom, 0f)
    }

    @Test
    fun `the mirrored guide and its state reach the screen`() = runTest(dispatcher) {
        runCurrent()
        assertNull(viewModel.uiState.value.guide)

        mirror.setGuide(fakeGuide("g1"))
        mirror.update(final = true) { it.copy(guideId = "g1", cx = 0.3f, height = 0.5f) }
        runCurrent()

        assertEquals("g1", viewModel.uiState.value.guide?.id)
        assertEquals(0.3f, viewModel.uiState.value.guideState.cx, 0f)
        assertEquals(0.5f, viewModel.uiState.value.guideState.height, 0f)

        mirror.setGuide(null)
        runCurrent()
        assertNull(viewModel.uiState.value.guide)
    }

    @Test
    fun `guide-edit mode starts off, needs a visible guide, and ends when the guide goes`() = runTest(dispatcher) {
        connect()
        runCurrent()
        assertFalse(viewModel.uiState.value.guideEditing)
        viewModel.setGuideEditing(true)
        assertFalse(viewModel.uiState.value.guideEditing)

        mirror.setGuide(fakeGuide("g1"))
        runCurrent()
        assertFalse(viewModel.uiState.value.guideEditing)
        viewModel.setGuideEditing(true)
        assertTrue(viewModel.uiState.value.guideEditing)
        // A guide gesture is owned here and goes to the photographer as guide.move, never as zoom (#57).
        viewModel.onGuideGesture(0.1f, 0.1f, 1.5f, final = true)
        assertTrue(session.sent.none { it is SessionMessage.ZoomSet })
        assertEquals(1, moves().size)
        viewModel.setGuideEditing(false)
        assertFalse(viewModel.uiState.value.guideEditing)

        viewModel.setGuideEditing(true)
        mirror.update(final = true) { it.copy(visible = false) }
        runCurrent()
        assertFalse(viewModel.uiState.value.guideEditing)
        viewModel.setGuideEditing(true)
        assertFalse(viewModel.uiState.value.guideEditing)
        mirror.update(final = true) { it.copy(visible = true) }
        runCurrent()
        assertFalse(viewModel.uiState.value.guideEditing)

        viewModel.setGuideEditing(true)
        mirror.setGuide(null)
        runCurrent()
        assertFalse(viewModel.uiState.value.guideEditing)
    }

    @Test
    fun `the name stays after the session ends`() = runTest(dispatcher) {
        connect()
        runCurrent()
        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()

        assertEquals("Dongje", viewModel.uiState.value.photographerName)
        assertFalse(viewModel.uiState.value.connected)
    }

    @Test
    fun `collapsed or invalid capabilities stop queued changes and let echoes resume`() = runTest(dispatcher) {
        val ranges = listOf(
            1f to 1f,
            2f to 1f,
            0f to 2f,
            -1f to 2f,
            Float.NaN to 2f,
            1f to Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY to 2f,
        )
        for ((minimum, maximum) in ranges) {
            connect()
            runCurrent()
            viewModel.onZoomGesture(2f, final = false)
            viewModel.onZoomGesture(3f, final = false)
            val count = requests().size

            session.deliver(SessionMessage.Capabilities(CameraCapabilities(minimum, maximum, emptyList())))
            runCurrent()
            assertFalse(viewModel.uiState.value.canZoom)
            viewModel.onZoomGesture(3f, final = true)
            session.deliver(SessionMessage.CameraStateUpdate(1.25f, Role.PHOTOGRAPHER, final = true))
            runCurrent()
            advanceTimeBy(200)
            runCurrent()

            assertEquals(1.25f, viewModel.uiState.value.zoom, 0f)
            assertEquals(count, requests().size)
        }
    }

    @Test
    fun `disconnect cancels a queued nonfinal slider change`() = runTest(dispatcher) {
        connect()
        runCurrent()
        viewModel.onZoomGesture(2f, final = false)
        viewModel.onZoomGesture(3f, final = false)

        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        viewModel.onZoomGesture(3f, final = true)
        advanceTimeBy(200)
        runCurrent()

        assertFalse(viewModel.uiState.value.canZoom)
        assertEquals(listOf(SessionMessage.ZoomSet(2f, final = false)), requests())
    }

    @Test
    fun `a rejected final value still ends adjustment and cancels pending work`() = runTest(dispatcher) {
        connect()
        runCurrent()
        viewModel.onZoomGesture(2f, final = false)
        viewModel.onZoomGesture(3f, final = false)

        viewModel.onZoomGesture(Float.NaN, final = true)
        session.deliver(SessionMessage.CameraStateUpdate(1.5f, Role.PHOTOGRAPHER, final = true))
        runCurrent()
        advanceTimeBy(200)
        runCurrent()

        assertEquals(1.5f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf(SessionMessage.ZoomSet(2f, final = false)), requests())
    }

    @Test
    fun `a guide gesture follows the finger and goes to the photographer like a pinch`() = runTest(dispatcher) {
        connectWithControls()
        mirror.setGuide(fakeGuide("g1"))
        runCurrent()
        viewModel.setGuideEditing(true)

        viewModel.onGuideGesture(0.1f, -0.1f, 1.5f, final = false)
        val state = viewModel.uiState.value.guideState
        assertEquals(0.6f, state.cx, EPSILON)
        assertEquals(0.4f, state.cy, EPSILON)
        assertEquals(1.05f, state.height, EPSILON)
        assertEquals(1, moves().size)
        assertEquals(0.6f, moves().single().cx, EPSILON)
        assertFalse(moves().single().final)

        viewModel.onGuideGesture(0.1f, 0f, 1f, final = false)
        assertEquals(1, moves().size)
        advanceTimeBy(60)
        assertEquals(2, moves().size)
        assertEquals(0.7f, moves().last().cx, EPSILON)

        viewModel.onGuideGesture(0f, 0f, 1f, final = true)
        assertEquals(3, moves().size)
        assertTrue(moves().last().final)
        assertEquals(0.7f, moves().last().cx, EPSILON)
    }

    @Test
    fun `a guide gesture is clamped like the photographer's, and the echo settles the guide after it`() =
        runTest(dispatcher) {
            connectWithControls()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()
            viewModel.setGuideEditing(true)

            viewModel.onGuideGesture(100f, 0f, 100f, final = false)
            assertEquals(GuideState.MAX_HEIGHT, viewModel.uiState.value.guideState.height, EPSILON)
            assertTrue(viewModel.uiState.value.guideState.cx < 100f)

            // An echo during the gesture does not pull the guide back under the finger...
            mirror.update(final = false) { it.copy(cx = 0.2f) }
            runCurrent()
            assertTrue(viewModel.uiState.value.guideState.cx > 0.2f)

            // ...but the first echo after the final value wins.
            viewModel.onGuideGesture(0f, 0f, 1f, final = true)
            mirror.update(final = true) { it.copy(cx = 0.25f, height = 0.8f) }
            runCurrent()
            assertEquals(0.25f, viewModel.uiState.value.guideState.cx, EPSILON)
            assertEquals(0.8f, viewModel.uiState.value.guideState.height, EPSILON)
        }

    @Test
    fun `a move names its guide, and a replaced guide ends the gesture in flight`() = runTest(dispatcher) {
        connectWithControls()
        mirror.setGuide(fakeGuide("g1"))
        runCurrent()
        viewModel.setGuideEditing(true)

        viewModel.onGuideGesture(0.1f, 0f, 1f, final = false)
        viewModel.onGuideGesture(0.1f, 0f, 1f, final = false)
        assertEquals("g1", moves().single().guideId)

        mirror.setGuide(fakeGuide("g2"))
        runCurrent()
        advanceTimeBy(60)
        assertEquals(1, moves().size)
        assertEquals("g2", viewModel.uiState.value.guide?.id)
    }

    @Test
    fun `guide gestures do nothing without the mode, a guide, or a connection`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()
        viewModel.onGuideGesture(0.1f, 0f, 1f, final = true)
        assertTrue(moves().isEmpty())

        mirror.setGuide(fakeGuide("g1"))
        runCurrent()
        viewModel.onGuideGesture(0.1f, 0f, 1f, final = true)
        assertTrue(moves().isEmpty())

        viewModel.setGuideEditing(true)
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        viewModel.onGuideGesture(0.1f, 0f, 1f, final = true)
        assertTrue(moves().isEmpty())
    }

    // ---- The guide's panel (FR-7.2) -------------------------------------------------------------------------------

    private fun opacities(): List<SessionMessage.GuideOpacitySet> =
        session.sent.filterIsInstance<SessionMessage.GuideOpacitySet>()

    @Test
    fun `Hide asks the photographer for the other state, and the echo hides the guide here`() = runTest(dispatcher) {
        connect()
        mirror.setGuide(fakeGuide("g1"))
        runCurrent()

        viewModel.onHideToggle()
        // Nothing changes here before the photographer's phone has hidden it for both.
        assertTrue(viewModel.uiState.value.guideState.visible)
        mirror.update(final = true) { it.copy(visible = false) }
        runCurrent()
        viewModel.onHideToggle()

        assertEquals(
            listOf(SessionMessage.GuideVisibleSet("g1", false), SessionMessage.GuideVisibleSet("g1", true)),
            session.sent.filterIsInstance<SessionMessage.GuideVisibleSet>(),
        )
        assertFalse(viewModel.uiState.value.guideState.visible)
    }

    @Test
    fun `the opacity shows at once, goes in steps, and the first echo after its last value wins`() =
        runTest(dispatcher) {
            connect()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()

            viewModel.onOpacityChange(0.6f, final = false)
            viewModel.onOpacityChange(0.7f, final = false)
            assertEquals(0.7f, viewModel.uiState.value.guideState.opacity, EPSILON)
            assertEquals(listOf(SessionMessage.GuideOpacitySet("g1", 0.6f)), opacities())

            // An echo of an earlier step does not pull the slider back during the drag.
            mirror.update(final = false) { it.copy(opacity = 0.6f) }
            runCurrent()
            assertEquals(0.7f, viewModel.uiState.value.guideState.opacity, EPSILON)
            advanceTimeBy(60)
            assertEquals(SessionMessage.GuideOpacitySet("g1", 0.7f), opacities().last())

            viewModel.onOpacityChange(0.95f, final = true)
            assertEquals(SessionMessage.GuideOpacitySet("g1", GuideState.MAX_OPACITY, final = true), opacities().last())
            mirror.update(final = true) { it.copy(opacity = 0.8f) }
            runCurrent()
            assertEquals(0.8f, viewModel.uiState.value.guideState.opacity, EPSILON)
        }

    @Test
    fun `remove asks the photographer's phone to take the guide off`() = runTest(dispatcher) {
        connect()
        mirror.setGuide(fakeGuide("g1"))
        runCurrent()

        viewModel.onRemoveGuide()

        assertEquals(
            listOf(SessionMessage.GuideRemove("g1")),
            session.sent.filterIsInstance<SessionMessage.GuideRemove>(),
        )
        // The guide goes with the photographer's `guide.clear`, on both phones at once.
        assertEquals("g1", viewModel.uiState.value.guide?.id)
    }

    @Test
    fun `the panel asks nothing for a guide this phone offered until the photographer has adopted it`() =
        runTest(dispatcher) {
            val offering = MutableStateFlow<String?>("g1")
            val offered = SubjectViewModel(session, mirror, references, FakeRemoteVideo(), offering = offering)
            connect()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()
            assertTrue(offered.uiState.value.guideWaiting)

            offered.onHideToggle()
            offered.onOpacityChange(0.7f, final = true)
            offered.onRemoveGuide()
            assertTrue(session.sent.none { it is SessionMessage.GuideVisibleSet || it is SessionMessage.GuideRemove })
            assertTrue(opacities().isEmpty())

            // Nor guide-edit mode, nor a move, which the photographer would drop as well.
            offered.setGuideEditing(true)
            assertFalse(offered.uiState.value.guideEditing)
            offered.onGuideGesture(0.1f, 0f, 1f, final = true)
            assertTrue(moves().isEmpty())

            offering.value = null
            runCurrent()
            offered.setGuideEditing(true)
            offered.onGuideGesture(0.1f, 0f, 1f, final = true)
            assertEquals(listOf("g1"), moves().map { it.guideId })
            offered.onHideToggle()
            assertEquals(
                listOf(SessionMessage.GuideVisibleSet("g1", false)),
                session.sent.filterIsInstance<SessionMessage.GuideVisibleSet>(),
            )
        }

    @Test
    fun `this phone's remove is said once its guide is gone, and not for the photographer's or a replacement`() =
        runTest(dispatcher) {
            val removed = mutableListOf<Unit>()
            backgroundScope.launch { viewModel.guideRemoved.collect { removed += it } }
            connect()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()

            viewModel.onRemoveGuide()
            runCurrent()
            assertTrue("not before the photographer's phone took it", removed.isEmpty())
            mirror.setGuide(null)
            runCurrent()
            assertEquals(1, removed.size)

            // The photographer's own ✕ is its line, with its own Undo; a replaced guide is no removal.
            mirror.setGuide(fakeGuide("g2"))
            runCurrent()
            mirror.setGuide(null)
            mirror.setGuide(fakeGuide("g3"))
            runCurrent()
            viewModel.onRemoveGuide()
            mirror.setGuide(fakeGuide("g4"))
            runCurrent()
            assertEquals(1, removed.size)
        }

    @Test
    fun `Undo asks for the last guide shown, only with no guide on the video and while connected`() =
        runTest(dispatcher) {
            connect()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()
            viewModel.onUndoRemove()
            mirror.setGuide(null)
            runCurrent()

            viewModel.onUndoRemove()
            session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
            runCurrent()
            viewModel.onUndoRemove()

            assertEquals(
                listOf(SessionMessage.GuideRestore("g1")),
                session.sent.filterIsInstance<SessionMessage.GuideRestore>(),
            )
        }

    @Test
    fun `the panel asks nothing without a guide or a connection, nor an opacity for a hidden guide`() =
        runTest(dispatcher) {
            connect()
            runCurrent()
            viewModel.onHideToggle()
            viewModel.onOpacityChange(0.7f, final = true)
            viewModel.onRemoveGuide()

            mirror.setGuide(fakeGuide("g1"))
            mirror.update(final = true) { it.copy(visible = false) }
            runCurrent()
            viewModel.onOpacityChange(0.7f, final = true)
            assertEquals(0.5f, viewModel.uiState.value.guideState.opacity, EPSILON)

            session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
            runCurrent()
            viewModel.onHideToggle()
            viewModel.onRemoveGuide()

            assertTrue(session.sent.none { it is SessionMessage.GuideVisibleSet || it is SessionMessage.GuideRemove })
            assertTrue(opacities().isEmpty())
        }

    @Test
    fun `the brightness slider shows its value at once, sends steps like zoom, and the echo wins afterwards`() =
        runTest(dispatcher) {
            connectWithControls()
            runCurrent()
            assertTrue(viewModel.uiState.value.hasBrightness)
            assertEquals(-4..4, viewModel.uiState.value.exposureRange)

            viewModel.onExposureGesture(1, final = false)
            viewModel.onExposureGesture(2, final = false)
            assertEquals(2, viewModel.uiState.value.exposure)
            assertEquals(listOf(SessionMessage.ExposureSet(1)), exposures())

            session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = false, exposure = 1))
            runCurrent()
            assertEquals(2, viewModel.uiState.value.exposure)
            advanceTimeBy(60)
            assertEquals(listOf(SessionMessage.ExposureSet(1), SessionMessage.ExposureSet(2)), exposures())

            viewModel.onExposureGesture(9, final = true)
            assertEquals(SessionMessage.ExposureSet(4, final = true), exposures().last())
            assertEquals(4, viewModel.uiState.value.exposure)
            session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3))
            runCurrent()
            assertEquals(3, viewModel.uiState.value.exposure)
        }

    @Test
    fun `a tap asks for the focus where the photographer's frame has it, turned back on the rear camera's mirror`() =
        runTest(dispatcher) {
            connectWithFocus()
            runCurrent()
            assertTrue(viewModel.uiState.value.supportsFocusPoint)

            // The rear camera's video is a mirror here: what is at a quarter from the left is at three quarters there.
            assertTrue(viewModel.onFocusTap(0.25f, 0.4f))
            assertEquals(listOf(SessionMessage.FocusSet(0.75f, 0.4f)), focuses())

            // The front camera's video comes mirrored, and the photographer's preview shows it the same way.
            session.deliver(
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, facing = CameraFacing.FRONT),
            )
            runCurrent()
            assertTrue(viewModel.onFocusTap(0.25f, 0.4f))
            assertEquals(SessionMessage.FocusSet(0.25f, 0.4f), focuses().last())
        }

    @Test
    fun `what the photographer did is a line, and an action this phone does not know is none`() = runTest(dispatcher) {
        val lines = mutableListOf<PhotographerLine>()
        backgroundScope.launch { viewModel.photographerLines.collect { lines += it } }
        connectWithControls()
        runCurrent()

        session.deliver(SessionMessage.PhotographerDid("zoom", zoom = 2f))
        session.deliver(SessionMessage.PhotographerDid("brightness", exposure = 3))
        session.deliver(SessionMessage.PhotographerDid("flash", flash = FlashMode.ON))
        session.deliver(SessionMessage.PhotographerDid("guide.move"))
        session.deliver(SessionMessage.PhotographerDid("guide"))
        session.deliver(SessionMessage.PhotographerDid("guide.hide"))
        session.deliver(SessionMessage.PhotographerDid("guide.show"))
        session.deliver(SessionMessage.PhotographerDid("guide.opacity", opacity = 0.3f))
        session.deliver(SessionMessage.PhotographerDid("guide.remove"))
        session.deliver(SessionMessage.PhotographerDid("guide.restore"))
        // From a newer photographer, and a ratio that is none.
        session.deliver(SessionMessage.PhotographerDid("lens"))
        session.deliver(SessionMessage.PhotographerDid("zoom", zoom = Float.NaN))
        runCurrent()

        assertEquals(
            listOf<PhotographerLine>(
                PhotographerLine.Did(PhotographerAction.Zoom(2f)),
                PhotographerLine.Did(PhotographerAction.Brightness(3)),
                PhotographerLine.Did(PhotographerAction.Flash(FlashMode.ON)),
                PhotographerLine.Did(PhotographerAction.GuideMove),
                PhotographerLine.Did(PhotographerAction.Guide),
                PhotographerLine.Did(PhotographerAction.GuideVisible(false)),
                PhotographerLine.Did(PhotographerAction.GuideVisible(true)),
                PhotographerLine.Did(PhotographerAction.GuideOpacity(0.3f)),
                PhotographerLine.Did(PhotographerAction.GuideRemove),
                PhotographerLine.Did(PhotographerAction.GuideRestore),
            ),
            lines,
        )
    }

    @Test
    fun `the photographer's own focus is a line once in a while, as its notice is on Camera`() = runTest(dispatcher) {
        var now = 10_000L
        val timed = SubjectViewModel(session, mirror, references, FakeRemoteVideo(), clock = { now })
        val lines = mutableListOf<PhotographerLine>()
        val boxes = mutableListOf<RemoteFocus>()
        backgroundScope.launch { timed.photographerLines.collect { lines += it } }
        backgroundScope.launch { timed.photographerFocus.collect { boxes += it } }
        connectWithFocus()
        runCurrent()

        session.deliver(SessionMessage.Focused(0.3f, 0.4f))
        runCurrent()
        assertEquals(listOf<PhotographerLine>(PhotographerLine.Focused), lines)

        // While the line shows, and at the last moment of the quiet time after it: the box only.
        now += 1_000
        session.deliver(SessionMessage.Focused(0.5f, 0.5f))
        now = 10_000L + SessionViewModel.FOCUS_NOTICE_MS + SessionViewModel.FOCUS_NOTICE_QUIET_MS - 1
        session.deliver(SessionMessage.Focused(0.6f, 0.6f))
        runCurrent()
        assertEquals(3, boxes.size)
        assertEquals(1, lines.size)

        now += 1
        session.deliver(SessionMessage.Focused(0.7f, 0.7f))
        // A tap outside the frame is neither a box nor a line.
        now += 10_000
        session.deliver(SessionMessage.Focused(1.5f, 0.7f))
        runCurrent()
        assertEquals(4, boxes.size)
        assertEquals(List(2) { PhotographerLine.Focused }, lines)
    }

    @Test
    fun `the photographer's pinch is zooming here, from its first step to its end`() = runTest(dispatcher) {
        connect()
        runCurrent()
        assertFalse(viewModel.photographerZooming.value)

        session.deliver(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER))
        runCurrent()
        assertTrue(viewModel.photographerZooming.value)

        session.deliver(SessionMessage.ZoomHoldUpdate(null))
        runCurrent()
        assertFalse(viewModel.photographerZooming.value)

        // This phone's own pinch holding the zoom is not the photographer zooming.
        session.deliver(SessionMessage.ZoomHoldUpdate(Role.SUBJECT))
        runCurrent()
        assertFalse(viewModel.photographerZooming.value)
    }

    @Test
    fun `the photographer's own focus shows where this phone has that point`() = runTest(dispatcher) {
        val shown = mutableListOf<RemoteFocus>()
        backgroundScope.launch { viewModel.photographerFocus.collect { shown += it } }
        connectWithFocus()
        runCurrent()

        // The rear camera's video is a mirror here.
        session.deliver(SessionMessage.Focused(0.25f, 0.4f))
        // Not a point of the frame.
        session.deliver(SessionMessage.Focused(1.5f, 0.4f))
        session.deliver(SessionMessage.Focused(0.5f, Float.NaN))
        runCurrent()
        assertEquals(listOf(RemoteFocus(0.75f, 0.4f)), shown)

        session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, facing = CameraFacing.FRONT),
        )
        runCurrent()
        session.deliver(SessionMessage.Focused(0.25f, 0.4f))
        runCurrent()
        assertEquals(RemoteFocus(0.25f, 0.4f), shown.last())
    }

    @Test
    fun `a tap asks for nothing outside the frame, in guide-edit mode, or before the session is connected`() =
        runTest(dispatcher) {
            runCurrent()
            assertFalse(viewModel.onFocusTap(0.5f, 0.5f))

            connectWithFocus()
            mirror.setGuide(fakeGuide("g1"))
            runCurrent()
            assertFalse(viewModel.onFocusTap(1.01f, 0.5f))
            assertFalse(viewModel.onFocusTap(0.5f, -0.01f))
            assertFalse(viewModel.onFocusTap(Float.NaN, 0.5f))

            // The touch is the guide's while the mode is on, and the bar it would bring goes.
            viewModel.setBrightnessOpen(true)
            viewModel.setGuideEditing(true)
            assertFalse(viewModel.uiState.value.brightnessOpen)
            assertFalse(viewModel.onFocusTap(0.5f, 0.5f))
            assertTrue(focuses().isEmpty())

            viewModel.setGuideEditing(false)
            assertTrue(viewModel.onFocusTap(0.5f, 0.5f))
        }

    @Test
    fun `a tap needs a camera with point focus or a brightness range`() = runTest(dispatcher) {
        // Zoom alone.
        connect()
        runCurrent()
        assertFalse(viewModel.uiState.value.supportsFocusPoint)
        assertFalse(viewModel.onFocusTap(0.5f, 0.5f))
        assertTrue(focuses().isEmpty())

        // A brightness range without point focus: the tap still puts the brightness back and brings the bar.
        connectWithControls()
        runCurrent()
        assertTrue(viewModel.onFocusTap(0.5f, 0.5f))
        assertEquals(1, focuses().size)
    }

    @Test
    fun `a tap waits for its answer, which gives the brightness the camera went back to`() = runTest(dispatcher) {
        connectWithFocus()
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3))
        runCurrent()
        assertEquals(3, viewModel.uiState.value.exposure)

        viewModel.onFocusTap(0.5f, 0.5f)
        assertTrue(viewModel.uiState.value.baseAwaited)

        // The photographer's own change is not the answer, nor is the echo of this phone's earlier pinch.
        session.deliver(SessionMessage.CameraStateUpdate(2f, Role.PHOTOGRAPHER, final = true, exposure = 3))
        session.deliver(SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = true, exposure = 3))
        runCurrent()
        assertTrue(viewModel.uiState.value.baseAwaited)

        session.deliver(
            SessionMessage.CameraStateUpdate(2f, Role.SUBJECT, final = true, exposure = 0, focused = true),
        )
        runCurrent()
        assertFalse(viewModel.uiState.value.baseAwaited)
        assertEquals(0, viewModel.uiState.value.exposure)
    }

    @Test
    fun `the bar goes when the photographer's frame changes, since it stood beside a point of the old one`() =
        runTest(dispatcher) {
            connectWithFocus()
            runCurrent()
            viewModel.onFocusTap(0.5f, 0.5f)
            viewModel.setBrightnessOpen(true)

            // The same frame: only the brightness changed.
            session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, exposure = 2))
            runCurrent()
            assertTrue(viewModel.uiState.value.brightnessOpen)

            session.deliver(
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, aspect = AspectRatio.R16_9),
            )
            runCurrent()
            assertFalse(viewModel.uiState.value.brightnessOpen)

            viewModel.setBrightnessOpen(true)
            session.deliver(
                SessionMessage.CameraStateUpdate(
                    1f,
                    Role.PHOTOGRAPHER,
                    final = true,
                    aspect = AspectRatio.R16_9,
                    facing = CameraFacing.FRONT,
                ),
            )
            runCurrent()
            assertFalse(viewModel.uiState.value.brightnessOpen)
        }

    @Test
    fun `an answer that the photographer gives only after its own wait is still taken`() = runTest(dispatcher) {
        connectWithFocus()
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3))
        runCurrent()

        // The photographer's camera did not report the base, so its phone answers after its 500 ms, and the link adds
        // its own delay.
        viewModel.onFocusTap(0.5f, 0.5f)
        advanceTimeBy(700)
        assertTrue(viewModel.uiState.value.baseAwaited)

        session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 0, focused = true),
        )
        runCurrent()
        assertFalse(viewModel.uiState.value.baseAwaited)
        assertEquals(0, viewModel.uiState.value.exposure)
    }

    @Test
    fun `a tap that gets no answer stops waiting after a moment, and at once when the link drops`() =
        runTest(dispatcher) {
            connectWithFocus()
            runCurrent()

            viewModel.onFocusTap(0.5f, 0.5f)
            advanceTimeBy(999)
            assertTrue(viewModel.uiState.value.baseAwaited)
            advanceTimeBy(2)
            assertFalse(viewModel.uiState.value.baseAwaited)

            viewModel.onFocusTap(0.5f, 0.5f)
            viewModel.setBrightnessOpen(true)
            session.setState(SessionState.Reconnecting("s_1", 0L))
            runCurrent()
            assertFalse(viewModel.uiState.value.baseAwaited)
            assertFalse(viewModel.uiState.value.brightnessOpen)
            assertFalse(viewModel.onFocusTap(0.5f, 0.5f))
        }

    // ---- This phone's shutter (FR-7.8, #314) ---------------------------------------------------------------------

    private fun presses(): Int = session.sent.count { it is SessionMessage.ShutterPress }

    /** The number of the press sent last, which its answer names. */
    private fun pressed(): Int = session.sent.filterIsInstance<SessionMessage.ShutterPress>().last().press

    private fun TestScope.refusals(): MutableList<Unit> {
        val refusals = mutableListOf<Unit>()
        backgroundScope.launch { viewModel.shutterRefusals.collect { refusals += it } }
        return refusals
    }

    @Test
    fun `a press of the shutter asks the photographer's phone once, and holds the shutter until the answer`() =
        runTest(dispatcher) {
            connect()
            val refusals = refusals()
            runCurrent()

            assertTrue(viewModel.onShutter())
            assertEquals(1, presses())
            assertTrue(viewModel.uiState.value.shutterWaiting)

            // A second press while the first waits asks nothing.
            assertFalse(viewModel.onShutter())
            assertEquals(1, presses())

            session.deliver(SessionMessage.ShutterAnswer(taken = true, press = pressed()))
            runCurrent()
            assertFalse(viewModel.uiState.value.shutterWaiting)
            assertTrue(refusals.isEmpty())

            // Free again: the next press asks.
            assertTrue(viewModel.onShutter())
            assertEquals(2, presses())
        }

    @Test
    fun `a press the photographer's phone took no photo for frees the shutter and is said`() = runTest(dispatcher) {
        connect()
        val refusals = refusals()
        runCurrent()
        viewModel.onShutter()

        session.deliver(SessionMessage.ShutterAnswer(taken = false, press = pressed()))
        runCurrent()

        assertFalse(viewModel.uiState.value.shutterWaiting)
        assertEquals(1, refusals.size)
    }

    @Test
    fun `a press without an answer is given up after its wait, and not before`() = runTest(dispatcher) {
        connect()
        val refusals = refusals()
        runCurrent()
        viewModel.onShutter()

        advanceTimeBy(SubjectViewModel.SHUTTER_WAIT_MS - 1)
        runCurrent()
        assertTrue(viewModel.uiState.value.shutterWaiting)
        assertTrue(refusals.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertFalse(viewModel.uiState.value.shutterWaiting)
        assertEquals(1, refusals.size)

        // An answer that comes after all changes nothing more.
        session.deliver(SessionMessage.ShutterAnswer(taken = false, press = pressed()))
        runCurrent()
        assertEquals(1, refusals.size)
    }

    @Test
    fun `an answer that comes after its press was given up does not answer the next press`() = runTest(dispatcher) {
        connect()
        val refusals = refusals()
        runCurrent()
        viewModel.onShutter()
        advanceTimeBy(SubjectViewModel.SHUTTER_WAIT_MS)
        runCurrent()
        assertEquals(1, refusals.size)

        // The second press waits, and the first one's answer arrives only now.
        assertTrue(viewModel.onShutter())
        val pressed = session.sent.filterIsInstance<SessionMessage.ShutterPress>().map { it.press }
        assertEquals(2, pressed.distinct().size)
        session.deliver(SessionMessage.ShutterAnswer(taken = false, press = pressed[0]))
        runCurrent()
        assertTrue(viewModel.uiState.value.shutterWaiting)
        assertEquals(1, refusals.size)

        session.deliver(SessionMessage.ShutterAnswer(taken = true, press = pressed[1]))
        runCurrent()
        assertFalse(viewModel.uiState.value.shutterWaiting)
        assertEquals(1, refusals.size)
    }

    @Test
    fun `a press before the session is connected asks nothing`() = runTest(dispatcher) {
        runCurrent()

        assertFalse(viewModel.onShutter())

        assertEquals(0, presses())
        assertFalse(viewModel.uiState.value.shutterWaiting)
    }

    @Test
    fun `a drop frees a press that waits, without a line`() = runTest(dispatcher) {
        connect()
        val refusals = refusals()
        runCurrent()
        viewModel.onShutter()

        session.setState(SessionState.Reconnecting("s_1", 0L))
        runCurrent()
        assertFalse(viewModel.uiState.value.shutterWaiting)
        advanceTimeBy(SubjectViewModel.SHUTTER_WAIT_MS + 1)
        runCurrent()

        // Subject view says that it reconnects; the shutter is dimmed meanwhile.
        assertTrue(refusals.isEmpty())
        assertFalse(viewModel.onShutter())
    }

    @Test
    fun `brightness and flash are hidden, and do nothing, without a range or a flash`() = runTest(dispatcher) {
        connect()
        runCurrent()
        assertFalse(viewModel.uiState.value.hasBrightness)
        assertFalse(viewModel.uiState.value.hasFlash)

        viewModel.setBrightnessOpen(true)
        viewModel.onExposureGesture(2, final = true)
        viewModel.onFlashTap()

        assertFalse(viewModel.uiState.value.brightnessOpen)
        assertTrue(exposures().isEmpty())
        assertTrue(session.sent.none { it is SessionMessage.FlashSet })
    }

    @Test
    fun `the flash button cycles off, auto, on, and the echo confirms or reverts it`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()
        assertTrue(viewModel.uiState.value.hasFlash)

        viewModel.onFlashTap()
        assertEquals(FlashMode.AUTO, viewModel.uiState.value.flash)
        assertEquals(
            listOf(SessionMessage.FlashSet(FlashMode.AUTO)),
            session.sent.filterIsInstance<SessionMessage.FlashSet>(),
        )
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, flash = FlashMode.AUTO))
        runCurrent()
        assertEquals(FlashMode.AUTO, viewModel.uiState.value.flash)

        viewModel.onFlashTap()
        assertEquals(FlashMode.ON, viewModel.uiState.value.flash)
        // The photographer kept auto (for example, their own tap won): the echo reverts the button.
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, flash = FlashMode.AUTO))
        runCurrent()
        assertEquals(FlashMode.AUTO, viewModel.uiState.value.flash)
    }

    @Test
    fun `a press while the photographer's countdown shows asks to cancel it, and says nothing`() =
        runTest(dispatcher) {
            connect()
            val refusals = refusals()
            runCurrent()
            session.deliver(SessionMessage.Countdown(3))
            runCurrent()

            assertTrue(viewModel.onShutter())
            assertEquals(true, session.sent.filterIsInstance<SessionMessage.ShutterPress>().last().cancel)
            assertTrue(viewModel.uiState.value.shutterWaiting)

            session.deliver(SessionMessage.ShutterAnswer(taken = false, press = pressed(), timer = true))
            runCurrent()
            assertFalse(viewModel.uiState.value.shutterWaiting)
            assertTrue(refusals.isEmpty())
        }

    @Test
    fun `a press without a countdown asks for a photo, and one the timer took up is no refusal`() =
        runTest(dispatcher) {
            connect()
            val refusals = refusals()
            runCurrent()

            assertTrue(viewModel.onShutter())
            assertEquals(false, session.sent.filterIsInstance<SessionMessage.ShutterPress>().last().cancel)

            // The photographer's timer is on: the countdown comes instead of the photo.
            session.deliver(SessionMessage.ShutterAnswer(taken = false, press = pressed(), timer = true))
            runCurrent()
            assertFalse(viewModel.uiState.value.shutterWaiting)
            assertTrue(refusals.isEmpty())
        }

    @Test
    fun `a cancel that is refused or not answered says nothing`() = runTest(dispatcher) {
        connect()
        val refusals = refusals()
        runCurrent()
        session.deliver(SessionMessage.Countdown(3))
        runCurrent()

        // The photographer left Camera, which stopped the countdown itself.
        viewModel.onShutter()
        session.deliver(SessionMessage.ShutterAnswer(taken = false, press = pressed()))
        runCurrent()
        assertFalse(viewModel.uiState.value.shutterWaiting)

        session.deliver(SessionMessage.Countdown(2))
        runCurrent()
        viewModel.onShutter()
        advanceTimeBy(SubjectViewModel.SHUTTER_WAIT_MS)
        runCurrent()
        assertFalse(viewModel.uiState.value.shutterWaiting)

        // "Can't take a photo right now" is for a press that asked for one.
        assertTrue(refusals.isEmpty())
    }

    // ---- The photographer's timer, set here too (#318) ------------------------------------------------------------

    private fun timerSets() = session.sent.filterIsInstance<SessionMessage.TimerSet>().map { it.seconds }

    @Test
    fun `the timer is the photographer's, as their phone says it`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()
        assertEquals(ShutterTimer.OFF, viewModel.uiState.value.timer)

        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, timer = 10))
        runCurrent()
        assertEquals(ShutterTimer.TEN, viewModel.uiState.value.timer)

        // Seconds this Pix does not offer, from a later one: shown as off, which this menu can name.
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, timer = 5))
        runCurrent()
        assertEquals(ShutterTimer.OFF, viewModel.uiState.value.timer)
    }

    @Test
    fun `a timer set before this screen existed shows at once`() = runTest(dispatcher) {
        // The photographer's first state comes as the subject connects, before Subject view is there to hear it.
        val earlier = FakeSessionManager()
        earlier.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        earlier.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, timer = 10))

        val late = SubjectViewModel(earlier, mirror, references, FakeRemoteVideo())
        runCurrent()

        assertEquals(ShutterTimer.TEN, late.uiState.value.timer)
    }

    @Test
    fun `a timer request that gets no answer is put back after its wait, and not before`() = runTest(dispatcher) {
        connectWithControls()
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, timer = 3))
        runCurrent()

        // A photographer's phone of before #318 does not know the request, and says nothing.
        viewModel.onTimerTap()
        assertEquals(ShutterTimer.TEN, viewModel.uiState.value.timer)
        advanceTimeBy(SubjectViewModel.TIMER_WAIT_MS - 1)
        runCurrent()
        assertEquals(ShutterTimer.TEN, viewModel.uiState.value.timer)
        advanceTimeBy(1)
        runCurrent()
        // Back to what the photographer's phone said last.
        assertEquals(ShutterTimer.THREE, viewModel.uiState.value.timer)
    }

    @Test
    fun `a timer request answered in time stays, whatever the wait`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()

        viewModel.onTimerTap()
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, timer = 3))
        runCurrent()
        advanceTimeBy(SubjectViewModel.TIMER_WAIT_MS * 2)
        runCurrent()

        assertEquals(ShutterTimer.THREE, viewModel.uiState.value.timer)
    }

    @Test
    fun `the menu's Timer cycles off, 3 s, 10 s, and the echo confirms or reverts it`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()

        viewModel.onTimerTap()
        assertEquals(ShutterTimer.THREE, viewModel.uiState.value.timer)
        assertEquals(listOf(3), timerSets())
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, timer = 3))
        runCurrent()
        assertEquals(ShutterTimer.THREE, viewModel.uiState.value.timer)

        viewModel.onTimerTap()
        assertEquals(ShutterTimer.TEN, viewModel.uiState.value.timer)
        assertEquals(listOf(3, 10), timerSets())
        // The photographer turned it off at the same moment, and theirs came later: the echo reverts the item.
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, timer = 0))
        runCurrent()
        assertEquals(ShutterTimer.OFF, viewModel.uiState.value.timer)

        viewModel.onTimerTap()
        viewModel.onTimerTap()
        viewModel.onTimerTap()
        assertEquals(listOf(3, 10, 3, 10, 0), timerSets())
    }

    @Test
    fun `the timer waits while the countdown runs, and without a link`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()
        session.deliver(SessionMessage.Countdown(3))
        runCurrent()

        viewModel.onTimerTap()
        assertEquals(ShutterTimer.OFF, viewModel.uiState.value.timer)
        assertTrue(timerSets().isEmpty())

        session.deliver(SessionMessage.Countdown(0))
        runCurrent()
        session.setState(SessionState.Reconnecting("s_1", 0L))
        runCurrent()
        viewModel.onTimerTap()
        assertTrue(timerSets().isEmpty())
    }

    @Test
    fun `the photographer's change of the timer is a line`() = runTest(dispatcher) {
        val lines = mutableListOf<PhotographerLine>()
        backgroundScope.launch { viewModel.photographerLines.collect { lines += it } }
        connectWithControls()
        runCurrent()

        session.deliver(SessionMessage.PhotographerDid("timer", seconds = 3))
        session.deliver(SessionMessage.PhotographerDid("timer", seconds = 0))
        runCurrent()

        assertEquals(
            listOf<PhotographerLine>(
                PhotographerLine.Did(PhotographerAction.Timer(ShutterTimer.THREE)),
                PhotographerLine.Did(PhotographerAction.Timer(ShutterTimer.OFF)),
            ),
            lines,
        )
    }

    @Test
    fun `the flash waits while the photographer's countdown runs`() = runTest(dispatcher) {
        connectWithControls()
        runCurrent()
        session.deliver(SessionMessage.Countdown(3))
        runCurrent()

        viewModel.onFlashTap()
        assertEquals(FlashMode.OFF, viewModel.uiState.value.flash)
        assertTrue(session.sent.none { it is SessionMessage.FlashSet })

        session.deliver(SessionMessage.Countdown(0))
        runCurrent()
        viewModel.onFlashTap()
        assertEquals(
            listOf(SessionMessage.FlashSet(FlashMode.AUTO)),
            session.sent.filterIsInstance<SessionMessage.FlashSet>(),
        )
    }

    // ---- Save guide (FR-6.16) and the photographer's set (FR-6.15), Iter3-P16 ---------------------------------------
    // Partitions: Save guide with a guide (saved with the reference copy, the anchor and source its image carried, and
    // the mirror's state; then filled), already saved (history again, still kept, #297), a store that fails, no guide; the set: none, GENERATING,
    // PICKING, ended; Pick a pose opened by itself once per set.

    private val box = NormalizedRect(0.3f, 0.2f, 0.7f, 0.9f)

    private fun saving(store: SavedGuideStore = SavedGuideStore.InMemory()): SubjectViewModel =
        SubjectViewModel(session, mirror, references, FakeRemoteVideo(), store)

    /** The photographer's guide on the video, as the subject's syncer leaves it, with its reference copy. */
    private fun guideOnVideo(): ReferenceGuide {
        val guide = fakeGuide("g1").copy(anchor = box, source = GuideSource.GENERATED)
        mirror.setGuide(guide)
        mirror.update(final = true) { GuideState("g1", cx = 0.4f, cy = 0.6f, height = 0.8f) }
        references.set(GuideReference("g1", fakeBitmap()))
        return guide
    }

    @Test
    fun saveGuide_keepsTheGuideAsThePhotographersPhoneDoes_andFillsTheButton() = runTest {
        val store = SavedGuideStore.InMemory()
        val model = saving(store)
        val notices = mutableListOf<GuideSaveNotice>()
        backgroundScope.launch { model.guideSaveNotices.collect { notices += it } }
        connect()
        guideOnVideo()
        runCurrent()

        model.onSaveGuide()
        runCurrent()

        val (saved, state) = store.load("g1").getOrThrow()
        assertEquals(box, saved.anchor)
        assertEquals(GuideSource.GENERATED, saved.source)
        assertSame(references.reference.value?.photo, saved.reference)
        assertEquals(0.4f, state.cx, 0f)
        assertTrue(model.uiState.value.guideSaved)
        assertFalse(model.uiState.value.savingGuide)
        assertEquals(listOf(GuideSaveNotice.SAVED), notices)
    }

    @Test
    fun saveGuide_whenAlreadySaved_removesIt() = runTest {
        val store = SavedGuideStore.InMemory()
        val model = saving(store)
        val notices = mutableListOf<GuideSaveNotice>()
        backgroundScope.launch { model.guideSaveNotices.collect { notices += it } }
        connect()
        guideOnVideo()
        runCurrent()
        model.onSaveGuide()
        runCurrent()

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf(GuideSaveNotice.SAVED, GuideSaveNotice.REMOVED), notices)
        // History again (#297): still on this phone, no longer a favorite.
        assertEquals(listOf(false), store.saved.value.map { it.favorite })
    }

    @Test
    fun saveGuide_aStoreThatFails_saysSo() = runTest {
        val failing = object : SavedGuideStore by SavedGuideStore.InMemory() {
            override suspend fun save(guide: ReferenceGuide, state: GuideState, favorite: Boolean): Result<SavedGuide> =
                Result.failure(IOException("Storage full"))
        }
        val model = saving(failing)
        val notices = mutableListOf<GuideSaveNotice>()
        backgroundScope.launch { model.guideSaveNotices.collect { notices += it } }
        connect()
        guideOnVideo()
        runCurrent()

        model.onSaveGuide()
        runCurrent()

        assertEquals(listOf(GuideSaveNotice.FAILED), notices)
        assertFalse(model.uiState.value.guideSaved)
        assertFalse(model.uiState.value.savingGuide)
    }

    @Test
    fun saveGuide_withoutAGuide_doesNothing() = runTest {
        val store = SavedGuideStore.InMemory()
        val model = saving(store)
        connect()
        runCurrent()

        model.onSaveGuide()
        runCurrent()

        assertTrue(store.saved.value.isEmpty())
    }

    @Test
    fun thePhotographersSet_isShownWhileOpen_andPickAPoseOpensOncePerSet() = runTest {
        val poses = PoseSyncer(session, FakePoseCandidateCodec(), dispatcher)
        val model = SubjectViewModel(session, mirror, references, FakeRemoteVideo(), poses = poses)
        connect()
        poses.startAsReceiver(backgroundScope)
        runCurrent()
        assertNull(model.uiState.value.poseStep)

        session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.GENERATING))
        runCurrent()
        assertEquals("set1" to PoseSetStep.GENERATING, model.uiState.value.poseSetId to model.uiState.value.poseStep)
        session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.PICKING))
        runCurrent()
        assertEquals(PoseSetStep.PICKING, model.uiState.value.poseStep)
        assertTrue(model.claimSetOpening("set1"))
        assertFalse(model.claimSetOpening("set1"))

        session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.CLOSED))
        runCurrent()
        assertNull(model.uiState.value.poseStep)
        assertTrue("a new set opens again", model.claimSetOpening("set2"))
    }

    // The photographer's tip of the guide on the video (FR-4.10, #213), state-based: none; a tip before its guide,
    // with its guide, with another guide, and after its guide went; closed until the tip button, which opens and
    // closes it and takes its dot away, and is the only way to close it; new words for the same guide keep the
    // banner as it is; a tip that goes takes the banner with it, and one that comes back starts closed; a guide the
    // photographer hides closes an open tip, which stays closed when the guide shows again.

    private val tip = PoseTip("Move a little closer.\nHold the phone at your waist, tilted up a little.")

    private fun tipMessage(guideId: String, tip: PoseTip = this.tip) = SessionMessage.GuideTip(guideId, tip.text)

    private fun TestScope.withTips(): SubjectViewModel {
        val tips = TipSyncer(session, MutableStateFlow(null))
        val model = SubjectViewModel(session, mirror, references, FakeRemoteVideo(), tips = tips)
        connect()
        tips.startAsReceiver(backgroundScope)
        runCurrent()
        return model
    }

    @Test
    fun thePhotographersTip_isThereOnlyWithItsGuide_andClosedUntilTheButton() = runTest {
        val model = withTips()
        assertNull(model.uiState.value.tip)

        // The tip can come before the guide's image has.
        session.deliver(tipMessage("g1"))
        runCurrent()
        assertNull(model.uiState.value.tip)

        mirror.setGuide(fakeGuide("g1"))
        runCurrent()
        assertEquals(SubjectTip("g1", tip, open = false, unseen = true), model.uiState.value.tip)

        model.onTipButton()
        assertEquals(SubjectTip("g1", tip, open = true, unseen = false), model.uiState.value.tip)
        model.onTipButton()
        assertEquals(SubjectTip("g1", tip, open = false, unseen = false), model.uiState.value.tip)
    }

    @Test
    fun anotherGuide_hasNoTipUntilItsOwnComes_whichStartsClosed() = runTest {
        val model = withTips()
        mirror.setGuide(fakeGuide("g1"))
        session.deliver(tipMessage("g1"))
        runCurrent()
        model.onTipButton()

        mirror.setGuide(fakeGuide("g2"))
        runCurrent()
        assertNull(model.uiState.value.tip)

        val other = PoseTip("Step back.")
        session.deliver(tipMessage("g2", other))
        runCurrent()
        assertEquals(SubjectTip("g2", other, open = false, unseen = true), model.uiState.value.tip)

        mirror.setGuide(null)
        runCurrent()
        assertNull(model.uiState.value.tip)
    }

    @Test
    fun aGuideThePhotographerHides_closesAnOpenTip_whichStaysClosedWhenTheGuideShowsAgain() = runTest {
        val model = withTips()
        mirror.setGuide(fakeGuide("g1"))
        session.deliver(tipMessage("g1"))
        runCurrent()
        model.onTipButton()
        assertEquals(SubjectTip("g1", tip, open = true, unseen = false), model.uiState.value.tip)

        // Hidden with its guide, the tip must not come back open by itself: only the button opens it (FR-4.10).
        mirror.update(final = true) { it.copy(visible = false) }
        runCurrent()
        assertEquals(SubjectTip("g1", tip, open = false, unseen = false), model.uiState.value.tip)
        mirror.update(final = true) { it.copy(visible = true) }
        runCurrent()
        assertEquals(SubjectTip("g1", tip, open = false, unseen = false), model.uiState.value.tip)

        // A closed tip, also one not looked at yet, is left as it is.
        session.deliver(SessionMessage.GuideTipClear)
        runCurrent()
        session.deliver(tipMessage("g1"))
        runCurrent()
        mirror.update(final = true) { it.copy(visible = false) }
        runCurrent()
        assertEquals(SubjectTip("g1", tip, open = false, unseen = true), model.uiState.value.tip)
    }

    @Test
    fun newWordsForTheSameGuide_keepTheBannerAsItIs_andATipThatGoesTakesItAway() = runTest {
        val model = withTips()
        mirror.setGuide(fakeGuide("g1"))
        session.deliver(tipMessage("g1"))
        runCurrent()
        model.onTipButton()

        val korean = PoseTip("조금 다가가세요.")
        session.deliver(tipMessage("g1", korean))
        runCurrent()
        assertEquals(SubjectTip("g1", korean, open = true, unseen = false), model.uiState.value.tip)

        session.deliver(SessionMessage.GuideTipClear)
        runCurrent()
        assertNull(model.uiState.value.tip)
        // Nothing to open or close without a tip.
        model.onTipButton()
        assertNull(model.uiState.value.tip)

        session.deliver(tipMessage("g1"))
        runCurrent()
        assertEquals(SubjectTip("g1", tip, open = false, unseen = true), model.uiState.value.tip)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
