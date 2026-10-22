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
import com.lastpenguin.pix.account.PasswordProblem
import com.lastpenguin.pix.account.isValidDisplayName
import com.lastpenguin.pix.account.passwordProblem
import com.lastpenguin.pix.account.pixIdProblem
import com.lastpenguin.pix.databinding.FragmentSignUpBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Sign up with a Pix ID, a name, and a password (R&S 6.8; FR-5.2, FR-5.3, FR-5.11, US-6). The account is signed in at
 * once and Friends opens. Owner: Accounts (#54).
 */
class SignUpFragment : Fragment(R.layout.fragment_sign_up) {

    private val viewModel: AccountViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSignUpBinding.bind(view)
        if (savedInstanceState == null) viewModel.clearError()
        val form = SignUpForm(binding, onSignUp = viewModel::signUp, onEdited = viewModel::clearError)
        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(form::render) }
                launch { viewModel.events.collect { if (it is AccountEvent.SignedIn) toFriends() } }
            }
        }
    }

    private fun toFriends() {
        findNavController().popBackStack(R.id.friendsFragment, false)
    }
}

/**
 * The fields of Sign up, each with its rule under it (US-6). A rule shows faint until the field breaks it; the server's
 * answer for a taken Pix ID or a refused password shows in the same place. Apart from the fragment, so it can be tested
 * on the real layout.
 */
class SignUpForm(
    private val binding: FragmentSignUpBinding,
    onSignUp: (pixId: String, displayName: String, password: String) -> Unit,
    private val onEdited: () -> Unit,
) {
    private val context = binding.root.context
    private var state = AccountUiState()

    init {
        val submit = {
            if (binding.signUpButton.isEnabled) {
                onSignUp(pixId(), binding.nameInput.text.toString(), binding.passwordInput.text.toString())
            }
        }
        for (input in listOf(binding.pixIdInput, binding.nameInput, binding.passwordInput)) {
            input.doAfterTextChanged {
                if (state.error != null) onEdited()
                renderRules()
            }
        }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.signUpButton.setOnClickListener { submit() }
        renderRules()
    }

    fun render(state: AccountUiState) {
        this.state = state
        renderRules()
    }

    private fun pixId() = binding.pixIdInput.text.toString()

    private fun renderRules() {
        val error = state.error as? AccountException
        val pixId = pixId()
        val pixIdBroken = pixId.isNotEmpty() && pixIdProblem(pixId) != null
        when (error?.error) {
            AccountError.PIX_ID_TAKEN, AccountError.PIX_ID_INVALID ->
                binding.pixIdRule.showRule(context.accountMessage(error), broken = true)

            else -> binding.pixIdRule.showRule(context.getString(R.string.pix_id_rule), pixIdBroken)
        }

        val name = binding.nameInput.text.toString()
        binding.nameRule.showRule(context.getString(R.string.name_rule), name.isNotEmpty() && !isValidDisplayName(name))

        val password = binding.passwordInput.text.toString()
        val problem = passwordProblem(password, pixId)
        when {
            error?.error == AccountError.PASSWORD_INVALID ->
                binding.passwordRule.showRule(context.accountMessage(error), broken = true)

            problem == PasswordProblem.SAME_AS_PIX_ID ->
                binding.passwordRule.showRule(context.getString(R.string.password_same_as_pix_id), broken = true)

            else -> binding.passwordRule.showRule(
                context.getString(R.string.password_rule),
                password.isNotEmpty() && problem != null,
            )
        }

        val shownAtField = error?.error in FIELD_ERRORS
        binding.errorText.isVisible = state.error != null && !shownAtField
        binding.errorText.text = state.error?.let(context::accountMessage)
        binding.signUpButton.isEnabled =
            !state.busy && pixIdProblem(pixId) == null && isValidDisplayName(name) && problem == null
    }

    private companion object {
        val FIELD_ERRORS = setOf(AccountError.PIX_ID_TAKEN, AccountError.PIX_ID_INVALID, AccountError.PASSWORD_INVALID)
    }
}
