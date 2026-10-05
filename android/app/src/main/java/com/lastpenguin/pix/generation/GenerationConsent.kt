package com.lastpenguin.pix.generation

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * Whether the user agreed that scene photos go to an external AI service (FR-4.2). Asked once and remembered.
 * Owner: Server/AI/Sync (#7).
 */
interface GenerationConsent {
    suspend fun isGiven(): Boolean

    suspend fun give()
}

private val Context.poseGenerationStore by preferencesDataStore(name = "pose_generation")

/** Kept in Jetpack DataStore (Design 1.2). A storage error counts as not given, so the notice is shown again. */
class DataStoreGenerationConsent(context: Context) : GenerationConsent {

    private val store = context.applicationContext.poseGenerationStore

    override suspend fun isGiven(): Boolean = try {
        store.data.first()[GIVEN] ?: false
    } catch (error: IOException) {
        Log.w(TAG, "Could not read the consent", error)
        false
    }

    override suspend fun give() {
        try {
            store.edit { it[GIVEN] = true }
        } catch (error: IOException) {
            Log.w(TAG, "Could not save the consent", error)
        }
    }

    private companion object {
        const val TAG = "PixPoses"
        val GIVEN = booleanPreferencesKey("consent_given")
    }
}
