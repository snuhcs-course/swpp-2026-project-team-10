// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.core.content.getSystemService
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.SentInvitation
import com.lastpenguin.pix.databinding.SheetInviteSentBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.account.avatarInitial
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Invite sent, photographer (R&S 6.5; FR-6.1–6.3), over Camera, so the camera is on when the friend accepts. The friend
 * gets a push; *Share invite link* and *Copy link* are for a push that does not arrive (AD-13). The sheet closes when
 * the friend is connected, opens *Invite not accepted* when the friend declines or a minute passes, and *Cancel invite*, or
 * closing it any other way while waiting, cancels the invitation. *Cancel invite* after the friend accepted ends the
 * session they are joining; closing the sheet then keeps it, as on Room code. The remote-control toggle (FR-7.5) is not
 * built.
 * Owner: Real-time (#119).
 */
class InviteSentSheet : BottomSheetDialogFragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    /** The sheet is going away on purpose, so closing it must not cancel the invitation. */
    private var leaving = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetInviteSentBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetInviteSentBinding.bind(view)
        binding.shareButton.setOnClickListener { sessionViewModel.invitation.value?.let(::share) }
        binding.copyButton.setOnClickListener { sessionViewModel.invitation.value?.let(::copy) }
        binding.cancelButton.setOnClickListener {
            sessionViewModel.cancelInvite()
            leave(R.id.action_inviteSent_to_friends)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.state.combine(sessionViewModel.invitation) { state, sent -> state to sent }
                    .collect { (state, sent) -> render(binding, state, sent) }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // A configuration change (the language, the font size) also tears the dialog down, but the sheet comes back
        // for the same invitation.
        if (leaving || activity?.isChangingConfigurations == true) return
        if (sessionViewModel.state.value is SessionState.Inviting) sessionViewModel.cancelInvite()
    }

    private fun render(binding: SheetInviteSentBinding, state: SessionState, sent: SentInvitation?) {
        val name = sent?.to?.displayName.orEmpty()
        binding.avatarText.text = avatarInitial(name)
        binding.bodyText.text = getString(R.string.invite_waiting_body, name)
        val waiting = state is SessionState.Inviting
        binding.shareButton.isEnabled = waiting
        binding.copyButton.isEnabled = waiting
        when (state) {
            is SessionState.Inviting -> binding.statusText.text = getString(R.string.invite_waiting, name)

            is SessionState.Connecting -> binding.statusText.text = getString(R.string.invite_accepted_connecting, name)

            // The Camera screen shows the live light from here (FR-6.8).
            is SessionState.Connected, is SessionState.Reconnecting -> {
                leaving = true
                dismiss()
            }

            is SessionState.Ended -> when (state.reason) {
                EndReason.DECLINED, EndReason.EXPIRED -> leave(R.id.action_inviteSent_to_inviteNotAccepted)

                EndReason.CANCELLED, EndReason.LEFT -> Unit

                else -> {
                    Toast.makeText(requireContext(), R.string.couldnt_connect, Toast.LENGTH_LONG).show()
                    leaving = true
                    dismiss()
                }
            }

            SessionState.Idle, is SessionState.Waiting -> binding.statusText.setText(R.string.invite_sending)
        }
    }

    private fun leave(@IdRes action: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.inviteSentSheet) return
        leaving = true
        navController.navigate(action)
    }

    private fun share(sent: SentInvitation) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text, sent.link))
        startActivity(Intent.createChooser(send, getString(R.string.share_invite_link)))
    }

    private fun copy(sent: SentInvitation) {
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.share_invite_link), sent.link))
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), R.string.link_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
