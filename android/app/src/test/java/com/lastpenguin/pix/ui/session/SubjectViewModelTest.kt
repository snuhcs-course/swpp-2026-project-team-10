package com.lastpenguin.pix.ui.session

import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeGuideRepository
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val session = FakeSessionManager()
    private val mirror = FakeGuideRepository()
    private lateinit var viewModel: SubjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SubjectViewModel(session, mirror, FakeRemoteVideo())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun connect() {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        session.deliver(SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f, 3f))))
    }

    private fun requests(): List<SessionMessage.ZoomSet> = session.sent.filterIsInstance<SessionMessage.ZoomSet>()

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

        val late = SubjectViewModel(session, FakeGuideRepository(), FakeRemoteVideo())
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
}
