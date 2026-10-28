// AI-generated with Claude Code, 2026-10-27, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.GoogleSignInException
import com.lastpenguin.pix.account.GoogleSignInProblem
import com.lastpenguin.pix.account.pixIdProblem

/** What a failed account call tells the user. The server names a broken Pix ID or password rule itself (FR-5.3, 5.11). */
fun Context.accountMessage(error: Exception): String = when (error) {
    is AccountException -> {
        val namesRule = error.error == AccountError.PIX_ID_INVALID || error.error == AccountError.PASSWORD_INVALID
        if (namesRule && error.serverMessage.isNotBlank()) error.serverMessage else accountMessage(error.error)
    }

    is GoogleSignInException -> getString(
        if (error.problem == GoogleSignInProblem.NO_ACCOUNT) R.string.google_no_account else R.string.google_failed,
    )

    else -> getString(R.string.account_error)
}

fun Context.accountMessage(error: AccountError): String = getString(
    when (error) {
        AccountError.INVALID_CREDENTIALS -> R.string.wrong_credentials
        AccountError.PIX_ID_TAKEN -> R.string.pix_id_taken
        AccountError.PIX_ID_INVALID -> R.string.pix_id_rule
        AccountError.PASSWORD_INVALID -> R.string.password_rule
        AccountError.GOOGLE_IN_USE -> R.string.google_in_use
        AccountError.GOOGLE_ALREADY_ADDED -> R.string.google_already_added
        AccountError.INVALID_GOOGLE_TOKEN -> R.string.google_failed
        AccountError.NOT_FOUND -> R.string.request_gone
        AccountError.SIGNED_OUT -> R.string.signed_out_expired
        AccountError.OFFLINE -> R.string.offline_error
        AccountError.UNAVAILABLE -> R.string.unavailable_error
        else -> R.string.account_error
    },
)

/** The first character of a name, for a round avatar. */
fun avatarInitial(name: String): String {
    val trimmed = name.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}

/** A rule under a field: faint while it holds or the field is empty, in the danger color once it is broken (US-6). */
fun TextView.showRule(rule: CharSequence, broken: Boolean) {
    text = rule
    setTextColor(ContextCompat.getColor(context, if (broken) R.color.pix_danger_text else R.color.pix_text_faint))
}

/**
 * The line under a Pix ID field (US-6, FR-5.15): the server's [refusal] of the form, else the answer to *Check* while
 * the field still holds the checked Pix ID, else the rule.
 */
fun TextView.showPixIdStatus(pixId: String, refusal: AccountException?, check: PixIdCheck?) {
    val answer = check?.takeIf { it.pixId == pixId }
    when {
        refusal != null -> showRule(context.accountMessage(refusal), broken = true)

        answer?.error != null -> showRule(context.accountMessage(answer.error), broken = true)

        answer?.available == false -> showRule(context.getString(R.string.pix_id_taken), broken = true)

        answer?.available == true -> {
            text = context.getString(R.string.pix_id_available)
            setTextColor(ContextCompat.getColor(context, R.color.pix_live))
        }

        answer != null -> showRule(context.getString(R.string.checking_pix_id), broken = false)

        else -> showRule(context.getString(R.string.pix_id_rule), pixId.isNotEmpty() && pixIdProblem(pixId) != null)
    }
}

/** *Check* asks only for a Pix ID that follows the rule, and once at a time for the same one. */
fun canCheckPixId(pixId: String, check: PixIdCheck?): Boolean =
    pixIdProblem(pixId) == null && !(check?.pixId == pixId && check.checking)
