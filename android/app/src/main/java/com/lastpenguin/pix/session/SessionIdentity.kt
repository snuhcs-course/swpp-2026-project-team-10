// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * What this phone says about itself in `hello` (Design 2.5.1). Iteration 1 has no accounts, so the name is the
 * phone's name from the system settings, or its model.
 */
data class SessionIdentity(val name: String, val appVersion: String) {

    companion object {
        private const val MAX_NAME_LENGTH = 40

        fun of(context: Context, appVersion: String): SessionIdentity {
            val deviceName = runCatching {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            }.getOrNull()
            val name = deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: Build.MODEL
            return SessionIdentity(name.take(MAX_NAME_LENGTH), appVersion)
        }
    }
}
