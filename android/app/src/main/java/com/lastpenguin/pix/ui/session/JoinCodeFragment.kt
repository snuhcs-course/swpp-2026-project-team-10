package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentJoinCodeBinding
import com.lastpenguin.pix.ui.PixViewModels

/** Join with code, subject (R&S 6.7). Owner: Real-time (#8). */
class JoinCodeFragment : Fragment(R.layout.fragment_join_code) {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentJoinCodeBinding.bind(view)

        // TODO(#8): collect sessionViewModel.state: Connected → Subject view;
        //  Ended(NOT_FOUND or EXPIRED) → Session not found, keeping the code.

        binding.joinButton.setOnClickListener {
            sessionViewModel.joinRoom(binding.codeInput.text.toString())
            findNavController().navigate(R.id.action_joinCode_to_subject)
        }
        binding.backButton.setOnClickListener {
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
        binding.tempNotFoundButton.setOnClickListener {
            findNavController().navigate(R.id.action_joinCode_to_sessionNotFound)
        }
    }
}
