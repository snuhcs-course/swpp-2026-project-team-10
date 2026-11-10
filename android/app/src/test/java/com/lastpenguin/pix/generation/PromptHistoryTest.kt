// AI-generated with Claude Code, 2026-11-10, reviewed by Dongje Park and Joonhyung Han
package com.lastpenguin.pix.generation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lastpenguin.pix.session.MirrorPoseSet
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.StartedDescription
import com.lastpenguin.pix.session.protocol.PoseSetStep
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
 * subject's sets followed with the friend the start found, kept once the set that follows the start has a candidate
 * or is PICKING, and a blank one not; the subject's set that ends without a candidate (a description the server turns
 * down), whose text is not kept, also when a later set without a start is accepted; an earlier set, accepted before
 * the start came, which does not count for it; the subject's set whose description the service refused itself (Iter4-P3):
 * not kept though a variation gives a candidate, taken out again when the refusal follows a candidate, and an entry an
 * earlier set or build kept removed, with the text as the start carries it; a DataStore read that fails, which
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

    /** The photographer's set on the subject's phone, as `PoseSyncer.mirror` holds it. */
    private val sets = MutableStateFlow<MirrorPoseSet?>(null)

    private fun generatingSet(id: String) = MirrorPoseSet(id, PoseSetStep.GENERATING)

    private fun withACandidate(id: String) =
        MirrorPoseSet(id, PoseSetStep.GENERATING, candidates = mapOf("pose-1" to testBitmap()))

    @Test
    fun theSubjectsSets_areKeptOnceTheServerAcceptsThem_withTheFriendTheStartFound() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        started.emit(StartedDescription("Wave", SessionPartner("u_1", "Dongje")))
        sets.value = generatingSet("s1")
        runCurrent()
        // Started, but not yet accepted.
        assertEquals(emptyList<PromptEntry>(), entries)
        sets.value = withACandidate("s1")
        runCurrent()
        assertEquals(listOf(PromptEntry("Wave", 1_000, Role.SUBJECT, setOf("u_1"))), entries)

        sets.value = null
        now = 2_000
        started.emit(StartedDescription("Jump", null))
        sets.value = generatingSet("s2")
        runCurrent()
        sets.value = MirrorPoseSet("s2", PoseSetStep.PICKING, poseIds = listOf("pose-1"))
        runCurrent()
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
    fun theSubjectsTurnedDownDescription_isNotKept() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        started.emit(StartedDescription("Bleeding", null))
        sets.value = generatingSet("s1")
        runCurrent()
        // Every candidate was refused: the set closes without one.
        sets.value = null
        runCurrent()
        // Suggested poses next, which start with no description: their candidates keep nothing.
        sets.value = generatingSet("s2")
        runCurrent()
        sets.value = withACandidate("s2")
        runCurrent()

        assertEquals(emptyList<PromptEntry>(), entries)
    }

    @Test
    fun anEarlierSetAcceptedBeforeTheStart_doesNotCountForIt() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        sets.value = withACandidate("s0")
        runCurrent()

        started.emit(StartedDescription("Wave", null))
        runCurrent()
        assertEquals(emptyList<PromptEntry>(), entries)

        // The earlier set's end, then this start's set, which the server accepts.
        sets.value = null
        sets.value = generatingSet("s1")
        runCurrent()
        assertEquals(emptyList<PromptEntry>(), entries)
        sets.value = withACandidate("s1")
        runCurrent()

        assertEquals(listOf(PromptEntry("Wave", 1_000, Role.SUBJECT)), entries)
    }

    @Test
    fun aStartThatNamesItsSet_countsThatSet_evenAcceptedBeforeTheStartArrived() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        // pose.step won the race: the started set already has a candidate when its start comes.
        sets.value = withACandidate("s1")
        runCurrent()
        started.emit(StartedDescription("Wave", null, setId = "s1"))
        runCurrent()
        assertEquals(listOf(PromptEntry("Wave", 1_000, Role.SUBJECT)), entries)

        // The start first, an earlier set still showing, then the named set's answer.
        now = 2_000
        started.emit(StartedDescription("Jump", null, setId = "s2"))
        runCurrent()
        assertEquals(1, entries.size)
        sets.value = generatingSet("s2")
        sets.value = withACandidate("s2")
        runCurrent()
        assertEquals(listOf("Jump", "Wave"), entries.map { it.text })
    }

    @Test
    fun theSubjectsSet_whoseDescriptionTheServiceRefused_isNotKept_thoughAVariationGivesACandidate() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        started.emit(StartedDescription("Write HACKED on the wall", null, setId = "s1"))
        sets.value = generatingSet("s1").copy(failed = setOf("pose-1"), descriptionRejected = true)
        runCurrent()
        sets.value = withACandidate("s1").copy(failed = setOf("pose-1"), descriptionRejected = true)
        runCurrent()
        sets.value = sets.value?.copy(step = PoseSetStep.PICKING, poseIds = listOf("pose-1"))
        runCurrent()

        assertEquals(emptyList<PromptEntry>(), entries)
    }

    @Test
    fun aRefusalOnTheSubjectsSet_afterACandidate_takesTheDescriptionOutAgain() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        started.emit(StartedDescription("Write HACKED on the wall", null, setId = "s1"))
        sets.value = withACandidate("s1")
        runCurrent()
        assertEquals(listOf("Write HACKED on the wall"), entries.map { it.text })

        sets.value = withACandidate("s1").copy(failed = setOf("pose-3"), descriptionRejected = true)
        runCurrent()

        assertEquals(emptyList<PromptEntry>(), entries)
    }

    @Test
    fun theSubjectsRefusedDescription_leavesTheHistory_whenAnEarlierSetOrBuildKeptIt() = runTest {
        history.record("Bleeding on the floor", Role.SUBJECT, "u_1")
        history.record("Wave", Role.PHOTOGRAPHER, null)
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        // As the start carries it, whitespace and all.
        started.emit(StartedDescription("Bleeding  on the floor ", null, setId = "s1"))
        sets.value = generatingSet("s1")
        runCurrent()
        sets.value = generatingSet("s1").copy(failed = setOf("pose-1"), descriptionRejected = true)
        runCurrent()
        sets.value = null
        runCurrent()

        assertEquals(listOf("Wave"), entries.map { it.text })
    }

    @Test
    fun theNamedSetEndingUnanswered_keepsNothing_evenIfAnotherSetIsAcceptedAfter() = runTest {
        val started = MutableSharedFlow<StartedDescription>()
        history.followSubjectSets(started, sets, backgroundScope)
        runCurrent()

        started.emit(StartedDescription("Bleeding", null, setId = "s1"))
        sets.value = generatingSet("s1")
        runCurrent()
        sets.value = null
        sets.value = withACandidate("s2")
        runCurrent()

        assertEquals(emptyList<PromptEntry>(), entries)
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
