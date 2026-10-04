package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/** Reference confirm (R&S 6.2). Owner: PM (#5). */
class ReferenceConfirmFragment : Fragment(R.layout.fragment_reference_confirm) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    /** *Choose another photo*; closing the picker keeps the current result. */
    private val pickPhoto = registerPhotoPicker { uri -> uri?.let(viewModel::onPhotoPicked) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentReferenceConfirmBinding.bind(view)

        binding.styleToggle.addOnButtonCheckedListener { _, buttonId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onStylePreview(if (buttonId == R.id.cutoutButton) GuideStyle.CUTOUT else GuideStyle.OUTLINE)
        }
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
        binding.cutoutButton.isEnabled = ready != null
        binding.outlineButton.isEnabled = ready != null
        binding.useGuideButton.isEnabled = ready != null
        if (ready != null) {
            val cutout = ready.style == GuideStyle.CUTOUT
            binding.referenceImage.setImageBitmap(if (cutout) ready.guide.cutout else ready.guide.outline)
            binding.styleToggle.check(if (cutout) R.id.cutoutButton else R.id.outlineButton)
        }

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
