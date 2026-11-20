// AI-generated with Claude Code, 2026-11-20, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.session.FakeGuideRepository
import com.lastpenguin.pix.session.FakeRemoteVideo
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.camera.ToggleLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Before
import org.junit.Test

/*
 * Partitions for the subject's zoom while the photographer may zoom too (FR-7.6, #236), state-based on the holder that
 * `camera.zoom.hold` names: the photographer, the subject, or none.
 * - The photographer holds it, then a pinch: nothing sent, the readout on the photographer's zoom, one label.
 * - A pinch held back: still held back to its final value when the zoom is free meanwhile; the next pinch is sent.
 * - The photographer's hold arriving during a pinch: no further step and no final value, the readout back on the
 *   photographer's zoom, one label.
 * - The subject holds it (its own pinch): the pinch goes on.
 * - A quick step while the photographer holds it: nothing sent, a label.
 * - The session dropping: the hold forgotten, so a pinch after the reconnect is sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubjectZoomHoldTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = FakeSessionManager()
    private val labels = mutableListOf<ToggleLabel>()
    private lateinit var viewModel: SubjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SubjectViewModel(session, FakeGuideRepository(), MirrorReference(), FakeRemoteVideo())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun requests(): List<SessionMessage.ZoomSet> = session.sent.filterIsInstance<SessionMessage.ZoomSet>()

    /** Connected to a photographer whose camera shows 1.4×, with the labels collected. */
    private fun TestScope.connected() {
        backgroundScope.launch { viewModel.toggleLabels.collect { labels += it } }
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        session.deliver(SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f, 3f))))
        session.deliver(SessionMessage.CameraStateUpdate(1.4f, Role.PHOTOGRAPHER, final = true))
        runCurrent()
    }

    private fun TestScope.holder(by: Role?) {
        session.deliver(SessionMessage.ZoomHoldUpdate(by))
        runCurrent()
    }

    @Test
    fun pinch_photographerHolds_sendsNothingAndSaysWhoIsZooming() = runTest(dispatcher) {
        connected()
        holder(Role.PHOTOGRAPHER)

        viewModel.onZoomGesture(2f, final = false)
        runCurrent()

        assertEquals(emptyList<SessionMessage.ZoomSet>(), requests())
        assertEquals(1.4f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf<ToggleLabel>(ToggleLabel.ZoomHeld), labels)
    }

    @Test
    fun heldBackPinch_zoomFreedMeanwhile_staysHeldBackUntilItsFingersLift() = runTest(dispatcher) {
        connected()
        holder(Role.PHOTOGRAPHER)
        viewModel.onZoomGesture(2f, final = false)

        holder(null)
        viewModel.onZoomGesture(2.4f, final = false)
        viewModel.onZoomGesture(2.6f, final = true)
        runCurrent()
        assertEquals(emptyList<SessionMessage.ZoomSet>(), requests())

        viewModel.onZoomGesture(1.2f, final = false)
        runCurrent()
        assertEquals(listOf(SessionMessage.ZoomSet(1.2f)), requests())
    }

    @Test
    fun photographerHold_duringAPinch_stopsSendingIt() = runTest(dispatcher) {
        connected()
        viewModel.onZoomGesture(2f, final = false)
        runCurrent()

        holder(Role.PHOTOGRAPHER)
        viewModel.onZoomGesture(2.5f, final = false)
        advanceTimeBy(100)
        viewModel.onZoomGesture(3f, final = true)
        runCurrent()

        assertEquals(listOf(SessionMessage.ZoomSet(2f)), requests())
        assertEquals(1.4f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf<ToggleLabel>(ToggleLabel.ZoomHeld), labels)
    }

    @Test
    fun pinch_subjectHolds_goesOn() = runTest(dispatcher) {
        connected()
        holder(Role.SUBJECT)

        viewModel.onZoomGesture(2f, final = false)
        runCurrent()

        assertEquals(listOf(SessionMessage.ZoomSet(2f)), requests())
        assertEquals(2f, viewModel.uiState.value.zoom, 0f)
    }

    @Test
    fun quickStep_photographerHolds_sendsNothing() = runTest(dispatcher) {
        connected()
        holder(Role.PHOTOGRAPHER)

        viewModel.onZoomTap()
        runCurrent()

        assertEquals(emptyList<SessionMessage.ZoomSet>(), requests())
        assertEquals(1.4f, viewModel.uiState.value.zoom, 0f)
        assertEquals(listOf<ToggleLabel>(ToggleLabel.ZoomHeld), labels)
    }

    @Test
    fun sessionDrop_photographerHeld_holdIsForgotten() = runTest(dispatcher) {
        connected()
        holder(Role.PHOTOGRAPHER)

        session.setState(SessionState.Reconnecting("s_1", since = 0L))
        runCurrent()
        session.setState(SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT))
        runCurrent()
        viewModel.onZoomGesture(2f, final = false)
        runCurrent()

        assertEquals(listOf(SessionMessage.ZoomSet(2f)), requests())
    }
}
