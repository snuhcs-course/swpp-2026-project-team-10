package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels

/** Connection lost, subject (R&S 6.6, FR-6.11). Owner: Real-time (#8). */
class ConnectionLostFragment : Fragment(R.layout.fragment_failure) {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        binding.failureTitle.setText(R.string.connection_lost_title)
        binding.failureMessage.setText(R.string.connection_lost_message)

        binding.primaryButton.setText(R.string.reconnect)
        binding.primaryButton.setOnClickListener {
            // TODO(#8): Subject view if the session is still open, otherwise back to camera.
            sessionViewModel.reconnect()
            findNavController().navigate(R.id.action_connectionLost_to_subject)
        }
        binding.secondaryButton.setText(R.string.leave)
        binding.secondaryButton.setOnClickListener {
            sessionViewModel.leave()
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
    }
}
