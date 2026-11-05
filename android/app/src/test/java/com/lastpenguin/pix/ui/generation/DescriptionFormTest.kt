// AI-generated with Claude Code, 2026-11-05, reviewed by Jaewan Park, Dongje Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.ui.generation

import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import com.lastpenguin.pix.generation.PromptEntry
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for Generate poses' form (FR-4.8, FR-4.11, US-5), on the real layout. Field: empty, typed text, a kept
 * text, 100 characters (the longest) and 101 (cut to 100), Korean. The descriptions used before: none (the section
 * hidden); one chip each in their order, without the switch outside a session; a chip's tap on a field with text; its
 * ✕; in a session, the switch between all and the friend's, nothing used with the friend yet, and the friend gone; the
 * same history again (the chips kept as they are); a friend's name of 30 characters (cut short, both halves of the
 * switch on the screen). The last description rejected or not. The one button (Iter3-A13): its label for an empty
 * field, spaces only, a typed text, a kept text, a chip, and the other phone's text; it sends the field as typed. The
 * ✕: shown with text, hidden with none and while the field waits; it empties the field as typing would.
 * In a session (FR-6.15): the other phone's text shown, but not over this field's typing; the field, the chips, and
 * the buttons waiting while the other person types or the link is paused, and the buttons while an answer is awaited;
 * the hint; Done and the buttons ending the typing; outside a session, nothing waits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DescriptionFormTest {
    private val changes = mutableListOf<String>()
    private val generated = mutableListOf<String>()
    private val focus = mutableListOf<Boolean>()
    private val forgotten = mutableListOf<String>()
    private lateinit var shared: DescriptionForm

    private fun form(text: String? = "", rejected: Boolean = false): FragmentDescribePoseBinding {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val binding = FragmentDescribePoseBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        DescriptionForm(
            binding,
            text = text,
            rejected = rejected,
            onTextChanged = { changes += it },
            onGenerate = { generated += it },
            onFocusChanged = { focus += it },
            onForget = { forgotten += it },
        ).also { shared = it }
        return binding
    }

    /** A description used before, with the friends it was used with. */
    private fun entry(text: String, vararg partners: String) =
        PromptEntry(text, 1_000, Role.PHOTOGRAPHER, partners.toSet())

    private fun FragmentDescribePoseBinding.chips(): List<Chip> =
        (0 until historyChips.childCount).map { historyChips.getChildAt(it) as Chip }

    private fun FragmentDescribePoseBinding.chipTexts() = chips().map { it.text.toString() }

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

        DescriptionForm(binding, text = null, rejected = false, {}, {}, {})

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
    fun noDescriptionUsedBefore_hidesTheSection() {
        val binding = form()

        shared.showHistory(emptyList(), partner = null)

        assertEquals(View.GONE, binding.historySection.visibility)
    }

    @Test
    fun descriptionsUsedBefore_areOneChipEachInTheirOrder_withoutTheSwitchOutsideASession() {
        val binding = form()

        shared.showHistory(listOf(entry("Wave"), entry("Jump", "u_2")), partner = null)

        assertEquals(View.VISIBLE, binding.historySection.visibility)
        assertEquals(View.GONE, binding.historyFilter.visibility)
        assertEquals(View.GONE, binding.historyEmpty.visibility)
        assertEquals(listOf("Wave", "Jump"), binding.chipTexts())
        assertTrue(binding.chips().all { it.isCloseIconVisible })
    }

    @Test
    fun chip_replacesTheFieldsTextAndLeavesTheCursorAtTheEnd() {
        val binding = form(text = "Something else")
        shared.showHistory(listOf(entry("Wave"), entry("Jump")), partner = null)
        val chip = binding.chips()[1]

        chip.performClick()

        assertEquals("Jump", binding.descriptionInput.text.toString())
        assertEquals("Jump".length, binding.descriptionInput.selectionStart)
        assertEquals("Jump", changes.last())
    }

    @Test
    fun aChipsClose_asksToForgetItsDescription() {
        val binding = form()
        shared.showHistory(listOf(entry("Wave"), entry("Jump")), partner = null)

        binding.chips()[1].performCloseIconClick()

        assertEquals(listOf("Jump"), forgotten)
        assertEquals("", binding.descriptionInput.text.toString())
    }

    @Test
    fun theSameHistoryAgain_keepsTheChipsAsTheyAre() {
        val binding = form()
        val entries = listOf(entry("Wave"), entry("Jump"))
        shared.showHistory(entries, partner = null)
        val chips = binding.chips()

        shared.showHistory(entries.toList(), partner = null)

        assertEquals(chips, binding.chips())
    }

    @Test
    fun aLongFriendsName_isCutShort_soBothHalvesOfTheSwitchStayOnTheScreen() {
        val binding = form()
        shared.showHistory(listOf(entry("Wave")), SessionPartner("u_2", "가".repeat(30)))
        val width = 360
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2_000, View.MeasureSpec.AT_MOST),
        )
        binding.root.layout(0, 0, width, binding.root.measuredHeight)

        val density = binding.root.resources.displayMetrics.density
        assertTrue(binding.historyWithPartner.width <= 160 * density)
        assertTrue(binding.historyAll.width > 0)
        assertTrue(binding.historyFilter.right <= (binding.historyFilter.parent as View).width)
    }

    @Test
    fun inASession_theSwitchShowsAllOrTheOnesUsedWithTheFriend_andSaysWhenThereAreNone() {
        val binding = form()
        val friend = SessionPartner("u_2", "Junhyeong")

        shared.showHistory(listOf(entry("Wave", "u_2"), entry("Jump", "u_3")), friend)

        assertEquals(View.VISIBLE, binding.historyFilter.visibility)
        assertEquals("With Junhyeong", binding.historyWithPartner.text.toString())
        assertTrue(binding.historyAll.isSelected)
        assertFalse(binding.historyWithPartner.isSelected)
        assertEquals(listOf("Wave", "Jump"), binding.chipTexts())

        binding.historyWithPartner.performClick()

        assertTrue(binding.historyWithPartner.isSelected)
        assertFalse(binding.historyAll.isSelected)
        assertEquals(listOf("Wave"), binding.chipTexts())
        assertEquals(View.GONE, binding.historyEmpty.visibility)

        // Wave forgotten: nothing was used with Junhyeong.
        shared.showHistory(listOf(entry("Jump", "u_3")), friend)

        assertEquals(emptyList<String>(), binding.chipTexts())
        assertEquals(View.VISIBLE, binding.historyEmpty.visibility)
        assertEquals("Nothing used with Junhyeong yet", binding.historyEmpty.text.toString())

        // The session ended: the whole history again, without the switch.
        shared.showHistory(listOf(entry("Jump", "u_3")), partner = null)

        assertEquals(View.GONE, binding.historyFilter.visibility)
        assertEquals(View.GONE, binding.historyEmpty.visibility)
        assertEquals(listOf("Jump"), binding.chipTexts())
    }

    @Test
    fun rejectedDescription_isToldUnderTheField() {
        val binding = form(text = "피 흘리는 모습", rejected = true)

        assertEquals(View.VISIBLE, binding.descriptionError.visibility)
        assertEquals(
            binding.root.context.getString(R.string.description_refused),
            binding.descriptionError.text.toString(),
        )
        assertEquals("피 흘리는 모습", binding.descriptionInput.text.toString())
    }

    private fun FragmentDescribePoseBinding.label() = generateButton.text.toString()

    private fun FragmentDescribePoseBinding.suggest() = root.context.getString(R.string.suggest_four_poses)

    private fun FragmentDescribePoseBinding.describe() = root.context.getString(R.string.generate_four_from_description)

    @Test
    fun theButton_sendsTheFieldAsTyped_andItsLabelSaysWhatItStarts() {
        val binding = form()
        assertEquals(binding.suggest(), binding.label())
        binding.generateButton.performClick()

        // Spaces alone ask for suggested poses too (PoseRequest.of).
        binding.descriptionInput.setText("   ")
        assertEquals(binding.suggest(), binding.label())
        binding.descriptionInput.setText("  Waving  ")
        assertEquals(binding.describe(), binding.label())
        binding.generateButton.performClick()

        // The ViewModel decides what an empty or padded text means.
        assertEquals(listOf("", "  Waving  "), generated)
    }

    @Test
    fun theLabel_followsAKeptText_theOtherPhonesText_andAChip() {
        val binding = form(text = "jump")
        assertEquals(binding.describe(), binding.label())

        shared.showShared("", typing = "Dongje", sharedWith = "Dongje")
        assertEquals(binding.suggest(), binding.label())
        shared.showShared("wave", typing = null, sharedWith = "Dongje")
        assertEquals(binding.describe(), binding.label())

        binding.descriptionInput.setText("")
        shared.showHistory(listOf(entry("Wave")), partner = null)
        binding.chips().single().performClick()
        assertEquals(binding.describe(), binding.label())
    }

    @Test
    fun theClear_emptiesTheFieldAsTypingWould_andShowsOnlyWithTextItCanTake() {
        val binding = form()
        val clear = binding.clearDescriptionButton
        assertEquals(View.GONE, clear.visibility)

        binding.descriptionInput.setText("wave")
        assertEquals(View.VISIBLE, clear.visibility)
        clear.performClick()

        assertEquals("", binding.descriptionInput.text.toString())
        assertEquals("", changes.last())
        assertEquals(binding.suggest(), binding.label())
        assertEquals(View.GONE, clear.visibility)

        // While the other person types, this field takes nothing.
        shared.showShared("jump", typing = "Dongje", sharedWith = "Dongje")
        assertEquals(View.GONE, clear.visibility)
        shared.showShared("jump", typing = null, sharedWith = "Dongje")
        assertEquals(View.VISIBLE, clear.visibility)
    }

    @Test
    fun inASession_theOtherPhonesTextIsShown_andEverythingWaitsWhileTheyType() {
        val binding = form()
        shared.showHistory(listOf(entry("Wave")), partner = null)

        shared.showShared("jump", typing = "Dongje", sharedWith = "Dongje")

        assertEquals("jump", binding.descriptionInput.text.toString())
        assertEquals("4/$DESCRIPTION_MAX_LENGTH", binding.descriptionCounter.text.toString())
        assertTrue(changes.isEmpty())
        assertFalse(binding.descriptionInput.isEnabled)
        assertFalse(binding.chips().single().isEnabled)
        assertFalse(binding.generateButton.isEnabled)
        assertEquals(View.VISIBLE, binding.descriptionTyping.visibility)
        assertEquals("Dongje is typing…", binding.descriptionTyping.text.toString())
        assertEquals("Dongje sees this too", binding.sharedHint.text.toString())

        shared.showShared("jump high", typing = null, sharedWith = "Dongje")

        assertEquals("jump high", binding.descriptionInput.text.toString())
        assertTrue(binding.descriptionInput.isEnabled)
        assertTrue(binding.chips().single().isEnabled)
        assertTrue(binding.generateButton.isEnabled)
        assertEquals(View.GONE, binding.descriptionTyping.visibility)
    }

    @Test
    fun inASession_theTextBeingTypedHereIsKept_andAPausedLinkOrAnAwaitedAnswerWait() {
        val binding = form()
        binding.descriptionInput.requestFocus()
        binding.descriptionInput.setText("mine")

        shared.showShared("theirs", typing = null, sharedWith = "Dongje", waiting = true)
        assertEquals("mine", binding.descriptionInput.text.toString())
        assertTrue(binding.descriptionInput.isEnabled)
        assertFalse(binding.generateButton.isEnabled)

        shared.showShared("theirs", typing = null, sharedWith = "Dongje", paused = true)
        assertFalse(binding.descriptionInput.hasFocus())
        assertFalse(binding.descriptionInput.isEnabled)
        assertEquals(View.GONE, binding.descriptionTyping.visibility)
    }

    @Test
    fun doneAndTheButtons_endTheTyping() {
        val binding = form()
        val input = binding.descriptionInput

        input.requestFocus()
        input.onEditorAction(EditorInfo.IME_ACTION_DONE)
        input.requestFocus()
        binding.generateButton.performClick()

        assertEquals(listOf(true, false, true, false), focus)
        assertEquals(listOf(""), generated)
    }

    @Test
    fun outsideASession_thereIsNoHint_andNothingWaits() {
        val binding = form(text = "wave")

        shared.showShared("wave", typing = null, sharedWith = null)

        assertEquals(View.GONE, binding.sharedHint.visibility)
        assertEquals(View.GONE, binding.descriptionTyping.visibility)
        assertTrue(binding.descriptionInput.isEnabled)
        assertTrue(binding.generateButton.isEnabled)
    }
}
