package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.session.SessionNotice
import com.lastpenguin.pix.ui.session.SessionViewModel
import java.text.DecimalFormat
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
    private val zoomLimitFormat = DecimalFormat("0.#")
    private var zoomSlider: TrackedSliderController? = null
    private var opacitySlider: TrackedSliderController? = null
    private var previewLogged = false
    private var noticeJob: Job? = null

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
        controls.previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        // TextureView composes predictably with guide and permission/error layers above it.
        controls.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        controls.previewView.previewStreamState.observe(viewLifecycleOwner) { state ->
            if (state == PreviewView.StreamState.STREAMING && !previewLogged) {
                previewLogged = true
                Timings.mark("camera.preview")
            }
        }
        zoomSlider = TrackedSliderController.zoom(controls.zoomControls.zoomSlider) { ratio, _ ->
            viewModel.onZoomChanged(ratio)
        }
        opacitySlider = TrackedSliderController(
            controls.opacitySlider,
            { getString(R.string.guide_opacity_percent, (it * 100f).roundToInt()) },
            viewModel::onOpacityChange,
        )
        setupZoomAccessibility(controls.zoomControls.zoomRatio)
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
        controls.shootTogetherButton.setOnClickListener {
            sessionViewModel.startRoom()
            findNavController().navigate(R.id.action_camera_to_roomCode)
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
        controls.styleToggleButton.setOnClickListener { viewModel.onStyleToggle() }
        controls.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        controls.endSessionButton.setOnClickListener { sessionViewModel.leave() }

        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { generationViewModel.uiState.collect { render(viewModel.uiState.value) } }
                // The photographer's side of a session: the live badge, End session, and "left" notices (FR-6.8, FR-6.10).
                launch {
                    sessionViewModel.state.collect { state ->
                        val connected = state as? SessionState.Connected
                        controls.liveBadge.isVisible = connected != null
                        controls.endSessionButton.isVisible = connected != null
                        controls.shootTogetherButton.isVisible = connected == null && !isScenePhotoActive()
                        connected?.let { controls.liveBadge.text = getString(R.string.live_badge, it.peer.displayName) }
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
                            controls.galleryButton.setImageResource(R.drawable.ic_photo)
                        } else {
                            controls.galleryButton.setImageBitmap(thumbnail)
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
        zoomSlider?.finishInteraction()
        opacitySlider?.finishInteraction()
        super.onPause()
    }

    override fun onDestroyView() {
        zoomSlider?.finishInteraction()
        zoomSlider = null
        opacitySlider?.finishInteraction()
        opacitySlider = null
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
                generationViewModel.useScene()
                navController.navigate(R.id.action_camera_to_generating)
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
        controls.compositionOverlay.isVisible = ready
        controls.guideOverlay.render(state.guide, state.guideState)
        controls.guideOverlay.isVisible = ready
        // Camera + guide (R&S 6.2). Visibility follows the guide alone: a lens switch briefly leaves READY,
        // and hiding the bar then would shift the zoom slider under an active drag.
        val guideEditable = granted && !unavailable
        controls.guideControls.isVisible = state.guide != null
        controls.styleToggleButton.isEnabled = guideEditable
        controls.styleToggleButton.setText(
            if (state.guideState.style == GuideStyle.CUTOUT) R.string.show_outline else R.string.show_cutout,
        )
        opacitySlider?.render(
            GuideState.MIN_OPACITY,
            GuideState.MAX_OPACITY,
            state.guideState.opacity,
            enabled = guideEditable && state.guide != null,
        )
        controls.removeGuideButton.isEnabled = guideEditable
        controls.hintText.setText(if (state.guide == null) R.string.hint_add_guide else R.string.hint_edit_guide)
        controls.cameraMessagePanel.isVisible = !granted || unavailable
        controls.cameraMessage.setText(if (granted) R.string.camera_unavailable else R.string.camera_permission_message)
        controls.cameraActionButton.setText(if (granted) R.string.try_again else R.string.open_settings)
        controls.requestPermissionButton.isVisible =
            !granted && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        controls.cameraProgress.isVisible = granted && !ready && !unavailable
        controls.shutterButton.isEnabled = ready && !state.isSaving
        controls.shutterButton.setText(if (state.isSaving) R.string.saving_photo else R.string.shutter)
        controls.guideButton.isEnabled = !state.isSaving
        controls.shootTogetherButton.isEnabled = !state.isSaving
        controls.galleryButton.isEnabled = state.lastPhoto != null

        val ratio = zoomFormat.format(state.zoom)
        controls.zoomControls.zoomRatio.text = getString(R.string.zoom_ratio, ratio)
        controls.zoomControls.zoomRatio.contentDescription = getString(R.string.camera_zoom_description, ratio)
        controls.zoomControls.zoomRatio.isEnabled = ready && state.maxZoom > state.minZoom
        controls.zoomControls.minZoom.text = getString(R.string.zoom_ratio, zoomLimitFormat.format(state.minZoom))
        controls.zoomControls.maxZoom.text = getString(R.string.zoom_ratio, zoomLimitFormat.format(state.maxZoom))
        // Lens switching briefly enters STARTING: preserve an already active slider drag across that transition.
        val canContinueZoom = granted && state.cameraStatus == CameraStatus.STARTING && zoomSlider?.isTracking == true
        zoomSlider?.render(state.minZoom, state.maxZoom, state.zoom, enabled = ready || canContinueZoom)
        controls.captureStatus.isVisible = state.captureNotice != null
        state.captureNotice?.let {
            controls.captureStatus.setText(
                when (it) {
                    CaptureNotice.SAVED -> R.string.photo_saved
                    CaptureNotice.STORAGE_FAILED -> R.string.photo_storage_failed
                    CaptureNotice.CAMERA_UNAVAILABLE -> R.string.photo_camera_closed
                    CaptureNotice.PERMISSION_REQUIRED -> R.string.camera_permission_message
                    CaptureNotice.SAVE_FAILED -> R.string.photo_save_failed
                },
            )
        }
        renderScenePhoto(controls, generationViewModel.uiState.value)
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
        // The photo is taken: no zoom until it is used or shot again.
        controls.zoomControls.root.isInvisible = reviewing
        controls.captureControls.isVisible = !reviewing
        controls.sceneReviewControls.isVisible = reviewing
        if (!active) return

        controls.sceneTitle.setText(if (reviewing) R.string.scene_review_title else R.string.scene_title)
        controls.hintText.setText(if (reviewing) R.string.scene_review_body else R.string.scene_body)
        // An earlier guide is not part of the scene photo and would be in the way of framing it.
        controls.guideOverlay.isVisible = false
        controls.guideControls.isVisible = false
        controls.captureStatus.isVisible = scene.shotFailed
        controls.captureStatus.setText(R.string.scene_shot_failed)
    }

    private fun showNotice(controls: FragmentCameraBinding, text: String) {
        controls.noticeText.text = text
        controls.noticeText.isVisible = true
        noticeJob?.cancel()
        noticeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(NOTICE_MS)
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
        private const val NOTICE_MS = 3_000L
    }
}
