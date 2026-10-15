// AI-generated with Claude Code, 2026-10-15, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Partitions (Testing Documentation 2.1.6, state-based): what each end of a joined session does with the kept code
 * (#81). Kept: Session not found after a join (NOT_FOUND, EXPIRED), Connection lost (CONNECTION_LOST), and a
 * reconnect that failed for another reason (ERROR). Forgotten: Leave, the photographer's End session (PEER_LEFT),
 * a cancelled or failed join (CANCELLED, ERROR), a room gone while reconnecting (NOT_FOUND), and Start as the
 * photographer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Robolectric for ProcessLifecycleOwner, which the ViewModel observes for the background limit.
@RunWith(AndroidJUnit4::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val session = FakeSessionManager()
    private val camera = FakeCameraController()
    private lateinit var model: SessionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val syncer = GuideSyncer(session, InMemoryGuideRepository(), InMemoryGuideRepository(), FakeGuideImageCodec())
        model = SessionViewModel(session, camera, syncer, RemoteControlHandler(session, camera))
        store.put("session", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun join_sessionNotFoundOrExpired_keepsTheCodeForEditing() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()

        session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        runCurrent()
        assertEquals(CODE, model.lastCode)

        session.setState(SessionState.Ended(EndReason.EXPIRED))
        runCurrent()
        assertEquals(CODE, model.lastCode)
    }

    @Test
    fun leave_afterAJoin_forgetsTheCodeAtOnce() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()

        model.leave()

        assertNull(model.lastCode)
    }

    @Test
    fun sessionEnd_byThePhotographerOrAFailedJoin_forgetsTheCode() = runTest(dispatcher) {
        for (reason in listOf(EndReason.PEER_LEFT, EndReason.CANCELLED, EndReason.ERROR)) {
            model.joinRoom(CODE)
            runCurrent()

            session.setState(SessionState.Ended(reason))
            runCurrent()

            assertNull("after $reason", model.lastCode)
        }
    }

    @Test
    fun connectionLost_keepsTheCode_andReconnectJoinsItAgain() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        assertEquals(CODE, model.lastCode)

        model.reconnect()
        runCurrent()

        val join = SessionEntry.RoomCode(CODE) to Role.SUBJECT
        assertEquals(listOf(join, join), session.starts)
    }

    @Test
    fun reconnect_intoARoomThatIsGone_forgetsTheCode() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        model.reconnect()
        runCurrent()

        session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        runCurrent()

        assertNull(model.lastCode)
    }

    @Test
    fun reconnect_thatFailsOtherwise_keepsTheCodeForAnotherTry() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        model.reconnect()
        runCurrent()

        session.setState(SessionState.Ended(EndReason.ERROR))
        runCurrent()

        assertEquals(CODE, model.lastCode)
    }

    @Test
    fun startRoom_asThePhotographer_forgetsASubjectCode() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()

        model.startRoom()
        runCurrent()

        assertNull(model.lastCode)
        assertEquals(SessionEntry.NewRoom to Role.PHOTOGRAPHER, session.starts.last())
    }

    private companion object {
        const val CODE = "123456"
    }
}
