// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentSignUpAgreementBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.settings.openPrivacyPolicy
import kotlinx.coroutines.launch

/**
 * The sign-up agreement (R&S 6.4; FR-5.13, FR-5.18), before any account is made: what the account keeps, the privacy
 * policy, and *Agree*. Sign in's *Sign up* opens it as Sign up's first page, and *Agree* opens the form
 * (`SignUpFragment`) over it. "Do you already have a Pix account?" answered *No, make one* opens it as Sign up with
 * Google ([ARG_GOOGLE]): *Agree* makes the account from the waiting Google account and opens Pix ID setup in the place
 * of Sign in and this screen. There, Back returns to Sign in and forgets the Google account, as closing the question
 * does, or a later sign-in with a password would add it, and a new process with no waiting Google account goes back at
 * once. Owner: Accounts (#54).
 */
class SignUpAgreementFragment : Fragment(R.layout.fragment_sign_up_agreement) {

    private val viewModel: AccountViewModel by activityViewModels { PixViewModels.Factory }

    private val google: Boolean get() = arguments?.getBoolean(ARG_GOOGLE) == true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (google && !viewModel.waitingGoogle) {
            findNavController().popBackStack()
            return
        }
        val binding = FragmentSignUpAgreementBinding.bind(view)
        val page = SignUpAgreement(
            binding,
            google,
            onAgree = { if (google) viewModel.signUpWithGoogle() else openForm() },
            onPolicy = { openPrivacyPolicy(R.id.signUpAgreementFragment) },
        )
        binding.backButton.setOnClickListener { leave() }
        // Only the Google sign-up calls the server here; the form's own errors must not show on this page.
        if (!google) return
        if (savedInstanceState == null) viewModel.clearError()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { leave() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(page::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun openForm() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.signUpAgreementFragment) {
            navController.navigate(R.id.action_signUpAgreement_to_signUp)
        }
    }

    private fun onEvent(event: AccountEvent) {
        if (event !is AccountEvent.SignedIn) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.signUpAgreementFragment) return
        if (event.needsPixId) {
            navController.navigate(R.id.action_signUpAgreement_to_pixIdSetup)
        } else {
            navController.popBackStack(R.id.signInFragment, true)
        }
    }

    private fun leave() {
        if (google) viewModel.forgetGoogle()
        findNavController().popBackStack()
    }

    companion object {
        /** True for Sign up with Google: the notice names the Google account, and *Agree* makes the account. */
        const val ARG_GOOGLE = "google"
    }
}

/**
 * The agreement page's views: the title and the body for Sign up or Sign up with Google ([google]), what the account
 * keeps (the Google account in place of the password), and *Agree*, which waits for a call already running. Apart from
 * the fragment, so it can be tested on the real layout.
 */
class SignUpAgreement(
    private val binding: FragmentSignUpAgreementBinding,
    google: Boolean,
    onAgree: () -> Unit,
    onPolicy: () -> Unit,
) {
    private val context = binding.root.context

    init {
        binding.titleText.setText(if (google) R.string.google_sign_up_title else R.string.sign_up_title)
        binding.bodyText.setText(if (google) R.string.google_sign_up_body else R.string.sign_up_agreement_body)
        binding.whatText.setText(
            if (google) R.string.sign_up_notice_what_google else R.string.sign_up_notice_what_password,
        )
        binding.policyButton.setOnClickListener { onPolicy() }
        binding.agreeButton.setOnClickListener { if (binding.agreeButton.isEnabled) onAgree() }
        render(AccountUiState())
    }

    fun render(state: AccountUiState) {
        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error?.let(context::accountMessage)
        binding.agreeButton.isEnabled = !state.busy
    }
}
