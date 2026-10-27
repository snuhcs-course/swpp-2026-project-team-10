// AI-generated with ChatGPT Codex and Claude Code, 2026-10-27, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.view.PreviewView
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
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.session.SessionNotice
import com.lastpenguin.pix.ui.session.SessionViewModel
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Camera screen. Guide gestures belong to #6; the scene photo for poses belongs to #7; live session UI belongs to #8. */
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val generationViewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private var binding: FragmentCameraBinding? = null
    private var sceneBack: OnBackPressedCallback? = null
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

    // The frame's ratio when the held frame was taken: the grid and the guide stay over a held frame of that ratio.
    private var stillAspect: AspectRatio? = null

    // The preview's last frame for PreviewHold; a test stands in for PreviewView, which has no surface on the JVM.
    @VisibleForTesting
    internal var previewFrame: () -> Bitmap? = { binding?.previewView?.bitmap }

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
        // PreviewView fits the viewport's crop but still draws the camera image around it: show only the frame, or,
        // for 1:1, the whole 4:3 area with the rest dimmed.
        controls.previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            fitToFrame(controls, placedAspect())
        }
        // The preview's place follows the screen's height and the bars' (R&S 6.2), known only after layout.
        val placePreview = View.OnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                view.post { binding?.let { layoutPreview(it, placedAspect()) } }
            }
        }
        controls.root.addOnLayoutChangeListener(placePreview)
        controls.topBar.addOnLayoutChangeListener(placePreview)
        controls.bottomPanel.addOnLayoutChangeListener(placePreview)
        // The zoom row stays above the panels over the shutter as they open and close.
        controls.bottomOverlays.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeZoomRow(controls) }
        controls.previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            Timings.mark("preview.stream", state.name)
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
        controls.flashButton.setOnClickListener { viewModel.onFlashTap() }
        controls.aspectButton.setOnClickListener {
            previewHold?.hold()
            viewModel.onAspectTap()
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
        // Friends, which also offers the room code without signing in (FR-8.1).
        controls.shootTogetherButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_friends)
        }
        controls.shutterButton.setOnClickListener {
            // While the scene photo for poses is framed, the shutter takes that photo and saves nothing.
            if (generationViewModel.uiState.value.phase == GenerationUiState.Phase.FRAMING) {
                generationViewModel.takeScene()
            } else {
                viewModel.onShutter()
            }
        }
        setupScenePhoto(controls)
        controls.galleryButton.setOnClickListener { openLastPhoto() }
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

        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { generationViewModel.uiState.collect { render(viewModel.uiState.value) } }
                // The photographer's side of a session: the live light, End session, and "left" notices (FR-6.8, FR-6.10).
                launch {
                    sessionViewModel.state.collect { state ->
                        val connected = state as? SessionState.Connected
                        renderLiveDot(controls, connected)
                        controls.endSessionButton.isVisible = connected != null
                        controls.shootTogetherButton.isVisible = connected == null && !isScenePhotoActive()
                        view.keepScreenOn = connected != null
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
                        }
                        showNotice(controls, text)
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
                    viewModel.uiState.map { it.lastPhoto }.distinctUntilChanged().collectLatest { uri ->
                        val thumbnail = if (uri == null) {
                            null
                        } else {
                            withContext(Dispatchers.IO) {
                                try {
                                    resolver.loadThumbnail(uri, Size(144, 144), null)
                                } catch (_: Exception) {
                                    null // The full photo can still be opened if thumbnail decoding fails.
                                }
                            }
                        }
                        if (thumbnail == null) {
                            controls.galleryThumbnail.setImageResource(R.drawable.gallery_placeholder)
                        } else {
                            controls.galleryThumbnail.setImageBitmap(thumbnail)
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
        previewHold?.release()
        previewHold = null
        binding = null
        sceneBack = null
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
        ViewCompat.setAccessibilityDelegate(
            thumbnail,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    val state = viewModel.uiState.value
                    val label = if (state.referenceExpanded) R.string.shrink_reference else R.string.enlarge_reference
                    info.addAction(
                        AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK, getString(label)),
                    )
                    for ((corner, move) in REFERENCE_MOVES) {
                        if (corner != state.referenceCorner) {
                            info.addAction(AccessibilityActionCompat(move.first, getString(move.second)))
                        }
                    }
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    val corner = REFERENCE_MOVES.entries.firstOrNull { it.value.first == action }?.key
                        ?: return super.performAccessibilityAction(host, action, args)
                    viewModel.setReferenceCorner(corner)
                    return true
                }
            },
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

    /**
     * Puts the preview where previewArea says for [aspect] (R&S 6.2), and the zoom row at the bottom of its image. The
     * controls get a dark gradient while they lie over it. Margins change only when the place does, as the screen
     * renders on every zoom step of a pinch.
     */
    private fun layoutPreview(controls: FragmentCameraBinding, aspect: AspectRatio) {
        val root = controls.root
        if (root.width == 0 || root.height == 0) return
        val area = previewArea(
            root.width.toFloat(),
            root.height.toFloat(),
            controls.topBar.height - TOP_BAR_OVERHANG_DP * resources.displayMetrics.density,
            controls.bottomPanel.height.toFloat(),
            aspect.previewAspect,
        )
        previewArea = area
        val container = controls.previewContainer
        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        val top = area.top.roundToInt()
        val bottom = (root.height - area.bottom).roundToInt()
        if (params.topMargin != top || params.bottomMargin != bottom) {
            params.topMargin = top
            params.bottomMargin = bottom
            container.layoutParams = params
        }
        controls.bottomPanel.setBackgroundResource(if (area.underControls > 0f) R.drawable.bg_scrim_bottom else 0)
        placeZoomRow(controls)
    }

    /** The zoom row at the bottom of the image as it shows (1:1 inside the 4:3 area), above the panels and controls. */
    private fun placeZoomRow(controls: FragmentCameraBinding) {
        val area = previewArea
        if (area.bottom <= area.top) return
        val frame = fitViewfinderFrame(
            controls.root.width.toFloat(),
            area.bottom - area.top,
            placedAspect().frameAspect,
        )
        val imageBottom = minOf(area.top + frame.bottom, area.bottom - area.underControls)
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
            val reviewing = generationViewModel.uiState.value.phase == GenerationUiState.Phase.REVIEWING
            if (reviewing && navController.currentDestination?.id == R.id.cameraFragment) {
                // Generate poses asks what to pose before anything is sent (FR-4.8).
                generationViewModel.useScene()
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
        // Over a held frame of the same ratio (a lens switch) the grid and the guide stay; after a ratio change they
        // would not fit the old frame.
        val overlays = ready || (previewHold?.holding == true && stillAspect == state.aspect)
        controls.compositionOverlay.isVisible = overlays
        // The grid and the guide follow the chosen frame (AD-10); a new ratio re-fits both at once.
        controls.compositionOverlay.frameAspect = state.aspect.frameAspect
        controls.guideOverlay.frameAspect = state.aspect.frameAspect
        // A held frame keeps the old ratio's place until the new one streams, as a shorter area would cut it.
        layoutPreview(controls, placedAspect())
        fitToFrame(controls, placedAspect())
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
        when {
            state.guide == null ->
                showHint(controls.hintText, R.string.hint_add_guide, R.drawable.ic_target_small, R.color.pix_accent)

            !state.guideState.visible ->
                showHint(controls.hintText, R.string.hint_guide_hidden, R.drawable.ic_visibility_off_small)

            state.guideEditing ->
                showHint(controls.hintText, R.string.hint_edit_guide, R.drawable.ic_move_small, R.color.pix_guide)

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
        renderScenePhoto(controls, generationViewModel.uiState.value)
    }

    /**
     * The pose tip (FR-4.10): a touch anywhere closes the banner and still does what it does where it lands; the tip
     * button opens it again. The banner is clickable as well, so TalkBack can close it.
     */
    private fun setupTip(controls: FragmentCameraBinding) {
        (controls.root as TouchWatchingLayout).onTouchEnd = viewModel::onAnyTouch
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
        val thumbnail = controls.referenceThumbnail
        // The scene photo frames a new scene, which an earlier guide's reference is not part of.
        val reference = state.guide?.reference?.takeUnless { isScenePhotoActive() }
        val shown = thumbnail.isVisible
        thumbnail.isVisible = reference != null
        // The enlarged photo reaches under the chips: the hint steps aside, and a capture status stays on top.
        controls.hintText.isInvisible = reference != null && state.referenceExpanded
        if (reference == null) {
            thumbnail.setImageDrawable(null)
            return
        }
        // Set only when it changes: a new drawable would lay the screen out again on every render.
        if ((thumbnail.drawable as? BitmapDrawable)?.bitmap !== reference) thumbnail.setImageBitmap(reference)
        if (referenceDrag?.isDragging == true || controls.previewContainer.width == 0) return
        val placement = placeReference(
            aspect = reference.width.toFloat() / reference.height,
            expanded = state.referenceExpanded,
            corner = state.referenceCorner,
            smallLongSide = (REFERENCE_SMALL_DP * resources.displayMetrics.density).roundToInt(),
            previewWidth = controls.previewContainer.width,
            area = referenceArea(controls),
        )
        val params = thumbnail.layoutParams
        if (params.width != placement.width || params.height != placement.height) {
            params.width = placement.width
            params.height = placement.height
            thumbnail.layoutParams = params
        }
        val x = placement.x.toFloat()
        val y = placement.y.toFloat()
        if (!shown) {
            thumbnail.animate().cancel()
            thumbnail.translationX = x
            thumbnail.translationY = y
        } else if (thumbnail.translationX != x || thumbnail.translationY != y) {
            thumbnail.animate().translationX(x).translationY(y).setDuration(REFERENCE_MOVE_MS).start()
        }
    }

    /** Whether the scene photo for poses is being framed or reviewed; the top bar is its title then. */
    private fun isScenePhotoActive(scene: GenerationUiState = generationViewModel.uiState.value): Boolean =
        scene.phase == GenerationUiState.Phase.REVIEWING || scene.phase == GenerationUiState.Phase.FRAMING

    /**
     * The scene photo for poses is taken and confirmed on this screen (R&S 6.3, FR-4.1): the live camera with the
     * shutter, then the photo with *Use this photo* and *Shoot again*. Applied last, over the camera's own controls,
     * so that the step shows only what it needs.
     */
    private fun renderScenePhoto(controls: FragmentCameraBinding, scene: GenerationUiState) {
        val reviewing = scene.phase == GenerationUiState.Phase.REVIEWING
        val active = isScenePhotoActive(scene)
        sceneBack?.isEnabled = active
        controls.sceneTitle.isVisible = active
        controls.sceneCancelButton.isVisible = active
        controls.shootTogetherButton.isVisible = !active && sessionViewModel.state.value !is SessionState.Connected
        // Guide and the thumbnail keep their place, so the shutter stays where it is.
        controls.guideButton.isInvisible = active
        controls.galleryButton.isInvisible = active
        controls.sceneStill.isVisible = reviewing
        controls.sceneStill.setImageBitmap(if (reviewing) scene.scene else null)
        // The photo is taken: no zoom until it is used or shot again (the pinch's enabled state is set in render).
        controls.zoomReadout.isInvisible = reviewing
        // The scene photo is a frame of the live preview: flash, ratio, and brightness do not apply to it.
        if (active) {
            controls.cameraSettings.isVisible = false
            controls.brightnessButton.isVisible = false
            controls.brightnessPanel.isVisible = false
        }
        if (active) {
            controls.editGuideButton.isVisible = false
            controls.tipButtonFrame.isVisible = false
        }
        controls.captureControls.isVisible = !reviewing
        controls.sceneReviewControls.isVisible = reviewing
        if (!active) return

        controls.sceneTitle.setText(if (reviewing) R.string.scene_review_title else R.string.scene_title)
        showHint(controls.hintText, if (reviewing) R.string.scene_review_body else R.string.scene_body)
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
        val aspect = getString(
            when (state.aspect) {
                AspectRatio.R4_3 -> R.string.aspect_4_3
                AspectRatio.R16_9 -> R.string.aspect_16_9
                AspectRatio.R1_1 -> R.string.aspect_1_1
            },
        )
        controls.aspectButton.text = aspect
        controls.aspectButton.contentDescription = getString(R.string.aspect_ratio_description, aspect)
        // A rebind waits for a photo being saved, so the ratio cannot change under the shutter.
        controls.aspectButton.isEnabled = ready && !state.isSaving

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

    /** The ratio the preview is placed for: a held frame's while it shows, then the chosen one. */
    private fun placedAspect(): AspectRatio =
        stillAspect.takeIf { previewHold?.holding == true } ?: viewModel.uiState.value.aspect

    /** Puts the preview's last frame over it while the camera rebinds (PreviewHold); false without a frame. */
    private fun showStill(): Boolean {
        val controls = binding ?: return false
        val frame = previewFrame() ?: return false
        val aspect = viewModel.uiState.value.aspect
        controls.aspectStill.setImageBitmap(heldFrame(frame, aspect))
        // The bitmap is the whole camera image; show the old frame only, as the preview did.
        controls.aspectStill.clipBounds = controls.previewView.clipBounds
        controls.aspectStill.isVisible = true
        // The grid stays over a held frame of the same ratio; a 1:1 one has its outside dimmed already.
        controls.compositionOverlay.dimOutside = false
        stillAspect = aspect
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
        binding?.aspectStill?.let {
            it.isVisible = false
            it.setImageDrawable(null)
        }
    }

    /**
     * Clips the preview to the fit-centered frame of [aspect] (AD-10), except 1:1, which shows the whole 4:3 area with
     * the rest dimmed (R&S 6.2), and puts the live light in the frame's corner. Nothing is clipped before layout.
     */
    private fun fitToFrame(controls: FragmentCameraBinding, aspect: AspectRatio) {
        val view = controls.previewView
        val frame = fitViewfinderFrame(view.width.toFloat(), view.height.toFloat(), aspect.frameAspect)
        val clip = if (frame.width > 0f && aspect.previewAspect == aspect.frameAspect) {
            Rect(frame.left.roundToInt(), frame.top.roundToInt(), frame.right.roundToInt(), frame.bottom.roundToInt())
        } else {
            null
        }
        if (view.clipBounds != clip) view.clipBounds = clip
        // A held 1:1 frame has its outside dimmed already (heldFrame), and the grid stays over it during a lens switch.
        controls.compositionOverlay.dimOutside =
            aspect.previewAspect != aspect.frameAspect && previewHold?.holding != true
        controls.liveDot.placeIn(controls.previewContainer, aspect.frameAspect)
    }

    /** The hint chip over the preview: what to do next, with an icon in the color of what it is about. */
    private fun showHint(
        chip: TextView,
        @StringRes text: Int,
        @DrawableRes icon: Int = 0,
        @ColorRes tint: Int = R.color.pix_text,
    ) {
        chip.setText(text)
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
        private const val FOCUS_RING_START_SCALE = 1.25f
        private const val FOCUS_RING_SHRINK_MS = 150L
        private const val FOCUS_RING_HOLD_MS = 700L
        private const val FOCUS_RING_FADE_MS = 200L
        private const val CHIP_ICON_DP = 14f

        /** The reference thumbnail's long side while small, and its margin in the preview's corners (FR-3.9). */
        private const val REFERENCE_SMALL_DP = 72f
        private const val REFERENCE_MARGIN_DP = 12f
        private const val REFERENCE_MOVE_MS = 150L

        /** TalkBack's way to move the reference thumbnail: an action id and its label for each corner. */
        private val REFERENCE_MOVES = mapOf(
            ReferenceCorner.TOP_LEFT to (R.id.move_reference_top_left to R.string.move_reference_top_left),
            ReferenceCorner.TOP_RIGHT to (R.id.move_reference_top_right to R.string.move_reference_top_right),
            ReferenceCorner.BOTTOM_LEFT to (R.id.move_reference_bottom_left to R.string.move_reference_bottom_left),
            ReferenceCorner.BOTTOM_RIGHT to (R.id.move_reference_bottom_right to R.string.move_reference_bottom_right),
        )
    }
}
