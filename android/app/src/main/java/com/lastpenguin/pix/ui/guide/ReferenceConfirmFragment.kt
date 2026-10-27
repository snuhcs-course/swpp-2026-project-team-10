// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/** Reference confirm (R&S 6.2): the outline only, with *Use this guide* and *Choose another photo*. Owner: PM (#5). */
class ReferenceConfirmFragment : Fragment(R.layout.fragment_reference_confirm) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    /** *Choose another photo*; closing the picker keeps the current result. */
    private val pickPhoto = registerPhotoPicker { uri -> uri?.let(viewModel::onPhotoPicked) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentReferenceConfirmBinding.bind(view)

        // Back leaves without the guide: a person still being found and the tip asked for it stop (FR-4.10).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            viewModel.onConfirmLeft()
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.useGuideButton.setOnClickListener {
            viewModel.onUseGuide()
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
        binding.chooseAnotherButton.setOnClickListener { pickPhoto() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(binding, state) }
            }
        }
    }

    private fun render(binding: FragmentReferenceConfirmBinding, state: ReferenceUiState) {
        val ready = state as? ReferenceUiState.Ready
        val working = state is ReferenceUiState.Working
        binding.referenceProgress.isVisible = working
        binding.referenceImage.isVisible = ready != null
        binding.referenceStatus.isVisible = working || ready != null
        binding.referenceStatus.setText(if (ready != null) R.string.person_found else R.string.finding_person)
        binding.referenceStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (ready != null) R.drawable.ic_person_check_small else 0,
            0,
            0,
            0,
        )
        binding.useGuideButton.isEnabled = ready != null
        // The outline alone, over the card's neutral background (FR-2.3); the reference's pixels are not shown.
        if (ready != null) binding.referenceImage.setImageBitmap(ready.guide.outline)

        when (state) {
            ReferenceUiState.NoPersonFound -> findNavController().navigate(
                R.id.action_referenceConfirm_to_noPersonFound,
            )

            ReferenceUiState.Failed -> {
                Toast.makeText(requireContext(), R.string.couldnt_use_photo, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.cameraFragment, false)
            }

            else -> Unit
        }
    }
}
