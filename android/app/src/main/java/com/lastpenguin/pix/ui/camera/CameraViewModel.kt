package com.lastpenguin.pix.ui.camera

import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the Camera screen shows (R&S 6.2: Camera, Camera + guide, Photo saved). */
data class CameraUiState(
    val zoomStops: List<Float> = emptyList(),
    val zoom: Float = 1f,
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

    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        // TODO(#3): camera.bind(owner, surfaceProvider).
    }

    fun onShutter() {
        // TODO(#3): camera.takePhoto(), then update lastPhoto.
    }

    fun onZoomChip(ratio: Float) {
        // TODO(#3): camera.setZoom(ratio).
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
