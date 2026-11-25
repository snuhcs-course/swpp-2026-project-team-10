// AI-generated with ChatGPT Codex and Claude Code, 2026-11-25, reviewed by Jaewan Park, Sungmin Jo, Dongje Park, and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.content.IntentSender
import android.net.Uri
import android.os.SystemClock
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
import com.lastpenguin.pix.camera.PhoneAlbum
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoAccess
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.camera.PhotoDeletion
import com.lastpenguin.pix.camera.baseExposureIndex
import com.lastpenguin.pix.camera.isAtZoomStep
import com.lastpenguin.pix.camera.nextZoomStep
import com.lastpenguin.pix.camera.quickZoomSteps
import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.PhotoShare
import com.lastpenguin.pix.session.PhotographerAction
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.ZoomHold
import com.lastpenguin.pix.telemetry.Telemetry
import com.lastpenguin.pix.telemetry.TelemetryEvent
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
    /** The guide is a favorite in the library now (#297). */
    SAVED,

    /** A second tap on a favorite's bookmark made it history again, still in the library (#297). */
    REMOVED,
    FAILED,
}

/**
 * What a toggle on the camera screens just did, shown as a short label at the top of the image (Design 2.8): showing or
 * hiding the guide, a zoom step, and a pinch held back while the other phone zooms.
 */
sealed interface ToggleLabel {
    data object GuideHidden : ToggleLabel

    data object GuideShown : ToggleLabel

    data class Zoom(val ratio: Float) : ToggleLabel

    /** The other phone's pinch holds the zoom, so this phone's changed nothing (FR-7.6, #236): "Jaewan is zooming". */
    data object ZoomHeld : ToggleLabel
}

/** The shutter's timer (FR-1.17): off, or the seconds from the shutter to the photo. */
enum class ShutterTimer(val seconds: Int) {
    OFF(0),
    THREE(3),
    TEN(10),
    ;

    /** The choice after this one at a tap on the menu's Timer: off, 3 s, 10 s, and off again. */
    fun next(): ShutterTimer = entries[(ordinal + 1) % entries.size]
}

/** What a choice in the camera menu changed (FR-1.17), which the screen says in a short notice. */
sealed interface MenuNotice {
    data class Flash(val mode: FlashMode) : MenuNotice

