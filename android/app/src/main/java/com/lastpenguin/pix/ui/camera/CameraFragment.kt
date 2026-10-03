package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.chip.ChipGroup
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Camera: the start screen. Also Camera + guide, Photo saved, and the photographer's live session
 * (R&S 6.2, 6.5). The camera binds to this view's lifecycle, so it is released while another screen is open.
 * Owner: Camera/Overlay (#3, #6).
 */
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var binding: FragmentCameraBinding? = null
    private var cameraBound = false
    private var noticeJob: Job? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) bindCamera() else binding?.permissionDenied?.isVisible = true
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCameraBinding.bind(view).also { this.binding = it }
        cameraBound = false

        binding.guideButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_addGuide)
        }
        binding.shootTogetherButton.setOnClickListener {
            sessionViewModel.startRoom()
            findNavController().navigate(R.id.action_camera_to_roomCode)
        }
        binding.shutterButton.setOnClickListener { viewModel.onShutter() }
        binding.galleryButton.setOnClickListener { viewModel.uiState.value.lastPhoto?.let(::openPhoto) }
        binding.openSettingsButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null),
                ),
            )
        }
        binding.guideOverlay.onGesture = viewModel::onGuideGesture
        binding.styleToggleButton.setOnClickListener { viewModel.onStyleToggle() }
        binding.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        // TODO(#6): opacitySlider → viewModel.onOpacityChange; render uiState.guide on guideOverlay; guideControls.
        binding.endSessionButton.setOnClickListener { sessionViewModel.leave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { ui ->
                        renderZoomChips(binding.zoomChips, ui.zoomStops, ui.zoom)
                        binding.galleryButton.isEnabled = ui.lastPhoto != null
                    }
                }
                launch {
                    viewModel.notices.collect { notice ->
                        val text = when (notice) {
                            CameraNotice.PhotoSaved -> getString(R.string.photo_saved)
                            is CameraNotice.SaveFailed -> getString(R.string.photo_save_failed)
                        }
                        showNotice(binding.noticeText, text)
                    }
                }
                // The photographer's side of a session: the live badge, End session, and "left" notices (FR-6.8, FR-6.10).
                launch {
                    sessionViewModel.state.collect { state ->
                        val connected = state as? SessionState.Connected
                        binding.liveBadge.isVisible = connected != null
                        binding.endSessionButton.isVisible = connected != null
                        connected?.let { binding.liveBadge.text = getString(R.string.live_badge, it.peer.displayName) }
                        view.keepScreenOn = connected != null
                    }
                }
                launch {
                    sessionViewModel.notices.collect { notice ->
                        val text = when (notice.reason) {
                            EndReason.CONNECTION_LOST -> R.string.peer_disconnected
                            else -> R.string.peer_left
                        }
                        showNotice(binding.noticeText, getString(text, notice.name))
                    }
                }
            }
        }

        if (hasCameraPermission()) bindCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onStart() {
        super.onStart()
        // Back from the system settings after granting the permission (FR-1.2).
        if (!cameraBound && hasCameraPermission()) bindCamera()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun bindCamera() {
        val binding = binding ?: return
        cameraBound = true
        binding.permissionDenied.isVisible = false
        viewModel.bindCamera(viewLifecycleOwner, binding.previewView.surfaceProvider)
    }

    /** One chip per zoom stop; the chip for the applied zoom is checked (FR-1.3). */
    private fun renderZoomChips(group: ChipGroup, stops: List<Float>, zoom: Float) {
        val shown = (0 until group.childCount).map { group.getChildAt(it).tag as Float }
        if (shown != stops) {
            group.removeAllViews()
            for (stop in stops) {
                val chip = layoutInflater.inflate(R.layout.chip_zoom, group, false) as Chip
                chip.id = View.generateViewId()
                chip.tag = stop
                chip.text = getString(R.string.zoom_chip, formatZoom(stop))
                chip.setOnClickListener { viewModel.onZoomChip(stop) }
                group.addView(chip)
            }
        }
        val match = (0 until group.childCount).map { group.getChildAt(it) }.firstOrNull {
            abs(it.tag as Float - zoom) <
                ZOOM_MATCH
        }
        if (match == null) {
            group.clearCheck()
        } else if (group.checkedChipId != match.id) {
            group.check(match.id)
        }
    }

    private fun formatZoom(ratio: Float): String =
        if (ratio == ratio.toInt().toFloat()) ratio.toInt().toString() else String.format(Locale.US, "%.1f", ratio)

    private fun openPhoto(uri: Uri) {
        val intent = Intent(
            Intent.ACTION_VIEW,
        ).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showNotice(binding?.noticeText ?: return, getString(R.string.no_gallery_app))
        }
    }

    private fun showNotice(view: TextView, text: String) {
        view.text = text
        view.isVisible = true
        noticeJob?.cancel()
        noticeJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(NOTICE_MS)
            view.isVisible = false
        }
    }

    private companion object {
        const val NOTICE_MS = 3_000L
        const val ZOOM_MATCH = 0.05f
    }
}
