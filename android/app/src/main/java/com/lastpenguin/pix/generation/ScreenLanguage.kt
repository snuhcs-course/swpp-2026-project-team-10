// AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.lastpenguin.pix.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language Pix's screens show, `ko` or `en`, for text the server writes (FR-10.2; Design 2.6.3). It comes from the
 * same resources as the strings, not from `Locale.getDefault()`, which can name a language Pix does not have.
 */
fun screenLanguage(resources: Resources): String =
    if (resources.getBoolean(R.bool.screens_in_korean)) KOREAN else ENGLISH

const val ENGLISH = "en"
const val KOREAN = "ko"

/**
 * [screenLanguage] while Pix runs: it follows the phone's language and Pix's own language setting, which Android passes
 * on as a configuration change, so the text the server wrote can be asked for again in the new language.
 */
fun screenLanguages(context: Context): StateFlow<String> {
    val app = context.applicationContext
    val language = MutableStateFlow(screenLanguage(app.resources))
    app.registerComponentCallbacks(
        object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                language.value = screenLanguage(app.createConfigurationContext(newConfig).resources)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        },
    )
    return language.asStateFlow()
}
