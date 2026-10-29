// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.session.PoseSetEnd
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.generation.PosePageAdapter
import com.lastpenguin.pix.ui.generation.PoseSlot
import com.lastpenguin.pix.ui.generation.setupPosePager
import com.lastpenguin.pix.ui.generation.showPosePages
import kotlinx.coroutines.launch

/**
 * *Pick a pose* on the subject's phone (R&S 6.6, FR-6.15): the photographer's candidates, as on their phone, without
 * *Try other poses*. The swipe stays on this phone; *Use this pose* asks the photographer's phone, whose first choice
 * decides, and the screen closes when the set ends, saying so when the photographer decided. It covers Subject view,
 * so it follows the session itself, like the guide screens (Design 2.6.5).
 * Owner: Shared steps (Iter3-P16).
 */
class SubjectPickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: SubjectPickViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)
        binding.tryOtherButton.isVisible = false
        val pages = PosePageAdapter()
        binding.setupPosePager(pages, viewModel::select)
        binding.usePoseButton.setOnClickListener { viewModel.usePose() }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        // Back leaves the photographer's set as it is; Subject view's *Pick a pose* opens it again.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToSubject() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { ui -> render(binding, pages, ui) } }
                launch {
                    viewModel.ended.collect { end ->
                        poseSetEndNotice(requireContext(), end, viewModel.uiState.value.photographerName)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                        backToSubject()
                    }
                }
                launch {
                    viewModel.pickFailed.collect {
                        Toast.makeText(requireContext(), R.string.pose_pick_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    // Subject view shows Connection lost or goes back to the camera once it is in front again.
                    sessionViewModel.state.collect { state -> if (state is SessionState.Ended) backToSubject() }
                }
            }
        }
    }

    private fun render(binding: FragmentPickPoseBinding, pages: PosePageAdapter, ui: SubjectPickUiState) {
        if (ui.gone) {
            backToSubject()
            return
        }
        val name = ui.photographerName
        binding.sharedHint.isVisible = name != null
        if (name != null) binding.sharedHint.text = getString(R.string.pose_shared_hint, name)
        val count = ui.candidates.size
        // No pose names travel; a screen reader says which of the candidates it is instead.
        val slots = ui.candidates.mapIndexed { index, (poseId, image) ->
            val description = getString(R.string.pose_page_counter_description, index + 1, count)
            PoseSlot(PlannedPose(poseId, description), CandidateEvent.Ready(poseId, image))
        }
        binding.showPosePages(pages, slots, ui.selectedPoseId, viewModel::select) { view != null }
        binding.usePoseButton.isEnabled = ui.selectedPoseId != null && !ui.waiting && !ui.reconnecting
    }

    private fun backToSubject() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectPickPoseFragment) nav.popBackStack()
    }
}

/**
 * What the subject's phone says when the photographer's set [end]ed (Design 2.6.5): that the photographer chose or
 * closed it, once *Pick a pose* had it; nothing for this phone's own pick, which the guide answers.
 */
internal fun poseSetEndNotice(context: Context, end: PoseSetEnd, photographer: String?): String? {
    if (!end.wasPicking || end.by == Role.SUBJECT || photographer == null) return null
    val text = if (end.step == PoseSetStep.CHOSEN) R.string.peer_chose_pose else R.string.peer_closed_poses
    return context.getString(text, photographer)
}
