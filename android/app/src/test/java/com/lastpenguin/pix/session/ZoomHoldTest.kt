// AI-generated with Claude Code, 2026-11-20, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for who holds the zoom (FR-7.6, #236), state-based on the holder: free, the photographer, the subject.
 * - take: free → taken; its own hold → kept; the other phone's hold → refused, the hold unchanged.
 * - release: by the holder → free; by the other phone → the hold stays; while free → still free.
 * - mayChange: free → true; its own hold → true; the other phone's hold → false.
 */
class ZoomHoldTest {
    private val hold = ZoomHold()

    @Test
    fun take_free_takesTheZoom() {
        assertTrue(hold.take(Role.SUBJECT))

        assertEquals(Role.SUBJECT, hold.holder.value)
    }

    @Test
    fun take_ownHold_keepsTheZoom() {
        hold.take(Role.PHOTOGRAPHER)

        assertTrue(hold.take(Role.PHOTOGRAPHER))
        assertEquals(Role.PHOTOGRAPHER, hold.holder.value)
    }

    @Test
    fun take_otherPhoneHolds_isRefused() {
        hold.take(Role.PHOTOGRAPHER)

        assertFalse(hold.take(Role.SUBJECT))
        assertEquals(Role.PHOTOGRAPHER, hold.holder.value)
    }

    @Test
    fun release_byHolder_freesTheZoom() {
        hold.take(Role.SUBJECT)

        hold.release(Role.SUBJECT)

        assertNull(hold.holder.value)
    }

    @Test
    fun release_byOtherPhone_keepsTheHold() {
        hold.take(Role.SUBJECT)

        hold.release(Role.PHOTOGRAPHER)

        assertEquals(Role.SUBJECT, hold.holder.value)
    }

    @Test
    fun release_free_staysFree() {
        hold.release(Role.PHOTOGRAPHER)

        assertNull(hold.holder.value)
    }

    @Test
    fun mayChange_freeOrOwnHold_isTrue() {
        assertTrue(hold.mayChange(Role.SUBJECT))

        hold.take(Role.SUBJECT)

        assertTrue(hold.mayChange(Role.SUBJECT))
    }

    @Test
    fun mayChange_otherPhoneHolds_isFalse() {
        hold.take(Role.SUBJECT)

        assertFalse(hold.mayChange(Role.PHOTOGRAPHER))
    }
}
