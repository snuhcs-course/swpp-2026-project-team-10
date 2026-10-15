// AI-generated with ChatGPT Codex and Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** The frame's shape (FR-1.13, AD-10), named as on a phone camera; the portrait frame is 3:4, 9:16, or 1:1. */
enum class AspectRatio { R4_3, R16_9, R1_1 }

/** FR-1.12. */
enum class FlashMode { OFF, AUTO, ON }

/** Supported zoom in primary-rear-camera units (1x). Sent to the subject in `camera.capabilities`. */
@Serializable
data class CameraCapabilities(
    val minZoom: Float,
    val maxZoom: Float,
    /** Subject-side zoom stops, e.g. [0.5, 0.6, 1, 2, 3], filtered to [minZoom, maxZoom]. */
    val zoomStops: List<Float>,
    // The controls below are not sent yet: their place in `camera.capabilities` comes with the remote controls
    // (Design 2.5.1, #57), so the subject reads their defaults until then.
    /** Exposure-compensation indexes; 0..0 when the camera has none (FR-1.11). */
    @Transient val exposureRange: IntRange = 0..0,
    /** EV per index step. */
    @Transient val exposureStep: Float = 0f,
    @Transient val hasFlash: Boolean = false,
    /** The ratios this phone can bind; always contains [AspectRatio.R4_3]. */
    @Transient val aspectRatios: List<AspectRatio> = listOf(AspectRatio.R4_3),
    @Transient val supportsFocusPoint: Boolean = false,
)

/** The applied camera values in one place (Design 2.3), which the photographer echoes to the subject. */
data class CameraState(
    val zoom: Float,
    val exposure: Int = 0,
    val flash: FlashMode = FlashMode.OFF,
    val aspect: AspectRatio = AspectRatio.R4_3,
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

    /** Applied zoom, exposure, flash, and aspect ratio in one value. */
    val state: StateFlow<CameraState>

    /** Zoom ratio actually applied, relative to the primary rear camera across any lens switch. */
    val zoom: StateFlow<Float>

    /** Binds Preview, ImageCapture, and ImageAnalysis with one viewport of the chosen aspect ratio (AD-10). */
    fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider)

    /** Primary-relative ratio, clamped to [CameraCapabilities.minZoom, CameraCapabilities.maxZoom]. */
    fun setZoom(ratio: Float)

    /** Focuses and meters at a point of the frame, 0..1 from its top-left corner (FR-1.4). */
    fun setFocusPoint(x: Float, y: Float)

    /** Exposure-compensation index, clamped to [CameraCapabilities.exposureRange] (FR-1.11). */
    fun setExposure(index: Int)

    /** Ignored when [CameraCapabilities.hasFlash] is false (FR-1.12). */
    fun setFlash(mode: FlashMode)

    /** Rebinds the use cases with a viewport of [ratio], after any photo being saved (FR-1.13). */
    fun setAspectRatio(ratio: AspectRatio)

    /** Full resolution in the chosen aspect ratio to Pictures/Pix, never with the guide (FR-1.5). */
    suspend fun takePhoto(): Result<Uri>

    /**
     * Copies the latest live frame before suspending, then crops and rotates it upright (FR-4.1).
     * Call before leaving the camera screen; the caller owns the returned bitmap.
     */
    suspend fun grabFrame(): Result<Bitmap>

    /** Frames go to [sink] while it is set; null stops streaming. */
    fun setFrameSink(sink: FrameSink?)
}
