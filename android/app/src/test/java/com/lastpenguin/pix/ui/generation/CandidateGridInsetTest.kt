// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * candidateGridInset, the 2×2 grid of Generating poses (Testing Documentation 2.1.6). Partitions: slots half the grid
 * wide that fit two rows (3:4 and 1:1 with room to spare, none needed); the height where 3:4 slots just fit and just
 * under it; a 9:16 scene, whose slots must be narrower; a grid too low for any slot; a grid not yet measured.
 */
class CandidateGridInsetTest {

    @Test
    fun slotsHalfTheGridWide_thatFitTwoRows_needNoInset() {
        assertEquals(0, candidateGridInset(width = 332, height = 1000, aspect = 0.75f, margin = 6))
        assertEquals(0, candidateGridInset(width = 332, height = 400, aspect = 1f, margin = 6))
    }

    @Test
    fun threeByFour_atTheHeightWhereTwoRowsJustFit_needsNoInset_andJustUnderIt_one() {
        // Slots half of 332 px wide are 154 px within their 6 px margins; two rows of them at 3:4 need
        // 2 × (154 / 0.75 + 12) = 434.7 px.
        assertEquals(0, candidateGridInset(width = 332, height = 436, aspect = 0.75f, margin = 6))
        assertEquals(1, candidateGridInset(width = 332, height = 434, aspect = 0.75f, margin = 6))
    }

    @Test
    fun nineBySixteen_narrowsBothColumnsUntilTwoRowsFit() {
        val inset = candidateGridInset(width = 996, height = 1317, aspect = 0.5625f, margin = 18)

        assertEquals(113, inset)
        // Each slot is then 996 / 2 - 113 - 36 = 349 px wide and 349 / 0.5625 = 620.4 px tall: two rows of
        // 620 + 36 px fit the 1317 px.
        val slot = 996 / 2 - inset - 2 * 18
        assertEquals(349, slot)
        assertTrue(2 * (slot / 0.5625f + 2 * 18) <= 1317)
    }

    @Test
    fun gridTooLowForAnySlot_leavesNoWidthToTheSlots() {
        // Half of 100 px less the margins is 38 px, all of it inset.
        assertEquals(38, candidateGridInset(width = 100, height = 10, aspect = 0.75f, margin = 6))
    }

    @Test
    fun gridNotYetMeasured_needsNoInset() {
        assertEquals(0, candidateGridInset(width = 0, height = 0, aspect = 0.5625f, margin = 18))
    }
}
