// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.nextZoomStep
import com.lastpenguin.pix.camera.quickZoomSteps
import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CaptureNotice { SAVED, SAVE_FAILED, STORAGE_FAILED, CAMERA_UNAVAILABLE, PERMISSION_REQUIRED }

/** What *Save guide* did (FR-3.8), shown as a short notice. */
enum class GuideSaveNotice {
    SAVED,

    /** Saved, and the oldest saved guide was deleted to keep [SavedGuideStore.MAX_SAVED]. */
    SAVED_OLDEST_REMOVED,
    ALREADY_SAVED,
    FAILED,
}

/**
 * The pose tip of the guide on the camera (FR-4.10), as the screen shows it: a banner over the bottom of the preview
 * that opens once when the guide comes, and the tip button in the top bar.
 */
data class TipBanner(
    /** The guide the tip is for; a new guide opens the banner again. */
    val guideId: String,
    val state: TipState,
    /** The banner is open; a touch anywhere closes it, and the tip button opens it again. */
    val open: Boolean,
    /** The tip arrived while the banner was closed: the tip button shows a dot until it is opened. */
    val unseen: Boolean = false,
)

/**
 * The tip after a change of the guide on the camera ([guideId]) or of its tip ([state]): a new guide's tip opens,
 * and a tip that arrives while closed is marked unseen; none without a guide or a tip.
 */
internal fun nextTip(previous: TipBanner?, guideId: String?, state: TipState?): TipBanner? {
    if (guideId == null || state == null) return null
    if (previous == null || previous.guideId != guideId) return TipBanner(guideId, state, open = true)
    val arrivedClosed = state is TipState.Ready && previous.state !is TipState.Ready && !previous.open
    return previous.copy(state = state, unseen = previous.unseen || arrivedClosed)
}

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
    /** The camera's zoom stops (`CameraCapabilities.zoomStops`); a tap on the readout steps through some of them. */
    val zoomStops: List<Float> = emptyList(),
    val zoom: Float = 1f,
    /** Applied exposure-compensation index (FR-1.11), within [exposureRange]; 0..0 hides the brightness control. */
    val exposure: Int = 0,
    val exposureRange: IntRange = 0..0,
    /** EV per exposure index, for the brightness label. */
    val exposureStep: Float = 0f,
    /** Whether the brightness slider is open above the controls. */
    val brightnessOpen: Boolean = false,
    /** False hides the flash button (FR-1.12). */
    val hasFlash: Boolean = false,
    val flash: FlashMode = FlashMode.OFF,
    /** The frame's shape (FR-1.13, AD-10): the preview, the grid, the guide, and the saved photo follow it. */
    val aspect: AspectRatio = AspectRatio.R4_3,
    val supportsFocusPoint: Boolean = false,
    val isSaving: Boolean = false,
    /** "Saved without the guide" for [CameraViewModel.NOTICE_MS], or why the last photo failed until the next one. */
    val captureNotice: CaptureNotice? = null,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
    /**
     * Guide-edit mode (AD-17): while on, every drag and pinch on the preview edits the guide; while off, a pinch
     * zooms the camera. On when a guide is added, off on the shutter or when the guide goes (Design 2.6.2).
     */
    val guideEditing: Boolean = false,
    /** The guide's reference is enlarged from its corner thumbnail (FR-3.9); every new guide starts small. */
    val referenceExpanded: Boolean = false,
    /** The preview corner the reference thumbnail sits in, kept from one guide to the next. */
    val referenceCorner: ReferenceCorner = ReferenceCorner.TOP_LEFT,
    /** The guide on the camera is one of the saved guides (FR-3.8), so *Save guide* shows it as saved. */
    val guideSaved: Boolean = false,
    /** *Save guide* is writing the guide; the button waits. */
    val savingGuide: Boolean = false,
    /** The last saved photo, for the thumbnail and "Saved without the guide". */
    val lastPhoto: Uri? = null,
    /** A short notice, such as "Junhyeong set zoom to 2×" (FR-7.4). */
    val notice: String? = null,
    /** The pose tip of the guide on the camera (FR-4.10), or null when it has none. */
    val tip: TipBanner? = null,
)

/**
 * Camera screen (Design 2.2, Figure 6).
 * Owner: Camera/Overlay (#3 camera, #6 guide).
 */
