// AI-generated with ChatGPT Codex and Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoDeleter
import com.lastpenguin.pix.camera.PhotoDeletion
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.camera.isAtZoomStep
import com.lastpenguin.pix.camera.nextZoomStep
import com.lastpenguin.pix.camera.quickZoomSteps
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.guide.GuideGeometry
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.ConfirmEnd
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.DescriptionEnd
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.MirrorReference
import com.lastpenguin.pix.session.PhotographerAction
import com.lastpenguin.pix.session.PoseSetEnd
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.RemoteVideo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.SharedTip
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.toAction
import com.lastpenguin.pix.ui.camera.GuideSaveNotice
import com.lastpenguin.pix.ui.camera.ReferenceCorner
import com.lastpenguin.pix.ui.camera.ReferenceDrop
import com.lastpenguin.pix.ui.camera.ToggleLabel
import com.lastpenguin.pix.ui.camera.isFavorite
import com.lastpenguin.pix.ui.camera.toggleSaved
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The photographer's tip of the guide on the video, as Subject view shows it (FR-4.10, #213): closed until the tip
 * button, whose dot says there is a tip not yet looked at, and closed again only by that button.
 */
data class SubjectTip(val guideId: String, val tip: PoseTip, val open: Boolean = false, val unseen: Boolean = true)

/**
 * The tip after a change of what the photographer sent for the guide on the video ([shared]): another guide's tip, or
 * one that comes back, starts closed; new words for the same guide leave the banner as it is; none without a tip.
 */
internal fun nextSubjectTip(previous: SubjectTip?, shared: SharedTip?): SubjectTip? = when {
    shared == null -> null
    previous?.guideId == shared.guideId -> previous.copy(tip = shared.tip)
    else -> SubjectTip(shared.guideId, shared.tip)
}

/** Whose ✕ took the guide off both phones (FR-7.2): this phone's, or the photographer's. */
enum class GuideRemoval { THIS_PHONE, PHOTOGRAPHER }

/** Subject view (R&S 6.6, FR-3.9, FR-4.10, FR-6.6, FR-6.15, FR-6.16, FR-7.1–7.3). */
data class SubjectUiState(
    /** For "Dongje's camera"; arrives in `hello`. */
    val photographerName: String? = null,
    /** Both data channels are up, so remote control can be used. */
    val connected: Boolean = false,
    /** The photographer's zoom range, from `camera.capabilities`; equal until it arrives. */
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    /** The zoom to show: the pinch's value during the gesture, until the photographer echoes applied zoom in `camera.state`. */
    val zoom: Float = 1f,
    /** The photographer's frame (AD-10), from `camera.state`: the video, the grid, and the guide fit to it. */
    val aspect: AspectRatio = AspectRatio.R4_3,
    /**
     * The video, the grid, the guide, and the reference photo show as a mirror, so the subject moves the way they see
     * themselves (#201): for the rear camera, from `camera.state`. The front camera's video comes mirrored already.
     */
    val mirrored: Boolean = true,
    /** The photographer's exposure range and EV per index, from `camera.capabilities`; 0..0 hides the control. */
    val exposureRange: IntRange = 0..0,
    val exposureStep: Float = 0f,
    /** The brightness to show: the slider's value during a drag, until the photographer echoes the applied one. */
    val exposure: Int = 0,
    /** Whether the brightness bar is up, beside where this phone tapped the video (FR-7.3). */
    val brightnessOpen: Boolean = false,
    /** Whether the photographer's camera focuses on a point, from `camera.capabilities`: a tap shows the box then. */
    val supportsFocusPoint: Boolean = false,
    /**
     * A tap to focus is not answered yet, so the brightness the photographer's camera went back to is not known. A
     * drag that began with that tap waits for it, so that it starts from there as on Camera (FR-1.11).
     */
    val baseAwaited: Boolean = false,
    /** False hides the flash button (FR-7.3). */
    val hasFlash: Boolean = false,
    /** The flash to show: the tapped mode, until the photographer echoes the applied one. */
    val flash: FlashMode = FlashMode.OFF,
    val guide: ReferenceGuide? = null,
    /** The guide to draw: the mirror's state, or this phone's own gesture while it runs (Design 2.6.5). */
    val guideState: GuideState = GuideState(),
    /**
     * Guide-edit mode (AD-17): while on, drags and pinches on the live video move the photographer's guide; while
     * off, a pinch zooms the photographer's camera. Off until the subject turns it on, and off again when the guide goes.
     */
    val guideEditing: Boolean = false,
    /**
     * The guide's reference photo in a corner (FR-3.9): the photographer's copy while it belongs to the guide on the
     * video, null otherwise. It stays while the guide is hidden, as on the photographer's phone.
     */
    val reference: Bitmap? = null,
    /** Whether the thumbnail is enlarged; a new reference photo starts small. */
    val referenceExpanded: Boolean = false,
    /** The thumbnail's corner on this phone; never sent, so each phone keeps its own. */
    val referenceCorner: ReferenceCorner = ReferenceCorner.TOP_LEFT,
    /**
     * The thumbnail is put away at its corner's side of the video, with a strip of it left to pull it back by
     * (FR-3.9, #237). On this phone only, like its size and corner; another guide's thumbnail shows again. A hidden
     * thumbnail is never [referenceExpanded]: hiding makes it small, so its strip is the small one's and it comes
     * back small.
     */
    val referenceHidden: Boolean = false,
    /**
     * A press of this phone's shutter waits for the photographer's phone to say whether it took the photo (FR-7.8,
     * #314): the shutter takes no other press meanwhile.
     */
    val shutterWaiting: Boolean = false,
    /**
     * The guide this phone offered (*Add a pose guide*) while the photographer's phone has not adopted it yet
     * (`GuideSyncer.offering`); null otherwise. That phone would drop a request for it, so the panel waits (FR-7.2).
     */
    val offeredGuide: String? = null,
    /** Whether the guide on the video is a favorite in this phone's library (FR-6.16, #297): *Save guide* is filled. */
    val guideSaved: Boolean = false,
    val savingGuide: Boolean = false,
    /**
     * The photographer's open set of candidates (Design 2.6.5): GENERATING while it is being created, PICKING while
     * this phone can pick from it on *Pick a pose*; null when there is none.
     */
    val poseSetId: String? = null,
    val poseStep: PoseSetStep? = null,
    /** With PICKING: whether a candidate's copy is here to pick from; a set with none opens no *Pick a pose*. */
    val posesReady: Boolean = false,
    /**
     * The photographer's open Reference confirm of the chosen pose (Design 2.6.5), which this phone shows on *Your
     * reference*; null when there is none.
     */
    val confirmId: String? = null,
    /** The photographer's open *Generate poses* (Design 2.6.5), which this phone shows too; null when there is none. */
    val describeId: String? = null,
    /** The photographer's tip of the guide on the video (FR-4.10, #213); null while that guide has none. */
    val tip: SubjectTip? = null,
    /** The session's photos in this phone's album (FR-6.14), oldest first; the newest is the thumbnail. */
    val photos: List<Uri> = emptyList(),
    /** The viewer of [photos] is open over the video (FR-6.14). */
    val photosOpen: Boolean = false,
    /** A photo of the photographer's is on its way here, from their shutter until it is saved (FR-6.14, #271). */
    val receivingPhoto: Boolean = false,
    /** Whether the camera menu is open under the top bar (FR-1.17). */
    val menuOpen: Boolean = false,
    /** The seconds the photographer's timer has left before its photo (FR-1.17), from `camera.timer`; null otherwise. */
    val countdown: Int? = null,
    /** The photographer's timer as chosen, on either phone (FR-1.17, #318). */
    val timer: ShutterTimer = ShutterTimer.OFF,
) {
    /** The thumbnail's photo, none until the session's first. */
    val lastPhoto: Uri?
        get() = photos.lastOrNull()

    val canZoom: Boolean
        get() = connected && minZoom.isFinite() && maxZoom.isFinite() && minZoom > 0f && maxZoom > minZoom

    /** The guide on the video is this phone's offer, not yet the photographer's: its panel's shared controls wait. */
    val guideWaiting: Boolean
        get() = guide != null && guide.id == offeredGuide

    /** Whether the photographer's camera has a brightness range to set (FR-7.3). */
    val hasBrightness: Boolean
        get() = exposureRange.first < exposureRange.last

    /**
     * Whether a tap on the video does something (FR-7.7): the camera focuses on a point or has a brightness to put
     * back, as on Camera, and the touch is not the guide's.
     */
    val canFocus: Boolean
        get() = connected && !guideEditing && (supportsFocusPoint || hasBrightness)
}

