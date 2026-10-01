package com.lastpenguin.pix.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [CameraController] on CameraX (Design 2.2, Figure 6).
 * Owner: Camera/Overlay (#3). Remote zoom (#10) calls [setZoom].
 */
class CameraXController(
    private val context: Context,
    private val photoSaver: PhotoSaver,
) : CameraController {

    // Bound together in one UseCaseGroup with a 3:4 ViewPort (AD-10, Design 2.6.2).
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoom: StateFlow<Float> = _zoom.asStateFlow()

    override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        TODO("#3: bind the three use cases, then publish capabilities (zoom stops: Design 2.6.5)")
    }

    override fun setZoom(ratio: Float) {
        TODO("#3: clamp to the capabilities and apply with CameraControl.setZoomRatio")
    }

    override suspend fun takePhoto(): Result<Uri> =
        TODO("#3: ImageCapture with photoSaver.outputOptions(), never with the guide (FR-1.5)")

    override suspend fun grabFrame(): Result<Bitmap> =
        TODO("#3: the latest analysis frame, rotated upright (FR-4.1)")

    override fun setFrameSink(sink: FrameSink?) {
        TODO("#3: pass analysis frames to sink while it is set (Design 2.6.4)")
    }
}
