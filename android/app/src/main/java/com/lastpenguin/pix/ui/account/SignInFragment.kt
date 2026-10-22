// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentSignInBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Sign in (R&S 6.4, 6.8; FR-5.2, FR-5.13): a Pix ID and a password, *Continue with Google*, or *Sign up*. A Google
 * account that no Pix account uses asks "Do you already have a Pix account?"; *Yes* stays here and adds Google to the
 * account the person signs in to. Owner: Accounts (#54).
 */
class SignInFragment : Fragment(R.layout.fragment_sign_in) {

    private val viewModel: AccountViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSignInBinding.bind(view)
        if (savedInstanceState == null) viewModel.clearError()
        val form = SignInForm(
            binding,
            onSignIn = viewModel::logIn,
            onGoogle = { viewModel.continueWithGoogle(requireActivity()) },
            onSignUp = {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.signInFragment) {
                    navController.navigate(R.id.action_signIn_to_signUp)
                }
            },
            onEdited = viewModel::clearError,
        )
        binding.backButton.setOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { leave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(form::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun onEvent(event: AccountEvent) {
        when (event) {
            is AccountEvent.SignedIn -> if (event.needsPixId) {
                findNavController().navigate(R.id.action_signIn_to_pixIdSetup)
            } else {
                findNavController().popBackStack(R.id.friendsFragment, false)
            }

            AccountEvent.AskExistingAccount -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.existing_account_title)
                .setMessage(R.string.existing_account_message)
                .setPositiveButton(R.string.existing_account_yes) { _, _ -> viewModel.answerExistingAccount(true) }
                .setNegativeButton(R.string.existing_account_no) { _, _ -> viewModel.answerExistingAccount(false) }
                .setOnCancelListener { viewModel.forgetGoogle() }
                .show()

            is AccountEvent.GoogleNotAdded -> Toast.makeText(
                requireContext(),
                getString(R.string.google_not_added, requireContext().accountMessage(event.error)),
                Toast.LENGTH_LONG,
            ).show()

            else -> Unit
        }
    }

    /** Back to Friends; a Google account waiting to be added is forgotten. */
    private fun leave() {
        viewModel.forgetGoogle()
        findNavController().popBackStack()
    }
}

/** The fields and buttons of Sign in. Apart from the fragment, so it can be tested on the real layout. */
class SignInForm(
    private val binding: FragmentSignInBinding,
    onSignIn: (pixId: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onSignUp: () -> Unit,
    /** A field changed: the last error no longer applies. */
    private val onEdited: () -> Unit,
) {
    private val context = binding.root.context
    private var state = AccountUiState()

    init {
        val submit = {
            if (binding.signInButton.isEnabled) {
                onSignIn(binding.pixIdInput.text.toString(), binding.passwordInput.text.toString())
            }
        }
        binding.pixIdInput.doAfterTextChanged { edited() }
        binding.passwordInput.doAfterTextChanged { edited() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.signInButton.setOnClickListener { submit() }
        binding.googleButton.setOnClickListener { onGoogle() }
        binding.signUpButton.setOnClickListener { onSignUp() }
    }

    fun render(state: AccountUiState) {
        this.state = state
        binding.bodyText.setText(if (state.linkingGoogle) R.string.link_google_body else R.string.sign_in_body)
        // While a Google account waits to be added, only the Pix ID and the password can sign in (FR-5.13).
        binding.orText.isVisible = !state.linkingGoogle
        binding.googleButton.isVisible = !state.linkingGoogle
        binding.signUpButton.isVisible = !state.linkingGoogle
        binding.googleButton.isEnabled = !state.busy
        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error?.let(context::accountMessage)
        renderButton()
    }

    private fun edited() {
        if (state.error != null) onEdited()
        renderButton()
    }

    private fun renderButton() {
        binding.signInButton.isEnabled =
            !state.busy && !binding.pixIdInput.text.isNullOrBlank() && !binding.passwordInput.text.isNullOrEmpty()
    }
}
