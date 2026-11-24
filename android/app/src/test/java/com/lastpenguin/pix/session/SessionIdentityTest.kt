// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park
package com.lastpenguin.pix.session

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * The name `hello` sends (#271), on the plain JVM.
 * Partitions: signed in (the account's name, trimmed), signed out (null), an account name of spaces only (the phone's),
 * and an account name over 40 characters (its first 40). Boundary, counted by code point: 30 emoji (60 UTF-16 units)
 * kept whole, and an emoji across the 40th character kept whole, never cut in two.
 */
class SessionIdentityTest {
    private val phone = SessionIdentity("Galaxy S23 Ultra", "0.1.0")

    @Test
    fun signedIn_sendsTheAccountsName_trimmed() {
        assertEquals("Test Account 1", phone.nameFor("  Test Account 1 "))
    }

    @Test
    fun signedOut_sendsThePhonesName() {
        assertEquals("Galaxy S23 Ultra", phone.nameFor(null))
    }

    @Test
    fun anAccountNameOfSpacesOnly_sendsThePhonesName() {
        assertEquals("Galaxy S23 Ultra", phone.nameFor("   "))
    }

    @Test
    fun anAccountNameOver40Characters_sendsItsFirst40() {
        assertEquals("a".repeat(40), phone.nameFor("a".repeat(41)))
    }

    @Test
    fun aNameOfEmoji_isCountedByCharacter_notByUtf16Unit() {
        val emoji = "\uD83D\uDE00"
        assertEquals(emoji.repeat(30), phone.nameFor(emoji.repeat(30)))
    }

    @Test
    fun anEmojiAtThe40thCharacter_isKeptWhole_neverCutInTwo() {
        val emoji = "\uD83D\uDE00"
        assertEquals("a".repeat(39) + emoji, phone.nameFor("a".repeat(39) + emoji + "b"))
    }
}
