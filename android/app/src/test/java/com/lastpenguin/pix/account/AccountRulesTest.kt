// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions for the rules the forms check as the user types (FR-5.2, FR-5.3, FR-5.11; the server's rules.py).
 * Pix ID: 3 characters (too short), 4 and 20 (the edges), 21 (too long), a capital, a space, another symbol, "." and
 * "_". Password: 7, 8, 64, 65 characters; letters only, digits only; the Pix ID itself in another case; an emoji
 * counted as one character. Display name: empty, spaces only, 30 and 31 characters after trimming. A Pix ID field
 * keeps a–z, 0–9, "." and "_" of pasted text, in lowercase.
 */
class AccountRulesTest {

    @Test
    fun pixId_atTheEdges_isValid() {
        assertNull(pixIdProblem("abcd"))
        assertNull(pixIdProblem("a".repeat(20)))
        assertNull(pixIdProblem("test.user_2"))
    }

    @Test
    fun pixId_justOutsideTheEdges_namesTheLength() {
        assertEquals(PixIdProblem.TOO_SHORT, pixIdProblem("abc"))
        assertEquals(PixIdProblem.TOO_SHORT, pixIdProblem(""))
        assertEquals(PixIdProblem.TOO_LONG, pixIdProblem("a".repeat(21)))
    }

    @Test
    fun pixId_withACapitalASpaceOrASymbol_breaksTheCharacterRule() {
        assertEquals(PixIdProblem.CHARACTERS, pixIdProblem("Test1"))
        assertEquals(PixIdProblem.CHARACTERS, pixIdProblem("test user"))
        assertEquals(PixIdProblem.CHARACTERS, pixIdProblem("test1!"))
    }

    @Test
    fun password_atTheEdges_isValid() {
        assertNull(passwordProblem("abcdefg1", "test1"))
        assertNull(passwordProblem("a1" + "x".repeat(62), "test1"))
    }

    @Test
    fun password_justOutsideTheEdges_namesTheLength() {
        assertEquals(PasswordProblem.TOO_SHORT, passwordProblem("abcdef1", "test1"))
        assertEquals(PasswordProblem.TOO_LONG, passwordProblem("a1" + "x".repeat(63), "test1"))
    }

    @Test
    fun password_withoutALetterOrADigit_needsBoth() {
        assertEquals(PasswordProblem.LETTER_AND_DIGIT, passwordProblem("abcdefgh", "test1"))
        assertEquals(PasswordProblem.LETTER_AND_DIGIT, passwordProblem("12345678", "test1"))
    }

    @Test
    fun password_thatIsThePixIdInAnyCase_isRefused() {
        assertEquals(PasswordProblem.SAME_AS_PIX_ID, passwordProblem("TESTUSER1", "testuser1"))
    }

    @Test
    fun password_lengthCountsAnEmojiAsOneCharacter() {
        // Seven characters, one of them two UTF-16 units: still too short, as the server counts it.
        assertEquals(PasswordProblem.TOO_SHORT, passwordProblem("abcde1\uD83D\uDE00", "test1"))
    }

    @Test
    fun displayName_isCountedAfterTrimming() {
        assertFalse(isValidDisplayName(""))
        assertFalse(isValidDisplayName("   "))
        assertTrue(isValidDisplayName("  " + "a".repeat(30) + "  "))
        assertFalse(isValidDisplayName("a".repeat(31)))
    }

    @Test
    fun normalizePixId_dropsCapitalsAndSpacesAround() {
        assertEquals("test2", normalizePixId("  Test2 "))
    }

    @Test
    fun keepPixIdCharacters_keepsWhatAPixIdCanHoldInLowercase() {
        assertEquals("testuser.p_1", keepPixIdCharacters("Test User.P_1!"))
        assertEquals("", keepPixIdCharacters("한글 @#"))
    }
}
