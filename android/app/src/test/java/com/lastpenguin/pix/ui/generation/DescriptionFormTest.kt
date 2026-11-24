// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park, Joonhyung Han, and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.generation.DESCRIPTION_MAX_LENGTH
import com.lastpenguin.pix.generation.PromptEntry
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.ui.OwnActivities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Partitions for Generate poses' form (FR-4.8, FR-4.11, US-5), on the real layout. Field: empty, typed text, a kept
 * text, 100 characters (the longest) and 101 (cut to 100), Korean. The descriptions used before: none (the section
 * hidden); one chip each in their order, without the switch outside a session; a chip's tap on a field with text; its
 * ✕; in a session, the switch between all and the friend's, nothing used with the friend yet, and the friend gone; the
 * same history again (the chips kept as they are); a friend's name of 30 characters (cut short, both halves of the
 * switch on the screen). The last description rejected or not. The one button: *Generate* for an empty field, spaces
 * only, and a text (FR-4.15); it sends the field as typed.
 * Auto (FR-4.15): an empty field left alone says Auto, with the line that says to tap it and nothing under it; tapped,
 * it shows the example, the counter, the options, and the history, which stay once it is left empty again; a kept text
 * opens them without a tap; a refused empty field shows the refusal, not the line; the field is as high saying Auto as
 * with an option. *Back to Auto*: shown with text, hidden with none and while the field waits; it empties the field as
 * typing would and folds what is under it; it stands under the field where the line to tap it stood, a touch target
 * high, with nothing in the field but its text, and a chosen option moves nothing under the field; a refusal shows
 * over it, and over the line to tap an empty field. What only the other phone's text opened folds when the text goes, and
 * stays if this field was tapped.
 * The options (FR-4.15): each situation's own, in their order, and Auto's without a situation; another situation, and
 * the same one again (the chips kept as they are); an option into an empty field, a second one after a comma, a
 * checked one tapped again, the last one taken out; checked by typed text and by the other phone's, not by a longer
 * word; one that would pass 100 characters; waiting while the other person types. A description used before that is
 * an option on the screen is not listed twice, alone or among others.
 * In a session (FR-6.15): the other phone's text shown, but not over this field's typing; the field, the chips, and
 * the buttons waiting while the other person types or the link is paused, and the buttons while an answer is awaited;
 * the hint; Done and the buttons ending the typing; outside a session, nothing waits.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DescriptionFormTest {
    @get:Rule
    val activities = OwnActivities()

    private val changes = mutableListOf<String>()
    private val generated = mutableListOf<String>()
    private val focus = mutableListOf<Boolean>()
    private val forgotten = mutableListOf<String>()
    private lateinit var shared: DescriptionForm

    private fun form(text: String? = "", rejected: Boolean = false): FragmentDescribePoseBinding {
        val activity = activities.start()
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

    private fun FragmentDescribePoseBinding.options(): List<Chip> =
        (0 until optionChips.childCount).map { optionChips.getChildAt(it) as Chip }

    private fun FragmentDescribePoseBinding.optionTexts() = options().map { it.text.toString() }

    private fun FragmentDescribePoseBinding.string(@StringRes id: Int) = root.context.getString(id)

    private fun FragmentDescribePoseBinding.option(@StringRes label: Int): Chip =
        options().single { it.text.toString() == string(label) }

    /** A tap on the field, which opens what is under it. */
    private fun FragmentDescribePoseBinding.tapField() {
        descriptionInput.requestFocus()
    }

    /** Where [view] is in the form, in px. */
    private fun FragmentDescribePoseBinding.placeOf(view: View): Rect =
        Rect(0, 0, view.width, view.height).also { root.offsetDescendantRectToMyCoords(view, it) }

    /** Lays the form out at the Galaxy S22's width. */
    private fun FragmentDescribePoseBinding.layOut() {
        val width = (360 * root.resources.displayMetrics.density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, width, root.measuredHeight)
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
        val activity = activities.start()
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
        binding.tapField()

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
        binding.tapField()
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
        binding.tapField()
        val friend = SessionPartner("u_2", "Joonhyung")

        shared.showHistory(listOf(entry("Wave", "u_2"), entry("Jump", "u_3")), friend)

        assertEquals(View.VISIBLE, binding.historyFilter.visibility)
        assertEquals("With Joonhyung", binding.historyWithPartner.text.toString())
        assertTrue(binding.historyAll.isSelected)
        assertFalse(binding.historyWithPartner.isSelected)
        assertEquals(listOf("Wave", "Jump"), binding.chipTexts())

        binding.historyWithPartner.performClick()

        assertTrue(binding.historyWithPartner.isSelected)
        assertFalse(binding.historyAll.isSelected)
        assertEquals(listOf("Wave"), binding.chipTexts())
        assertEquals(View.GONE, binding.historyEmpty.visibility)

        // Wave forgotten: nothing was used with Joonhyung.
        shared.showHistory(listOf(entry("Jump", "u_3")), friend)

        assertEquals(emptyList<String>(), binding.chipTexts())
        assertEquals(View.VISIBLE, binding.historyEmpty.visibility)
        assertEquals("Nothing used with Joonhyung yet", binding.historyEmpty.text.toString())

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

    @Test
    fun theButton_readsGenerateWhateverTheFieldHolds_andSendsTheFieldAsTyped() {
        val binding = form()
        val generate = binding.string(R.string.generate_from_description)
        assertEquals(generate, binding.generateButton.text.toString())
        binding.generateButton.performClick()

        binding.descriptionInput.setText("   ")
        assertEquals(generate, binding.generateButton.text.toString())
        binding.descriptionInput.setText("  Waving  ")
        assertEquals(generate, binding.generateButton.text.toString())
        binding.generateButton.performClick()

        // The ViewModel decides what an empty or padded text means.
        assertEquals(listOf("", "  Waving  "), generated)
    }

    @Test
    fun anEmptyFieldLeftAlone_saysAuto_withTheLineToTapIt_andNothingUnderIt() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        shared.showHistory(listOf(entry("Wave")), partner = null)

        val auto = binding.string(R.string.describe_auto) + "\n" + binding.string(R.string.describe_auto_body)
        assertEquals(auto, binding.descriptionInput.hint.toString())
        // Its outline is in the accent's tint, as the chosen value's.
        assertTrue(binding.descriptionInput.isActivated)
        assertEquals(View.VISIBLE, binding.descriptionHelper.visibility)
        assertEquals(binding.string(R.string.describe_auto_helper), binding.descriptionHelper.text.toString())
        assertEquals(View.GONE, binding.descriptionCounter.visibility)
        assertEquals(View.GONE, binding.optionsSection.visibility)
        assertEquals(View.GONE, binding.historySection.visibility)
    }

    @Test
    fun aTappedField_showsTheExample_theCounter_theOptions_andTheHistory() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        shared.showHistory(listOf(entry("Wave")), partner = null)

        binding.tapField()

        assertEquals(binding.string(R.string.describe_hint), binding.descriptionInput.hint.toString())
        assertFalse(binding.descriptionInput.isActivated)
        assertEquals(View.GONE, binding.descriptionHelper.visibility)
        assertEquals(View.VISIBLE, binding.descriptionCounter.visibility)
        assertEquals(View.VISIBLE, binding.optionsSection.visibility)
        assertEquals(View.VISIBLE, binding.historySection.visibility)
    }

    @Test
    fun onceTapped_theOptionsAndTheHistoryStay_whenTheEmptyFieldIsLeft() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        shared.showHistory(listOf(entry("Wave")), partner = null)
        binding.tapField()

        shared.stopTyping()

        // The field says Auto again, and what a closed keyboard uncovers is still there to choose.
        assertEquals(binding.string(R.string.describe_auto), binding.descriptionInput.hint.toString().lines().first())
        assertEquals(View.VISIBLE, binding.descriptionHelper.visibility)
        assertEquals(View.VISIBLE, binding.optionsSection.visibility)
        assertEquals(View.VISIBLE, binding.historySection.visibility)
    }

    @Test
    fun aKeptText_opensTheOptionsAndTheHistory_withoutATap() {
        val binding = form(text = "jump")
        shared.showOptions(Situation.FULL_BODY)
        shared.showHistory(listOf(entry("Wave")), partner = null)

        assertFalse(binding.descriptionInput.isActivated)
        assertEquals(View.GONE, binding.descriptionHelper.visibility)
        assertEquals(View.VISIBLE, binding.descriptionCounter.visibility)
        assertEquals(View.VISIBLE, binding.optionsSection.visibility)
        assertEquals(View.VISIBLE, binding.historySection.visibility)
    }

    @Test
    fun aRefusedEmptyField_showsTheRefusal_overTheLineToTapIt() {
        val binding = form(text = "", rejected = true)
        binding.layOut()

        assertEquals(View.VISIBLE, binding.descriptionError.visibility)
        assertEquals(View.VISIBLE, binding.descriptionHelper.visibility)
        assertTrue(binding.placeOf(binding.descriptionError).bottom <= binding.placeOf(binding.descriptionHelper).top)
    }

    @Test
    fun aRefusedText_showsTheRefusal_overBackToAuto() {
        val binding = form(text = "피 흘리는 모습", rejected = true)
        binding.layOut()

        assertEquals(View.VISIBLE, binding.descriptionError.visibility)
        assertEquals(View.VISIBLE, binding.clearDescriptionButton.visibility)
        val refusal = binding.placeOf(binding.descriptionError)
        assertTrue(refusal.bottom <= binding.placeOf(binding.clearDescriptionButton).top)
        assertTrue(refusal.top >= binding.placeOf(binding.descriptionInput).bottom)
    }

    @Test
    fun backToAuto_standsUnderTheField_whereTheLineToTapItStood_andTheFieldKeepsItsWidthForText() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        binding.layOut()
        val line = binding.placeOf(binding.descriptionHelper)

        binding.descriptionInput.setText("wave")
        binding.layOut()

        // One line under the field says how to get to the other way: to typing, or back to Auto.
        val back = binding.placeOf(binding.clearDescriptionButton)
        assertEquals(View.GONE, binding.descriptionHelper.visibility)
        assertEquals(line.left, back.left)
        assertTrue("$back under the field", back.top >= binding.placeOf(binding.descriptionInput).bottom)
        assertEquals(line.centerY().toFloat(), back.centerY().toFloat(), 1f)
        // A touch target high (NFR-16), with the counter still at the row's end.
        val density = binding.root.resources.displayMetrics.density
        assertTrue(back.height() >= (48 * density).toInt())
        assertEquals(binding.placeOf(binding.descriptionInput).right, binding.placeOf(binding.descriptionCounter).right)
        // Nothing stands in the field but its text.
        assertEquals(binding.descriptionInput.paddingStart, binding.descriptionInput.paddingEnd)
    }

    @Test
    fun aChosenOption_andBackToAutoInItsLine_moveNothingUnderTheField() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        binding.tapField()
        binding.layOut()
        val options = binding.placeOf(binding.optionsSection).top

        binding.option(R.string.pose_option_peace_sign).performClick()
        binding.layOut()

        assertEquals(View.VISIBLE, binding.clearDescriptionButton.visibility)
        assertEquals(options, binding.placeOf(binding.optionsSection).top)
    }

    @Test
    fun theField_isAsHighSayingAutoAsWithAnOption() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        binding.layOut()
        val saysAuto = binding.descriptionInput.height

        binding.option(R.string.pose_option_peace_sign).performClick()
        binding.layOut()

        assertTrue(saysAuto > 0)
        assertEquals(saysAuto, binding.descriptionInput.height)
    }

    @Test
    fun backToAuto_emptiesTheFieldAsTypingWould_foldsWhatIsUnderIt_andShowsOnlyWithTextItCanTake() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val back = binding.clearDescriptionButton
        assertEquals(View.GONE, back.visibility)

        binding.tapField()
        binding.descriptionInput.setText("wave")
        assertEquals(View.VISIBLE, back.visibility)
        assertEquals(binding.string(R.string.back_to_auto), back.text.toString())
        back.performClick()

        assertEquals("", binding.descriptionInput.text.toString())
        assertEquals("", changes.last())
        assertFalse(binding.descriptionInput.hasFocus())
        assertEquals(View.GONE, back.visibility)
        assertEquals(View.VISIBLE, binding.descriptionHelper.visibility)
        assertEquals(View.GONE, binding.optionsSection.visibility)

        // While the other person types, this field takes nothing.
        shared.showShared("jump", typing = "Dongje", sharedWith = "Dongje")
        assertEquals(View.GONE, back.visibility)
        shared.showShared("jump", typing = null, sharedWith = "Dongje")
        assertEquals(View.VISIBLE, back.visibility)
    }

    @Test
    fun options_areTheSituationsOwn_inTheirOrder() {
        val binding = form()

        shared.showOptions(Situation.FULL_BODY)

        val labels = listOf(
            R.string.pose_option_peace_sign,
            R.string.pose_option_hand_in_pocket,
            R.string.pose_option_looking_back,
            R.string.pose_option_walking,
            R.string.pose_option_arms_up,
            R.string.pose_option_thumbs_up,
        )
        assertEquals(labels.map { binding.string(it) }, binding.optionTexts())
        assertTrue(binding.options().none { it.isCloseIconVisible })
    }

    @Test
    fun withoutASituation_theOptionsAreAutos() {
        val binding = form()

        shared.showOptions(null)
        val none = binding.optionTexts()
        shared.showOptions(Situation.DEFAULT)

        assertEquals(none, binding.optionTexts())
        assertEquals(binding.string(R.string.pose_option_peace_sign), none.first())
        assertEquals(binding.string(R.string.pose_option_arms_up), none.last())
    }

    @Test
    fun anotherSituation_replacesTheOptions_andTheSameOneAgainKeepsTheChips() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val chips = binding.options()

        shared.showOptions(Situation.FULL_BODY)
        assertEquals(chips, binding.options())

        shared.showOptions(Situation.SELFIE)
        assertEquals(binding.string(R.string.pose_option_wink), binding.optionTexts()[2])
    }

    @Test
    fun anOption_goesIntoTheEmptyField_andIsChecked() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val peace = binding.option(R.string.pose_option_peace_sign)
        assertFalse(peace.isChecked)

        peace.performClick()

        val text = binding.string(R.string.pose_option_peace_sign)
        assertEquals(text, binding.descriptionInput.text.toString())
        assertEquals(text.length, binding.descriptionInput.selectionStart)
        assertEquals(text, changes.last())
        assertTrue(peace.isChecked)
        assertFalse(binding.option(R.string.pose_option_walking).isChecked)
    }

    @Test
    fun aSecondOption_followsAfterAComma_andACheckedOneTappedAgainLeaves() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val peace = binding.option(R.string.pose_option_peace_sign)
        val pocket = binding.option(R.string.pose_option_hand_in_pocket)

        peace.performClick()
        pocket.performClick()
        assertEquals("Peace sign, Hand in pocket", binding.descriptionInput.text.toString())
        assertTrue(peace.isChecked && pocket.isChecked)

        peace.performClick()
        assertEquals("Hand in pocket", binding.descriptionInput.text.toString())
        assertFalse(peace.isChecked)
        assertTrue(pocket.isChecked)
        assertEquals("Hand in pocket", changes.last())
    }

    @Test
    fun theLastOptionTakenOut_leavesAuto_withTheOptionsStillThere() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val peace = binding.option(R.string.pose_option_peace_sign)
        peace.performClick()

        peace.performClick()

        assertEquals("", binding.descriptionInput.text.toString())
        assertEquals(View.VISIBLE, binding.descriptionHelper.visibility)
        // The chip just tapped does not go from under the finger.
        assertEquals(View.VISIBLE, binding.optionsSection.visibility)
        assertFalse(peace.isChecked)
    }

    @Test
    fun anOption_isCheckedByTypedText_andNotByALongerWord() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val peace = binding.option(R.string.pose_option_peace_sign)

        binding.descriptionInput.setText("smiling, peace sign")
        assertTrue(peace.isChecked)

        binding.descriptionInput.setText("Peace signs with both hands")
        assertFalse(peace.isChecked)
    }

    @Test
    fun anOption_thatWouldPassTheLongestDescription_cannotBeChosen() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val walking = binding.option(R.string.pose_option_walking)
        val room = DESCRIPTION_MAX_LENGTH - ", Walking".length

        binding.descriptionInput.setText("a".repeat(room))
        assertTrue(walking.isEnabled)

        binding.descriptionInput.setText("a".repeat(room + 1))
        assertFalse(walking.isEnabled)
        walking.performClick()
        assertEquals("a".repeat(room + 1), binding.descriptionInput.text.toString())
        assertFalse(walking.isChecked)

        // One already in the description can always be taken out.
        binding.descriptionInput.setText("Walking, " + "a".repeat(DESCRIPTION_MAX_LENGTH - "Walking, ".length))
        assertTrue(walking.isEnabled)
    }

    @Test
    fun inASession_theOtherPhonesOptionIsChecked_andTheOptionsWaitWhileTheyType() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        val peace = binding.option(R.string.pose_option_peace_sign)

        shared.showShared("Peace sign", typing = "Dongje", sharedWith = "Dongje")

        assertEquals(View.VISIBLE, binding.optionsSection.visibility)
        assertTrue(peace.isChecked)
        assertTrue(binding.options().none { it.isEnabled })

        shared.showShared("Peace sign", typing = null, sharedWith = "Dongje")

        assertTrue(binding.options().all { it.isEnabled })
    }

    @Test
    fun whatOnlyTheOtherPhonesTextOpened_foldsWhenTheTextGoes_andStaysIfThisFieldWasTapped() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)

        shared.showShared("Peace sign", typing = null, sharedWith = "Dongje")
        assertEquals(View.VISIBLE, binding.optionsSection.visibility)

        // They went back to Auto: this phone, which touched nothing, says Auto with nothing under it too.
        shared.showShared("", typing = null, sharedWith = "Dongje")
        assertEquals(View.GONE, binding.optionsSection.visibility)
        assertEquals(View.VISIBLE, binding.descriptionHelper.visibility)

        // Tapped here: what the tap opened is this phone's own.
        binding.tapField()
        shared.stopTyping()
        shared.showShared("Peace sign", typing = null, sharedWith = "Dongje")
        shared.showShared("", typing = null, sharedWith = "Dongje")
        assertEquals(View.VISIBLE, binding.optionsSection.visibility)
    }

    @Test
    fun aDescriptionUsedBefore_thatIsAnOptionOnTheScreen_isNotListedTwice() {
        val binding = form()
        shared.showOptions(Situation.FULL_BODY)
        binding.tapField()

        shared.showHistory(listOf(entry("peace sign"), entry("Wave"), entry("Peace sign, Wave")), partner = null)
        assertEquals(listOf("Wave", "Peace sign, Wave"), binding.chipTexts())

        // Nothing else was used: no section for it.
        shared.showHistory(listOf(entry("Peace sign")), partner = null)
        assertEquals(View.GONE, binding.historySection.visibility)

        // Selfie has no walking pose, so a description of it is listed there.
        shared.showHistory(listOf(entry("Walking")), partner = null)
        assertEquals(View.GONE, binding.historySection.visibility)
        shared.showOptions(Situation.SELFIE)
        assertEquals(listOf("Walking"), binding.chipTexts())
        assertEquals(View.VISIBLE, binding.historySection.visibility)
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
