package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Connection lost, subject (R&S 6.6, FR-6.11). *Reconnect* joins the same code again; if the photographer has
 * closed the room, "The session has ended" and back to the camera. Owner: Real-time (#8).
 */
class ConnectionLostFragment : Fragment(R.layout.fragment_failure) {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var reconnecting = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        binding.failureTitle.setText(R.string.connection_lost_title)
        binding.failureMessage.setText(R.string.connection_lost_message)

        binding.primaryButton.setText(R.string.reconnect)
        binding.primaryButton.setOnClickListener {
            if (sessionViewModel.lastCode == null) {
                backToCamera()
                return@setOnClickListener
            }
            reconnecting = true
            binding.primaryButton.isEnabled = false
            binding.failureMessage.setText(R.string.connecting)
            sessionViewModel.reconnect()
        }
        binding.secondaryButton.setText(R.string.leave)
        binding.secondaryButton.setOnClickListener {
            sessionViewModel.leave()
            backToCamera()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.transitions.collect { state -> onTransition(binding, state) }
            }
        }
    }

    private fun onTransition(binding: FragmentFailureBinding, state: SessionState) {
        if (!reconnecting) return
        when (state) {
            is SessionState.Connecting -> if (state.sessionId != null) {
                findNavController().navigate(R.id.action_connectionLost_to_subject)
            }

            is SessionState.Ended -> when (state.reason) {
                EndReason.NOT_FOUND, EndReason.EXPIRED -> {
                    Toast.makeText(requireContext(), R.string.session_ended, Toast.LENGTH_SHORT).show()
                    backToCamera()
                }

                EndReason.CANCELLED, EndReason.LEFT -> Unit

                else -> {
                    reconnecting = false
                    binding.primaryButton.isEnabled = true
                    binding.failureMessage.setText(R.string.connection_lost_message)
                }
            }

            else -> Unit
        }
    }

    private fun backToCamera() {
        findNavController().popBackStack(R.id.cameraFragment, false)
    }
}
