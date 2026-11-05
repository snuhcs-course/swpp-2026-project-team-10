// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.OpenedInvitation
import com.lastpenguin.pix.databinding.FragmentJoinCodeBinding
import com.lastpenguin.pix.databinding.ItemIncomingInvitationBinding
import com.lastpenguin.pix.push.PushNotifications
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.account.accountMessage
import com.lastpenguin.pix.ui.account.avatarInitial
import kotlinx.coroutines.launch

/**
 * See their camera, subject (R&S 6.7), opened by *I'll be in the photo* on Who takes the photo?: the code from the
 * photographer's phone, and the buttons there that show it (#188). *Connect* is enabled once 6 digits are in (FR-8.2).
 * A valid code opens Subject view, an unknown or expired one opens Session not found with the code kept (FR-8.3), too
 * many wrong codes from this network say so here (NFR-15), and any other failure says so here (FR-8.5). The kept code
 * is offered only when Session not found opens this screen again (`keepCode`); otherwise the screen starts empty (#81).
 * Above the code, the received invitations wait for their minute, so one whose notification was dismissed can still be
 * accepted (FR-6.17): *Accept* opens Subject view as on Invitation, and the keyboard stays down while any is shown.
 * Owner: Real-time (#8).
 */
class JoinCodeFragment : Fragment(R.layout.fragment_join_code) {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private val incoming: IncomingInvitationsViewModel by activityViewModels { PixViewModels.Factory }
    private var joining = false

    /** The rows shown, in the list's order, so a tick of the time left changes their texts instead of the rows. */
    private var rows = emptyList<Pair<String, ItemIncomingInvitationBinding>>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentJoinCodeBinding.bind(view)
        rows = emptyList()
        val photographer = getString(R.string.role_photographer)
        val showCode = getString(R.string.show_code)
        binding.joinHint.text = requireContext().withColoredPart(
            getString(R.string.join_hint, photographer, showCode),
            getString(R.string.room_code_path, photographer, showCode),
            R.color.pix_text_soft,
        )

        // The kept code is offered only when the screen opens to correct it, from Session not found (FR-8.3); a
        // fresh one from Who takes the photo? starts empty (#81). Typing then replaces the whole code, while a tap still
        // puts the cursor on one digit to change.
        if (arguments?.getBoolean(ARG_KEEP_CODE) == true && binding.codeInput.text.isNullOrEmpty()) {
            sessionViewModel.lastCode?.let(binding.codeInput::setText)
            binding.codeInput.setSelectAllOnFocus(true)
        }
        binding.codeInput.doAfterTextChanged {
            updateJoinButton(binding)
            renderDigits(binding)
        }
        binding.codeInput.setOnFocusChangeListener { _, _ -> renderDigits(binding) }
        renderInvitations(binding, incoming.uiState.value)
        updateJoinButton(binding)
        renderDigits(binding)
        // The code is all there is to fill in here, so the number keyboard comes up with the screen, unless an
        // invitation, which the keyboard would cover, is waiting.
        if (savedInstanceState == null && incoming.uiState.value.invitations.isEmpty()) {
            binding.codeInput.post {
                binding.codeInput.requestFocus()
                WindowCompat.getInsetsController(requireActivity().window, binding.codeInput)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }

        binding.joinButton.setOnClickListener { join(binding) }
        binding.codeInput.setOnEditorActionListener { _, actionId, event ->
            val done = actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (done && binding.joinButton.isEnabled) join(binding)
            done
        }
        binding.backButton.setOnClickListener {
            findNavController().popBackStack(R.id.cameraFragment, false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { sessionViewModel.transitions.collect { state -> onTransition(binding, state) } }
                launch { incoming.keepRefreshing() }
                launch { incoming.uiState.collect { renderInvitations(binding, it) } }
                launch { incoming.events.collect(::onInvitationEvent) }
                // An accepted invitation's session, followed by its state as on Invitation (InvitationJoin).
                launch { sessionViewModel.state.collect(::onSession) }
            }
        }
    }

    override fun onDestroyView() {
        rows = emptyList()
        super.onDestroyView()
    }

    private fun onTransition(binding: FragmentJoinCodeBinding, state: SessionState) {
        if (!joining) return
        when (state) {
            is SessionState.Connecting -> if (state.sessionId != null) {
                findNavController().navigate(R.id.action_joinCode_to_subject)
            }

            is SessionState.Ended -> when (state.reason) {
                EndReason.NOT_FOUND, EndReason.EXPIRED ->
                    findNavController().navigate(R.id.action_joinCode_to_sessionNotFound)

                EndReason.CANCELLED, EndReason.LEFT -> Unit

                else -> {
                    joining = false
                    binding.statusText.setText(
                        if (state.reason ==
                            EndReason.RATE_LIMITED
                        ) {
                            R.string.join_rate_limited
                        } else {
                            R.string.couldnt_connect
                        },
                    )
                    binding.statusText.isVisible = true
                    updateJoinButton(binding)
                    renderInvitations(binding, incoming.uiState.value)
                }
            }

            else -> Unit
        }
    }

