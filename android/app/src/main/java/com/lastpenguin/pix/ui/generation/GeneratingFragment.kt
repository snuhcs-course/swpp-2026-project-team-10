package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentGeneratingBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/** Generating poses (R&S 6.3). Owner: Server/AI/Sync (#7). */
class GeneratingFragment : Fragment(R.layout.fragment_generating) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGeneratingBinding.bind(view)
        // No pick callback: candidates cannot be selected until generation ends.
        val candidates = PoseCandidateAdapter()
        binding.candidateGrid.adapter = candidates

        binding.cancelButton.setOnClickListener { cancel() }
        // System back does the same as Cancel (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { cancel() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(binding, candidates, state) }
            }
        }
    }

    private fun cancel() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun render(binding: FragmentGeneratingBinding, candidates: PoseCandidateAdapter, state: GenerationUiState) {
        binding.sceneImage.setImageBitmap(state.scene)
        candidates.submitList(state.slots())
        // Until the templates are there, the number of poses is not known.
        binding.readyCount.text = if (state.templates.isEmpty()) {
            getString(R.string.creating_pose)
        } else {
            getString(R.string.ready_count, state.readyCount, state.templates.size)
        }

        // A phase change can arrive twice before this screen is gone; navigate only while it is still in front.
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.generatingFragment) return
        when (state.phase) {
            GenerationUiState.Phase.GENERATING -> Unit

            GenerationUiState.Phase.PICKING -> navController.navigate(R.id.action_generating_to_pickPose)

            GenerationUiState.Phase.FAILED -> navController.navigate(R.id.action_generating_to_generationFailed)

            // Nothing is being generated, for example after the app process was restarted on this screen.
            GenerationUiState.Phase.IDLE,
            GenerationUiState.Phase.FRAMING,
            GenerationUiState.Phase.REVIEWING,
            -> navController.popBackStack(R.id.cameraFragment, false)
        }
    }
}
