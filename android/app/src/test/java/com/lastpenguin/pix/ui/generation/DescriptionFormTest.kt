// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for Generate poses' form (FR-4.8, US-5), on the real layout. Field: empty, typed text, a kept text,
 * 100 characters (the longest) and 101 (cut to 100), Korean. Chips: one per idea, a tap on an empty field and on a
 * field with text. The last description rejected or not. Buttons: Generate with text, with an empty field, and Skip.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DescriptionFormTest {
    private val changes = mutableListOf<String>()
    private val generated = mutableListOf<String>()
    private var suggested = 0

    private fun form(text: String? = "", rejected: Boolean = false): FragmentDescribePoseBinding {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = FragmentDescribePoseBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        DescriptionForm(
            binding,
            ideas = activity.resources.getStringArray(R.array.pose_ideas).toList(),
            text = text,
            rejected = rejected,
            onTextChanged = { changes += it },
            onGenerate = { generated += it },
            onSuggest = { suggested++ },
        )
        return binding
    }

    @Test
    fun emptyField_showsTheCounterAtZeroAndNoError() {
        val binding = form()

        assertEquals("", binding.descriptionInput.text.toString())
        assertEquals("0/$DESCRIPTION_MAX_LENGTH", binding.descriptionCounter.text.toString())
        assertEquals(View.GONE, binding.descriptionError.visibility)
    }

    @Test
    fun typing_updatesTheCounterAndTellsTheViewModel() {
        val binding = form()

        binding.descriptionInput.setText("벽에 기대서 웃기")

        assertEquals("9/$DESCRIPTION_MAX_LENGTH", binding.descriptionCounter.text.toString())
        assertEquals("벽에 기대서 웃기", changes.last())
    }

    @Test
    fun keptText_isShownWithItsCount() {
        val binding = form(text = "Jumping")

        assertEquals("Jumping", binding.descriptionInput.text.toString())
        assertEquals("7/$DESCRIPTION_MAX_LENGTH", binding.descriptionCounter.text.toString())
    }

    @Test
    fun noText_keepsWhatTheFieldRestoredItself() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = FragmentDescribePoseBinding.inflate(LayoutInflater.from(activity))
        binding.descriptionInput.setText("Restored")

        DescriptionForm(binding, emptyList(), text = null, rejected = false, {}, {}, {})

        assertEquals("Restored", binding.descriptionInput.text.toString())
        assertEquals("8/$DESCRIPTION_MAX_LENGTH", binding.descriptionCounter.text.toString())
    }

    @Test
    fun input_stopsAtTheLongestDescription() {
        val binding = form()

        binding.descriptionInput.setText("가".repeat(DESCRIPTION_MAX_LENGTH))
        assertEquals(DESCRIPTION_MAX_LENGTH, binding.descriptionInput.length())

        binding.descriptionInput.setText("가".repeat(DESCRIPTION_MAX_LENGTH + 1))
        assertEquals(DESCRIPTION_MAX_LENGTH, binding.descriptionInput.length())
        assertEquals("100/100", binding.descriptionCounter.text.toString())
    }

    @Test
    fun ideas_areOneChipEachAndFitTheLimit() {
        val binding = form()
        val ideas = binding.root.resources.getStringArray(R.array.pose_ideas).toList()

        val chips = (0 until binding.ideaChips.childCount).map { binding.ideaChips.getChildAt(it) as Chip }

        assertEquals(ideas, chips.map { it.text.toString() })
        assertTrue(ideas.size >= 4)
        assertTrue(ideas.all { it.length <= DESCRIPTION_MAX_LENGTH })
    }

    @Test
    fun chip_replacesTheFieldsTextAndLeavesTheCursorAtTheEnd() {
        val binding = form(text = "Something else")
        val chip = binding.ideaChips.getChildAt(1) as Chip

        chip.performClick()

        assertEquals(chip.text.toString(), binding.descriptionInput.text.toString())
        assertEquals(chip.text.length, binding.descriptionInput.selectionStart)
        assertEquals(chip.text.toString(), changes.last())
    }

    @Test
    fun rejectedDescription_isToldUnderTheField() {
        val binding = form(text = "피 흘리는 모습", rejected = true)

        assertEquals(View.VISIBLE, binding.descriptionError.visibility)
        assertEquals(
            binding.root.context.getString(R.string.try_different_description),
            binding.descriptionError.text.toString(),
        )
        assertEquals("피 흘리는 모습", binding.descriptionInput.text.toString())
    }

    @Test
    fun generate_sendsTheFieldAsTyped_andSkipAsksForSuggestions() {
        val binding = form()

        binding.generateButton.performClick()
        binding.descriptionInput.setText("  Waving  ")
        binding.generateButton.performClick()
        binding.suggestButton.performClick()

        // The ViewModel decides what an empty or padded text means.
        assertEquals(listOf("", "  Waving  "), generated)
        assertEquals(1, suggested)
    }
}
