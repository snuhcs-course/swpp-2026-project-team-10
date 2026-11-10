// AI-generated with Claude Code, 2026-11-10, reviewed by Jaewan Park and Joonhyung Han
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
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Generating poses (R&S 6.3). Back at the top left asks first, since the AI service is already making the set, then
 * stops it and returns to Generate poses with the photo and the description (#199). Owner: Server/AI/Sync (#7); Back
 * Iter4-P2.
 */
class GeneratingFragment : Fragment(R.layout.fragment_generating) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGeneratingBinding.bind(view)
        // No pick callback: candidates cannot be selected until generation ends.
        val candidates = PoseCandidateAdapter()
        binding.candidateGrid.adapter = candidates
        binding.candidateGrid.addItemDecoration(
            CandidateGridFit(resources.getDimensionPixelSize(R.dimen.pose_card_margin)),
        )

        binding.backButton.setOnClickListener { askToStop() }
        // System back does what Back does (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { askToStop() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(binding, candidates, state) }
            }
        }
    }

    /** Back: asks first, as the set is already being made. */
    private fun askToStop() {
        confirmGoingBack(generating = true, onConfirm = ::stop)
    }

    /** Back confirmed: the requests stop, and Generate poses shows the photo and the description again. */
    private fun stop() {
        val navController = findNavController()
        val generating = viewModel.uiState.value.phase == GenerationUiState.Phase.GENERATING
        if (!generating || navController.currentDestination?.id != R.id.generatingFragment) return
        // Navigates first, so that this screen does not take Generate poses' phase for a restart.
        navController.navigate(R.id.action_generating_to_describePose)
        viewModel.stopGenerating()
    }

    private fun render(binding: FragmentGeneratingBinding, candidates: PoseCandidateAdapter, state: GenerationUiState) {
        binding.sceneImage.showScene(state.scene)
        candidates.frameAspect = aspectOf(state.scene)
        candidates.submitList(state.slots())
        // Until the poses are there, the number of slots is not known; suggestions take the first seconds.
        binding.readyCount.text = when {
            state.poses.isNotEmpty() -> getString(R.string.ready_count, state.readyCount, state.poses.size)
            state.request == PoseRequest.Suggested -> getString(R.string.choosing_poses)
            else -> getString(R.string.creating_pose)
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
            GenerationUiState.Phase.DESCRIBING,
            -> navController.popBackStack(R.id.cameraFragment, false)
        }
    }
}