    private fun join(binding: FragmentJoinCodeBinding) {
        val code = binding.codeInput.text.toString()
        if (code.length != CODE_LENGTH || !code.all(Char::isDigit)) return
        joining = true
        binding.statusText.setText(R.string.connecting)
        binding.statusText.isVisible = true
        updateJoinButton(binding)
        renderInvitations(binding, incoming.uiState.value)
        sessionViewModel.joinRoom(code)
    }

    /**
     * A session open on this phone would keep the invitation's from starting, so nothing is accepted (FR-6.9); a room
     * it waits in alone, after its subject left, is ended first.
     */
    private fun accept(invitation: OpenedInvitation) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (sessionViewModel.endWaitingRoom()) {
                incoming.accept(invitation)
            } else {
                toast(getString(R.string.invitation_in_session))
            }
        }
    }

    private fun onInvitationEvent(event: IncomingInvitationEvent) {
        when (event) {
            is IncomingInvitationEvent.Accepted -> {
                PushNotifications.cancelInvitation(requireContext(), event.invitationId)
                if (sessionViewModel.joinAccepted(event.invitationId, event.place, event.from)) {
                    incoming.joining = true
                } else {
                    toast(getString(R.string.invitation_in_session))
                }
            }

            is IncomingInvitationEvent.Declined -> PushNotifications.cancelInvitation(
                requireContext(),
                event.invitationId,
            )

            is IncomingInvitationEvent.Gone -> {
                PushNotifications.cancelInvitation(requireContext(), event.invitationId)
                toast(getString(R.string.invitation_gone_title))
            }

            is IncomingInvitationEvent.AnswerFailed -> toast(requireContext().accountMessage(event.error))
        }
    }

    private fun onSession(state: SessionState) {
        if (!incoming.joining) return
        val join = InvitationJoin.of(state) ?: return
        incoming.joining = false
        when (join) {
            InvitationJoin.JOINED -> {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.joinCodeFragment) {
                    navController.navigate(R.id.action_joinCode_to_subject)
                }
            }

            InvitationJoin.GONE -> toast(getString(R.string.invitation_gone_title))

            InvitationJoin.FAILED -> toast(getString(R.string.couldnt_connect))

            InvitationJoin.CLOSED -> Unit
        }
    }

    /**
     * One compact row per invitation, also for one, with the time it has left; *Received invitations* and the "Or"
     * before the code only while there are any. Every row waits while an answer or a code is on its way.
     */
    private fun renderInvitations(binding: FragmentJoinCodeBinding, state: IncomingInvitationsUiState) {
        val invitations = state.invitations
        binding.invitationsSection.isVisible = invitations.isNotEmpty()
        binding.codeHeading.setText(if (invitations.isEmpty()) R.string.join_body else R.string.join_body_or)
        if (rows.map { it.first } != invitations.map { it.id }) {
            binding.invitationsList.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)
            rows = invitations.map { invitation ->
                val row = ItemIncomingInvitationBinding.inflate(inflater, binding.invitationsList, true)
                val name = invitation.from.displayName
                row.avatarText.text = avatarInitial(name)
                row.nameText.text = name
                row.acceptButton.contentDescription = getString(R.string.accept_invitation_from, name)
                row.declineButton.contentDescription = getString(R.string.decline_invitation_from, name)
                row.acceptButton.setOnClickListener { accept(invitation) }
                row.declineButton.setOnClickListener { incoming.decline(invitation) }
                invitation.id to row
            }
        }
        val waiting = state.answering != null || joining
        rows.forEachIndexed { index, (_, row) ->
            val seconds = ((invitations[index].expiresAt - state.now).coerceAtLeast(0) + 999) / 1_000
            row.timeLeftText.text = getString(R.string.invitation_time_left, seconds / 60, seconds % 60)
            row.acceptButton.isEnabled = !waiting
            row.declineButton.isEnabled = !waiting
        }
        updateJoinButton(binding)
    }

    /** The six boxes show the digits typed so far; the one the next digit goes into is outlined. */
    private fun renderDigits(binding: FragmentJoinCodeBinding) {
        val code = binding.codeInput.text?.toString().orEmpty()
        val boxes =
            listOf(binding.digit1, binding.digit2, binding.digit3, binding.digit4, binding.digit5, binding.digit6)
        val next = minOf(code.length, CODE_LENGTH - 1)
        boxes.forEachIndexed { index, box ->
            box.text = code.getOrNull(index)?.toString().orEmpty()
            box.isActivated = binding.codeInput.hasFocus() && index == next
        }
    }

    private fun updateJoinButton(binding: FragmentJoinCodeBinding) {
        binding.joinButton.isEnabled = !joining && incoming.uiState.value.answering == null &&
            binding.codeInput.text?.length == CODE_LENGTH
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val CODE_LENGTH = 6

        /** The navigation argument Session not found sets (nav_graph.xml). */
        const val ARG_KEEP_CODE = "keepCode"
    }
}
