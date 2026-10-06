package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun unexpectedCaptureExceptionDoesNotLeaveShutterBusy() = runTest {
        camera.throwOnCapture = true
        model.onShutter()
        runCurrent()
        assertFalse(model.uiState.value.isSaving)
        assertEquals(CaptureNotice.SAVE_FAILED, model.uiState.value.captureNotice)
    }

    @Test
    fun zoomDisplayFollowsAppliedCameraStateIncludingRemoteChanges() = runTest {
        runCurrent()
        model.onZoomChanged(1.73f)
        assertEquals(listOf(1.73f), camera.zoomRequests)
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
        model.onZoomChanged(0.9f)
        camera.status.value = CameraStatus.STARTING
        model.onZoomChanged(0.7f)
        model.onZoomChanged(0.5f)
        runCurrent()

        assertEquals(listOf(0.9f, 0.7f, 0.5f), camera.zoomRequests)
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
        var capture = CompletableDeferred<Result<Uri>>()
        var captureCount = 0
        var throwOnCapture = false
        val zoomRequests = mutableListOf<Float>()

        override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit
        override fun setZoom(ratio: Float) {
            zoomRequests.add(ratio)
        }
        override suspend fun takePhoto(): Result<Uri> {
            captureCount++
            if (throwOnCapture) throw IllegalStateException("Camera closed")
            return capture.await()
        }
        override suspend fun grabFrame(): Result<Bitmap> = Result.failure(IllegalStateException("No frame"))
        override fun setFrameSink(sink: FrameSink?) = Unit
    }

    @Test
    fun `removing the guide clears the repository`() = runTest(dispatcher) {
        runCurrent()

        model.onRemoveGuide()
        runCurrent()

        assertEquals(listOf<ReferenceGuide?>(null), guides.set)
        assertNull(model.uiState.value.guide)
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
