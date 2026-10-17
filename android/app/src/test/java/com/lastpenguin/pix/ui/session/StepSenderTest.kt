// AI-generated with Claude Code, 2026-10-16, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Partitions (Design 2.6.5, the step-then-final pattern): the first step at once; steps inside the interval queued
 * with the newest winning; the queued step after the interval; a step after the sender idled, at once again; the
 * final value at once, ending the gesture and dropping a queued step; a cancel that does the same without sending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepSenderTest {

    @Test
    fun `steps go out at once, then at most once per interval with the newest winning`() = runTest {
        val sent = mutableListOf<Pair<Int, Boolean>>()
        val sender = StepSender<Int>(backgroundScope, 50) { value, final -> sent += value to final }

        sender.step(1)
        sender.step(2)
        sender.step(3)
        assertEquals(listOf(1 to false), sent)
        assertTrue(sender.adjusting)

        advanceTimeBy(60)
        assertEquals(listOf(1 to false, 3 to false), sent)

        advanceTimeBy(200)
        assertEquals(2, sent.size)
        sender.step(4)
        assertEquals(4 to false, sent.last())
        assertEquals(3, sent.size)
    }

    @Test
    fun `the final value goes out at once, ends the gesture, and drops a queued step`() = runTest {
        val sent = mutableListOf<Pair<Int, Boolean>>()
        val sender = StepSender<Int>(backgroundScope, 50) { value, final -> sent += value to final }

        sender.step(1)
        sender.step(2)
        sender.finish(5)

        assertEquals(listOf(1 to false, 5 to true), sent)
        assertFalse(sender.adjusting)
        advanceTimeBy(200)
        assertEquals(2, sent.size)
    }

    @Test
    fun `cancel ends the gesture and drops a queued step without sending`() = runTest {
        val sent = mutableListOf<Pair<Int, Boolean>>()
        val sender = StepSender<Int>(backgroundScope, 50) { value, final -> sent += value to final }

        sender.step(1)
        sender.step(2)
        sender.cancel()

        assertFalse(sender.adjusting)
        advanceTimeBy(200)
        assertEquals(listOf(1 to false), sent)
    }
}
