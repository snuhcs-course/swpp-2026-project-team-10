// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.FakeInvitationManager
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideImageCodec
import com.lastpenguin.pix.session.FakeGuideReferenceCodec
import com.lastpenguin.pix.session.FakeSessionManager
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.IcePath
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.MobileDataNotice
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Partitions (Testing Documentation 2.1.6, state-based): what each end of a joined session does with the kept code
 * (#81). Kept: Session not found after a join (NOT_FOUND, EXPIRED), Connection lost (CONNECTION_LOST), and a
 * reconnect that failed for another reason (ERROR). Forgotten: Leave, the photographer's End session (PEER_LEFT),
 * a cancelled or failed join (CANCELLED, ERROR), a room gone while reconnecting (NOT_FOUND), and Start as the
 * photographer. The mobile-data notice (FR-6.13): a cellular path the first time (pending until a screen shows it,
 * remembered only then), a second cellular path, once said before, a Wi-Fi path, no path, a session that ends
 * before a screen shows it (dropped, not remembered, said in the next session), and a short drop (still pending).
 * Reconnecting (Design 2.6.7): the photographer's frames go on, and its capabilities go again once connected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Robolectric for ProcessLifecycleOwner, which the ViewModel observes for the background limit.
@RunWith(AndroidJUnit4::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val session = FakeSessionManager()
    private val camera = FakeCameraController()
    private val mobileData = MobileDataNotice.InMemory()
    private val invitations = FakeInvitationManager()
    private val codec = FakeGuideImageCodec()
    private lateinit var model: SessionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val syncer = GuideSyncer(
            session,
            InMemoryGuideRepository(),
            InMemoryGuideRepository(),
            codec,
            FakeGuideReferenceCodec(),
            MirrorReference(),
            dispatcher,
        )
        model = SessionViewModel(
            session,
            camera,
            syncer,
            RemoteControlHandler(session, camera, InMemoryGuideRepository()),
            mobileData,
            invitations,
        )
        store.put("session", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun guideFromTheSubject_isNoticedWithTheirName() = runTest(dispatcher) {
        val notices = mutableListOf<SessionNotice>()
        backgroundScope.launch { model.notices.collect { notices += it } }
        session.setState(SessionState.Connected("s_1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        runCurrent()

        val image = codec.encode(fakeGuide("s1"))
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
        runCurrent()

        assertEquals(listOf(SessionNotice.RemoteGuide("Junhyeong")), notices)
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
    fun connectionLost_keepsTheCode_andReconnectRejoinsWithIt() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        assertEquals(CODE, model.lastCode)

        model.reconnect()
        runCurrent()

        assertEquals(
            listOf(SessionEntry.RoomCode(CODE) to Role.SUBJECT, SessionEntry.Rejoin(CODE) to Role.SUBJECT),
            session.starts,
        )
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

    private fun connected() = session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))

    @Test
    fun reconnecting_keepsTheFrames_andSendsTheCapabilitiesAgainWhenConnected() = runTest(dispatcher) {
        model.startRoom()
        val live = SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER)
        session.setState(live)
        runCurrent()
        assertEquals(session.frameSink, camera.sink)
        assertEquals(1, session.sent.count { it is SessionMessage.Capabilities })

        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        assertEquals(session.frameSink, camera.sink)
        // Shoot together does nothing while the session is still there.
        model.startRoom()
        runCurrent()
        assertEquals(1, session.starts.size)

        session.setState(live)
        runCurrent()
        assertEquals(2, session.sent.count { it is SessionMessage.Capabilities })
        session.setState(SessionState.Ended(EndReason.LEFT))
        runCurrent()
    }

    @Test
    fun background_endsAReconnectingSessionOnTime_alsoWhenTheLinkComesBack() = runTest(dispatcher) {
        val process = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
        process.handleLifecycleEvent(Lifecycle.Event.ON_START)
        model.startRoom()
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()

        // To the background while reconnecting; the link is back 30 s later, and the deadline stays.
        process.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        advanceTimeBy(30_000)
        session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        advanceTimeBy(29_999)
        runCurrent()
        assertTrue(session.leaves.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(EndReason.LEFT), session.leaves)

        process.handleLifecycleEvent(Lifecycle.Event.ON_START)
        session.setState(SessionState.Ended(EndReason.LEFT))
        runCurrent()
    }

    @Test
    fun mobileData_aNoticeWaitingThroughAShortDrop_staysPending() = runTest(dispatcher) {
        connected()
        session.setPath(IcePath("relay", "srflx", "cellular", "udp"))
        runCurrent()
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        assertTrue(model.mobileDataPending.value)
        connected()
        runCurrent()
        assertTrue(model.mobileDataPending.value)
    }

    @Test
    fun mobileData_theFirstCellularPath_waitsForAScreen_andIsRememberedOnlyOnceShown() = runTest(dispatcher) {
        connected()
        session.setPath(IcePath("relay", "srflx", "cellular", "udp"))
        runCurrent()
        assertTrue(model.mobileDataPending.value)
        assertFalse(mobileData.wasShown())

        model.mobileDataNoticeShown()
        runCurrent()
        assertFalse(model.mobileDataPending.value)
        assertTrue(mobileData.wasShown())

        session.setPath(IcePath("srflx", "srflx", "cellular", null))
        runCurrent()
        assertFalse(model.mobileDataPending.value)
    }

    @Test
    fun mobileData_saidBefore_wifi_orNoPath_isNotPending() = runTest(dispatcher) {
        connected()
        session.setPath(IcePath("host", "host", "wifi", null))
        runCurrent()
        session.setPath(null)
        runCurrent()
        assertFalse(model.mobileDataPending.value)

        mobileData.markShown()
        session.setPath(IcePath("relay", "relay", "cellular", "tcp"))
        runCurrent()
        assertFalse(model.mobileDataPending.value)
    }

    @Test
    fun mobileData_aSessionThatEndsBeforeAScreenShowsIt_dropsIt_andTheNextSessionSaysIt() = runTest(dispatcher) {
        connected()
        session.setPath(IcePath("srflx", "srflx", "cellular", null))
        runCurrent()
        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        session.setPath(null)
        runCurrent()
        assertFalse(model.mobileDataPending.value)

        // A confirmation after the session ended remembers nothing.
        model.mobileDataNoticeShown()
        runCurrent()
        assertFalse(mobileData.wasShown())

        connected()
        session.setPath(IcePath("srflx", "srflx", "cellular", null))
        runCurrent()
        assertTrue(model.mobileDataPending.value)
    }

    private companion object {
        const val CODE = "123456"
    }

    // ---- Invitations (Design 2.6.6; FR-6.1–6.3, FR-6.9) ---------------------------------------------------------------

    private val friend = PixUser("u_2", "test2", "Test Account 2")

    /** Friends, open while the invitation is on its way, as it sent it: it takes what each *Invite* did. */
    private fun TestScope.inviteResults(): MutableList<InviteResult> {
        val results = mutableListOf<InviteResult>()
        backgroundScope.launch { model.inviteResults.collect { results += it } }
        return results
    }

    @Test
    fun invite_sendsTheInvitation_andTakesThePhotographersPlace() = runTest(dispatcher) {
        val results = inviteResults()
        model.invite(friend)
        runCurrent()

        assertEquals(listOf("invite:u_2"), invitations.calls)
        assertEquals(SessionEntry.Invite("inv_1", invitations.sent.place) to Role.PHOTOGRAPHER, session.starts.single())
        assertEquals(invitations.sent, model.invitation.value)
        assertEquals(listOf<InviteResult>(InviteResult.Sent), results)
    }

    @Test
    fun invite_aFriendInAnotherSession_saysSo_andStartsNothing() = runTest(dispatcher) {
        val results = inviteResults()
        invitations.failure = AccountError.BUSY
        model.invite(friend)
        runCurrent()

        assertEquals(listOf<InviteResult>(InviteResult.Failed(friend, AccountError.BUSY)), results)
        assertTrue(session.starts.isEmpty())
        assertNull(model.invitation.value)
    }

    @Test
    fun invite_whileASessionIsOpen_orTwiceAtOnce_sendsOne() = runTest(dispatcher) {
        inviteResults()
        session.setState(SessionState.Waiting(CODE))
        runCurrent()
        model.invite(friend)
        runCurrent()
        assertTrue(invitations.calls.isEmpty())

        session.setState(SessionState.Ended(EndReason.CANCELLED))
        runCurrent()
        model.invite(friend)
        model.invite(friend)
        runCurrent()
        assertEquals(listOf("invite:u_2"), invitations.calls)
    }

    @Test
    fun invite_answeredAfterItsScreenClosed_cancelsTheInvitation_andStartsNothing() = runTest(dispatcher) {
        val friends = backgroundScope.launch { model.inviteResults.collect {} }
        val answer = CompletableDeferred<Unit>()
        invitations.inviteGate = answer
        runCurrent()
        model.invite(friend)
        runCurrent()
        // Back to Camera before the server answered.
        friends.cancel()
        runCurrent()
        answer.complete(Unit)
        runCurrent()

        assertEquals(listOf("invite:u_2", "cancel:inv_1"), invitations.calls)
        assertTrue(session.starts.isEmpty())
        assertNull(model.invitation.value)

        // Nothing waits, so the next *Invite* goes out.
        inviteResults()
        model.invite(friend)
        runCurrent()
        assertEquals(SessionEntry.Invite("inv_1", invitations.sent.place) to Role.PHOTOGRAPHER, session.starts.single())
    }

    @Test
    fun invite_aSessionStartedWhileItIsOnItsWay_cancelsTheInvitation_andKeepsTheSession() = runTest(dispatcher) {
        val results = inviteResults()
        model.invite(friend)
        // *Use a room code* before the server answered the invitation.
        model.startRoom()
        runCurrent()

        assertEquals(listOf("invite:u_2", "cancel:inv_1"), invitations.calls)
        assertEquals(listOf(SessionEntry.NewRoom to Role.PHOTOGRAPHER), session.starts)
        assertNull(model.invitation.value)
        assertTrue(results.isEmpty())
    }

    @Test
    fun cancelInvite_whileWaiting_cancelsOnTheServerAndLeaves() = runTest(dispatcher) {
        inviteResults()
        model.invite(friend)
        runCurrent()
        session.setState(SessionState.Inviting("inv_1"))
        runCurrent()
        model.cancelInvite()
        runCurrent()

        assertEquals(listOf("invite:u_2", "cancel:inv_1"), invitations.calls)
        assertEquals(listOf(EndReason.CANCELLED), session.leaves)
    }

    @Test
    fun cancelInvite_whileWaiting_leavesAtOnce_soARoomCanStartBeforeTheServerAnswers() = runTest(dispatcher) {
        inviteResults()
        model.invite(friend)
        runCurrent()
        session.setState(SessionState.Inviting("inv_1"))
        runCurrent()
        val slow = CompletableDeferred<Unit>()
        invitations.cancelGate = slow

        model.cancelInvite()
        runCurrent()
        // The session is left while the server's answer is still on its way.
        assertEquals(listOf(EndReason.CANCELLED), session.leaves)
        session.setState(SessionState.Ended(EndReason.CANCELLED))
        runCurrent()
        // *Use a room code*, right after Friends opens.
        model.startRoom()
        runCurrent()
        assertEquals(SessionEntry.NewRoom to Role.PHOTOGRAPHER, session.starts.last())

        slow.complete(Unit)
        runCurrent()
        assertEquals(listOf("invite:u_2", "cancel:inv_1"), invitations.calls)
    }

    @Test
    fun cancelInvite_onceTheFriendAccepted_leavesTheSessionTheyAreJoining_butNotOnceConnected() = runTest(dispatcher) {
        inviteResults()
        model.invite(friend)
        runCurrent()
        session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        runCurrent()
        model.cancelInvite()
        runCurrent()
        assertTrue(session.leaves.isEmpty())

        session.setState(SessionState.Connecting("s_1"))
        runCurrent()
        model.cancelInvite()
        runCurrent()
        // The invitation is answered, so the server has nothing to cancel.
        assertEquals(listOf("invite:u_2"), invitations.calls)
        assertEquals(listOf(EndReason.CANCELLED), session.leaves)
    }

    @Test
    fun joinInvitation_takesTheFriendsPlace_asTheSubject() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        val place = SessionPlace("s_1", "subject-token", emptyList())
        assertTrue(model.joinInvitation("inv_1", place))
        runCurrent()

        assertEquals(SessionEntry.Invitation("inv_1", place) to Role.SUBJECT, session.starts.last())
        assertNull(model.lastCode)
    }

    @Test
    fun joinInvitation_whileASessionIsOpen_startsNothing_andKeepsTheCode() = runTest(dispatcher) {
        model.joinRoom(CODE)
        runCurrent()
        val place = SessionPlace("s_2", "subject-token", emptyList())
        for (open in listOf(
            SessionState.Waiting(CODE),
            SessionState.Inviting("inv_0"),
            SessionState.Connecting("s_1"),
            SessionState.Connected("s_1", PeerInfo("Dongje"), Role.SUBJECT),
            SessionState.Reconnecting("s_1", since = 0L),
        )) {
            session.setState(open)
            runCurrent()

            assertTrue(open.toString(), model.busy)
            assertFalse(open.toString(), model.joinInvitation("inv_1", place))
        }
        runCurrent()

        assertEquals(listOf(SessionEntry.RoomCode(CODE) to Role.SUBJECT), session.starts)
        assertEquals(CODE, model.lastCode)
    }
}
