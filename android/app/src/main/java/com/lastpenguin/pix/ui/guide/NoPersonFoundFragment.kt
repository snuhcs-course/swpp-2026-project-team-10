package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels

/** No person found (R&S 6.2, FR-2.4). Owner: PM (#5). */
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

        binding.primaryButton.setText(R.string.choose_another_photo)
        binding.primaryButton.setOnClickListener { pickPhoto() }
        binding.secondaryButton.setText(R.string.back_to_camera)
        binding.secondaryButton.setOnClickListener {
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
    }
}
