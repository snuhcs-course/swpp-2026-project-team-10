// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.session

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * What this phone says about itself in `hello` (Design 2.5.1). [name] is the phone's name from the system settings, or
 * its model, which `hello` sends while no one is signed in; signed in, it sends the account's name ([nameFor], #271).
 */
data class SessionIdentity(val name: String, val appVersion: String) {

    /** The name `hello` sends: the signed-in account's [accountName], else the phone's. */
    fun nameFor(accountName: String?): String =
        accountName?.trim()?.takeIf { it.isNotEmpty() }?.atMost(MAX_NAME_LENGTH) ?: name

    companion object {
        private const val MAX_NAME_LENGTH = 40

        /**
         * The first [count] characters, counted by code point: `take` counts UTF-16 units and can cut an emoji in two,
         * which the other phone shows as a broken character.
         */
        private fun String.atMost(count: Int): String =
            if (codePointCount(0, length) <= count) this else substring(0, offsetByCodePoints(0, count))

        fun of(context: Context, appVersion: String): SessionIdentity {
            val deviceName = runCatching {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            }.getOrNull()
            val name = deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: Build.MODEL
            return SessionIdentity(name.atMost(MAX_NAME_LENGTH), appVersion)
        }
    }
}
