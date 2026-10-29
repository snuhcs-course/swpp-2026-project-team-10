// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.account.accountMessage
import kotlinx.coroutines.launch

/**
 * Invitation, the friend's phone (R&S 6.6; FR-6.1, FR-6.4), opened from the notification or the invite link through
 * the deep link `pix://invite/{invitationId}`. *Accept* takes this phone's place in the session and opens Subject view
 * once it has it; *Decline* closes, and the photographer sees *Invite not accepted*. *Accept* while another session is
 * open on this phone says so and accepts nothing. An invitation that cannot be answered any more says so. Owner:
 * Real-time (#119).
 */
class InvitationFragment : Fragment(R.layout.fragment_failure) {

    private val viewModel: InvitationViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        viewModel.open(requireArguments().getString(ARG_INVITATION_ID).orEmpty())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is InvitationEvent.Accepted -> join(event)
                            InvitationEvent.Declined -> backToCamera()
                            is InvitationEvent.AnswerFailed -> toast(requireContext().accountMessage(event.error))
                        }
                    }
                }
                // The state, not its transitions: a session that connected or ended while the screen was stopped, as
                // with Pix in the background, still decides what follows when it comes back.
                launch { sessionViewModel.state.collect(::onSession) }
            }
        }
    }

    /** A session open on this phone would keep the invitation's from starting, so nothing is accepted (FR-6.9). */
    private fun accept() {
        if (sessionViewModel.busy) toast(getString(R.string.invitation_in_session)) else viewModel.accept()
    }

    private fun join(accepted: InvitationEvent.Accepted) {
        if (sessionViewModel.joinInvitation(accepted.invitationId, accepted.place)) {
            viewModel.joining = true
            return
        }
        // A session opened here after *Accept* was tapped; the invitation is answered, so it is gone for this phone.
        toast(getString(R.string.invitation_in_session))
        viewModel.gone()
    }

    private fun onSession(state: SessionState) {
        if (!viewModel.joining) return
        when (state) {
            is SessionState.Connecting -> if (state.sessionId != null) navigate(R.id.action_invitation_to_subject)

            is SessionState.Connected, is SessionState.Reconnecting -> navigate(R.id.action_invitation_to_subject)

            is SessionState.Ended -> {
                viewModel.joining = false
                when (state.reason) {
                    // The photographer left between the accept and this phone taking its place.
                    EndReason.NOT_FOUND, EndReason.EXPIRED -> viewModel.gone()

                    EndReason.CANCELLED, EndReason.LEFT -> Unit

                    else -> {
                        toast(getString(R.string.couldnt_connect))
                        viewModel.gone()
                    }
                }
            }

            else -> Unit
        }
    }

    private fun render(binding: FragmentFailureBinding, state: InvitationUiState) {
        val invitation = state is InvitationUiState.Open || state is InvitationUiState.SignedOut ||
            state is InvitationUiState.Loading
        // The friend's picture slot of the wireframe for an invitation; the alert for one that cannot be answered.
        binding.failureIcon.setImageResource(if (invitation) R.drawable.ic_people else R.drawable.ic_alert)
        binding.failureIcon.setBackgroundResource(if (invitation) R.drawable.bg_avatar else R.drawable.bg_failure_icon)
        binding.failureIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (invitation) R.color.pix_text_soft else R.color.pix_danger_text,
            ),
        )
        binding.primaryButton.isVisible = state !is InvitationUiState.Loading
        binding.secondaryButton.isVisible = true
        binding.primaryButton.isEnabled = true
        binding.secondaryButton.isEnabled = true
        when (state) {
            InvitationUiState.Loading -> {
                binding.failureTitle.setText(R.string.invitation_loading)
                binding.failureMessage.text = ""
                backButton(binding)
            }

            is InvitationUiState.Open -> {
                binding.failureTitle.text = getString(R.string.invitation_title, state.from)
                binding.failureMessage.setText(if (state.answering) R.string.connecting else R.string.invitation_body)
                binding.primaryButton.setText(R.string.accept)
                binding.primaryButton.isEnabled = !state.answering
                binding.primaryButton.setOnClickListener { accept() }
                binding.secondaryButton.setText(R.string.decline)
                binding.secondaryButton.isEnabled = !state.answering
                binding.secondaryButton.setOnClickListener { viewModel.decline() }
            }

            // Friends signs in, then back here the answer is offered (Sign in returns to Friends).
            is InvitationUiState.SignedOut -> {
                binding.failureTitle.text = getString(R.string.invitation_title, state.from)
                binding.failureMessage.setText(R.string.invitation_sign_in)
                binding.primaryButton.setText(R.string.sign_in)
                binding.primaryButton.setOnClickListener { navigate(R.id.action_invitation_to_friends) }
                backButton(binding)
            }

            InvitationUiState.Gone -> {
                binding.failureTitle.setText(R.string.invitation_gone_title)
                binding.failureMessage.setText(R.string.invitation_gone_message)
                binding.primaryButton.setText(R.string.back_to_camera)
                binding.primaryButton.setOnClickListener { backToCamera() }
                binding.secondaryButton.isVisible = false
            }

            InvitationUiState.Failed -> {
                binding.failureTitle.setText(R.string.invitation_failed_title)
                binding.failureMessage.setText(R.string.offline_error)
                binding.primaryButton.setText(R.string.try_again)
                binding.primaryButton.setOnClickListener { viewModel.retry() }
                backButton(binding)
            }
        }
    }

    private fun backButton(binding: FragmentFailureBinding) {
        binding.secondaryButton.setText(R.string.back_to_camera)
        binding.secondaryButton.setOnClickListener { backToCamera() }
    }

    private fun navigate(action: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.invitationFragment) navController.navigate(action)
    }

    private fun backToCamera() {
        findNavController().popBackStack(R.id.cameraFragment, false)
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** The navigation argument, from the deep link `pix://invite/{invitationId}`. */
        const val ARG_INVITATION_ID = "invitationId"
    }
}
