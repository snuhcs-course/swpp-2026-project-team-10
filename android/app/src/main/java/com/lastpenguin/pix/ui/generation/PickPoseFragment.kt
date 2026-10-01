package com.lastpenguin.pix.ui.generation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentPickPoseBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.guide.ReferenceViewModel

/** Pick a pose (R&S 6.3). Owner: Server/AI/Sync (#7). The chosen pose goes to Reference confirm (#5). */
class PickPoseFragment : Fragment(R.layout.fragment_pick_pose) {

    private val viewModel: GenerationViewModel by activityViewModels { PixViewModels.Factory }
    private val referenceViewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPickPoseBinding.bind(view)

        // TODO(#7): show the candidates; tapping one calls viewModel.select(templateId).
        //  Use this pose stays disabled until one is selected.

        binding.usePoseButton.setOnClickListener {
            // TODO(#7): referenceViewModel.onCandidatePicked(the selected image).
            findNavController().navigate(R.id.action_pickPose_to_referenceConfirm)
        }
        binding.tryOtherButton.setOnClickListener {
            viewModel.retry()
            findNavController().navigate(R.id.action_pickPose_to_generating)
        }
    }
}