    data class Timer(val timer: ShutterTimer) : MenuNotice
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

/** Whether [guide] is a favorite in [saved] (FR-3.8, #297): what *Save guide*'s bookmark shows. */
internal fun isFavorite(saved: List<SavedGuide>, guide: ReferenceGuide?): Boolean =
    guide != null && saved.any { it.id == guide.id && it.favorite }

/**
 * *Save guide* as a toggle (FR-3.8, FR-6.16, #297): marks [guide] a favorite in [store], kept with [state] if the library
 * has no copy yet, or makes a favorite history again, by its id, which a guide keeps through save and load. Nothing is
 * deleted: the guide stays in the library either way.
 */
internal suspend fun toggleSaved(store: SavedGuideStore, guide: ReferenceGuide, state: GuideState): GuideSaveNotice =
    try {
        val kept = store.saved.value.firstOrNull { it.id == guide.id }
        when {
            kept?.favorite == true -> store.setFavorite(guide.id, false).map { GuideSaveNotice.REMOVED }

            kept != null -> store.setFavorite(guide.id, true).map { GuideSaveNotice.SAVED }

            // A photo guide's history entry may be written meanwhile (`keepPhotoGuide`), and `save` keeps an entry as
            // it is: mark it a favorite then, or the heart would say saved while the library has it as history.
            else -> store.save(guide, state, favorite = true).mapCatching { saved ->
                if (!saved.favorite) store.setFavorite(saved.id, true).getOrThrow()
                GuideSaveNotice.SAVED
            }
        }.getOrDefault(GuideSaveNotice.FAILED)
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
    /** Whether the camera menu is open under the top bar (FR-1.17); it and the ratios are never open together. */
    val menuOpen: Boolean = false,
    /** The shutter's timer (FR-1.17), kept while the app runs; Pix starts with it off. */
    val timer: ShutterTimer = ShutterTimer.OFF,
    /** The seconds left before the timer's photo while it counts down, from the timer's seconds to 1; null otherwise. */
    val countdown: Int? = null,
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
     * zooms the camera. Off for a new guide, on by its toggle, and off again on the shutter or when the guide is
     * hidden, replaced, or removed (Design 2.6.2, #304).
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
    /** The guide on the camera is a favorite in the library (FR-3.8, #297), so *Save guide* shows it as saved. */
    val guideSaved: Boolean = false,
    /** *Save guide* is writing the guide; the button waits. */
    val savingGuide: Boolean = false,
    /**
     * The thumbnail's photo (FR-1.6): in a session, the session's newest, none until its first; otherwise the newest
     * photo on the phone that Pix may read, or the one just taken here.
     */
    val lastPhoto: Uri? = null,
    /** The photos of the session running now, oldest first, for the viewer (FR-1.6); null outside a session. */
    val sessionPhotos: List<Uri>? = null,
    /**
     * The phone's photos that Pix may read, oldest first by when each was taken, with one just saved here that
     * MediaStore has not told of yet.
     */
    val phonePhotos: List<Uri> = emptyList(),
    /** How much of the phone's photos [phonePhotos] has, which the photo permission decides (FR-1.6). */
    val photoAccess: PhotoAccess = PhotoAccess.ALL,
    /** The albums of [phonePhotos], the one with the newest photo first, for the Albums tab (FR-1.6). */
    val photoAlbums: List<PhoneAlbum> = emptyList(),
    /** The viewer's tab outside a session (FR-1.6): Photos at each opening. */
    val galleryTab: GalleryTab = GalleryTab.PHOTOS,
    /** The album opened from the Albums tab, whose photos the grid and its pages show; null otherwise (FR-1.6). */
    val album: PhoneAlbum? = null,
    /** The viewer of [viewerPhotos] is open over the camera (FR-1.6). */
    val photosOpen: Boolean = false,
    /**
     * The photo picked from the grid, which the viewer pages from (FR-1.6); null while the grid shows, and in a
     * session, whose viewer opens at the newest with no grid.
     */
    val pickedPhoto: Uri? = null,
    /** A short notice, such as "Joonhyung set zoom to 2×" (FR-7.4). */
    val notice: String? = null,
    /** The pose tip of the guide on the camera (FR-4.10), or null when it has none. */
    val tip: TipBanner? = null,
) {
    /** What the viewer pages through: the session's photos in a session, the phone's otherwise, or its [album]'s. */
    val viewerPhotos: List<Uri>
        get() = sessionPhotos ?: album?.photos ?: phonePhotos

    /** Outside a session the viewer's tabs and an album's grid show until a photo is picked (FR-1.6). */
    private val gallery: Boolean
        get() = photosOpen && sessionPhotos == null && pickedPhoto == null

    /**
     * A grid of photos shows, of all of them on the Photos tab or of an album opened from the Albums tab, until a
     * photo is picked from it (FR-1.6).
     */
    val photosGrid: Boolean
        get() = gallery && (galleryTab == GalleryTab.PHOTOS || album != null)

    /** The Albums tab shows the phone's albums, none of them opened (FR-1.6). */
    val albumsShown: Boolean
        get() = gallery && galleryTab == GalleryTab.ALBUMS && album == null

    /**
     * Whether the thumbnail opens the viewer: with a photo in it, and outside a session also while Pix may not read
     * every photo, so a tap can ask for them and the grid can say what it shows (FR-1.6).
     */
    val opensPhotos: Boolean
        get() = viewerPhotos.isNotEmpty() || (sessionPhotos == null && photoAccess != PhotoAccess.ALL)
}

/** The photo viewer's tabs outside a session (FR-1.6): every photo, or the phone's albums. */
enum class GalleryTab { PHOTOS, ALBUMS }

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
    /** The phone's photos that Pix may read (`PhonePhotos`, FR-1.6), oldest first, taken here or anywhere else. */
    private val phonePhotos: StateFlow<List<Uri>> = MutableStateFlow(emptyList()),
    /** How much of the phone's photos [phonePhotos] has (`PhonePhotos.access`). */
    private val photoAccess: StateFlow<PhotoAccess> = MutableStateFlow(PhotoAccess.ALL),
    /** The albums of [phonePhotos] (`PhonePhotos.albums`). */
    private val photoAlbums: StateFlow<List<PhoneAlbum>> = MutableStateFlow(emptyList()),
    /** Reads the permission and the phone's photos again (`PhonePhotos.refresh`). */
    private val rereadPhotos: suspend () -> Unit = {},
    /** The photos of the session running now (`PhotoSyncer.sessionPhotos`, FR-1.6); null outside a session. */
    private val sessionPhotos: Flow<List<Uri>?> = flowOf(null),
    /** Deletes a photo from the viewer, from the phone and the session's photos (`AppContainer.photoDeleter`). */
    private val deleter: PhotoDeleter = PhotoDeleter { PhotoDeletion.Failed },
    private val clock: () -> Long = System::currentTimeMillis,
    /** For durations, the telemetry's times (Design 2.8): never set back, unlike [clock], which dates the photos. */
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    /** How the phone is held: each photo is saved as it was at its shutter press (FR-1.5). */
    private val phoneTurns: PhoneTurns = PhoneTurns.Fixed(),
    /** Who holds the zoom in a session (`RemoteControlHandler.zoomHold`, FR-7.6); free outside one. */
    private val zoomHold: ZoomHold = ZoomHold(),
    /** Tells the subject's phone where a tap focused (`RemoteControlHandler.focusedHere`, FR-7.7). */
    private val onFocused: (x: Float, y: Float) -> Unit = { _, _ -> },
    /** Tells the subject's phone what was done on this camera (`RemoteControlHandler.didHere`, FR-7.4, #292). */
    private val onDid: (PhotographerAction) -> Unit = {},
    /**
     * Tells the subject's phone the seconds the timer has left, and 0 when it ends with the photo or is cancelled
     * (`RemoteControlHandler.countdownHere`, FR-1.17).
     */
    private val onCountdown: (Int) -> Unit = {},
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

    // True from a pinch's step that the subject's pinch held back until that pinch's final value (FR-7.6).
    private var zoomHeldBack = false

    /** Whether the gesture on the guide that is running has moved it, for what the subject's phone is told (#292). */
    private var guideGestureMoved = false

    private val _guideSaveNotices = Channel<GuideSaveNotice>(Channel.BUFFERED)

    /** One notice per *Save guide* tap, shown like the other short notices; kept until the screen reads it. */
    val guideSaveNotices: Flow<GuideSaveNotice> = _guideSaveNotices.receiveAsFlow()

    private val _toggleLabels = Channel<ToggleLabel>(Channel.CONFLATED)

    /** One label per toggle, the newest replacing one not yet shown; the screen shows it for [NOTICE_MS]. */
    val toggleLabels: Flow<ToggleLabel> = _toggleLabels.receiveAsFlow()

    private val _photoNotDeleted = Channel<Unit>(Channel.BUFFERED)

    /** One per photo the viewer could not delete, for its notice; kept until the screen reads it. */
    val photoNotDeleted: Flow<Unit> = _photoNotDeleted.receiveAsFlow()

    private val _deleteQuestions = Channel<IntentSender>(Channel.BUFFERED)

    /**
     * Android's question for each photo of another app that the viewer's Delete asked for (FR-1.16): the screen shows
     * it, and its answer goes to [deleteAnswered].
     */
    val deleteQuestions: Flow<IntentSender> = _deleteQuestions.receiveAsFlow()

    /** The photo whose Android question is open, and whether its yes deletes it; null while none is. */
    private var asking: Pair<Uri, Boolean>? = null

    private val _menuNotices = Channel<MenuNotice>(Channel.CONFLATED)

    /** One per choice in the camera menu, the newest replacing one not yet shown (FR-1.17). */
    val menuNotices: Flow<MenuNotice> = _menuNotices.receiveAsFlow()

    private val _timedShots = Channel<Unit>(Channel.CONFLATED)

    /** One each time the timer's countdown ends in a photo, for the preview's blink, as at the shutter (FR-1.5). */
    val timedShots: Flow<Unit> = _timedShots.receiveAsFlow()

    // The timer's countdown while it runs (FR-1.17).
    private var countdownJob: Job? = null

    private val _guideRemoved = Channel<Unit>(Channel.CONFLATED)

    /** Remove guide took a guide off, for the notice whose Undo puts it back (#298). */
    val guideRemoved: Flow<Unit> = _guideRemoved.receiveAsFlow()

    /** The guide Remove guide took off, and where it was, until it is put back or another guide goes on. */
    private var removed: Pair<ReferenceGuide, GuideState>? = null

    // The thumbnail's and the viewer's sources (FR-1.6): outside a session the phone's photos, with the photo just
    // saved here before MediaStore tells of it; in a session the session's photos alone.
    private var album: List<Uri> = emptyList()
    private var albums: List<PhoneAlbum> = emptyList()
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
            // reads the new frame, and the final change reaches a connected subject. The rebind drops the focus point,
            // so the brightness bar and the box beside it go, as on Subject view.
            camera.state.map { it.aspect }.distinctUntilChanged().drop(1).collect {
                guides.update(final = true) { state -> state }
                _uiState.update { it.copy(brightnessOpen = false) }
            }
        }
        viewModelScope.launch {
            camera.facing.collect { facing ->
                pendingZoomStep = null
                _uiState.update { it.copy(facing = facing) }
            }
        }
        viewModelScope.launch {
            // The base brightness, 0 EV, once the camera's range is known and whenever the camera changes; what a
            // drag added is gone with it (FR-1.11).
            camera.capabilities
                .map { it?.exposureRange to it?.exposureStep }
                .distinctUntilChanged()
                .collect { applyBaseBrightness() }
        }
        viewModelScope.launch {
            camera.hasFrontCamera.collect { has -> _uiState.update { it.copy(canSwitchCamera = has) } }
        }
        viewModelScope.launch {
            phonePhotos.collect(::takePhonePhotos)
        }
        viewModelScope.launch {
            photoAlbums.collect { read ->
                albums = read
                showNewestPhoto()
            }
        }
        viewModelScope.launch {
            photoAccess.collect { access ->
                _uiState.update { it.copy(photoAccess = access) }
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
                // Another guide on the camera: nothing is left for Undo to put back.
                if (guide != null && guide !== removed?.first) removed = null
                _uiState.update {
                    // A new guide starts with the camera's gestures, which are used more than the guide's (#304): the
                    // mode holds only for the guide it was turned on for; no guide, no mode.
                    val editing = guide != null && guide === it.guide && it.guideEditing
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
                isFavorite(saved, guide)
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

    /** The phone's photos as read, the newest of them the thumbnail's. */
    private fun takePhonePhotos(photos: List<Uri>) {
        album = photos
        newestSaved = photos.lastOrNull()
        showNewestPhoto()
    }

    /**
     * In a session, the session's newest photo, none until its first, and the session's photos for the viewer;
     * otherwise the newest saved here, and the phone's photos (FR-1.6).
     */
    private fun showNewestPhoto() {
        val session = thisSession
        val saved = newestSaved
        val phonePhotos = if (saved != null && saved !in album) album + saved else album
        _uiState.update {
            val next = it.copy(
                lastPhoto = if (session != null) session.lastOrNull() else saved,
                sessionPhotos = session,
                phonePhotos = phonePhotos,
                photoAlbums = albums,
                // The album as read now; one that has no photo left is gone, and the Albums tab shows again.
                album = it.album?.let { shown -> albums.firstOrNull { read -> read.id == shown.id } },
            )
            // The viewer closes as a session starts or ends, so it never turns from one's photos to the other's.
            val sameKind = (it.sessionPhotos == null) == (session == null)
            val open = it.photosOpen && sameKind && next.opensPhotos
            next.copy(
                photosOpen = open,
                pickedPhoto = it.pickedPhoto.takeIf { open && next.viewerPhotos.isNotEmpty() },
                album = next.album.takeIf { open },
                galleryTab = if (open) next.galleryTab else GalleryTab.PHOTOS,
            )
        }
    }

    /**
     * A tap on the thumbnail opens its photos over the camera (FR-1.6), so Pix stays in front: in a session the
     * session's, at the newest; otherwise the phone's, as a grid of them all, the newest first.
     */
    fun openPhotos() {
        _uiState.update {
            val all = it.copy(galleryTab = GalleryTab.PHOTOS, album = null)
            all.copy(photosOpen = all.opensPhotos && it.countdown == null, pickedPhoto = null)
        }
    }

    /** The viewer's tabs (FR-1.6): Photos or Albums, only while one of them shows, not an album or a photo. */
    fun chooseGalleryTab(tab: GalleryTab) {
        _uiState.update { state ->
            val onTab = state.albumsShown || (state.photosGrid && state.album == null)
            if (onTab) state.copy(galleryTab = tab) else state
        }
    }

    /** A tap on an album of the Albums tab opens its photos as a grid (FR-1.6). */
    fun openAlbum(id: String) {
        _uiState.update { state ->
            val album = state.photoAlbums.firstOrNull { it.id == id }
            if (state.albumsShown && album != null) state.copy(album = album) else state
        }
    }

    /**
     * The photo permission was just asked, from the thumbnail or the grid (FR-1.6): the phone's photos are read again
     * with what was allowed, and the viewer opens on them if it is not open yet.
     */
    fun photoAccessAnswered() {
        viewModelScope.launch {
            rereadPhotos()
            // Before the collectors hear of it, so the viewer opens on the photos just allowed.
            _uiState.update { it.copy(photoAccess = photoAccess.value) }
            albums = photoAlbums.value
            takePhonePhotos(phonePhotos.value)
            if (!_uiState.value.photosOpen) openPhotos()
        }
    }

    /** As the screen starts: the permission may have changed in the phone's settings meanwhile (FR-1.6). */
    fun readPhotosAgain() {
        viewModelScope.launch { rereadPhotos() }
    }

    /** A tap on a photo of the grid opens it in the viewer, where a swipe shows the others (FR-1.6). */
    fun pickPhoto(uri: Uri) {
        _uiState.update { if (it.photosGrid && uri in it.viewerPhotos) it.copy(pickedPhoto = uri) else it }
    }

    /**
     * The viewer's Back, or the system's, and a drag down of its photo, one step back (FR-1.6): from a photo picked from
     * a grid to that grid; from an album's grid to the Albums tab; from either tab, the viewer closes.
     */
    fun closePhotos() {
        _uiState.update {
            when {
                it.photosOpen && it.pickedPhoto != null -> it.copy(pickedPhoto = null)
                it.photosOpen && it.album != null -> it.copy(album = null)
                else -> it.copy(photosOpen = false, pickedPhoto = null, album = null, galleryTab = GalleryTab.PHOTOS)
            }
        }
    }

    /** Whether the viewer asks before it deletes [uri]: for Pix's own photos, which go at once (FR-1.16). */
    fun asksBeforeDeleting(uri: Uri): Boolean = deleter.asksFirst(uri)

    /**
     * The viewer's Delete (FR-1.6, FR-1.16), once Pix asked for its own photo: the photo leaves the viewer and the
     * thumbnail at once, before MediaStore tells of it, or stays, and [photoNotDeleted] says so. In a session the
     * deleter drops it from the session's photos too. Another app's photo goes to Android's question
     * ([deleteQuestions]) instead.
     */
    fun deletePhoto(uri: Uri) = delete(uri, answered = false)

    /**
     * Android's question for another app's photo was answered: a yes deleted it, or, on Android 10, lets Pix delete
     * it now; a no keeps it, with nothing to say.
     */
    fun deleteAnswered(yes: Boolean) {
        val (uri, deletesItself) = asking ?: return
        asking = null
        when {
            !yes -> Unit
            deletesItself -> dropPhoto(uri)
            else -> delete(uri, answered = true)
        }
    }

    private fun delete(uri: Uri, answered: Boolean) {
        viewModelScope.launch {
            when (val result = deleter.delete(uri)) {
                PhotoDeletion.Deleted -> dropPhoto(uri)

                PhotoDeletion.Failed -> _photoNotDeleted.trySend(Unit)

                // Asked once already, a second question would never end.
                is PhotoDeletion.NeedsConsent -> if (answered) {
                    _photoNotDeleted.trySend(Unit)
                } else {
                    asking = uri to result.deletesItself
                    _deleteQuestions.trySend(result.request)
                }
            }
        }
    }

    /** [uri] is gone from the phone: from the viewer and the thumbnail at once, before MediaStore tells of it. */
    private fun dropPhoto(uri: Uri) {
        album = album - uri
        albums =
            albums.map {
                if (uri in
                    it.photos
                ) {
                    it.copy(photos = it.photos - uri)
                } else {
                    it
                }
            }.filter { it.photos.isNotEmpty() }
        if (newestSaved == uri) newestSaved = album.lastOrNull()
        showNewestPhoto()
    }

    /**
     * The ratios under the top bar open or close (FR-1.13). They open in the camera menu's place, which closes, and not
     * while the timer counts down.
     */
    fun setAspectsOpen(open: Boolean) {
        _uiState.update {
            val opens = open && it.countdown == null
            it.copy(aspectsOpen = opens, menuOpen = it.menuOpen && !opens)
        }
    }

    /**
     * The camera menu under the top bar opens or closes (FR-1.17). It opens in the ratios' place, which close, and not
     * while the timer counts down.
     */
    fun setMenuOpen(open: Boolean) {
        _uiState.update {
            val opens = open && it.countdown == null
            it.copy(menuOpen = opens, aspectsOpen = it.aspectsOpen && !opens)
        }
    }

    /**
     * The menu's Timer (FR-1.17): each tap the next of off, 3 s, and 10 s, which the screen says in a notice. Not while
     * the timer counts down.
     */
    fun onTimerTap() {
        if (_uiState.value.countdown != null) return
        val next = _uiState.value.timer.next()
        _uiState.update { it.copy(timer = next) }
        _menuNotices.trySend(MenuNotice.Timer(next))
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
     * The shutter: with the timer off, the photo now ([shoot]), and true when it takes one. With the timer on, the
     * countdown starts instead, and its end takes the photo ([timedShots]); a tap while it counts down cancels it
     * (FR-1.17). Neither while the camera is not ready or has not yet taken the photo of the press before.
     */
    fun onShutter(): Boolean {
        val state = _uiState.value
        if (state.countdown != null) {
            cancelCountdown()
            return false
        }
        if (state.timer == ShutterTimer.OFF) return shoot()
        if (state.shutterBusy || camera.status.value != CameraStatus.READY) return false
        startCountdown(state.timer.seconds)
        return false
    }

    /**
     * The scene photo's shutter (FR-4.1, FR-1.17): with the timer off, [take] at once; with it on, the countdown, at
     * whose end [take] runs, as the photo's shutter does; a tap while it counts down cancels it. Not while the camera is
     * not ready.
     */
    fun onSceneShutter(take: () -> Unit) {
        val state = _uiState.value
        when {
            state.countdown != null -> cancelCountdown()
            state.timer == ShutterTimer.OFF -> take()
            camera.status.value == CameraStatus.READY -> startCountdown(state.timer.seconds, take)
        }
    }

    /**
     * The timer's countdown (FR-1.17): from [seconds] down to 1, a second each, told to the subject's phone at each
     * step, then the photo, or [take] for the scene photo. The shutter's own changes come at its start: the guide's edit
     * mode and the open panels end, and an earlier photo's notice goes.
     */
    private fun startCountdown(seconds: Int, take: (() -> Unit)? = null) {
        _uiState.update {
            it.copy(
                countdown = seconds,
                captureNotice = null,
                guideEditing = false,
                aspectsOpen = false,
                menuOpen = false,
            )
        }
        countdownJob = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.update { it.copy(countdown = left) }
                onCountdown(left)
                delay(COUNTDOWN_STEP_MS)
            }
            countdownJob = null
            _uiState.update { it.copy(countdown = null) }
            onCountdown(0)
            if (take != null) {
                take()
                return@launch
            }
            // A camera that went away during the countdown takes no photo, and says so as a failed one would.
            if (shoot()) {
                _timedShots.trySend(Unit)
            } else {
                _uiState.update { it.copy(captureNotice = CaptureNotice.CAMERA_UNAVAILABLE) }
            }
        }
    }

    /**
     * Stops the timer's countdown, if one runs, without a photo (FR-1.17): the shutter's second tap, or the screen
     * going to the background or away. The subject's phone is told, so its number goes too.
     */
    fun cancelCountdown() {
        val job = countdownJob ?: return
        countdownJob = null
        job.cancel()
        _uiState.update { it.copy(countdown = null) }
        onCountdown(0)
    }

    /**
     * Takes a photo, unless the camera is not ready or has not yet taken the photo of the press before; true when it
     * takes one. The shutter is free again as soon as the camera took the picture, while the photo is still being
     * saved (NFR-19), so photos can follow each other; [CameraUiState.isSaving] lasts until all of them are done. Their
     * results are published in shutter order.
     */
    private fun shoot(): Boolean {
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
                menuOpen = false,
            )
        }
        val takenAt = clock()
        val pressedAt = elapsed()
        // At the press, like the time: the phone may turn while an earlier photo is still being saved.
        val turn = phoneTurns.turn.value
        // Now, not once it is saved: the photo belongs to the session running at the shutter.
        val shot = photos.shutter()
        // The press before, for the order below.
        val earlier = lastShot
        lastShot = viewModelScope.launch {
            // This press's own hold on the shutter: the "taken" of an earlier photo, come late, must not free it.
            var held = true
            // When the camera took the photo, for its `photo_taken` event (NFR-19; Design 2.8, Telemetry).
            var capturedAt: Long? = null
            fun free() {
                if (!held) return
                held = false
                _uiState.update { it.copy(shutterBusy = false) }
            }
            try {
                val result = try {
                    camera.setPhotoTurn(turn)
                    camera.takePhoto(
                        onCaptured = {
                            capturedAt = elapsed()
                            free()
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
                // A camera that never said "taken", or a photo that failed before it.
                free()
                val endedAt = elapsed()
                // In shutter order, whatever order the saves end in: the thumbnail is the newest photo, and the
                // session sends the photos as they were taken (PhotoSyncer).
                earlier?.join()
                Telemetry.event(
                    TelemetryEvent.PhotoTaken(
                        saved = result.isSuccess,
                        shutterMs = capturedAt?.let { it - pressedAt },
                        ms = endedAt - pressedAt,
                        inSession = shot != null,
                    ),
                )
                result.fold(
                    onSuccess = { uri ->
                        // No notice (FR-1.6, #204): the thumbnail shows the new photo, in a session once it is the
                        // session's.
                        newestSaved = uri
                        showNewestPhoto()
                        shot?.let { photos.taken(it, uri, takenAt) }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        shot?.let(photos::notTaken)
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
        if (!mayZoom(final)) return
        val asked = requestZoom(ratio, final)
        if (!final) return
        // Told before the zoom is free again, so the subject's phone hears the ratio before the pinch's end (#292).
        if (asked) _uiState.value.let { onDid(PhotographerAction.Zoom(ratio.coerceIn(it.minZoom, it.maxZoom))) }
        zoomHold.release(Role.PHOTOGRAPHER)
    }

    /**
     * Whether this phone may zoom now (FR-7.6, #236): not while the subject's pinch holds the zoom. A pinch's step takes
     * the zoom for the photographer until its final value; a pinch held back stays so until its fingers lift, and its
     * first step says who is zooming. A single final value (a quick step, a TalkBack action) takes nothing.
     */
    private fun mayZoom(final: Boolean): Boolean {
        if (zoomHeldBack) {
            if (final) zoomHeldBack = false
            return false
        }
        val may = if (final) zoomHold.mayChange(Role.PHOTOGRAPHER) else zoomHold.take(Role.PHOTOGRAPHER)
        if (may) return true
        zoomHeldBack = !final
        _toggleLabels.trySend(ToggleLabel.ZoomHeld)
        return false
    }

    /**
     * A tap on the zoom readout (FR-1.3): the next quick step, such as 0.6×, 1×, and 2×, the last back to the first,
     * from the step an earlier tap asked for while the camera has not applied it yet.
     */
    fun onZoomTap() {
        val state = _uiState.value
        val step = nextZoomStep(quickZoomSteps(state.minZoom, state.maxZoom), pendingZoomStep ?: state.zoom) ?: return
        if (!mayZoom(final = true)) return
        if (requestZoom(step, final = true)) {
            pendingZoomStep = step
            _toggleLabels.trySend(ToggleLabel.Zoom(step))
            onDid(PhotographerAction.Zoom(step))
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
        // A tap's step and a pinch's last value glide where the camera can show it (#310); a pinch's steps are applied
        // as they come, so the picture follows the fingers.
        if (final) camera.glideZoom(ratio) else camera.setZoom(ratio, final = false)
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
        if (state.supportsFocusPoint) {
            camera.setFocusPoint(x, y)
            onFocused(x, y)
        }
        // A new tap drops the brightness a drag added after the last one, back to the base (FR-1.11), also on a camera
        // that cannot focus on a point.
        applyBaseBrightness()
    }

    /** The exposure index of the base brightness, 0 EV, for this camera, or null without exposure compensation. */
    fun baseExposureIndex(): Int? = camera.capabilities.value?.baseExposureIndex()

    private fun applyBaseBrightness() {
        baseExposureIndex()?.let(camera::setExposure)
    }

    /**
     * From the brightness bar or a drag on the preview: each step is applied as it comes, like a pinch zoom (FR-1.11).
     * [final] is the drag's end, which the subject's phone is told of (#292).
     */
    fun onExposureChange(index: Int, final: Boolean = false) {
        val range = _uiState.value.exposureRange
        if (range.first >= range.last) return
        val applied = index.coerceIn(range)
        camera.setExposure(applied)
        if (final) onDid(PhotographerAction.Brightness(applied))
    }

    fun setBrightnessOpen(open: Boolean) {
        _uiState.update { it.copy(brightnessOpen = open && it.exposureRange.first < it.exposureRange.last) }
    }

    /**
     * The menu's Flash cycles off, auto, on (FR-1.12), which the screen says in a notice (FR-1.17); not while the timer
     * counts down.
     */
    fun onFlashTap() {
        val state = _uiState.value
        if (!state.hasFlash || state.countdown != null) return
        val next = when (state.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        camera.setFlash(next)
        onDid(PhotographerAction.Flash(next))
        _menuNotices.trySend(MenuNotice.Flash(next))
    }

    /**
     * The camera switch (FR-1.14): the front camera for a selfie, or the rear one again, with or without a session. The
     * choice lasts while the app runs; Pix opens with the rear camera. Not while a photo is saved or the timer counts
     * down.
     */
    fun onSwitchCamera() {
        val state = _uiState.value
        if (state.isSaving || !state.canSwitchCamera || state.countdown != null) return
        camera.setFacing(if (state.facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK)
    }

    /**
     * A ratio chosen under the top bar (FR-1.13): the ratios close, and a new one goes to the camera and is kept for the
     * next time. None while a photo is being saved, since a rebind waits for it.
     */
    fun onAspectChosen(aspect: AspectRatio) {
        val state = _uiState.value
        _uiState.update { it.copy(aspectsOpen = false) }
        if (state.isSaving || aspect == state.aspect || state.countdown != null) return
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
        if (dx != 0f || dy != 0f || scale != 1f) guideGestureMoved = true
        if (!final) return
        // A gesture that moved the guide is told once, at its end; a touch that moved nothing is not (#292).
        if (guideGestureMoved && _uiState.value.guide != null) onDid(PhotographerAction.GuideMove)
        guideGestureMoved = false
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
     * *Save guide* (FR-3.8): marks the guide on the camera a favorite in the library, to use again from *Add a pose
     * guide*; another tap makes it history again, still in the library (#297).
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

    /**
     * Removes the guide; a connected subject gets `guide.clear` through GuideSyncer (#9). It is kept, with where it
     * was, for [onUndoRemoveGuide] (#298).
     */
    fun onRemoveGuide() {
        val guide = guides.guide.value ?: return
        removed = guide to guides.state.value
        guides.setGuide(null)
        _guideRemoved.trySend(Unit)
    }

    /**
     * Puts back the guide [onRemoveGuide] took off, at its place, size, and opacity, unless another guide went on since
     * (#298). It is the same guide, so it stays saved, PoseTips gives back its tip, and GuideSyncer sends it to a
     * connected subject again.
     */
    fun onUndoRemoveGuide() {
        val (guide, state) = removed ?: return
        removed = null
        if (guides.guide.value != null) return
        guides.setGuide(guide)
        guides.update(final = true) { state }
    }

    override fun onCleared() {
        // A pinch cut off with the screen never sends its final value; the zoom must not stay the photographer's.
        zoomHold.release(Role.PHOTOGRAPHER)
        cancelCountdown()
    }

    companion object {
        /** One step of the timer's countdown (FR-1.17). */
        const val COUNTDOWN_STEP_MS = 1_000L

        /** How long a short notice stays on the Camera screen, such as the remote action notices. */
        const val NOTICE_MS = 3_000L

        /**
         * How long the notice that the subject left or dropped stays (FR-6.10, FR-6.11): longer than [NOTICE_MS], since
         * the photographer may not be looking at the screen (#238).
         */
        const val PEER_GONE_NOTICE_MS = 10_000L
    }
}
