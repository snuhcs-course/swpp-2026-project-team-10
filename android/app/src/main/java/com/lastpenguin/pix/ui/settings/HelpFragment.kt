// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentHelpBinding

/**
 * How to use Pix, from Settings (FR-10.4): adding a guide, generating poses, shooting together, and what the subject's
 * phone controls. The first-use tutorial comes in its own pull request (#123).
 * Owner: Settings (#123).
 */
class HelpFragment : Fragment(R.layout.fragment_help) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FragmentHelpBinding.bind(view).backButton.setOnClickListener { findNavController().popBackStack() }
    }
}
