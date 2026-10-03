package com.lastpenguin.pix.ui.camera

import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Camera screen shows (R&S 6.2: Camera, Camera + guide, Photo saved). */
data class CameraUiState(
    val zoomStops: List<Float> = emptyList(),
    val zoom: Float = 1f,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
    /** The last saved photo, for the thumbnail and "Saved without the guide". */
    val lastPhoto: Uri? = null,
)

/** A short, one-time notice on the Camera screen (FR-1.6, FR-1.7; remote actions come from #10, FR-7.4). */
sealed interface CameraNotice {
    data object PhotoSaved : CameraNotice

    data class SaveFailed(val message: String?) : CameraNotice
}

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

    private val _notices = MutableSharedFlow<CameraNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<CameraNotice> = _notices.asSharedFlow()

    init {
        viewModelScope.launch {
            camera.capabilities.collect { caps -> _uiState.update { it.copy(zoomStops = caps?.zoomStops.orEmpty()) } }
        }
        viewModelScope.launch {
            camera.zoom.collect { zoom -> _uiState.update { it.copy(zoom = zoom) } }
        }
        // TODO(#6): guides.guide and guides.state → uiState.guide and guideState.
    }

    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        camera.bind(owner, surfaceProvider)
    }

    fun onShutter() {
        viewModelScope.launch {
            camera.takePhoto()
                .onSuccess { uri ->
                    _uiState.update { it.copy(lastPhoto = uri) }
                    _notices.tryEmit(CameraNotice.PhotoSaved)
                }
                .onFailure { _notices.tryEmit(CameraNotice.SaveFailed(it.message)) }
        }
    }

    fun onZoomChip(ratio: Float) {
        camera.setZoom(ratio)
    }

    /** From GuideOverlayView.onGesture. */
    fun onGuideGesture(dx: Float, dy: Float, scale: Float, final: Boolean) {
        // TODO(#6): guides.update(final) { it.copy(cx = ..., cy = ..., height = ...) }.
    }

    fun onOpacityChange(opacity: Float, final: Boolean) {
        // TODO(#6): guides.update(final) { it.copy(opacity = ...) }, within 0.1–0.9.
    }

    fun onStyleToggle() {
        // TODO(#6): switch between CUTOUT and OUTLINE.
    }

    fun onRemoveGuide() {
        // TODO(#6): guides.setGuide(null).
    }
}
