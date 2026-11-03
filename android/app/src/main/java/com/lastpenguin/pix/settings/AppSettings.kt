// AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
package com.lastpenguin.pix.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.guide.GuideLook
import com.lastpenguin.pix.guide.GuideThickness
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The choices on the settings screen that outlive the app (FR-10.1, Design 2.8): the guide's look and the latency
 * indicator, and which screens' tutorial tips were seen (FR-10.4). Each change applies at once, through the flows.
 * The language is not here: Android keeps it as Pix's per-app language (`AppCompatDelegate.setApplicationLocales`).
 * Owner: Settings (#123).
 */
interface AppSettings {
    /** The color and thickness every guide on this phone is drawn in (FR-3.10). */
    val guideLook: StateFlow<GuideLook>

    /** Whether both phones' screens show the live view's delay during a session (FR-10.3); off by default. */
    val showLatency: StateFlow<Boolean>

    /** The screens whose tutorial tips were seen; null until the stored answer is read, so none ever flash (FR-10.4). */
    val tutorialSeen: StateFlow<Set<TutorialScreen>?>

    suspend fun setGuideColor(color: GuideColor)

    suspend fun setGuideThickness(thickness: GuideThickness)

    suspend fun setShowLatency(show: Boolean)

    /** Adds [screens] to the tips seen. */
    suspend fun setTutorialSeen(screens: Set<TutorialScreen>)

    /** Forgets every tip seen, so each screen shows its tips again (*Show the tutorial again*). */
    suspend fun resetTutorial()

    /** Remembers nothing beyond the process; for tests, which start as if every screen's tips were seen. */
    class InMemory(
        look: GuideLook = GuideLook(),
        latency: Boolean = false,
        tutorial: Set<TutorialScreen>? = TutorialScreen.entries.toSet(),
    ) : AppSettings {
        private val _guideLook = MutableStateFlow(look)
        override val guideLook: StateFlow<GuideLook> = _guideLook.asStateFlow()

        private val _showLatency = MutableStateFlow(latency)
        override val showLatency: StateFlow<Boolean> = _showLatency.asStateFlow()

        private val _tutorialSeen = MutableStateFlow(tutorial)
        override val tutorialSeen: StateFlow<Set<TutorialScreen>?> = _tutorialSeen.asStateFlow()

        override suspend fun setGuideColor(color: GuideColor) {
            _guideLook.value = _guideLook.value.copy(color = color)
        }

        override suspend fun setGuideThickness(thickness: GuideThickness) {
            _guideLook.value = _guideLook.value.copy(thickness = thickness)
        }

        override suspend fun setShowLatency(show: Boolean) {
            _showLatency.value = show
        }

        override suspend fun setTutorialSeen(screens: Set<TutorialScreen>) {
            _tutorialSeen.value = _tutorialSeen.value.orEmpty() + screens
        }

        override suspend fun resetTutorial() {
            _tutorialSeen.value = emptySet()
        }
    }
}

/** The screens the first-use tutorial explains, each with its own tips the first time it opens (FR-10.4, Design 2.6.8). */
enum class TutorialScreen {
    CAMERA,
    ADD_GUIDE,
    GUIDE_ON_CAMERA,
    GENERATE_POSES,
    SUBJECT_VIEW,
}

private val Context.appSettingsStore by preferencesDataStore(name = "app_settings")

/**
 * Kept in Jetpack DataStore (Design 1.2), read into the flows on [scope] from the start. Until the first read, and after
 * a storage error, the flows hold the defaults; a choice that cannot be saved is lost with the process.
 */
class DataStoreAppSettings(context: Context, scope: CoroutineScope) : AppSettings {

    private val store = context.applicationContext.appSettingsStore

    private val preferences = store.data
        .catch { error ->
            if (error !is IOException) throw error
            Log.w(TAG, "Could not read the settings", error)
            emit(emptyPreferences())
        }

    override val guideLook: StateFlow<GuideLook> =
        preferences.map(::lookOf).stateIn(scope, SharingStarted.Eagerly, GuideLook())

    override val showLatency: StateFlow<Boolean> =
        preferences.map { it[SHOW_LATENCY] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override val tutorialSeen: StateFlow<Set<TutorialScreen>?> =
        preferences.map<Preferences, Set<TutorialScreen>?> { stored ->
            val names = stored[TUTORIAL_SEEN].orEmpty()
            TutorialScreen.entries.filter { it.name in names }.toSet()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun setGuideColor(color: GuideColor) = save { it[GUIDE_COLOR] = color.name }

    override suspend fun setGuideThickness(thickness: GuideThickness) = save { it[GUIDE_THICKNESS] = thickness.name }

    override suspend fun setShowLatency(show: Boolean) = save { it[SHOW_LATENCY] = show }

    override suspend fun setTutorialSeen(screens: Set<TutorialScreen>) = save { stored ->
        stored[TUTORIAL_SEEN] = stored[TUTORIAL_SEEN].orEmpty() + screens.map { it.name }
    }

    override suspend fun resetTutorial() = save { it.remove(TUTORIAL_SEEN) }

    private suspend fun save(change: suspend (MutablePreferences) -> Unit) {
        try {
            store.edit(change)
        } catch (error: IOException) {
            Log.w(TAG, "Could not save the settings", error)
        }
    }

    private fun lookOf(preferences: Preferences) = GuideLook(
        color = GuideColor.entries.firstOrNull { it.name == preferences[GUIDE_COLOR] } ?: GuideColor.WHITE,
        thickness = GuideThickness.entries.firstOrNull { it.name == preferences[GUIDE_THICKNESS] }
            ?: GuideThickness.NORMAL,
    )

    private companion object {
        const val TAG = "PixSettings"
        val GUIDE_COLOR = stringPreferencesKey("guide_color")
        val GUIDE_THICKNESS = stringPreferencesKey("guide_thickness")
        val SHOW_LATENCY = booleanPreferencesKey("show_latency")
        val TUTORIAL_SEEN = stringSetPreferencesKey("tutorial_seen_screens")
    }
}
