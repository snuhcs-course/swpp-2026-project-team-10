package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels

/** Couldn't create poses (R&S 6.3, FR-4.7). Owner: Server/AI/Sync (#7). */
class GenerationFailedFragment : Fragment(R.layout.fragment_failure) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        // The scene photo is kept for Try again (FR-4.7).
        val scene = viewModel.uiState.value.scene
        if (scene == null) {
            // Nothing failed here: the app process was restarted on this screen and the photo is gone.
            view.post { if (isAdded) backToCamera() }
            return
        }
        binding.failureTitle.setText(R.string.generation_failed_title)
        binding.failureMessage.setText(R.string.generation_failed_message)
        binding.failurePhoto.isVisible = true
        binding.failurePhotoImage.setImageBitmap(scene)

        binding.primaryButton.setText(R.string.try_again)
        binding.primaryButton.setOnClickListener {
            viewModel.retry()
            findNavController().navigate(R.id.action_generationFailed_to_generating)
        }
        binding.secondaryButton.setText(R.string.back_to_camera)
        binding.secondaryButton.setOnClickListener { backToCamera() }
        // System back does the same as Back to camera (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToCamera() }
    }

    private fun backToCamera() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }
}
