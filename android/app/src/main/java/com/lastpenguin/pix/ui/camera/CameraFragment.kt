// AI-generated with ChatGPT Codex and Claude Code, 2026-11-05, reviewed by Jaewan Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.view.PreviewView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.fadeTo
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.session.SessionNotice
import com.lastpenguin.pix.ui.session.SessionViewModel
import com.lastpenguin.pix.ui.settings.SettingsViewModel
import com.lastpenguin.pix.ui.settings.TutorialFragment
import com.lastpenguin.pix.ui.settings.followLook
import com.lastpenguin.pix.ui.settings.renderLatency
import com.lastpenguin.pix.ui.settings.showTutorialWhenDue
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Camera screen. Guide gestures belong to #6; the scene photo for poses belongs to #7; live session UI belongs to #8. */
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val settingsViewModel: SettingsViewModel by activityViewModels { PixViewModels.Factory }
    private var binding: FragmentCameraBinding? = null
    private var sceneBack: OnBackPressedCallback? = null
    private var aspectsBack: OnBackPressedCallback? = null
    private var permissionRequested = false
    private val zoomFormat = DecimalFormat("0.0")
    private var pinchZoom: PinchZoomController? = null
    private var referenceDrag: ReferenceDragController? = null
    private var opacitySlider: TrackedSliderController? = null
    private var brightnessSlider: TrackedSliderController? = null
    private var previewLogged = false
    private var noticeJob: Job? = null

    // The last frame stays on screen through a ratio change or a lens switch, until the rebound preview streams
    // again (NFR-19, Design 2.6.2, 2.6.5).
    private var previewHold: PreviewHold? = null
    private var stillJob: Job? = null

    // The frame's ratio and place when the held frame was taken, while it is on screen.
    private var stillAspect: AspectRatio? = null
    private var stillPlace: FramePlace? = null

    // Whether the held frame waits dimmed and blurred (veilStill), for a new ratio or the other camera; a lens change of
    // the zoom, which goes on under the fingers, leaves it as it is.
    private var stillVeiled = false
    private var blur: ValueAnimator? = null

    // The held frame turning over for the other camera (flipStill).
    private var flip: ValueAnimator? = null

    // The held frame's blur in pixels, which a test cannot read back from the view.
    @VisibleForTesting
    internal var stillBlur = 0f
        private set

    // From a new ratio over a held frame until that frame has gone: the grid and the guide wait for the new frame, so
    // the guide is never drawn where it does not belong (FR-3.7, AD-10).
    private var reframing = false

    // Where the preview shows and for which ratio; a new ratio over a held frame moves it there (reframe).
    private var shownAspect: AspectRatio? = null
    private var shown: FramePlace? = null
    private var move: ValueAnimator? = null

    // The move under way, which a test sets to a point between its ends: the display's paused frames catch up at once.
    @VisibleForTesting
    internal val frameMove: ValueAnimator? get() = move

    @VisibleForTesting
    internal val stillFlip: ValueAnimator? get() = flip

    // The preview's last frame for PreviewHold; a test stands in for PreviewView, which has no surface on the JVM.
    @VisibleForTesting
    internal var previewFrame: () -> Bitmap? = { binding?.previewView?.bitmap }

    // The last photo's thumbnail, off the main thread; a test stands in for MediaStore, which has no photos on the JVM.
    @VisibleForTesting
    internal var thumbnailOf: (Uri) -> Bitmap? = { uri ->
        requireContext().contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null)
    }

    // Where the preview is, and how much of it the top bar and the controls cover (previewArea).
    private var previewArea = PreviewArea(0f, 0f, 0f)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (binding != null) {
            render(viewModel.uiState.value)
            if (granted) bindCamera()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val controls = FragmentCameraBinding.bind(view)
        binding = controls
        permissionRequested = savedInstanceState?.getBoolean(PERMISSION_REQUESTED) ?: permissionRequested
        previewHold = PreviewHold(show = ::showStill, hide = ::hideStill)
        controls.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        // TextureView composes predictably with guide and permission/error layers above it.
        controls.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        // Far enough that the held frame turning over stays inside the preview instead of reaching out of the screen.
        controls.aspectStill.cameraDistance = FLIP_CAMERA_DISTANCE * resources.displayMetrics.density
        // PreviewView fits the viewport's crop but still draws the camera image around it: show only the frame, or,
        // for 1:1, the whole 4:3 area with the rest dimmed.
        controls.previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitToFrame(controls) }
        // The preview's place follows the screen's height and the bars' (R&S 6.2), known only after layout.
        val placePreview = View.OnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                view.post { binding?.let(::placePreview) }
            }
        }
        controls.root.addOnLayoutChangeListener(placePreview)
        controls.topBar.addOnLayoutChangeListener(placePreview)
        controls.bottomPanel.addOnLayoutChangeListener(placePreview)
        // The zoom row stays above the panels over the shutter as they open and close.
        controls.bottomOverlays.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeZoomRow(controls) }
        val previewStreaming = MutableStateFlow(false)
        controls.previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            Timings.mark("preview.stream", state.name)
            previewStreaming.value = state == PreviewView.StreamState.STREAMING
            if (state == PreviewView.StreamState.STREAMING && !previewLogged) {
                previewLogged = true
                Timings.mark("camera.preview")
            }
            val hold = previewHold
            if (hold != null) {
                val holding = hold.holding
                hold.onStream(state == PreviewView.StreamState.STREAMING)
                if (hold.holding != holding) render(viewModel.uiState.value)
            }
        }
        // A pinch on the preview zooms while guide-edit mode is off (AD-17); each step is applied as it comes.
        // A tap focuses there (FR-1.4); the pinch controller owns the preview's touches, so it reports the tap.
        pinchZoom = PinchZoomController(
            controls.previewView,
            range = ::zoomRange,
            current = { viewModel.uiState.value.zoom },
            onTap = ::onPreviewTap,
        ) { ratio, final -> viewModel.onZoomChanged(ratio, final) }
        opacitySlider = TrackedSliderController(
            controls.opacitySlider,
            { getString(R.string.guide_opacity_percent, (it * 100f).roundToInt()) },
            viewModel::onOpacityChange,
        )
        // The slider runs from 1, because TrackedSliderController takes positive values; each step is an index.
        brightnessSlider = TrackedSliderController(
            controls.brightnessSlider,
            { formatEv(sliderIndex(it)) },
        ) { value, _ -> viewModel.onExposureChange(sliderIndex(value)) }
        // While the scene photo is taken, the flash button turns the phone's light on and off instead (FR-4.1).
        controls.flashButton.setOnClickListener {
            if (isScenePhotoActive()) generationViewModel.toggleLight() else viewModel.onFlashTap()
        }
        // The ratios open under the top bar (FR-1.13); a touch anywhere else closes them (setupTip), and so does Back.
        controls.aspectButton.setOnClickListener {
            viewModel.setAspectsOpen(!viewModel.uiState.value.aspectsOpen)
        }
        for ((aspect, option) in aspectOptions(controls)) {
            option.contentDescription = getString(R.string.aspect_ratio_description, aspectLabel(aspect))
            option.setOnClickListener { chooseAspect(aspect) }
        }
        aspectsBack = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            viewModel.setAspectsOpen(false)
        }
        controls.brightnessButton.setOnClickListener {
            viewModel.setBrightnessOpen(!viewModel.uiState.value.brightnessOpen)
        }
        setupZoomAccessibility(controls.zoomReadout)
        setupCompositionLevel(controls.compositionOverlay)
        controls.cameraActionButton.setOnClickListener {
            if (hasCameraPermission()) {
                bindCamera()
            } else {
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }
        }
        controls.requestPermissionButton.setOnClickListener {
            permissionRequested = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        controls.guideButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_addGuide)
        }
        // Who takes the photo? first, then that phone's ways to connect (FR-5.2, R&S 6.7).
        controls.shootTogetherButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_shootTogether)
        }
        controls.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_settings)
        }
        controls.shutterButton.setOnClickListener {
            // While the scene photo for poses is framed, the shutter takes that photo and saves nothing.
            if (generationViewModel.uiState.value.phase == GenerationUiState.Phase.FRAMING) {
                generationViewModel.takeScene()
            } else if (viewModel.onShutter()) {
                // The preview blinks as the photo is taken (FR-1.5).
                blinkPreview(controls.previewScrim)
            }
        }
        setupScenePhoto(controls)
        controls.galleryButton.setOnClickListener { openLastPhoto() }
        // Selfie mode (FR-1.14): the last frame stays up, blurred, and turns over while the other camera opens.
        controls.switchCameraButton.setOnClickListener {
            previewHold?.hold()
            veilStill(flip = true)
            viewModel.onSwitchCamera()
        }
        controls.guideOverlay.onGesture = viewModel::onGuideGesture
        controls.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        controls.hideGuideButton.setOnClickListener { viewModel.onHideToggle() }
        controls.saveGuideButton.setOnClickListener { viewModel.onSaveGuide() }
        setupReference(controls)
        setupTip(controls)
        controls.editGuideButton.setOnClickListener {
            viewModel.setGuideEditing(!viewModel.uiState.value.guideEditing)
        }
        controls.endSessionButton.setOnClickListener { sessionViewModel.leave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { generationViewModel.uiState.collect { render(viewModel.uiState.value) } }
                // The guide in the settings' color and thickness (FR-3.10).
                launch {
                    controls.guideOverlay.followLook(settingsViewModel.guideLook, viewModel.uiState.map { it.guide })
                }
                launch {
                    combine(
                        settingsViewModel.showLatency,
                        sessionViewModel.state,
                        sessionViewModel.latencyMs,
                    ) { _, _, _ -> }.collect { renderLatency(controls) }
                }
                // Camera's tutorial tips once the preview really shows, so nothing comes before it (FR-1.1, FR-10.4);
                // READY has no picture yet. Camera's own outside a session, and the guide's once a visible guide is on, whose ring needs it; neither
                // during the scene photo or under another screen, such as a link's, which opens over Camera at a start.
                launch {
                    val tips = combine(
                        settingsViewModel.tutorialDue(TutorialScreen.CAMERA),
                        settingsViewModel.tutorialDue(TutorialScreen.GUIDE_ON_CAMERA),
                        combine(previewStreaming, generationViewModel.uiState) { streaming, scene ->
                            streaming && !isScenePhotoActive(scene)
                        },
                        sessionViewModel.state.map { it is SessionState.Idle || it is SessionState.Ended },
                        viewModel.uiState.map { it.guide != null && it.guideState.visible },
                    ) { camera, guide, showing, alone, guided ->
                        when {
                            !showing -> null
                            camera && alone -> TutorialScreen.CAMERA
                            guide && guided -> TutorialScreen.GUIDE_ON_CAMERA
                            else -> null
                        }
                    }
                    showTutorialWhenDue(tips, TutorialFragment.AFTER_PICTURE_MS) { screen ->
                        findNavController().currentDestination?.id == R.id.cameraFragment && !isScenePhotoActive() &&
                            (
                                screen != TutorialScreen.GUIDE_ON_CAMERA ||
                                    viewModel.uiState.value.let { it.guide != null && it.guideState.visible }
                                )
                    }
                }
                // The photographer's side of a session: the live light, End session, and "left" notices (FR-6.8, FR-6.10).
                // While the link reconnects the light is off, and the session can still be ended (Design 2.6.7).
                launch {
                    sessionViewModel.state.collect { state ->
                        val connected = state as? SessionState.Connected
                        val live = connected != null || state is SessionState.Reconnecting
                        renderLiveDot(controls, connected)
                        controls.endSessionButton.isVisible = live
                        controls.shootTogetherButton.isVisible = !live && !isScenePhotoActive()
                        controls.settingsButton.isVisible = controls.shootTogetherButton.isVisible
                        view.keepScreenOn = live
                    }
                }
                launch {
                    sessionViewModel.notices.collect { notice ->
                        val text = when (notice) {
                            is SessionNotice.PeerGone -> getString(
                                if (notice.reason ==
                                    EndReason.CONNECTION_LOST
                                ) {
                                    R.string.peer_disconnected
                                } else {
                                    R.string.peer_left
                                },
                                notice.name,
                            )

                            is SessionNotice.RemoteZoom ->
                                getString(
                                    R.string.remote_zoom_notice,
                                    notice.name,
                                    DecimalFormat("0.#").format(notice.ratio),
                                )

                            is SessionNotice.RemoteGuideMove -> getString(
                                R.string.remote_guide_move_notice,
                                notice.name,
                            )

                            is SessionNotice.RemoteBrightness ->
                                getString(R.string.remote_brightness_notice, notice.name, formatEv(notice.index))

                            is SessionNotice.RemoteFlash -> getString(
                                when (notice.mode) {
                                    FlashMode.OFF -> R.string.remote_flash_off_notice
                                    FlashMode.AUTO -> R.string.remote_flash_auto_notice
                                    FlashMode.ON -> R.string.remote_flash_on_notice
                                },
                                notice.name,
                            )

                            is SessionNotice.RemoteGuide -> getString(R.string.remote_guide_notice, notice.name)
                        }
                        showNotice(controls, text)
                    }
                }
                launch {
                    sessionViewModel.photosNotSent.collect { notSent ->
                        val text = if (notSent.count == 1) {
                            getString(R.string.session_photo_not_sent, notSent.name)
                        } else {
                            getString(R.string.session_photos_not_sent, notSent.count, notSent.name)
                        }
                        showNotice(controls, text)
                        // Each for its full time: the next would replace this one before it can be read.
                        delay(CameraViewModel.NOTICE_MS)
                    }
                }
                launch {
                    sessionViewModel.mobileDataPending.collect { pending ->
                        if (!pending) return@collect
                        showNotice(controls, getString(R.string.mobile_data_notice))
                        sessionViewModel.mobileDataNoticeShown()
                    }
                }
                launch {
                    viewModel.guideSaveNotices.collect { notice ->
                        val text = when (notice) {
                            GuideSaveNotice.SAVED -> getString(R.string.guide_saved_notice)

                            GuideSaveNotice.SAVED_OLDEST_REMOVED ->
                                getString(R.string.guide_saved_oldest_removed_notice, SavedGuideStore.MAX_SAVED)

                            GuideSaveNotice.ALREADY_SAVED -> getString(R.string.guide_already_saved_notice)

                            GuideSaveNotice.FAILED -> getString(R.string.guide_save_failed_notice)
                        }
                        showNotice(controls, text)
                    }
                }
                launch {
                    // A photo just taken grows into the thumbnail (FR-1.6); one there as the screen opens just shows.
                    var opening = true
                    viewModel.uiState.map { it.lastPhoto }.distinctUntilChanged().collectLatest { uri ->
                        val taken = !opening
                        opening = false
                        val thumbnail = if (uri == null) {
                            null
                        } else {
                            withContext(Dispatchers.IO) {
                                try {
                                    thumbnailOf(uri)
                                } catch (_: Exception) {
                                    null // The full photo can still be opened if thumbnail decoding fails.
                                }
                            }
                        }
                        if (thumbnail == null) {
                            controls.galleryThumbnail.setImageResource(R.drawable.gallery_placeholder)
                        } else {
                            controls.galleryThumbnail.setImageBitmap(thumbnail)
                            if (taken) growIn(controls.galleryThumbnail)
                        }
                    }
                }
            }
        }
        render(viewModel.uiState.value)
    }

    override fun onResume() {
        super.onResume()
        render(viewModel.uiState.value)
        if (hasCameraPermission()) {
            bindCamera()
        } else if (!permissionRequested) {
            permissionRequested = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PERMISSION_REQUESTED, permissionRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        pinchZoom?.finishInteraction()
        opacitySlider?.finishInteraction()
        brightnessSlider?.finishInteraction()
        super.onPause()
    }

    override fun onDestroyView() {
        pinchZoom?.finishInteraction()
        pinchZoom = null
        referenceDrag = null
        opacitySlider?.finishInteraction()
        opacitySlider = null
        brightnessSlider?.finishInteraction()
        brightnessSlider = null
        move?.let {
            move = null
            it.cancel()
        }
        // Without its hold, the screen takes the held frame down at once, also one a cancelled move left up.
        val hold = previewHold
        previewHold = null
        hold?.release()
        binding?.let(::clearStill)
        reframing = false
        shown = null
        shownAspect = null
        binding = null
        sceneBack = null
        aspectsBack = null
        previewLogged = false
        super.onDestroyView()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val controls = binding ?: return
        viewModel.bindCamera(viewLifecycleOwner, controls.previewView.surfaceProvider)
    }

    /** A pinch starts while the camera is ready and continues through a lens switch's STARTING, as the slider did. */
    private fun zoomRange(): ClosedFloatingPointRange<Float>? {
        val state = viewModel.uiState.value
        if (!hasCameraPermission() || state.maxZoom <= state.minZoom) return null
        val continuing = state.cameraStatus == CameraStatus.STARTING && pinchZoom?.isTracking == true
        return if (state.cameraStatus == CameraStatus.READY || continuing) state.minZoom..state.maxZoom else null
    }

    private fun setupZoomAccessibility(readout: View) {
        fun adjust(factor: Float): Boolean {
            val state = viewModel.uiState.value
            if (state.cameraStatus != CameraStatus.READY) return false
            val ratio = (state.zoom * factor).coerceIn(state.minZoom, state.maxZoom)
            if (ratio == state.zoom) return false
            viewModel.onZoomChanged(ratio)
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

    /**
     * The reference thumbnail (FR-3.9): a tap enlarges it or makes it small again, and a drag drops it into the
     * nearest corner. TalkBack gets the same tap and an action for each other corner.
     */
    private fun setupReference(controls: FragmentCameraBinding) {
        val thumbnail = controls.referenceThumbnail
        referenceDrag = ReferenceDragController(
            thumbnail,
            area = { referenceArea(controls) },
            onTap = { viewModel.setReferenceExpanded(!viewModel.uiState.value.referenceExpanded) },
        ) { corner ->
            corner?.let(viewModel::setReferenceCorner)
            renderReference(controls, viewModel.uiState.value)
        }
        thumbnail.setReferenceActions(
            expanded = { viewModel.uiState.value.referenceExpanded },
            corner = { viewModel.uiState.value.referenceCorner },
            moveTo = viewModel::setReferenceCorner,
        )
        // The corners follow the preview's size and the panels at its bottom, which grow when brightness or a notice
        // opens; both are known only after layout.
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
                thumbnail.post { binding?.let { renderReference(it, viewModel.uiState.value) } }
            }
        }
        controls.previewContainer.addOnLayoutChangeListener(relayout)
        controls.bottomOverlays.addOnLayoutChangeListener(relayout)
    }

    /** Inside the preview's margins and above the zoom row, the panels over the shutter, and the controls. */
    private fun referenceArea(controls: FragmentCameraBinding): ReferenceArea {
        val margin = (REFERENCE_MARGIN_DP * resources.displayMetrics.density).roundToInt()
        val container = controls.previewContainer
        val zoomTop = (controls.zoomRow.top + controls.zoomRow.translationY).roundToInt()
        val bottom = minOf(zoomTop - container.top, container.height - previewArea.underControls.roundToInt()) - margin
        return ReferenceArea(margin, margin, container.width - margin, bottom)
    }

    /** Where previewArea puts the image of [aspect] on this screen (R&S 6.2); null before layout. */
    private fun placeFor(controls: FragmentCameraBinding, aspect: AspectRatio): FramePlace? {
        val root = controls.root
        if (root.width == 0 || root.height == 0) return null
        return framePlace(
            root.width.toFloat(),
            root.height.toFloat(),
            controls.topBar.height - TOP_BAR_OVERHANG_DP * resources.displayMetrics.density,
            controls.bottomPanel.height.toFloat(),
            aspect,
        )
    }

    /** After the screen or a bar changed size: the preview in its ratio's place at once, a move ended. */
    private fun placePreview(controls: FragmentCameraBinding) {
        endMove()
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        val place = placeFor(controls, aspect) ?: return
        shownAspect = aspect
        showPlace(controls, place)
    }

    /**
     * Shows the preview in [aspect]'s place. A new ratio over a held frame moves there in [Motion.MOVE_MS] while the
     * camera rebinds (Design 2.6.2): the frame's edges, and the held frame with the scene, to where the new ratio's
     * preview will show it. Otherwise, and while animations are off, the preview is there at once.
     */
    private fun reframe(controls: FragmentCameraBinding, aspect: AspectRatio) {
        if (aspect == shownAspect) return
        val to = placeFor(controls, aspect) ?: return
        endMove()
        val from = shown
        shownAspect = aspect
        if (from == null || previewHold?.holding != true || !controls.aspectStill.isVisible) {
            showPlace(controls, to)
            return
        }
        reframing = true
        if (!Motion.on()) {
            moveStep(controls, from, to, 1f)
            return
        }
        move = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.MOVE_MS
            interpolator = Motion.STANDARD
            addUpdateListener { step -> binding?.let { moveStep(it, from, to, step.animatedValue as Float) } }
            doOnEnd { ended ->
                if (move !== ended) return@doOnEnd
                move = null
                // The camera may stream again already: the held frame goes now that the frame is in its place.
                stillSettled()
            }
            start()
        }
    }

    /** Ends a move at its place, as when it is replaced or the screen changes size. */
    private fun endMove() {
        move?.end()
    }

    /** [fraction] of a ratio change's move: the frame's edges, and the held frame with the scene (sceneMove). */
    private fun moveStep(controls: FragmentCameraBinding, from: FramePlace, to: FramePlace, fraction: Float) {
        val place = lerp(from, to, fraction)
        showPlace(controls, place)
        val still = controls.aspectStill
        val taken = stillPlace
        if (!still.isVisible || taken == null) return
        val scene = sceneMove(taken, place)
        // The still is in the area's coordinates, as the preview was where it was taken.
        still.imageMatrix = Matrix().apply {
            setTranslate(-scene.fromX, -(scene.fromY - taken.area.top))
            postScale(scene.scale, scene.scale)
            postTranslate(scene.x, scene.y - place.area.top)
        }
        still.clipBounds = place.clip()
    }

    /**
     * Puts the preview at [place] (R&S 6.2): the area's margins, the clip, the live light, and the zoom row at the
     * bottom of its image. The controls get a dark gradient while they lie over it. Margins change only when the
     * place does, as the screen renders on every zoom step of a pinch.
     */
    private fun showPlace(controls: FragmentCameraBinding, place: FramePlace) {
        shown = place
        previewArea = place.area
        val container = controls.previewContainer
        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        val top = place.area.top.roundToInt()
        val bottom = (controls.root.height - place.area.bottom).roundToInt()
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            container.layoutParams = params
        }
        controls.bottomPanel.setBackgroundResource(if (place.area.underControls > 0f) R.drawable.bg_scrim_bottom else 0)
        fitToFrame(controls)
        placeZoomRow(controls)
    }

    /** The zoom row at the bottom of the image as it shows (1:1 inside the 4:3 area), above the panels and controls. */
    private fun placeZoomRow(controls: FragmentCameraBinding) {
        val place = shown ?: return
        val area = place.area
        if (area.bottom <= area.top) return
        val imageBottom = minOf(place.frame.bottom, area.bottom - area.underControls)
        val margin = ZOOM_ROW_MARGIN_DP * resources.displayMetrics.density
        placeZoomRow(controls.zoomRow, controls.bottomOverlays, imageBottom, margin)
    }

    private fun setupCompositionLevel(overlay: CameraCompositionView) {
        // This is a view-scoped sensor adapter, like ScaleGestureDetector, not shared camera state.
        val monitor = CameraLevelMonitor(requireContext())
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                monitor.start(overlay::setLevel)
            }

            override fun onPause(owner: LifecycleOwner) {
                monitor.stop()
                overlay.setLevel(null)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                monitor.stop()
            }
        })
    }

    /** *Cancel*, *Shoot again*, and *Use this photo* of the scene photo step (R&S 6.3, FR-4.1). */
    private fun setupScenePhoto(controls: FragmentCameraBinding) {
        controls.sceneCancelButton.setOnClickListener { generationViewModel.cancel() }
        controls.shootAgainButton.setOnClickListener { generationViewModel.retakeScene() }
        controls.usePhotoButton.setOnClickListener {
            val navController = findNavController()
            // Generate poses asks what to pose before anything is sent (FR-4.8), once a person is found (FR-4.13).
            if (navController.currentDestination?.id == R.id.cameraFragment && generationViewModel.useScene()) {
                navController.navigate(R.id.action_camera_to_describePose)
            }
        }
        // System back does the same as the secondary action (R&S 6.9): Shoot again on the photo, Cancel on the camera.
        // It is enabled only during this step, so back leaves the app from the plain camera as before.
        sceneBack = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            if (generationViewModel.uiState.value.phase == GenerationUiState.Phase.REVIEWING) {
                generationViewModel.retakeScene()
            } else {
                generationViewModel.cancel()
            }
        }
    }

    private fun render(state: CameraUiState) {
        val controls = binding ?: return
        val granted = hasCameraPermission()
        val ready = granted && state.cameraStatus == CameraStatus.READY
        val unavailable = state.cameraStatus == CameraStatus.UNAVAILABLE
        // First, so a frame held from here on also hides the spinner below.
        previewHold?.onStatus(state.cameraStatus)
        // A new ratio over a held frame moves there while the camera rebinds; otherwise the preview is there at once.
        reframe(controls, state.aspect)
        // Over a held frame of the same ratio (a lens switch) the grid and the guide stay; through a ratio change they
        // wait for the new frame.
        val overlays = !reframing && (ready || previewHold?.holding == true)
        controls.compositionOverlay.isVisible = overlays
        // The grid and the guide follow the chosen frame (AD-10).
        controls.compositionOverlay.frameAspect = state.aspect.frameAspect
        controls.guideOverlay.frameAspect = state.aspect.frameAspect
        fitToFrame(controls)
        controls.guideOverlay.render(state.guide, state.guideState)
        controls.guideOverlay.isVisible = overlays
        // Camera + guide (R&S 6.2). Visibility follows the guide alone: a lens switch briefly leaves READY,
        // and hiding the bar then would move the controls under an active touch.
        val guideEditable = granted && !unavailable
        controls.guideControls.isVisible = state.guide != null
        val opacityEnabled = guideEditable && state.guide != null && state.guideState.visible
        opacitySlider?.render(GuideState.MIN_OPACITY, GuideState.MAX_OPACITY, state.guideState.opacity, opacityEnabled)
        controls.opacityValue.isEnabled = opacityEnabled
        controls.opacityValue.text =
            getString(R.string.guide_opacity_percent, (state.guideState.opacity * 100f).roundToInt())
        controls.removeGuideButton.isEnabled = guideEditable
        val hidden = !state.guideState.visible
        setIconOnce(controls.hideGuideButton, if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
        controls.hideGuideButton.contentDescription =
            getString(if (hidden) R.string.show_guide else R.string.hide_guide)
        controls.hideGuideButton.isEnabled = guideEditable
        // Save guide (FR-3.8): filled once saved; a tap then says where the saved guide is.
        setIconOnce(
            controls.saveGuideButton,
            if (state.guideSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
        )
        controls.saveGuideButton.contentDescription =
            getString(if (state.guideSaved) R.string.guide_saved else R.string.save_guide)
        controls.saveGuideButton.isEnabled = guideEditable && !state.savingGuide
        // Guide-edit mode (AD-17): the overlay owns the preview's touches while on; the pinch zooms while off.
        // Its toggle sits beside the zoom readout, so it stays when the guide's panel changes (Iter2-P11).
        controls.editGuideButton.isVisible = state.guide != null
        // Checkable, so TalkBack reads the mode as on or off; the ViewModel decides the state after a tap.
        controls.editGuideButton.isChecked = state.guideEditing
        controls.editGuideButton.isEnabled = guideEditable && state.guideState.visible
        controls.guideOverlay.editable = state.guideEditing
        // Computed once per render: while the scene photo is framed the guide is hidden, so the pinch zooms whatever
        // the mode says, and while the photo is shown there is no zoom. Flipping it off and on again in one render
        // would end a pinch in progress, which the detector cannot restart in the same touch stream.
        val scene = generationViewModel.uiState.value
        pinchZoom?.enabled = if (isScenePhotoActive(scene)) {
            scene.phase != GenerationUiState.Phase.REVIEWING
        } else {
            !state.guideEditing
        }
        // Guide-colored once a guide is on the camera; it then changes the guide instead of adding one.
        controls.guideButton.isActivated = state.guide != null
        controls.guideButton.contentDescription =
            getString(if (state.guide == null) R.string.guide_add_description else R.string.guide_change_description)
        // The tutorial shows how to add a guide and move it (FR-10.4), so the chip says only what the screen does not:
        // that the guide is hidden, and that a pinch zooms again once edit mode is off.
        when {
            state.guide == null || state.guideEditing -> showHint(controls.hintText, null)

            !state.guideState.visible ->
                showHint(controls.hintText, R.string.hint_guide_hidden, R.drawable.ic_visibility_off_small)

            else -> showHint(controls.hintText, R.string.hint_pinch_zoom, R.drawable.ic_zoom_small)
        }
        controls.cameraMessagePanel.isVisible = !granted || unavailable
        // The permission and camera messages fill the preview; nothing over it applies then.
        controls.zoomRow.isInvisible = controls.cameraMessagePanel.isVisible
        renderLiveDot(controls, sessionViewModel.state.value as? SessionState.Connected)
        controls.cameraMessage.setText(if (granted) R.string.camera_unavailable else R.string.camera_permission_message)
        controls.cameraActionButton.setText(if (granted) R.string.try_again else R.string.open_settings)
        controls.requestPermissionButton.isVisible =
            !granted && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        // The held last frame stands for a camera that is about to stream again, so it shows no spinner.
        controls.cameraProgress.isVisible = granted && !ready && !unavailable && previewHold?.holding != true
        controls.shutterButton.isEnabled = ready && !state.isSaving
        controls.shutterButton.contentDescription =
            getString(if (state.isSaving) R.string.saving_photo else R.string.shutter)
        controls.guideButton.isEnabled = !state.isSaving
        controls.shootTogetherButton.isEnabled = !state.isSaving
        controls.galleryButton.isEnabled = state.lastPhoto != null
        // On any phone with a front camera, in a session too, and while the scene photo for poses is framed.
        controls.switchCameraButton.isVisible = state.canSwitchCamera
        controls.switchCameraButton.isEnabled = granted && !state.isSaving
        controls.switchCameraButton.contentDescription = getString(
            if (state.facing == CameraFacing.BACK) R.string.switch_to_front_camera else R.string.switch_to_rear_camera,
        )

        renderCameraSettings(controls, state, ready)

        val ratio = zoomFormat.format(state.zoom)
        controls.zoomReadout.text = getString(R.string.zoom_ratio, ratio)
        controls.zoomReadout.contentDescription = getString(R.string.camera_zoom_description, ratio)
        controls.zoomReadout.isEnabled = ready && state.maxZoom > state.minZoom
        // The round shutter has no label, so saving is shown where the result appears next.
        val notice = state.captureNotice
        when {
            state.isSaving -> showStatus(controls.captureStatus, R.string.saving_photo, StatusLook.PROGRESS)

            notice == null -> controls.captureStatus.isVisible = false

            else -> showStatus(
                controls.captureStatus,
                when (notice) {
                    CaptureNotice.SAVED -> R.string.photo_saved
                    CaptureNotice.STORAGE_FAILED -> R.string.photo_storage_failed
                    CaptureNotice.CAMERA_UNAVAILABLE -> R.string.photo_camera_closed
                    CaptureNotice.PERMISSION_REQUIRED -> R.string.camera_permission_message
                    CaptureNotice.SAVE_FAILED -> R.string.photo_save_failed
                },
                if (notice == CaptureNotice.SAVED) StatusLook.SUCCESS else StatusLook.ERROR,
            )
        }
        renderReference(controls, state)
        renderTip(controls, state)
        renderScenePhoto(controls, generationViewModel.uiState.value, state, ready)
        renderLatency(controls)
    }

    /**
     * The pose tip (FR-4.10): a touch anywhere closes the banner and still does what it does where it lands; the tip
     * button opens it again. The banner is clickable as well, so TalkBack can close it.
     */
    private fun setupTip(controls: FragmentCameraBinding) {
        (controls.root as TouchWatchingLayout).onTouchEnd = { event ->
            viewModel.onAnyTouch()
            // The ratios close at a touch anywhere but on them or their button, which opens and closes them itself.
            if (!controls.aspectButton.isUnder(event, controls.root) &&
                !controls.aspectPanel.isUnder(event, controls.root)
            ) {
                viewModel.setAspectsOpen(false)
            }
        }
        controls.tipButton.setOnClickListener { viewModel.onTipButton() }
        controls.tipBanner.setOnClickListener { viewModel.onAnyTouch() }
        ViewCompat.replaceAccessibilityAction(
            controls.tipBanner,
            AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.close_tip),
        ) { _, _ ->
            viewModel.onAnyTouch()
            true
        }
    }

    /**
     * The pose tip of the guide on the camera (FR-4.10): hidden with the guide, and the button only while a tip is
     * coming or there. The scene photo step hides both (renderScenePhoto).
     */
    private fun renderTip(controls: FragmentCameraBinding, state: CameraUiState) {
        val tip = state.tip?.takeIf { state.guide != null && state.guideState.visible }
        val available = tip != null && tip.state != TipState.Failed
        controls.tipButtonFrame.isVisible = available
        controls.tipDot.isVisible = available && tip.unseen
        controls.tipBanner.isVisible = tip?.open == true
        controls.tipText.text = when (val shown = tip?.state) {
            TipState.Preparing -> getString(R.string.tip_preparing)
            is TipState.Ready -> shown.tip.text
            TipState.Failed -> getString(R.string.tip_failed)
            null -> null
        }
    }

    /**
     * The guide's reference in its corner while there is one (FR-3.9): small, or half the preview's width when
     * enlarged. It glides to a new place, and stays while the guide is hidden; the scene photo step hides it and
     * shows its own hint. Layout changes call this alone, so it applies every rule of its own, the scene photo's too.
     */
    private fun renderReference(controls: FragmentCameraBinding, state: CameraUiState) {
        // The scene photo frames a new scene, which an earlier guide's reference is not part of.
        val reference = state.guide?.reference?.takeUnless { isScenePhotoActive() }
        // The enlarged photo reaches under the chips: the hint steps aside, and a capture status stays on top.
        controls.hintText.isInvisible =
            controls.hintText.text.isNullOrEmpty() || (reference != null && state.referenceExpanded)
        controls.referenceThumbnail.showReference(reference) { photo ->
            if (referenceDrag?.isDragging == true || controls.previewContainer.width == 0) {
                null
            } else {
                placeReference(
                    aspect = photo.width.toFloat() / photo.height,
                    expanded = state.referenceExpanded,
                    corner = state.referenceCorner,
                    smallLongSide = (REFERENCE_SMALL_DP * resources.displayMetrics.density).roundToInt(),
                    previewWidth = controls.previewContainer.width,
                    area = referenceArea(controls),
                )
            }
        }
    }

    /** The latency indicator (FR-10.3) in the top bar, except while the scene photo's title takes its place. */
    private fun renderLatency(controls: FragmentCameraBinding) {
        controls.latencyChip.renderLatency(
            settingsViewModel.showLatency.value && !isScenePhotoActive(),
            sessionViewModel.state.value,
            sessionViewModel.latencyMs.value,
        )
    }

    /** Whether the scene photo for poses is being framed or reviewed; the top bar is its title then. */
    private fun isScenePhotoActive(scene: GenerationUiState = generationViewModel.uiState.value): Boolean =
        scene.phase == GenerationUiState.Phase.REVIEWING || scene.phase == GenerationUiState.Phase.FRAMING

    /**
     * The scene photo for poses is taken and confirmed on this screen (R&S 6.3, FR-4.1): the live camera with the
     * shutter, then the photo with *Use this photo* and *Shoot again*. Applied last, over the camera's own controls,
     * so that the step shows only what it needs.
     */
    private fun renderScenePhoto(
        controls: FragmentCameraBinding,
        scene: GenerationUiState,
        camera: CameraUiState,
        ready: Boolean,
    ) {
        val reviewing = scene.phase == GenerationUiState.Phase.REVIEWING
        val active = isScenePhotoActive(scene)
        sceneBack?.isEnabled = active
        controls.sceneTitle.isVisible = active
        controls.sceneCancelButton.isVisible = active
        val session = sessionViewModel.state.value
        controls.shootTogetherButton.isVisible =
            !active && session !is SessionState.Connected && session !is SessionState.Reconnecting
        controls.settingsButton.isVisible = controls.shootTogetherButton.isVisible
        // Guide and the thumbnail keep their place, so the shutter stays where it is.
        controls.guideButton.isInvisible = active
        controls.galleryButton.isInvisible = active
        controls.sceneStill.isVisible = reviewing
        controls.sceneStill.setImageBitmap(if (reviewing) scene.scene else null)
        // The photo is taken: no zoom until it is used or shot again (the pinch's enabled state is set in render).
        controls.zoomReadout.isInvisible = reviewing
        // The scene photo is a frame of the live preview: a photo's flash, the ratio, and brightness do not apply to it.
        // While the scene is framed, the flash button keeps the phone's light on for it instead (FR-4.1). It says on
        // only once the camera has the light on, so it never claims a light that is not.
        if (active) {
            val light = !reviewing && camera.hasFlash
            controls.cameraSettings.isVisible = light
            controls.aspectButton.isVisible = false
            controls.aspectPanel.isVisible = false
            aspectsBack?.isEnabled = false
            controls.flashButton.isVisible = light
            controls.flashButton.isEnabled = ready
            controls.flashButton.setIconResource(if (camera.torch) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
            controls.flashButton.contentDescription =
                getString(if (camera.torch) R.string.scene_light_on else R.string.scene_light_off)
            controls.brightnessButton.isVisible = false
            controls.brightnessPanel.isVisible = false
        }
        if (active) {
            controls.editGuideButton.isVisible = false
            controls.tipButtonFrame.isVisible = false
        }
        controls.captureControls.isVisible = !reviewing
        controls.sceneReviewControls.isVisible = reviewing
        // Not before the phone has found a person in the photo (FR-4.13).
        controls.usePhotoButton.isEnabled = reviewing && !scene.checkingPerson && !scene.noPerson
        if (!active) return

        val noPerson = reviewing && scene.noPerson
        controls.sceneTitle.setText(
            when {
                noPerson -> R.string.scene_no_person_title
                reviewing -> R.string.scene_review_title
                else -> R.string.scene_title
            },
        )
        when {
            noPerson -> showHint(
                controls.hintText,
                R.string.scene_no_person_body,
                R.drawable.ic_alert_small,
                R.color.pix_danger,
            )

            reviewing -> showHint(controls.hintText, R.string.scene_review_body)

            else -> showHint(controls.hintText, null)
        }
        // An earlier guide is not part of the scene photo and would be in the way of framing it.
        controls.guideOverlay.isVisible = false
        controls.guideControls.isVisible = false
        controls.tipBanner.isVisible = false
        if (scene.shotFailed) {
            showStatus(controls.captureStatus, R.string.scene_shot_failed, StatusLook.ERROR)
        } else {
            controls.captureStatus.isVisible = false
        }
    }

    /** Flash, aspect ratio, and brightness (FR-1.11–1.13). */
    private fun renderCameraSettings(controls: FragmentCameraBinding, state: CameraUiState, ready: Boolean) {
        controls.cameraSettings.isVisible = true
        controls.aspectButton.isVisible = true
        controls.flashButton.isVisible = state.hasFlash
        controls.flashButton.isEnabled = ready
        controls.flashButton.setIconResource(
            when (state.flash) {
                FlashMode.OFF -> R.drawable.ic_flash_off
                FlashMode.AUTO -> R.drawable.ic_flash_auto
                FlashMode.ON -> R.drawable.ic_flash_on
            },
        )
        controls.flashButton.contentDescription = getString(
            when (state.flash) {
                FlashMode.OFF -> R.string.flash_off
                FlashMode.AUTO -> R.string.flash_auto
                FlashMode.ON -> R.string.flash_on
            },
        )
        val aspect = aspectLabel(state.aspect)
        controls.aspectButton.text = aspect
        controls.aspectButton.contentDescription = getString(R.string.aspect_ratio_description, aspect)
        // A rebind waits for a photo being saved, so the ratio cannot change under the shutter.
        val choosable = ready && !state.isSaving
        controls.aspectButton.isEnabled = choosable
        // The ratios under the top bar, the chosen one selected (FR-1.13).
        controls.aspectPanel.isVisible = state.aspectsOpen
        aspectsBack?.isEnabled = state.aspectsOpen
        for ((choice, option) in aspectOptions(controls)) {
            option.isSelected = choice == state.aspect
            option.isEnabled = choosable
        }

        val range = state.exposureRange
        val hasBrightness = range.first < range.last
        controls.brightnessButton.isVisible = hasBrightness
        controls.brightnessButton.isChecked = state.brightnessOpen
        controls.brightnessButton.isEnabled = ready
        controls.brightnessPanel.isVisible = state.brightnessOpen
        brightnessSlider?.render(
            1f,
            ExposureScale.sliderMax(range),
            ExposureScale.sliderValue(state.exposure, range),
            enabled = ready && hasBrightness,
        )
        controls.brightnessValue.text = formatEv(state.exposure)
    }

    /** The ratios under the top bar in their order: the tallest first (FR-1.13). */
    private fun aspectOptions(controls: FragmentCameraBinding): List<Pair<AspectRatio, View>> = listOf(
        AspectRatio.R16_9 to controls.aspect916Option,
        AspectRatio.R4_3 to controls.aspect34Option,
        AspectRatio.R1_1 to controls.aspect11Option,
    )

    /** A ratio as the phone is held, upright: 3:4, 9:16, or 1:1. */
    private fun aspectLabel(aspect: AspectRatio): String = getString(
        when (aspect) {
            AspectRatio.R4_3 -> R.string.aspect_3_4
            AspectRatio.R16_9 -> R.string.aspect_9_16
            AspectRatio.R1_1 -> R.string.aspect_1_1
        },
    )

    /**
     * A ratio chosen under the top bar (FR-1.13): a new one holds the preview's last frame while the camera rebinds, as
     * the ratio's change moves it (reframe); the chosen one only closes the ratios.
     */
    private fun chooseAspect(aspect: AspectRatio) {
        val state = viewModel.uiState.value
        if (aspect != state.aspect && !state.isSaving) {
            previewHold?.hold()
            veilStill(flip = false)
        }
        viewModel.onAspectChosen(aspect)
    }

    /** Whether [event], in [root]'s coordinates, is on this view as it shows. */
    private fun View.isUnder(event: MotionEvent, root: View): Boolean {
        if (!isShown) return false
        val at = IntArray(2).also(::getLocationInWindow)
        val origin = IntArray(2).also(root::getLocationInWindow)
        val x = event.x + origin[0] - at[0]
        val y = event.y + origin[1] - at[1]
        return x >= 0f && x < width && y >= 0f && y < height
    }

    /** The exposure index for a brightness slider position (ExposureScale, shared with Subject view). */
    private fun sliderIndex(value: Float): Int = ExposureScale.index(value, viewModel.uiState.value.exposureRange)

    /** An exposure index in EV, such as "+0.7" or "0". */
    private fun formatEv(index: Int): String = ExposureScale.formatEv(index, viewModel.uiState.value.exposureStep)

    /** A tap on the preview: focus there if it is inside the camera image, and show where (FR-1.4). */
    private fun onPreviewTap(x: Float, y: Float) {
        val controls = binding ?: return
        val state = viewModel.uiState.value
        if (state.cameraStatus != CameraStatus.READY || !state.supportsFocusPoint || isScenePhotoActive()) return
        val preview = controls.previewView
        val frame = fitViewfinderFrame(preview.width.toFloat(), preview.height.toFloat(), state.aspect.frameAspect)
        if (frame.width <= 0f || x !in frame.left..frame.right || y !in frame.top..frame.bottom) return
        viewModel.onFocusTap((x - frame.left) / frame.width, (y - frame.top) / frame.height)
        val ring = controls.focusRing
        ring.animate().cancel()
        ring.translationX = x - ring.layoutParams.width / 2f
        ring.translationY = y - ring.layoutParams.height / 2f
        ring.alpha = 1f
        ring.scaleX = FOCUS_RING_START_SCALE
        ring.scaleY = FOCUS_RING_START_SCALE
        ring.isVisible = true
        ring.animate().scaleX(1f).scaleY(1f).setDuration(FOCUS_RING_SHRINK_MS).withEndAction {
            ring.animate().alpha(0f).setStartDelay(FOCUS_RING_HOLD_MS).setDuration(FOCUS_RING_FADE_MS)
                .withEndAction { ring.isVisible = false }
        }
    }

    /**
     * The live light (FR-6.8): on while a session is [connected] and the camera streams to it, so not over a camera
     * message. A rebind (a lens or a ratio) keeps it, as the held frame stands for a camera about to stream again.
     */
    private fun renderLiveDot(controls: FragmentCameraBinding, connected: SessionState.Connected?) {
        controls.liveDot.isVisible = connected != null && !controls.cameraMessagePanel.isVisible
        connected?.let { controls.liveDot.contentDescription = getString(R.string.live_badge, it.peer.displayName) }
    }

    /** Puts the preview's last frame over it while the camera rebinds (PreviewHold); false without a frame. */
    private fun showStill(): Boolean {
        val controls = binding ?: return false
        val frame = previewFrame() ?: return false
        endMove()
        // One still fading out is replaced.
        clearStill(controls)
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        controls.aspectStill.setImageBitmap(heldFrame(frame, aspect))
        // The bitmap is the whole camera image; show the old frame only, as the preview did.
        controls.aspectStill.clipBounds = controls.previewView.clipBounds
        controls.aspectStill.isVisible = true
        controls.stillGround.isVisible = true
        // The grid stays over a held frame of the same ratio; a 1:1 one has its outside dimmed already.
        controls.compositionOverlay.dimOutside = false
        stillAspect = aspect
        stillPlace = shown
        Timings.mark("preview.still", "on")
        stillJob?.cancel()
        // If the stream never reports a restart, the old frame still goes.
        stillJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(STILL_TIMEOUT_MS)
            previewHold?.release()
            render(viewModel.uiState.value)
        }
        return true
    }

    private fun hideStill() {
        Timings.mark("preview.still", "off")
        stillJob?.cancel()
        stillJob = null
        // A frame still moving or turning goes once it is still (stillSettled).
        if (move == null && flip == null) dropStill()
    }

    /** A move or a turn has ended: the held frame goes now if the camera streams again. */
    private fun stillSettled() {
        if (move != null || flip != null || previewHold?.holding == true) return
        dropStill()
        render(viewModel.uiState.value)
    }

    /**
     * How the held frame waits while the camera rebinds for a new ratio or the other camera (Design 2.6.2): dimmed,
     * and blurred where Android can (12 and later); for the other camera it also turns over, top over bottom
     * ([flip]). A lens change of the zoom leaves it as it is.
     */
    private fun veilStill(flip: Boolean) {
        val controls = binding ?: return
        if (previewHold?.holding != true || !controls.aspectStill.isVisible) return
        if (flip) flipStill(controls.aspectStill)
        if (stillVeiled) return
        stillVeiled = true
        val scrim = controls.previewScrim
        if (!scrim.isVisible) {
            scrim.alpha = 0f
            scrim.isVisible = true
        }
        scrim.fadeTo(HOLD_DIM, Motion.DIM_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurStill(controls.aspectStill)
    }

    /**
     * Turns the held frame over, top over bottom, in [Motion.FLIP_MS], as the other camera opens (FR-1.14): to its edge,
     * then from the other edge back to flat, so its face shows throughout. Not while animations are off.
     */
    private fun flipStill(still: ImageView) {
        if (!Motion.on()) return
        flip?.let {
            flip = null
            it.cancel()
        }
        flip = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.FLIP_MS
            interpolator = Motion.STANDARD
            addUpdateListener {
                val turned = it.animatedValue as Float
                still.rotationX = if (turned < 0.5f) HALF_TURN * turned else HALF_TURN * turned - HALF_TURN
            }
            doOnEnd { ended ->
                if (flip !== ended) return@doOnEnd
                flip = null
                still.rotationX = 0f
                stillSettled()
            }
            start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurStill(still: ImageView) {
        val radius = STILL_BLUR_DP * resources.displayMetrics.density
        if (!Motion.on()) {
            setStillBlur(still, radius)
            return
        }
        blur = ValueAnimator.ofFloat(0f, radius).apply {
            duration = Motion.DIM_MS
            addUpdateListener { setStillBlur(still, it.animatedValue as Float) }
            start()
        }
    }

    private fun setStillBlur(still: ImageView, radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || stillBlur == radius) return
        stillBlur = radius
        still.setRenderEffect(
            if (radius < MIN_BLUR_PX) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
    }

    /**
     * Takes the held frame down for the camera's own preview: faded after a new ratio or the other camera, with the
     * grid and the guide fading back in after a ratio change; at once after a lens change of the zoom, which goes on
     * under the fingers, while animations are off, and when the screen goes.
     */
    private fun dropStill() {
        val controls = binding ?: return
        val overlaysBack = reframing
        reframing = false
        val still = controls.aspectStill
        if (!still.isVisible) return
        if (!stillVeiled || previewHold == null || !Motion.on()) {
            clearStill(controls)
            return
        }
        // The new preview shows through as the held frame fades.
        controls.stillGround.isVisible = false
        if (overlaysBack) {
            for (overlay in listOf(controls.guideOverlay, controls.compositionOverlay)) {
                overlay.alpha = 0f
                overlay.fadeTo(1f, Motion.FADE_MS)
            }
        } else if (stillAspect?.let { it.previewAspect != it.frameAspect } == true) {
            // A lens switch in 1:1: the grid dims the outside again at once, so the frame fades inside it only.
            still.clipBounds = shown?.frameClip()
        }
        still.fadeTo(0f, Motion.FADE_MS) { binding?.let(::clearStill) }
        controls.previewScrim.fadeTo(0f, Motion.FADE_MS)
    }

    private fun clearStill(controls: FragmentCameraBinding) {
        blur?.let {
            blur = null
            it.cancel()
        }
        flip?.let {
            flip = null
            it.cancel()
        }
        val still = controls.aspectStill
        setStillBlur(still, 0f)
        still.animate().cancel()
        still.isVisible = false
        still.setImageDrawable(null)
        still.imageMatrix = null
        still.alpha = 1f
        still.rotationX = 0f
        controls.stillGround.isVisible = false
        controls.previewScrim.animate().cancel()
        controls.previewScrim.alpha = 0f
        controls.previewScrim.isVisible = false
        stillVeiled = false
        stillAspect = null
        stillPlace = null
    }

    /** The preview blinks as a photo is taken (FR-1.5), down and back in [Motion.BLINK_MS]; not without animations. */
    private fun blinkPreview(scrim: View) {
        if (!Motion.on() || binding?.aspectStill?.isVisible == true) return
        scrim.alpha = 0f
        scrim.isVisible = true
        scrim.fadeTo(SHUTTER_DIM, Motion.BLINK_MS / 3) {
            scrim.fadeTo(0f, Motion.BLINK_MS * 2 / 3) { scrim.isVisible = false }
        }
    }

    /** A new photo's thumbnail grows in from [THUMBNAIL_START_SCALE] (FR-1.6). */
    private fun growIn(view: View) {
        if (!Motion.on()) return
        view.animate().cancel()
        view.scaleX = THUMBNAIL_START_SCALE
        view.scaleY = THUMBNAIL_START_SCALE
        view.alpha = 0f
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(Motion.FADE_MS).setInterpolator(Motion.STANDARD)
            .start()
    }

    /**
     * Clips the preview to where its image shows (AD-10): the frame, or for 1:1 the whole 4:3 area with the rest
     * dimmed (R&S 6.2), and puts the live light in the frame's corner. Nothing is clipped before layout.
     */
    private fun fitToFrame(controls: FragmentCameraBinding) {
        val place = shown
        val clip = place?.clip()
        if (controls.previewView.clipBounds != clip) controls.previewView.clipBounds = clip
        if (controls.previewScrim.clipBounds != clip) controls.previewScrim.clipBounds = clip
        val aspect = shownAspect ?: viewModel.uiState.value.aspect
        // A held 1:1 frame has its outside dimmed already (heldFrame), and the grid stays over it during a lens switch.
        controls.compositionOverlay.dimOutside =
            aspect.previewAspect != aspect.frameAspect && previewHold?.holding != true
        place?.let { controls.liveDot.placeAt(it.frame.inArea(it.area)) }
    }

    /** What of the area shows, in the area's coordinates; null when all of it does. */
    private fun FramePlace.clip(): Rect? = if (showsArea) null else shows.inArea(area).toRect()

    /** The frame alone, in the area's coordinates. */
    private fun FramePlace.frameClip(): Rect = frame.inArea(area).toRect()

    private fun ViewfinderFrame.inArea(area: PreviewArea) = copy(top = top - area.top)

    private fun ViewfinderFrame.toRect() =
        Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

    /** The hint chip over the preview: what to do next, with an icon in the color of what it is about. */
    private fun showHint(
        chip: TextView,
        @StringRes text: Int?,
        @DrawableRes icon: Int = 0,
        @ColorRes tint: Int = R.color.pix_text,
    ) {
        if (text == null) chip.text = null else chip.setText(text)
        chip.isInvisible = text == null
        setChipIcon(chip, icon)
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(requireContext().getColor(tint)))
    }

    /** Sets [icon] at the start of [chip], at the size of the top bar's icons, or removes it for 0. */
    private fun setChipIcon(chip: TextView, @DrawableRes icon: Int) {
        val size = (CHIP_ICON_DP * resources.displayMetrics.density).roundToInt()
        val drawable = if (icon == 0) null else AppCompatResources.getDrawable(requireContext(), icon)?.mutate()
        drawable?.setBounds(0, 0, size, size)
        chip.setCompoundDrawablesRelative(drawable, null, null, null)
    }

    private enum class StatusLook { PROGRESS, SUCCESS, ERROR }

    /** The status chip: saving, "Saved without the guide" in green, or why a photo failed in the danger color. */
    private fun showStatus(chip: TextView, @StringRes text: Int, look: StatusLook) {
        val context = requireContext()
        chip.isVisible = true
        chip.setText(text)
        chip.setBackgroundResource(
            when (look) {
                StatusLook.PROGRESS -> R.drawable.bg_chip_glass
                StatusLook.SUCCESS -> R.drawable.bg_chip_success
                StatusLook.ERROR -> R.drawable.bg_chip_danger
            },
        )
        chip.setTextColor(context.getColor(if (look == StatusLook.ERROR) R.color.pix_danger_text else R.color.pix_text))
        setChipIcon(
            chip,
            when (look) {
                StatusLook.PROGRESS -> 0
                StatusLook.SUCCESS -> R.drawable.ic_check_circle_small
                StatusLook.ERROR -> R.drawable.ic_alert_small
            },
        )
        // The check keeps its own two colors; the alert takes the text color.
        TextViewCompat.setCompoundDrawableTintList(
            chip,
            if (look == StatusLook.ERROR) ColorStateList.valueOf(context.getColor(R.color.pix_danger_text)) else null,
        )
    }

    /**
     * Sets [icon] on [button] only when it differs from the one set last: each call inflates a new drawable and lays
     * the button out again, and the screen renders on every zoom step of a pinch.
     */
    private fun setIconOnce(button: MaterialButton, @DrawableRes icon: Int) {
        if (button.getTag(R.id.icon_resource) == icon) return
        button.setIconResource(icon)
        button.setTag(R.id.icon_resource, icon)
    }

    private fun showNotice(controls: FragmentCameraBinding, text: String) {
        controls.noticeText.text = text
        controls.noticeText.isVisible = true
        noticeJob?.cancel()
        noticeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(CameraViewModel.NOTICE_MS)
            controls.noticeText.isVisible = false
        }
    }

    private fun openLastPhoto() {
        val uri = viewModel.uiState.value.lastPhoto ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/jpeg")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        } catch (_: ActivityNotFoundException) {
            binding?.let { Snackbar.make(it.root, R.string.gallery_unavailable, Snackbar.LENGTH_LONG).show() }
        } catch (_: SecurityException) {
            binding?.let { Snackbar.make(it.root, R.string.photo_unavailable, Snackbar.LENGTH_LONG).show() }
        }
    }

    companion object {
        private const val PERMISSION_REQUESTED = "camera.permissionRequested"
        private const val STILL_TIMEOUT_MS = 2_000L
        private const val HOLD_DIM = 0.5f
        private const val SHUTTER_DIM = 0.6f
        private const val STILL_BLUR_DP = 16f
        private const val MIN_BLUR_PX = 0.5f
        private const val HALF_TURN = 180f
        private const val FLIP_CAMERA_DISTANCE = 8_000f
        private const val THUMBNAIL_START_SCALE = 0.6f
        private const val THUMBNAIL_PX = 144
        private const val FOCUS_RING_START_SCALE = 1.25f
        private const val FOCUS_RING_SHRINK_MS = 150L
        private const val FOCUS_RING_HOLD_MS = 700L
        private const val FOCUS_RING_FADE_MS = 200L
        private const val CHIP_ICON_DP = 14f
    }
}
