package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetRoomCodeBinding
import com.lastpenguin.pix.ui.PixViewModels

/**
 * Room code, photographer (R&S 6.7). When the subject joins, the sheet closes and the Camera
 * screen shows the live badge. Owner: Real-time (#8).
 */
class RoomCodeSheet : BottomSheetDialogFragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetRoomCodeBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetRoomCodeBinding.bind(view)

        // TODO(#8): collect sessionViewModel.state: Waiting(code) → codeText; Connected → dismiss().

        binding.joinInsteadButton.setOnClickListener {
            sessionViewModel.cancelRoom()
            findNavController().navigate(R.id.action_roomCode_to_joinCode)
        }
        binding.cancelButton.setOnClickListener {
            sessionViewModel.cancelRoom()
            dismiss()
        }
        binding.tempFriendJoinedButton.setOnClickListener { dismiss() }
    }
}
