// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * indicator, and whether the first-use tutorial was seen (FR-10.4). Each change applies at once, through the flows.
 * The language is not here: Android keeps it as Pix's per-app language (`AppCompatDelegate.setApplicationLocales`).
 * Owner: Settings (#123).
 */
interface AppSettings {
    /** The color and thickness every guide on this phone is drawn in (FR-3.10). */
    val guideLook: StateFlow<GuideLook>

    /** Whether both phones' screens show the live view's delay during a session (FR-10.3); off by default. */
    val showLatency: StateFlow<Boolean>

    /** Whether the first-use tutorial was seen; null until the stored answer is read, so it never flashes (FR-10.4). */
    val tutorialSeen: StateFlow<Boolean?>

    suspend fun setGuideColor(color: GuideColor)

    suspend fun setGuideThickness(thickness: GuideThickness)

    suspend fun setShowLatency(show: Boolean)

    suspend fun setTutorialSeen()

    /** Remembers nothing beyond the process; for tests, which start as if the tutorial was seen. */
    class InMemory(
        look: GuideLook = GuideLook(),
        latency: Boolean = false,
        tutorial: Boolean? = true,
    ) : AppSettings {
        private val _guideLook = MutableStateFlow(look)
        override val guideLook: StateFlow<GuideLook> = _guideLook.asStateFlow()

        private val _showLatency = MutableStateFlow(latency)
        override val showLatency: StateFlow<Boolean> = _showLatency.asStateFlow()

        private val _tutorialSeen = MutableStateFlow(tutorial)
        override val tutorialSeen: StateFlow<Boolean?> = _tutorialSeen.asStateFlow()

        override suspend fun setGuideColor(color: GuideColor) {
            _guideLook.value = _guideLook.value.copy(color = color)
        }

        override suspend fun setGuideThickness(thickness: GuideThickness) {
            _guideLook.value = _guideLook.value.copy(thickness = thickness)
        }

        override suspend fun setShowLatency(show: Boolean) {
            _showLatency.value = show
        }

        override suspend fun setTutorialSeen() {
            _tutorialSeen.value = true
        }
    }
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

    override val tutorialSeen: StateFlow<Boolean?> =
        preferences.map<Preferences, Boolean?> {
            it[TUTORIAL_SEEN] ?: false
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun setGuideColor(color: GuideColor) = save { it[GUIDE_COLOR] = color.name }

    override suspend fun setGuideThickness(thickness: GuideThickness) = save { it[GUIDE_THICKNESS] = thickness.name }

    override suspend fun setShowLatency(show: Boolean) = save { it[SHOW_LATENCY] = show }

    override suspend fun setTutorialSeen() = save { it[TUTORIAL_SEEN] = true }

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
        val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    }
}
