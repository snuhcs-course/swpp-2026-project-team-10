// AI-generated with Claude Code, 2026-11-10, reviewed by Joonhyung Han and Sungmin Jo
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
import com.lastpenguin.pix.ui.generation.PoseKey
import com.lastpenguin.pix.ui.generation.PosePageAdapter
import com.lastpenguin.pix.ui.generation.PoseSaveViewModel
import com.lastpenguin.pix.ui.generation.PoseSlot
import com.lastpenguin.pix.ui.generation.inSet
import com.lastpenguin.pix.ui.generation.setupPosePager
import com.lastpenguin.pix.ui.generation.showPosePages
import com.lastpenguin.pix.ui.generation.showPoseSaveNotice
import kotlinx.coroutines.launch

/**
 * *Pick a pose* on the subject's phone (R&S 6.6, FR-6.15): the photographer's candidates, as on their phone in a
 * session, without *Regenerate*. The swipe stays on this phone; *Select* asks the photographer's phone, whose first choice
 * decides, and the screen closes when the set ends, saying so when the photographer decided. It covers Subject view,
 * so it follows the session itself, like the guide screens (Design 2.6.5). The ♥ keeps a candidate in this phone's own
 * library (FR-4.14) and sends nothing to the photographer's phone.
 * Owner: Shared steps (Iter3-P16); the ♥ the library (#171).
 */
class SubjectPickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: SubjectPickViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val saves: PoseSaveViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)
        val pages = PosePageAdapter(::onHeart)
        binding.setupPosePager(pages, viewModel::select)
        binding.selectButton.setOnClickListener { viewModel.usePose() }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        // Back leaves the photographer's set as it is; Subject view's *Pick a pose* opens it again.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToSubject() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { ui -> render(binding, pages, ui) } }
                launch { saves.hearts.collect { showPages(binding, pages, viewModel.uiState.value) } }
                launch {
                    saves.results.collect { result ->
                        if (result.setId == viewModel.uiState.value.setId) showPoseSaveNotice(result.notice)
                    }
                }
                launch {
                    viewModel.ended.collect { end ->
                        // Only this screen's set: an end kept from an earlier one says nothing about it.
                        val ui = viewModel.uiState.value
                        if (end.setId != ui.setId) return@collect
                        poseSetEndNotice(requireContext(), end, ui.photographerName)?.let {
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

    /** The ♥ on a candidate (FR-4.14): this phone's own library, which the photographer's phone never hears of. */
    private fun onHeart(slot: PoseSlot) {
        val setId = viewModel.uiState.value.setId ?: return
        val image = (slot.event as? CandidateEvent.Ready)?.image ?: return
        saves.toggle(PoseKey(setId, slot.pose.id), image)
    }

    private fun render(binding: FragmentPickPoseBinding, pages: PosePageAdapter, ui: SubjectPickUiState) {
        if (ui.gone) {
            backToSubject()
            return
        }
        val name = ui.photographerName
        binding.sharedHint.isVisible = name != null
        if (name != null) binding.sharedHint.text = getString(R.string.shared_hint, name)
        showPages(binding, pages, ui)
        binding.selectButton.isEnabled = ui.selectedPoseId != null && !ui.waiting && !ui.reconnecting
    }

    private fun showPages(binding: FragmentPickPoseBinding, pages: PosePageAdapter, ui: SubjectPickUiState) {
        if (ui.gone) return
        val count = ui.candidates.size
        // No pose names travel; a screen reader says which of the candidates it is instead.
        val slots = ui.candidates.mapIndexed { index, (poseId, image) ->
            val description = getString(R.string.pose_page_counter_description, index + 1, count)
            PoseSlot(PlannedPose(poseId, description), CandidateEvent.Ready(poseId, image))
        }
        val hearts = saves.hearts.value.inSet(ui.setId)
        binding.showPosePages(pages, slots, ui.selectedPoseId, viewModel::select, hearts) { view != null }
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
