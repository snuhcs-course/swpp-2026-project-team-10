package com.lastpenguin.pix.ui.session

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentJoinCodeBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Join with code, subject (R&S 6.7). *Join* is enabled once 6 digits are in (FR-8.2). A valid code opens
 * Subject view, an unknown or expired one opens Session not found with the code kept (FR-8.3), and any other
 * failure shows the same-Wi-Fi message here (FR-8.5). Owner: Real-time (#8).
 */
class JoinCodeFragment : Fragment(R.layout.fragment_join_code) {

    private val sessionViewModel: SessionViewModel by activityViewModels { PixViewModels.Factory }
    private var joining = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentJoinCodeBinding.bind(view)

        if (binding.codeInput.text.isNullOrEmpty()) sessionViewModel.lastCode?.let(binding.codeInput::setText)
        binding.codeInput.doAfterTextChanged { updateJoinButton(binding) }
        updateJoinButton(binding)

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
                sessionViewModel.transitions.collect { state -> onTransition(binding, state) }
            }
        }
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
                    binding.statusText.setText(R.string.couldnt_connect_wifi)
                    binding.statusText.isVisible = true
                    updateJoinButton(binding)
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
        sessionViewModel.joinRoom(code)
    }

    private fun updateJoinButton(binding: FragmentJoinCodeBinding) {
        binding.joinButton.isEnabled = !joining && binding.codeInput.text?.length == CODE_LENGTH
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}
