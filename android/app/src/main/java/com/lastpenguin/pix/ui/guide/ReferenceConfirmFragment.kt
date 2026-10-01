package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.ui.PixViewModels

/** Reference confirm (R&S 6.2). Owner: PM (#5). */
class ReferenceConfirmFragment : Fragment(R.layout.fragment_reference_confirm) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentReferenceConfirmBinding.bind(view)

        // TODO(#5): collect viewModel.uiState; on NoPersonFound, navigate to No person found.

        binding.cutoutButton.setOnClickListener { viewModel.onStylePreview(GuideStyle.CUTOUT) }
        binding.outlineButton.setOnClickListener { viewModel.onStylePreview(GuideStyle.OUTLINE) }
        binding.useGuideButton.setOnClickListener {
            viewModel.onUseGuide()
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
        binding.chooseAnotherButton.setOnClickListener {
            // TODO(#5): open the system photo picker again.
        }
        binding.tempNoPersonButton.setOnClickListener {
            findNavController().navigate(R.id.action_referenceConfirm_to_noPersonFound)
        }
    }
}
