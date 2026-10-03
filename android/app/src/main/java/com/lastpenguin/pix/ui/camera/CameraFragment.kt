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
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel
import java.text.DecimalFormat
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
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
    private var renderedZoomStops: List<Float>? = null
    private var previewLogged = false

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
        // TODO(#8): show liveBadge and endSessionButton from session state.

        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
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
        renderedZoomStops = null
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
    @SuppressLint("ClickableViewAccessibility") // Zoom chips provide accessible equivalents to pinch.
    private fun setupPinchZoom(preview: PreviewView) {
        var requestedZoom = 1f
        val detector =
            ScaleGestureDetector(
                requireContext(),
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        requestedZoom = viewModel.uiState.value.zoom
                        return viewModel.uiState.value.cameraStatus == CameraStatus.READY
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val state = viewModel.uiState.value
                        requestedZoom = (requestedZoom * detector.scaleFactor).coerceIn(state.minZoom, state.maxZoom)
                        viewModel.onZoomChip(requestedZoom)
                        return true
                    }
                },
            )
        preview.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            true
        }
    }

    private fun render(state: CameraUiState) {
        val controls = binding ?: return
        val granted = hasCameraPermission()
        val ready = granted && state.cameraStatus == CameraStatus.READY
        val unavailable = state.cameraStatus == CameraStatus.UNAVAILABLE
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

        if (renderedZoomStops != state.zoomStops) {
            renderedZoomStops = state.zoomStops
            controls.zoomChips.removeAllViews()
            state.zoomStops.forEach { ratio ->
                controls.zoomChips.addView(
                    Chip(requireContext()).apply {
                        text = getString(R.string.zoom_ratio, DecimalFormat("0.#").format(ratio))
                        isCheckable = true
                        tag = ratio
                        setEnsureMinTouchTargetSize(true)
                        setOnClickListener {
                            viewModel.onZoomChip(ratio)
                            render(viewModel.uiState.value)
                        }
                    },
                )
            }
        }
        for (index in 0 until controls.zoomChips.childCount) {
            (controls.zoomChips.getChildAt(index) as Chip).apply {
                isEnabled = ready
                isChecked = abs((tag as Float) - state.zoom) < 0.01f
            }
        }
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
    }
}
