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
import com.lastpenguin.pix.databinding.FragmentGoogleSignUpBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.settings.openPrivacyPolicy
import kotlinx.coroutines.launch

/**
 * Sign up with Google (R&S 6.4; FR-5.13, FR-5.18), after "Do you already have a Pix account?" answered *No, make one*:
 * what the account keeps and the agreement, before the waiting Google account makes it. *Sign up* makes the account
 * and opens Pix ID setup in the place of Sign in and this screen. Back returns to Sign in and forgets the Google
 * account, as closing the question does, or a later sign-in with a password would add it. A new process has no waiting
 * Google account, so the screen goes back at once. Owner: Accounts (#54).
 */
class GoogleSignUpFragment : Fragment(R.layout.fragment_google_sign_up) {

    private val viewModel: AccountViewModel by activityViewModels { PixViewModels.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!viewModel.waitingGoogle) {
            findNavController().popBackStack()
            return
        }
        val binding = FragmentGoogleSignUpBinding.bind(view)
        if (savedInstanceState == null) viewModel.clearError()
        val form = GoogleSignUpForm(
            binding,
            onSignUp = viewModel::signUpWithGoogle,
            onPolicy = { openPrivacyPolicy(R.id.googleSignUpFragment) },
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
        if (event !is AccountEvent.SignedIn) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.googleSignUpFragment) return
        if (event.needsPixId) {
            navController.navigate(R.id.action_googleSignUp_to_pixIdSetup)
        } else {
            navController.popBackStack(R.id.signInFragment, true)
        }
    }

    private fun leave() {
        viewModel.forgetGoogle()
        findNavController().popBackStack()
    }
}

/**
 * What the Google account's new Pix account keeps, the agreement, and *Sign up*, which waits for the agreement and for
 * a call already running. Apart from the fragment, so it can be tested on the real layout.
 */
class GoogleSignUpForm(
    private val binding: FragmentGoogleSignUpBinding,
    onSignUp: () -> Unit,
    onPolicy: () -> Unit,
) {
    private val context = binding.root.context
    private var state = AccountUiState()
    private val notice = SignUpNotice(binding.notice, google = true, onPolicy = onPolicy, onAgreed = { render(state) })

    init {
        binding.signUpButton.setOnClickListener { if (binding.signUpButton.isEnabled) onSignUp() }
        render(state)
    }

    fun render(state: AccountUiState) {
        this.state = state
        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error?.let(context::accountMessage)
        binding.signUpButton.isEnabled = !state.busy && notice.agreed
    }
}
