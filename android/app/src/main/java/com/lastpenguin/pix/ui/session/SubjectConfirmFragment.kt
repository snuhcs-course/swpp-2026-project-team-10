// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.session.ConfirmEnd
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.ConfirmStep
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * *Your reference* on the subject's phone (R&S 6.6, FR-6.15): the outline on the photographer's Reference confirm, as
 * on their phone, without *Choose another photo*. *Use this guide* asks the photographer's phone, whose first use
 * decides, and the screen closes when the confirm ends, saying so when the photographer decided; the guide then comes
 * to Subject view like any guide. It covers Subject view, so it follows the session itself, like the guide screens
 * (Design 2.6.5).
 * Owner: Shared steps (Iter3-P16).
 */
class SubjectConfirmFragment : Fragment(R.layout.fragment_reference_confirm) {

    private val viewModel: SubjectConfirmViewModel by viewModels { PixViewModels.Factory }
    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentReferenceConfirmBinding.bind(view)
        binding.chooseAnotherButton.isVisible = false
        binding.useGuideButton.setOnClickListener { viewModel.useGuide() }
        binding.backButton.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        // Back leaves the photographer's confirm as it is; Subject view's *Your reference* opens it again.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { backToSubject() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { ui -> render(binding, ui) } }
                launch {
                    viewModel.ended.collect { end ->
                        // Only this screen's confirm: an end kept from an earlier one says nothing about it.
                        val ui = viewModel.uiState.value
                        if (end.confirmId != ui.confirmId) return@collect
                        confirmEndNotice(requireContext(), end, ui.photographerName)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                        backToSubject()
                    }
                }
                launch {
                    viewModel.useFailed.collect {
                        Toast.makeText(requireContext(), R.string.guide_use_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    // Subject view shows Connection lost or goes back to the camera once it is in front again.
                    sessionViewModel.state.collect { state -> if (state is SessionState.Ended) backToSubject() }
                }
            }
        }
    }

    private fun render(binding: FragmentReferenceConfirmBinding, ui: SubjectConfirmUiState) {
        if (ui.gone) {
            backToSubject()
            return
        }
        val name = ui.photographerName
        binding.sharedHint.isVisible = name != null
        if (name != null) binding.sharedHint.text = getString(R.string.reference_shared_hint, name)
        // As on the photographer's phone: the progress while their phone finds the person, then the outline alone.
        val guide = ui.guide
        binding.referenceProgress.isVisible = guide == null
        binding.referenceImage.isVisible = guide != null
        binding.referenceStatus.isVisible = true
        binding.referenceStatus.setText(if (guide != null) R.string.person_found else R.string.finding_person)
        binding.referenceStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (guide != null) R.drawable.ic_person_check_small else 0,
            0,
            0,
            0,
        )
        if (guide != null) binding.referenceImage.setImageBitmap(guide.outline)
        binding.useGuideButton.isEnabled = guide != null && !ui.waiting && !ui.reconnecting
    }

    private fun backToSubject() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.subjectConfirmFragment) nav.popBackStack()
    }
}

/**
 * What the subject's phone says when the photographer's Reference confirm [end]ed (Design 2.6.5): that the photographer
 * used the guide or closed the reference; nothing for this phone's own use, which the guide on the video answers.
 */
internal fun confirmEndNotice(context: Context, end: ConfirmEnd, photographer: String?): String? {
    if (end.by == Role.SUBJECT || photographer == null) return null
    val text = if (end.step == ConfirmStep.USED) R.string.peer_used_guide else R.string.peer_closed_reference
    return context.getString(text, photographer)
}
