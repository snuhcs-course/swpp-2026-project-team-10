// AI-generated with Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.ui.session

import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.FlashMode
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
import org.junit.Before
import org.junit.Test

/*
 * Partitions: the photographer's frame on Subject view (AD-10). Before any camera.state (4:3); a ratio in a
 * camera.state; a later change; a ratio sent before the screen opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubjectViewModelAspectTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = FakeSessionManager()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun state(aspect: AspectRatio) =
        SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, true, 0, FlashMode.OFF, aspect)

    @Test
    fun aspect_beforeAnyCameraState_isTheDefault43() = runTest(dispatcher) {
        val viewModel = SubjectViewModel(session, FakeGuideRepository(), FakeRemoteVideo())
        runCurrent()

        assertEquals(AspectRatio.R4_3, viewModel.uiState.value.aspect)
    }

    @Test
    fun aspect_followsEveryCameraState() = runTest(dispatcher) {
        val viewModel = SubjectViewModel(session, FakeGuideRepository(), FakeRemoteVideo())
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        runCurrent()

        session.deliver(state(AspectRatio.R16_9))
        runCurrent()
        assertEquals(AspectRatio.R16_9, viewModel.uiState.value.aspect)

        session.deliver(state(AspectRatio.R1_1))
        runCurrent()
        assertEquals(AspectRatio.R1_1, viewModel.uiState.value.aspect)
    }

    @Test
    fun aspect_sentBeforeTheScreenOpened_isShownAtOnce() = runTest(dispatcher) {
        session.deliver(state(AspectRatio.R16_9))

        val viewModel = SubjectViewModel(session, FakeGuideRepository(), FakeRemoteVideo())
        runCurrent()

        assertEquals(AspectRatio.R16_9, viewModel.uiState.value.aspect)
    }
}
