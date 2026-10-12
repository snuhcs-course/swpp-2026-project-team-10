// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
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
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetRoomCodeBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Room code, photographer (R&S 6.7). When the subject joins, the sheet closes and the Camera
 * screen shows the live badge. Closing the sheet any other way while waiting cancels the code (FR-8.4).
 * Owner: Real-time (#8).
 */
class RoomCodeSheet : BottomSheetDialogFragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var dismissedForConnection = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetRoomCodeBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetRoomCodeBinding.bind(view)

        binding.joinInsteadButton.setOnClickListener {
            sessionViewModel.cancelRoom()
            findNavController().navigate(R.id.action_roomCode_to_joinCode)
        }
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
        if (dismissedForConnection) return
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
                binding.statusText.setText(R.string.waiting_for_friend)
            }

            is SessionState.Connecting -> binding.statusText.setText(R.string.friend_joined_connecting)

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
                        else -> R.string.couldnt_connect_wifi
                    },
                )
            }
        }
    }

    /** "482915" → "482 915", as in the wireframe. */
    private fun formatCode(code: String): String = "${code.take(3)} ${code.drop(3)}"
}
