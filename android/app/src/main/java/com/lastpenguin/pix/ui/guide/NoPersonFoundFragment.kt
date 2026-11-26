// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.guide

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.showFailureTopBar

/**
 * No person found (R&S 6.2, FR-2.4): Back alone in the top bar, to where the reference was chosen (#199, #294). For a
 * photo its line says to go back, and Back on a photo from Add a pose guide opens that sheet again with its gallery,
 * so another photo is one tap away; a photo from the library returns there. A generated pose keeps the line it had
 * and goes one step back, to Pick a pose or, in a session, the camera.
 * Owner: PM (#5); the top bar Iter4-P2; Back to the sheet Iter5-A3.
 */
class NoPersonFoundFragment : Fragment(R.layout.fragment_failure) {

    private val viewModel: ReferenceViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFailureBinding.bind(view)
        binding.failureIcon.setImageResource(R.drawable.ic_person_off)
        binding.failureTitle.setText(R.string.no_person_title)
        binding.failureMessage.setText(
            if (viewModel.fromPhoto) R.string.no_person_photo_message else R.string.no_person_message,
        )

        binding.showFailureTopBar(null, onBack = ::back)
        // System back does what Back does (R&S 6.9).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { back() }
        leaveWhenTheSessionEnds(viewModel)
    }

    private fun back() = backToChoosing(
        viewModel,
        from = R.id.noPersonFoundFragment,
        toSheet = R.id.action_noPersonFound_to_addGuide,
        gallery = true,
    )
}
