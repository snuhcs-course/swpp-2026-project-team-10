// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.account.AuthRepository
import com.lastpenguin.pix.account.AuthState
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideThickness
import com.lastpenguin.pix.settings.AppLanguage
import com.lastpenguin.pix.settings.AppSettings
import com.lastpenguin.pix.settings.Language
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The settings screen's choices (FR-10.1), shared by the activity: the camera screens read the guide's look and the
 * latency indicator from here, so a change applies the moment they show again. The AI consent is
 * `GenerationViewModel`'s, which asks for it.
 * Owner: Settings (#123).
 */
class SettingsViewModel(
    private val settings: AppSettings,
    private val languages: AppLanguage,
    auth: AuthRepository,
) : ViewModel() {

    val guideLook: StateFlow<GuideLook> = settings.guideLook

    val showLatency: StateFlow<Boolean> = settings.showLatency

    /** For the *Pix account* row: the Pix ID once signed in. */
    val account: StateFlow<AuthState> = auth.state

    fun setGuideColor(color: GuideColor) {
        viewModelScope.launch { settings.setGuideColor(color) }
    }

    fun setGuideThickness(thickness: GuideThickness) {
        viewModelScope.launch { settings.setGuideThickness(thickness) }
    }

    fun setShowLatency(show: Boolean) {
        viewModelScope.launch { settings.setShowLatency(show) }
    }

    /** Pix's own language (FR-10.2), read each time, since Android keeps it. */
    val language: Language get() = languages.current

    /** Recreates the activity in [language]; this screen stays on the back stack. */
    fun chooseLanguage(language: Language) = languages.choose(language)
}
