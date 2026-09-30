package com.lastpenguin.pix.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** What the photographer's camera can do. Sent to the subject in `camera.capabilities`. */
@Serializable
data class CameraCapabilities(
    val minZoom: Float,
    val maxZoom: Float,
    /** Zoom chips shown on both phones, e.g. [0.6, 1, 2, 3] within [minZoom, maxZoom]. */
    val zoomStops: List<Float>,
)

/** Receives camera frames for streaming. Must copy the image and close it before returning. */
fun interface FrameSink {
    fun onFrame(image: ImageProxy)
}

/**
 * The only owner of the camera (Design AD-4, 2.1).
 * Owner: Camera/Overlay.
 */
interface CameraController {
    val capabilities: StateFlow<CameraCapabilities?>

    /** Zoom ratio actually applied. */
    val zoom: StateFlow<Float>

    /** Binds Preview, ImageCapture, and ImageAnalysis with one 3:4 viewport (AD-10). */
    fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)

    /** Clamped to [CameraCapabilities.minZoom, CameraCapabilities.maxZoom]. */
    fun setZoom(ratio: Float)

    /** Full resolution to Pictures/Pix, never with the guide (FR-1.5). */
    suspend fun takePhoto(): Result<Uri>

    /** The latest frame, rotated upright. Used as the scene photo for pose generation (FR-4.1). */
    suspend fun grabFrame(): Result<Bitmap>

    /** Frames go to [sink] while it is set; null stops streaming. */
    fun setFrameSink(sink: FrameSink?)
}
