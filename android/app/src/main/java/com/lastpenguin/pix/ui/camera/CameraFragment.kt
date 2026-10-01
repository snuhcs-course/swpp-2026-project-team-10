package com.lastpenguin.pix.ui.camera

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel

/**
 * Camera: the start screen. Also Camera + guide, Photo saved, and the photographer's live session
 * (R&S 6.2, 6.5). Owner: Camera/Overlay (#3, #6).
 */
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCameraBinding.bind(view)

        // TODO(#3): ask for the camera permission (FR-1.2), add a PreviewView, call viewModel.bindCamera.
        // TODO(#3, #6): collect viewModel.uiState: zoom chips, guide, guideControls, thumbnail, notices.
        // TODO(#8): while sessionViewModel.state is Connected, show liveBadge and endSessionButton.

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
    }
}
