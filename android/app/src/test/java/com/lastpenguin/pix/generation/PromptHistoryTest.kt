// AI-generated with Claude Code, 2026-11-02, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * The descriptions used before (FR-4.11; Testing Documentation 2.1.6), kept in memory. Partitions: a first
 * description, with its role and time; the same text again, also with other whitespace (one entry, moved to the front,
 * the friends joined, the role and time the latest); a blank text (nothing); a friend kept, and none without one;
 * MAX_ENTRIES kept and the one used longest ago dropped at one more; ✕ on a kept text and on an unknown one; the
 * subject's sets followed with the session's friend at that moment, and a blank one not.
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
    fun theSubjectsSets_areKeptAsTheyStart_withTheSessionsFriendAtThatMoment() = runTest {
        val started = MutableSharedFlow<String>()
        var partner: SessionPartner? = SessionPartner("u_1", "Dongje")
        history.followSubjectSets(started, { partner }, backgroundScope)
        runCurrent()

        started.emit("Wave")
        now = 2_000
        partner = null
        started.emit("Jump")
        started.emit("   ")
        runCurrent()

        assertEquals(
            listOf(
                PromptEntry("Jump", 2_000, Role.SUBJECT),
                PromptEntry("Wave", 1_000, Role.SUBJECT, setOf("u_1")),
            ),
            entries,
        )
    }
}