/** A line Subject view shows about what the photographer did on their own camera (FR-7.4, #292). */
sealed interface PhotographerLine {
    /** An action told as `camera.did`: the zoom, the brightness, the flash, the guide moved, or a guide set. */
    data class Did(val action: PhotographerAction) : PhotographerLine

    /** The photographer's own tap to focus (`camera.focus`), whose box shows at every tap and this line less often. */
    data object Focused : PhotographerLine
}

/**
 * Subject view (Design 2.2, Figure 7). [video] is where the screen attaches its renderer.
 *
 * Every control is a request to the photographer (AD-7): the screen shows the requested value at once, sends steps
 * at most every [SEND_INTERVAL_MS] and the final value right away ([StepSender]), and the photographer's echo in
 * `camera.state` or `guide.state` decides what stays once the gesture is over (Design 2.6.5). The guide on the video
 * can be saved on this phone, and the photographer's Generate poses ([descriptions]), set of candidates ([poses]),
 * and Reference confirm ([confirms]) show here while they are open. The session's newest photo ([sessionPhotos]) is a
 * thumbnail that opens the session's photos in a viewer over the video (FR-6.14).
 * Owner: Real-time (#8 live view, #10 remote zoom, #57 remote controls), with the guide from #9; *Save guide*, the
 * photographer's candidates, and their Reference confirm Iter3-P16.
 */
