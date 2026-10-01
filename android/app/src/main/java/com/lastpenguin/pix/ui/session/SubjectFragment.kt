package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.ui.PixViewModels

/** Subject view (R&S 6.6). Owner: Real-time (#8, #10), with the guide from #9. */
class SubjectFragment : Fragment(R.layout.fragment_subject) {

    private val viewModel: SubjectViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSubjectBinding.bind(view)
        binding.guideOverlay.editable = false

        // TODO(#8, #9, #10): collect viewModel.uiState: name, live video, guide, zoom chips → viewModel.onZoomChip.
        // TODO(#8): Ended(CONNECTION_LOST) → Connection lost; Ended(PEER_LEFT) → back to camera with a message.
        // TODO(#8): system back asks "Leave the session?" first (R&S 6.9).

        binding.leaveButton.setOnClickListener {
            sessionViewModel.leave()
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
        binding.tempConnectionLostButton.setOnClickListener {
            findNavController().navigate(R.id.action_subject_to_connectionLost)
        }
    }
}
