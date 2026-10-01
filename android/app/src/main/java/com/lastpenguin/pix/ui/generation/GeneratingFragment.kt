package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentGeneratingBinding
import com.lastpenguin.pix.ui.PixViewModels

/** Generating poses (R&S 6.3). Owner: Server/AI/Sync (#7). */
class GeneratingFragment : Fragment(R.layout.fragment_generating) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGeneratingBinding.bind(view)

        // TODO(#7): collect viewModel.uiState: fill the slots and readyCount;
        //  PICKING → Pick a pose, FAILED → Couldn't create poses.

        binding.cancelButton.setOnClickListener {
            viewModel.cancel()
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
        binding.tempReadyButton.setOnClickListener {
            findNavController().navigate(R.id.action_generating_to_pickPose)
        }
        binding.tempFailedButton.setOnClickListener {
            findNavController().navigate(R.id.action_generating_to_generationFailed)
        }
    }
}
