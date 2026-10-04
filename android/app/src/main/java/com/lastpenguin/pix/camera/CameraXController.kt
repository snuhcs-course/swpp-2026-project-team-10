package com.lastpenguin.pix.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.lastpenguin.pix.core.Timings
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** CameraX is the sole camera owner; the preview's UI and guide never enter photos or analysis frames. */
class CameraXController(
    context: Context,
    private val photoSaver: PhotoSaver,
) : CameraController {
    private val context = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(this.context)
    private var provider: ProcessCameraProvider? = null

    @Volatile
    private var binding: Binding? = null

    @Volatile
    private var frameSink: FrameSink? = null

    private val _status = MutableStateFlow(CameraStatus.IDLE)
    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoom: StateFlow<Float> = _zoom.asStateFlow()

    // CameraX resets ZoomState when its lifecycle stops. Preserve only a successfully applied
    // request separately, so that the reset is still published without replacing the user's framing.
    private var retainedZoom = 1f

    override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        mainExecutor.execute {
            if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@execute
            val previous = binding
            if (previous?.owner === owner && _status.value != CameraStatus.UNAVAILABLE) {
                if (previous.surfaceProvider !== surfaceProvider) {
                    previous.surfaceProvider = surfaceProvider
                    previous.preview?.setSurfaceProvider(surfaceProvider)
                }
                refreshStatus(previous)
                return@execute
            }
            releaseBinding()
            val current = Binding(owner, surfaceProvider)
            binding = current
            current.lifecycleObserver = LifecycleEventObserver { _, event ->
                if (binding !== current) return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        current.active = false
                        current.open = false
                        current.restoreZoomOnOpen = true
                        current.restoringZoom = false
                        current.zoomRequestId++
                        current.frames.clear()
                        refreshStatus(current)
                    }

                    Lifecycle.Event.ON_DESTROY -> releaseBinding()

                    Lifecycle.Event.ON_START -> {
                        current.active = true
                        restoreZoomIfNeeded(current)
                        refreshStatus(current)
                    }

                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(current.lifecycleObserver!!)
            refreshStatus(current)
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (binding !== current || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                        return@addListener
                    }
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        bindUseCases(cameraProvider, current, current.surfaceProvider)
                    } catch (error: Exception) {
                        failBinding(current, error)
                    }
                }, mainExecutor)
            } catch (error: Exception) {
                failBinding(current, error)
            }
        }
    }

    private fun bindUseCases(
        cameraProvider: ProcessCameraProvider,
        current: Binding,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        check(cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) { "No rear camera is available" }
        // Pix is portrait-only. All outputs describe the same 3:4 field of view (AD-10).
        val rotation = Surface.ROTATION_0
        val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
            .build()
        val capture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspect)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    .build(),
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspect)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
        current.preview = preview
        current.capture = capture
        current.analysis = analysis
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "PixCameraAnalysis") }
        current.executor = executor
        analysis.setAnalyzer(executor) { image -> analyze(current, image) }
        preview.setSurfaceProvider(surfaceProvider)
        val group = UseCaseGroup.Builder()
            // ViewPort.FIT ignores aspect ratio. Crop to 3:4 here, then display with PreviewView.FIT_CENTER.
            .setViewPort(ViewPort.Builder(Rational(3, 4), rotation).setScaleType(ViewPort.FILL_CENTER).build())
            .addUseCase(preview)
            .addUseCase(capture)
            .addUseCase(analysis)
            .build()
        val camera = cameraProvider.bindToLifecycle(current.owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
        current.camera = camera
        current.zoomObserver = Observer { state ->
            if (binding === current) publishZoom(state)
        }
        camera.cameraInfo.zoomState.observe(current.owner, current.zoomObserver!!)
        current.stateObserver = Observer { state ->
            if (binding === current) {
                val open = state.type == CameraState.Type.OPEN
                if (current.open && !open) {
                    // A transient camera disconnect can also reset zoom without stopping the screen.
                    current.restoreZoomOnOpen = true
                    current.restoringZoom = false
                    current.zoomRequestId++
                }
                current.open = open
                // CameraX can wait for another camera client without attaching an error.
                // Keep retry/settings guidance visible while waiting; a later OPEN recovers automatically.
                current.failed = state.error != null || state.type == CameraState.Type.PENDING_OPEN
                if (!current.open || current.failed) current.frames.clear()
                state.error?.let { Log.w(TAG, "Camera state error ${it.code}", it.cause) }
                restoreZoomIfNeeded(current)
                refreshStatus(current)
            }
        }
        camera.cameraInfo.cameraState.observe(current.owner, current.stateObserver!!)
    }

    override fun setZoom(ratio: Float) {
        mainExecutor.execute {
            val current = binding ?: return@execute
            val range = _capabilities.value ?: return@execute
            val requested = clampedCameraZoom(ratio, range.minZoom, range.maxZoom) ?: return@execute
            if (_status.value != CameraStatus.READY) return@execute
            applyZoom(current, requested)
        }
    }

    private fun restoreZoomIfNeeded(current: Binding) {
        if (!current.active || !current.open || current.failed || !current.restoreZoomOnOpen) return
        val state = current.camera?.cameraInfo?.zoomState?.value ?: return
        val requested = retainedZoom.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        current.restoreZoomOnOpen = false
        if (state.zoomRatio == requested) return
        current.restoringZoom = true
        applyZoom(current, requested)
    }

    private fun applyZoom(current: Binding, requested: Float) {
        val camera = current.camera ?: return
        val requestId = ++current.zoomRequestId
        val restoring = current.restoringZoom
        val future = try {
            camera.cameraControl.setZoomRatio(requested)
        } catch (error: Exception) {
            current.restoringZoom = false
            if (restoring) current.failed = true
            Log.w(TAG, "Zoom request could not start", error)
            refreshStatus(current)
            return
        }
        future.addListener({
            if (binding !== current || current.zoomRequestId != requestId) return@addListener
            try {
                future.get()
                // Successful completion confirms this ratio was applied. A reset to 1x delivered
                // by the lifecycle's ZoomState observer must never replace this retained value.
                retainedZoom = requested
            } catch (_: CancellationException) {
                // A newer gesture or unbind supersedes the previous request.
                if (restoring) current.failed = true
            } catch (error: Exception) {
                if (restoring) current.failed = true
                Log.w(TAG, "Zoom request was not applied", error)
            }
            current.restoringZoom = false
            // Never echo the retained/requested value: CameraX's observed zoom is authoritative.
            camera.cameraInfo.zoomState.value?.let(::publishZoom)
            refreshStatus(current)
        }, mainExecutor)
    }

    override suspend fun takePhoto(): Result<Uri> = withContext(Dispatchers.Main.immediate) {
        val capture = binding?.capture
        if (_status.value != CameraStatus.READY || capture == null) {
            return@withContext Result.failure(IllegalStateException("The camera is not ready"))
        }
        suspendCancellableCoroutine { continuation ->
            try {
                Timings.mark("camera.capture.start")
                capture.takePicture(
                    photoSaver.outputOptions(),
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val result = output.savedUri?.let { Result.success(it) }
                                ?: Result.failure(IOException("The saved photo has no gallery URI"))
                            if (result.isSuccess) {
                                Timings.mark("camera.capture.saved")
                            } else {
                                Timings.mark("camera.capture.failed", "missing_uri")
                            }
                            if (continuation.isActive) continuation.resume(result)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Timings.mark("camera.capture.failed", "code=${exception.imageCaptureError}")
                            if (continuation.isActive) continuation.resume(Result.failure(exception))
                        }
                    },
                )
            } catch (error: Exception) {
                Timings.mark("camera.capture.failed", error.javaClass.simpleName)
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
            // CameraX cannot cancel a dispatched save; a cancelled caller must not receive a late callback.
        }
    }

    override suspend fun grabFrame(): Result<Bitmap> {
        // Snapshot before dispatching: a caller can navigate away immediately after starting this operation.
        val snapshot = if (_status.value == CameraStatus.READY) binding?.frames?.snapshot() else null
        if (snapshot == null) return Result.failure(IllegalStateException("No live camera frame is available"))
        return withContext(Dispatchers.Default) {
            try {
                Result.success(snapshot.toUprightBitmap())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    override fun setFrameSink(sink: FrameSink?) {
        frameSink = sink
    }

    private fun analyze(current: Binding, image: ImageProxy) {
        val frame = CloseOnceImageProxy(image)
        try {
            if (binding !== current || !current.active) return
            current.frames.update(frame)
            if (binding !== current || !current.active) {
                current.frames.clear()
                return
            }
            if (_status.value != CameraStatus.READY) mainExecutor.execute { refreshStatus(current) }
            frameSink?.onFrame(frame)
        } catch (error: Exception) {
            Log.e(TAG, "Could not process camera frame", error)
        } finally {
            frame.close()
        }
    }

    private fun publishZoom(state: ZoomState) {
        _capabilities.value = CameraCapabilities(
            state.minZoomRatio,
            state.maxZoomRatio,
            cameraZoomStops(state.minZoomRatio, state.maxZoomRatio),
        )
        _zoom.value = state.zoomRatio
    }

    private fun refreshStatus(current: Binding) {
        if (binding !== current) return
        _status.value = when {
            !current.owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> CameraStatus.IDLE
            current.failed -> CameraStatus.UNAVAILABLE
            current.restoringZoom || current.restoreZoomOnOpen -> CameraStatus.STARTING
            current.open && current.frames.hasFrame() -> CameraStatus.READY
            else -> CameraStatus.STARTING
        }
    }

    private fun failBinding(current: Binding, error: Exception) {
        if (binding !== current) return
        Log.e(TAG, "Could not bind rear camera", error)
        releaseBinding()
        _status.value = CameraStatus.UNAVAILABLE
    }

    /** Main thread only. Unbind just our three use cases; do not reset the process-wide provider. */
    private fun releaseBinding() {
        val current = binding ?: return
        binding = null
        current.active = false
        _status.value = CameraStatus.IDLE
        _capabilities.value = null
        current.lifecycleObserver?.let { current.owner.lifecycle.removeObserver(it) }
        current.zoomObserver?.let { current.camera?.cameraInfo?.zoomState?.removeObserver(it) }
        current.stateObserver?.let { current.camera?.cameraInfo?.cameraState?.removeObserver(it) }
        current.analysis?.clearAnalyzer()
        val useCases = listOfNotNull(current.preview, current.capture, current.analysis)
        if (useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        current.preview?.setSurfaceProvider(null)
        current.frames.clear()
        current.executor?.shutdown()
    }

    private class Binding(val owner: LifecycleOwner, var surfaceProvider: Preview.SurfaceProvider) {
        val frames = CameraFrameCache()

        @Volatile
        var active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        var preview: Preview? = null
        var capture: ImageCapture? = null
        var analysis: ImageAnalysis? = null
        var executor: ExecutorService? = null
        var camera: Camera? = null
        var lifecycleObserver: LifecycleEventObserver? = null
        var zoomObserver: Observer<ZoomState>? = null
        var stateObserver: Observer<CameraState>? = null
        var open = false
        var failed = false
        var restoreZoomOnOpen = true
        var restoringZoom = false
        var zoomRequestId = 0L
    }

    /** The sink owns its call's frame, but an omitted close or thrown exception cannot stall CameraX. */
    private class CloseOnceImageProxy(private val delegate: ImageProxy) : ImageProxy by delegate {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) delegate.close()
        }
    }

    private companion object {
        const val TAG = "PixCamera"
    }
}
