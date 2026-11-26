// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for an option of Generate poses in the description's text (FR-4.15). Whether the description has it: an
 * empty description; the option alone; among others; with spaces around it; in other letter case; inside a longer
 * word; as part of a phrase; before a comma with nothing after. Adding it: to an empty description, to spaces only, to
 * a text, to a text that ends in a comma; a result of exactly the longest description (kept) and one character more
 * (refused). Removing it: the only part; the first, a middle, and the last of several; written twice; the other
 * parts keep their order and what was typed in them.
 */
class PoseOptionsTest {

    @Test
    fun has_anEmptyDescription_isFalse() {
        assertFalse(PoseOptions.has("", "Wave"))
        assertFalse(PoseOptions.has("   ", "Wave"))
    }

    @Test
    fun has_theOptionAlone_amongOthers_orWithSpacesAround_isTrue() {
        assertTrue(PoseOptions.has("Wave", "Wave"))
        assertTrue(PoseOptions.has("Jump, Wave, smiling", "Wave"))
        assertTrue(PoseOptions.has("Jump ,  Wave  ", "Wave"))
        assertTrue(PoseOptions.has("Wave,", "Wave"))
    }

    @Test
    fun has_anotherLetterCase_isTrue() {
        assertTrue(PoseOptions.has("peace SIGN", "Peace sign"))
    }

    @Test
    fun has_theOptionInsideALongerWordOrAPhrase_isFalse() {
        // "브이로그" is not the pose "브이", and "브이 하고 웃기" is the user's own sentence.
        assertFalse(PoseOptions.has("브이로그 찍는 느낌", "브이"))
        assertFalse(PoseOptions.has("브이 하고 웃기", "브이"))
    }

    @Test
    fun toggle_onAnEmptyDescription_isTheOptionAlone() {
        assertEquals("브이", PoseOptions.toggle("", "브이"))
        assertEquals("브이", PoseOptions.toggle("   ", "브이"))
    }

    @Test
    fun toggle_onAText_addsTheOptionAfterAComma() {
        assertEquals("살짝 웃으면서, 브이", PoseOptions.toggle("살짝 웃으면서", "브이"))
        assertEquals("브이, 주머니에 손, 만세", PoseOptions.toggle("브이, 주머니에 손", "만세"))
    }

    @Test
    fun toggle_onATextThatEndsInAComma_addsNoSecondComma() {
        assertEquals("Jump, Wave", PoseOptions.toggle("Jump, ", "Wave"))
        assertEquals("Jump, Wave", PoseOptions.toggle("Jump ,", "Wave"))
    }

    @Test
    fun toggle_toExactlyTheLongestDescription_isKept_andOneMore_isRefused() {
        val option = "Wave"
        val fits = "a".repeat(DESCRIPTION_MAX_LENGTH - ", $option".length)

        assertEquals(DESCRIPTION_MAX_LENGTH, PoseOptions.toggle(fits, option)!!.length)
        assertNull(PoseOptions.toggle(fits + "a", option))
    }

    @Test
    fun toggle_onTheOnlyPart_emptiesTheDescription() {
        assertEquals("", PoseOptions.toggle("브이", "브이"))
        assertEquals("", PoseOptions.toggle(" 브이 ,", "브이"))
    }

    @Test
    fun toggle_onTheFirstAMiddleAndTheLastPart_leavesTheOthersInOrder() {
        val text = "Wave, hair in the wind, Jump"

        assertEquals("hair in the wind, Jump", PoseOptions.toggle(text, "Wave"))
        assertEquals("Wave, Jump", PoseOptions.toggle(text, "hair in the wind"))
        assertEquals("Wave, hair in the wind", PoseOptions.toggle(text, "Jump"))
    }

    @Test
    fun toggle_onAnOptionWrittenTwice_removesBoth() {
        assertEquals("Jump", PoseOptions.toggle("Wave, Jump, wave", "Wave"))
    }

    @Test
    fun toggle_removingAnOption_isNeverRefusedForLength() {
        val full = "Wave, " + "a".repeat(DESCRIPTION_MAX_LENGTH - "Wave, ".length)

        assertEquals("a".repeat(DESCRIPTION_MAX_LENGTH - "Wave, ".length), PoseOptions.toggle(full, "Wave"))
    }
}
