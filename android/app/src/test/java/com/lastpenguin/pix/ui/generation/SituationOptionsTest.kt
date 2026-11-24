// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.PixApplication
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import com.lastpenguin.pix.generation.PoseOptions
import com.lastpenguin.pix.generation.Situation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/*
 * Partitions for the options each situation offers on Generate poses (FR-4.15), in English and in Korean: every
 * situation has five or six; none is blank or repeats another of its situation, in any letter case; none has a comma,
 * which would cut it in two in the description (PoseOptions); each one alone, and a situation's first three together,
 * fit the longest description, so a field with nothing typed takes them. Landmark's five, without the one that changed
 * the background when it was tried.
 */
@RunWith(AndroidJUnit4::class)
class SituationOptionsTest {

    private fun options(situation: Situation): List<String> {
        val context = ApplicationProvider.getApplicationContext<PixApplication>()
        return situation.poseOptions.map(context::getString)
    }

    private fun check() {
        for (situation in Situation.entries) {
            val options = options(situation)
            assertTrue("$situation: $options", options.size in 5..6)
            assertTrue("$situation: $options", options.none { it.isBlank() || ',' in it || it != it.trim() })
            assertEquals("$situation: $options", options.size, options.map { it.lowercase() }.toSet().size)
            // Chosen one after another, the first three are all in the description.
            val three = options.take(3).fold("") { text, option -> checkNotNull(PoseOptions.toggle(text, option)) }
            assertTrue("$situation: $three", three.length <= DESCRIPTION_MAX_LENGTH)
            assertTrue("$situation: $three", options.take(3).all { PoseOptions.has(three, it) })
        }
    }

    @Test
    fun everySituationsOptions_areFiveOrSixDistinctWordsWithoutAComma_inEnglish() = check()

    @Test
    fun landmarksOptions_areTheFiveThatKeptTheScene() {
        // "Facing the landmark" went (#274): tried once, it turned the view around and changed the background.
        assertEquals(
            listOf("Pointing at the landmark", "Arms wide", "Landmark on the palm", "Peace sign", "Both arms up"),
            options(Situation.LANDMARK),
        )
    }

    @Test
    @Config(qualifiers = "ko")
    fun everySituationsOptions_areFiveOrSixDistinctWordsWithoutAComma_inKorean() {
        // Korean indeed: the first option is not the English one.
        assertEquals("브이", options(Situation.DEFAULT).first())
        check()
    }
}
