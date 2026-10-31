// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo
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
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.account.accountMessage
import kotlinx.coroutines.launch

/**
 * Invite not accepted, photographer (R&S 6.5, FR-6.3): the friend declined or did not answer within 60 s. *Invite
 * again* sends a new invitation to the same friend and opens *Invite sent*; *Back to camera* leaves the camera alone.
 * Owner: Real-time (#119).
 */
class InviteNotAcceptedFragment : Fragment(R.layout.fragment_failure) {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        val friend = sessionViewModel.invitation.value?.to
        binding.failureTitle.setText(R.string.invite_not_accepted_title)
        binding.failureMessage.text = getString(R.string.invite_not_accepted_message, friend?.displayName.orEmpty())

        binding.primaryButton.setText(R.string.invite_again)
        binding.primaryButton.isEnabled = friend != null
        binding.primaryButton.setOnClickListener { friend?.let(sessionViewModel::invite) }
        binding.secondaryButton.setText(R.string.back_to_camera)
        binding.secondaryButton.setOnClickListener { findNavController().popBackStack(R.id.cameraFragment, false) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Disabled while the invitation is on its way, and enabled again when it ends, also when it was
                // dropped with Pix in the background, which reports no result.
                launch {
                    sessionViewModel.sendingInvite.collect { sending ->
                        binding.primaryButton.isEnabled = friend != null && !sending
                    }
                }
                sessionViewModel.inviteResults.collect { result ->
                    when (result) {
                        InviteResult.Sent -> findNavController().navigate(R.id.action_inviteNotAccepted_to_inviteSent)

                        is InviteResult.Failed -> {
                            val message = if (result.error == AccountError.BUSY) {
                                getString(R.string.invite_busy, result.friend.displayName)
                            } else {
                                requireContext().accountMessage(result.error)
                            }
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
