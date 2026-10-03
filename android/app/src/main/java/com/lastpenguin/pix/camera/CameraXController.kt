package com.lastpenguin.pix.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.lastpenguin.pix.core.Timings
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [CameraController] on CameraX (Design 2.2, Figure 6; AD-4, AD-10). Preview, ImageCapture, and ImageAnalysis are
 * bound together with one 3:4 viewport, so the preview, the saved photo, and the streamed frames show the same
 * field of view. Analysis frames go to the [FrameSink] while one is set (Design 2.6.4).
 * Owner: Camera/Overlay (#3). Remote zoom (#10) calls [setZoom].
 */
class CameraXController(
    private val context: Context,
    private val photoSaver: PhotoSaver,
) : CameraController {

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor = Executors.newSingleThreadExecutor { Thread(it, "pix-analysis") }

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    @Volatile
    private var sink: FrameSink? = null

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoom: StateFlow<Float> = _zoom.asStateFlow()

    override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({ bindUseCases(providerFuture.get(), owner, surfaceProvider) }, mainExecutor)
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        val preview = Preview.Builder().build().apply { setSurfaceProvider(surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(STREAM_LONG_SIDE, STREAM_SHORT_SIDE),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image ->
            val current = sink
            if (current == null) image.close() else current.onFrame(image)
        }
        val group = UseCaseGroup.Builder()
            // The app is portrait only, so ROTATION_0 makes this a 3:4 portrait frame (AD-10).
            .setViewPort(ViewPort.Builder(Rational(FRAME_WIDTH, FRAME_HEIGHT), Surface.ROTATION_0).build())
            .addUseCase(preview)
            .addUseCase(capture)
            .addUseCase(analysis)
            .build()
        provider.unbindAll()
        val bound = try {
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
        } catch (e: Exception) {
            Log.e(TAG, "Could not bind the camera", e)
            return
        }
        camera = bound
        imageCapture = capture
        bound.cameraInfo.zoomState.observe(owner) { state ->
            _zoom.value = state.zoomRatio
            val caps = capabilitiesOf(state)
            if (_capabilities.value != caps) {
                _capabilities.value = caps
                Timings.mark("camera.bound", "zoom ${caps.minZoom}..${caps.maxZoom} stops=${caps.zoomStops}")
            }
        }
    }

    /** 0.6× only with an ultra-wide lens, 1×, then 2× and 3× if the camera reaches them (Design 2.6.5). */
    private fun capabilitiesOf(state: ZoomState): CameraCapabilities {
        val min = state.minZoomRatio
        val max = state.maxZoomRatio
        val stops = buildList {
            if (min < 1f) add((min * 10).roundToInt() / 10f)
            add(1f)
            if (max >= 2f) add(2f)
            if (max >= 3f) add(3f)
        }
        return CameraCapabilities(minZoom = min, maxZoom = max, zoomStops = stops)
    }

    override fun setZoom(ratio: Float) {
        val current = camera ?: return
        val caps = _capabilities.value
        val clamped = if (caps == null) ratio else ratio.coerceIn(caps.minZoom, caps.maxZoom)
        current.cameraControl.setZoomRatio(clamped)
    }

    override suspend fun takePhoto(): Result<Uri> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("The camera is not ready"))
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                photoSaver.outputOptions(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val uri = output.savedUri
                        continuation.resume(
                            if (uri ==
                                null
                            ) {
                                Result.failure(IllegalStateException("No photo URI"))
                            } else {
                                Result.success(uri)
                            },
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(Result.failure(exception))
                    }
                },
            )
        }
    }

    override suspend fun grabFrame(): Result<Bitmap> {
        val capture = imageCapture ?: return Result.failure(IllegalStateException("The camera is not ready"))
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                mainExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = try {
                            image.toBitmap().upright(image.imageInfo.rotationDegrees)
                        } finally {
                            image.close()
                        }
                        continuation.resume(Result.success(bitmap))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(Result.failure(exception))
                    }
                },
            )
        }
    }

    override fun setFrameSink(sink: FrameSink?) {
        this.sink = sink
    }

    private fun Bitmap.upright(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        const val TAG = "PixCamera"
        const val FRAME_WIDTH = 3
        const val FRAME_HEIGHT = 4
        const val STREAM_LONG_SIDE = 960
        const val STREAM_SHORT_SIDE = 720
    }
}
