package com.lastpenguin.pix.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Supported zoom in primary-rear-camera units (1x). Sent to the subject in `camera.capabilities`. */
@Serializable
data class CameraCapabilities(
    val minZoom: Float,
    val maxZoom: Float,
    /** Subject-side zoom stops, e.g. [0.5, 0.6, 1, 2, 3], filtered to [minZoom, maxZoom]. */
    val zoomStops: List<Float>,
)

/**
 * Receives YUV_420_888 frames on the camera's serial analysis thread, never the main thread.
 * Copy the image (respecting its cropRect and imageInfo.rotationDegrees) and close it before returning.
 * Do not retain the ImageProxy or its planes, or perform blocking network work here.
 */
fun interface FrameSink {
    fun onFrame(image: ImageProxy)
}

/** READY requires an open camera and its first analysis frame. IDLE means no active lifecycle owner. */
enum class CameraStatus { IDLE, STARTING, READY, UNAVAILABLE }

/**
 * The only owner of the camera (Design AD-4, 2.1).
 * Owner: Camera/Overlay.
 */
interface CameraController {
    /** Starts IDLE; binding/opening failures become UNAVAILABLE. Calling bind again retries. */
    val status: StateFlow<CameraStatus>

    val capabilities: StateFlow<CameraCapabilities?>

    /** Zoom ratio actually applied, relative to the primary rear camera across any lens switch. */
    val zoom: StateFlow<Float>

    /** Binds Preview, ImageCapture, and ImageAnalysis with one 3:4 viewport (AD-10). */
    fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)

    /** Primary-relative ratio, clamped to [CameraCapabilities.minZoom, CameraCapabilities.maxZoom]. */
    fun setZoom(ratio: Float)

    /** Full resolution to Pictures/Pix, never with the guide (FR-1.5). */
    suspend fun takePhoto(): Result<Uri>

    /**
     * Copies the latest live frame before suspending, then crops and rotates it upright (FR-4.1).
     * Call before leaving the camera screen; the caller owns the returned bitmap.
     */
    suspend fun grabFrame(): Result<Bitmap>

    /** Frames go to [sink] while it is set; null stops streaming. */
    fun setFrameSink(sink: FrameSink?)
}
