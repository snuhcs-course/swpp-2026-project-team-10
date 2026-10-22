// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.os.Bundle
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
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.pixIdProblem
import com.lastpenguin.pix.databinding.FragmentPixIdSetupBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Pix ID setup, for an account made with Google (R&S 6.8; FR-5.2, FR-5.3, US-6). *Save* waits until the Pix ID follows
 * the rule; a taken one keeps the input. Back leaves without one, and Friends asks again. Owner: Accounts (#54).
 */
class PixIdSetupFragment : Fragment(R.layout.fragment_pix_id_setup) {

    private val viewModel: AccountViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPixIdSetupBinding.bind(view)
        if (savedInstanceState == null) viewModel.clearError()
        val form = PixIdForm(binding, onSave = viewModel::savePixId, onEdited = viewModel::clearError)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(form::render) }
                launch { viewModel.events.collect { if (it == AccountEvent.PixIdSaved) toFriends() } }
            }
        }
    }

    private fun toFriends() {
        findNavController().popBackStack(R.id.friendsFragment, false)
    }
}

/** The Pix ID field, its rule, and *Save*. Apart from the fragment, so it can be tested on the real layout. */
class PixIdForm(
    private val binding: FragmentPixIdSetupBinding,
    onSave: (String) -> Unit,
    private val onEdited: () -> Unit,
) {
    private val context = binding.root.context
    private var state = AccountUiState()

    init {
        val submit = { if (binding.saveButton.isEnabled) onSave(binding.pixIdInput.text.toString()) }
        binding.pixIdInput.doAfterTextChanged {
            if (state.error != null) onEdited()
            renderRule()
        }
        binding.pixIdInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.saveButton.setOnClickListener { submit() }
        renderRule()
    }

    fun render(state: AccountUiState) {
        this.state = state
        renderRule()
    }

    private fun renderRule() {
        val pixId = binding.pixIdInput.text.toString()
        val error = state.error as? AccountException
        val atField = error?.error == AccountError.PIX_ID_TAKEN || error?.error == AccountError.PIX_ID_INVALID
        if (error != null && atField) {
            binding.pixIdRule.showRule(context.accountMessage(error), broken = true)
        } else {
            binding.pixIdRule.showRule(
                context.getString(R.string.pix_id_rule),
                pixId.isNotEmpty() && pixIdProblem(pixId) != null,
            )
        }
        binding.errorText.isVisible = state.error != null && !atField
        binding.errorText.text = state.error?.let(context::accountMessage)
        binding.saveButton.isEnabled = !state.busy && pixIdProblem(pixId) == null
    }
}
