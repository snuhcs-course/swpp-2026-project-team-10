package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
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
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionNotice
import com.lastpenguin.pix.ui.session.SessionViewModel
import java.text.DecimalFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Camera screen. Guide gestures belong to #6; live session UI belongs to #8. */
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var binding: FragmentCameraBinding? = null
    private var permissionRequested = false
    private val zoomFormat = DecimalFormat("0.0")
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
        setupPinchZoom(controls.previewView)
        setupZoomAccessibility(controls.zoomRatio)
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
        controls.shutterButton.setOnClickListener { viewModel.onShutter() }
        controls.galleryButton.setOnClickListener { openLastPhoto() }
        controls.guideOverlay.onGesture = viewModel::onGuideGesture
        controls.styleToggleButton.setOnClickListener { viewModel.onStyleToggle() }
        controls.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        // TODO(#6): wire opacitySlider and render guide controls when overlay editing is implemented.
        controls.endSessionButton.setOnClickListener { sessionViewModel.leave() }

        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                // The photographer's side of a session: the live badge, End session, and "left" notices (FR-6.8, FR-6.10).
                launch {
                    sessionViewModel.state.collect { state ->
                        val connected = state as? SessionState.Connected
                        controls.liveBadge.isVisible = connected != null
                        controls.endSessionButton.isVisible = connected != null
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

    override fun onDestroyView() {
        binding = null
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

    // Attach pinch to the preview, not its parent: #6's overlay can own guide gestures.
    @SuppressLint("ClickableViewAccessibility") // The zoom readout exposes accessible zoom actions.
    private fun setupPinchZoom(preview: PreviewView) {
        var requestedZoom = 1f
        val detector =
            ScaleGestureDetector(
                requireContext(),
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        val state = viewModel.uiState.value
                        requestedZoom = state.zoom
                        return state.cameraStatus == CameraStatus.READY && state.maxZoom > state.minZoom
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val state = viewModel.uiState.value
                        val factor = detector.scaleFactor
                        val acceptsZoom = state.cameraStatus == CameraStatus.READY ||
                            state.cameraStatus == CameraStatus.STARTING
                        val validRange = state.minZoom.isFinite() && state.maxZoom.isFinite() &&
                            state.minZoom > 0f && state.maxZoom >= state.minZoom
                        if (!acceptsZoom || !validRange || !factor.isFinite() || factor <= 0f) {
                            return false
                        }
                        // Accumulate gesture deltas without waiting for CameraX's asynchronous state
                        // even while crossing lenses. Clamp each step so reversing at a limit responds immediately.
                        requestedZoom = (requestedZoom * factor).coerceIn(state.minZoom, state.maxZoom)
                        viewModel.onZoomChanged(requestedZoom)
                        return true
                    }
                },
            ).apply {
                isQuickScaleEnabled = false
                isStylusScaleEnabled = false
            }
        preview.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            preview.parent.requestDisallowInterceptTouchEvent(
                detector.isInProgress && event.actionMasked != MotionEvent.ACTION_UP &&
                    event.actionMasked != MotionEvent.ACTION_CANCEL,
            )
            true
        }
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

    private fun render(state: CameraUiState) {
        val controls = binding ?: return
        val granted = hasCameraPermission()
        val ready = granted && state.cameraStatus == CameraStatus.READY
        val unavailable = state.cameraStatus == CameraStatus.UNAVAILABLE
        controls.compositionOverlay.isVisible = ready
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
        controls.zoomRatio.text = getString(R.string.zoom_ratio, ratio)
        controls.zoomRatio.contentDescription = getString(R.string.camera_zoom_description, ratio)
        controls.zoomRatio.isEnabled = ready && state.maxZoom > state.minZoom
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
