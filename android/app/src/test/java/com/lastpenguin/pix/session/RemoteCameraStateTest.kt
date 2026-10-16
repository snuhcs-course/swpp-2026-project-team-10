// AI-generated with Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraState
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Partitions: the whole applied state in camera.state (Design 2.5.1) as the photographer's phone sends it.
 * The first echo with exposure, flash, and ratio already set; a change of the ratio, the flash, or the exposure on
 * this phone (one final state each); a state change that moves only the zoom (no extra state); a change after stop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCameraStateTest {
    private val session = FakeSessionManager()
    private val camera = FakeCameraController()
    private val handler = RemoteControlHandler(session, camera, clock = { 0L })

    private fun echoes(): List<SessionMessage.CameraStateUpdate> =
        session.sent.filterIsInstance<SessionMessage.CameraStateUpdate>()

    @Test
    fun start_valuesAlreadySet_areInTheFirstEcho() = runTest {
        camera.state.value = CameraState(zoom = 1f, exposure = 3, flash = FlashMode.AUTO, aspect = AspectRatio.R16_9)

        handler.start(this)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(
            SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, false, 3, FlashMode.AUTO, AspectRatio.R16_9),
            echoes().first(),
        )
        handler.stop()
    }

    @Test
    fun ratioFlashOrExposureChange_sendsOneFinalStateEachAtOnce() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        val before = echoes().size

        camera.state.update { it.copy(aspect = AspectRatio.R1_1) }
        runCurrent()
        camera.state.update { it.copy(flash = FlashMode.ON) }
        runCurrent()
        camera.state.update { it.copy(exposure = -2) }
        runCurrent()

        val sent = echoes().drop(before)
        assertEquals(
            listOf(
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, true, 0, FlashMode.OFF, AspectRatio.R1_1),
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, true, 0, FlashMode.ON, AspectRatio.R1_1),
                SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, true, -2, FlashMode.ON, AspectRatio.R1_1),
            ),
            sent,
        )
        handler.stop()
    }

    @Test
    fun stateChangeOfOnlyTheZoom_sendsNoExtraState() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        val before = echoes().size

        camera.state.update { it.copy(zoom = 2f) }
        runCurrent()

        assertEquals(before, echoes().size)
        handler.stop()
    }

    @Test
    fun ratioChangeAfterStop_sendsNothing() = runTest {
        handler.start(this)
        advanceTimeBy(200)
        runCurrent()
        handler.stop()
        val before = echoes().size

        camera.state.update { it.copy(aspect = AspectRatio.R16_9) }
        runCurrent()

        assertEquals(before, echoes().size)
    }
}
