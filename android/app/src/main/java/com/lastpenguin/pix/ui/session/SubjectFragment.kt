// AI-generated with ChatGPT Codex and Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PhotographerAction
import com.lastpenguin.pix.session.PoseStartStep
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.FlowProgressView
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.NoticeAction
import com.lastpenguin.pix.ui.NoticeAnchor
import com.lastpenguin.pix.ui.NoticeLength
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.camera.CountdownRing
import com.lastpenguin.pix.ui.camera.ExposureScale
import com.lastpenguin.pix.ui.camera.FocusBrightness
import com.lastpenguin.pix.ui.camera.FramePlace
import com.lastpenguin.pix.ui.camera.GuideSaveNotice
import com.lastpenguin.pix.ui.camera.PhotoViewer
import com.lastpenguin.pix.ui.camera.PinchZoomController
import com.lastpenguin.pix.ui.camera.PreviewArea
import com.lastpenguin.pix.ui.camera.REFERENCE_FLING_DP
import com.lastpenguin.pix.ui.camera.REFERENCE_MARGIN_DP
import com.lastpenguin.pix.ui.camera.REFERENCE_PEEK_DP
import com.lastpenguin.pix.ui.camera.REFERENCE_SMALL_DP
import com.lastpenguin.pix.ui.camera.ReferenceArea
import com.lastpenguin.pix.ui.camera.ReferenceDragController
import com.lastpenguin.pix.ui.camera.ReferencePlacement
import com.lastpenguin.pix.ui.camera.TOP_BAR_OVERHANG_DP
import com.lastpenguin.pix.ui.camera.ToggleLabel
import com.lastpenguin.pix.ui.camera.TrackedSliderController
import com.lastpenguin.pix.ui.camera.TurningIcons
import com.lastpenguin.pix.ui.camera.ViewfinderFrame
import com.lastpenguin.pix.ui.camera.ZOOM_ROW_MARGIN_DP
import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
import com.lastpenguin.pix.ui.camera.framePlace
import com.lastpenguin.pix.ui.camera.guideSaveIcon
import com.lastpenguin.pix.ui.camera.guideSaveTint
import com.lastpenguin.pix.ui.camera.isUnder
import com.lastpenguin.pix.ui.camera.keepTouches
import com.lastpenguin.pix.ui.camera.lerp
import com.lastpenguin.pix.ui.camera.photoThumbnail
import com.lastpenguin.pix.ui.camera.photoToView
import com.lastpenguin.pix.ui.camera.placeReference
import com.lastpenguin.pix.ui.camera.placeZoomRow
import com.lastpenguin.pix.ui.camera.previewArea
import com.lastpenguin.pix.ui.camera.previewAspect
import com.lastpenguin.pix.ui.camera.saveGuideTint
import com.lastpenguin.pix.ui.camera.setLabelIcon
import com.lastpenguin.pix.ui.camera.setReferenceActions
import com.lastpenguin.pix.ui.camera.showPhotoThumbnails
import com.lastpenguin.pix.ui.camera.showPinchModeLabel
import com.lastpenguin.pix.ui.camera.showReference
import com.lastpenguin.pix.ui.camera.showReferenceHandle
import com.lastpenguin.pix.ui.fadeTo
import com.lastpenguin.pix.ui.reveal
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.TutorialFragment
import com.lastpenguin.pix.ui.settings.followLook
import com.lastpenguin.pix.ui.settings.renderLatency
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import com.lastpenguin.pix.ui.settings.tutorialFor
import com.lastpenguin.pix.ui.showNotice
import com.lastpenguin.pix.ui.showNoticeOnScreen
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Subject view (R&S 6.6): the photographer's live video, fitted to their frame, with their name and *Leave*.
 * A pinch on the video zooms the photographer's camera while guide-edit mode is off, with the Camera screen's
 * readout (FR-7.1, AD-17); with the mode on, drags and pinches move the photographer's guide (FR-7.2). The
 * brightness bar and the camera menu's flash are the photographer's controls on this phone (FR-7.3), and the guide's
 * reference photo sits in a corner as on their Camera screen (FR-3.9). *Guide* opens Add a pose guide, whose guide
 * goes to the photographer (FR-6.15, Design 2.6.5), and *Save guide* keeps the guide on this phone (FR-6.16). While
 * the photographer describes or creates poses, picks one, or checks its guide, the video says so, and their *Generate
 * poses*, candidates, and the chosen pose's outline open on this phone's screens of the same steps (Design 2.6.5).
 * The bottom row is Camera's: *Guide* at the left, and the session's newest photo as a thumbnail at the right, empty
 * until the first, which opens the session's photos in a viewer over the screen, inside Pix, so the session goes on
 * (FR-6.14). In the middle, Camera's shutter asks the photographer's phone for a photo (FR-7.8, #314), and the line
 * right above it says whose camera this is and that it is live, or that a photo is coming (#271), for the whole
 * session, so no notice says it again.
 * System back closes the viewer, or else asks first (R&S 6.9). The first time the video shows, the tutorial's tips of
 * this screen come over it (FR-10.4). The video fades in, dims under a text, moves to the photographer's new ratio as
 * their frame does, and blinks at their shutter (Design 2.6.4). Leaving for another screen, the video goes at once and
 * the rest fades (Design 2.1).
 * Owner: Real-time (#8, #10, #57), with the guide from #9 and Iter3-P9; *Save guide*, the candidates, and the outline
 * Iter3-P16.
 */
class SubjectFragment :
    Fragment(R.layout.fragment_subject),
    NoticeAnchor {

    private val viewModel: SubjectViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private val zoomFormat = DecimalFormat("0.0")
    private var pinchZoom: PinchZoomController? = null
    private var opacitySlider: TrackedSliderController? = null

    // The focus box at this phone's tap and the brightness bar beside it, as on Camera (FR-7.3, FR-7.7).
    private var focusBrightness: FocusBrightness? = null
    private var renderer: SurfaceViewRenderer? = null
    private var watchdog: FrameWatchdog? = null
    private var referenceDrag: ReferenceDragController? = null
    private var leaveDialog: AlertDialog? = null

    // "Guide removed" with Undo, by either phone's ✕ (FR-7.2), until a guide is on the video again.
    private var undoNotice: Snackbar? = null

    // Whether the last render had a guide on the video: one that comes back takes the Undo down.
    private var guideShown = false

    // True while "Leave the session?" is up: it covers this screen without being another destination ([uncovered]).
    private val leaveAsked = MutableStateFlow(false)
    private var turningIcons: TurningIcons? = null
    private var photoViewer: PhotoViewer? = null
    private var toggleLabelJob: Job? = null

    /** The text of a label shown without a time of its own ([holdZoomingLabel]), until it is let go. */
    private var heldLabel: String? = null
    private var pinchModeJob: Job? = null

    // Set while a step waits for this screen to be the current destination again (inFront).
    private var frontWatch: Pair<NavController, NavController.OnDestinationChangedListener>? = null

    // Where the video is, and how much of it the top bar and the controls cover (previewArea).
    private var videoArea = PreviewArea(0f, 0f, 0f)

    // Where the video shows and for which ratio; the photographer's new ratio moves it there (reshape).
    private var shownAspect: AspectRatio? = null
    private var shown: FramePlace? = null
    private var move: ValueAnimator? = null

    // From the photographer's new ratio while their video shows until a frame of that shape arrives: the video stays
    // dimmed, and the grid and the guide wait for it.
    private var reshaping = false
    private var reshapeJob: Job? = null

    // How dark the video is meant to be (renderVideo): fully before its first frame, dimmed under a text, else clear.
    private var veil = 1f

    // Whether the photographer's video shows: connected, with frames coming.
    private val videoShowing = MutableStateFlow(false)

    // The session photo's thumbnail and the viewer's picture, off the main thread; a test stands in for MediaStore,
    // which has no photos on the JVM.
    @VisibleForTesting
    internal var thumbnailOf: (Uri) -> Bitmap? = { uri -> requireContext().photoThumbnail(uri) }

    @VisibleForTesting
    internal var photoOf: (Uri) -> Bitmap? = { uri -> requireContext().photoToView(uri) }

    // The shutter's ring while the photographer's timer counts down (FR-1.17), made with the first countdown shown.
    private var countdownRing: CountdownRing? = null

    @VisibleForTesting
    internal val shutterRing: CountdownRing? get() = countdownRing

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSubjectBinding.bind(view)
        binding.guideOverlay.editable = false
        binding.guideOverlay.onGesture = viewModel::onGuideGesture
        view.keepScreenOn = true

        val live = binding.liveView
        live.init(viewModel.video.eglContext, null)
        live.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        live.setEnableHardwareScaler(true)
        veil = 1f
        // The first frame and each new shape, at once rather than at the next check of the video.
        val frames = FrameWatchdog(live) { view.post { if (this.view != null) onFrameShape(binding) } }
        viewModel.video.attach(frames)
        renderer = live
        watchdog = frames
        // The video's place follows the screen's height and the bars' (R&S 6.2, 6.6), known only after layout.
        val placeVideo = View.OnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                view.post {
                    if (view.isAttachedToWindow) placeVideo(binding)
                }
            }
        }
        // The zoom row stays above the tip as it opens and closes.
        binding.bottomOverlays.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeZoomRow(binding) }
        // The camera's name is fitted to the width above the shutter, known only after layout.
        binding.bottomOverlays.addOnLayoutChangeListener { overlays, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                overlays.post { if (view != null) renderCaption(binding, viewModel.uiState.value) }
            }
        }
        binding.root.addOnLayoutChangeListener(placeVideo)
        binding.topBar.addOnLayoutChangeListener(placeVideo)
        binding.bottomPanel.addOnLayoutChangeListener(placeVideo)
        // The whole area takes the pinch, the margins beside a video of another shape too. A tap inside the image asks
        // the photographer's camera to focus there (FR-7.7), and a drag up or down sets its brightness (FR-7.3), as on
        // Camera; the pinch controller owns the video's touches, so it reports both, and neither in guide-edit mode.
        pinchZoom = PinchZoomController(
            binding.videoContainer,
            range = { viewModel.uiState.value.let { if (it.canZoom) it.minZoom..it.maxZoom else null } },
            current = { viewModel.uiState.value.zoom },
            onTap = { x, y -> onVideoTap(x, y) },
            onDrag = videoBrightnessDrag,
            onZoom = viewModel::onZoomGesture,
        )
        setupZoomAccessibility(binding.zoomReadout)
        setupFocusAccessibility(binding)
        // The icons turn with this phone, held as the subject holds it, and so do the zoom readout, the countdown, Guide
        // and Pick a pose with their labels, and the menu's items with theirs (FR-1.15); the other texts and the layout
        // stay.
        viewModel.watchPhoneTurn(viewLifecycleOwner)
        turningIcons = TurningIcons(
            controls = listOf(
                binding.cameraMenuButton,
                binding.tipButton,
                binding.hideGuideButton,
                binding.saveGuideButton,
                binding.removeGuideButton,
                binding.editGuideButton,
                binding.zoomReadout,
                binding.countdownNumber,
            ),
            views = listOf(
                binding.flashIndicator,
                binding.timerIndicator,
                binding.menuFlash,
                binding.menuTimer,
                binding.menuGrid,
                binding.menuSettings,
                binding.guideButton,
                binding.pickPoseButton,
                binding.galleryThumbnail,
            ),
        )
        binding.editGuideButton.setOnClickListener {
            viewModel.setGuideEditing(!viewModel.uiState.value.guideEditing)
            showPinchMode(binding, viewModel.uiState.value.guideEditing)
        }
        // The photographer's brightness from this phone (FR-7.3), on the Camera screen's bar: each step of a drag is
        // sent as it comes and the last once more as final.
        binding.exposureBar.describe = { ExposureScale.formatEv(it, viewModel.uiState.value.exposureStep) }
        binding.exposureBar.onChange = viewModel::onExposureGesture
        focusBrightness = FocusBrightness(binding.focusRing, binding.exposureBar, viewLifecycleOwner.lifecycleScope) {
            viewModel.setBrightnessOpen(false)
        }
        setupMenu(binding)
        // The pose flow's bar starts empty on the subject's first step (#210), whatever this phone showed before.
        FlowProgressView.shownStep = 0
        // The session's photos open over the video, inside Pix, so the session goes on (FR-6.14).
        binding.galleryButton.setOnClickListener { viewModel.openPhotos() }
        val photoViewer =
            PhotoViewer(
                binding.photoViewerHolder,
                viewLifecycleOwner.lifecycleScope,
                load = { photoOf(it) },
                thumbnail = { thumbnailOf(it) },
                onBack = viewModel::closePhotos,
                onDelete = viewModel::deletePhoto,
                opensFrom = binding.galleryButton,
            )
        this.photoViewer = photoViewer
        binding.guideButton.setOnClickListener { findNavController().navigate(R.id.action_subject_to_addGuide) }
        // The guide's panel, as on Camera (FR-7.2): Hide, the opacity, and ✕ ask the photographer's phone; ♥ is this
        // phone's own (FR-6.16).
        binding.hideGuideButton.setOnClickListener { viewModel.onHideToggle() }
        binding.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        binding.saveGuideButton.setOnClickListener { viewModel.onSaveGuide() }
        opacitySlider = TrackedSliderController(
            binding.opacitySlider,
            { getString(R.string.guide_opacity_percent, (it * 100f).roundToInt()) },
            viewModel::onOpacityChange,
        )
        setupTip(binding)
        // This phone's shutter (FR-7.8, #314): the photographer's phone takes the photo and tells of it as of any
        // other, so the blink, "Receiving photo…", and the saved photo follow as they do for the photographer's own.
        binding.shutterButton.setOnClickListener { viewModel.onShutter() }
        binding.pickPoseButton.setOnClickListener {
            val ui = viewModel.uiState.value
            when {
                confirming(ui) -> openConfirm()
                describing(ui) -> openDescribe()
                generating(ui) -> openGenerating()
                else -> openPick()
            }
        }

        setupReference(binding)

        // A session that ended while Add a pose guide was open has no transition left for this screen to see.
        (sessionViewModel.state.value as? SessionState.Ended)?.let { view.post { onTransition(it) } }

        // Leave asks first, as Back does (#298); Settings is in the camera menu (setupMenu).
        binding.leaveButton.setOnClickListener { confirmLeave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val ui = viewModel.uiState.value
            when {
                ui.photosOpen -> viewModel.closePhotos()
                ui.menuOpen -> viewModel.setMenuOpen(false)
                else -> confirmLeave()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.phoneTurn.collect { turn ->
                        turningIcons?.turnTo(turn)
                        photoViewer.turnTo(turn)
                    }
                }
                // The grid as this phone's Settings says (FR-1.9), and the menu's item for it (FR-1.17); Subject view
                // has no horizon bar.
                launch {
                    settingsViewModel.showGrid.collect {
                        binding.compositionOverlay.showGrid = it
                        renderMenu(binding, viewModel.uiState.value)
                    }
                }
                // The photographer's guide in this phone's own color and thickness (FR-3.10).
                launch {
                    binding.guideOverlay.followLook(settingsViewModel.guideLook, viewModel.uiState.map { it.guide })
                }
                launch {
                    // Either phone's ✕ (FR-7.2), as the photographer's phone says it too, with the same Undo; none once a
                    // guide is on the video again, as after a screen that was stopped meanwhile.
                    viewModel.guideRemoved.collect { removal ->
                        val ui = viewModel.uiState.value
                        if (ui.guide != null) return@collect
                        offerUndo(
                            when (removal) {
                                GuideRemoval.THIS_PHONE -> getString(R.string.guide_removed_from_camera)

                                GuideRemoval.PHOTOGRAPHER ->
                                    ui.photographerName?.let { getString(R.string.remote_guide_remove_notice, it) }
                                        ?: getString(R.string.guide_removed_from_camera)
                            },
                        )
                    }
                }
                launch {
                    viewModel.photoNotDeleted.collect {
                        showNotice(R.string.photo_not_deleted)
                    }
                }
                launch {
                    combine(settingsViewModel.showLatency, sessionViewModel.state, sessionViewModel.latencyMs, ::Triple)
                        .collect { (show, state, latency) -> binding.latencyChip.renderLatency(show, state, latency) }
                }
                launch {
                    viewModel.uiState.collect { ui ->
                        binding.liveDot.contentDescription = ui.photographerName
                            ?.let { getString(R.string.subject_title, it) }
                            ?: getString(R.string.subject_title_placeholder)
                        reshape(binding, ui.aspect)
                        renderMirror(binding, ui.mirrored)
                        renderReference(binding, ui)
                        // The video fits itself to the stream; the grid and the guide fit to the same frame (AD-10).
                        // 1:1 comes in the 4:3 around it, which is dimmed outside the frame, as on Camera (R&S 6.2).
                        binding.compositionOverlay.frameAspect = ui.aspect.frameAspect
                        binding.compositionOverlay.dimOutside = ui.aspect.previewAspect != ui.aspect.frameAspect
                        binding.guideOverlay.frameAspect = ui.aspect.frameAspect
                        binding.guideOverlay.render(ui.guide, ui.guideState)
                        renderOverlays(binding, ui)
                        // Guide-edit mode (AD-17): the overlay owns the video's touches while on; the pinch zooms while off.
                        binding.guideOverlay.editable = ui.guideEditing
                        binding.guideOverlay.filled = ui.guideEditing
                        pinchZoom?.enabled = !ui.guideEditing
                        // The toggle sits beside the readout while a guide is on the video; a hidden guide cannot be edited.
                        binding.editGuideButton.isVisible = ui.guide != null
                        // Checked before enabled: MaterialButton ignores setChecked while it is disabled, so a
                        // hidden guide would leave the toggle drawn as on (found by SubjectFragmentRenderTest).
                        binding.editGuideButton.isChecked = ui.guideEditing
                        // Nor while a guide this phone offered waits for the photographer's phone (FR-7.2).
                        binding.editGuideButton.isEnabled = ui.connected && ui.guideState.visible && !ui.guideWaiting
                        if (ui.guide == null) binding.pinchModeLabel.isVisible = false
                        // Guide-colored once a guide is on the video; it then replaces the guide on both phones.
                        binding.guideButton.isActivated = ui.guide != null
                        binding.guideButton.isEnabled = ui.connected
                        binding.guideButton.contentDescription = getString(
                            if (ui.guide == null) R.string.guide_add_description else R.string.guide_change_description,
                        )
                        // A guide on the video again, put back or another: nothing is left to undo.
                        if (ui.guide != null && !guideShown) dismissUndo()
                        guideShown = ui.guide != null
                        // The tip first: whether the guide's panel fits depends on it.
                        renderTip(binding, ui)
                        renderGuideControls(binding, ui)
                        binding.galleryButton.isEnabled = ui.lastPhoto != null
                        photoViewer.render(ui.photos, ui.photosOpen)
                        renderPoses(binding, ui)
                        renderCaption(binding, ui)
                        renderShutter(binding, ui)
                        renderCameraControls(binding, ui)
                        val ratio = zoomFormat.format(ui.zoom)
                        binding.zoomReadout.text = getString(R.string.zoom_ratio, ratio)
                        binding.zoomReadout.contentDescription = getString(R.string.camera_zoom_description, ratio)
                        binding.zoomReadout.isEnabled = ui.canZoom
                        if (!ui.canZoom) pinchZoom?.finishInteraction()
                    }
                }
                launch {
                    showPhotoThumbnails(viewModel.uiState.map { it.lastPhoto }, binding.galleryThumbnail) {
                        thumbnailOf(it)
                    }
                }
                // Its tutorial tips once the photographer's video shows, a moment later so the video comes first, and not
                // under another screen (FR-10.4).
                launch {
                    showTutorialWhenDue(
                        settingsViewModel.tutorialFor(TutorialScreen.SUBJECT_VIEW, videoShowing),
                        TutorialFragment.AFTER_PICTURE_MS,
                    ) { findNavController().currentDestination?.id == R.id.subjectFragment }
                }
                launch {
                    while (true) {
                        renderVideo(binding)
                        delay(STALL_CHECK_MS)
                    }
                }
                // The photographer's shutter, when it is pressed rather than when the photo arrives (FR-1.5).
                launch { sessionViewModel.shutters.collect { blinkVideo(binding.videoScrim) } }
                launch {
                    sessionViewModel.transitions.collect { state -> onTransition(state) }
                }
                launch {
                    sessionViewModel.guideOfferFailed.collect {
                        showNotice(R.string.guide_offer_failed)
                    }
                }
                launch {
                    viewModel.guideSaveNotices.collect { notice ->
                        val text = when (notice) {
                            GuideSaveNotice.SAVED -> getString(R.string.guide_saved_notice)
                            GuideSaveNotice.REMOVED -> getString(R.string.guide_removed_notice)
                            GuideSaveNotice.FAILED -> getString(R.string.guide_save_failed_notice)
                        }
                        showToggleLabel(binding, text, guideSaveIcon(notice), guideSaveTint(notice))
                    }
                }
                launch { viewModel.photographerFocus.collect { showPhotographerFocus(binding, it) } }
                // What the photographer did on their own camera, said where this screen's labels show (FR-7.4, #292).
                launch { viewModel.photographerLines.collect { showPhotographerLine(binding, it) } }
                // What became of this phone's own asks, each in a line of a label's time: said only while this screen
                // is what its user looks at. Under Add a pose guide, another screen, or the photos, the line would
                // come and go unseen, so it is left where it waits until the screen is uncovered.
                launch {
                    uncovered().collectLatest { shown ->
                        if (!shown) return@collectLatest
                        coroutineScope {
                            // The situation this phone picked for AI poses (FR-6.15, #276).
                            launch { sessionViewModel.poseStartAnswers.collect { showPoseStart(binding, it) } }
                            // A press that took no photo (FR-7.8, #314).
                            launch {
                                viewModel.shutterRefusals.collect {
                                    showToggleLabel(
                                        binding,
                                        getString(R.string.subject_shutter_refused),
                                        R.drawable.ic_alert_small,
                                        R.color.pix_text,
                                    )
                                }
                            }
                        }
                    }
                }
                launch {
                    // The photographer's pinch is named from its first step to its end; the ratio's line replaces it.
                    viewModel.photographerZooming.collect { zooming ->
                        if (zooming) holdZoomingLabel(binding) else letHeldLabelGo(binding)
                    }
                }
                launch {
                    viewModel.toggleLabels.collect { label ->
                        when (label) {
                            is ToggleLabel.Zoom -> showToggleLabel(
                                binding,
                                getString(R.string.zoom_ratio, DecimalFormat("0.#").format(label.ratio)),
                                R.drawable.ic_zoom_small,
                                R.color.pix_accent,
                            )

                            // The photographer's pinch holds the zoom (FR-7.6, #236). While it runs the line is up
                            // already and stays; after it, a held-back pinch of this phone still says so for a while.
                            ToggleLabel.ZoomHeld -> {
                                val name = viewModel.uiState.value.photographerName ?: return@collect
                                if (viewModel.photographerZooming.value) {
                                    holdZoomingLabel(binding)
                                } else {
                                    showToggleLabel(
                                        binding,
                                        getString(R.string.zoom_held_label, name),
                                        R.drawable.ic_zoom_small,
                                        R.color.pix_text,
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }
                }
                launch {
                    // A set that ended while this screen is in front; its Pick a pose says so itself while open.
                    viewModel.poseSetEnds.collect { end ->
                        poseSetEndNotice(requireContext(), end, viewModel.uiState.value.photographerName)?.let {
                            showNotice(it)
                        }
                    }
                }
                launch {
                    // Generate poses that the photographer closed while this screen is in front.
                    viewModel.describeEnds.collect { end ->
                        describeEndNotice(requireContext(), end, viewModel.uiState.value.photographerName)?.let {
                            showNotice(it)
                        }
                    }
                }
                launch {
                    // A confirm that ended while this screen is in front; its Your reference says so itself while open.
                    viewModel.confirmEnds.collect { end ->
                        confirmEndNotice(requireContext(), end, viewModel.uiState.value.photographerName)?.let {
                            showNotice(it)
                        }
                    }
                }
                launch {
                    sessionViewModel.mobileDataPending.collect { pending ->
                        if (!pending) return@collect
                        showNotice(R.string.mobile_data_notice, NoticeLength.LONG)
                        sessionViewModel.mobileDataNoticeShown()
                    }
                }
            }
        }
    }

    override fun onPause() {
        pinchZoom?.finishInteraction()
        opacitySlider?.finishInteraction()
        focusBrightness?.finishDrag()
        super.onPause()
    }

    /** Where previewArea puts the photographer's image of [aspect] on this screen, as on theirs (R&S 6.2, 6.6). */
    private fun placeFor(binding: FragmentSubjectBinding, aspect: AspectRatio): FramePlace? {
        val root = binding.root
        if (root.width == 0 || root.height == 0) return null
        return framePlace(
            root.width.toFloat(),
            root.height.toFloat(),
            binding.topBar.height - TOP_BAR_OVERHANG_DP * resources.displayMetrics.density,
            binding.bottomPanel.height.toFloat(),
            aspect,
        )
    }

    /** After the screen or a bar changed size: the video in its ratio's place at once, a move ended. */
    private fun placeVideo(binding: FragmentSubjectBinding) {
        move?.end()
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        val place = placeFor(binding, aspect) ?: return
        shownAspect = aspect
        showPlace(binding, place)
    }

    /**
     * Shows the video in [aspect]'s place. The photographer's new ratio while their video shows moves there in
     * [Motion.MOVE_MS], as their own frame does (Design 2.6.4): the last frame keeps fitting the moving area, dimmed,
     * and the grid and the guide wait until a frame of the new shape arrives or [RESHAPE_TIMEOUT_MS] has passed.
     * Otherwise, and while animations are off, the video is there at once.
     */
    private fun reshape(binding: FragmentSubjectBinding, aspect: AspectRatio) {
        if (aspect == shownAspect) return
        val to = placeFor(binding, aspect) ?: return
        move?.end()
        val from = shown
        shownAspect = aspect
        val showing = sessionViewModel.state.value is SessionState.Connected && watchdog?.hasFrame() == true
        if (from == null || !showing) {
            showPlace(binding, to)
            return
        }
        reshaping = true
        reshapeJob?.cancel()
        reshapeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(RESHAPE_TIMEOUT_MS)
            endReshape(binding)
        }
        renderVideo(binding)
        if (Motion.on()) {
            move = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = Motion.MOVE_MS
                interpolator = Motion.STANDARD
                addUpdateListener { step ->
                    if (view != null) showPlace(binding, lerp(from, to, step.animatedValue as Float))
                }
                doOnEnd { if (move === it) move = null }
                start()
            }
        } else {
            showPlace(binding, to)
        }
        // A frame of the new shape that came first.
        if (abs((watchdog?.shape ?: 0f) - aspect.previewAspect) < SHAPE_TOLERANCE) endReshape(binding)
    }

    /** The photographer's new shape came, or its time is up: the video clears, and the grid and the guide return. */
    private fun endReshape(binding: FragmentSubjectBinding) {
        if (!reshaping) return
        reshaping = false
        reshapeJob?.cancel()
        reshapeJob = null
        for (overlay in listOf(binding.guideOverlay, binding.compositionOverlay)) {
            overlay.alpha = 0f
            overlay.fadeTo(1f, Motion.FADE_MS)
        }
        renderOverlays(binding, viewModel.uiState.value)
        renderVideo(binding)
    }

    /**
     * The rear camera's video as a mirror (#201), so the subject moves to the guide the way they see themselves. The
     * renderer flips the video, since a SurfaceView ignores its own scale; the grid, the guide, and the reference photo
     * flip as views. A touch reaches the flipped guide in its own coordinates, so a drag still moves it under the
     * finger, and the guide sent and received stays in the photographer's coordinates. The reference photo's drag
     * reads the screen's coordinates, which the flip leaves alone.
     */
    private fun renderMirror(binding: FragmentSubjectBinding, mirrored: Boolean) {
        renderer?.setMirror(mirrored)
        val scale = if (mirrored) -1f else 1f
        // The reference photo's flip is set with its size, which is its scale too (showReference).
        for (view in listOf(binding.compositionOverlay, binding.guideOverlay)) {
            view.scaleX = scale
        }
    }

    /** The grid while connected and the guide, except while the photographer's new ratio comes. */
    private fun renderOverlays(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        binding.compositionOverlay.isVisible = ui.connected && !reshaping
        binding.guideOverlay.isVisible = !reshaping
    }

    /** The first frame, or one of a new shape: the video shows at once, and a new ratio's wait ends with its shape. */
    private fun onFrameShape(binding: FragmentSubjectBinding) {
        val shape = watchdog?.shape ?: return
        // The stream has its area's shape: 1:1 comes in the 4:3 around it (CameraFrameSource.streamCrop).
        if (abs(shape - viewModel.uiState.value.aspect.previewAspect) < SHAPE_TOLERANCE) endReshape(binding)
        renderVideo(binding)
    }

    /**
     * Puts the video at [place] (R&S 6.2, 6.6): the area's margins, the live light, and the zoom row at the bottom of
     * its image. The bottom panel gets a dark gradient while it lies over the video.
     */
    private fun showPlace(binding: FragmentSubjectBinding, place: FramePlace) {
        shown = place
        videoArea = place.area
        val container = binding.videoContainer
        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        val top = place.area.top.roundToInt()
        val bottom = (binding.root.height - place.area.bottom).roundToInt()
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            container.layoutParams = params
        }
        binding.bottomPanel.setBackgroundResource(if (place.area.underControls > 0f) R.drawable.bg_scrim_bottom else 0)
        binding.liveDot.placeAt(place.frame.copy(top = place.frame.top - place.area.top))
        placeZoomRow(binding)
    }

    /** The zoom row at the bottom of the video as it shows (1:1 inside the 4:3 area), above the slider and Guide. */
    private fun placeZoomRow(binding: FragmentSubjectBinding) {
        val place = shown ?: return
        val area = place.area
        if (area.bottom <= area.top) return
        val imageBottom = minOf(place.frame.bottom, area.bottom - area.underControls)
        val margin = ZOOM_ROW_MARGIN_DP * resources.displayMetrics.density
        placeZoomRow(binding.zoomRow, binding.bottomOverlays, imageBottom, margin, binding.noticeLine)
        binding.pinchModeLabel.translationY = binding.zoomRow.translationY
    }

    /**
     * "Connecting…" until the session is up and its first frame has arrived; "camera paused" only when frames stop
     * after that (Design 2.8). A session is Connected before WebRTC delivers the first frame, so counting the stall
     * from the watchdog's creation showed "paused" before any video. While the link reconnects, "Reconnecting…" over
     * the last frame (Design 2.6.7). While the photographer creates poses or picks one, its camera is off, and the
     * text says why. The video is dark before its first frame and dims under any of these texts and while the
     * photographer's new ratio comes (Design 2.6.4).
     */
    private fun renderVideo(binding: FragmentSubjectBinding) {
        val frames = watchdog ?: return
        val state = sessionViewModel.state.value
        val connected = state is SessionState.Connected
        val reconnecting = state is SessionState.Reconnecting
        val streaming = connected && frames.hasFrame()
        val stalled = streaming && frames.nanosSinceLastFrame() > STALL_NANOS
        val poses = poseStatus(viewModel.uiState.value).takeIf { connected }
        val text = !streaming || stalled || poses != null
        binding.connectingText.isVisible = text
        binding.liveDot.isVisible = streaming && !stalled
        // Not while a pose step's text or the session photo covers the video, which then is not what the tips show.
        videoShowing.value = streaming && !stalled && poses == null && !viewModel.uiState.value.photosOpen
        renderShutter(binding, viewModel.uiState.value)
        binding.connectingText.text = when {
            reconnecting -> getString(R.string.reconnecting)
            !connected || (!frames.hasFrame() && poses == null) -> getString(R.string.connecting)
            poses != null -> poses
            else -> getString(R.string.camera_paused)
        }
        veilVideo(
            binding.videoScrim,
            when {
                !frames.hasFrame() -> 1f
                text || reshaping -> VIDEO_DIM
                else -> 0f
            },
        )
    }

    /** Takes the video's dim to [level]: it clears in [Motion.FADE_MS] and darkens in [Motion.DIM_MS]. */
    private fun veilVideo(scrim: View, level: Float) {
        if (level == veil) return
        val clearing = level < veil
        veil = level
        if (level > 0f) scrim.isVisible = true
        scrim.fadeTo(level, if (clearing) Motion.FADE_MS else Motion.DIM_MS) {
            if (veil == 0f) scrim.isVisible = false
        }
    }

    /** The photographer's shutter (photo.shutter): the video blinks as their preview does, unless it is dimmed. */
    private fun blinkVideo(scrim: View) {
        if (!Motion.on() || veil != 0f) return
        scrim.alpha = 0f
        scrim.isVisible = true
        scrim.fadeTo(SHUTTER_DIM, Motion.BLINK_MS / 3) {
            scrim.fadeTo(0f, Motion.BLINK_MS * 2 / 3) { if (veil == 0f) scrim.isVisible = false }
        }
    }

    /**
     * The photographer's reference photo (FR-3.9), as on their Camera screen: a tap enlarges it or makes it small,
     * and a drag drops it into the nearest corner of this phone's video, or hides it at that corner's side (#237),
     * none of which the photographer sees.
     */
    private fun setupReference(binding: FragmentSubjectBinding) {
        val thumbnail = binding.referenceThumbnail
        val density = resources.displayMetrics.density
        referenceDrag = ReferenceDragController(
            thumbnail,
            area = { referenceArea(binding) },
            previewWidth = { binding.videoContainer.width },
            peek = (REFERENCE_PEEK_DP * density).roundToInt(),
            flingSpeed = REFERENCE_FLING_DP * density,
            onTap = viewModel::onReferenceTap,
        ) { drop ->
            drop?.let(viewModel::onReferenceDropped)
            renderReference(binding, viewModel.uiState.value)
        }.also { it.listenOn(binding.referenceHandle) }
        thumbnail.setReferenceActions(
            expanded = { viewModel.uiState.value.referenceExpanded },
            corner = { viewModel.uiState.value.referenceCorner },
            hidden = { viewModel.uiState.value.referenceHidden },
            moveTo = viewModel::setReferenceCorner,
            setHidden = viewModel::setReferenceHidden,
        )
        // The corners follow the video's size and the slider above Guide, known only after layout.
        val relayout = View.OnLayoutChangeListener {
                _,
                left,
                top,
                right,
                bottom,
                oldLeft,
                oldTop,
                oldRight,
                oldBottom,
            ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                thumbnail.post { if (thumbnail.isAttachedToWindow) renderReference(binding, viewModel.uiState.value) }
            }
        }
        binding.videoContainer.addOnLayoutChangeListener(relayout)
        binding.bottomOverlays.addOnLayoutChangeListener(relayout)
    }

    /**
     * The thumbnail in its corner, small or enlarged, or hidden at that corner's side with its handle over the strip
     * (#237), while the photographer's reference photo is here.
     */
    private fun renderReference(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val container = binding.videoContainer
        val dragging = referenceDrag?.isDragging == true
        var placed: ReferencePlacement? = null
        binding.referenceThumbnail.showReference(ui.reference, mirrored = ui.mirrored) { photo ->
            if (dragging || container.width == 0) {
                null
            } else {
                val density = resources.displayMetrics.density
                placeReference(
                    aspect = photo.width.toFloat() / photo.height,
                    expanded = ui.referenceExpanded,
                    corner = ui.referenceCorner,
                    smallLongSide = (REFERENCE_SMALL_DP * density).roundToInt(),
                    previewWidth = container.width,
                    area = referenceArea(binding),
                    hidden = ui.referenceHidden,
                    peek = (REFERENCE_PEEK_DP * density).roundToInt(),
                ).also { placed = it }
            }
        }
        // The handle stays as it is through a drag, which may have begun on it.
        if (!dragging) {
            binding.referenceHandle.showReferenceHandle(
                placement = placed.takeIf { ui.reference != null && ui.referenceHidden },
                corner = ui.referenceCorner,
                previewWidth = container.width,
                area = referenceArea(binding),
            )
        }
    }

    /** Inside the video's margins and above the zoom row, the slider, and Guide. */
    private fun referenceArea(binding: FragmentSubjectBinding): ReferenceArea {
        val margin = (REFERENCE_MARGIN_DP * resources.displayMetrics.density).roundToInt()
        val container = binding.videoContainer
        val zoomTop = (binding.zoomRow.top + binding.zoomRow.translationY).roundToInt()
        val bottom = minOf(zoomTop - container.top, container.height - videoArea.underControls.roundToInt()) - margin
        return ReferenceArea(margin, margin, container.width - margin, bottom)
    }

    override fun onDestroyView() {
        // A dialog belongs to the activity: one left open would leave a screen that is gone.
        leaveDialog?.dismiss()
        leaveDialog = null
        dismissUndo()
        guideShown = false
        countdownRing?.stop()
        countdownRing = null
        turningIcons?.stop()
        turningIcons = null
        photoViewer?.stop()
        photoViewer = null
        move?.let {
            move = null
            it.cancel()
        }
        shown = null
        shownAspect = null
        reshaping = false
        stopWatchingFront()
        referenceDrag = null
        pinchZoom?.finishInteraction()
        pinchZoom = null
        opacitySlider?.finishInteraction()
        opacitySlider = null
        focusBrightness?.clear()
        focusBrightness = null
        // The bar stood beside a box of this view, on its own time: a view made again shows neither.
        viewModel.setBrightnessOpen(false)
        watchdog?.let(viewModel.video::detach)
        watchdog = null
        renderer?.release()
        renderer = null
        super.onDestroyView()
    }

    /**
     * Going to another screen (Design 2.1), the video goes at once: its surface takes no view alpha, so it would stay
     * bright while the rest of the screen fades, then vanish. The ground under it fades with the rest.
     */
    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (!enter && nextAnim != 0) renderer?.isInvisible = true
        return super.onCreateAnimator(transit, enter, nextAnim)
    }

    /**
     * The photographer's tip (FR-4.10, #213): only the tip button opens and closes it, and a touch anywhere else, on the
     * banner too, leaves it as it is. A touch anywhere closes the camera menu.
     */
    private fun setupTip(binding: FragmentSubjectBinding) {
        binding.root.onTouchEnd = { event ->
            // The camera menu closes at a touch anywhere but on it or its button, which opens and closes it (FR-1.17).
            if (!binding.cameraMenuButton.isUnder(event, binding.root) &&
                !binding.cameraMenu.isUnder(event, binding.root)
            ) {
                viewModel.setMenuOpen(false)
            }
        }
        binding.tipButton.setOnClickListener { viewModel.onTipButton() }
        // A tap on the open tip neither closes it nor focuses the photographer's camera through it (FR-7.7).
        binding.tipBanner.keepTouches()
    }

    /**
     * The photographer's tip of the guide on the video (FR-4.10, #213): its button while that guide has one and shows,
     * with a dot until the tip was opened, lit while the tip is open and named for what its tap does, and the banner
     * only once the button opened it.
     */
    private fun renderTip(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val tip = ui.tip?.takeIf { ui.guide != null && ui.guideState.visible }
        val open = tip?.open == true
        binding.tipButtonFrame.isVisible = tip != null
        binding.tipDot.isVisible = tip?.unseen == true
        binding.tipButton.isActivated = open
        binding.tipButton.contentDescription = getString(if (open) R.string.close_tip else R.string.show_tip)
        binding.tipBanner.isVisible = open
        binding.tipText.text = tip?.tip?.text
    }

    /**
     * The guide's panel above the shutter while a guide is on the video, as Camera's (FR-7.2): Hide, the opacity, and ✕
     * show the photographer's guide as both phones have it, and go to the photographer's phone; the opacity is off while
     * the guide is hidden, and the three while not connected or while a guide this phone offered waits for the
     * photographer's phone to adopt it. ♥ is this phone's own *Save guide*.
     */
    private fun renderGuideControls(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        // The open tip comes first, as the line that names the camera gives way to it: at a large font the panel steps
        // aside while the two do not fit together, and is back once the tip closes.
        binding.guideControls.isVisible =
            ui.guide != null && (!binding.tipBanner.isVisible || panelFitsBesideTheTip(binding))
        val hidden = !ui.guideState.visible
        val shared = ui.connected && !ui.guideWaiting
        val opacityEnabled = shared && ui.guide != null && !hidden
        opacitySlider?.render(GuideState.MIN_OPACITY, GuideState.MAX_OPACITY, ui.guideState.opacity, opacityEnabled)
        binding.opacityValue.isEnabled = opacityEnabled
        binding.opacityValue.text =
            getString(R.string.guide_opacity_percent, (ui.guideState.opacity * 100f).roundToInt())
        val hideIcon = if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        // Setting the same icon again would redraw the button on every state change.
        if (binding.hideGuideButton.tag != hideIcon) {
            binding.hideGuideButton.setIconResource(hideIcon)
            binding.hideGuideButton.tag = hideIcon
        }
        binding.hideGuideButton.contentDescription = getString(if (hidden) R.string.show_guide else R.string.hide_guide)
        binding.hideGuideButton.isEnabled = shared
        binding.removeGuideButton.isEnabled = shared
        renderSaveGuide(binding, ui)
    }

    /**
     * Whether the open tip, the guide's panel, and the zoom row above them fit between the top bar and the shutter's
     * row. The longest tip at the largest font does not (CameraLayoutTest). True before the first layout.
     */
    private fun panelFitsBesideTheTip(binding: FragmentSubjectBinding): Boolean {
        val overlays = binding.bottomOverlays
        val width = overlays.width - overlays.paddingLeft - overlays.paddingRight
        if (width <= 0) return true
        val across = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val down = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val margin = ZOOM_ROW_MARGIN_DP * resources.displayMetrics.density
        var needed = overlays.paddingBottom + binding.zoomRow.height + margin
        for (panel in listOf(binding.tipBanner, binding.guideControls)) {
            panel.measure(across, down)
            needed += panel.measuredHeight + (panel.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        }
        return needed <= binding.bottomPanel.top - binding.topBar.bottom
    }

    /** *Save guide* (FR-6.16): on the guide's panel, a ♥ filled red once a favorite here (#297). */
    private fun renderSaveGuide(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val button = binding.saveGuideButton
        val icon = if (ui.guideSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart
        // Setting the same icon again would redraw the button on every state change.
        if (button.tag != icon) {
            button.setIconResource(icon)
            button.iconTint = requireContext().getColorStateList(saveGuideTint(ui.guideSaved))
            button.tag = icon
        }
        button.contentDescription = getString(if (ui.guideSaved) R.string.guide_saved else R.string.save_guide)
        button.isEnabled = ui.connected && !ui.savingGuide
    }

    /**
     * The photographer's steps (Design 2.6.5): *Generate poses*, *Generating poses* (Iter3-A13), *Pick a pose*, or *Your
     * reference* opens by itself the first time this screen sees the description open, the set created or ready, or the
     * confirm open while it is in front, and the button beside *Guide* opens it again while it lasts. A set opens one
     * of its screens by itself once: *Generating poses* goes on to *Pick a pose* by itself, and after Back from it,
     * *Pick a pose* waits for the button.
     */
    private fun renderPoses(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        // A set with no copy here has nothing to pick from: the photographer picks on its own phone.
        val picking = ui.poseStep == PoseSetStep.PICKING && ui.posesReady && ui.connected
        val generating = generating(ui)
        val confirming = confirming(ui)
        val describing = describing(ui)
        val step = picking || generating || confirming || describing
        binding.pickPoseButton.isVisible = step
        // The right third is the thumbnail's alone, in its middle as Camera's gallery button, or the step's button's
        // and the thumbnail's, each as far from that middle (#314).
        val density = resources.displayMetrics.density
        binding.galleryButton.translationX = if (step) STEP_BESIDE_THUMBNAIL_DP * density else 0f
        // The step's name is centered under its icon and may be wider than it, never as far as the shutter: on a
        // narrow phone it takes two lines sooner.
        val row = binding.guideRow.width
        if (row > 0) {
            val toShutter = row / 3f - (STEP_BESIDE_THUMBNAIL_DP + SHUTTER_HALF_DP + STEP_CLEAR_OF_SHUTTER_DP) * density
            binding.pickPoseLabel.maxWidth = (2 * toShutter).toInt().coerceAtLeast((MIN_TOUCH_DP * density).toInt())
        }
        val label = when {
            confirming -> R.string.pose_guide_title
            describing -> R.string.describe_title
            generating -> R.string.generating_title
            else -> R.string.pick_pose_title
        }
        binding.pickPoseLabel.setText(label)
        binding.pickPoseButton.contentDescription = getString(label)
        // A step that starts while the session photo is open here waits, and opens as the viewer closes.
        if (ui.photosOpen) return
        val confirmId = ui.confirmId
        val setId = ui.poseSetId
        val describeId = ui.describeId
        // Each is claimed only while this screen is the current destination, or one that came while Add a pose guide
        // covers it would be spent without opening.
        if (confirming && confirmId != null) {
            if (inFront(binding) && viewModel.claimConfirmOpening(confirmId)) openConfirm()
        } else if (picking && setId != null) {
            if (inFront(binding) && viewModel.claimSetOpening(setId)) openPick()
        } else if (generating && setId != null) {
            if (inFront(binding) && viewModel.claimSetOpening(setId)) openGenerating()
        } else if (describing && describeId != null) {
            if (inFront(binding) && viewModel.claimDescribeOpening(describeId)) openDescribe()
        }
    }

    /**
     * Whether this screen itself is what its user looks at: the current destination, so not under a sheet such as Add
     * a pose guide, which leaves it started, and not under its own photos or a dialog of its own, "Leave the session?"
     * or the tutorial's tips. A new dialog of this screen belongs here too.
     */
    private fun uncovered(): Flow<Boolean> {
        // Without a navigation controller, as in a screen test, nothing can cover it but its photos.
        val nav = runCatching { findNavController() }.getOrNull()
        val onTop = if (nav == null) {
            flowOf(true)
        } else {
            callbackFlow {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    trySend(destination.id == R.id.subjectFragment)
                }
                nav.addOnDestinationChangedListener(listener)
                awaitClose { nav.removeOnDestinationChangedListener(listener) }
            }
        }
        val photos = viewModel.uiState.map { it.photosOpen }
        return combine(onTop, photos, leaveAsked, tutorialOver()) { top, open, leaving, tips ->
            top && !open && !leaving && !tips
        }.distinctUntilChanged()
    }

    /** Whether the tutorial's tips are over this screen: a child dialog, so the destination stays this one. */
    private fun tutorialOver(): Flow<Boolean> = callbackFlow {
        val fragments = childFragmentManager
        val watcher = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentAttached(manager: FragmentManager, fragment: Fragment, context: Context) {
                if (fragment is TutorialFragment) trySend(true)
            }

            override fun onFragmentDetached(manager: FragmentManager, fragment: Fragment) {
                if (fragment is TutorialFragment) trySend(false)
            }
        }
        trySend(TutorialFragment.over(this@SubjectFragment) != null)
        fragments.registerFragmentLifecycleCallbacks(watcher, false)
        awaitClose { fragments.unregisterFragmentLifecycleCallbacks(watcher) }
    }

    /**
     * What became of the situation this phone picked for AI poses (FR-6.15, #276): the photographer takes the scene
     * photo next, is asked to agree first, or their phone could not begin.
     */
    private fun showPoseStart(binding: FragmentSubjectBinding, step: PoseStartStep) {
        val name = viewModel.uiState.value.photographerName
        when (step) {
            // Their phone shows the AI notice first: this one waits, and says so.
            PoseStartStep.ASKING -> if (name != null) {
                showToggleLabel(
                    binding,
                    getString(R.string.subject_poses_asking, name),
                    R.drawable.ic_sparkle,
                    R.color.pix_text,
                )
            }

            PoseStartStep.STARTED -> if (name != null) {
                showToggleLabel(
                    binding,
                    getString(R.string.subject_poses_started, name),
                    R.drawable.ic_sparkle,
                    R.color.pix_text,
                )
            }

            PoseStartStep.REFUSED -> showToggleLabel(
                binding,
                getString(R.string.subject_poses_refused),
                R.drawable.ic_alert_small,
                R.color.pix_text,
            )
        }
    }

    /**
     * The line right above the shutter, once connected (#314; in the shutter's place before it, #271): whose camera
     * this is and that it is live, or, from the photographer's shutter until the photo is saved here, that it is coming
     * (FR-6.14). While a step's button shows, the video's own text says what the photographer does, so the line is
     * the name alone, without the red dot. The open tip takes the line's place until its button closes it: at the
     * largest font the two do not fit above the shutter together.
     */
    private fun renderCaption(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        binding.cameraCaption.isVisible = ui.connected && ui.tip?.open != true
        val receiving = ui.receivingPhoto
        val live = !receiving && !binding.pickPoseButton.isVisible
        binding.cameraCaptionName.isVisible = !receiving
        // The red dot says "live", as the light over the video does: not beside the name of a camera that is off.
        binding.cameraCaptionDot.isVisible = live
        binding.cameraCaptionSpinner.isVisible = receiving
        binding.cameraCaptionStatus.isVisible = receiving || live
        binding.cameraCaptionStatus.setText(
            if (receiving) R.string.subject_receiving_photo else R.string.subject_live,
        )
        fitCaption(binding, ui.photographerName, live)
    }

    /**
     * One line while the name and "Live" fit the width above the shutter; at a font too large for that, the name
     * stands over "Live" (SessionCaption). The name is the photographer's account, or their phone's while signed out.
     */
    private fun fitCaption(binding: FragmentSubjectBinding, name: String?, live: Boolean) {
        val overlays = binding.bottomOverlays
        SessionCaption(
            binding.cameraCaption,
            binding.cameraCaptionDot,
            binding.cameraCaptionTitle,
            binding.cameraCaptionSeparator,
            binding.cameraCaptionStatus,
        ) { if (it == null) getString(R.string.subject_title_placeholder) else getString(R.string.subject_title, it) }
            .fit(
                overlays.width - overlays.paddingLeft - overlays.paddingRight,
                name,
                getString(R.string.subject_live),
                live,
            )
    }

    /**
     * This phone's shutter (FR-7.8, #314): dimmed while the photographer's video does not show, as while the link
     * reconnects or their camera is off for a pose's step, and while a press waits for its answer. While the
     * photographer's timer counts down it stops the countdown, as Camera's own does (FR-1.17): it shows Camera's stop
     * square in the timer's ring, filling through the photographer's seconds, and is named so; the tutorial's tip on it
     * says so too, and no line on the screen.
     */
    private fun renderShutter(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        binding.shutterButton.isEnabled = ui.connected && videoShowing.value && !ui.shutterWaiting
        renderCountdownRing(binding.shutterButton, ui)
        val name = ui.photographerName
        val timer = ui.timer
        binding.shutterButton.contentDescription = when {
            ui.countdown != null -> getString(R.string.shutter_cancel_timer)

            // With the photographer's timer on, a press starts its countdown (FR-7.8, #318), and the shutter says so.
            name == null && timer != ShutterTimer.OFF -> getString(R.string.shutter_timer, timer.seconds)

            name == null -> getString(R.string.shutter)

            timer != ShutterTimer.OFF -> getString(R.string.subject_shutter_timer, name, timer.seconds)

            else -> getString(R.string.subject_shutter, name)
        }
    }

    /**
     * The timer's ring on the shutter while the photographer's countdown shows (FR-1.17), over the timer's whole length
     * as chosen; the white shutter again as it ends.
     */
    private fun renderCountdownRing(shutter: View, ui: SubjectUiState) {
        val left = ui.countdown
        val ring = countdownRing
        if (left == null) {
            ring?.stop()
            if (ring != null &&
                shutter.background === ring.drawable
            ) {
                shutter.setBackgroundResource(R.drawable.bg_shutter)
            }
            return
        }
        val shown = ring ?: CountdownRing(requireContext()).also { countdownRing = it }
        if (shutter.background !== shown.drawable) shutter.background = shown.drawable
        shown.show(left, ui.timer.seconds)
    }

    /**
     * Whether this screen is the current destination. Under Add a pose guide it is not, and closing the sheet changes
     * nothing this screen renders from, so the steps are rendered again once this screen is current.
     */
    private fun inFront(binding: FragmentSubjectBinding): Boolean {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectFragment) return true
        if (frontWatch == null) {
            val listener = object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) {
                    if (destination.id != R.id.subjectFragment) return
                    stopWatchingFront()
                    // After the change, not inside it, since opening a step navigates.
                    binding.root.post { if (view != null) renderPoses(binding, viewModel.uiState.value) }
                }
            }
            frontWatch = nav to listener
            nav.addOnDestinationChangedListener(listener)
        }
        return false
    }

    private fun stopWatchingFront() {
        frontWatch?.let { (nav, listener) -> nav.removeOnDestinationChangedListener(listener) }
        frontWatch = null
    }

    /**
     * Whether the photographer's Generate poses is open here and no later step is: a set or a confirm held here is
     * shown first, since the description's end may come after the next step's start.
     */
    private fun describing(ui: SubjectUiState): Boolean =
        ui.describeId != null && ui.connected && ui.poseStep == null && ui.confirmId == null

    private fun openDescribe() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectFragment) nav.navigate(R.id.action_subject_to_describe)
    }

    /**
     * Whether the photographer's Reference confirm is open here. It follows the choice that ended the set, so a set
     * still PICKING here is shown first: its end is on its way.
     */
    private fun confirming(ui: SubjectUiState): Boolean =
        ui.confirmId != null && ui.connected && ui.poseStep != PoseSetStep.PICKING

    private fun openPick() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectFragment) nav.navigate(R.id.action_subject_to_pickPose)
    }

    /** Whether the photographer's set is being created; a confirm or a description held here is shown first. */
    private fun generating(ui: SubjectUiState): Boolean =
        ui.poseStep == PoseSetStep.GENERATING && ui.connected && !confirming(ui) && !describing(ui)

    private fun openGenerating() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectFragment) nav.navigate(R.id.action_subject_to_generating)
    }

    private fun openConfirm() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectFragment) nav.navigate(R.id.action_subject_to_confirm)
    }

    /**
     * "Dongje is describing a pose…", "… is creating poses…", "… is picking a pose…", or "… is checking the guide…"
     * while a step of the photographer's is open.
     */
    private fun poseStatus(ui: SubjectUiState): String? {
        val name = ui.photographerName ?: return null
        if (confirming(ui)) return getString(R.string.peer_checking_guide, name)
        if (describing(ui)) return getString(R.string.peer_describing_pose, name)
        return when (ui.poseStep) {
            PoseSetStep.GENERATING -> getString(R.string.peer_creating_poses, name)
            PoseSetStep.PICKING -> getString(R.string.peer_picking_pose, name)
            else -> null
        }
    }

    /**
     * The brightness bar beside the focus box, up after a tap on the video while the photographer's camera has a
     * range, and the camera menu (FR-7.3, FR-1.17).
     */
    private fun renderCameraControls(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        // Until a tap to focus is answered, the bar shows the brightness from before it: its own drag waits too.
        binding.exposureBar.awaitingValue = ui.baseAwaited
        binding.exposureBar.render(
            ui.exposureRange,
            ui.exposureStep,
            ui.exposure,
            enabled = ui.connected && ui.hasBrightness,
        )
        focusBrightness?.place(videoFrame(binding, ui))
        focusBrightness?.show(ui.brightnessOpen)
        renderMenu(binding, ui)
        renderCountdown(binding, ui)
    }

    /**
     * The camera menu (FR-1.17): its button opens and closes it. The photographer's flash goes to its next choice at each
     * tap and this phone's grid on or off, leaving it open; Settings closes it and opens that screen, which covers this
     * one and comes back to it when the session ends (#204).
     */
    private fun setupMenu(binding: FragmentSubjectBinding) {
        val toggle = View.OnClickListener { viewModel.setMenuOpen(!viewModel.uiState.value.menuOpen) }
        binding.cameraMenuButton.setOnClickListener(toggle)
        // A tap on the icons beside the button opens the menu too, as on Camera, through the area over them.
        binding.indicatorsTouch.setOnClickListener(toggle)
        binding.menuFlash.setOnClickListener { viewModel.onFlashTap() }
        binding.menuTimer.setOnClickListener { viewModel.onTimerTap() }
        binding.menuGrid.setOnClickListener { settingsViewModel.toggleShowGrid() }
        binding.menuSettings.setOnClickListener {
            viewModel.setMenuOpen(false)
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.subjectFragment) nav.navigate(R.id.action_subject_to_settings)
        }
    }

    /**
     * The camera menu's button, the photographer's flash and timer beside it while either is on, as Camera shows its
     * own, and the menu's items, each in the accent while what it sets is on (FR-1.17): the photographer's flash, only
     * when their camera has one (FR-7.3), their timer (#318), and this phone's grid.
     */
    private fun renderMenu(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        binding.cameraMenuButton.isActivated = ui.menuOpen
        binding.cameraMenu.reveal(ui.menuOpen)
        val flashOn = ui.hasFlash && ui.flash != FlashMode.OFF
        val timerOn = ui.timer != ShutterTimer.OFF
        binding.flashIndicator.isVisible = flashOn
        binding.timerIndicator.isVisible = timerOn
        binding.indicatorsTouch.isVisible = flashOn || timerOn
        setImageOnce(
            binding.flashIndicator,
            when (ui.flash) {
                FlashMode.OFF -> R.drawable.ic_flash_off
                FlashMode.AUTO -> R.drawable.ic_flash_auto
                FlashMode.ON -> R.drawable.ic_flash_on
            },
        )
        setImageOnce(binding.timerIndicator, timerIcon(ui.timer))
        binding.menuFlash.isVisible = ui.hasFlash
        // The photographer's flash and timer wait while their timer counts down, as on their phone (FR-1.17).
        binding.menuFlash.isEnabled = ui.connected && ui.countdown == null
        binding.menuFlash.isActivated = flashOn
        binding.menuTimer.isEnabled = ui.connected && ui.countdown == null
        binding.menuTimer.isActivated = timerOn
        setImageOnce(
            binding.menuTimerIcon,
            when (ui.timer) {
                ShutterTimer.OFF -> R.drawable.ic_menu_timer_off
                ShutterTimer.THREE -> R.drawable.ic_menu_timer_3
                ShutterTimer.TEN -> R.drawable.ic_menu_timer_10
            },
        )
        binding.menuTimer.contentDescription = timerText(ui.timer)
        val icon = when (ui.flash) {
            FlashMode.OFF -> R.drawable.ic_menu_flash_off
            FlashMode.AUTO -> R.drawable.ic_menu_flash_auto
            FlashMode.ON -> R.drawable.ic_menu_flash_on
        }
        if (binding.menuFlashIcon.tag != icon) {
            binding.menuFlashIcon.tag = icon
            binding.menuFlashIcon.setImageResource(icon)
        }
        binding.menuFlash.contentDescription = getString(
            when (ui.flash) {
                FlashMode.OFF -> R.string.flash_off
                FlashMode.AUTO -> R.string.flash_auto
                FlashMode.ON -> R.string.flash_on
            },
        )
        val grid = settingsViewModel.showGrid.value
        binding.menuGrid.isActivated = grid
        binding.menuGrid.contentDescription = getString(if (grid) R.string.grid_on else R.string.grid_off)
    }

    /** The timer beside the menu's button, at the weight of the top bar's icons; never off, which shows none. */
    @DrawableRes
    private fun timerIcon(timer: ShutterTimer): Int =
        if (timer == ShutterTimer.TEN) R.drawable.ic_timer_10 else R.drawable.ic_timer_3

    /** "Timer off", or "Timer 3 s": the menu's item as TalkBack reads it, as on Camera. */
    private fun timerText(timer: ShutterTimer): String = if (timer == ShutterTimer.OFF) {
        getString(R.string.timer_off)
    } else {
        getString(R.string.timer_seconds, timer.seconds)
    }

    /** Sets [image]'s drawable only when it changes, which the tag remembers: a render comes with every zoom step. */
    private fun setImageOnce(image: ImageView, @DrawableRes icon: Int) {
        if (image.tag == icon) return
        image.tag = icon
        image.setImageResource(icon)
    }

    /** The photographer's timer counting down (FR-1.17): the seconds left over the video, and whose photo it is. */
    private fun renderCountdown(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val left = ui.countdown
        binding.countdownPanel.isVisible = left != null
        if (left == null) return
        binding.countdownNumber.text = left.toString()
        // The name comes in `hello`, before any countdown; without it the number says enough.
        val name = ui.photographerName
        binding.countdownLine.isVisible = name != null
        if (name != null) binding.countdownLine.text = getString(R.string.photographer_timer_label, name)
    }

    /**
     * A tap on the video inside the photographer's image (FR-7.7): their camera is asked to focus there, the box shows
     * where, and the brightness bar comes up beside it (FR-7.3), as a tap on Camera does. False when nothing was asked:
     * a tap beside the image, a camera that can do neither, or a video that is not showing.
     */
    private fun onVideoTap(x: Float, y: Float): Boolean {
        val binding = view?.let(FragmentSubjectBinding::bind) ?: return false
        // Not over a video that is not coming: the photographer's camera is paused or not yet seen, and would drop the
        // request, so a box here would show a focus that was never set.
        if (!videoShowing.value) return false
        // Nor while the photographer's camera rebinds for a new ratio: it drops a tap until it is ready, which the
        // first frame of the new shape says, and the frame on screen is not the one the tap would be placed in.
        if (reshaping) return false
        val ui = viewModel.uiState.value
        val frame = videoFrame(binding, ui)
        if (frame.width <= 0f || x !in frame.left..frame.right || y !in frame.top..frame.bottom) return false
        if (!viewModel.onFocusTap((x - frame.left) / frame.width, (y - frame.top) / frame.height)) return false
        val focusBrightness = focusBrightness ?: return false
        focusBrightness.tap(x, y, focuses = ui.supportsFocusPoint, brightness = ui.hasBrightness)
        if (ui.hasBrightness) {
            viewModel.setBrightnessOpen(true)
            focusBrightness.place(frame)
            focusBrightness.hold()
        }
        return true
    }

    /**
     * The photographer's own tap to focus (FR-7.7): the focus box at that place on this phone's video, alone, as the
     * photographer's phone shows this phone's taps. A bar this phone has up goes, since the camera no longer focuses
     * beside it and the brightness is the photographer's base again; while a finger is on it, nothing moves under it.
     */
    private fun showPhotographerFocus(binding: FragmentSubjectBinding, focus: RemoteFocus) {
        if (binding.exposureBar.isHeld) return
        val frame = videoFrame(binding, viewModel.uiState.value)
        if (frame.width <= 0f || frame.height <= 0f) return
        // The box first: the bar's going then leaves it alone.
        focusBrightness?.showBoxAlone(frame.left + focus.x * frame.width, frame.top + focus.y * frame.height)
        viewModel.setBrightnessOpen(false)
    }

    /**
     * A drag up or down on the video (FR-7.3): with the bar up after a tap, it moves the brightness on from there, once
     * that tap is answered.
     * Without it, the drag taps where it started first, as on Camera, which puts the photographer's brightness back to
     * their base: this phone learns that base from the tap's answer, so the drag's way counts from when it came
     * (`SubjectUiState.baseAwaited`), and the bar's sun starts there.
     */
    private val videoBrightnessDrag = object : PinchZoomController.VerticalDrag {
        // The tap this drag began with is not answered yet.
        private var waiting = false

        // How far up the finger was when the bar's drag began.
        private var from = 0f

        override fun onStart(x: Float, y: Float): Boolean {
            val binding = view?.let(FragmentSubjectBinding::bind) ?: return false
            val ui = viewModel.uiState.value
            if (!ui.connected || !ui.hasBrightness) return false
            waiting = false
            from = 0f
            if (!ui.brightnessOpen || !binding.exposureBar.isVisible) {
                waiting = onVideoTap(x, y)
                return waiting
            }
            // The bar is up, but its tap may not be answered yet: then it still shows the brightness from before.
            if (ui.baseAwaited) {
                waiting = true
                return true
            }
            binding.exposureBar.startDrag()
            return binding.exposureBar.isDragging
        }

        override fun onMove(up: Float) {
            val bar = view?.let(FragmentSubjectBinding::bind)?.exposureBar ?: return
            if (waiting) {
                val ui = viewModel.uiState.value
                // The bar went meanwhile, as at the photographer's own focus: this drag moves nothing any more.
                if (!ui.brightnessOpen) {
                    waiting = false
                    return
                }
                if (ui.baseAwaited) return
                waiting = false
                from = up
                bar.startDrag()
            }
            // Nothing while the bar is not dragged.
            bar.dragTo(up - from)
        }

        override fun onEnd() {
            waiting = false
            focusBrightness?.finishDrag()
        }
    }

    /** The photographer's image on this screen: the video's frame in their ratio, fitted in its area (AD-10). */
    private fun videoFrame(binding: FragmentSubjectBinding, ui: SubjectUiState): ViewfinderFrame {
        val area = binding.videoContainer
        return fitViewfinderFrame(area.width.toFloat(), area.height.toFloat(), ui.aspect.frameAspect)
    }

    /**
     * TalkBack has no tap at a place on the video, and the brightness button is gone (#273): its action on the video
     * asks for the focus at the image's middle, which brings the bar, and moves its focus onto the bar, which stays up
     * while it has it.
     */
    private fun setupFocusAccessibility(binding: FragmentSubjectBinding) {
        ViewCompat.replaceAccessibilityAction(
            binding.liveView,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.focus_and_brightness_action),
        ) { _, _ ->
            val frame = videoFrame(binding, viewModel.uiState.value)
            if (!onVideoTap(frame.centerX, frame.centerY)) return@replaceAccessibilityAction false
            val bar = binding.exposureBar
            // Once the bar shows. Without a screen reader the focus does not move, and the bar keeps its own time.
            bar.post {
                if (!bar.isShown) return@post
                bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                if (!bar.isAccessibilityFocused) {
                    bar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                }
            }
            true
        }
    }

    private fun setupZoomAccessibility(readout: View) {
        fun adjust(factor: Float): Boolean {
            val ui = viewModel.uiState.value
            if (!ui.canZoom) return false
            val ratio = (ui.zoom * factor).coerceIn(ui.minZoom, ui.maxZoom)
            if (ratio == ui.zoom) return false
            viewModel.onZoomGesture(ratio, final = true)
            return true
        }
        ViewCompat.replaceAccessibilityAction(
            readout,
            AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
            getString(R.string.zoom_in),
        ) { _, _ -> adjust(1.1f) }
        ViewCompat.replaceAccessibilityAction(
            readout,
            AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
            getString(R.string.zoom_out),
        ) { _, _ -> adjust(1f / 1.1f) }
        // A tap steps through the quick zoom steps, such as 0.6×, 1×, and 2× (FR-1.3).
        readout.setOnClickListener { viewModel.onZoomTap() }
        ViewCompat.replaceAccessibilityAction(
            readout,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.zoom_next_step),
        ) { _, _ ->
            viewModel.onZoomTap()
            true
        }
    }

    private fun onTransition(state: SessionState) {
        if (state !is SessionState.Ended) return
        when (state.reason) {
            EndReason.CONNECTION_LOST -> {
                // The action is Subject view's: close Add a pose guide first if it is open over this screen.
                val nav = findNavController()
                if (nav.currentDestination?.id != R.id.subjectFragment) nav.popBackStack(R.id.subjectFragment, false)
                nav.navigate(R.id.action_subject_to_connectionLost)
            }

            EndReason.PEER_LEFT -> {
                val name = viewModel.uiState.value.photographerName
                showNotice(
                    name?.let { getString(R.string.peer_ended_session, it) }
                        ?: getString(R.string.peer_ended_session_unnamed),
                )
                backToCamera()
            }

            EndReason.LEFT, EndReason.CANCELLED -> backToCamera()

            else -> {
                showNotice(R.string.session_ended)
                backToCamera()
            }
        }
    }

    /**
     * [text], a guide removed by either phone, with *Undo*, which asks the photographer's phone to put it back (FR-7.2).
     * It goes once a guide is on the video again.
     */
    private fun offerUndo(text: String) {
        dismissUndo()
        undoNotice =
            showNoticeOnScreen(text, NoticeLength.UNDO, NoticeAction(R.string.undo) { viewModel.onUndoRemove() })
    }

    private fun dismissUndo() {
        undoNotice?.dismiss()
        undoNotice = null
    }

    /** Asks before leaving, for Back and Leave alike (#298); one tap leaves the photographer's camera for good. */
    private fun confirmLeave() {
        if (leaveDialog?.isShowing == true) return
        leaveDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.leave_session_title)
            .setPositiveButton(R.string.leave) { _, _ -> leaveNow() }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { leaveAsked.value = false }
            .show()
        leaveAsked.value = true
    }

    private fun leaveNow() {
        pinchZoom?.finishInteraction()
        focusBrightness?.finishDrag()
        sessionViewModel.leave()
        backToCamera()
    }

    private fun backToCamera() {
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    /**
     * Notices take the zoom row's place, as on Camera, so they cover the row but never the line that names the camera,
     * the shutter, or the controls under the video (#298); above the controls while the row is hidden. Over the
     * session's photos, they sit where the viewer says, not over the photo.
     */
    override val noticeAnchor: View?
        get() {
            val binding = view?.let(FragmentSubjectBinding::bind) ?: return null
            photoViewer?.takeIf { it.isOpen }?.let { return it.noticeAnchor }
            return if (binding.zoomRow.isShown) binding.noticeLine else binding.bottomPanel
        }

    /**
     * Passes frames to the renderer and remembers when the last one came, to detect a paused camera, and its shape. It
     * runs on the renderer's thread and calls [onShape] there for the first frame and each new shape.
     */
    private class FrameWatchdog(private val renderer: VideoSink, private val onShape: () -> Unit) : VideoSink {
        @Volatile
        private var lastFrameNanos = 0L

        /** The last frame's width / height as shown, its rotation applied; 0 before the first. */
        @Volatile
        var shape = 0f
            private set

        override fun onFrame(frame: VideoFrame) {
            val first = lastFrameNanos == 0L
            lastFrameNanos = System.nanoTime()
            val next = if (frame.rotatedHeight > 0) frame.rotatedWidth.toFloat() / frame.rotatedHeight else 0f
            val changed = next != shape
            shape = next
            renderer.onFrame(frame)
            if (first || changed) onShape()
        }

        /** Whether any frame has arrived since the screen opened. */
        fun hasFrame(): Boolean = lastFrameNanos != 0L

        /** Since the last frame; meaningful only once [hasFrame]. */
        fun nanosSinceLastFrame(): Long = System.nanoTime() - lastFrameNanos
    }

    /** What a toggle just did, at the top of the video for a few seconds, as on the Camera screen (Design 2.8). */
    private fun showToggleLabel(
        binding: FragmentSubjectBinding,
        text: String,
        @DrawableRes icon: Int,
        @ColorRes tint: Int,
        durationMs: Long = LABEL_MS,
    ) {
        val chip = binding.toggleLabel
        chip.text = text
        setLabelIcon(chip, icon, tint)
        chip.isVisible = true
        toggleLabelJob?.cancel()
        toggleLabelJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(durationMs)
            // A pinch of the photographer's that still goes on has its label back, which this one took the place of.
            if (heldLabel != null) showZoomingLabel(binding) else chip.isVisible = false
        }
    }

    /**
     * What the photographer did on their own camera (FR-7.4, #292), in the words Camera has for what the subject did,
     * since the same thing happened the other way round. The focus's line is as short as Camera's.
     */
    private fun showPhotographerLine(binding: FragmentSubjectBinding, line: PhotographerLine) {
        val ui = viewModel.uiState.value
        val name = ui.photographerName ?: return
        val white = R.color.pix_text
        when (line) {
            PhotographerLine.Focused -> showToggleLabel(
                binding,
                getString(R.string.remote_focus_notice, name),
                R.drawable.ic_target_small,
                white,
                SessionViewModel.FOCUS_NOTICE_MS,
            )

            is PhotographerLine.Did -> when (val action = line.action) {
                is PhotographerAction.Zoom -> showToggleLabel(
                    binding,
                    getString(R.string.remote_zoom_notice, name, DecimalFormat("0.#").format(action.ratio)),
                    R.drawable.ic_zoom_small,
                    white,
                )

                is PhotographerAction.Brightness -> showToggleLabel(
                    binding,
                    getString(
                        R.string.remote_brightness_notice,
                        name,
                        ExposureScale.formatEv(action.index, ui.exposureStep),
                    ),
                    R.drawable.ic_brightness,
                    white,
                )

                is PhotographerAction.Timer -> if (action.timer == ShutterTimer.OFF) {
                    showToggleLabel(
                        binding,
                        getString(R.string.remote_timer_off_notice, name),
                        R.drawable.ic_menu_timer_off,
                        white,
                    )
                } else {
                    showToggleLabel(
                        binding,
                        getString(R.string.remote_timer_notice, name, action.timer.seconds),
                        timerIcon(action.timer),
                        white,
                    )
                }

                is PhotographerAction.Flash -> when (action.mode) {
                    FlashMode.OFF -> showToggleLabel(
                        binding,
                        getString(R.string.remote_flash_off_notice, name),
                        R.drawable.ic_flash_off,
                        white,
                    )

                    FlashMode.AUTO -> showToggleLabel(
                        binding,
                        getString(R.string.remote_flash_auto_notice, name),
                        R.drawable.ic_flash_auto,
                        white,
                    )

                    FlashMode.ON -> showToggleLabel(
                        binding,
                        getString(R.string.remote_flash_on_notice, name),
                        R.drawable.ic_flash_on,
                        white,
                    )
                }

                PhotographerAction.GuideMove -> showToggleLabel(
                    binding,
                    getString(R.string.remote_guide_move_notice, name),
                    R.drawable.ic_guide,
                    white,
                )

                PhotographerAction.Guide ->
                    showToggleLabel(binding, getString(R.string.remote_guide_notice, name), R.drawable.ic_guide, white)

                is PhotographerAction.GuideVisible -> if (action.visible) {
                    showToggleLabel(
                        binding,
                        getString(R.string.remote_guide_show_notice, name),
                        R.drawable.ic_visibility,
                        white,
                    )
                } else {
                    showToggleLabel(
                        binding,
                        getString(R.string.remote_guide_hide_notice, name),
                        R.drawable.ic_visibility_off_small,
                        white,
                    )
                }

                is PhotographerAction.GuideOpacity -> showToggleLabel(
                    binding,
                    getString(R.string.remote_guide_opacity_notice, name, (action.opacity * 100f).roundToInt()),
                    R.drawable.ic_guide,
                    white,
                )

                // Said with its Undo through guideRemoved, which waits for this screen (FR-7.2).
                PhotographerAction.GuideRemove -> Unit

                PhotographerAction.GuideRestore -> showToggleLabel(
                    binding,
                    getString(R.string.remote_guide_restore_notice, name),
                    R.drawable.ic_guide,
                    white,
                )
            }
        }
    }

    /**
     * "… is zooming" for as long as the photographer's pinch (#292): a label without a time of its own, which goes with
     * [letHeldLabelGo] or when another label takes its place.
     */
    private fun holdZoomingLabel(binding: FragmentSubjectBinding) {
        toggleLabelJob?.cancel()
        showZoomingLabel(binding)
    }

    /** Puts "… is zooming" in the label's place and keeps its text as the held one. */
    private fun showZoomingLabel(binding: FragmentSubjectBinding) {
        val name = viewModel.uiState.value.photographerName ?: return
        val text = getString(R.string.zoom_held_label, name)
        val chip = binding.toggleLabel
        chip.text = text
        setLabelIcon(chip, R.drawable.ic_zoom_small, R.color.pix_text)
        chip.isVisible = true
        heldLabel = text
    }

    /** The pinch is over: its label goes, unless another label has taken its place and keeps its own time. */
    private fun letHeldLabelGo(binding: FragmentSubjectBinding) {
        val held = heldLabel ?: return
        heldLabel = null
        if (toggleLabelJob?.isActive == true || binding.toggleLabel.text.toString() != held) return
        binding.toggleLabel.isVisible = false
    }

    /** Over the guide-edit mode toggle for a few seconds: what a pinch moves now (Design 2.8). */
    private fun showPinchMode(binding: FragmentSubjectBinding, guide: Boolean) {
        showPinchModeLabel(binding.pinchModeLabel, guide)
        pinchModeJob?.cancel()
        pinchModeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(LABEL_MS)
            binding.pinchModeLabel.isVisible = false
        }
    }

    internal companion object {
        /** How far the thumbnail and a step's button each stand from the right third's middle while a step lasts. */
        const val STEP_BESIDE_THUMBNAIL_DP = 26f

        /** Half the shutter, and what a step's name keeps clear of it. */
        const val SHUTTER_HALF_DP = 36f
        const val STEP_CLEAR_OF_SHUTTER_DP = 4f

        /** A control's smallest width (NFR-16), which a step's name may always take. */
        const val MIN_TOUCH_DP = 48f

        const val STALL_NANOS = 2_000_000_000L
        const val STALL_CHECK_MS = 500L

        /** As long as the Camera screen's labels stay (CameraViewModel.NOTICE_MS). */
        const val LABEL_MS = 3_000L
        const val RESHAPE_TIMEOUT_MS = 2_000L

        // Frames the encoder scales down keep their shape to within rounding.
        const val SHAPE_TOLERANCE = 0.02f
        const val VIDEO_DIM = 0.6f
        const val SHUTTER_DIM = 0.5f
    }
}
