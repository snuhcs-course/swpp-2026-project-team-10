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
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.guide.ReferenceViewModel
import kotlinx.coroutines.launch

/** Pick a pose (R&S 6.3). Owner: Server/AI/Sync (#7). The chosen pose goes to Reference confirm (#5). */
class PickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)
        val candidates = PoseCandidateAdapter(onPick = { viewModel.select(it.id) })
        binding.candidateGrid.adapter = candidates

        binding.usePoseButton.setOnClickListener {
            val image = viewModel.uiState.value.selectedImage ?: return@setOnClickListener
            // The same step as an uploaded photo: the person is separated on the phone (FR-4.6).
            referenceViewModel.onCandidatePicked(image)
            findNavController().navigate(R.id.action_pickPose_to_referenceConfirm)
            // Reference confirm has the image now. The scene and the other candidates cannot be reached again, so
            // they are let go; after navigating, so that this screen does not take the empty state for a restart.
            viewModel.cancel()
        }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.tryOtherButton.setOnClickListener {
            viewModel.retry()
            findNavController().navigate(R.id.action_pickPose_to_generating)
        }
        // Leaving this screen with system back discards the candidates.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToCamera() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(binding, candidates, state) }
            }
        }
    }

    private fun backToCamera() {
        viewModel.cancel()
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun render(binding: FragmentPickPoseBinding, candidates: PoseCandidateAdapter, state: GenerationUiState) {
        // A pose that could not be created is left out; the others can still be used (FR-4.7).
        candidates.submitList(state.slots().filter { it.event is CandidateEvent.Ready })
        binding.usePoseButton.isEnabled = state.selectedImage != null

        // There are no candidates to pick from, for example after the app process was restarted on this screen.
        val navController = findNavController()
        val idle = state.phase == GenerationUiState.Phase.IDLE
        if (idle && navController.currentDestination?.id == R.id.pickPoseFragment) {
            navController.popBackStack(R.id.cameraFragment, false)
        }
    }
}
