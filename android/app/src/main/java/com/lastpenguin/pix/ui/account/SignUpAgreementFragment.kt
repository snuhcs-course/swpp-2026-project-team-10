// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentSignUpAgreementBinding
import com.lastpenguin.pix.databinding.ItemConsentDetailBinding
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.settings.openPrivacyPolicy
import kotlinx.coroutines.launch

/**
 * The sign-up agreement (R&S 6.4; FR-5.13, FR-5.18), before any account is made: the three required consents with
 * their details, the privacy policy, and *Agree and continue*. Sign in's *Sign up* opens it as Sign up's first page,
 * and *Agree and continue* opens the form (`SignUpFragment`) over it. "Do you already have a Pix account?" answered
 * *No, make one* opens it as Sign up with Google ([ARG_GOOGLE]): *Agree and continue* makes the account from the
 * waiting Google account and opens Pix ID setup in the place of Sign in and this screen. There, Back returns to Sign in
 * and forgets the Google account, as closing the question does, or a later sign-in with a password would add it, and a
 * new process with no waiting Google account goes back at once. Owner: Accounts (#54).
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
        /** True for Sign up with Google: the details name the Google account, and *Agree and continue* makes it. */
        const val ARG_GOOGLE = "google"
    }
}

/**
 * The agreement page's views: the header for Sign up or Sign up with Google ([google]), and the three
 * required consents (14 or older, collection and use, transfer abroad), each with its own box. *Agree to all* checks or
 * clears the three, and follows them. The last two show their details, a label in its own column beside each value,
 * under their chevrons; what is collected names the Google account in place of the password. *Agree and continue* waits
 * for all three and for a call already running. The boxes keep their state through a recreated screen. Apart from the
 * fragment, so it can be tested on the real layout.
 */
class SignUpAgreement(
    private val binding: FragmentSignUpAgreementBinding,
    google: Boolean,
    onAgree: () -> Unit,
    onPolicy: () -> Unit,
) {
    private val context = binding.root.context
    private val consents = listOf(binding.ageCheck, binding.collectionCheck, binding.transferCheck)
    private var busy = false

    /** Whether all three consents are given. */
    val agreed: Boolean get() = consents.all { it.isChecked }

    init {
        binding.titleText.setText(if (google) R.string.google_sign_up_title else R.string.sign_up_title)
        binding.ageCheck.text = required(R.string.sign_up_consent_age)
        binding.collectionCheck.text = required(R.string.sign_up_consent_collection)
        binding.transferCheck.text = required(R.string.sign_up_consent_transfer)

        val items = if (google) R.string.sign_up_consent_items_google else R.string.sign_up_consent_items_password
        details(
            binding.collectionDetails,
            R.string.sign_up_consent_label_items to items,
            R.string.sign_up_consent_label_purpose to R.string.sign_up_consent_purpose,
            R.string.sign_up_consent_label_period to R.string.sign_up_consent_period,
        )
        details(
            binding.transferDetails,
            R.string.sign_up_consent_label_recipients to R.string.sign_up_consent_transfer_recipients,
            R.string.sign_up_consent_label_items to R.string.sign_up_consent_transfer_items,
            R.string.sign_up_consent_label_purpose to R.string.sign_up_consent_transfer_purpose,
            R.string.sign_up_consent_label_when to R.string.sign_up_consent_transfer_when,
            R.string.sign_up_consent_label_period to R.string.sign_up_consent_transfer_period,
        )
        expandable(binding.collectionToggle, binding.collectionDetails, R.string.sign_up_consent_collection)
        expandable(binding.transferToggle, binding.transferDetails, R.string.sign_up_consent_transfer)

        // A tap, not a change, so a restored box does not check or clear the others.
        binding.allCheck.setOnClickListener {
            val checked = binding.allCheck.isChecked
            consents.forEach { it.isChecked = checked }
        }
        consents.forEach { consent ->
            consent.setOnCheckedChangeListener { _, _ ->
                binding.allCheck.isChecked = agreed
                renderButton()
            }
        }
        binding.policyRow.settingTitle.setText(R.string.sign_up_consent_policy)
        binding.policyRow.settingValue.isVisible = false
        binding.policyRow.root.setOnClickListener { onPolicy() }
        binding.agreeButton.setOnClickListener { if (binding.agreeButton.isEnabled) onAgree() }
        render(AccountUiState())
    }

    fun render(state: AccountUiState) {
        busy = state.busy
        binding.errorText.isVisible = state.error != null
        binding.errorText.text = state.error?.let(context::accountMessage)
        renderButton()
    }

    private fun renderButton() {
        binding.agreeButton.isEnabled = !busy && agreed
    }

    /** "(Required) [text]", the mark in the accent color. */
    private fun required(@StringRes text: Int): CharSequence {
        val mark = context.getString(R.string.sign_up_consent_required)
        return SpannableStringBuilder()
            .append(mark, ForegroundColorSpan(ContextCompat.getColor(context, R.color.pix_accent)), 0)
            .append(' ')
            .append(context.getString(text))
    }

    /** The [lines], label and value, at the top of [into], above what the layout put there (the right to refuse). */
    private fun details(into: LinearLayout, vararg lines: Pair<Int, Int>) {
        val inflater = LayoutInflater.from(context)
        lines.forEachIndexed { index, (label, value) ->
            val line = ItemConsentDetailBinding.inflate(inflater, into, false)
            line.detailLabel.setText(label)
            line.detailValue.setText(value)
            into.addView(line.root, index)
        }
    }

    /** [toggle] shows and hides [details]; its chevron points down while they are hidden, up while they show. */
    private fun expandable(toggle: ImageButton, details: View, @StringRes name: Int) {
        fun show(open: Boolean) {
            details.isVisible = open
            toggle.rotation = if (open) -90f else 90f
            val description = if (open) R.string.sign_up_consent_details_hide else R.string.sign_up_consent_details_show
            toggle.contentDescription = context.getString(description, context.getString(name))
        }
        show(false)
        toggle.setOnClickListener { show(!details.isVisible) }
    }
}
