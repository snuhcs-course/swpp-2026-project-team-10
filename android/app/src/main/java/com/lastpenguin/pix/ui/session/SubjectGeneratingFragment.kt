// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentGeneratingBinding
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.CandidateGridFit
import com.lastpenguin.pix.ui.generation.PoseCandidateAdapter
import com.lastpenguin.pix.ui.generation.PoseSlot
import com.lastpenguin.pix.ui.generation.aspectOf
import com.lastpenguin.pix.ui.generation.showScene
import kotlinx.coroutines.launch

/**
 * *Generating poses* on the subject's phone (R&S 6.6, FR-6.15, Iter3-A13): the photographer's set as it is created,
 * the same slots in the same order as on their phone, each candidate fading in as its copy arrives and a failed one
 * saying so. It has no *Cancel*, since the set is the photographer's to cancel; Back returns to Subject view, whose
 * button opens it again. The set's *Pick a pose* follows it with copies here; the set's end, or *Pick a pose* without
 * a copy, goes back to Subject view. It covers Subject view, so it follows the session itself (Design 2.6.5).
 * Owner: Shared steps (Iter3-A13).
 */
class SubjectGeneratingFragment : Fragment(R.layout.fragment_generating) {

    private val viewModel: SubjectGeneratingViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentGeneratingBinding.bind(view)
        val candidates = PoseCandidateAdapter()
        binding.candidateGrid.adapter = candidates
        binding.candidateGrid.addItemDecoration(
            CandidateGridFit(resources.getDimensionPixelSize(R.dimen.pose_card_margin)),
        )
        // In place of Cancel: Back leaves the photographer's set as it is (R&S 6.9).
        binding.cancelButton.setText(R.string.back)
        binding.cancelButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToSubject() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { ui -> render(binding, candidates, ui) } }
                launch {
                    viewModel.ended.collect { end ->
                        // Only this screen's set: an end kept from an earlier one says nothing about it.
                        val ui = viewModel.uiState.value
                        if (end.setId != ui.setId) return@collect
                        val name = ui.photographerName
                        if (end.step == PoseSetStep.CLOSED && name != null) {
                            val text = if (ui.allFailed) R.string.peer_poses_failed else R.string.peer_stopped_poses
                            Toast.makeText(requireContext(), getString(text, name), Toast.LENGTH_SHORT).show()
                        }
                        backToSubject()
                    }
                }
                launch {
                    // Subject view shows Connection lost or goes back to the camera once it is in front again.
                    sessionViewModel.state.collect { state -> if (state is SessionState.Ended) backToSubject() }
                }
            }
        }
    }

    private fun render(
        binding: FragmentGeneratingBinding,
        candidates: PoseCandidateAdapter,
        ui: SubjectGeneratingUiState,
    ) {
        if (ui.gone) {
            backToSubject()
            return
        }
        if (ui.picking) {
            toPickPose()
            return
        }
        binding.sceneImage.showScene(ui.scene)
        candidates.frameAspect = aspectOf(ui.scene)
        // No pose names travel; a screen reader says which of the slots it is instead.
        candidates.submitList(
            ui.slots.mapIndexed { index, (poseId, event) ->
                val description = getString(R.string.pose_page_counter_description, index + 1, ui.slots.size)
                PoseSlot(PlannedPose(poseId, description), event)
            },
        )
        // As on the photographer's phone: until the slots are known, what the set is doing.
        binding.readyCount.text = when {
            ui.planned -> getString(R.string.ready_count, ui.readyCount, ui.slots.size)
            ui.suggested -> getString(R.string.choosing_poses)
            else -> getString(R.string.creating_pose)
        }
    }

    /** Straight on to the subject's *Pick a pose*, which takes this screen's place. */
    private fun toPickPose() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectGeneratingFragment) {
            nav.navigate(R.id.action_subjectGenerating_to_pickPose)
        }
    }

    private fun backToSubject() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectGeneratingFragment) nav.popBackStack()
    }
}
