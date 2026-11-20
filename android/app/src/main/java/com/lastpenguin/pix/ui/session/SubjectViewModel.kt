// AI-generated with ChatGPT Codex and Claude Code, 2026-11-20, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.PhoneTurns
import com.lastpenguin.pix.camera.PhotoDeleter
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
import com.lastpenguin.pix.session.PoseSetEnd
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.RemoteVideo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.SharedTip
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.camera.GuideSaveNotice
import com.lastpenguin.pix.ui.camera.ReferenceCorner
import com.lastpenguin.pix.ui.camera.ReferenceDrop
import com.lastpenguin.pix.ui.camera.ToggleLabel
import com.lastpenguin.pix.ui.camera.toggleSaved
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The photographer's tip of the guide on the video, as Subject view shows it (FR-4.10, #213): closed until the tip
 * button, whose dot says there is a tip not yet looked at; a touch anywhere closes it again.
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
    /** Whether the brightness slider is open above the readout. */
    val brightnessOpen: Boolean = false,
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
    /** Whether the guide on the video is among this phone's saved guides (FR-6.16): *Save guide* is filled then. */
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
) {
    /** The thumbnail's photo, none until the session's first. */
    val lastPhoto: Uri?
        get() = photos.lastOrNull()

    val canZoom: Boolean
        get() = connected && minZoom.isFinite() && maxZoom.isFinite() && minZoom > 0f && maxZoom > minZoom

    /** Whether the photographer's camera has a brightness range to set (FR-7.3). */
    val hasBrightness: Boolean
        get() = exposureRange.first < exposureRange.last
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
    private val deleter: PhotoDeleter = PhotoDeleter { false },
    /** How this phone is held, which its own controls' icons turn with; nothing is sent (FR-1.15). */
    private val phoneTurns: PhoneTurns = PhoneTurns.Fixed(),
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
    private val photographerZooming: Boolean get() = session.peerZoomHolder.value == Role.PHOTOGRAPHER

    private val exposureSender = StepSender<Int>(viewModelScope, SEND_INTERVAL_MS) { index, final ->
        session.send(SessionMessage.ExposureSet(index, final))
        Timings.mark("exposure.sent", "$index${if (final) " final" else ""}")
    }
    private val guideSender = StepSender<GuideState>(viewModelScope, SEND_INTERVAL_MS) { state, final ->
        val guideId = state.guideId ?: return@StepSender
        session.send(SessionMessage.GuideMove(guideId, state.cx, state.cy, state.height, final))
        Timings.mark("move.sent", "${state.cx} ${state.cy} ${state.height}${if (final) " final" else ""}")
    }

    init {
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
        viewModelScope.launch {
            // Kept by the session, so a state sent before this screen existed, or before `incoming` was collected,
            // is here too. Outside a gesture it is also the value to show.
            session.peerCamera.collect { camera ->
                _uiState.update {
                    it.copy(
                        aspect = camera?.aspect ?: AspectRatio.R4_3,
                        mirrored = (camera?.facing ?: CameraFacing.BACK) == CameraFacing.BACK,
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
                if (connected == null) endInteractions()
                _uiState.update {
                    it.copy(
                        connected = connected != null,
                        photographerName = connected?.peer?.displayName ?: it.photographerName,
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
                if (message !is SessionMessage.CameraStateUpdate) return@collect
                // While a pinch or the slider is moving its own steps rule the display; the echo wins once it ends.
                _uiState.update {
                    it.copy(
                        zoom = shownZoom(message.zoom, message.by, shown = it.zoom),
                        exposure = if (exposureSender.adjusting) it.exposure else message.exposure,
                        flash = message.flash,
                    )
                }
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
                if (guide?.id != previous?.id) guideSender.cancel()
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
                guide != null && saved.any { it.id == guide.id }
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
                        // This phone's own gesture is drawn until it ends; the photographer's echo then settles the
                        // guide. Hiding and opacity stay the photographer's even meanwhile (FR-7.2).
                        guideState = if (guideSender.adjusting) {
                            it.guideState.copy(visible = state.visible, opacity = state.opacity)
                        } else {
                            state
                        },
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

    /** The tip button: the tip opens, and is no longer unseen; another tap while it is open closes it (FR-4.10). */
    fun onTipButton() {
        _uiState.update { state ->
            val tip = state.tip ?: return@update state
            state.copy(tip = tip.copy(open = !tip.open, unseen = false))
        }
    }

    /** A touch anywhere on the screen ended: an open tip closes, as on the Camera screen. */
    fun onAnyTouch() {
        _uiState.update { state ->
            state.tip?.takeIf { it.open }?.let { state.copy(tip = it.copy(open = false)) } ?: state
        }
    }

    /**
     * *Save guide* (FR-6.16): keeps the guide on the video on this phone, to use again from *Add a pose guide*, as the
     * photographer's phone keeps it: the mask as received, the reference copy the thumbnail shows, the `anchor` and
     * `source` its image carried, and the mirror's state (Design 2.6.5). Not saved twice: another tap takes it out of
     * the saved guides again. When [SavedGuideStore.MAX_SAVED] are saved, the store deletes the oldest and the notice
     * says so.
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
            if (!deleter.delete(uri)) _photoNotDeleted.trySend(Unit)
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

    /** The guide-edit mode toggle; there is no mode without a visible guide. */
    fun setGuideEditing(on: Boolean) {
        _uiState.update { it.copy(guideEditing = on && it.guide != null && it.guideState.visible) }
        if (!on) guideSender.cancel()
    }

    /**
     * From GuideOverlayView.onGesture while guide-edit mode is on (FR-7.2): the guide follows the finger here,
     * within the photographer's limits for its frame, and the photographer gets `guide.move` with the result; its
     * echoed `guide.state` settles the guide once the gesture is over (Design 2.6.5).
     */
    fun onGuideGesture(dx: Float, dy: Float, scale: Float, final: Boolean) {
        val ui = _uiState.value
        val guide = ui.guide
        if (guide == null || !ui.guideEditing || !ui.connected) {
            if (final) guideSender.cancel()
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
        if (photographerZooming) {
            holdBack(gestureGoesOn = !final)
            return
        }
        zoomTo(ratio, final)
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
     * A pinch that already sent steps ends there with a final value of that zoom, since the photographer's phone drops
     * a pinch it held back up to its final value (`RemoteControlHandler`).
     */
    private fun holdBack(gestureGoesOn: Boolean) {
        val photographerZoom = session.peerCamera.value?.zoom
        if (zoomSender.adjusting && photographerZoom != null) {
            zoomSender.finish(photographerZoom)
        } else {
            zoomSender.cancel()
        }
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
        if (photographerZooming) {
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

    fun setBrightnessOpen(open: Boolean) {
        _uiState.update { it.copy(brightnessOpen = open && it.hasBrightness) }
    }

    /** The flash button cycles off, auto, on, like the photographer's own (FR-7.3); the echo confirms or reverts it. */
    fun onFlashTap() {
        val ui = _uiState.value
        if (!ui.connected || !ui.hasFlash) return
        val next = when (ui.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        _uiState.update { it.copy(flash = next) }
        session.send(SessionMessage.FlashSet(next))
        Timings.mark("flash.sent", "$next")
    }

    private fun endInteractions() {
        pendingZoomStep = null
        zoomHeldBack = false
        zoomSender.cancel()
        exposureSender.cancel()
        guideSender.cancel()
    }

    private companion object {
        const val SEND_INTERVAL_MS = 50L
    }
}
