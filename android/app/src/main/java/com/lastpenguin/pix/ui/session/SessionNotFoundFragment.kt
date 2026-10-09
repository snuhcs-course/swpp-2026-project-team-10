// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding

/** Session not found (R&S 6.7, FR-8.3). *Try again* goes back to Join with code. Owner: Real-time (#8). */
class SessionNotFoundFragment : Fragment(R.layout.fragment_failure) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        binding.failureTitle.setText(R.string.session_not_found_title)
        binding.failureMessage.setText(R.string.session_not_found_message)

        binding.primaryButton.setText(R.string.try_again)
        binding.primaryButton.setOnClickListener {
            // Join with code prefills the code kept in SessionViewModel (FR-8.3).
            findNavController().navigate(R.id.action_sessionNotFound_to_joinCode)
        }
        binding.secondaryButton.setText(R.string.back)
        binding.secondaryButton.setOnClickListener {
            findNavController().popBackStack(R.id.cameraFragment, false)
        }
    }
}