class CameraViewModel(
    private val camera: CameraController,
    private val guides: GuideRepository,
    private val settings: CameraSettings = CameraSettings.InMemory(),
    private val savedGuides: SavedGuideStore = SavedGuideStore.InMemory(),
    /** The tip of the guide on the camera (`PoseTips.onCamera`, FR-4.10). */
    private val tips: StateFlow<GuideTip?> = MutableStateFlow(null),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
    private var savedNoticeJob: Job? = null
    private var failedTipJob: Job? = null

    private val _guideSaveNotices = Channel<GuideSaveNotice>(Channel.BUFFERED)

    /** One notice per *Save guide* tap, shown like the other short notices; kept until the screen reads it. */
    val guideSaveNotices: Flow<GuideSaveNotice> = _guideSaveNotices.receiveAsFlow()

    init {
        // The last ratio chosen comes back when the screen opens (Design 2.8); before the camera binds, if it can.
        viewModelScope.launch { settings.aspectRatio()?.let(camera::setAspectRatio) }
        viewModelScope.launch {
            camera.status.collect { status -> _uiState.update { it.copy(cameraStatus = status) } }
        }
        viewModelScope.launch {
            camera.capabilities.collect { capabilities ->
                _uiState.update {
                    val exposureRange = capabilities?.exposureRange ?: 0..0
                    it.copy(
                        minZoom = capabilities?.minZoom ?: 1f,
                        maxZoom = capabilities?.maxZoom ?: 1f,
                        zoomStops = capabilities?.zoomStops.orEmpty(),
                        exposureRange = exposureRange,
                        exposureStep = capabilities?.exposureStep ?: 0f,
                        brightnessOpen = it.brightnessOpen && exposureRange.first < exposureRange.last,
                        hasFlash = capabilities?.hasFlash ?: false,
                        supportsFocusPoint = capabilities?.supportsFocusPoint ?: false,
                    )
                }
            }
        }
        viewModelScope.launch {
            camera.state.collect { state ->
                _uiState.update { it.copy(exposure = state.exposure, flash = state.flash, aspect = state.aspect) }
            }
        }
        viewModelScope.launch {
            // A new frame keeps the guide's center and height and clamps them again (Design 2.6.2); the repository
            // reads the new frame, and the final change reaches a connected subject.
            camera.state.map { it.aspect }.distinctUntilChanged().drop(1).collect {
                guides.update(final = true) { state -> state }
            }
        }
        viewModelScope.launch {
            camera.zoom.collect { zoom -> _uiState.update { it.copy(zoom = zoom) } }
        }
        viewModelScope.launch {
            guides.guide.collect { guide ->
                _uiState.update {
                    // A new guide starts in edit mode so that it can be placed at once; no guide, no mode.
                    val editing = guide != null && (guide !== it.guide || it.guideEditing)
                    val expanded = it.referenceExpanded && guide === it.guide
                    it.copy(guide = guide, guideEditing = editing, referenceExpanded = expanded)
                }
            }
        }
        viewModelScope.launch {
            guides.state.collect { state ->
                _uiState.update { it.copy(guideState = state, guideEditing = it.guideEditing && state.visible) }
            }
        }
        viewModelScope.launch {
            // A guide keeps its id when it is saved and loaded again, so the id tells whether it is saved.
            combine(guides.guide, savedGuides.saved) { guide, saved ->
                guide != null && saved.any { it.id == guide.id }
            }
                .collect { saved -> _uiState.update { it.copy(guideSaved = saved) } }
        }
        viewModelScope.launch {
            // A tip is shown only with its own guide: the guide changes here before the tips follow it, and a tip
            // still named for the guide before has none.
            combine(guides.guide.map { it?.id }.distinctUntilChanged(), tips) { id, tip ->
                id to tip?.takeIf { it.guideId == id }?.state
            }
                .collect { (id, tip) ->
                    _uiState.update { it.copy(tip = nextTip(it.tip, id, tip)) }
                    if (tip == TipState.Failed) closeFailedTip()
                }
        }
    }

    /** A touch anywhere on the screen ended: an open tip closes (FR-4.10). */
    fun onAnyTouch() {
        _uiState.update { state ->
            state.tip?.takeIf { it.open }?.let { state.copy(tip = it.copy(open = false)) }
                ?: state
        }
    }

    /** The tip button: the tip opens again, and is no longer unseen. A tip that could not be had has no button. */
    fun onTipButton() {
        _uiState.update { state ->
            val tip = state.tip?.takeIf { it.state != TipState.Failed } ?: return@update state
            state.copy(tip = tip.copy(open = true, unseen = false))
        }
    }

    /** "Couldn't get a tip" stays for [NOTICE_MS] if the banner is open, then the banner closes for good. */
    private fun closeFailedTip() {
        failedTipJob?.cancel()
        failedTipJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _uiState.update { state ->
                val tip = state.tip?.takeIf { it.state == TipState.Failed } ?: return@update state
                state.copy(tip = tip.copy(open = false))
            }
        }
    }

    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        camera.bind(owner, surfaceProvider)
    }

    fun onShutter() {
        if (_uiState.value.isSaving || camera.status.value != CameraStatus.READY) return
        savedNoticeJob?.cancel()
        // Set before launching so two taps in the same frame cannot enqueue two captures. The shutter also ends
        // guide editing (Design 2.6.2): the guide is placed, and the next pinch zooms again.
        _uiState.update { it.copy(isSaving = true, captureNotice = null, guideEditing = false) }
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

    /** [final] is false for the steps of a pinch, so the camera holds its lens until the pinch ends (NFR-19). */
    fun onZoomChanged(ratio: Float, final: Boolean = true) {
        val status = camera.status.value
        if ((status == CameraStatus.READY || status == CameraStatus.STARTING) &&
            camera.capabilities.value != null && ratio.isFinite()
        ) {
            camera.setZoom(ratio, final)
        }
    }

    /** A tap on the zoom readout (FR-1.3): the next quick step, such as 0.6×, 1×, and 2×, the last back to the first. */
    fun onZoomTap() {
        val state = _uiState.value
        nextZoomStep(quickZoomSteps(state.zoomStops), state.zoom)?.let { onZoomChanged(it) }
    }

    /** A tap on the preview at [x], [y] of the frame, 0..1 (FR-1.4); the camera focuses and meters there. */
    fun onFocusTap(x: Float, y: Float) {
        val state = _uiState.value
        if (state.cameraStatus != CameraStatus.READY || !state.supportsFocusPoint) return
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return
        camera.setFocusPoint(x, y)
    }

    /** From the brightness slider: each step is applied as it comes, like a pinch zoom (FR-1.11). */
    fun onExposureChange(index: Int) {
        val range = _uiState.value.exposureRange
        if (range.first >= range.last) return
        camera.setExposure(index.coerceIn(range))
    }

    fun setBrightnessOpen(open: Boolean) {
        _uiState.update { it.copy(brightnessOpen = open && it.exposureRange.first < it.exposureRange.last) }
    }

    /** The flash button cycles off, auto, on (FR-1.12). */
    fun onFlashTap() {
        val state = _uiState.value
        if (!state.hasFlash) return
        camera.setFlash(
            when (state.flash) {
                FlashMode.OFF -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.ON
                FlashMode.ON -> FlashMode.OFF
            },
        )
    }

    /** The ratio button cycles 4:3, 16:9, 1:1 (FR-1.13), and the choice is kept for the next time. */
    fun onAspectTap() {
        val state = _uiState.value
        if (state.isSaving) return
        val next = when (state.aspect) {
            AspectRatio.R4_3 -> AspectRatio.R16_9
            AspectRatio.R16_9 -> AspectRatio.R1_1
            AspectRatio.R1_1 -> AspectRatio.R4_3
        }
        camera.setAspectRatio(next)
        viewModelScope.launch { settings.saveAspectRatio(next) }
    }

    /** The guide-edit mode toggle; there is no mode without a visible guide. */
    fun setGuideEditing(on: Boolean) {
        _uiState.update { it.copy(guideEditing = on && it.guide != null && it.guideState.visible) }
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

    /**
     * *Hide* / *Show* (FR-3.6): the guide goes away for a moment and comes back where it was. Hiding ends the
     * guide-edit mode (Design 2.6.2); a connected subject's copy follows through `guide.state` (FR-6.7).
     */
    fun onHideToggle() {
        if (_uiState.value.guide == null) return
        guides.update(final = true) { it.copy(visible = !it.visible) }
    }

    /** A tap on the reference thumbnail enlarges it, and another tap makes it small again (FR-3.9). */
    fun setReferenceExpanded(expanded: Boolean) {
        _uiState.update { it.copy(referenceExpanded = expanded && it.guide?.reference != null) }
    }

    /** A drag drops the reference thumbnail into one of the preview's corners, and nowhere else (FR-3.9). */
    fun setReferenceCorner(corner: ReferenceCorner) {
        _uiState.update { it.copy(referenceCorner = corner) }
    }

    /**
     * *Save guide* (FR-3.8): keeps the guide on the camera, to use again from *Add a pose guide*. A saved guide is
     * not saved twice; when [SavedGuideStore.MAX_SAVED] are saved, the store deletes the oldest and the notice says so.
     */
    fun onSaveGuide() {
        val state = _uiState.value
        val guide = state.guide ?: return
        if (state.savingGuide) return
        // The store's own list, not only guideSaved, which follows it a moment later.
        val before = savedGuides.saved.value
        if (state.guideSaved || before.any { it.id == guide.id }) {
            _guideSaveNotices.trySend(GuideSaveNotice.ALREADY_SAVED)
            return
        }
        val full = before.size >= SavedGuideStore.MAX_SAVED
        _uiState.update { it.copy(savingGuide = true) }
        viewModelScope.launch {
            val notice = try {
                savedGuides.save(guide, guides.state.value).fold(
                    onSuccess = { if (full) GuideSaveNotice.SAVED_OLDEST_REMOVED else GuideSaveNotice.SAVED },
                    onFailure = { GuideSaveNotice.FAILED },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                GuideSaveNotice.FAILED
            } finally {
                _uiState.update { it.copy(savingGuide = false) }
            }
            _guideSaveNotices.send(notice)
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
