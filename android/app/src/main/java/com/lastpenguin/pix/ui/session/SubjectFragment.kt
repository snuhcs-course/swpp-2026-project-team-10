// AI-generated with ChatGPT Codex and Claude Code, 2026-11-25, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PhotographerAction
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.PixViewModels
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
import com.lastpenguin.pix.ui.camera.TurningIcons
import com.lastpenguin.pix.ui.camera.ViewfinderFrame
import com.lastpenguin.pix.ui.camera.ZOOM_ROW_MARGIN_DP
import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
import com.lastpenguin.pix.ui.camera.framePlace
import com.lastpenguin.pix.ui.camera.guideSaveIcon
import com.lastpenguin.pix.ui.camera.guideSaveTint
import com.lastpenguin.pix.ui.camera.isUnder
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
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.TutorialFragment
import com.lastpenguin.pix.ui.settings.followLook
import com.lastpenguin.pix.ui.settings.renderLatency
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import com.lastpenguin.pix.ui.settings.tutorialFor
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
 * brightness slider and the flash button are the photographer's controls on this phone (FR-7.3), and the guide's
 * reference photo sits in a corner as on their Camera screen (FR-3.9). *Guide* opens Add a pose guide, whose guide
 * goes to the photographer (FR-6.15, Design 2.6.5), and *Save guide* keeps the guide on this phone (FR-6.16). While
 * the photographer describes or creates poses, picks one, or checks its guide, the video says so, and their *Generate
 * poses*, candidates, and the chosen pose's outline open on this phone's screens of the same steps (Design 2.6.5).
 * The bottom row is Camera's: *Guide* at the left, and the session's newest photo as a thumbnail at the right, empty
 * until the first, which opens the session's photos in a viewer over the screen, inside Pix, so the session goes on
 * (FR-6.14). In the middle, where Camera's shutter is, it says whose camera this is and that it is live, or that a
 * photo is coming (#271), for the whole session, so no notice says it again.
 * System back closes the viewer, or else asks first (R&S 6.9). The first time the video shows, the tutorial's tips of
 * this screen come over it (FR-10.4). The video fades in, dims under a text, moves to the photographer's new ratio as
 * their frame does, and blinks at their shutter (Design 2.6.4). Leaving for another screen, the video goes at once and
 * the rest fades (Design 2.1).
 * Owner: Real-time (#8, #10, #57), with the guide from #9 and Iter3-P9; *Save guide*, the candidates, and the outline
 * Iter3-P16.
 */
class SubjectFragment : Fragment(R.layout.fragment_subject) {

    private val viewModel: SubjectViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private val zoomFormat = DecimalFormat("0.0")
    private var pinchZoom: PinchZoomController? = null

    // The focus box at this phone's tap and the brightness bar beside it, as on Camera (FR-7.3, FR-7.7).
    private var focusBrightness: FocusBrightness? = null
    private var renderer: SurfaceViewRenderer? = null
    private var watchdog: FrameWatchdog? = null
    private var referenceDrag: ReferenceDragController? = null
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
        // The camera's name is fitted to the slot's width, known only after layout.
        binding.cameraCaptionTitle.addOnLayoutChangeListener { title, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                title.post { if (view != null) renderCaption(binding, viewModel.uiState.value) }
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
        // The icons turn with this phone, held as the subject holds it, and so do the zoom readout and Guide and Pick a
        // pose with their labels (FR-1.15); the other texts and the layout stay.
        viewModel.watchPhoneTurn(viewLifecycleOwner)
        turningIcons = TurningIcons(
            controls = listOf(
                binding.flashButton,
                binding.settingsButton,
                binding.tipButton,
                binding.saveGuideButton,
                binding.editGuideButton,
                binding.zoomReadout,
            ),
            views = listOf(binding.guideButton, binding.pickPoseButton, binding.galleryThumbnail),
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
        binding.flashButton.setOnClickListener { viewModel.onFlashTap() }
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
            )
        this.photoViewer = photoViewer
        binding.guideButton.setOnClickListener { findNavController().navigate(R.id.action_subject_to_addGuide) }
        binding.saveGuideButton.setOnClickListener { viewModel.onSaveGuide() }
        setupTip(binding)
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

        binding.leaveButton.setOnClickListener { leaveNow() }
        // Settings in a session (#204): it covers this screen and comes back to it when the session ends.
        binding.settingsButton.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.subjectFragment) nav.navigate(R.id.action_subject_to_settings)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (viewModel.uiState.value.photosOpen) viewModel.closePhotos() else confirmLeave()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.phoneTurn.collect { turn ->
                        turningIcons?.turnTo(turn)
                        photoViewer.turnTo(turn)
                    }
                }
                // The grid as this phone's Settings says (FR-1.9); Subject view has no horizon bar.
                launch { settingsViewModel.showGrid.collect { binding.compositionOverlay.showGrid = it } }
                // The photographer's guide in this phone's own color and thickness (FR-3.10).
                launch {
                    binding.guideOverlay.followLook(settingsViewModel.guideLook, viewModel.uiState.map { it.guide })
                }
                launch {
                    viewModel.photoNotDeleted.collect {
                        Toast.makeText(requireContext(), R.string.photo_not_deleted, Toast.LENGTH_SHORT).show()
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
                        binding.compositionOverlay.frameAspect = ui.aspect.frameAspect
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
                        binding.editGuideButton.isEnabled = ui.connected && ui.guideState.visible
                        if (ui.guide == null) binding.pinchModeLabel.isVisible = false
                        // Guide-colored once a guide is on the video; it then replaces the guide on both phones.
                        binding.guideButton.isActivated = ui.guide != null
                        binding.guideButton.isEnabled = ui.connected
                        binding.guideButton.contentDescription = getString(
                            if (ui.guide == null) R.string.guide_add_description else R.string.guide_change_description,
                        )
                        renderSaveGuide(binding, ui)
                        renderTip(binding, ui)
                        binding.galleryButton.isEnabled = ui.lastPhoto != null
                        photoViewer.render(ui.photos, ui.photosOpen)
                        renderPoses(binding, ui)
                        renderCaption(binding, ui)
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
                        Toast.makeText(requireContext(), R.string.guide_offer_failed, Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    // Generate poses that the photographer closed while this screen is in front.
                    viewModel.describeEnds.collect { end ->
                        describeEndNotice(requireContext(), end, viewModel.uiState.value.photographerName)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    // A confirm that ended while this screen is in front; its Your reference says so itself while open.
                    viewModel.confirmEnds.collect { end ->
                        confirmEndNotice(requireContext(), end, viewModel.uiState.value.photographerName)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    sessionViewModel.mobileDataPending.collect { pending ->
                        if (!pending) return@collect
                        Toast.makeText(requireContext(), R.string.mobile_data_notice, Toast.LENGTH_LONG).show()
                        sessionViewModel.mobileDataNoticeShown()
                    }
                }
            }
        }
    }

    override fun onPause() {
        pinchZoom?.finishInteraction()
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
        if (abs((watchdog?.shape ?: 0f) - aspect.frameAspect) < SHAPE_TOLERANCE) endReshape(binding)
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
        if (abs(shape - viewModel.uiState.value.aspect.frameAspect) < SHAPE_TOLERANCE) endReshape(binding)
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
        placeZoomRow(binding.zoomRow, binding.bottomOverlays, imageBottom, margin)
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
     * The photographer's tip (FR-4.10, #213): the tip button opens and closes it, a touch anywhere closes it and still
     * does what it does where it lands, and the banner is clickable as well, so TalkBack can close it.
     */
    private fun setupTip(binding: FragmentSubjectBinding) {
        binding.root.onTouchEnd = { event ->
            // The tip button opens and closes the tip itself; closed here first, its tap would only open it again.
            if (!binding.tipButtonFrame.isUnder(event, binding.root)) viewModel.onAnyTouch()
        }
        binding.tipButton.setOnClickListener { viewModel.onTipButton() }
        binding.tipBanner.setOnClickListener { viewModel.onAnyTouch() }
        ViewCompat.replaceAccessibilityAction(
            binding.tipBanner,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.close_tip),
        ) { _, _ ->
            viewModel.onAnyTouch()
            true
        }
    }

    /**
     * The photographer's tip of the guide on the video (FR-4.10, #213): its button while that guide has one and shows,
     * with a dot until the tip was opened, and the banner only once the button opened it.
     */
    private fun renderTip(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val tip = ui.tip?.takeIf { ui.guide != null && ui.guideState.visible }
        binding.tipButtonFrame.isVisible = tip != null
        binding.tipDot.isVisible = tip?.unseen == true
        binding.tipBanner.isVisible = tip?.open == true
        binding.tipText.text = tip?.tip?.text
    }

    /**
     * *Save guide* (FR-6.16): beside the guide-edit toggle while a guide is on the video, a ♥ filled red once a favorite
     * here (#297).
     */
    private fun renderSaveGuide(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        val button = binding.saveGuideButton
        button.isVisible = ui.guide != null
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
        binding.pickPoseButton.isVisible = picking || generating || confirming || describing
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
     * The slot where Camera's shutter is (#271), once connected and while no step's button takes it: whose camera this
     * is and that it is live, or, from the photographer's shutter until the photo is saved here, that it is coming
     * (FR-6.14).
     */
    private fun renderCaption(binding: FragmentSubjectBinding, ui: SubjectUiState) {
        binding.cameraCaption.isVisible = ui.connected && !binding.pickPoseButton.isVisible
        val title = cameraTitle(binding.cameraCaptionTitle, ui.photographerName)
        if (binding.cameraCaptionTitle.text.toString() != title) binding.cameraCaptionTitle.text = title
        binding.cameraCaptionDot.isVisible = !ui.receivingPhoto
        binding.cameraCaptionSpinner.isVisible = ui.receivingPhoto
        binding.cameraCaptionStatus.setText(
            if (ui.receivingPhoto) R.string.subject_receiving_photo else R.string.subject_live,
        )
    }

    /**
     * "Dongje's camera" for the slot: the name is the photographer's account, or their phone's while signed out, which
     * can be long, such as "길동의 Galaxy S23 Ultra". The text gets smaller down to its smallest size first; a name
     * that still does not fit is cut short, so "'s camera" stays.
     */
    private fun cameraTitle(view: TextView, name: String?): String {
        if (name == null) return getString(R.string.subject_title_placeholder)
        val full = getString(R.string.subject_title, name)
        val width = (view.width - view.totalPaddingLeft - view.totalPaddingRight).toFloat()
        if (width <= 0f) return full
        val paint = TextPaint(view.paint).apply { textSize = view.autoSizeMinTextSize.toFloat() }
        if (paint.measureText(full) <= width) return full
        val rest = paint.measureText(getString(R.string.subject_title, ""))
        val short = TextUtils.ellipsize(name, paint, (width - rest).coerceAtLeast(0f), TextUtils.TruncateAt.END)
        return getString(R.string.subject_title, short)
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
     * range, and the flash button, hidden when it has no flash (FR-7.3).
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
        binding.flashButton.isVisible = ui.hasFlash
        binding.flashButton.isEnabled = ui.connected
        binding.flashButton.setIconResource(
            when (ui.flash) {
                FlashMode.OFF -> R.drawable.ic_flash_off
                FlashMode.AUTO -> R.drawable.ic_flash_auto
                FlashMode.ON -> R.drawable.ic_flash_on
            },
        )
        binding.flashButton.contentDescription = getString(
            when (ui.flash) {
                FlashMode.OFF -> R.string.flash_off
                FlashMode.AUTO -> R.string.flash_auto
                FlashMode.ON -> R.string.flash_on
            },
        )
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
                Toast.makeText(
                    requireContext(),
                    name?.let { getString(R.string.peer_ended_session, it) }
                        ?: getString(R.string.peer_ended_session_unnamed),
                    Toast.LENGTH_SHORT,
                ).show()
                backToCamera()
            }

            EndReason.LEFT, EndReason.CANCELLED -> backToCamera()

            else -> {
                Toast.makeText(requireContext(), R.string.session_ended, Toast.LENGTH_SHORT).show()
                backToCamera()
            }
        }
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.leave_session_title)
            .setPositiveButton(R.string.leave) { _, _ -> leaveNow() }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
