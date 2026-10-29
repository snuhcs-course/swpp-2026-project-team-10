// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.session.SessionViewModel
import kotlinx.coroutines.launch

/**
 * Reference confirm (R&S 6.2): the outline only, with *Use this guide* and *Choose another photo*; under the title, whose
 * pick it is when the subject chose the candidate (FR-6.15). Owner: PM (#5).
 */
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
            backToShooting(viewModel)
        }
        binding.chooseAnotherButton.setOnClickListener { pickPhoto() }
        leaveWhenTheSessionEnds(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(binding, state) } }
                launch {
                    viewModel.chosenBy.collect { name ->
                        binding.chosenByText.isVisible = name != null
                        if (name != null) binding.chosenByText.text = getString(R.string.peer_chose_this_pose, name)
                    }
                }
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
                backToShooting(viewModel)
            }

            else -> Unit
        }
    }
}

/** Back to the screen *Add a pose guide* was opened from: Subject view for the subject's guide, else the camera. */
internal fun Fragment.backToShooting(viewModel: ReferenceViewModel) {
    findNavController().popBackStack(if (viewModel.forSubject) R.id.subjectFragment else R.id.cameraFragment, false)
}

/**
 * Opened from Subject view, this screen covers it, so Subject view does not see the session end: go back to it, which
 * shows Connection lost or returns to the camera, instead of making a guide for a session that is gone.
 */
internal fun Fragment.leaveWhenTheSessionEnds(viewModel: ReferenceViewModel) {
    if (!viewModel.forSubject) return
    val session = ViewModelProvider(requireActivity(), PixViewModels.Factory)[SessionViewModel::class.java]
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            session.state.collect { state ->
                if (state is SessionState.Ended) {
                    viewModel.onConfirmLeft()
                    backToShooting(viewModel)
                }
            }
        }
    }
}
