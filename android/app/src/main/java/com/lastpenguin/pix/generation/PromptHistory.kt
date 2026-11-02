// AI-generated with Claude Code, 2026-11-02, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A description a set was made from before (FR-4.11), as *Generate poses* lists it. */
@Serializable
data class PromptEntry(
    /** The description as the set asked for it (`PoseRequest.of`); one entry per text. */
    val text: String,
    /** When a set was last made from it, on this phone's clock. */
    val usedAt: Long,
    /** This phone's part the last time: the photographer of the photo, or the subject in it. */
    val role: Role,
    /** The friends it was used with, by account id (Design 2.6.6); empty when used alone or over a room code. */
    val partners: Set<String> = emptySet(),
)

/**
 * The descriptions used before (FR-4.11; Design 2.6.3, Prompt history): on this phone only, since the server keeps
 * no description (NFR-13), and still there after the app restarts. Each set a description starts, on either phone of a
 * session, is recorded once it starts, with this phone's role and the session's friend; the same text used again
 * moves to the front and adds the friend. At most [MAX_ENTRIES] are kept, and the one used longest ago goes first.
 * Owner: Pose generation; the history Iter3-P24.
 */
interface PromptHistory {
    /** Newest use first; empty until the storage has been read once. */
    val entries: StateFlow<List<PromptEntry>>

    /**
     * A set started from [text] with this phone as [role], and with the friend [partnerId] in an invitation's
     * session. A blank text, which asks for suggested poses, is nothing to keep.
     */
    suspend fun record(text: String, role: Role, partnerId: String?)

    /** ✕ on the entry of [text]; an unknown text does nothing. */
    suspend fun delete(text: String)

    /** Keeps the history for the life of the process only; for tests and screens that keep none. */
    class InMemory(private val clock: () -> Long = System::currentTimeMillis) : PromptHistory {
        private val _entries = MutableStateFlow<List<PromptEntry>>(emptyList())
        override val entries: StateFlow<List<PromptEntry>> = _entries.asStateFlow()

        override suspend fun record(text: String, role: Role, partnerId: String?) {
            _entries.update { it.recording(text, role, partnerId, clock()) ?: it }
        }

        override suspend fun delete(text: String) {
            _entries.update { it.without(text) }
        }
    }

    companion object {
        const val MAX_ENTRIES = 50
    }
}

/**
 * These entries after a set started from [text]: its entry first, made now or moved there with [partnerId] added to
 * its friends, and the oldest beyond [PromptHistory.MAX_ENTRIES] dropped. Null when [text] is blank.
 */
internal fun List<PromptEntry>.recording(text: String, role: Role, partnerId: String?, now: Long): List<PromptEntry>? {
    val described = PoseRequest.of(text) as? PoseRequest.Described ?: return null
    val before = firstOrNull { it.text == described.text }
    val entry = PromptEntry(described.text, now, role, before?.partners.orEmpty() + listOfNotNull(partnerId))
    return (listOf(entry) + filter { it.text != described.text }).take(PromptHistory.MAX_ENTRIES)
}

internal fun List<PromptEntry>.without(text: String): List<PromptEntry> = filter { it.text != text }

/**
 * Subject: each set the photographer's phone started from a shared *Generate poses* ([started], its description) is
 * this phone's history too, with the session's friend from [partner] (Design 2.6.5). Followed on [scope] for the life
 * of the app, because the set can start while Subject view, not *Generate poses*, is in front.
 */
fun PromptHistory.followSubjectSets(started: Flow<String>, partner: () -> SessionPartner?, scope: CoroutineScope): Job =
    scope.launch { started.collect { text -> record(text, Role.SUBJECT, partner()?.userId) } }

private val Context.promptHistoryStore by preferencesDataStore(
    name = "prompt_history",
    // A file that cannot be read starts the history over, rather than ending every read with an error.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Kept in Jetpack DataStore (Design 1.2), as one JSON list. A storage error leaves the entries as they were read last,
 * and a change that cannot be written is logged and lost.
 */
class DataStorePromptHistory(
    context: Context,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : PromptHistory {

    private val store = context.applicationContext.promptHistoryStore
    private val json = Json { ignoreUnknownKeys = true }

    override val entries: StateFlow<List<PromptEntry>> = store.data
        .map(::read)
        .catch { error ->
            Log.w(TAG, "Could not read the prompt history", error)
            emit(emptyList())
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun record(text: String, role: Role, partnerId: String?) {
        change { it.recording(text, role, partnerId, clock()) }
    }

    override suspend fun delete(text: String) {
        change { it.without(text) }
    }

    /** Writes [transform] of the stored entries, unless it answers null. */
    private suspend fun change(transform: (List<PromptEntry>) -> List<PromptEntry>?) {
        try {
            store.edit { preferences ->
                transform(read(preferences))?.let { preferences[ENTRIES] = json.encodeToString(it) }
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not write the prompt history", error)
        }
    }

    /** The stored entries; a value that does not decode, from another version of the app, counts as none. */
    private fun read(preferences: Preferences): List<PromptEntry> =
        preferences[ENTRIES]?.let { runCatching { json.decodeFromString<List<PromptEntry>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val TAG = "PixPoses"
        val ENTRIES = stringPreferencesKey("entries")
    }
}
