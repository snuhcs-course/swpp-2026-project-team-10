// AI-generated with Claude Code, 2026-10-31, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.camera.PhotoAlbum
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
import com.lastpenguin.pix.session.PhotoContent
import com.lastpenguin.pix.session.PhotoReader
import com.lastpenguin.pix.session.PhotoSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.camera.CameraViewModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.zip.CRC32
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
 * Session photos (Design 2.6.5, 2.6.7): one not sent waits for the Camera screen; those going when the session ends are
 * said after the end's own notice; a drop keeps one going; the subject's saved or failed photo is a notice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Robolectric for ProcessLifecycleOwner, which the ViewModel observes for the background limit.
@RunWith(AndroidJUnit4::class)
class SessionViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val session = FakeSessionManager()
    private val camera = FakeCameraController()
    private val mobileData = MobileDataNotice.InMemory()
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
        )
        store.put("session", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private val photoUri = Uri.parse("content://media/external/images/media/1")

    /** A ViewModel with a session photo syncer; [reader] gives no photo unless a test says so. */
    private fun withPhotos(
        reader: PhotoReader = PhotoReader { null },
        album: PhotoAlbum,
    ): Pair<SessionViewModel, PhotoSyncer> {
        val photos = PhotoSyncer(session, reader, album, File(folder.root, "parts"), dispatcher)
        val syncer = GuideSyncer(
            session,
            InMemoryGuideRepository(),
            InMemoryGuideRepository(),
            codec,
            FakeGuideReferenceCodec(),
            MirrorReference(),
            dispatcher,
        )
        val photoModel = SessionViewModel(
            session,
            camera,
            syncer,
            RemoteControlHandler(session, camera, InMemoryGuideRepository()),
            mobileData,
            photoSyncer = photos,
        )
        store.put("photos", photoModel)
        return photoModel to photos
    }

    private val noAlbum = PhotoAlbum { _, _, _ -> Result.failure(IOException("No album")) }

    @Test
    fun aPhotoNotSentDuringTheSession_waitsForTheCameraScreen() = runTest(dispatcher) {
        val (photoModel, photos) = withPhotos(album = noAlbum)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        runCurrent()

        photos.taken(photoUri, takenAt = 1)
        runCurrent()

        assertEquals(PhotosNotSent("Junhyeong", 1), photoModel.photosNotSent.value)
        photoModel.photosNotSentShown()
        assertNull(photoModel.photosNotSent.value)
    }

    @Test
    fun photosStillGoingWhenTheSessionEnds_areSaidAfterTheEndsOwnNotice() = runTest(dispatcher) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val reader = PhotoReader { PhotoContent(bytes.size.toLong(), ByteArrayInputStream(bytes)) }
        val (photoModel, photos) = withPhotos(reader, noAlbum)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        runCurrent()
        photos.taken(photoUri, takenAt = 1)
        photos.taken(photoUri, takenAt = 2)
        runCurrent()

        session.setState(SessionState.Idle)
        runCurrent()
        advanceTimeBy(CameraViewModel.NOTICE_MS - 1)
        runCurrent()
        assertNull(photoModel.photosNotSent.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(PhotosNotSent("Junhyeong", 2), photoModel.photosNotSent.value)
    }

    @Test
    fun aDropKeepsThePhotoGoing() = runTest(dispatcher) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val reader = PhotoReader { PhotoContent(bytes.size.toLong(), ByteArrayInputStream(bytes)) }
        val (photoModel, photos) = withPhotos(reader, noAlbum)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        runCurrent()
        photos.taken(photoUri, takenAt = 1)
        runCurrent()
        val photoId = session.sent.filterIsInstance<SessionMessage.PhotoBegin>().single().photoId

        session.setState(SessionState.Reconnecting("s1", since = 0))
        runCurrent()
        session.deliver(SessionMessage.PhotoSaved(photoId, ok = true))
        runCurrent()
        session.setState(SessionState.Idle)
        advanceTimeBy(CameraViewModel.NOTICE_MS)
        runCurrent()

        assertNull(photoModel.photosNotSent.value)
    }

    @Test
    fun theSubjectsPhoto_isNoticedAsSavedOrNot() = runTest(dispatcher) {
        var albumWorks = true
        val album = PhotoAlbum { _, _, _ ->
            if (albumWorks) Result.success(photoUri) else Result.failure(IOException("The disk is full"))
        }
        val (photoModel, _) = withPhotos(album = album)
        val notices = mutableListOf<PhotoNotice>()
        backgroundScope.launch { photoModel.photoNotices.collect { notices += it } }
        photoModel.joinRoom("123456")
        session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        runCurrent()

        deliverPhoto("p1")
        albumWorks = false
        deliverPhoto("p2")

        assertEquals(listOf(PhotoNotice.Saved, PhotoNotice.SaveFailed("Dongje")), notices)
    }

    private fun TestScope.deliverPhoto(id: String) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        session.deliver(SessionMessage.PhotoBegin(id, "jpeg", bytes.size.toLong(), 1, 1))
        session.deliver(SessionMessage.PhotoChunk(id, 0, Base64.getEncoder().encodeToString(bytes)))
        session.deliver(SessionMessage.PhotoEnd(id, CRC32().apply { update(bytes) }.value))
        runCurrent()
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
}
