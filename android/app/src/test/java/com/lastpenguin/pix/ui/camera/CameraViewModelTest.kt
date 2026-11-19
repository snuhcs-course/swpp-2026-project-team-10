// AI-generated with ChatGPT Codex and Claude Code, 2026-11-18, reviewed by Jaewan Park, Sungmin Jo, Dongje Park, and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.CameraState
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.PhotoShare
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import org.junit.runner.RunWith

// Robolectric only for a real Uri, which a saved photo needs.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CameraViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val camera = FakeCamera()
    private val guides = FakeGuides()
    private lateinit var model: CameraViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = CameraViewModel(camera, guides)
        store.put("camera", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    // Selfie mode (FR-1.14): the switch goes to the front camera and back; a phone without a front camera has no
    // switch, and a photo being saved holds it.
    @Test
    fun switchCamera_goesToTheFrontCameraAndBack() = runTest {
        runCurrent()
        assertTrue(model.uiState.value.canSwitchCamera)

        model.onSwitchCamera()
        runCurrent()
        assertEquals(CameraFacing.FRONT, model.uiState.value.facing)

        model.onSwitchCamera()
        runCurrent()
        assertEquals(CameraFacing.BACK, model.uiState.value.facing)
        assertEquals(listOf(CameraFacing.FRONT, CameraFacing.BACK), camera.facingRequests)
    }

    @Test
    fun switchCamera_withoutAFrontCamera_doesNothing() = runTest {
        camera.hasFrontCamera.value = false
        runCurrent()
        assertFalse(model.uiState.value.canSwitchCamera)

        model.onSwitchCamera()
        runCurrent()

        assertTrue(camera.facingRequests.isEmpty())
        assertEquals(CameraFacing.BACK, model.uiState.value.facing)
    }

    @Test
    fun switchCamera_whileAPhotoIsSaved_doesNothing() = runTest {
        runCurrent()
        model.onShutter()
        runCurrent()
        // The shutter is free again, and the photo is still being saved.
        camera.captured.single()()
        assertTrue(model.uiState.value.isSaving)

        model.onSwitchCamera()
        runCurrent()

        assertTrue(camera.facingRequests.isEmpty())
    }

    // The shutter and the photos in flight (NFR-19, #208). The shutter: busy from the press until the camera took the
    // photo, then free while the photo is saved. Photos in flight: none, one, or two, each saved or failed, in shutter
    // order or the newer one first; the camera switch and the ratio wait for all of them. A late "taken" of an earlier
    // photo frees nothing.
    @Test
    fun shutter_onceTheCameraTookThePhoto_isFreeWhileThePhotoIsSaved() = runTest {
        runCurrent()
        model.onShutter()
        runCurrent()
        assertTrue(model.uiState.value.shutterBusy)

        camera.captured.single()()

        assertFalse(model.uiState.value.shutterBusy)
        assertTrue(model.uiState.value.isSaving)
    }

    @Test
    fun shutter_pressedWhileAPhotoIsSaved_takesAnother_andEachPhotoKeepsItsSession() = runTest {
        val share = RecordingShare()
        val sharing = CameraViewModel(camera, guides, photos = share, clock = { 1_000 })
        val first = camera.capture
        val second = CompletableDeferred<Result<Uri>>()
        val photo = Uri.parse("content://media/external/images/media/2")
        sharing.onShutter()
        runCurrent()
        camera.captured[0]()
        share.session = "s2"
        camera.capture = second

        val taken = sharing.onShutter()
        runCurrent()
        first.complete(Result.failure(IOException("Storage full")))
        second.complete(Result.success(photo))
        runCurrent()

        assertTrue(taken)
        assertEquals(2, camera.captureCount)
        assertEquals(listOf("s1"), share.failed)
        assertEquals(listOf(Triple("s2", photo, 1_000L)), share.shared)
        assertEquals(photo, sharing.uiState.value.lastPhoto)
    }

    @Test
    fun saving_withTwoPhotosInFlight_endsWithTheLastOne() = runTest {
        val first = camera.capture
        val second = CompletableDeferred<Result<Uri>>()
        runCurrent()
        model.onShutter()
        runCurrent()
        camera.captured[0]()
        camera.capture = second
        model.onShutter()
        runCurrent()
        camera.captured[1]()

        first.complete(Result.success(Uri.parse("content://media/external/images/media/1")))
        runCurrent()
        assertTrue(model.uiState.value.isSaving)
        model.onSwitchCamera()
        model.onAspectChosen(AspectRatio.R16_9)
        runCurrent()
        assertTrue(camera.facingRequests.isEmpty())
        assertTrue(camera.aspectRequests.isEmpty())

        second.complete(Result.success(Uri.parse("content://media/external/images/media/2")))
        runCurrent()
        assertFalse(model.uiState.value.isSaving)
    }

    @Test
    fun photos_savedOutOfOrder_arePublishedInShutterOrder() = runTest {
        val share = RecordingShare()
        val sharing = CameraViewModel(camera, guides, photos = share, clock = { 1_000 })
        val first = camera.capture
        val second = CompletableDeferred<Result<Uri>>()
        val older = Uri.parse("content://media/external/images/media/1")
        val newer = Uri.parse("content://media/external/images/media/2")
        sharing.onShutter()
        runCurrent()
        camera.captured[0]()
        camera.capture = second
        sharing.onShutter()
        runCurrent()

        // The newer photo is saved first: it waits for the older one.
        second.complete(Result.success(newer))
        runCurrent()
        assertTrue(share.shared.isEmpty())
        assertNull(sharing.uiState.value.lastPhoto)
        first.complete(Result.success(older))
        runCurrent()

        assertEquals(listOf(older, newer), share.shared.map { it.second })
        assertEquals(newer, sharing.uiState.value.lastPhoto)
        assertFalse(sharing.uiState.value.isSaving)
    }

    // Camera's thumbnail (FR-1.6; Iter4-A2): outside a session the newest in Pix's album, taken here or received, and a
    // photo taken here at once; in a session only the session's newest, none until its first; after it, the album's
    // newest again. The viewer (FR-1.6): outside a session Pix's album, with a photo just taken; in a session only the
    // session's photos; none to open without photos, and it closes when a session starts or ends. A photo deleted from
    // it leaves its pages and the thumbnail at once, before MediaStore tells of it, also one just taken; the last one
    // closes it; a delete that fails keeps the photo and says so once.
    private fun photo(id: Int): Uri = Uri.parse("content://media/external/images/media/$id")

    @Test
    fun lastPhoto_outsideASession_isTheAlbumsNewest_orThePhotoJustTaken() = runTest {
        val album = MutableStateFlow(listOf(photo(10)))
        val shooting = CameraViewModel(camera, guides, albumPhotos = album)
        runCurrent()
        assertEquals(photo(10), shooting.uiState.value.lastPhoto)

        shooting.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(photo(11)))
        runCurrent()
        assertEquals(photo(11), shooting.uiState.value.lastPhoto)

        // One received in a session, then the newest deleted in the gallery, then all of them.
        album.value = listOf(photo(10), photo(12))
        runCurrent()
        assertEquals(photo(12), shooting.uiState.value.lastPhoto)
        album.value = listOf(photo(10))
        runCurrent()
        assertEquals(photo(10), shooting.uiState.value.lastPhoto)
        album.value = emptyList()
        runCurrent()
        assertNull(shooting.uiState.value.lastPhoto)
    }

    @Test
    fun lastPhoto_inASession_isOnlyTheSessionsNewest_noneUntilItsFirst_andTheAlbumsAfterIt() = runTest {
        val album = MutableStateFlow(listOf(photo(10)))
        val session = MutableStateFlow<List<Uri>?>(null)
        val shooting = CameraViewModel(camera, guides, albumPhotos = album, sessionPhotos = session)
        runCurrent()

        session.value = emptyList()
        runCurrent()
        assertNull(shooting.uiState.value.lastPhoto)
        assertEquals(emptyList<Uri>(), shooting.uiState.value.sessionPhotos)

        // A photo saved here that is not the session's, such as one pressed before it started, stays out of it.
        shooting.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(photo(11)))
        runCurrent()
        assertNull(shooting.uiState.value.lastPhoto)

        session.value = listOf(photo(12))
        album.value = listOf(photo(10), photo(11), photo(12))
        runCurrent()
        assertEquals(photo(12), shooting.uiState.value.lastPhoto)

        session.value = null
        runCurrent()
        assertEquals(photo(12), shooting.uiState.value.lastPhoto)
        assertNull(shooting.uiState.value.sessionPhotos)
    }

    @Test
    fun photos_outsideASession_openPixsAlbum_withThePhotoJustTaken_andCloseWhenASessionStarts() = runTest {
        val album = MutableStateFlow(emptyList<Uri>())
        val session = MutableStateFlow<List<Uri>?>(null)
        val shooting = CameraViewModel(camera, guides, albumPhotos = album, sessionPhotos = session)
        runCurrent()
        shooting.openPhotos()
        assertFalse(shooting.uiState.value.photosOpen)

        album.value = listOf(photo(10), photo(11))
        runCurrent()
        shooting.openPhotos()
        assertTrue(shooting.uiState.value.photosOpen)
        assertEquals(listOf(photo(10), photo(11)), shooting.uiState.value.viewerPhotos)
        shooting.closePhotos()
        assertFalse(shooting.uiState.value.photosOpen)

        // A photo just taken is a page at once, before MediaStore tells of it.
        shooting.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(photo(12)))
        runCurrent()
        assertEquals(listOf(photo(10), photo(11), photo(12)), shooting.uiState.value.viewerPhotos)

        shooting.openPhotos()
        session.value = emptyList()
        runCurrent()
        assertFalse(shooting.uiState.value.photosOpen)
    }

    @Test
    fun photos_inASession_openOnlyTheSessions_andCloseWhenItEnds() = runTest {
        val album = MutableStateFlow(listOf(photo(10)))
        val session = MutableStateFlow<List<Uri>?>(emptyList())
        val shooting = CameraViewModel(camera, guides, albumPhotos = album, sessionPhotos = session)
        runCurrent()
        // The album has a photo, but none is the session's.
        shooting.openPhotos()
        assertFalse(shooting.uiState.value.photosOpen)

        session.value = listOf(photo(11))
        runCurrent()
        shooting.openPhotos()
        assertTrue(shooting.uiState.value.photosOpen)
        assertEquals(listOf(photo(11)), shooting.uiState.value.viewerPhotos)
        shooting.closePhotos()
        assertFalse(shooting.uiState.value.photosOpen)

        shooting.openPhotos()
        session.value = null
        runCurrent()
        assertFalse(shooting.uiState.value.photosOpen)
    }

    @Test
    fun deletePhoto_outsideASession_dropsItFromThePages_andTheThumbnail_atOnce_andTheLastClosesTheViewer() = runTest {
        val album = MutableStateFlow(listOf(photo(10), photo(11)))
        val deleted = mutableListOf<Uri>()
        val shooting =
            CameraViewModel(
                camera,
                guides,
                albumPhotos = album,
                deleter = PhotoDeleter {
                    deleted += it
                    true
                },
            )
        runCurrent()
        shooting.openPhotos()

        // MediaStore tells of it later; the album here never does.
        shooting.deletePhoto(photo(11))
        runCurrent()
        assertEquals(listOf(photo(11)), deleted)
        assertEquals(listOf(photo(10)), shooting.uiState.value.viewerPhotos)
        assertEquals(photo(10), shooting.uiState.value.lastPhoto)
        assertTrue(shooting.uiState.value.photosOpen)

        shooting.deletePhoto(photo(10))
        runCurrent()
        assertEquals(emptyList<Uri>(), shooting.uiState.value.viewerPhotos)
        assertNull(shooting.uiState.value.lastPhoto)
        assertFalse(shooting.uiState.value.photosOpen)
    }

    @Test
    fun deletePhoto_thePhotoJustTaken_leavesThePages_beforeMediaStoreTellsOfIt() = runTest {
        val album = MutableStateFlow(listOf(photo(10)))
        val shooting = CameraViewModel(camera, guides, albumPhotos = album, deleter = PhotoDeleter { true })
        runCurrent()
        shooting.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(photo(12)))
        runCurrent()

        shooting.deletePhoto(photo(12))
        runCurrent()

        assertEquals(listOf(photo(10)), shooting.uiState.value.viewerPhotos)
        assertEquals(photo(10), shooting.uiState.value.lastPhoto)
    }

    @Test
    fun deletePhoto_thatFails_keepsThePhoto_andSaysSoOnce() = runTest {
        val album = MutableStateFlow(listOf(photo(10)))
        val shooting = CameraViewModel(camera, guides, albumPhotos = album, deleter = PhotoDeleter { false })
        val notices = mutableListOf<Unit>()
        backgroundScope.launch { shooting.photoNotDeleted.collect { notices += it } }
        runCurrent()
        shooting.openPhotos()

        shooting.deletePhoto(photo(10))
        runCurrent()

        assertEquals(1, notices.size)
        assertEquals(listOf(photo(10)), shooting.uiState.value.viewerPhotos)
        assertTrue(shooting.uiState.value.photosOpen)
    }

    @Test
    fun shutter_aLateTakenOfAnEarlierPhoto_doesNotFreeTheNextPress() = runTest {
        runCurrent()
        // A camera that never says "taken": the shutter is free once the photo is saved.
        model.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(Uri.parse("content://media/external/images/media/1")))
        runCurrent()
        assertFalse(model.uiState.value.shutterBusy)
        camera.capture = CompletableDeferred()
        model.onShutter()
        runCurrent()

        camera.captured[0]()

        assertTrue(model.uiState.value.shutterBusy)
    }

    // Photos as held (FR-1.5, #206): each is saved at the turn of its own press, also when the phone turns before the
    // camera takes it and while an earlier photo is saved.
    @Test
    fun shutter_savesEachPhotoAtTheTurnOfItsOwnPress() = runTest {
        val turns = PhoneTurns.Fixed(90)
        val held = CameraViewModel(camera, guides, phoneTurns = turns)
        runCurrent()
        held.onShutter()
        turns.set(0)
        runCurrent()
        camera.captured[0]()
        turns.set(270)

        held.onShutter()
        runCurrent()

        assertEquals(listOf(90, 270), camera.turns)
    }

    @Test
    fun captureIsBlockedUntilCameraReady() = runTest {
        camera.status.value = CameraStatus.STARTING
        model.onShutter()
        runCurrent()
        assertEquals(0, camera.captureCount)
        assertFalse(model.uiState.value.isSaving)
    }

    @Test
    fun repeatedTapsDoNotQueueCapturesAndFailureAllowsRetry() = runTest {
        runCurrent()
        model.onShutter()
        model.onShutter()
        assertTrue(model.uiState.value.isSaving)
        runCurrent()
        assertEquals(1, camera.captureCount)

        camera.capture.complete(Result.failure(IOException("Storage full")))
        runCurrent()
        assertFalse(model.uiState.value.isSaving)
        assertEquals(CaptureNotice.SAVE_FAILED, model.uiState.value.captureNotice)
        assertNull(model.uiState.value.lastPhoto)
        assertEquals(guides.state.value, model.uiState.value.guideState)

        camera.capture = CompletableDeferred()
        model.onShutter()
        runCurrent()
        assertEquals(2, camera.captureCount)
        assertNull(model.uiState.value.captureNotice)
        camera.capture.complete(Result.failure(IOException("Storage still full")))
        runCurrent()
    }

    @Test
    fun aSavedPhotoIsSharedWithTheMomentOfTheShutter() = runTest {
        val share = RecordingShare()
        val sharing = CameraViewModel(camera, guides, photos = share, clock = { 1_000 })
        val photo = Uri.parse("content://media/external/images/media/1")

        sharing.onShutter()
        runCurrent()
        // Asked at the shutter, not once it is saved: the session may have changed by then.
        share.session = "another"
        camera.capture.complete(Result.success(photo))
        runCurrent()

        assertEquals(listOf(Triple("s1", photo, 1_000L)), share.shared)
    }

    /** A session s1 until a test changes it. */
    private class RecordingShare : PhotoShare {
        var session: String? = "s1"
        val shared = mutableListOf<Triple<String, Uri, Long>>()
        val failed = mutableListOf<String>()

        override fun shutter() = session

        override fun taken(sessionId: String, uri: Uri, takenAt: Long) {
            shared += Triple(sessionId, uri, takenAt)
        }

        override fun notTaken(sessionId: String) {
            failed += sessionId
        }
    }

    @Test
    fun aPhotoOutsideASessionIsNotShared() = runTest {
        val share = RecordingShare().apply { session = null }
        val sharing = CameraViewModel(camera, guides, photos = share)

        sharing.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(Uri.parse("content://media/external/images/media/1")))
        runCurrent()

        assertTrue(share.shared.isEmpty())
    }

    @Test
    fun aPhotoThatFailedIsNotShared() = runTest {
        val share = RecordingShare()
        val sharing = CameraViewModel(camera, guides, photos = share)

        sharing.onShutter()
        runCurrent()
        camera.capture.complete(Result.failure(IOException("Storage full")))
        runCurrent()

        assertTrue(share.shared.isEmpty())
        assertEquals(listOf("s1"), share.failed)
    }

    @Test
    fun aSavedPhoto_updatesTheThumbnail_withNoNotice() = runTest {
        val photo = Uri.parse("content://media/external/images/media/1")
        model.onShutter()
        runCurrent()
        camera.capture.complete(Result.success(photo))
        runCurrent()

        // No notice (FR-1.6, #204): the thumbnail shows the photo.
        assertEquals(photo, model.uiState.value.lastPhoto)
        assertFalse(model.uiState.value.isSaving)
        assertNull(model.uiState.value.captureNotice)
    }

    @Test
    fun saveFailureStaysUntilTheNextPhoto() = runTest {
        model.onShutter()
        runCurrent()
        camera.capture.complete(Result.failure(IOException("Storage full")))
        runCurrent()
        advanceTimeBy(CameraViewModel.NOTICE_MS * 2)
        runCurrent()
        assertEquals(CaptureNotice.SAVE_FAILED, model.uiState.value.captureNotice)
    }

    @Test
    fun unexpectedCaptureExceptionDoesNotLeaveShutterBusy() = runTest {
        camera.throwOnCapture = true
        model.onShutter()
        runCurrent()
        assertFalse(model.uiState.value.shutterBusy)
        assertFalse(model.uiState.value.isSaving)
        assertEquals(CaptureNotice.SAVE_FAILED, model.uiState.value.captureNotice)
    }

    @Test
    fun zoomDisplayFollowsAppliedCameraStateIncludingRemoteChanges() = runTest {
        runCurrent()
        model.onZoomChanged(1.73f)
        assertEquals(listOf(1.73f), camera.zoomRequests)
        // A single change, like a Zoom in / Zoom out action, is final.
        assertEquals(listOf(true), camera.zoomFinals)
        // A request is not yet the applied value; the camera remains authoritative.
        assertEquals(1f, model.uiState.value.zoom)
        camera.zoom.value = 1.8f
        runCurrent()
        assertEquals(1.8f, model.uiState.value.zoom)
        camera.zoom.value = 3f
        runCurrent()
        assertEquals(3f, model.uiState.value.zoom)
    }

    @Test
    fun invalidZoomAndUnavailableCameraDoNotIssueCommands() = runTest {
        model.onZoomChanged(Float.NaN)
        model.onZoomChanged(Float.POSITIVE_INFINITY)
        camera.status.value = CameraStatus.UNAVAILABLE
        model.onZoomChanged(2f)
        model.onShutter()
        runCurrent()
        assertTrue(camera.zoomRequests.isEmpty())
        assertEquals(0, camera.captureCount)
        assertEquals(CameraStatus.UNAVAILABLE, model.uiState.value.cameraStatus)
    }

    @Test
    fun pinchRequestsContinueAcrossLensTransitionAndReadoutWaitsForAppliedZoom() = runTest {
        camera.capabilities.value = CameraCapabilities(0.5f, 4f, listOf(0.5f, 0.6f, 1f, 2f, 3f))
        runCurrent()
        model.onZoomChanged(0.9f, final = false)
        camera.status.value = CameraStatus.STARTING
        model.onZoomChanged(0.7f, final = false)
        model.onZoomChanged(0.5f, final = true)
        runCurrent()

        assertEquals(listOf(0.9f, 0.7f, 0.5f), camera.zoomRequests)
        // The camera holds its lens until the pinch's last value (Design 2.6.5).
        assertEquals(listOf(false, false, true), camera.zoomFinals)
        assertEquals(1f, model.uiState.value.zoom)
        assertEquals(0.5f, model.uiState.value.minZoom)
        assertEquals(CameraStatus.STARTING, model.uiState.value.cameraStatus)

        camera.zoom.value = 0.5f
        camera.status.value = CameraStatus.READY
        runCurrent()
        assertEquals(0.5f, model.uiState.value.zoom)
        assertEquals(CameraStatus.READY, model.uiState.value.cameraStatus)

        // A normalized remote change must use the same readout as the photographer's pinch.
        camera.zoom.value = 0.75f
        runCurrent()
        assertEquals(0.75f, model.uiState.value.zoom)
    }

    @Test
    fun zoomCannotStartUntilTheCameraRangeIsKnown() = runTest {
        camera.capabilities.value = null
        camera.status.value = CameraStatus.STARTING
        model.onZoomChanged(0.5f)
        camera.status.value = CameraStatus.READY
        model.onZoomChanged(1.5f)
        runCurrent()
        assertTrue(camera.zoomRequests.isEmpty())
    }

    @Test
    fun idleCameraRejectsZoomEvenWithRetainedCapabilities() = runTest {
        camera.status.value = CameraStatus.IDLE
        model.onZoomChanged(0.5f)
        runCurrent()
        assertTrue(camera.zoomRequests.isEmpty())
    }

    @Test
    fun startingCameraRejectsNonFiniteRequests() = runTest {
        camera.status.value = CameraStatus.STARTING
        model.onZoomChanged(Float.NaN)
        model.onZoomChanged(Float.POSITIVE_INFINITY)
        model.onZoomChanged(Float.NEGATIVE_INFINITY)
        runCurrent()
        assertTrue(camera.zoomRequests.isEmpty())
    }

    @Test
    fun cancellationDoesNotShowSaveFailure() = runTest {
        model.onShutter()
        runCurrent()
        camera.capture.complete(Result.failure(CancellationException("Screen closed")))
        runCurrent()
        assertFalse(model.uiState.value.isSaving)
        assertNull(model.uiState.value.captureNotice)
    }

    @Test
    fun captureErrorsHaveActionableMessages() {
        assertEquals(CaptureNotice.PERMISSION_REQUIRED, captureFailureNotice(SecurityException()))
        assertEquals(
            CaptureNotice.STORAGE_FAILED,
            captureFailureNotice(ImageCaptureException(ImageCapture.ERROR_FILE_IO, "No space", null)),
        )
        assertEquals(
            CaptureNotice.CAMERA_UNAVAILABLE,
            captureFailureNotice(ImageCaptureException(ImageCapture.ERROR_CAMERA_CLOSED, "Closed", null)),
        )
        assertEquals(CaptureNotice.SAVE_FAILED, captureFailureNotice(IllegalStateException()))
    }

    private class FakeCamera : CameraController {
        override val status = MutableStateFlow(CameraStatus.READY)
        override val capabilities = MutableStateFlow<CameraCapabilities?>(
            CameraCapabilities(1f, 4f, listOf(1f, 2f, 3f)),
        )
        override val zoom = MutableStateFlow(1f)
        override val state = MutableStateFlow(CameraState(zoom = 1f))
        var capture = CompletableDeferred<Result<Uri>>()
        var captureCount = 0
        var throwOnCapture = false
        val zoomRequests = mutableListOf<Float>()
        val zoomFinals = mutableListOf<Boolean>()

        override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit
        override fun setZoom(ratio: Float, final: Boolean) {
            zoomRequests.add(ratio)
            zoomFinals.add(final)
        }
        override fun setFocusPoint(x: Float, y: Float) = Unit
        override fun setExposure(index: Int) = Unit
        override fun setFlash(mode: FlashMode) = Unit
        override fun setTorch(on: Boolean) = Unit
        val aspectRequests = mutableListOf<AspectRatio>()
        override fun setAspectRatio(ratio: AspectRatio) {
            aspectRequests += ratio
        }

        /** Each photo's "the camera took it" callback, in shutter order; a test calls one to say so. */
        val captured = mutableListOf<() -> Unit>()

        /** The turn each photo was saved at, in shutter order (FR-1.5). */
        val turns = mutableListOf<Int>()
        private var turn = 0

        override fun setPhotoTurn(turn: Int) {
            this.turn = turn
        }

        override suspend fun takePhoto(onCaptured: () -> Unit): Result<Uri> {
            captureCount++
            turns += turn
            if (throwOnCapture) throw IllegalStateException("Camera closed")
            captured += onCaptured
            return capture.await()
        }
        override suspend fun grabFrame(): Result<Bitmap> = Result.failure(IllegalStateException("No frame"))
        override fun setFrameSink(sink: FrameSink?) = Unit
        override val facing = MutableStateFlow(CameraFacing.BACK)
        override val hasFrontCamera = MutableStateFlow(true)
        val facingRequests = mutableListOf<CameraFacing>()
        override fun setFacing(facing: CameraFacing) {
            facingRequests += facing
            this.facing.value = facing
        }
    }

    @Test
    fun `removing the guide clears the repository`() = runTest(dispatcher) {
        runCurrent()

        model.onRemoveGuide()
        runCurrent()

        assertEquals(listOf<ReferenceGuide?>(null), guides.set)
        assertNull(model.uiState.value.guide)
    }

    @Test
    fun `a new guide starts edit mode, and the shutter, hiding, or removing the guide ends it`() = runTest(dispatcher) {
        runCurrent()
        assertFalse(model.uiState.value.guideEditing)
        model.setGuideEditing(true)
        assertFalse(model.uiState.value.guideEditing)

        guides.setGuide(guide("g1"))
        runCurrent()
        assertTrue(model.uiState.value.guideEditing)
        model.setGuideEditing(false)
        assertFalse(model.uiState.value.guideEditing)
        model.setGuideEditing(true)
        assertTrue(model.uiState.value.guideEditing)

        model.onShutter()
        assertFalse(model.uiState.value.guideEditing)
        camera.capture.complete(Result.success(Uri.parse("content://media/1")))
        runCurrent()
        assertFalse(model.uiState.value.guideEditing)

        guides.setGuide(guide("g2"))
        runCurrent()
        assertTrue(model.uiState.value.guideEditing)
        guides.state.value = guides.state.value.copy(visible = false)
        runCurrent()
        assertFalse(model.uiState.value.guideEditing)
        model.setGuideEditing(true)
        assertFalse(model.uiState.value.guideEditing)
        guides.state.value = guides.state.value.copy(visible = true)
        runCurrent()
        assertFalse(model.uiState.value.guideEditing)
        model.setGuideEditing(true)
        assertTrue(model.uiState.value.guideEditing)

        model.setGuideEditing(true)
        guides.setGuide(null)
        runCurrent()
        assertFalse(model.uiState.value.guideEditing)
    }

    private fun guide(id: String): ReferenceGuide {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return ReferenceGuide(id, bitmap, bitmap, 0.5f, GuideSource.GALLERY)
    }

    private class FakeGuides : GuideRepository {
        override val guide = MutableStateFlow<ReferenceGuide?>(null)
        override val state = MutableStateFlow(GuideState(cx = 0.3f, height = 0.8f))
        override val changes = MutableSharedFlow<GuideChange>()
        val set = mutableListOf<ReferenceGuide?>()
        override fun setGuide(guide: ReferenceGuide?) {
            set += guide
            this.guide.value = guide
        }
        override fun update(final: Boolean, change: (GuideState) -> GuideState) = Unit
    }
}
