// AI-generated with ChatGPT Codex and Claude Code, 2026-10-04, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureNotice { SAVED, SAVE_FAILED, STORAGE_FAILED, CAMERA_UNAVAILABLE, PERMISSION_REQUIRED }

internal fun captureFailureNotice(error: Throwable): CaptureNotice = when {
    error is SecurityException -> CaptureNotice.PERMISSION_REQUIRED

    error is ImageCaptureException && error.imageCaptureError == ImageCapture.ERROR_FILE_IO ->
        CaptureNotice.STORAGE_FAILED

    error is ImageCaptureException && error.imageCaptureError in
        listOf(ImageCapture.ERROR_CAMERA_CLOSED, ImageCapture.ERROR_INVALID_CAMERA) ->
        CaptureNotice.CAMERA_UNAVAILABLE

    else -> CaptureNotice.SAVE_FAILED
}

/** Everything the Camera screen shows (R&S 6.2: Camera, Camera + guide, Photo saved). */
data class CameraUiState(
    val cameraStatus: CameraStatus = CameraStatus.IDLE,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val zoom: Float = 1f,
    val isSaving: Boolean = false,
    /** "Saved without the guide" for [CameraViewModel.NOTICE_MS], or why the last photo failed until the next one. */
    val captureNotice: CaptureNotice? = null,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
    /** The last saved photo, for the thumbnail and "Saved without the guide". */
    val lastPhoto: Uri? = null,
    /** A short notice, such as "Junhyeong set zoom to 2×" (FR-7.4). */
    val notice: String? = null,
)

/**
 * Camera screen (Design 2.2, Figure 6).
 * Owner: Camera/Overlay (#3 camera, #6 guide).
 */
class CameraViewModel(
    private val camera: CameraController,
    private val guides: GuideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    private var savedNoticeJob: Job? = null

    init {
        viewModelScope.launch {
            camera.status.collect { status -> _uiState.update { it.copy(cameraStatus = status) } }
        }
        viewModelScope.launch {
            camera.capabilities.collect { capabilities ->
                _uiState.update {
                    it.copy(
                        minZoom = capabilities?.minZoom ?: 1f,
                        maxZoom = capabilities?.maxZoom ?: 1f,
                    )
                }
            }
        }
        viewModelScope.launch {
            camera.zoom.collect { zoom -> _uiState.update { it.copy(zoom = zoom) } }
        }
        viewModelScope.launch {
            guides.guide.collect { guide -> _uiState.update { it.copy(guide = guide) } }
        }
        viewModelScope.launch {
            guides.state.collect { state -> _uiState.update { it.copy(guideState = state) } }
        }
    }

    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        camera.bind(owner, surfaceProvider)
    }

    fun onShutter() {
        if (_uiState.value.isSaving || camera.status.value != CameraStatus.READY) return
        savedNoticeJob?.cancel()
        // Set before launching so two taps in the same frame cannot enqueue two captures.
        _uiState.update { it.copy(isSaving = true, captureNotice = null) }
        viewModelScope.launch {
            try {
                camera.takePhoto().fold(
                    onSuccess = { uri ->
                        _uiState.update { it.copy(lastPhoto = uri, captureNotice = CaptureNotice.SAVED) }
                        // Only a confirmation (FR-1.6): it goes by itself, while a failure stays until the next photo.
                        savedNoticeJob = viewModelScope.launch {
                            delay(NOTICE_MS)
                            _uiState.update { it.copy(captureNotice = null) }
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiState.update { it.copy(captureNotice = captureFailureNotice(error)) }
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(captureNotice = captureFailureNotice(error)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onZoomChanged(ratio: Float) {
        val status = camera.status.value
        if ((status == CameraStatus.READY || status == CameraStatus.STARTING) &&
            camera.capabilities.value != null && ratio.isFinite()
        ) {
            camera.setZoom(ratio)
        }
    }

    /** From GuideOverlayView.onGesture. */
    fun onGuideGesture(dx: Float, dy: Float, scale: Float, final: Boolean) {
        if (!dx.isFinite() || !dy.isFinite() || !scale.isFinite() || scale <= 0f) return
        // Read inside update: several touch steps may arrive before uiState renders the previous one.
        // An unchanged final step still publishes the reliable gesture-end event.
        guides.update(final) { state ->
            state.copy(cx = state.cx + dx, cy = state.cy + dy, height = state.height * scale)
        }
    }

    /** From the opacity slider; the repository keeps it within 10–90% (FR-3.4). */
    fun onOpacityChange(opacity: Float, final: Boolean) {
        if (!opacity.isFinite()) return
        guides.update(final) { it.copy(opacity = opacity) }
    }

    /** Switches cutout ↔ outline; position and size are untouched (FR-3.5). */
    fun onStyleToggle() {
        guides.update(final = true) { state ->
            state.copy(style = if (state.style == GuideStyle.CUTOUT) GuideStyle.OUTLINE else GuideStyle.CUTOUT)
        }
    }

    /** Removes the guide; a connected subject gets `guide.clear` through GuideSyncer (#9). */
    fun onRemoveGuide() {
        guides.setGuide(null)
    }

    companion object {
        /** How long a short notice stays on the Camera screen: "Saved without the guide" and remote action notices. */
        const val NOTICE_MS = 3_000L
    }
}
