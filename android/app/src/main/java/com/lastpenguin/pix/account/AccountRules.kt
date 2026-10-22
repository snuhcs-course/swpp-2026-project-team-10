// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

// The server's rules (Design 2.5.3, `server/src/pix_server/accounts/rules.py`), checked as the user types so that
// *Save* and *Sign up* stay disabled with the rule shown (US-6). The server checks them again.

const val PIX_ID_MIN = 4
const val PIX_ID_MAX = 20
const val PASSWORD_MIN = 8
const val PASSWORD_MAX = 64
const val DISPLAY_NAME_MAX = 30

private val PIX_ID_CHARACTERS = Regex("[a-z0-9._]+")

enum class PixIdProblem { TOO_SHORT, TOO_LONG, CHARACTERS }

enum class PasswordProblem { TOO_SHORT, TOO_LONG, LETTER_AND_DIGIT, SAME_AS_PIX_ID }

/** The first rule [pixId] breaks, as typed, or null (FR-5.2, FR-5.3). */
fun pixIdProblem(pixId: String): PixIdProblem? = when {
    pixId.length < PIX_ID_MIN -> PixIdProblem.TOO_SHORT
    pixId.length > PIX_ID_MAX -> PixIdProblem.TOO_LONG
    !PIX_ID_CHARACTERS.matches(pixId) -> PixIdProblem.CHARACTERS
    else -> null
}

/** The first rule [password] breaks, or null (FR-5.11). Lengths count characters, as the server does, not UTF-16 units. */
fun passwordProblem(password: String, pixId: String): PasswordProblem? {
    val length = password.codePointCount(0, password.length)
    return when {
        length < PASSWORD_MIN -> PasswordProblem.TOO_SHORT
        length > PASSWORD_MAX -> PasswordProblem.TOO_LONG
        password.none(Char::isLetter) || password.none(Char::isDigit) -> PasswordProblem.LETTER_AND_DIGIT
        password.equals(pixId, ignoreCase = true) -> PasswordProblem.SAME_AS_PIX_ID
        else -> null
    }
}

/** 1–30 characters after trimming. */
fun isValidDisplayName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.codePointCount(0, trimmed.length) in 1..DISPLAY_NAME_MAX
}

/** A Pix ID has no capitals or spaces, so the server ignores them when signing in and looking up (Design 2.5.3). */
fun normalizePixId(text: String): String = text.trim().lowercase()

/** What a Pix ID field keeps of typed or pasted text: lowercase letters, digits, "." and "_" (FR-5.5). */
fun keepPixIdCharacters(text: CharSequence): String =
    text.toString().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
