// AI-generated with Claude Code, 2026-11-02, reviewed by Dongje Park and Sungmin Jo
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
 * [screenLanguage] while Pix runs, in [current]: it follows the phone's language, which Android passes on as a
 * configuration change, and Pix's own language from the settings screen, so the text the server wrote can be asked for
 * again in the new language. From Android 13 Pix's own language reaches the application too; on Android 10–12 AppCompat
 * gives it to the activity only, so `MainActivity` hands its resources to [follow] each time it is created, which a new
 * language does.
 */
class ScreenLanguages(context: Context) {
    private val app = context.applicationContext
    private val language = MutableStateFlow(screenLanguage(context.resources))

    val current: StateFlow<String> = language.asStateFlow()

    init {
        app.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    language.value = screenLanguage(app.createConfigurationContext(newConfig).resources)
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            },
        )
    }

    /** The language of an activity's [resources], which are the screens' own. */
    fun follow(resources: Resources) {
        language.value = screenLanguage(resources)
    }
}
