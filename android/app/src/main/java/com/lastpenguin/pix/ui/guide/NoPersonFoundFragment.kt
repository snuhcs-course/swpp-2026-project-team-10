// AI-generated with Claude Code, 2026-11-10, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.showFailureTopBar

/**
 * No person found (R&S 6.2, FR-2.4): *Choose another photo* at the top right, and Back at the top left to the step
 * before, the camera or Pick a pose (#199). Owner: PM (#5); the top bar Iter4-P2.
 */
class NoPersonFoundFragment : Fragment(R.layout.fragment_failure) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    /** Another photo goes back to Reference confirm; closing the picker stays here. */
    private val pickPhoto = registerPhotoPicker { uri ->
        if (uri != null) {
            viewModel.onPhotoPicked(uri)
            findNavController().navigate(R.id.action_noPersonFound_to_referenceConfirm)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        binding.failureIcon.setImageResource(R.drawable.ic_person_off)
        binding.failureTitle.setText(R.string.no_person_title)
        binding.failureMessage.setText(R.string.no_person_message)

        binding.showFailureTopBar(
            R.string.choose_another_photo,
            // As system back: the step before, which the back stack holds.
            onBack = { findNavController().popBackStack() },
            onAction = { pickPhoto() },
        )
        leaveWhenTheSessionEnds(viewModel)
    }
}
