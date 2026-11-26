// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.session

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.FakeInvitationManager
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.camera.PhotoAlbum
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.generation.TipState
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
import com.lastpenguin.pix.session.PoseStartStep
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.SharedTip
import com.lastpenguin.pix.session.ShutterOutcome
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.fakeGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.shoot
import com.lastpenguin.pix.ui.camera.CameraViewModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.zip.CRC32
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * said after the end's own notice, under that session's name; a drop keeps one going; the subject's saved or failed
 * photo is a notice, which waits for the activity; one saved before this phone has seen Connected is noticed at once,
 * and one that failed then waits for that session's name. A room this phone waits in alone, as after its subject left
 * (#188): ended for the subject's way in; Idle (nothing to end); a session with someone in it or on its way (kept).
 * The guide's tip (#213, Design 2.5.1): the photographer sends it once connected and again after a reconnect, not while
 * waiting alone; the subject keeps it from Connecting on, through a reconnect, until the session ends.
 * The guide (#268, Design 2.5.1, 2.6.7): after a reconnect the photographer sends one changed while the link was down,
 * whatever the session's first `hello` named; in another session it goes by that session's `hello`; the subject's
 * copy stays through a reconnect and goes when the session ends.
 * The subject's tap to focus (FR-7.7, FR-7.4): its box at every tap; its notice at the first tap, not at a tap while
 * the notice shows or in the quiet time after it (just before its end / at its end), again at the first tap of the
 * next session, and not again after a reconnect within that time.
 * Who watches the camera (#271): a subject connecting is a notice on the photographer's phone, not again after a drop,
 * again for the next subject once the room waited alone; none on the subject's phone.
 * The subject's pinch (#292, FR-7.4): named from its first step, through its steps, until its last value is applied,
 * when the notice with the ratio follows; a pinch that ends without a last value (a cancel) stays named for
 * ZOOMING_LINGER_MS (just before its end / at its end); a quick step, one final value, names no one; a session that
 * ends during a pinch names no one.
 * The drop on the photographer's phone (#293, FR-6.12): the subject named for the whole wait, however long, until the
 * link is back; no longer once the wait ends (the room waits again, an invitation's session ends); the subject's
 * phone names no one; a drop whose Connected this reader never saw, named from the state alone and not by an earlier
 * session's name.
 * The subject's shutter (#314): taken through the Camera screen in front, a notice; with that screen gone, not taken
 * and no notice.
 * The subject starts AI poses (#276): on the photographer's phone, begun through the Camera screen in front, a notice,
 * and refused with that screen gone; on the subject's phone, the situation sent with its own number and its answer
 * heard, begun or not; no answer, refused after POSE_START_WAIT_MS (1 ms before / at it); of the given-up request, a
 * late refusal unheard, a late "asked" bringing it back with its answer said, and a late start said once; the
 * photographer asked, waited for without a limit; one request at a time, and "asked" said again at another tile; a
 * given-up request's refusal not the next one's answer; an answer no screen read and a request that waits, both
 * gone with their session; and nothing sent without a link, which counts as refused.
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
            invitations,
            photoSyncer = photos,
        )
        store.put("photos", photoModel)
        return photoModel to photos
    }

    private val noAlbum = PhotoAlbum { _, _, _ -> Result.failure(IOException("No album")) }

    private val tipOnCamera = MutableStateFlow<GuideTip?>(null)
    private val tip = PoseTip("Move a little closer.\nHold the phone at your waist, tilted up a little.")

    /** A ViewModel with the syncer of the guide's tip (#213), whose tip on Camera is [tipOnCamera]. */
    private fun withTips(): Pair<SessionViewModel, TipSyncer> {
        val tips = TipSyncer(session, tipOnCamera)
        val syncer = GuideSyncer(
            session,
            InMemoryGuideRepository(),
            InMemoryGuideRepository(),
            codec,
            FakeGuideReferenceCodec(),
            MirrorReference(),
            dispatcher,
        )
        val tipModel = SessionViewModel(
            session,
            camera,
            syncer,
            RemoteControlHandler(session, camera, InMemoryGuideRepository()),
            mobileData,
            invitations,
            tipSyncer = tips,
        )
        store.put("tips", tipModel)
        return tipModel to tips
    }

    private fun tipsSent() = session.sent.filter { it is SessionMessage.GuideTip || it is SessionMessage.GuideTipClear }

    @Test
    fun theGuidesTip_goesToTheSubjectOnceConnected_andAgainAfterAReconnect() = runTest(dispatcher) {
        val (tipModel, _) = withTips()
        tipOnCamera.value = GuideTip("g1", TipState.Ready(tip))
        tipModel.startRoom()
        session.setState(SessionState.Waiting(CODE))
        runCurrent()
        // Alone in its room: no one to tell.
        assertEquals(emptyList<SessionMessage>(), tipsSent())

        val live = SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)
        session.setState(live)
        runCurrent()
        val sent = SessionMessage.GuideTip("g1", tip.text)
        assertEquals(listOf<SessionMessage>(sent), tipsSent())

        // The tip went while the link was down; the subject hears so when it is back.
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        tipOnCamera.value = null
        runCurrent()
        assertEquals(listOf<SessionMessage>(sent), tipsSent())
        session.setState(live)
        runCurrent()
        assertEquals(listOf(sent, SessionMessage.GuideTipClear), tipsSent())
        session.setState(SessionState.Ended(EndReason.LEFT))
        runCurrent()
    }

    @Test
    fun theSubject_keepsThePhotographersTip_fromJoiningUntilTheSessionEnds() = runTest(dispatcher) {
        val (tipModel, tips) = withTips()
        tipModel.joinRoom(CODE)
        session.setState(SessionState.Connecting("s1"))
        runCurrent()
        // The photographer's side is connected first and sends at once: the tip is read before this phone's Connected.
        session.deliver(SessionMessage.GuideTip("g1", tip.text))
        runCurrent()
        assertEquals(SharedTip("g1", tip), tips.mirror.value)

        connected()
        runCurrent()
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        assertEquals(SharedTip("g1", tip), tips.mirror.value)

        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()
        assertNull(tips.mirror.value)
    }

    private val guides = InMemoryGuideRepository()
    private val mirror = InMemoryGuideRepository()

    /** A ViewModel whose guide syncer sends [guides] and draws on [mirror] (#268). */
    private fun withGuides(): SessionViewModel {
        val syncer =
            GuideSyncer(session, guides, mirror, codec, FakeGuideReferenceCodec(), MirrorReference(), dispatcher)
        val guideModel = SessionViewModel(
            session,
            camera,
            syncer,
            RemoteControlHandler(session, camera, InMemoryGuideRepository()),
            mobileData,
            invitations,
        )
        store.put("guides", guideModel)
        return guideModel
    }

    private fun imagesSent() = session.sent.filterIsInstance<SessionMessage.GuideImageBegin>().map { it.guideId }

    @Test
    fun theGuide_changedWhileTheLinkWasDown_goesToTheSubjectOnceItIsBack() = runTest(dispatcher) {
        withGuides().startRoom()
        guides.setGuide(fakeGuide("g1"))
        // The subject joined holding g1, so the image is not sent.
        val live = SessionState.Connected("s1", PeerInfo("Joonhyung", haveGuideId = "g1"), Role.PHOTOGRAPHER)
        session.setState(live)
        runCurrent()
        assertEquals(emptyList<String>(), imagesSent())
        guides.setGuide(fakeGuide("g2"))
        runCurrent()
        assertEquals(listOf("g2"), imagesSent())

        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        guides.setGuide(fakeGuide("g1"))
        runCurrent()
        session.setState(live)
        runCurrent()

        // The hello still names g1, but the subject has held g2 since.
        assertEquals(listOf("g2", "g1"), imagesSent())
        session.setState(SessionState.Ended(EndReason.LEFT))
        runCurrent()
    }

    @Test
    fun theGuide_inAnotherSessionAfterADrop_goesByThatSessionsHello() = runTest(dispatcher) {
        withGuides().startRoom()
        guides.setGuide(fakeGuide("g1"))
        session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(listOf("g1"), imagesSent())

        // The link is lost for good, and someone else joins the room: their hello names nothing.
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        session.setState(SessionState.Waiting(CODE))
        runCurrent()
        session.setState(SessionState.Connected("s2", PeerInfo("Seongmin"), Role.PHOTOGRAPHER))
        runCurrent()

        assertEquals(listOf("g1", "g1"), imagesSent())
        session.setState(SessionState.Ended(EndReason.LEFT))
        runCurrent()
    }

    @Test
    fun theSubject_keepsThePhotographersGuideThroughAReconnect_andDropsItWhenTheSessionEnds() = runTest(dispatcher) {
        withGuides().joinRoom(CODE)
        session.setState(SessionState.Connecting("s1"))
        runCurrent()
        val image = codec.encode(fakeGuide("g1"))
        session.deliver(image.begin())
        for (index in image.chunks.indices) session.deliver(image.chunk(index))
        session.deliver(image.end())
        runCurrent()
        assertEquals("g1", mirror.guide.value?.id)

        connected()
        runCurrent()
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()
        assertEquals("g1", mirror.guide.value?.id)

        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()
        assertNull(mirror.guide.value)
    }

    @Test
    fun aPhotoNotSentDuringTheSession_waitsForTheCameraScreen() = runTest(dispatcher) {
        val (photoModel, photos) = withPhotos(album = noAlbum)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()

        photos.shoot(photoUri, takenAt = 1)
        runCurrent()

        // Nothing has read it yet: it waits, as it does while the Camera screen is not in front.
        assertEquals(PhotosNotSent("Joonhyung", 1), photoModel.photosNotSent.first())
    }

    /** What the Camera screen would read, from now on. */
    private fun TestScope.notSent(model: SessionViewModel): List<PhotosNotSent> {
        val read = mutableListOf<PhotosNotSent>()
        backgroundScope.launch { model.photosNotSent.collect { read += it } }
        return read
    }

    @Test
    fun photosStillGoingWhenTheSessionEnds_areSaidAfterTheEndsOwnNotice() = runTest(dispatcher) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val reader = PhotoReader { PhotoContent(bytes.size.toLong(), ByteArrayInputStream(bytes)) }
        val (photoModel, photos) = withPhotos(reader, noAlbum)
        val notSent = notSent(photoModel)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()
        photos.shoot(photoUri, takenAt = 1)
        photos.shoot(photoUri, takenAt = 2)
        runCurrent()

        session.setState(SessionState.Idle)
        runCurrent()
        advanceTimeBy(CameraViewModel.NOTICE_MS - 1)
        runCurrent()
        assertTrue(notSent.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(PhotosNotSent("Joonhyung", 2)), notSent)
    }

    @Test
    fun photosOfAnEndedSession_keepItsNameWhenAnotherSessionStartsDuringTheWait() = runTest(dispatcher) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val reader = PhotoReader { PhotoContent(bytes.size.toLong(), ByteArrayInputStream(bytes)) }
        val (photoModel, photos) = withPhotos(reader, noAlbum)
        val notSent = notSent(photoModel)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()
        photos.shoot(photoUri, takenAt = 1)
        runCurrent()

        session.setState(SessionState.Idle)
        runCurrent()
        session.setState(SessionState.Connected("s2", PeerInfo("Mina"), Role.PHOTOGRAPHER))
        runCurrent()
        advanceTimeBy(CameraViewModel.NOTICE_MS)
        runCurrent()

        assertEquals(listOf(PhotosNotSent("Joonhyung", 1)), notSent)
    }

    @Test
    fun aDropKeepsThePhotoGoing() = runTest(dispatcher) {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val reader = PhotoReader { PhotoContent(bytes.size.toLong(), ByteArrayInputStream(bytes)) }
        val (photoModel, photos) = withPhotos(reader, noAlbum)
        val notSent = notSent(photoModel)
        photoModel.startRoom()
        session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()
        photos.shoot(photoUri, takenAt = 1)
        runCurrent()
        val photoId = session.sent.filterIsInstance<SessionMessage.PhotoBegin>().single().photoId

        session.setState(SessionState.Reconnecting("s1", since = 0))
        runCurrent()
        session.deliver(SessionMessage.PhotoSaved(photoId, ok = true))
        runCurrent()
        session.setState(SessionState.Idle)
        advanceTimeBy(CameraViewModel.NOTICE_MS)
        runCurrent()

        assertTrue(notSent.isEmpty())
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

    @Test
    fun aNoticeNobodyReadsYet_waitsForTheActivity() = runTest(dispatcher) {
        val (photoModel, _) = withPhotos(album = { _, _, _ -> Result.success(photoUri) })
        photoModel.joinRoom("123456")
        session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        runCurrent()

        deliverPhoto("p1")

        assertEquals(PhotoNotice.Saved, photoModel.photoNotices.first())
    }

    @Test
    fun aSavedPhoto_isNoticedBeforeThisPhoneHasSeenConnected() = runTest(dispatcher) {
        val (photoModel, _) = withPhotos(album = { _, _, _ -> Result.success(photoUri) })
        photoModel.joinRoom("123456")
        session.setState(SessionState.Connecting("s1"))
        runCurrent()

        deliverPhoto("p1")

        assertEquals(PhotoNotice.Saved, photoModel.photoNotices.first())
    }

    @Test
    fun aFailedPhoto_waitsForThePeersNameOfThisSession() = runTest(dispatcher) {
        val (photoModel, _) = withPhotos(album = noAlbum)
        val notices = mutableListOf<PhotoNotice>()
        backgroundScope.launch { photoModel.photoNotices.collect { notices += it } }
        photoModel.joinRoom("123456")
        session.setState(SessionState.Connecting("s1"))
        runCurrent()

        deliverPhoto("p1")
        assertTrue(notices.isEmpty())

        session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        runCurrent()
        assertEquals(listOf(PhotoNotice.SaveFailed("Dongje")), notices)
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
        session.setState(SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()

        val image = codec.encode(fakeGuide("s1"))
        session.deliver(image.begin())
        for (i in image.chunks.indices) session.deliver(image.chunk(i))
        session.deliver(image.end())
        runCurrent()

        assertEquals(listOf(SessionNotice.Watching("Joonhyung"), SessionNotice.RemoteGuide("Joonhyung")), notices)
    }

    @Test
    fun aSubjectConnecting_isNoticed_notAgainAfterADrop_butAgainForTheNextSubject() = runTest(dispatcher) {
        val notices = mutableListOf<SessionNotice>()
        backgroundScope.launch { model.notices.collect { notices += it } }

        session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 1"), Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(listOf(SessionNotice.Watching("Test Account 1")), notices)

        // The link drops and comes back: the same live view goes on.
        session.setState(SessionState.Reconnecting("s_1", 0L))
        runCurrent()
        session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 1"), Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(listOf(SessionNotice.Watching("Test Account 1")), notices)

        // The subject left and the room waited alone: the next one starts a live view of its own.
        session.setState(SessionState.Waiting("ABC123"))
        runCurrent()
        session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        runCurrent()
        assertEquals(
            listOf(SessionNotice.Watching("Test Account 1"), SessionNotice.Watching("Test Account 2")),
            notices,
        )
    }

    @Test
    fun theSubjectsOwnConnection_isNoNotice() = runTest(dispatcher) {
        val notices = mutableListOf<SessionNotice>()
        backgroundScope.launch { model.notices.collect { notices += it } }

        session.setState(SessionState.Connected("s_1", PeerInfo("Test Account 1"), Role.SUBJECT))
        runCurrent()

        assertTrue(notices.isEmpty())
    }

    /** A ViewModel on a camera that focuses on a point, whose time is [clock]. */
    private fun withPointFocus(clock: () -> Long): SessionViewModel {
        val focusCamera = FakeCameraController(supportsFocusPoint = true)
        val syncer = GuideSyncer(
            session,
            InMemoryGuideRepository(),
            InMemoryGuideRepository(),
            codec,
            FakeGuideReferenceCodec(),
            MirrorReference(),
            dispatcher,
        )
        return SessionViewModel(
            session,
            focusCamera,
            syncer,
            RemoteControlHandler(session, focusCamera, InMemoryGuideRepository()),
            mobileData,
            invitations,
            clock = clock,
        ).also { store.put("focus", it) }
    }

    private fun asPhotographerOf(name: String): MutableList<SessionNotice> {
        val notices = mutableListOf<SessionNotice>()
        session.setState(SessionState.Connected("s_1", PeerInfo(name), Role.PHOTOGRAPHER))
        return notices
    }

    @Test
    fun theSubjectsPinch_isNamedFromItsFirstStep_untilItsLastValueIsApplied() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        runCurrent()
        notices.clear()
        assertNull(model.subjectZooming.value)

        session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        runCurrent()
        assertEquals("Joonhyung", model.subjectZooming.value)

        // Longer than a notice's own time, as long as the steps come.
        repeat(4) {
            advanceTimeBy(1_000)
            session.deliver(SessionMessage.ZoomSet(1.6f + it * 0.1f, final = false))
            runCurrent()
            assertEquals("Joonhyung", model.subjectZooming.value)
        }
        assertTrue(notices.isEmpty())

        session.deliver(SessionMessage.ZoomSet(2f, final = true))
        runCurrent()
        assertNull(model.subjectZooming.value)
        assertEquals(listOf<SessionNotice>(SessionNotice.RemoteZoom("Joonhyung", 2f)), notices)
    }

    @Test
    fun aPinchThatEndsWithoutALastValue_staysNamedALittle_thenNoLonger() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        runCurrent()
        notices.clear()
        session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        runCurrent()

        // The subject's phone took the pinch back: no value is applied, so no notice with a ratio comes.
        session.deliver(SessionMessage.ZoomSet(1.5f, final = true, cancel = true))
        runCurrent()
        assertEquals("Joonhyung", model.subjectZooming.value)
        advanceTimeBy(SessionViewModel.ZOOMING_LINGER_MS - 1)
        runCurrent()
        assertEquals("Joonhyung", model.subjectZooming.value)
        advanceTimeBy(1)
        runCurrent()
        assertNull(model.subjectZooming.value)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun aQuickStep_namesNoOneZooming_onlyItsRatio() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        val zooming = mutableListOf<String?>()
        backgroundScope.launch { model.subjectZooming.collect { zooming += it } }
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.ZoomSet(2f, final = true))
        runCurrent()

        assertEquals(listOf<String?>(null), zooming)
        assertEquals(listOf<SessionNotice>(SessionNotice.RemoteZoom("Joonhyung", 2f)), notices)
    }

    @Test
    fun aSessionThatEndsDuringThePinch_namesNoOne() = runTest(dispatcher) {
        asPhotographerOf("Joonhyung")
        runCurrent()
        session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        runCurrent()
        assertEquals("Joonhyung", model.subjectZooming.value)

        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()

        assertNull(model.subjectZooming.value)
    }

    @Test
    fun theSubjectsFocus_showsItsBoxAtEveryTap_andItsNoticeOnlyOnceInAWhile() = runTest(dispatcher) {
        var now = 10_000L
        val focusModel = withPointFocus { now }
        val notices = mutableListOf<SessionNotice>()
        val boxes = mutableListOf<RemoteFocus>()
        backgroundScope.launch { focusModel.notices.collect { notices += it } }
        backgroundScope.launch { focusModel.remoteFocus.collect { boxes += it } }
        session.setState(SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()

        session.deliver(SessionMessage.FocusSet(0.3f, 0.4f))
        runCurrent()
        assertEquals(listOf(SessionNotice.RemoteFocus("Joonhyung")), notices)

        // While the notice shows, and at the last moment of the quiet time after it.
        now += 1_000
        session.deliver(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()
        now = 10_000L + SessionViewModel.FOCUS_NOTICE_MS + SessionViewModel.FOCUS_NOTICE_QUIET_MS - 1
        session.deliver(SessionMessage.FocusSet(0.6f, 0.6f))
        runCurrent()
        assertEquals(listOf(RemoteFocus(0.3f, 0.4f), RemoteFocus(0.5f, 0.5f), RemoteFocus(0.6f, 0.6f)), boxes)
        assertEquals(1, notices.size)

        now += 1
        session.deliver(SessionMessage.FocusSet(0.7f, 0.7f))
        runCurrent()
        assertEquals(4, boxes.size)
        assertEquals(List(2) { SessionNotice.RemoteFocus("Joonhyung") }, notices)
    }

    @Test
    fun theFocusNotice_comesAgainInTheNextSession_butNotAfterAReconnect() = runTest(dispatcher) {
        var now = 10_000L
        val focusModel = withPointFocus { now }
        val notices = mutableListOf<SessionNotice>()
        backgroundScope.launch { focusModel.notices.collect { notices += it } }
        val connected = SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)
        session.setState(connected)
        runCurrent()
        session.deliver(SessionMessage.FocusSet(0.3f, 0.4f))
        runCurrent()

        // A short drop within the quiet time: the same session, so no second notice.
        session.setState(SessionState.Reconnecting("s_1", 0L))
        runCurrent()
        session.setState(connected)
        runCurrent()
        now += 500
        session.deliver(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()
        assertEquals(1, notices.size)

        // Another session, still within that time.
        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()
        session.setState(SessionState.Connected("s_2", PeerInfo("Sungmin"), Role.PHOTOGRAPHER))
        runCurrent()
        now += 500
        session.deliver(SessionMessage.FocusSet(0.5f, 0.5f))
        runCurrent()
        assertEquals(
            listOf(SessionNotice.RemoteFocus("Joonhyung"), SessionNotice.RemoteFocus("Sungmin")),
            notices.filterIsInstance<SessionNotice.RemoteFocus>(),
        )
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

    @Test
    fun theSubjectsShutter_isTakenByTheCameraScreen_andIsANotice() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        var presses = 0
        model.setRemoteShutter {
            presses++
            ShutterOutcome.TAKEN
        }
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.ShutterPress(1))
        runCurrent()

        assertEquals(1, presses)
        assertEquals(listOf<SessionNotice>(SessionNotice.RemoteShutter("Joonhyung")), notices)
        assertEquals(SessionMessage.ShutterAnswer(taken = true, press = 1), session.sent.last())
    }

    @Test
    fun onceTheCameraScreenIsGone_aPressIsNotTaken_andIsNoNotice() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        model.setRemoteShutter { ShutterOutcome.TAKEN }
        model.setRemoteShutter(null)
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.ShutterPress(1))
        runCurrent()

        assertTrue(notices.isEmpty())
        assertEquals(SessionMessage.ShutterAnswer(taken = false, press = 1), session.sent.last())
    }

    @Test
    fun theSubjectsPress_thatStartsOrStopsTheTimer_saysWhoPressed_andWhoCancelled() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        model.setRemoteShutter { cancel -> if (cancel) ShutterOutcome.TIMER_STOPPED else ShutterOutcome.TIMER_STARTED }
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.ShutterPress(1))
        runCurrent()
        session.deliver(SessionMessage.ShutterPress(2, cancel = true))
        runCurrent()

        assertEquals(
            listOf(SessionNotice.RemoteShutterPress("Joonhyung"), SessionNotice.RemoteTimerCancel("Joonhyung")),
            notices,
        )
    }

    @Test
    fun theSubjectsChangeOfTheTimer_isANotice_withWhatItIsNow() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.TimerSet(3))
        runCurrent()
        session.deliver(SessionMessage.TimerSet(0))
        runCurrent()

        assertEquals(
            listOf(
                SessionNotice.RemoteTimer("Joonhyung", ShutterTimer.THREE),
                SessionNotice.RemoteTimer("Joonhyung", ShutterTimer.OFF),
            ),
            notices,
        )
    }

    // ---- The subject starts AI poses (FR-6.15, #276) --------------------------------------------------------------

    @Test
    fun theSubjectsSituation_beginsTheScenePhotoOnTheCameraScreen_andIsANotice() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        val asked = mutableListOf<Situation>()
        model.setRemotePoseStarter { situation, answer ->
            asked += situation
            answer(PoseStartStep.STARTED)
        }
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.PoseStart("half_body"))
        runCurrent()

        assertEquals(listOf(Situation.HALF_BODY), asked)
        assertEquals(listOf<SessionNotice>(SessionNotice.RemotePoseStart("Joonhyung")), notices)
        assertEquals(SessionMessage.PoseStartAnswer(started = true), session.sent.last())
    }

    @Test
    fun onceTheCameraScreenIsGone_theSubjectsStartIsRefused() = runTest(dispatcher) {
        val notices = asPhotographerOf("Joonhyung")
        backgroundScope.launch { model.notices.collect { notices += it } }
        val starter: (Situation, (PoseStartStep) -> Unit) -> Unit = { _, answer -> answer(PoseStartStep.STARTED) }
        model.setRemotePoseStarter(starter)
        model.dropRemotePoseStarter(starter)
        runCurrent()
        notices.clear()

        session.deliver(SessionMessage.PoseStart("half_body"))
        runCurrent()

        assertTrue(notices.isEmpty())
        assertEquals(SessionMessage.PoseStartAnswer(started = false), session.sent.last())
    }

    private fun TestScope.poseStartAnswers(): MutableList<PoseStartStep> {
        val answers = mutableListOf<PoseStartStep>()
        backgroundScope.launch { model.poseStartAnswers.collect { answers += it } }
        return answers
    }

    /** The request sent last, whose number its answers name. */
    private fun asked(): SessionMessage.PoseStart = session.sent.filterIsInstance<SessionMessage.PoseStart>().last()

    private fun answerPoses(started: Boolean, asking: Boolean = false, request: Int = asked().request) {
        session.deliver(SessionMessage.PoseStartAnswer(started, request, asking))
    }

    @Test
    fun theSubject_asksForPosesWithItsSituation_andHearsWhatBecameOfIt() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()

        model.askForPoses(Situation.LANDMARK)
        runCurrent()
        assertEquals("landmark", asked().situation)
        assertTrue(answers.isEmpty())

        answerPoses(started = true)
        runCurrent()
        assertEquals(listOf(PoseStartStep.STARTED), answers)

        model.askForPoses(Situation.SELFIE)
        runCurrent()
        answerPoses(started = false)
        runCurrent()
        assertEquals(listOf(PoseStartStep.STARTED, PoseStartStep.REFUSED), answers)
        // Each request has its own number.
        assertEquals(2, session.sent.filterIsInstance<SessionMessage.PoseStart>().map { it.request }.distinct().size)
    }

    @Test
    fun aStartWithoutAnAnswer_countsAsRefusedAfterItsWait_andNotBefore() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()

        advanceTimeBy(SessionViewModel.POSE_START_WAIT_MS - 1)
        runCurrent()
        assertTrue(answers.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED), answers)

        // A refusal comes after all: the request was given up, and nothing more is said.
        answerPoses(started = false)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED), answers)
    }

    @Test
    fun whileThePhotographerIsAsked_theSubjectWaitsWithoutATimeLimit_forTheirAnswer() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()

        // The AI notice is up on the photographer's phone, which says so at once.
        answerPoses(started = false, asking = true)
        runCurrent()
        assertEquals(listOf(PoseStartStep.ASKING), answers)
        // However long they read it.
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(PoseStartStep.ASKING), answers)

        answerPoses(started = true)
        runCurrent()
        assertEquals(listOf(PoseStartStep.ASKING, PoseStartStep.STARTED), answers)
    }

    @Test
    fun whileARequestWaits_anotherIsNotSent_andTheFirstsAnswerIsTheOneSaid() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()
        val first = asked().request

        // The sheet again, and another tile, before the photographer's phone answered: one request at a time.
        model.askForPoses(Situation.LANDMARK)
        runCurrent()
        assertEquals(1, session.sent.count { it is SessionMessage.PoseStart })
        assertTrue(answers.isEmpty())

        answerPoses(started = true, request = first)
        runCurrent()
        assertEquals(listOf(PoseStartStep.STARTED), answers)

        // Decided: the next tile asks again.
        model.askForPoses(Situation.LANDMARK)
        runCurrent()
        assertEquals(2, session.sent.count { it is SessionMessage.PoseStart })
    }

    @Test
    fun whileThePhotographerIsAsked_anotherTileSaysSoAgain_andAsksNothing() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()
        answerPoses(started = false, asking = true)
        runCurrent()

        model.askForPoses(Situation.LANDMARK)
        runCurrent()

        assertEquals(1, session.sent.count { it is SessionMessage.PoseStart })
        assertEquals(listOf(PoseStartStep.ASKING, PoseStartStep.ASKING), answers)
    }

    @Test
    fun aLateWordThatThePhotographerIsAsked_bringsTheGivenUpRequestBack_andItsAnswerIsSaid() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()
        val request = asked().request
        advanceTimeBy(SessionViewModel.POSE_START_WAIT_MS)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED), answers)

        // The answer was kept behind a full buffer: the photographer's phone has had its notice up all along.
        answerPoses(started = false, asking = true, request = request)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED, PoseStartStep.ASKING), answers)
        // The request waits again, so another tile asks nothing.
        model.askForPoses(Situation.LANDMARK)
        runCurrent()
        assertEquals(1, session.sent.count { it is SessionMessage.PoseStart })

        answerPoses(started = true, request = request)
        runCurrent()
        assertEquals(PoseStartStep.STARTED, answers.last())
    }

    @Test
    fun aLateStartOfAGivenUpRequest_isSaid_andALateRefusalIsNotSaidAgain() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()
        val first = asked().request
        advanceTimeBy(SessionViewModel.POSE_START_WAIT_MS)
        runCurrent()

        // The poses did begin on the photographer's phone: the subject hears it after all.
        answerPoses(started = true, request = first)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED, PoseStartStep.STARTED), answers)
        // Once: the same answer again says nothing.
        answerPoses(started = true, request = first)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED, PoseStartStep.STARTED), answers)

        model.askForPoses(Situation.LANDMARK)
        runCurrent()
        val second = asked().request
        advanceTimeBy(SessionViewModel.POSE_START_WAIT_MS)
        runCurrent()
        answerPoses(started = false, request = second)
        runCurrent()
        // "Can't start AI poses right now" was said at the limit already.
        assertEquals(listOf(PoseStartStep.REFUSED, PoseStartStep.STARTED, PoseStartStep.REFUSED), answers)
    }

    @Test
    fun anAnswerToARequestGivenUp_isNotTheNextOnes() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()
        val first = asked().request
        advanceTimeBy(SessionViewModel.POSE_START_WAIT_MS)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED), answers)
        model.askForPoses(Situation.LANDMARK)
        runCurrent()

        // The first's refusal, come late: said at its limit already, and not the second's answer.
        answerPoses(started = false, request = first)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED), answers)

        answerPoses(started = true)
        runCurrent()
        assertEquals(listOf(PoseStartStep.REFUSED, PoseStartStep.STARTED), answers)
    }

    @Test
    fun anAnswerNoScreenRead_andARequestThatWaits_goWithTheirSession() = runTest(dispatcher) {
        // No screen reads the answers: Subject view is under Settings.
        connected()
        runCurrent()
        model.askForPoses(Situation.DEFAULT)
        runCurrent()
        answerPoses(started = true)
        model.askForPoses(Situation.LANDMARK)
        runCurrent()

        // The session ends, and another begins with another photographer.
        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()
        session.setState(SessionState.Connected("s2", PeerInfo("Sungmin"), Role.SUBJECT))
        runCurrent()
        val answers = poseStartAnswers()
        advanceTimeBy(SessionViewModel.POSE_START_WAIT_MS + 1)
        runCurrent()

        // Neither the old session's answer nor its request's time limit says anything in the new one.
        assertTrue(answers.isEmpty())
    }

    @Test
    fun withoutALink_theSubjectsSituationIsNotSent_andCountsAsRefused() = runTest(dispatcher) {
        connected()
        val answers = poseStartAnswers()
        runCurrent()
        session.setState(SessionState.Reconnecting("s1", 0L))
        runCurrent()

        model.askForPoses(Situation.DEFAULT)
        runCurrent()

        assertTrue(session.sent.none { it is SessionMessage.PoseStart })
        assertEquals(listOf(PoseStartStep.REFUSED), answers)
    }

    /** A drop as the session says it: with the other phone's name and this phone's role (#293). */
    private fun dropped(id: String, name: String, role: Role = Role.PHOTOGRAPHER) =
        SessionState.Reconnecting(id, 0L, peer = PeerInfo(name), role = role)

    @Test
    fun aDropSeenWithoutItsConnected_namesTheSubjectFromTheStateAlone() = runTest(dispatcher) {
        // An earlier session, whose name must not come back.
        session.setState(SessionState.Connected("s_0", PeerInfo("Sungmin"), Role.PHOTOGRAPHER))
        runCurrent()
        session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        runCurrent()

        // A state flow shows its reader only the latest value: Connected and the drop right after it come as one.
        session.setState(SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        session.setState(dropped("s_1", "Joonhyung"))
        runCurrent()

        assertEquals("Joonhyung", model.reconnectingWith.value)
    }

    @Test
    fun aDrop_namesTheSubjectForTheWholeWait_untilTheLinkIsBack() = runTest(dispatcher) {
        val connected = SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)
        session.setState(connected)
        runCurrent()
        assertNull(model.reconnectingWith.value)

        session.setState(dropped("s_1", "Joonhyung"))
        runCurrent()
        assertEquals("Joonhyung", model.reconnectingWith.value)
        // The session decides how long the wait is; the name has no time of its own.
        advanceTimeBy(29_000)
        runCurrent()
        assertEquals("Joonhyung", model.reconnectingWith.value)

        session.setState(connected)
        runCurrent()
        assertNull(model.reconnectingWith.value)
    }

    @Test
    fun aDropThatIsGivenUp_namesNoOne() = runTest(dispatcher) {
        // A room code's photographer waits in its room again.
        session.setState(SessionState.Connected("s_1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        runCurrent()
        session.setState(dropped("s_1", "Joonhyung"))
        runCurrent()
        session.setState(SessionState.Waiting("482915"))
        runCurrent()
        assertNull(model.reconnectingWith.value)

        // An invitation's session ends.
        session.setState(SessionState.Connected("s_2", PeerInfo("Sungmin"), Role.PHOTOGRAPHER))
        runCurrent()
        session.setState(dropped("s_2", "Sungmin"))
        runCurrent()
        assertEquals("Sungmin", model.reconnectingWith.value)
        session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        runCurrent()
        assertNull(model.reconnectingWith.value)
    }

    @Test
    fun theSubjectsPhone_namesNoOneWhileReconnecting() = runTest(dispatcher) {
        // Subject view has its own "Reconnecting…" over the video.
        connected()
        runCurrent()
        session.setState(dropped("s1", "Dongje", Role.SUBJECT))
        runCurrent()
        assertNull(model.reconnectingWith.value)
    }

    private fun connected() = session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))

    @Test
    fun reconnecting_keepsTheFrames_andSendsTheCapabilitiesAgainWhenConnected() = runTest(dispatcher) {
        model.startRoom()
        val live = SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)
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
        session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
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

    /** The same friend as the session carries them (FR-4.11). */
    private val partner = SessionPartner(friend.userId, friend.displayName)

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
        assertEquals(
            SessionEntry.Invite("inv_1", invitations.sent.place, partner) to Role.PHOTOGRAPHER,
            session.starts.single(),
        )
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
        assertEquals(
            SessionEntry.Invite("inv_1", invitations.sent.place, partner) to Role.PHOTOGRAPHER,
            session.starts.single(),
        )
    }

    @Test
    fun invite_aSessionStartedWhileItIsOnItsWay_cancelsTheInvitation_andKeepsTheSession() = runTest(dispatcher) {
        val results = inviteResults()
        model.invite(friend)
        // *Show a code* before the server answered the invitation.
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
        // *Show a code*, right after Share my camera opens.
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
        assertTrue(model.joinInvitation("inv_1", place, partner))
        runCurrent()

        assertEquals(SessionEntry.Invitation("inv_1", place, partner) to Role.SUBJECT, session.starts.last())
        assertNull(model.lastCode)
    }

    @Test
    fun endWaitingRoom_endsARoomWithNobodyIn_andLetsTheInvitationStart() = runTest(dispatcher) {
        session.endsOnLeave = true
        session.setState(SessionState.Waiting(CODE))
        runCurrent()

        assertTrue(model.endWaitingRoom())

        assertEquals(listOf(EndReason.CANCELLED), session.leaves)
        assertTrue(model.joinInvitation("inv_1", SessionPlace("s_2", "subject-token", emptyList()), partner))
    }

    @Test
    fun endWaitingRoom_keepsASessionWithSomeoneInOrOnItsWay_andEndsNothingWhenIdle() = runTest(dispatcher) {
        session.endsOnLeave = true
        assertTrue(model.endWaitingRoom())
        for (open in listOf(
            SessionState.Inviting("inv_0"),
            SessionState.Connecting("s_1"),
            SessionState.Connected("s_1", PeerInfo("Dongje"), Role.PHOTOGRAPHER),
            SessionState.Reconnecting("s_1", since = 0L),
        )) {
            session.setState(open)
            runCurrent()

            assertFalse(open.toString(), model.endWaitingRoom())
        }
        assertEquals(emptyList<EndReason>(), session.leaves)
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
            assertFalse(open.toString(), model.joinInvitation("inv_1", place, partner))
        }
        runCurrent()

        assertEquals(listOf(SessionEntry.RoomCode(CODE) to Role.SUBJECT), session.starts)
        assertEquals(CODE, model.lastCode)
    }
}