class SubjectViewModel(
    private val session: SessionManager,
    private val mirror: GuideRepository,
    private val references: MirrorReference,
    val video: RemoteVideo,
    private val savedGuides: SavedGuideStore = SavedGuideStore.InMemory(),
    private val poses: PoseSyncer? = null,
    private val confirms: ConfirmSyncer? = null,
    private val descriptions: DescriptionSyncer? = null,
    private val tips: TipSyncer? = null,
    /** The session's photos in this phone's album (`PhotoSyncer.sessionPhotos`, FR-6.14); null outside a session. */
    private val sessionPhotos: Flow<List<Uri>?> = flowOf(null),
    /** Deletes a photo from the viewer, from the album and the session's photos (`AppContainer.photoDeleter`). */
    private val deleter: PhotoDeleter = PhotoDeleter { PhotoDeletion.Failed },
    /** Whether a photo of the photographer's is on its way here (`PhotoSyncer.receiving`, #271). */
    private val receivingPhoto: Flow<Boolean> = flowOf(false),
    /** The guide this phone offered and the photographer has not adopted yet (`GuideSyncer.offering`). */
    private val offering: Flow<String?> = flowOf(null),
    /** How this phone is held, which its own controls' icons turn with; nothing is sent (FR-1.15). */
    private val phoneTurns: PhoneTurns = PhoneTurns.Fixed(),
    /** For the time between two lines about the photographer's focus (#292). */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    /** Follows how this phone is held while [owner], Subject view, is in front (FR-1.15). */
    fun watchPhoneTurn(owner: LifecycleOwner) = phoneTurns.watch(owner)

    /** How this phone is held, which the controls' icons turn with (FR-1.15). */
    val phoneTurn: StateFlow<Int> = phoneTurns.turn

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    private val _guideSaveNotices = Channel<GuideSaveNotice>(Channel.BUFFERED)

    /** One notice per *Save guide* tap, as on the Camera screen; kept until the screen reads it. */
    val guideSaveNotices: Flow<GuideSaveNotice> = _guideSaveNotices.receiveAsFlow()

    private val _photoNotDeleted = Channel<Unit>(Channel.BUFFERED)

    /** One per photo the viewer could not delete, for its notice; kept until the screen reads it. */
    val photoNotDeleted: Flow<Unit> = _photoNotDeleted.receiveAsFlow()

    private val _photographerFocus = MutableSharedFlow<RemoteFocus>(extraBufferCapacity = 16)

    /**
     * Each of the photographer's own taps to focus (`camera.focus`, FR-7.7), for the box on the video: where this phone
     * shows that point, so turned to the mirror for the rear camera.
     */
    val photographerFocus: SharedFlow<RemoteFocus> = _photographerFocus.asSharedFlow()

    private val _photographerLines = MutableSharedFlow<PhotographerLine>(extraBufferCapacity = 16)

    /**
     * What the photographer did on their own camera (FR-7.4, #292), for a line where this screen's labels show, as
     * Camera says what the subject did. A line the screen is not there to read is not kept.
     */
    val photographerLines: SharedFlow<PhotographerLine> = _photographerLines.asSharedFlow()

    /** When the line about the photographer's focus was last said, on [clock]; null before the first. */
    private var focusLineAt: Long? = null

    /**
     * Whether the photographer's pinch is changing the zoom now (FR-7.6, #292): from its first step to its end, the
     * screen says who is zooming, and the ratio's line follows.
     */
    val photographerZooming: StateFlow<Boolean> = session.peerZoomHolder
        .map { it == Role.PHOTOGRAPHER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _shutterRefusals = Channel<Unit>(Channel.CONFLATED)

    /**
     * A press of this phone's shutter that took no photo (FR-7.8, #314): the photographer's phone said so, or said
     * nothing in time. Subject view says it in a line; one that no screen read is not said later.
     */
    val shutterRefusals: Flow<Unit> = _shutterRefusals.receiveAsFlow()

    private val _toggleLabels = Channel<ToggleLabel>(Channel.CONFLATED)

    /** One label per zoom step, as on the Camera screen (Design 2.8); the newest replaces one not yet shown. */
    val toggleLabels: Flow<ToggleLabel> = _toggleLabels.receiveAsFlow()

    /** Each of the photographer's sets that ended, for its notice while this screen is in front (Design 2.6.5). */
    val poseSetEnds: Flow<PoseSetEnd> = poses?.ended ?: emptyFlow()

    /** The last set one of whose screens this screen opened by itself, so it opens once per set. */
    private var setOpenedFor: String? = null

    /** Each of the photographer's Reference confirms that ended, for its notice while this screen is in front. */
    val confirmEnds: Flow<ConfirmEnd> = confirms?.ended ?: emptyFlow()

    /** The last confirm whose *Your reference* this screen opened by itself, so it opens once per confirm. */
    private var confirmOpenedFor: String? = null

    /** Each of the photographer's Generate poses that ended, for its notice while this screen is in front. */
    val describeEnds: Flow<DescriptionEnd> = descriptions?.ended ?: emptyFlow()

    /** The last description whose *Generate poses* this screen opened by itself, so it opens once per description. */
    private var describeOpenedFor: String? = null

    private val zoomSender = StepSender<Float>(viewModelScope, SEND_INTERVAL_MS) { ratio, final ->
        session.send(SessionMessage.ZoomSet(ratio, final))
        Timings.mark("zoom.sent", "$ratio${if (final) " final" else ""}")
    }

    // The quick step a tap asked for until the photographer's camera echoes it, so that a late echo of an earlier step
    // does not take the readout, and the next tap, back to it (FR-7.1).
    private var pendingZoomStep: Float? = null

    // Whether this phone's pinch was held back by the photographer's (FR-7.6) until its fingers lift.
    private var zoomHeldBack = false

    // Kept by the session from the last `camera.zoom.hold`, so a screen that opens late still knows it.
    private val photographerHolds: Boolean get() = session.peerZoomHolder.value == Role.PHOTOGRAPHER

    private val exposureSender = StepSender<Int>(viewModelScope, SEND_INTERVAL_MS) { index, final ->
        session.send(SessionMessage.ExposureSet(index, final))
        Timings.mark("exposure.sent", "$index${if (final) " final" else ""}")
    }

    // Runs while a tap to focus waits for its answer (SubjectUiState.baseAwaited).
    private var baseWait: Job? = null

    // Runs while a press of the shutter waits for its answer (SubjectUiState.shutterWaiting).
    private var shutterWait: Job? = null

    // How many times this phone's shutter asked for a photo: the number of the press that waits, if one does.
    private var shutterPresses = 0

    // True when the press that waits asked to stop the photographer's countdown, not for a photo (FR-1.17).
    private var shutterCancels = false

    // Runs while a change of the timer waits for the photographer's phone to answer it ([TIMER_WAIT_MS]).
    private var timerWait: Job? = null

    // Takes down a number whose next second never came (showCountdown).
    private var countdownExpiry: Job? = null

    private val guideSender = StepSender<GuideState>(viewModelScope, SEND_INTERVAL_MS) { state, final ->
        val guideId = state.guideId ?: return@StepSender
        session.send(SessionMessage.GuideMove(guideId, state.cx, state.cy, state.height, final))
        Timings.mark("move.sent", "${state.cx} ${state.cy} ${state.height}${if (final) " final" else ""}")
    }

    // The last guide on the video, which Undo asks for again once it is removed (FR-7.2).
    private var lastGuideId: String? = null

    // The guide this phone's ✕ asked to remove, until it goes: its going is this phone's removal.
    private var removing: String? = null

    // The photographer said its ✕ (`camera.did`), whose guide may still be on the video until `guide.clear` comes.
    private var photographerRemoving = false

    private val _guideRemoved = Channel<GuideRemoval>(Channel.BUFFERED)

    /**
     * A ✕ took the guide off both phones, once it is gone from the video: "Guide removed" or "Dongje removed the guide",
     * with *Undo* (FR-7.2). Kept until the screen reads it, so a screen stopped meanwhile still offers *Undo*.
     */
    val guideRemoved: Flow<GuideRemoval> = _guideRemoved.receiveAsFlow()

    private val opacitySender = StepSender<GuideState>(viewModelScope, SEND_INTERVAL_MS) { state, final ->
        val guideId = state.guideId ?: return@StepSender
        session.send(SessionMessage.GuideOpacitySet(guideId, state.opacity, final))
        Timings.mark("opacity.sent", "${state.opacity}${if (final) " final" else ""}")
    }

    init {
        viewModelScope.launch {
            receivingPhoto.collect { receiving -> _uiState.update { it.copy(receivingPhoto = receiving) } }
        }
        viewModelScope.launch {
            offering.collect { id ->
                // A guide that waits for the photographer's phone cannot be edited yet: its moves would be dropped.
                _uiState.update { it.copy(offeredGuide = id, guideEditing = it.guideEditing && it.guide?.id != id) }
            }
        }
        viewModelScope.launch {
            sessionPhotos.collect { photos ->
                // The viewer goes with the session's photos, which end with it.
                _uiState.update {
                    it.copy(photos = photos.orEmpty(), photosOpen = it.photosOpen && !photos.isNullOrEmpty())
                }
            }
        }
        // The photographer may have echoed a state before this screen existed; later echoes come through incoming.
        session.peerCamera.value?.let { camera ->
            _uiState.update { it.copy(zoom = camera.zoom, exposure = camera.exposure, flash = camera.flash) }
        }
        // And their timer, which the first state of a session carries before this screen is there to hear it (#318).
        _uiState.update { it.copy(timer = photographersTimer()) }
        viewModelScope.launch {
            // Kept by the session, so a state sent before this screen existed, or before `incoming` was collected,
            // is here too. Outside a gesture it is also the value to show.
            session.peerCamera.collect { camera ->
                _uiState.update {
                    val aspect = camera?.aspect ?: AspectRatio.R4_3
                    val mirrored = (camera?.facing ?: CameraFacing.BACK) == CameraFacing.BACK
                    it.copy(
                        aspect = aspect,
                        mirrored = mirrored,
                        // The bar stands beside a point of the frame it was tapped in: another ratio or camera moves
                        // or turns the image under it, so it goes (FR-7.3).
                        brightnessOpen = it.brightnessOpen && aspect == it.aspect && mirrored == it.mirrored,
                        zoom = camera?.zoom?.let { zoom -> shownZoom(zoom, by = null, shown = it.zoom) } ?: it.zoom,
                        exposure = camera?.exposure?.takeUnless { exposureSender.adjusting } ?: it.exposure,
                        flash = camera?.flash ?: it.flash,
                    )
                }
            }
        }
        viewModelScope.launch {
            session.state.collect { state ->
                val connected = state as? SessionState.Connected
                if (connected == null) {
                    endInteractions()
                    countdownExpiry?.cancel()
                }
                _uiState.update {
                    it.copy(
                        connected = connected != null,
                        photographerName = connected?.peer?.displayName ?: it.photographerName,
                        // The bar is for a camera that can be reached; so is a countdown, whose end may not come.
                        brightnessOpen = it.brightnessOpen && connected != null,
                        countdown = it.countdown.takeIf { connected != null },
                    )
                }
            }
        }
        viewModelScope.launch {
            // Kept by the session, so it is there even if the message came before this screen existed.
            session.peerCapabilities.collect { caps ->
                val range = caps?.exposureRange ?: 0..0
                _uiState.update {
                    it.copy(
                        minZoom = caps?.minZoom ?: 1f,
                        maxZoom = caps?.maxZoom ?: 1f,
                        exposureRange = range,
                        exposureStep = caps?.exposureStep ?: 0f,
                        brightnessOpen = it.brightnessOpen && range.first < range.last,
                        supportsFocusPoint = caps?.supportsFocusPoint ?: false,
                        hasFlash = caps?.hasFlash ?: false,
                    )
                }
                val ui = _uiState.value
                if (!ui.canZoom) zoomSender.cancel()
                if (!ui.hasBrightness) exposureSender.cancel()
            }
        }
        viewModelScope.launch { session.peerZoomHolder.collect { onZoomHolder(it) } }
        viewModelScope.launch {
            session.incoming.collect { message ->
                if (message is SessionMessage.Focused) {
                    // The photographer's own tap, in their frame: on the rear camera's mirror it is at 1 − x here.
                    val (x, y) = message
                    if (x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) {
                        _photographerFocus.tryEmit(RemoteFocus(if (_uiState.value.mirrored) 1f - x else x, y))
                        sayFocused()
                    }
                    return@collect
                }
                if (message is SessionMessage.PhotographerDid) {
                    when (val action = message.toAction()) {
                        null -> Unit

                        // Not a line: a notice with Undo once the guide is gone, kept until the screen reads it.
                        PhotographerAction.GuideRemove -> if (_uiState.value.guide == null) {
                            _guideRemoved.trySend(GuideRemoval.PHOTOGRAPHER)
                        } else {
                            photographerRemoving = true
                        }

                        else -> _photographerLines.tryEmit(PhotographerLine.Did(action))
                    }
                    return@collect
                }
                if (message is SessionMessage.ShutterAnswer) {
                    // Only the press that still waits is answered: one given up already has said so, and its answer,
                    // come late, is not the next press's.
                    // A refusal is said for a press that asked for a photo and got neither it nor the timer.
                    val cancel = shutterCancels
                    if (message.press == shutterPresses && endShutterWait() &&
                        !message.taken && !message.timer && !cancel
                    ) {
                        _shutterRefusals.trySend(Unit)
                    }
                    return@collect
                }
                if (message is SessionMessage.Countdown) {
                    showCountdown(message.left)
                    return@collect
                }
                if (message !is SessionMessage.CameraStateUpdate) return@collect
                // The tap's answer, with the brightness the camera went back to. No other echo of this phone's is
                // taken for it: one of an earlier pinch or drag may still be on its way, with the old brightness.
                if (message.focused) endBaseWait()
                // While a pinch or the bar is moving its own steps rule the display; the echo wins once it ends.
                _uiState.update {
                    it.copy(
                        zoom = shownZoom(message.zoom, message.by, shown = it.zoom),
                        exposure = if (exposureSender.adjusting) it.exposure else message.exposure,
                        flash = message.flash,
                        timer = photographersTimer(),
                    )
                }
                // Whatever the photographer's phone says of its state answers a change of the timer asked here.
                timerWait?.cancel()
                timerWait = null
                if (message.by == Role.SUBJECT && message.final) {
                    Timings.mark("zoom.echo", "${message.zoom} exposure=${message.exposure} flash=${message.flash}")
                }
            }
        }
        // The mirror is written by GuideSyncer while the session runs; the overlay draws it (FR-6.7).
        viewModelScope.launch {
            mirror.guide.collect { guide ->
                val previous = _uiState.value.guide
                _uiState.update {
                    it.copy(
                        guide = guide,
                        guideEditing = it.guideEditing && guide != null,
                        // The same guide's thumbnail stays put away; another guide's photo is new, so it shows.
                        referenceHidden = it.referenceHidden && guide != null && guide.id == it.guide?.id,
                    )
                }
                // A gesture never crosses guide identities (Design 2.6.2): a removed or replaced guide ends it.
                if (guide?.id != previous?.id) {
                    opacitySender.cancel()
                    endGuideGesture()
                    if (guide == null && previous != null) {
                        when {
                            previous.id == removing -> _guideRemoved.trySend(GuideRemoval.THIS_PHONE)
                            photographerRemoving -> _guideRemoved.trySend(GuideRemoval.PHOTOGRAPHER)
                        }
                    }
                    removing = null
                    photographerRemoving = false
                }
                if (guide != null) lastGuideId = guide.id
            }
        }
        viewModelScope.launch {
            // The photographer's reference arrives after the guide's image and goes with it (Design 2.5.1).
            combine(mirror.guide, references.reference) { guide, reference ->
                reference?.photo?.takeIf { guide != null && reference.guideId == guide.id }
            }.collect { photo ->
                _uiState.update {
                    it.copy(reference = photo, referenceExpanded = it.referenceExpanded && photo === it.reference)
                }
            }
        }
        viewModelScope.launch {
            // A guide keeps its id on both phones and when it is saved, so the id tells whether it is saved here.
            combine(mirror.guide, savedGuides.saved) { guide, saved ->
                isFavorite(saved, guide)
            }
                .collect { saved -> _uiState.update { it.copy(guideSaved = saved) } }
        }
        tips?.let { syncer ->
            viewModelScope.launch {
                // A tip shows only with its own guide, whichever of the two arrives first (Design 2.5.1).
                combine(mirror.guide, syncer.mirror) { guide, shared -> shared?.takeIf { it.guideId == guide?.id } }
                    .collect { shared -> _uiState.update { it.copy(tip = nextSubjectTip(it.tip, shared)) } }
            }
        }
        poses?.let { syncer ->
            viewModelScope.launch {
                syncer.mirror.collect { set ->
                    val ready = set?.ready?.isNotEmpty() == true
                    _uiState.update { it.copy(poseSetId = set?.setId, poseStep = set?.step, posesReady = ready) }
                }
            }
        }
        confirms?.let { syncer ->
            viewModelScope.launch {
                syncer.mirror.collect { confirm -> _uiState.update { it.copy(confirmId = confirm?.confirmId) } }
            }
        }
        descriptions?.let { syncer ->
            viewModelScope.launch {
                syncer.mirror.collect { description ->
                    _uiState.update { it.copy(describeId = description?.descriptionId) }
                }
            }
        }
        viewModelScope.launch {
            mirror.state.collect { state ->
                _uiState.update {
                    it.copy(
                        // This phone's own gesture, a move or the opacity slider, is drawn until it ends; the
                        // photographer's echo then settles the guide (FR-7.2).
                        guideState = state.copy(
                            cx = if (guideSender.adjusting) it.guideState.cx else state.cx,
                            cy = if (guideSender.adjusting) it.guideState.cy else state.cy,
                            height = if (guideSender.adjusting) it.guideState.height else state.height,
                            opacity = if (opacitySender.adjusting) it.guideState.opacity else state.opacity,
                        ),
                        // A guide the photographer hid cannot be edited, so the mode ends and the pinch zooms again.
                        guideEditing = it.guideEditing && state.visible,
                        // Its tip is hidden with it, and closed: shown again, the guide brings back the button
                        // alone, since only the button opens the tip (FR-4.10).
                        tip = it.tip?.let { tip -> if (state.visible) tip else tip.copy(open = false) },
                    )
                }
            }
        }
    }

    /**
     * The tip button, the only way to open and close the tip, as on the Camera screen (FR-4.10): a tap opens it, and it
     * is no longer unseen; another tap while it is open closes it.
     */
    fun onTipButton() {
        _uiState.update { state ->
            val tip = state.tip ?: return@update state
            state.copy(tip = tip.copy(open = !tip.open, unseen = false))
        }
    }

    /**
     * *Save guide* (FR-6.16): keeps the guide on the video on this phone, to use again from *Add a pose guide*, as the
     * photographer's phone keeps it: the mask as received, the reference copy the thumbnail shows, the `anchor` and
     * `source` its image carried, and the mirror's state (Design 2.6.5), as a favorite; another tap makes it history
     * again, still in the library (#297).
     */
    fun onSaveGuide() {
        val ui = _uiState.value
        val guide = ui.guide ?: return
        if (ui.savingGuide) return
        // Without a reference copy yet, the outline is the saved guide's thumbnail.
        val kept = guide.copy(reference = ui.reference ?: guide.reference)
        val state = mirror.state.value
        _uiState.update { it.copy(savingGuide = true) }
        viewModelScope.launch {
            val notice = try {
                toggleSaved(savedGuides, kept, state)
            } finally {
                _uiState.update { it.copy(savingGuide = false) }
            }
            _guideSaveNotices.send(notice)
        }
    }

    /**
     * Whether Subject view should open a screen of the set [setId] by itself now, *Generating poses* or *Pick a pose*:
     * once per set, so Back keeps it closed and the button opens it again (Design 2.6.5); *Generating poses* goes on to
     * *Pick a pose* by itself.
     */
    fun claimSetOpening(setId: String): Boolean {
        if (setOpenedFor == setId) return false
        setOpenedFor = setId
        return true
    }

    /** Whether Subject view should open *Your reference* by itself for [confirmId] now: once per confirm, as a set. */
    fun claimConfirmOpening(confirmId: String): Boolean {
        if (confirmOpenedFor == confirmId) return false
        confirmOpenedFor = confirmId
        return true
    }

    /** Whether Subject view should open *Generate poses* by itself for [describeId] now: once per description. */
    fun claimDescribeOpening(describeId: String): Boolean {
        if (describeOpenedFor == describeId) return false
        describeOpenedFor = describeId
        return true
    }

    /** A tap on the thumbnail opens the session's photos over the video, at the newest (FR-6.14). */
    fun openPhotos() {
        _uiState.update { it.copy(photosOpen = it.photos.isNotEmpty()) }
    }

    /** The viewer's Back, or the system's, closes it; the video is there as it was. */
    fun closePhotos() {
        _uiState.update { it.copy(photosOpen = false) }
    }

    /**
     * The viewer's Delete, once asked (FR-6.14): the deleter drops the photo from the session's photos, and so from
     * the viewer and the thumbnail, or it stays, and [photoNotDeleted] says so.
     */
    fun deletePhoto(uri: Uri) {
        viewModelScope.launch {
            // The session's photos are Pix's own, which need no question of Android's.
            if (deleter.delete(uri) != PhotoDeletion.Deleted) _photoNotDeleted.trySend(Unit)
        }
    }

    /** A tap on the reference thumbnail enlarges it or makes it small again (FR-3.9). A hidden one stays small. */
    fun setReferenceExpanded(expanded: Boolean) {
        _uiState.update { it.copy(referenceExpanded = expanded && it.reference != null && !it.referenceHidden) }
    }

    /** A drag or TalkBack moves the thumbnail to [corner], on this phone only. */
    fun setReferenceCorner(corner: ReferenceCorner) {
        _uiState.update { it.copy(referenceCorner = corner) }
    }

    /** Puts the thumbnail away at its corner's side, small, or shows it again (#237), on this phone only. */
    fun setReferenceHidden(hidden: Boolean) {
        _uiState.update {
            val hides = hidden && it.reference != null
            it.copy(referenceHidden = hides, referenceExpanded = it.referenceExpanded && !hides)
        }
    }

    /** A drag let the thumbnail go: into [drop]'s corner, or hidden, and small, at that corner's side (#237). */
    fun onReferenceDropped(drop: ReferenceDrop) {
        _uiState.update {
            val hides = drop.hidden && it.reference != null
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
     * *Hide* or *Show* on the guide's panel (FR-7.2): asks the photographer's phone, whose echo hides or shows the guide
     * on both phones.
     */
    fun onHideToggle() {
        val ui = _uiState.value
        val guide = ui.guide ?: return
        if (!ui.connected || ui.guideWaiting) return
        session.send(SessionMessage.GuideVisibleSet(guide.id, !ui.guideState.visible))
        Timings.mark("guide.visible.sent", "${!ui.guideState.visible}")
    }

    /**
     * From the opacity slider (FR-7.2): the guide here takes the value at once, the photographer's phone gets it as
     * `guide.opacity.set` (steps at most every [SEND_INTERVAL_MS], the last at once), and its echo settles it.
     */
    fun onOpacityChange(opacity: Float, final: Boolean) {
        val ui = _uiState.value
        val guide = ui.guide
        if (guide == null || !ui.connected || !ui.guideState.visible || ui.guideWaiting) {
            if (final) opacitySender.cancel()
            return
        }
        if (!opacity.isFinite()) return
        val shown = ui.guideState.copy(
            guideId = guide.id,
            opacity = opacity.coerceIn(GuideState.MIN_OPACITY, GuideState.MAX_OPACITY),
        )
        _uiState.update { it.copy(guideState = shown) }
        if (final) opacitySender.finish(shown) else opacitySender.step(shown)
    }

    /** ✕ on the guide's panel (FR-7.2): the photographer's phone removes the guide, and both phones lose it. */
    fun onRemoveGuide() {
        val ui = _uiState.value
        val guide = ui.guide ?: return
        if (!ui.connected || ui.guideWaiting) return
        removing = guide.id
        session.send(SessionMessage.GuideRemove(guide.id))
        Timings.mark("guide.remove.sent", guide.id)
    }

    /**
     * *Undo* after either phone's ✕ (FR-7.2): asks the photographer's phone to put the last guide back where it was,
     * unless a guide is on the video again.
     */
    fun onUndoRemove() {
        val ui = _uiState.value
        val id = lastGuideId ?: return
        if (!ui.connected || ui.guide != null) return
        session.send(SessionMessage.GuideRestore(id))
        Timings.mark("guide.restore.sent", id)
    }

    /**
     * The guide-edit mode toggle; there is no mode without a visible guide, nor for a guide this phone offered that the
     * photographer's phone has not adopted yet, which would drop its moves.
     */
    fun setGuideEditing(on: Boolean) {
        _uiState.update {
            val editing = on && it.guide != null && it.guideState.visible && !it.guideWaiting
            // The video's touches are the guide's then, so the bar that a tap brought goes (FR-7.7).
            it.copy(guideEditing = editing, brightnessOpen = it.brightnessOpen && !editing)
        }
        if (!on) endGuideGesture()
    }

    /**
     * Ends this phone's guide gesture without a last value to send. No echo settles the guide then, so when [settle]
     * it goes back to the mirror's state at once: after a gesture that was still running, or always when the link
     * dropped, where a last value that was sent may not arrive either (Design 2.6.5).
     */
    private fun endGuideGesture(settle: Boolean = guideSender.adjusting) {
        guideSender.cancel()
        if (!settle) return
        val state = mirror.state.value
        // The opacity slider's own drag is drawn until it ends, as when the mirror changes.
        _uiState.update {
            val opacity = if (opacitySender.adjusting) it.guideState.opacity else state.opacity
            it.copy(guideState = state.copy(opacity = opacity))
        }
    }

    /**
     * From GuideOverlayView.onGesture while guide-edit mode is on (FR-7.2): the guide follows the finger here,
     * within the photographer's limits for its frame, and the photographer gets `guide.move` with the result; its
     * echoed `guide.state` settles the guide once the gesture is over (Design 2.6.5).
     */
    fun onGuideGesture(dx: Float, dy: Float, scale: Float, final: Boolean) {
        val ui = _uiState.value
        val guide = ui.guide
        if (guide == null || !ui.guideEditing || !ui.connected || ui.guideWaiting) {
            if (final) endGuideGesture()
            return
        }
        if (!dx.isFinite() || !dy.isFinite() || !scale.isFinite() || scale <= 0f) return
        val current = ui.guideState
        val requested = current.copy(
            guideId = guide.id,
            cx = current.cx + dx,
            cy = current.cy + dy,
            height = current.height * scale,
        )
        val moved = GuideGeometry.clamp(requested, guide.aspect, ui.aspect.frameAspect)
        _uiState.update { it.copy(guideState = moved) }
        if (final) guideSender.finish(moved) else guideSender.step(moved)
    }

    /**
     * One pinch step, or its last value when [final] (FR-7.1). The readout follows at once; steps go to the
     * photographer at most every [SEND_INTERVAL_MS] (the newest wins), the final value right away, and the echoed
     * `camera.state` decides what stays (Design 2.6.5).
     */
    fun onZoomGesture(ratio: Float, final: Boolean) {
        pendingZoomStep = null
        if (zoomHeldBack) {
            if (final) zoomHeldBack = false
            return
        }
        if (photographerHolds) {
            holdBack(gestureGoesOn = !final)
            return
        }
        zoomTo(ratio, final)
    }

    /**
     * The line about the photographer's focus, as rarely as Camera's notice about the subject's (FR-7.7): none for a
     * tap while it shows or in the quiet time after it, so taps in a row do not keep saying the same line.
     */
    private fun sayFocused() {
        val now = clock()
        val last = focusLineAt
        if (last != null &&
            now - last < SessionViewModel.FOCUS_NOTICE_MS + SessionViewModel.FOCUS_NOTICE_QUIET_MS
        ) {
            return
        }
        focusLineAt = now
        _photographerLines.tryEmit(PhotographerLine.Focused)
    }

    /**
     * The photographer's phone says whose pinch holds the zoom (FR-7.6, #236). When it is the photographer's, a pinch of
     * this phone that is running was held back there: it stops sending until its fingers lift.
     */
    private fun onZoomHolder(by: Role?) {
        if (by == Role.PHOTOGRAPHER && zoomSender.adjusting) holdBack(gestureGoesOn = true)
    }

    /**
     * The photographer's pinch holds the zoom: this phone's change is not sent, the readout follows the photographer's
     * applied zoom, and a label says who is zooming. A pinch that [gestureGoesOn] stays held back until its final value.
     * A pinch that already sent steps ends there with a cancel, which changes no zoom, since the photographer's phone
     * drops a pinch it held back until its end (`RemoteControlHandler`).
     */
    private fun holdBack(gestureGoesOn: Boolean) {
        val photographerZoom = session.peerCamera.value?.zoom
        if (zoomSender.adjusting) {
            val ratio = photographerZoom ?: _uiState.value.zoom
            session.send(SessionMessage.ZoomSet(ratio, final = true, cancel = true))
        }
        zoomSender.cancel()
        zoomHeldBack = gestureGoesOn
        photographerZoom?.let { zoom -> _uiState.update { it.copy(zoom = zoom) } }
        _toggleLabels.trySend(ToggleLabel.ZoomHeld)
    }

    /**
     * A tap on the zoom readout (FR-7.1): the next quick step of the photographer's camera, sent as a final value and
     * shown until its echo comes, whatever earlier steps echo meanwhile.
     */
    fun onZoomTap() {
        val ui = _uiState.value
        val step = nextZoomStep(quickZoomSteps(ui.minZoom, ui.maxZoom), ui.zoom) ?: return
        if (photographerHolds) {
            holdBack(gestureGoesOn = false)
            return
        }
        if (zoomTo(step, final = true)) {
            pendingZoomStep = step
            _toggleLabels.trySend(ToggleLabel.Zoom(step))
        }
    }

    /** Shows [ratio] and asks the photographer for it; false when the zoom cannot be changed. */
    private fun zoomTo(ratio: Float, final: Boolean): Boolean {
        val ui = _uiState.value
        if (!ratio.isFinite() || !ui.canZoom) {
            // A disabled or interrupted pinch must release echo suppression even if its final request is rejected.
            if (final) zoomSender.cancel()
            return false
        }
        val clamped = ratio.coerceIn(ui.minZoom, ui.maxZoom)
        _uiState.update { it.copy(zoom = clamped) }
        if (final) zoomSender.finish(clamped) else zoomSender.step(clamped)
        return true
    }

    /**
     * The zoom to show for an [echoed] one, [by] whom when known: the [shown] one while a pinch runs, or while a quick
     * step waits for its echo and this one, not the photographer's own, is of an earlier step.
     */
    private fun shownZoom(echoed: Float, by: Role?, shown: Float): Float {
        if (zoomSender.adjusting) return shown
        val pending = pendingZoomStep ?: return echoed
        if (by != Role.PHOTOGRAPHER && !isAtZoomStep(echoed, pending)) return shown
        pendingZoomStep = null
        return echoed
    }

    /** From the brightness slider (FR-7.3): an exposure index, shown at once and sent like a zoom step. */
    fun onExposureGesture(index: Int, final: Boolean) {
        val ui = _uiState.value
        if (!ui.connected || !ui.hasBrightness) {
            if (final) exposureSender.cancel()
            return
        }
        val clamped = index.coerceIn(ui.exposureRange)
        _uiState.update { it.copy(exposure = clamped) }
        if (final) exposureSender.finish(clamped) else exposureSender.step(clamped)
    }

    /**
     * A tap on the video at [x], [y] of the frame as this phone shows it, 0..1 (FR-7.7): asks the photographer's
     * camera to focus there, which also puts its brightness back to the photographer's base, as a tap on Camera does.
     * The rear camera's video is a mirror here, so x is turned back to the photographer's frame; the front camera's
     * is mirrored on both phones. False when nothing was asked ([SubjectUiState.canFocus], a point outside the frame).
     */
    fun onFocusTap(x: Float, y: Float): Boolean {
        val ui = _uiState.value
        if (!ui.canFocus) return false
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return false
        val framed = if (ui.mirrored) 1f - x else x
        session.send(SessionMessage.FocusSet(framed, y))
        Timings.mark("focus.sent", "$framed $y")
        // The answer is the `camera.state` with the base; a photographer that sends none is not waited for long.
        baseWait?.cancel()
        _uiState.update { it.copy(baseAwaited = true) }
        baseWait = viewModelScope.launch {
            delay(BASE_WAIT_MS)
            baseWait = null
            _uiState.update { it.copy(baseAwaited = false) }
        }
        return true
    }

    /** The tap's answer came, or nothing waits for it any longer. */
    private fun endBaseWait() {
        baseWait?.cancel()
        baseWait = null
        _uiState.update { it.copy(baseAwaited = false) }
    }

    /**
     * This phone's shutter (FR-7.8, #314): asks the photographer's phone for a photo, at once, and holds the shutter
     * until its answer or [SHUTTER_WAIT_MS]. While the photographer's countdown shows, the press asks to stop it
     * instead (FR-1.17), as a tap on Camera's shutter does. False when nothing was asked: not connected, or a press
     * still waits.
     */
    fun onShutter(): Boolean {
        val ui = _uiState.value
        if (!ui.connected || ui.shutterWaiting) return false
        // What the press means goes with it: it may arrive after the countdown ended, and must not take a photo then.
        val cancel = ui.countdown != null
        // Numbered, so only its own answer ends its wait: one that comes after the wait is for no later press.
        val press = ++shutterPresses
        shutterCancels = cancel
        session.send(SessionMessage.ShutterPress(press, cancel))
        Timings.mark("shutter.sent", if (cancel) "cancel" else null)
        _uiState.update { it.copy(shutterWaiting = true) }
        shutterWait = viewModelScope.launch {
            delay(SHUTTER_WAIT_MS)
            // No word of it in time: as far as this phone knows, no photo was taken. A cancel says nothing: the
            // countdown shows, or goes, by the photographer's own seconds.
            shutterWait = null
            _uiState.update { it.copy(shutterWaiting = false) }
            if (!cancel) _shutterRefusals.trySend(Unit)
        }
        return true
    }

    /** The press's answer came, or nothing waits for it any longer; true when a press was waiting. */
    private fun endShutterWait(): Boolean {
        val waiting = shutterWait ?: return false
        waiting.cancel()
        shutterWait = null
        _uiState.update { it.copy(shutterWaiting = false) }
        return true
    }

    fun setBrightnessOpen(open: Boolean) {
        _uiState.update { it.copy(brightnessOpen = open && it.hasBrightness) }
    }

    /** The camera menu under the top bar opens or closes (FR-1.17). */
    fun setMenuOpen(open: Boolean) {
        _uiState.update { it.copy(menuOpen = open) }
    }

    /**
     * The photographer's timer has [left] seconds before its photo, or 0 as it ended (`camera.timer`, FR-1.17). Each
     * second's message keeps the number up for [COUNTDOWN_STALE_MS] at most, so a number whose end never came, as
     * when the photographer's phone stopped, goes by itself.
     */
    private fun showCountdown(left: Int) {
        countdownExpiry?.cancel()
        val shown = left.takeIf { it > 0 }
        _uiState.update { it.copy(countdown = shown) }
        if (shown == null) return
        countdownExpiry = viewModelScope.launch {
            delay(COUNTDOWN_STALE_MS)
            _uiState.update { it.copy(countdown = null) }
        }
    }

    /**
     * The menu's Flash cycles the photographer's flash off, auto, on, like their own (FR-7.3); the echo confirms or
     * reverts it.
     */
    fun onFlashTap() {
        val ui = _uiState.value
        // Not while the photographer's timer counts down, as on their own phone (FR-1.17).
        if (!ui.connected || !ui.hasFlash || ui.countdown != null) return
        val next = when (ui.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        _uiState.update { it.copy(flash = next) }
        session.send(SessionMessage.FlashSet(next))
        Timings.mark("flash.sent", "$next")
    }

    /**
     * The menu's Timer cycles the photographer's timer off, 3 s, 10 s, like their own (FR-1.17, #318); the echo
     * confirms or reverts it, and no echo within [TIMER_WAIT_MS] reverts it too. Not while the timer counts down, as
     * on their phone.
     */
    fun onTimerTap() {
        val ui = _uiState.value
        if (!ui.connected || ui.countdown != null) return
        val next = ui.timer.next()
        _uiState.update { it.copy(timer = next) }
        session.send(SessionMessage.TimerSet(next.seconds))
        Timings.mark("timer.sent", "${next.seconds}")
        // A phone that does not know the request says nothing: the item then goes back to what it said last, or it
        // would show a timer that a press of the shutter does not get.
        timerWait?.cancel()
        timerWait = viewModelScope.launch {
            delay(TIMER_WAIT_MS)
            timerWait = null
            _uiState.update { it.copy(timer = photographersTimer()) }
        }
    }

    /**
     * The photographer's timer as their phone said it last, which the session keeps. Seconds this Pix does not offer,
     * from a later one, show as off, which this menu can name.
     */
    private fun photographersTimer(): ShutterTimer =
        ShutterTimer.ofSeconds(session.peerTimerSeconds.value) ?: ShutterTimer.OFF

    private fun endInteractions() {
        endBaseWait()
        endShutterWait()
        timerWait?.cancel()
        timerWait = null
        pendingZoomStep = null
        zoomHeldBack = false
        zoomSender.cancel()
        exposureSender.cancel()
        opacitySender.cancel()
        endGuideGesture(settle = true)
    }

    companion object {
        private const val SEND_INTERVAL_MS = 50L

        /**
         * How long a press of the shutter waits for its answer. The photographer's phone answers at once, so this is
         * the link's time alone; after it the press counts as not taken.
         */
        const val SHUTTER_WAIT_MS = 2_000L

        /** How long a change of the timer waits for the photographer's phone to answer it (#318). */
        const val TIMER_WAIT_MS = 2_000L

        /**
         * How long one second of the photographer's countdown stays without the next (FR-1.17): the second itself
         * (`CameraViewModel.COUNTDOWN_STEP_MS`), and as much again for the link.
         */
        const val COUNTDOWN_STALE_MS = 2_000L

        /** What the link may add to an answer's way, both directions together (NFR-7's 0.5 s for a remote action). */
        private const val LINK_ALLOWANCE_MS = 500L

        /**
         * How long a tap to focus waits for its answer: as long as the photographer's phone itself waits for its
         * camera before it answers (`RemoteControlHandler.ECHO_TIMEOUT_MS`), and the link's delay on top. Shorter, a
         * drag would start from the brightness before the tap while the answer was still on its way. An answer comes
         * in a tenth of a second or two as a rule; only an older photographer, which sends none, makes a drag begun
         * with the tap wait this long.
         */
        private const val BASE_WAIT_MS = RemoteControlHandler.ECHO_TIMEOUT_MS + LINK_ALLOWANCE_MS
    }
}
