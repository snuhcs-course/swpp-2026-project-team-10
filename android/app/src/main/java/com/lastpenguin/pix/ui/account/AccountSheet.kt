// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.account.PasswordProblem
import com.lastpenguin.pix.account.isValidDisplayName
import com.lastpenguin.pix.account.passwordProblem
import com.lastpenguin.pix.databinding.DialogTextInputBinding
import com.lastpenguin.pix.databinding.SheetAccountBinding
import com.lastpenguin.pix.ui.PixViewModels
import kotlinx.coroutines.launch

/**
 * Account, opened from the account button on Friends (R&S 6.8; FR-5.12, FR-5.14; decided in Iter2-P13, until the
 * settings screen of Iterations 3–4): the name, which can be edited, the Pix ID, *Add Google* or *Add a password* for
 * the way the account cannot sign in yet, and *Sign out*. Owner: Accounts (#54).
 */
class AccountSheet : BottomSheetDialogFragment() {

    private val viewModel: AccountViewModel by activityViewModels { PixViewModels.Factory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetAccountBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = SheetAccountBinding.bind(view)
        if (savedInstanceState == null) viewModel.clearError()
        binding.editNameButton.setOnClickListener { editName() }
        binding.addGoogleOption.setOnClickListener { viewModel.addGoogle(requireActivity()) }
        binding.addPasswordOption.setOnClickListener { addPassword() }
        binding.signOutButton.setOnClickListener { viewModel.signOut() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.authState.collect { state ->
                        when (state) {
                            is AuthState.SignedIn -> render(binding, state.account)
                            AuthState.SignedOut -> dismiss()
                            AuthState.Loading -> Unit
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.errorText.isVisible = state.error != null
                        binding.errorText.text = state.error?.let(requireContext()::accountMessage)
                        binding.signOutButton.isEnabled = !state.busy
                        binding.addGoogleOption.isEnabled = !state.busy
                        binding.addPasswordOption.isEnabled = !state.busy
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        val message = when (event) {
                            AccountEvent.NameSaved -> R.string.name_saved
                            AccountEvent.GoogleAdded -> R.string.google_added
                            AccountEvent.PasswordAdded -> R.string.password_added
                            AccountEvent.SignedOut -> R.string.signed_out
                            else -> null
                        }
                        message?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
    }

    private fun render(binding: SheetAccountBinding, account: Account) {
        binding.nameText.text = account.displayName
        binding.pixIdText.isVisible = account.pixId != null
        binding.pixIdText.text = account.pixId?.let { getString(R.string.at_pix_id, it) }
        binding.methodsText.setText(
            when {
                account.hasGoogle && account.hasPassword -> R.string.signs_in_with_both
                account.hasGoogle -> R.string.signs_in_with_google
                else -> R.string.signs_in_with_password
            },
        )
        binding.addGoogleOption.isVisible = !account.hasGoogle
        // The Pix ID is the login name, so a password needs one first (FR-5.14).
        binding.addPasswordOption.isVisible = !account.hasPassword && account.pixId != null
    }

    private fun editName() {
        val current = (viewModel.authState.value as? AuthState.SignedIn)?.account?.displayName.orEmpty()
        showInputDialog(
            title = R.string.edit_name,
            hint = R.string.name_label,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            text = current,
            rule = { text -> getString(R.string.name_rule) to (text.isNotEmpty() && !isValidDisplayName(text)) },
            valid = ::isValidDisplayName,
            onSave = viewModel::saveDisplayName,
        )
    }

    private fun addPassword() {
        val pixId = (viewModel.authState.value as? AuthState.SignedIn)?.account?.pixId.orEmpty()
        showInputDialog(
            title = R.string.add_password,
            hint = R.string.password_label,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text = "",
            rule = { text ->
                val problem = passwordProblem(text, pixId)
                if (problem == PasswordProblem.SAME_AS_PIX_ID) {
                    getString(R.string.password_same_as_pix_id) to true
                } else {
                    getString(R.string.password_rule) to (text.isNotEmpty() && problem != null)
                }
            },
            valid = { passwordProblem(it, pixId) == null },
            onSave = viewModel::addPassword,
        )
    }

    /** A dialog with one field, its rule, and *Save*, which waits until [valid]. */
    private fun showInputDialog(
        @StringRes title: Int,
        @StringRes hint: Int,
        inputType: Int,
        text: String,
        rule: (String) -> Pair<String, Boolean>,
        valid: (String) -> Boolean,
        onSave: (String) -> Unit,
    ) {
        val field = DialogTextInputBinding.inflate(layoutInflater)
        field.input.setHint(hint)
        field.input.inputType = inputType
        field.input.setText(text)
        field.input.setSelection(text.length)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(field.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ -> onSave(field.input.text.toString()) }
            .show()
        val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        fun check() {
            val value = field.input.text.toString()
            val (ruleText, broken) = rule(value)
            field.ruleText.showRule(ruleText, broken)
            save.isEnabled = valid(value)
        }
        field.input.doAfterTextChanged { check() }
        check()
        field.input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}
