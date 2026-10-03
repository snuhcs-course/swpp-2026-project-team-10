package com.lastpenguin.pix.ui.camera

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Camera: the start screen. Also Camera + guide, Photo saved, and the photographer's live session
 * (R&S 6.2, 6.5). Owner: Camera/Overlay (#3, #6).
 */
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var noticeJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCameraBinding.bind(view)

        // TODO(#3): ask for the camera permission (FR-1.2), add a PreviewView, call viewModel.bindCamera.
        // TODO(#3, #6): collect viewModel.uiState: zoom chips, guide, guideControls, thumbnail, notices.

        binding.guideButton.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_addGuide)
        }
        binding.shootTogetherButton.setOnClickListener {
            sessionViewModel.startRoom()
            findNavController().navigate(R.id.action_camera_to_roomCode)
        }
        binding.shutterButton.setOnClickListener { viewModel.onShutter() }
        binding.galleryButton.setOnClickListener {
            // TODO(#3): open the system gallery.
        }
        binding.guideOverlay.onGesture = viewModel::onGuideGesture
        binding.styleToggleButton.setOnClickListener { viewModel.onStyleToggle() }
        binding.removeGuideButton.setOnClickListener { viewModel.onRemoveGuide() }
        // TODO(#6): opacitySlider → viewModel.onOpacityChange.
        binding.endSessionButton.setOnClickListener { sessionViewModel.leave() }

        // The photographer's side of a session: the live badge, End session, and "left" notices (FR-6.8, FR-6.10).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }
}
