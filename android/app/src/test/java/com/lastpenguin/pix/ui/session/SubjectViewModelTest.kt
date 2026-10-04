package com.lastpenguin.pix.ui.session

import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.session.FakeGuideRepository
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val session = FakeSessionManager()
    private lateinit var viewModel: SubjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SubjectViewModel(session, FakeGuideRepository(), FakeRemoteVideo())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun connect() {
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        session.deliver(SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f, 3f))))
    }

    @Test
    fun `connecting fills the name, the chips, and enables remote zoom`() = runTest(dispatcher) {
        runCurrent()
        assertFalse(viewModel.uiState.value.connected)

        connect()
        runCurrent()

        val ui = viewModel.uiState.value
        assertEquals("Dongje", ui.photographerName)
        assertEquals(listOf(0.6f, 1f, 2f, 3f), ui.zoomStops)
        assertTrue(ui.connected)
    }

    @Test
    fun `a chip shows at once and asks the photographer`() = runTest(dispatcher) {
        connect()
        runCurrent()

        viewModel.onZoomChip(2f)

        assertEquals(2f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf<SessionMessage>(SessionMessage.ZoomSet(2f)), session.sent)
    }

    @Test
    fun `the echoed zoom wins over the chip`() = runTest(dispatcher) {
        connect()
        runCurrent()
        viewModel.onZoomChip(2f)

        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true))
        runCurrent()

        assertEquals(1f, viewModel.uiState.value.zoom, 0f)
    }

    @Test
    fun `chips do nothing before the session is connected`() = runTest(dispatcher) {
        runCurrent()

        viewModel.onZoomChip(2f)

        assertTrue(session.sent.isEmpty())
        assertEquals(1f, viewModel.uiState.value.zoom, 0f)
    }

    @Test
    fun `the name stays after the session ends`() = runTest(dispatcher) {
        connect()
        runCurrent()
        session.setState(SessionState.Ended(com.lastpenguin.pix.session.EndReason.PEER_LEFT))
        runCurrent()

        assertEquals("Dongje", viewModel.uiState.value.photographerName)
        assertFalse(viewModel.uiState.value.connected)
    }
}
