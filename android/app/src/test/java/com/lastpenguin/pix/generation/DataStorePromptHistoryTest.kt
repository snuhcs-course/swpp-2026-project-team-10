// AI-generated with Claude Code, 2026-11-02, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.session.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/*
 * The descriptions used before (FR-4.11), kept across runs: none at first, a recorded one listed, still listed by a
 * new instance, and gone for both once deleted. One test, because the DataStore behind the context extension lives
 * for the process.
 */
@RunWith(AndroidJUnit4::class)
class DataStorePromptHistoryTest {
    @Test
    fun emptyAtFirst_thenRecordedAndKeptForANewInstance_andGoneOnceDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(Dispatchers.Default)
        val history = DataStorePromptHistory(context, scope) { 1_000 }
        assertEquals(emptyList<PromptEntry>(), history.entries.value)

        history.record("Wave", Role.PHOTOGRAPHER, "u_2")
        val kept = listOf(PromptEntry("Wave", 1_000, Role.PHOTOGRAPHER, setOf("u_2")))
        assertEquals(kept, withTimeout(5_000) { history.entries.first { it.isNotEmpty() } })

        val again = DataStorePromptHistory(context, scope)
        assertEquals(kept, withTimeout(5_000) { again.entries.first { it.isNotEmpty() } })

        again.delete("Wave")
        assertEquals(emptyList<PromptEntry>(), withTimeout(5_000) { history.entries.first { it.isEmpty() } })
        scope.cancel()
    }
}
