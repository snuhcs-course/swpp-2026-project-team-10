// AI-generated with Claude Code, 2026-10-26, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * Whether this phone has said that a session's live view uses mobile data (FR-6.13, Design 2.6.7). Said once.
 * Owner: Real-time.
 */
interface MobileDataNotice {
    suspend fun wasShown(): Boolean

    suspend fun markShown()

    /** Remembers nothing beyond the process; for tests. */
    class InMemory(private var shown: Boolean = false) : MobileDataNotice {
        override suspend fun wasShown(): Boolean = shown

        override suspend fun markShown() {
            shown = true
        }
    }
}

private val Context.sessionNoticeStore by preferencesDataStore(name = "session_notices")

/** Kept in Jetpack DataStore (Design 1.2). A storage error counts as not shown, so the notice may come again. */
class DataStoreMobileDataNotice(context: Context) : MobileDataNotice {

    private val store = context.applicationContext.sessionNoticeStore

    override suspend fun wasShown(): Boolean = try {
        store.data.first()[SHOWN] ?: false
    } catch (error: IOException) {
        Log.w(TAG, "Could not read the mobile-data notice", error)
        false
    }

    override suspend fun markShown() {
        try {
            store.edit { it[SHOWN] = true }
        } catch (error: IOException) {
            Log.w(TAG, "Could not save the mobile-data notice", error)
        }
    }

    private companion object {
        const val TAG = "PixSession"
        val SHOWN = booleanPreferencesKey("mobile_data_shown")
    }
}
