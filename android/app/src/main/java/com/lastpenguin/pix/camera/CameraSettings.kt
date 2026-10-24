// AI-generated with Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * The camera choices that outlive the app: the aspect ratio, restored when the Camera screen opens (Design 2.8).
 * Owner: Camera/Overlay.
 */
interface CameraSettings {
    /** The last ratio chosen, or null before the first choice or when it cannot be read. */
    suspend fun aspectRatio(): AspectRatio?

    suspend fun saveAspectRatio(ratio: AspectRatio)

    /** Remembers nothing beyond the process; for tests and screens that do not keep settings. */
    class InMemory : CameraSettings {
        private var ratio: AspectRatio? = null

        override suspend fun aspectRatio(): AspectRatio? = ratio

        override suspend fun saveAspectRatio(ratio: AspectRatio) {
            this.ratio = ratio
        }
    }
}

private val Context.cameraSettingsStore by preferencesDataStore(name = "camera_settings")

/** Kept in Jetpack DataStore (Design 1.2). A storage error leaves the default 4:3. */
class DataStoreCameraSettings(context: Context) : CameraSettings {

    private val store = context.applicationContext.cameraSettingsStore

    override suspend fun aspectRatio(): AspectRatio? = try {
        store.data.first()[ASPECT_RATIO]?.let { name -> AspectRatio.entries.firstOrNull { it.name == name } }
    } catch (error: IOException) {
        Log.w(TAG, "Could not read the aspect ratio", error)
        null
    }

    override suspend fun saveAspectRatio(ratio: AspectRatio) {
        try {
            store.edit { it[ASPECT_RATIO] = ratio.name }
        } catch (error: IOException) {
            Log.w(TAG, "Could not save the aspect ratio", error)
        }
    }

    private companion object {
        const val TAG = "PixCameraSettings"
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
    }
}
