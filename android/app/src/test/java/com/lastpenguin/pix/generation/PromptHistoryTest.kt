// AI-generated with Claude Code, 2026-11-03, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.StartedDescription
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * The descriptions used before (FR-4.11; Testing Documentation 2.1.6), kept in memory. Partitions: a first
 * description, with its role and time; the same text again, also with other whitespace (one entry, moved to the front,
 * the friends joined, the role and time the latest); a blank text (nothing); a friend kept, and none without one;
 * MAX_ENTRIES kept and the one used longest ago dropped at one more; ✕ on a kept text and on an unknown one; the
 * subject's sets followed with the friend the start found, and a blank one not; a DataStore read that fails, which
 * keeps the entries read last and reads again, so a change written meanwhile shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptHistoryTest {
    private var now = 1_000L
    private val history = PromptHistory.InMemory { now }

    private val entries get() = history.entries.value

    @Test
    fun aDescription_isKeptWithItsRoleAndWhenItWasUsed() = runTest {
        history.record("Wave", Role.PHOTOGRAPHER, partnerId = null)

        assertEquals(listOf(PromptEntry("Wave", 1_000, Role.PHOTOGRAPHER)), entries)
    }

    @Test
    fun theSameText_isOneEntry_movedToTheFront_withItsFriendsJoined() = runTest {
        history.record("Wave", Role.PHOTOGRAPHER, "u_2")
        now = 2_000
        history.record("Jump", Role.PHOTOGRAPHER, null)
        now = 3_000
        history.record("  Wave\n", Role.SUBJECT, "u_3")
        now = 4_000
        history.record(" Wave ", Role.SUBJECT, "u_3")

        assertEquals(
            listOf(
                PromptEntry("Wave", 4_000, Role.SUBJECT, setOf("u_2", "u_3")),
                PromptEntry("Jump", 2_000, Role.PHOTOGRAPHER),
            ),
            entries,
        )
    }

    @Test
    fun aDescriptionWithRunsOfWhitespace_isKeptAsTheSetAskedForIt() = runTest {
        history.record("  lean   on\tthe wall ", Role.PHOTOGRAPHER, null)

        assertEquals("lean on the wall", entries.single().text)
    }

    @Test
    fun aBlankText_isNothingToKeep() = runTest {
        history.record(" \n\t ", Role.PHOTOGRAPHER, "u_2")

        assertEquals(emptyList<PromptEntry>(), entries)
    }

    @Test
    fun atMostFiftyAreKept_andTheOneUsedLongestAgoGoesFirst() = runTest {
        for (i in 1..PromptHistory.MAX_ENTRIES) {
            now = i.toLong()
            history.record("pose $i", Role.PHOTOGRAPHER, null)
        }
        assertEquals(PromptHistory.MAX_ENTRIES, entries.size)
        assertEquals("pose 1", entries.last().text)

        now = 51
        history.record("pose 51", Role.PHOTOGRAPHER, null)

        assertEquals(PromptHistory.MAX_ENTRIES, entries.size)
        assertEquals("pose 51", entries.first().text)
        assertEquals("pose 2", entries.last().text)
    }

    @Test
    fun delete_dropsTheEntry_andAnUnknownTextChangesNothing() = runTest {
        history.record("Wave", Role.PHOTOGRAPHER, null)
        history.record("Jump", Role.PHOTOGRAPHER, null)

        history.delete("Wave")
        history.delete("Lean")

        assertEquals(listOf("Jump"), entries.map { it.text })
    }

    @Test
    fun theSubjectsSets_areKeptAsTheyStart_withTheFriendTheStartFound() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, backgroundScope)
        runCurrent()

        started.emit(StartedDescription("Wave", SessionPartner("u_1", "Dongje")))
        now = 2_000
        started.emit(StartedDescription("Jump", null))
        started.emit(StartedDescription("   ", SessionPartner("u_1", "Dongje")))
        runCurrent()

        assertEquals(
            listOf(
                PromptEntry("Jump", 2_000, Role.SUBJECT),
                PromptEntry("Wave", 1_000, Role.SUBJECT, setOf("u_1")),
            ),
            entries,
        )
    }

    @Test
    fun aReadThatFails_keepsTheEntriesReadLast_andReadsAgainSoAChangeStillShows() = runTest {
        val store = FlakyStore(failures = 1)
        val kept = DataStorePromptHistory(store, backgroundScope) { 1_000 }
        runCurrent()
        assertEquals(emptyList<PromptEntry>(), kept.entries.value)

        kept.record("Wave", Role.PHOTOGRAPHER, null)
        runCurrent()
        assertEquals(emptyList<PromptEntry>(), kept.entries.value)

        advanceTimeBy(DataStorePromptHistory.READ_RETRY_MS + 1)
        runCurrent()
        assertEquals(listOf(PromptEntry("Wave", 1_000, Role.PHOTOGRAPHER)), kept.entries.value)
    }

    /** Preferences whose reads after the first fail [failures] times, as a storage error would, then show the data. */
    private class FlakyStore(private var failures: Int) : DataStore<Preferences> {
        private val stored = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = flow {
            emit(stored.value)
            stored.drop(1).collect { preferences ->
                if (failures > 0) {
                    failures--
                    throw IOException("storage error")
                }
                emit(preferences)
            }
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            stored.value = transform(stored.value)
            return stored.value
        }
    }
}
