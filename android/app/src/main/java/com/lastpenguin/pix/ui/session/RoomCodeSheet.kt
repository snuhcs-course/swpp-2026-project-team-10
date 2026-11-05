// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetRoomCodeBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Room code, photographer (R&S 6.7), from *Show a code* on Share my camera. It says which phone enters the code and
 * where: the person in the photo, through *I'll be in the photo* (#188). When the subject joins, the sheet closes and
 * the Camera screen shows the live badge. Closing the sheet any other way while waiting cancels the code (FR-8.4).
 * Owner: Real-time (#8).
 */
class RoomCodeSheet : BottomSheetDialogFragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var dismissedForConnection = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetRoomCodeBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetRoomCodeBinding.bind(view)

        val subject = getString(R.string.role_subject)
        binding.whereText.text = requireContext().withColoredPart(
            getString(R.string.room_code_path, getString(R.string.shoot_together), subject),
            subject,
            R.color.pix_accent,
        )
        binding.cancelButton.setOnClickListener {
            sessionViewModel.cancelRoom()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.state.collect { state -> render(binding, state) }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // A configuration change (the language, the font size) also tears the dialog down, but the sheet comes back
        // with the same room, so the code must stay.
        if (dismissedForConnection || activity?.isChangingConfigurations == true) return
        val state = sessionViewModel.state.value
        if (state is SessionState.Idle || state is SessionState.Waiting) sessionViewModel.cancelRoom()
    }

    private fun render(binding: SheetRoomCodeBinding, state: SessionState) {
        when (state) {
            SessionState.Idle -> {
                binding.codeText.setText(R.string.room_code_placeholder)
                binding.statusText.setText(R.string.getting_code)
            }

            is SessionState.Waiting -> {
                binding.codeText.text = formatCode(state.code)
                binding.statusText.setText(R.string.waiting_to_connect)
            }

            is SessionState.Connecting -> binding.statusText.setText(R.string.peer_joined_connecting)

            // The sheet closed at Connected, before any drop could happen; an invitation has its own sheet.
            is SessionState.Reconnecting, is SessionState.Inviting -> Unit

            is SessionState.Connected -> {
                dismissedForConnection = true
                dismiss()
            }

            is SessionState.Ended -> {
                binding.codeText.setText(R.string.room_code_placeholder)
                binding.statusText.setText(
                    when (state.reason) {
                        EndReason.EXPIRED -> R.string.code_expired
                        EndReason.CANCELLED, EndReason.LEFT, EndReason.PEER_LEFT -> R.string.getting_code
                        else -> R.string.couldnt_connect
                    },
                )
            }
        }
    }

    /** "482915" → "482 915", as in the wireframe. */
    private fun formatCode(code: String): String = "${code.take(3)} ${code.drop(3)}"
}
