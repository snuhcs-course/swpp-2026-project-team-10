// AI-generated with Claude Code, 2026-11-13, reviewed by Jaewan Park and Joonhyung Han
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
import com.lastpenguin.pix.settings.TutorialScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
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

    /** The Camera screen's base brightness in EV (FR-1.11), which its tap to focus goes back to. */
    val baseBrightness: StateFlow<Float> = settings.baseBrightness

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

    fun setBaseBrightness(ev: Float) {
        viewModelScope.launch { settings.setBaseBrightness(ev) }
    }

    /** Set at once: the stored answer comes back later, and a screen shown again must not show its tips twice. */
    private val tutorialClosed = MutableStateFlow(emptySet<TutorialScreen>())

    /** Whether [screen]'s tips still have to show there; false until the stored answer is read (FR-10.4). */
    fun tutorialDue(screen: TutorialScreen): Flow<Boolean> =
        combine(settings.tutorialSeen, tutorialClosed) { seen, closed ->
            seen != null && screen !in seen && screen !in closed
        }.distinctUntilChanged()

    /** The tips of [screens] closed, by a tap or Back: they do not come by themselves again. */
    fun tutorialSeen(screens: Set<TutorialScreen>) {
        tutorialClosed.update { it + screens }
        viewModelScope.launch { settings.setTutorialSeen(screens) }
    }

    /** *Show the tutorial again*: each screen shows its tips again the next time it opens. */
    fun resetTutorial() {
        tutorialClosed.value = emptySet()
        viewModelScope.launch { settings.resetTutorial() }
    }

    /** Pix's own language (FR-10.2), read each time, since Android keeps it. */
    val language: Language get() = languages.current

    /** Recreates the activity in [language]; this screen stays on the back stack. */
    fun chooseLanguage(language: Language) = languages.choose(language)
}
