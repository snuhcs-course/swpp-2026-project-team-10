// AI-generated with ChatGPT Codex and Claude Code, 2026-10-31, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState as CameraXState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
import com.lastpenguin.pix.BuildConfig
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
import kotlinx.coroutines.flow.update
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

    private val _state = MutableStateFlow(CameraState(zoom = 1f))
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    // The camera chosen (FR-1.14); a binding keeps the one it was started with until it is replaced.
    private var chosenFacing = CameraFacing.BACK
    private val _facing = MutableStateFlow(chosenFacing)
    override val facing: StateFlow<CameraFacing> = _facing.asStateFlow()

    private val _hasFrontCamera = MutableStateFlow(false)
    override val hasFrontCamera: StateFlow<Boolean> = _hasFrontCamera.asStateFlow()

    // The chosen camera's exposure, flash, and focus support, read once per camera like the zoom plan.
    private var controls: Controls? = null

    // The camera the zoom plan and the controls were read for; another one reads them again.
    private var discoveredFor: CameraFacing? = null

    // Kept across rebinds and lens switches, like retainedZoom: CameraX starts every binding without them.
    private var aspect = AspectRatio.R4_3
    private var retainedExposure = 0
    private var flash = FlashMode.OFF

    // The light for the scene photo (setTorch). CameraX turns it off whenever the camera closes, so it is kept here;
    // only the answer to the newest request since the camera opened is published.
    private var torch = false
    private var torchRequest = 0

    // Whether the bound lens has a flash; a separately exposed ultrawide may not.
    private var lensHasFlash = true

    // CameraX resets ZoomState when its lifecycle stops, and every new binding starts at its lens's own 1×. Preserve
    // only a successfully applied request separately; the zoom observer publishes nothing until it is restored, so the
    // reset never replaces the user's framing on either phone.
    private var retainedZoom = 1f
    private var pendingZoom: Float? = null

    // Whether pendingZoom is the last value of a pinch; until it is, the bound lens is held (CameraZoomPlan.lensFor).
    private var pendingFinal = true

    // At most one request per display frame, the newest: a precaution, because touch events can come faster than the
    // display and each setZoomRatio replaces the camera's repeating request. No stutter was measured from it on the
    // S23 Ultra (Design 2.6.5).
    private var drainScheduled = false
    private val drainOnFrame = Choreographer.FrameCallback {
        drainScheduled = false
        binding?.let(::drainZoom)
    }
    private var zoomPlan: CameraZoomPlan? = null
    private var primarySelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var ultrawideSelector: CameraSelector? = null

    // Until when every camera frame is logged as `zoom.frame` (NFR-19): shortly after each zoom request, lens switch,
    // or shutter press, so `android/scripts/zoom_timings.py` sees those frames without logging the whole session.
    @Volatile
    private var traceUntil = 0L

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
            startBinding(owner, surfaceProvider)
        }
    }

    /** [lens] is where a lens switch is heading; without it, the lens follows the zoom to restore. */
    private fun startBinding(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, lens: ZoomLens? = null) {
        val current = Binding(owner, surfaceProvider, chosenFacing)
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
                    current.requestedNative = null
                    current.exposureRestored = false
                    current.restoringExposure = false
                    pendingZoom = null
                    pendingFinal = true
                    current.frames.clear()
                    torchOff()
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
                    if (discoveredFor != current.facing) {
                        discoveredFor = current.facing
                        controls = null
                        zoomPlan = null
                        ultrawideSelector = null
                    }
                    if (controls == null) discoverControls(cameraProvider, current.facing)
                    if (zoomPlan == null) discoverZoomPlan(cameraProvider, current.facing)
                    val plan = checkNotNull(zoomPlan)
                    val requested = plan.clamp(pendingZoom ?: retainedZoom)!!
                    val target = lens?.takeIf { it == ZoomLens.PRIMARY || plan.ultrawide != null }
                    current.lens = when {
                        target == null -> plan.lensFor(requested)

                        // A request that came after the switch began decides again from where the switch was heading,
                        // so a pinch's last value does not bind one lens and then switch again.
                        pendingZoom != null -> plan.lensFor(requested, target, pendingFinal)

                        else -> target
                    }
                    current.range = plan.rangeFor(current.lens)
                    bindUseCases(cameraProvider, current, current.surfaceProvider)
                } catch (error: Exception) {
                    failBinding(current, error)
                }
            }, mainExecutor)
        } catch (error: Exception) {
            failBinding(current, error)
        }
    }

    private fun discoverZoomPlan(cameraProvider: ProcessCameraProvider, facing: CameraFacing) {
        val infos = cameraProvider.availableCameraInfos
        _hasFrontCamera.value = CameraSelector.DEFAULT_FRONT_CAMERA.filter(infos).isNotEmpty()
        val primary = facing.selector.filter(infos).firstOrNull()
            ?: error("No ${facing.name.lowercase()} camera is available")
        primarySelector = primary.cameraSelector
        val primaryState = checkNotNull(primary.zoomState.value) { "The ${facing.name.lowercase()} camera has no zoom" }
        val primaryRange = checkNotNull(LensZoomRange.create(1f, primaryState.minZoomRatio, primaryState.maxZoomRatio))
        // A logical camera may already expose zoom-out. Prefer that route wherever supported;
        // otherwise use only independently selectable rear lenses, never nested physicalCameraInfos. The front camera
        // zooms on its own lens only (FR-1.14).
        val ultrawide = if (facing == CameraFacing.FRONT) null else findUltrawide(infos, primary, primaryRange)
        ultrawideSelector = ultrawide?.first
        zoomPlan = CameraZoomPlan(primaryRange, ultrawide?.second)
        publishCapabilities()
    }

    /** The widest rear lens that CameraX exposes apart from [primary] and whose range joins the primary's, if any. */
    private fun findUltrawide(
        infos: List<CameraInfo>,
        primary: CameraInfo,
        primaryRange: LensZoomRange,
    ): Pair<CameraSelector, LensZoomRange>? =
        infos.mapNotNull { info ->
            try {
                if (info === primary || info.lensFacing != CameraSelector.LENS_FACING_BACK) return@mapNotNull null
                val intrinsic = info.intrinsicZoomRatio
                if (intrinsic >= 1f) return@mapNotNull null
                val state = info.zoomState.value ?: return@mapNotNull null
                val range = LensZoomRange.create(intrinsic, state.minZoomRatio, state.maxZoomRatio)
                    ?: return@mapNotNull null
                if (CameraZoomPlan(primaryRange, range).ultrawide == null) return@mapNotNull null
                info.cameraSelector to range
            } catch (error: Exception) {
                Log.w(TAG, "Ignoring unavailable ultrawide camera", error)
                null
            }
        }.minByOrNull { it.second.minZoom }

    private fun bindUseCases(
        cameraProvider: ProcessCameraProvider,
        current: Binding,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        // Pix is portrait-only. All outputs describe the same field of view, cut from the sensor's 4:3 (AD-10). The
        // preview mirrors the front camera by itself (CameraX's default); photos and frames are mirrored to match.
        val rotation = Surface.ROTATION_0
        val ratio = this.aspect
        current.aspect = ratio
        val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
            .traceFrames(current.lens)
            .build()
        val capture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flash.captureMode)
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
                        ResolutionStrategy(
                            analysisSize(ratio),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
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
            // ViewPort.FIT ignores aspect ratio. Crop to the chosen ratio here, then display with PreviewView.FIT_CENTER.
            .setViewPort(
                ViewPort.Builder(Rational(ratio.portraitParts.first, ratio.portraitParts.second), rotation)
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build(),
            )
            .addUseCase(preview)
            .addUseCase(capture)
            .addUseCase(analysis)
            .build()
        val selector = if (current.lens == ZoomLens.PRIMARY) primarySelector else checkNotNull(ultrawideSelector)
        val camera = cameraProvider.bindToLifecycle(current.owner, selector, group)
        current.camera = camera
        // A separately exposed ultrawide may have no flash of its own: while it is bound the flash is unavailable,
        // and the chosen mode comes back with the primary camera.
        lensHasFlash = camera.cameraInfo.hasFlashUnit()
        if (!lensHasFlash) capture.flashMode = ImageCapture.FLASH_MODE_OFF
        publishCapabilities()
        _state.update { it.copy(flash = appliedFlash()) }
        current.zoomObserver = Observer { state ->
            // Until the retained zoom is restored, the state is the new lens's own 1× (0.5× on a 0.5 ultrawide):
            // publishing it would flash on the readout and in the subject's echo during a lens switch.
            if (binding === current && !current.restoreZoomOnOpen && !current.restoringZoom) publishZoom(current, state)
        }
        camera.cameraInfo.zoomState.observe(current.owner, current.zoomObserver!!)
        current.stateObserver = Observer { state ->
            if (binding === current) {
                val open = state.type == CameraXState.Type.OPEN
                if (current.open && !open) {
                    // A transient camera disconnect can also reset zoom without stopping the screen.
                    current.restoreZoomOnOpen = true
                    current.restoringZoom = false
                    current.zoomRequestId++
                    current.requestedNative = null
                }
                val opened = open && !current.open
                current.open = open
                if (!open) torchOff()
                // CameraX can wait for another camera client without attaching an error.
                // Keep retry/settings guidance visible while waiting; a later OPEN recovers automatically.
                current.failed = state.error != null || state.type == CameraXState.Type.PENDING_OPEN
                if (!current.open || current.failed) current.frames.clear()
                state.error?.let {
                    Log.w(TAG, "Camera state error ${it.code}", it.cause)
                    // A failing ultrawide or front camera gives the primary rear camera back, opened or not.
                    if (recoverPrimary(current) || recoverRear(current)) return@Observer
                }
                restoreZoomIfNeeded(current)
                restoreExposureIfNeeded(current)
                if (opened && torch) applyTorch(current)
                refreshStatus(current)
            }
        }
        camera.cameraInfo.cameraState.observe(current.owner, current.stateObserver!!)
    }

    override fun setZoom(ratio: Float, final: Boolean) {
        mainExecutor.execute {
            val current = binding ?: return@execute
            val requested = zoomPlan?.clamp(ratio) ?: return@execute
            if (!current.active || current.failed) return@execute
            extendTrace()
            markZoom("zoom.request", "$requested${if (final) " final" else ""}")
            // Keep the latest gesture delta while a lens opens or a photo finishes saving.
            pendingZoom = requested
            pendingFinal = final
            if (!drainScheduled) {
                drainScheduled = true
                Choreographer.getInstance().postFrameCallback(drainOnFrame)
            }
        }
    }

    private fun drainZoom(current: Binding) {
        if (binding !== current || _status.value != CameraStatus.READY || current.capturesInFlight > 0) return
        val requested = pendingZoom ?: return
        val plan = zoomPlan ?: return
        val lens = plan.lensFor(requested, current.lens, pendingFinal)
        if (lens != current.lens) {
            switchLens(current, lens)
        } else {
            pendingZoom = null
            applyZoom(current, requested)
        }
    }

    private fun switchLens(current: Binding, lens: ZoomLens? = null) {
        if (binding !== current) return
        extendTrace()
        markZoom("zoom.lens", "from=${current.lens}")
        // Invalidate old callbacks, but retain the combined range and the latest pinch target.
        releaseBinding(preserveZoomPlan = true)
        startBinding(current.owner, current.surfaceProvider, lens)
    }

    private fun restoreZoomIfNeeded(current: Binding) {
        if (!current.active || !current.open || current.failed || !current.restoreZoomOnOpen) return
        val state = current.camera?.cameraInfo?.zoomState?.value ?: return
        val range = current.range ?: return
        val plan = zoomPlan ?: return
        val requested = plan.clamp(pendingZoom ?: retainedZoom)!!
        current.restoreZoomOnOpen = false
        // A pinch still in progress keeps the lens it switched to; a settled zoom gets its own lens.
        val settled = pendingZoom == null || pendingFinal
        if (plan.lensFor(requested, current.lens, settled) != current.lens) {
            // A reversal or lifecycle stop can leave the new binding on the wrong lens. Queue
            // the correct one without overwriting retained framing with a temporary clamped ratio.
            pendingZoom = requested
            pendingFinal = settled
            return
        }
        pendingZoom = null
        val native = range.nativeZoom(requested)
        if (state.zoomRatio == native) {
            retainedZoom = range.appZoom(state.zoomRatio)
            current.requestedNative = native
            publishZoom(current, state)
            return
        }
        current.restoringZoom = true
        applyZoom(current, range.appZoom(native))
    }

    private fun applyZoom(current: Binding, requested: Float) {
        val camera = current.camera ?: return
        val range = current.range ?: return
        val restoring = current.restoringZoom
        val native = range.nativeZoom(requested)
        // The same native ratio again shows nothing new, but would still replace the repeating request: every step
        // inside the hold band asks the held primary for its minimum, and so does a pinch held at either end.
        if (!restoring && native == current.requestedNative) return
        current.requestedNative = native
        val requestId = ++current.zoomRequestId
        markZoom("zoom.apply", "$requested native=$native lens=${current.lens}")
        val future = try {
            camera.cameraControl.setZoomRatio(native)
        } catch (error: Exception) {
            current.restoringZoom = false
            current.requestedNative = null
            if (restoring) current.failed = true
            Log.w(TAG, "Zoom request could not start", error)
            if (restoring && recoverPrimary(current)) return
            refreshStatus(current)
            return
        }
        future.addListener({
            if (binding !== current || current.zoomRequestId != requestId) return@addListener
            var applied = false
            try {
                future.get()
                applied = true
                // Ratios remain relative to the primary camera across lens changes and lifecycle resets. Keep what
                // the lens shows: a held lens can show less than the pinch asked for (CameraZoomPlan.lensFor).
                retainedZoom = range.appZoom(native)
                if (restoring) current.frames.clear()
            } catch (_: CancellationException) {
                // A newer gesture or unbind supersedes the previous request.
                if (restoring) current.failed = true
            } catch (error: Exception) {
                if (restoring) current.failed = true
                Log.w(TAG, "Zoom request was not applied", error)
            }
            // A request that did not land may be asked for again.
            if (!applied) current.requestedNative = null
            current.restoringZoom = false
            if (current.failed && recoverPrimary(current)) return@addListener
            // Never echo a requested value: normalize only CameraX's observed zoom.
            camera.cameraInfo.zoomState.value?.let { publishZoom(current, it) }
            refreshStatus(current)
        }, mainExecutor)
    }

    override suspend fun takePhoto(): Result<Uri> = withContext(Dispatchers.Main.immediate) {
        val current = binding
        val capture = current?.capture
        if (_status.value != CameraStatus.READY || capture == null) {
            return@withContext Result.failure(IllegalStateException("The camera is not ready"))
        }
        suspendCancellableCoroutine { continuation ->
            current.capturesInFlight++
            var finished = false
            fun finishCapture() {
                if (finished) return
                finished = true
                current.capturesInFlight--
                drainZoom(current)
                rebindIfChanged(current)
            }
            try {
                extendTrace()
                Timings.mark("camera.capture.start")
                capture.takePicture(
                    // As the mirrored preview showed it, so the photo matches the guide placed on it (FR-1.14).
                    photoSaver.outputOptions(mirrored = current.facing == CameraFacing.FRONT),
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
                            finishCapture()
                            if (continuation.isActive) continuation.resume(result)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Timings.mark("camera.capture.failed", "code=${exception.imageCaptureError}")
                            finishCapture()
                            if (continuation.isActive) continuation.resume(Result.failure(exception))
                        }
                    },
                )
            } catch (error: Exception) {
                Timings.mark("camera.capture.failed", error.javaClass.simpleName)
                finishCapture()
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

    // --- focus, exposure, flash, and aspect ratio (Iter2-P10) ---------------------------------------------------

    override fun setFocusPoint(x: Float, y: Float) {
        mainExecutor.execute {
            val current = binding ?: return@execute
            val camera = current.camera ?: return@execute
            if (_status.value != CameraStatus.READY) return@execute
            // The preview's buffer, its viewport crop, and its turn to upright locate the frame on the sensor. The
            // front camera's preview is mirrored, so the point tapped is on the other side of what the camera sees.
            val info = current.preview?.resolutionInfo ?: return@execute
            val crop = info.cropRect
            val seenX = if (current.facing == CameraFacing.FRONT) 1f - x else x
            val point = frameToBuffer(seenX, y, info.rotationDegrees, crop.left, crop.top, crop.width(), crop.height())
                ?: return@execute
            val factory = SurfaceOrientedMeteringPointFactory(
                info.resolution.width.toFloat(),
                info.resolution.height.toFloat(),
            )
            // Focus, exposure, and white balance at that point; the camera returns to continuous focus after 5 s.
            val action = FocusMeteringAction.Builder(factory.createPoint(point.x, point.y)).build()
            try {
                // A newer tap cancels this one, so its result is not needed.
                camera.cameraControl.startFocusAndMetering(action)
                Timings.mark("camera.focus")
            } catch (error: Exception) {
                Log.w(TAG, "Focus request could not start", error)
            }
        }
    }

    override fun setExposure(index: Int) {
        mainExecutor.execute {
            val range = controls?.exposureRange ?: return@execute
            retainedExposure = clampedExposure(index, range) ?: return@execute
            val current = binding ?: return@execute
            // A camera that is not open yet gets the index when it opens.
            if (!current.open || current.failed) return@execute
            current.exposureRestored = true
            applyExposure(current, retainedExposure)
        }
    }

    override fun setFlash(mode: FlashMode) {
        mainExecutor.execute {
            // The capabilities report no flash while a lens without one is bound.
            if (controls?.hasFlash != true || !lensHasFlash) return@execute
            flash = mode
            binding?.capture?.flashMode = mode.captureMode
            _state.update { it.copy(flash = appliedFlash()) }
        }
    }

    /** The flash mode photos are taken with: the chosen one, or off on a lens without a flash. */
    private fun appliedFlash(): FlashMode = if (lensHasFlash) flash else FlashMode.OFF

    override fun setTorch(on: Boolean) {
        mainExecutor.execute {
            // Asked again when the camera did not apply the last request, so a light left on can still be turned off.
            if (on == torch && on == _state.value.torch) return@execute
            torch = on
            binding?.let(::applyTorch)
        }
    }

    /**
     * The kept light on an open camera with a flash; a closed one gets it when it opens again. The state says it is on
     * only once CameraX has applied it, so the scene photo is never taken before the light is on. A request that fails
     * on an open camera leaves the state as it was, and turning the light off is asked once more while [retry].
     */
    private fun applyTorch(current: Binding, retry: Boolean = true) {
        val camera = current.camera ?: return
        if (!current.open || current.failed || !camera.cameraInfo.hasFlashUnit()) return
        val on = torch
        val request = ++torchRequest
        try {
            val applied = camera.cameraControl.enableTorch(on)
            applied.addListener({
                // A request dropped because the camera closed is answered by torchOff.
                if (binding !== current || request != torchRequest || !current.open) return@addListener
                when {
                    runCatching { applied.get() }.isSuccess -> _state.update { it.copy(torch = on) }
                    !on && retry -> applyTorch(current, retry = false)
                    else -> Log.w(TAG, "The camera did not turn the light ${if (on) "on" else "off"}")
                }
            }, mainExecutor)
        } catch (error: Exception) {
            Log.w(TAG, "Torch request could not start", error)
        }
    }

    /**
     * The camera closed, or the binding went: CameraX has turned the light off, and an answer still on its way is from
     * before, so it must not say the light is on after the camera opens again.
     */
    private fun torchOff() {
        torchRequest++
        _state.update { it.copy(torch = false) }
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        mainExecutor.execute {
            if (ratio == aspect) return@execute
            aspect = ratio
            _state.update { it.copy(aspect = ratio) }
            binding?.let(::rebindIfChanged)
        }
    }

    override fun setFacing(facing: CameraFacing) {
        mainExecutor.execute {
            if (facing == chosenFacing) return@execute
            if (facing == CameraFacing.FRONT && !_hasFrontCamera.value) return@execute
            chooseFacing(facing)
            binding?.let(::rebindIfChanged)
        }
    }

    /** The other camera has its own zoom range, exposure steps, and flash: they are read again, from 1x and 0 EV. */
    private fun chooseFacing(facing: CameraFacing) {
        chosenFacing = facing
        _facing.value = facing
        retainedZoom = 1f
        pendingZoom = null
        pendingFinal = true
        retainedExposure = 0
        _capabilities.value = null
        _zoom.value = 1f
        _state.update { it.copy(zoom = 1f, exposure = 0) }
    }

    /**
     * Rebinds when the binding's camera or its viewport's ratio differs from the chosen one, once no photo is being
     * saved. The zoom and the latest pinch target carry over, as for a lens switch; another camera reads its own
     * lenses and controls first (startBinding).
     */
    private fun rebindIfChanged(current: Binding) {
        if (binding !== current || current.capturesInFlight > 0) return
        val facingChanged = current.facing != chosenFacing
        // A binding still waiting for the camera provider builds its viewport with the chosen ratio anyway.
        val aspectChanged = current.camera != null && current.aspect != aspect
        if (!facingChanged && !aspectChanged) return
        if (facingChanged) {
            Timings.mark("camera.facing", chosenFacing.name)
        } else {
            Timings.mark("camera.aspect", aspect.name)
        }
        releaseBinding(preserveZoomPlan = true)
        startBinding(current.owner, current.surfaceProvider)
    }

    /**
     * Applies the retained exposure index once each time the camera opens, because CameraX opens every camera at
     * 0 EV. The camera stays STARTING until it is applied, so no photo is taken at the reset value.
     */
    private fun restoreExposureIfNeeded(current: Binding) {
        if (!current.open || current.failed) {
            current.exposureRestored = false
            current.restoringExposure = false
            return
        }
        if (current.exposureRestored) return
        current.exposureRestored = true
        if (retainedExposure == 0) return
        current.restoringExposure = true
        applyExposure(current, retainedExposure)
    }

    private fun applyExposure(current: Binding, index: Int) {
        val camera = current.camera
        val exposure = camera?.cameraInfo?.exposureState
        if (camera == null || exposure == null || !exposure.isExposureCompensationSupported) {
            finishExposureRestore(current)
            return
        }
        // The bound lens may be an ultrawide with a narrower range than the primary camera's.
        val bounded = index.coerceIn(exposure.exposureCompensationRange.lower, exposure.exposureCompensationRange.upper)
        val future = try {
            camera.cameraControl.setExposureCompensationIndex(bounded)
        } catch (error: Exception) {
            Log.w(TAG, "Exposure request could not start", error)
            finishExposureRestore(current)
            return
        }
        future.addListener({
            if (binding !== current) return@addListener
            try {
                future.get()
            } catch (error: Exception) {
                // A newer index cancels this one; either way, publish what the camera holds now.
                Log.d(TAG, "Exposure request ended without applying", error)
            }
            _state.update { it.copy(exposure = camera.cameraInfo.exposureState.exposureCompensationIndex) }
            finishExposureRestore(current)
        }, mainExecutor)
    }

    private fun finishExposureRestore(current: Binding) {
        if (!current.restoringExposure) return
        current.restoringExposure = false
        refreshStatus(current)
    }

    private fun discoverControls(cameraProvider: ProcessCameraProvider, facing: CameraFacing) {
        val info = facing.selector.filter(cameraProvider.availableCameraInfos).firstOrNull() ?: return
        val exposure = info.exposureState
        val supported = exposure.isExposureCompensationSupported
        val center = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        controls = Controls(
            exposureRange = if (supported) {
                exposure.exposureCompensationRange.lower..exposure.exposureCompensationRange.upper
            } else {
                0..0
            },
            exposureStep = if (supported) exposure.exposureCompensationStep.toFloat() else 0f,
            hasFlash = info.hasFlashUnit(),
            supportsFocusPoint = info.isFocusMeteringSupported(FocusMeteringAction.Builder(center).build()),
        )
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
            if (binding === current && _status.value == CameraStatus.READY) {
                frameSink?.onFrame(frame, mirrored = current.facing == CameraFacing.FRONT)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not process camera frame", error)
        } finally {
            frame.close()
        }
    }

    private fun publishCapabilities() {
        val plan = zoomPlan ?: return
        val controls = controls
        _capabilities.value = CameraCapabilities(
            minZoom = plan.minZoom,
            maxZoom = plan.maxZoom,
            zoomStops = cameraZoomStops(plan.minZoom, plan.maxZoom),
            exposureRange = controls?.exposureRange ?: 0..0,
            exposureStep = controls?.exposureStep ?: 0f,
            // Not on a separately exposed ultrawide without a flash of its own.
            hasFlash = controls?.hasFlash == true && lensHasFlash,
            // A viewport crops any ratio out of the sensor's 4:3, so every phone can bind all three.
            aspectRatios = AspectRatio.entries,
            supportsFocusPoint = controls?.supportsFocusPoint ?: false,
        )
    }

    private fun publishZoom(current: Binding, state: ZoomState) {
        _zoom.value = current.range?.appZoom(state.zoomRatio) ?: return
        _state.update { it.copy(zoom = _zoom.value) }
    }

    private fun refreshStatus(current: Binding) {
        if (binding !== current) return
        publishStatus(
            when {
                !current.owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> CameraStatus.IDLE
                current.failed -> CameraStatus.UNAVAILABLE
                current.restoringZoom || current.restoreZoomOnOpen || current.restoringExposure -> CameraStatus.STARTING
                current.open && current.frames.hasFrame() -> CameraStatus.READY
                else -> CameraStatus.STARTING
            },
        )
        drainZoom(current)
    }

    private fun publishStatus(next: CameraStatus) {
        if (_status.value != next) Timings.mark("camera.status", next.name)
        _status.value = next
    }

    // The zoom measurement runs in debug builds only (Testing Documentation 2.1.4): a release build logs no frames and
    // no mark per touch event.
    private fun extendTrace() {
        if (BuildConfig.DEBUG) traceUntil = SystemClock.elapsedRealtime() + TRACE_MS
    }

    private fun markZoom(name: String, detail: String) {
        if (BuildConfig.DEBUG) Timings.mark(name, detail)
    }

    /** Logs each completed frame's sensor time and applied zoom while [traceUntil] lasts (NFR-19); debug builds only. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun Preview.Builder.traceFrames(lens: ZoomLens): Preview.Builder = apply {
        if (!BuildConfig.DEBUG) return@apply
        Camera2Interop.Extender(this).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    if (SystemClock.elapsedRealtime() > traceUntil) return
                    val sensor = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                    val zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        result.get(CaptureResult.CONTROL_ZOOM_RATIO)
                    } else {
                        null
                    }
                    val crop = result.get(CaptureResult.SCALER_CROP_REGION)?.width()
                    Timings.mark("zoom.frame", "us=${sensor / 1_000} z=$zoom crop=$crop lens=$lens")
                }
            },
        )
    }

    private fun failBinding(current: Binding, error: Exception) {
        if (binding !== current) return
        Log.e(TAG, "Could not bind the ${current.facing.name.lowercase()} camera", error)
        if (recoverPrimary(current) || recoverRear(current)) return
        releaseBinding()
        publishStatus(CameraStatus.UNAVAILABLE)
    }

    /**
     * A front camera that cannot be bound, or that fails once open, gives the rear one back rather than no camera at
     * all (FR-1.14). The rear camera that fails too stays UNAVAILABLE, with no switch back.
     */
    private fun recoverRear(current: Binding): Boolean {
        if (binding !== current || current.facing != CameraFacing.FRONT) return false
        Log.w(TAG, "Front camera unavailable; restoring the rear camera")
        chooseFacing(CameraFacing.BACK)
        rebindIfChanged(current)
        return true
    }

    private fun recoverPrimary(current: Binding): Boolean {
        val plan = zoomPlan ?: return false
        if (binding !== current || current.lens != ZoomLens.ULTRAWIDE || plan.ultrawide == null) return false
        Log.w(TAG, "Ultrawide unavailable; restoring the primary camera")
        zoomPlan = CameraZoomPlan(plan.primary)
        ultrawideSelector = null
        pendingZoom = zoomPlan!!.clamp(pendingZoom ?: retainedZoom)
        publishCapabilities()
        switchLens(current)
        return true
    }

    /** Main thread only. Unbind just our three use cases; do not reset the process-wide provider. */
    private fun releaseBinding(preserveZoomPlan: Boolean = false) {
        val current = binding ?: return
        binding = null
        current.active = false
        torchOff()
        publishStatus(if (preserveZoomPlan) CameraStatus.STARTING else CameraStatus.IDLE)
        if (!preserveZoomPlan) {
            _capabilities.value = null
            zoomPlan = null
            ultrawideSelector = null
            pendingZoom = null
            pendingFinal = true
        }
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

    private class Binding(
        val owner: LifecycleOwner,
        var surfaceProvider: Preview.SurfaceProvider,
        /** The camera this binding opens, fixed for its life: choosing another replaces the binding. */
        val facing: CameraFacing,
    ) {
        val frames = CameraFrameCache(mirrored = facing == CameraFacing.FRONT)

        @Volatile
        var active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        var preview: Preview? = null
        var capture: ImageCapture? = null
        var analysis: ImageAnalysis? = null
        var executor: ExecutorService? = null
        var camera: Camera? = null
        var lens = ZoomLens.PRIMARY
        var range: LensZoomRange? = null
        var capturesInFlight = 0
        var lifecycleObserver: LifecycleEventObserver? = null
        var zoomObserver: Observer<ZoomState>? = null
        var stateObserver: Observer<CameraXState>? = null
        var open = false
        var failed = false
        var restoreZoomOnOpen = true
        var restoringZoom = false
        var zoomRequestId = 0L

        // The native ratio last asked of this binding's camera, or null once the camera may have reset it.
        var requestedNative: Float? = null

        // The ratio this binding's viewport was built with.
        var aspect = AspectRatio.R4_3

        // Whether the retained exposure index was applied since the camera last opened, and whether that is pending.
        var exposureRestored = false
        var restoringExposure = false
    }

    /** The chosen camera's controls besides zoom (Design 2.3). */
    private class Controls(
        val exposureRange: IntRange,
        val exposureStep: Float,
        val hasFlash: Boolean,
        val supportsFocusPoint: Boolean,
    )

    /** The sink owns its call's frame, but an omitted close or thrown exception cannot stall CameraX. */
    private class CloseOnceImageProxy(private val delegate: ImageProxy) : ImageProxy by delegate {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) delegate.close()
        }
    }

    private companion object {
        const val TAG = "PixCamera"

        // How long frames are logged after the last zoom request, lens switch, or shutter press.
        const val TRACE_MS = 1_500L

        /**
         * The analysis buffer, which the viewport then crops: 960×720 for 4:3 and 720×720 for 1:1, and 1280×960
         * for 16:9 so its crop is 1280×720 (Design 2.6.4).
         */
        fun analysisSize(ratio: AspectRatio): Size =
            if (ratio == AspectRatio.R16_9) Size(1280, 960) else Size(960, 720)

        val CameraFacing.selector: CameraSelector
            get() = when (this) {
                CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            }

        val FlashMode.captureMode: Int
            get() = when (this) {
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            }
    }
}
