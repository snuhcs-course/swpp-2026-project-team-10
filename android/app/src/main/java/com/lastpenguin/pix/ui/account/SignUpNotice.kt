// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ViewSignUpNoticeBinding

/**
 * What a Pix account keeps, and the agreement it waits for (FR-5.18, NFR-13), on Sign up and on Sign up with Google,
 * before the account is made: what, why, how long, and who receives it, with the privacy policy, and "I'm 14 or older,
 * and I agree". [google] names the Google account in place of the password. The checkbox keeps its state through a
 * recreated screen, which calls [onAgreed] again. Apart from the screens, so both use it and it is tested on the real
 * layout. Owner: Accounts (#54).
 */
class SignUpNotice(
    private val binding: ViewSignUpNoticeBinding,
    google: Boolean,
    onPolicy: () -> Unit,
    onAgreed: (Boolean) -> Unit,
) {
    val agreed: Boolean get() = binding.agreeCheck.isChecked

    init {
        binding.whatText.setText(
            if (google) R.string.sign_up_notice_what_google else R.string.sign_up_notice_what_password,
        )
        binding.policyButton.setOnClickListener { onPolicy() }
        binding.agreeCheck.setOnCheckedChangeListener { _, checked -> onAgreed(checked) }
    }
}
