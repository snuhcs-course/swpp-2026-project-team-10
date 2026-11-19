// AI-generated with ChatGPT Codex and Claude Code, 2026-11-19, reviewed by Jaewan Park
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
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.camera.isAtZoomStep
import com.lastpenguin.pix.camera.nextZoomStep
import com.lastpenguin.pix.camera.quickZoomSteps
import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.PhotoShare
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last photo failed. A saved photo has no notice: the thumbnail shows it (FR-1.6, #204). */
enum class CaptureNotice { SAVE_FAILED, STORAGE_FAILED, CAMERA_UNAVAILABLE, PERMISSION_REQUIRED }

/** What *Save guide* did (FR-3.8), shown as a short notice. */
enum class GuideSaveNotice {
    SAVED,

    /** Saved, and the oldest saved guide was deleted to keep [SavedGuideStore.MAX_SAVED]. */
    SAVED_OLDEST_REMOVED,

    /** A second tap on a saved guide's bookmark took it out of the saved guides again. */
    REMOVED,
    FAILED,
}

/**
 * What a toggle on the camera screens just did, shown as a short label at the top of the image (Design 2.8): showing or
 * hiding the guide, and a zoom step.
 */
sealed interface ToggleLabel {
    data object GuideHidden : ToggleLabel

    data object GuideShown : ToggleLabel

    data class Zoom(val ratio: Float) : ToggleLabel
}

/**
 * The pose tip of the guide on the camera (FR-4.10), as the screen shows it: a banner over the bottom of the preview
 * that opens once when the guide comes, and the tip button in the zoom row.
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

/**
 * *Save guide* as a toggle (FR-3.8, FR-6.16): saves [guide] with [state], or takes it out of [store] when it is saved
 * already, by its id, which a guide keeps through save and load.
 */
internal suspend fun toggleSaved(store: SavedGuideStore, guide: ReferenceGuide, state: GuideState): GuideSaveNotice =
    try {
        if (store.saved.value.any { it.id == guide.id }) {
            store.delete(guide.id)
            GuideSaveNotice.REMOVED
        } else {
            store.save(guide, state).fold(
                onSuccess = { if (it.removedOldest) GuideSaveNotice.SAVED_OLDEST_REMOVED else GuideSaveNotice.SAVED },
                onFailure = { GuideSaveNotice.FAILED },
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        GuideSaveNotice.FAILED
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
    val zoom: Float = 1f,
    /** Applied exposure-compensation index (FR-1.11), within [exposureRange]; 0..0 hides the brightness control. */
    val exposure: Int = 0,
    val exposureRange: IntRange = 0..0,
    /** EV per exposure index, for the brightness label. */
    val exposureStep: Float = 0f,
    /** Whether the brightness slider is open above the controls. */
    val brightnessOpen: Boolean = false,
    /** Whether the ratios are open under the top bar (FR-1.13). */
    val aspectsOpen: Boolean = false,
    /** False hides the flash button (FR-1.12). */
    val hasFlash: Boolean = false,
    val flash: FlashMode = FlashMode.OFF,
    /** The phone's light for the scene photo is on, as the camera applied it (FR-4.1). */
    val torch: Boolean = false,
    /** The frame's shape (FR-1.13, AD-10): the preview, the grid, the guide, and the saved photo follow it. */
    val aspect: AspectRatio = AspectRatio.R4_3,
    /** The camera in use (FR-1.14); the front one is selfie mode. */
    val facing: CameraFacing = CameraFacing.BACK,
    /** False hides the camera switch: the phone has no front camera, or the cameras are not known yet. */
    val canSwitchCamera: Boolean = false,
    val supportsFocusPoint: Boolean = false,
    /** From the shutter until the camera has taken the photo: the shutter waits, so one tap is one photo (NFR-19). */
    val shutterBusy: Boolean = false,
    /** A photo is being saved, or several: the camera switch, the ratio, and the ways off Camera wait for them. */
    val isSaving: Boolean = false,
    /** Why the last photo failed, until the next one. */
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
    /**
     * The reference thumbnail is put away at its corner's side of the preview, with a strip of it left to pull it
     * back by (FR-3.9, #237). On this phone only; another guide's thumbnail shows again. A hidden thumbnail is never
     * [referenceExpanded]: hiding makes it small, so its strip is the small one's and it comes back small.
     */
    val referenceHidden: Boolean = false,
    /** The guide on the camera is one of the saved guides (FR-3.8), so *Save guide* shows it as saved. */
    val guideSaved: Boolean = false,
    /** *Save guide* is writing the guide; the button waits. */
    val savingGuide: Boolean = false,
    /**
     * The thumbnail's photo (FR-1.6): in a session, the session's newest, none until its first; otherwise the newest in
     * Pix's album, taken here or received.
     */
    val lastPhoto: Uri? = null,
    /** The photos of the session running now, oldest first, for the viewer (FR-1.6); null outside a session. */
    val sessionPhotos: List<Uri>? = null,
    /** The photos in Pix's album, oldest first, with one just saved here that MediaStore has not told of yet. */
    val albumPhotos: List<Uri> = emptyList(),
    /** The viewer of [viewerPhotos] is open over the camera (FR-1.6). */
    val photosOpen: Boolean = false,
    /** A short notice, such as "Joonhyung set zoom to 2×" (FR-7.4). */
    val notice: String? = null,
    /** The pose tip of the guide on the camera (FR-4.10), or null when it has none. */
    val tip: TipBanner? = null,
) {
    /** What the viewer pages through: the session's photos in a session, Pix's album otherwise. */
    val viewerPhotos: List<Uri>
        get() = sessionPhotos ?: albumPhotos
}

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
    /** Each saved photo, which in a session goes to the subject's phone too (FR-6.14). */
    private val photos: PhotoShare = PhotoShare.None,
    /** The photos in Pix's album (`PixAlbumPhotos`, FR-1.6), oldest first, taken here or received in a session. */
    private val albumPhotos: StateFlow<List<Uri>> = MutableStateFlow(emptyList()),
    /** The photos of the session running now (`PhotoSyncer.sessionPhotos`, FR-1.6); null outside a session. */
    private val sessionPhotos: Flow<List<Uri>?> = flowOf(null),
    /** Deletes a photo from the viewer, from the album and the session's photos (`AppContainer.photoDeleter`). */
    private val deleter: PhotoDeleter = PhotoDeleter { false },
    /** The base brightness in EV from the settings (FR-1.11): each tap to focus starts from it again. */
    private val baseBrightness: StateFlow<Float> = MutableStateFlow(0f),
    private val clock: () -> Long = System::currentTimeMillis,
    /** How the phone is held: each photo is saved as it was at its shutter press (FR-1.5). */
    private val phoneTurns: PhoneTurns = PhoneTurns.Fixed(),
) : ViewModel() {

    /** Follows how the phone is held while [owner], the camera screen, is in front (FR-1.5, FR-1.15). */
    fun watchPhoneTurn(owner: LifecycleOwner) = phoneTurns.watch(owner)

    /** How the phone is held, which the controls' icons turn with (FR-1.15). */
    val phoneTurn: StateFlow<Int> = phoneTurns.turn

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    // The quick step a tap asked for until the camera applies it, so that another tap goes on from it (FR-1.3); the
    // readout still shows the applied zoom.
    private var pendingZoomStep: Float? = null
    private var failedTipJob: Job? = null

    private val _guideSaveNotices = Channel<GuideSaveNotice>(Channel.BUFFERED)

    /** One notice per *Save guide* tap, shown like the other short notices; kept until the screen reads it. */
    val guideSaveNotices: Flow<GuideSaveNotice> = _guideSaveNotices.receiveAsFlow()

    private val _toggleLabels = Channel<ToggleLabel>(Channel.CONFLATED)

    /** One label per toggle, the newest replacing one not yet shown; the screen shows it for [NOTICE_MS]. */
    val toggleLabels: Flow<ToggleLabel> = _toggleLabels.receiveAsFlow()

    private val _photoNotDeleted = Channel<Unit>(Channel.BUFFERED)

    /** One per photo the viewer could not delete, for its notice; kept until the screen reads it. */
    val photoNotDeleted: Flow<Unit> = _photoNotDeleted.receiveAsFlow()

    // The thumbnail's and the viewer's sources (FR-1.6): outside a session Pix's album, with the photo just saved here
    // before MediaStore tells of it; in a session the session's photos alone.
    private var album: List<Uri> = emptyList()
    private var newestSaved: Uri? = null
    private var thisSession: List<Uri>? = null

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
                _uiState.update {
                    it.copy(exposure = state.exposure, flash = state.flash, aspect = state.aspect, torch = state.torch)
                }
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
            camera.facing.collect { facing ->
                pendingZoomStep = null
                _uiState.update { it.copy(facing = facing) }
            }
        }
        viewModelScope.launch {
            // The base brightness, once the camera's range is known and whenever the setting or the camera changes;
            // what a drag added is gone with it (FR-1.11).
            camera.capabilities
                .map { it?.exposureRange to it?.exposureStep }
                .distinctUntilChanged()
                .combine(baseBrightness) { _, _ -> }
                .collect { applyBaseBrightness() }
        }
        viewModelScope.launch {
            camera.hasFrontCamera.collect { has -> _uiState.update { it.copy(canSwitchCamera = has) } }
        }
        viewModelScope.launch {
            albumPhotos.collect { photos ->
                album = photos
                newestSaved = photos.lastOrNull()
                showNewestPhoto()
            }
        }
        viewModelScope.launch {
            sessionPhotos.collect { photos ->
                thisSession = photos
                showNewestPhoto()
            }
        }
        viewModelScope.launch {
            camera.zoom.collect { zoom ->
                if (pendingZoomStep?.let { isAtZoomStep(zoom, it) } == true) pendingZoomStep = null
                _uiState.update { it.copy(zoom = zoom) }
            }
        }
        viewModelScope.launch {
            guides.guide.collect { guide ->
                _uiState.update {
                    // A new guide starts in edit mode so that it can be placed at once; no guide, no mode.
                    val editing = guide != null && (guide !== it.guide || it.guideEditing)
                    val expanded = it.referenceExpanded && guide === it.guide
                    // A saved copy keeps the guide's id and its photo, so it stays hidden; another guide's photo
                    // is one the user has not seen, so it shows.
                    val hidden = it.referenceHidden && guide != null && guide.id == it.guide?.id
                    it.copy(
                        guide = guide,
                        guideEditing = editing,
                        referenceExpanded = expanded,
                        referenceHidden = hidden,
                    )
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

    /**
     * In a session, the session's newest photo, none until its first, and the session's photos for the viewer;
     * otherwise the newest saved here, and Pix's album (FR-1.6).
     */
    private fun showNewestPhoto() {
        val session = thisSession
        val saved = newestSaved
        val albumPhotos = if (saved != null && saved !in album) album + saved else album
        _uiState.update {
            val next = it.copy(
                lastPhoto = if (session != null) session.lastOrNull() else saved,
                sessionPhotos = session,
                albumPhotos = albumPhotos,
            )
            // The viewer closes as a session starts or ends, so it never turns from one's photos to the other's.
            val sameKind = (it.sessionPhotos == null) == (session == null)
            next.copy(photosOpen = it.photosOpen && sameKind && next.viewerPhotos.isNotEmpty())
        }
    }

    /**
     * A tap on the thumbnail opens its photos over the camera, at the newest (FR-1.6): the session's in a session,
     * Pix's album otherwise, so Pix stays in front in both.
     */
    fun openPhotos() {
        _uiState.update { it.copy(photosOpen = it.viewerPhotos.isNotEmpty()) }
    }

    /** The viewer's Back, or the system's, closes it. */
    fun closePhotos() {
        _uiState.update { it.copy(photosOpen = false) }
    }

    /**
     * The viewer's Delete, once asked (FR-1.6): the photo leaves the viewer and the thumbnail at once, before
     * MediaStore tells of it, or stays, and [photoNotDeleted] says so. In a session the deleter drops it from the
     * session's photos too.
     */
    fun deletePhoto(uri: Uri) {
        viewModelScope.launch {
            if (!deleter.delete(uri)) {
                _photoNotDeleted.trySend(Unit)
                return@launch
            }
            album = album - uri
            if (newestSaved == uri) newestSaved = album.lastOrNull()
            showNewestPhoto()
        }
    }

    /** The ratios under the top bar open or close (FR-1.13). */
    fun setAspectsOpen(open: Boolean) {
        _uiState.update { it.copy(aspectsOpen = open) }
    }

    /**
     * The tip button: the tip opens again, and is no longer unseen; another tap while it is open closes it. A tip that
     * could not be had has no button.
     */
    fun onTipButton() {
        _uiState.update { state ->
            val tip = state.tip?.takeIf { it.state != TipState.Failed } ?: return@update state
            state.copy(tip = tip.copy(open = !tip.open, unseen = false))
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

    // Photos asked for and not yet saved or failed; touched on the main thread only.
    private var photosInFlight = 0

    // The newest press's work; each press publishes its result after the one before it.
    private var lastShot: Job? = null

    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        camera.bind(owner, surfaceProvider)
    }

    /**
     * Takes a photo, unless the camera is not ready or has not yet taken the photo of the press before; true when it
     * takes one. The shutter is free again as soon as the camera took the picture, while the photo is still being
     * saved (NFR-19), so photos can follow each other; [CameraUiState.isSaving] lasts until all of them are done. Their
     * results are published in shutter order.
     */
    fun onShutter(): Boolean {
        if (_uiState.value.shutterBusy || camera.status.value != CameraStatus.READY) return false
        // Set before launching so two taps in the same frame cannot enqueue two captures. The shutter also ends
        // guide editing (Design 2.6.2): the guide is placed, and the next pinch zooms again.
        photosInFlight++
        _uiState.update {
            it.copy(
                shutterBusy = true,
                isSaving = true,
                captureNotice = null,
                guideEditing = false,
                aspectsOpen = false,
            )
        }
        val takenAt = clock()
        // At the press, like the time: the phone may turn while an earlier photo is still being saved.
        val turn = phoneTurns.turn.value
        // Now, not once it is saved: the photo belongs to the session running at the shutter.
        val sessionId = photos.shutter()
        // The press before, for the order below.
        val earlier = lastShot
        lastShot = viewModelScope.launch {
            // This press's own hold on the shutter: the "taken" of an earlier photo, come late, must not free it.
            var held = true
            fun free() {
                if (!held) return
                held = false
                _uiState.update { it.copy(shutterBusy = false) }
            }
            try {
                val result = try {
                    camera.setPhotoTurn(turn)
                    camera.takePhoto(onCaptured = ::free)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
                // A camera that never said "taken", or a photo that failed before it.
                free()
                // In shutter order, whatever order the saves end in: the thumbnail is the newest photo, and the
                // session sends the photos as they were taken (PhotoSyncer).
                earlier?.join()
                result.fold(
                    onSuccess = { uri ->
                        // No notice (FR-1.6, #204): the thumbnail shows the new photo, in a session once it is the
                        // session's.
                        newestSaved = uri
                        showNewestPhoto()
                        sessionId?.let { photos.taken(it, uri, takenAt) }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        sessionId?.let(photos::notTaken)
                        _uiState.update { it.copy(captureNotice = captureFailureNotice(error)) }
                    },
                )
            } finally {
                free()
                photosInFlight--
                _uiState.update { it.copy(isSaving = photosInFlight > 0) }
            }
        }
        return true
    }

    /** [final] is false for the steps of a pinch, so the camera holds its lens until the pinch ends (NFR-19). */
    fun onZoomChanged(ratio: Float, final: Boolean = true) {
        pendingZoomStep = null
        requestZoom(ratio, final)
    }

    /**
     * A tap on the zoom readout (FR-1.3): the next quick step, such as 0.6×, 1×, and 2×, the last back to the first,
     * from the step an earlier tap asked for while the camera has not applied it yet.
     */
    fun onZoomTap() {
        val state = _uiState.value
        val step = nextZoomStep(quickZoomSteps(state.minZoom, state.maxZoom), pendingZoomStep ?: state.zoom) ?: return
        if (requestZoom(step, final = true)) {
            pendingZoomStep = step
            _toggleLabels.trySend(ToggleLabel.Zoom(step))
        }
    }

    /** Asks the camera for [ratio] while it can zoom; false when it cannot. */
    private fun requestZoom(ratio: Float, final: Boolean): Boolean {
        val status = camera.status.value
        if ((status != CameraStatus.READY && status != CameraStatus.STARTING) ||
            camera.capabilities.value == null || !ratio.isFinite()
        ) {
            return false
        }
        camera.setZoom(ratio, final)
        return true
    }

    /**
     * A tap on the preview at [x], [y] of the frame, 0..1 (FR-1.4): the camera focuses and meters there if it can, and
     * the brightness goes back to the base.
     */
    fun onFocusTap(x: Float, y: Float) {
        val state = _uiState.value
        if (state.cameraStatus != CameraStatus.READY) return
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return
        if (state.supportsFocusPoint) camera.setFocusPoint(x, y)
        // A new tap drops the brightness a drag added after the last one, back to the base (FR-1.11), also on a camera
        // that cannot focus on a point.
        applyBaseBrightness()
    }

    /** The exposure index of the base brightness for this camera, or null without exposure compensation. */
    fun baseExposureIndex(): Int? {
        val capabilities = camera.capabilities.value ?: return null
        val range = capabilities.exposureRange
        if (range.first >= range.last) return null
        return ExposureScale.indexOf(baseBrightness.value, capabilities.exposureStep, range)
    }

    private fun applyBaseBrightness() {
        baseExposureIndex()?.let(camera::setExposure)
    }

    /** From the brightness bar or a drag on the preview: each step is applied as it comes, like a pinch zoom (FR-1.11). */
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

    /**
     * The camera switch (FR-1.14): the front camera for a selfie, or the rear one again, with or without a session. The
     * choice lasts while the app runs; Pix opens with the rear camera.
     */
    fun onSwitchCamera() {
        val state = _uiState.value
        if (state.isSaving || !state.canSwitchCamera) return
        camera.setFacing(if (state.facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK)
    }

    /**
     * A ratio chosen under the top bar (FR-1.13): the ratios close, and a new one goes to the camera and is kept for the
     * next time. None while a photo is being saved, since a rebind waits for it.
     */
    fun onAspectChosen(aspect: AspectRatio) {
        val state = _uiState.value
        _uiState.update { it.copy(aspectsOpen = false) }
        if (state.isSaving || aspect == state.aspect) return
        camera.setAspectRatio(aspect)
        viewModelScope.launch { settings.saveAspectRatio(aspect) }
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
        _toggleLabels.trySend(if (guides.state.value.visible) ToggleLabel.GuideShown else ToggleLabel.GuideHidden)
    }

    /**
     * A tap on the reference thumbnail enlarges it, and another tap makes it small again (FR-3.9). A hidden one stays
     * small.
     */
    fun setReferenceExpanded(expanded: Boolean) {
        _uiState.update {
            it.copy(referenceExpanded = expanded && it.guide?.reference != null && !it.referenceHidden)
        }
    }

    /** A drag drops the reference thumbnail into one of the preview's corners, and nowhere else (FR-3.9). */
    fun setReferenceCorner(corner: ReferenceCorner) {
        _uiState.update { it.copy(referenceCorner = corner) }
    }

    /**
     * Puts the reference thumbnail away at its corner's side, small, or shows it again (#237); nothing without one.
     */
    fun setReferenceHidden(hidden: Boolean) {
        _uiState.update {
            val hides = hidden && it.guide?.reference != null
            it.copy(referenceHidden = hides, referenceExpanded = it.referenceExpanded && !hides)
        }
    }

    /** A drag let the thumbnail go: into [drop]'s corner, or hidden, and small, at that corner's side (#237). */
    fun onReferenceDropped(drop: ReferenceDrop) {
        _uiState.update {
            val hides = drop.hidden && it.guide?.reference != null
            it.copy(
                referenceCorner = drop.corner,
                referenceHidden = hides,
                referenceExpanded = it.referenceExpanded && !hides,
            )
        }
    }

    /** A tap on the thumbnail: a hidden one comes back, small, and a shown one changes its size (FR-3.9). */
    fun onReferenceTap() {
        if (_uiState.value.referenceHidden) {
            setReferenceHidden(false)
        } else {
            setReferenceExpanded(!_uiState.value.referenceExpanded)
        }
    }

    /**
     * *Save guide* (FR-3.8): keeps the guide on the camera, to use again from *Add a pose guide*. A saved guide is
     * not saved twice: another tap takes it out of the saved guides again. When [SavedGuideStore.MAX_SAVED] are saved,
     * the store deletes the oldest and the notice says so.
     */
    fun onSaveGuide() {
        val state = _uiState.value
        val guide = state.guide ?: return
        if (state.savingGuide) return
        _uiState.update { it.copy(savingGuide = true) }
        viewModelScope.launch {
            val notice = try {
                // The store's own list, not only guideSaved, which follows it a moment later.
                toggleSaved(savedGuides, guide, guides.state.value)
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
        /** How long a short notice stays on the Camera screen, such as the remote action notices. */
        const val NOTICE_MS = 3_000L

        /**
         * How long the notice that the subject left or dropped stays (FR-6.10, FR-6.11): longer than [NOTICE_MS], since
         * the photographer may not be looking at the screen (#238).
         */
        const val PEER_GONE_NOTICE_MS = 10_000L
    }
}
