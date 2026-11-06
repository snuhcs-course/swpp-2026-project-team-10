// AI-generated with Claude Code, 2026-10-27, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.Relation
import com.lastpenguin.pix.account.UserLookup
import com.lastpenguin.pix.databinding.FragmentAddFriendBinding
import com.lastpenguin.pix.databinding.FragmentPixIdSetupBinding
import com.lastpenguin.pix.databinding.FragmentSignInBinding
import com.lastpenguin.pix.databinding.FragmentSignUpBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * Partitions for the account forms on their real layouts (US-6, US-7; FR-5.2, FR-5.3, FR-5.5, FR-5.8, FR-5.11,
 * FR-5.15). Sign up: empty (rules faint, button off), "Test User!" (the Pix ID rule in red, US-6), a password without a
 * digit, the password equal to the Pix ID, all valid (button on), PIX_ID_TAKEN from the server (at the field; editing
 * clears it; it wins over a Check that said free), busy. Check on Sign up: an empty or broken Pix ID (off), a valid one
 * (asks for it), checking (off, "Checking…"), free (in green), taken (in red), failed (the reason in red), an answer for
 * another Pix ID than the field's (the rule instead). Pix ID setup: invalid and valid input, TAKEN with the input kept,
 * Check. Sign in: both fields needed, a refused
 * password, the Google account waiting to be added (only the Pix ID and password are offered). Add a friend: typed
 * capitals and symbols (kept out), each relation's button or label, No user found (Search again, field in red), an
 * edit after a search (the result is dropped).
 */
@RunWith(AndroidJUnit4::class)
class AccountFormsTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    private val inflater = LayoutInflater.from(activity)
    private val calls = mutableListOf<String>()
    private val red = ContextCompat.getColor(activity, R.color.pix_danger_text)
    private val faint = ContextCompat.getColor(activity, R.color.pix_text_faint)
    private val green = ContextCompat.getColor(activity, R.color.pix_live)

    private val View.shown get() = visibility == View.VISIBLE

    private fun signUp(): Pair<FragmentSignUpBinding, SignUpForm> {
        val binding = FragmentSignUpBinding.inflate(inflater)
        activity.setContentView(binding.root)
        val form = SignUpForm(
            binding,
            onCheck = { calls += "check:$it" },
            onSignUp = { id, name, _ -> calls += "signUp:$id:$name" },
            onEdited = { calls += "edited" },
        )
        return binding to form
    }

    @Test
    fun signUp_empty_showsTheRulesFaintAndWaits() {
        val (binding, _) = signUp()

        assertEquals(faint, binding.pixIdRule.currentTextColor)
        assertEquals("4–20 characters: a–z, 0–9, \".\", \"_\"", binding.pixIdRule.text.toString())
        assertFalse(binding.signUpButton.isEnabled)
    }

    @Test
    fun signUp_aPixIdWithASpaceAndASymbol_showsTheRuleInRed() {
        val (binding, _) = signUp()

        binding.pixIdInput.setText("Test User!")

        assertEquals(red, binding.pixIdRule.currentTextColor)
        assertFalse(binding.signUpButton.isEnabled)
    }

    @Test
    fun signUp_passwordWithoutADigit_orEqualToThePixId_waits() {
        val (binding, _) = signUp()
        binding.pixIdInput.setText("testuser1")
        binding.nameInput.setText("Test Account 1")

        binding.passwordInput.setText("abcdefgh")
        assertEquals(red, binding.passwordRule.currentTextColor)
        assertFalse(binding.signUpButton.isEnabled)

        binding.passwordInput.setText("TestUser1")
        assertEquals("The password can't be the same as the Pix ID", binding.passwordRule.text.toString())
        assertFalse(binding.signUpButton.isEnabled)
    }

    @Test
    fun signUp_allValid_sendsTheFields() {
        val (binding, _) = signUp()
        binding.pixIdInput.setText("test1")
        binding.nameInput.setText("Test Account 1")
        binding.passwordInput.setText("sunset2026")

        assertTrue(binding.signUpButton.isEnabled)
        binding.signUpButton.performClick()

        assertEquals(listOf("signUp:test1:Test Account 1"), calls)
    }

    @Test
    fun signUp_takenPixId_isShownAtTheFieldUntilEdited() {
        val (binding, form) = signUp()
        binding.pixIdInput.setText("test1")

        form.render(AccountUiState(error = AccountException(AccountError.PIX_ID_TAKEN, "This Pix ID is taken")))

        assertEquals("This Pix ID is taken", binding.pixIdRule.text.toString())
        assertEquals(red, binding.pixIdRule.currentTextColor)
        assertFalse(binding.errorText.shown)
        assertEquals("test1", binding.pixIdInput.text.toString())

        binding.pixIdInput.setText("test11")
        assertEquals(listOf("edited"), calls)
    }

    @Test
    fun signUp_check_waitsForAValidPixIdAndAsksForIt() {
        val (binding, _) = signUp()
        assertFalse(binding.checkButton.isEnabled)

        binding.pixIdInput.setText("Test User!")
        assertFalse(binding.checkButton.isEnabled)

        binding.pixIdInput.setText("test5")
        assertTrue(binding.checkButton.isEnabled)
        binding.checkButton.performClick()
        assertEquals(listOf("check:test5"), calls)
    }

    @Test
    fun signUp_check_showsCheckingThenTheAnswerForThatPixIdOnly() {
        val (binding, form) = signUp()
        binding.pixIdInput.setText("test5")

        form.render(AccountUiState(pixIdCheck = PixIdCheck("test5")))
        assertEquals("Checking…", binding.pixIdRule.text.toString())
        assertFalse(binding.checkButton.isEnabled)

        form.render(AccountUiState(pixIdCheck = PixIdCheck("test5", available = true)))
        assertEquals("This Pix ID is available", binding.pixIdRule.text.toString())
        assertEquals(green, binding.pixIdRule.currentTextColor)
        assertTrue(binding.checkButton.isEnabled)

        binding.pixIdInput.setText("test6")
        assertEquals("4–20 characters: a–z, 0–9, \".\", \"_\"", binding.pixIdRule.text.toString())
        assertEquals(faint, binding.pixIdRule.currentTextColor)

        form.render(AccountUiState(pixIdCheck = PixIdCheck("test6", available = false)))
        assertEquals("This Pix ID is taken", binding.pixIdRule.text.toString())
        assertEquals(red, binding.pixIdRule.currentTextColor)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun signUp_check_failed_saysWhyAtTheField() {
        val (binding, form) = signUp()
        binding.pixIdInput.setText("test5")

        form.render(AccountUiState(pixIdCheck = PixIdCheck("test5", error = AccountException(AccountError.OFFLINE))))

        assertEquals("Couldn't reach Pix. Check your connection and try again.", binding.pixIdRule.text.toString())
        assertEquals(red, binding.pixIdRule.currentTextColor)
        assertTrue(binding.checkButton.isEnabled)
    }

    @Test
    fun signUp_takenAtSignUp_winsOverACheckThatSaidFree() {
        val (binding, form) = signUp()
        binding.pixIdInput.setText("test5")

        form.render(
            AccountUiState(
                error = AccountException(AccountError.PIX_ID_TAKEN),
                pixIdCheck = PixIdCheck("test5", available = true),
            ),
        )

        assertEquals("This Pix ID is taken", binding.pixIdRule.text.toString())
        assertEquals(red, binding.pixIdRule.currentTextColor)
    }

    @Test
    fun signUp_busy_waits() {
        val (binding, form) = signUp()
        binding.pixIdInput.setText("test1")
        binding.nameInput.setText("Test Account 1")
        binding.passwordInput.setText("sunset2026")

        form.render(AccountUiState(busy = true))

        assertFalse(binding.signUpButton.isEnabled)
    }

    @Test
    fun pixIdSetup_savesOnlyAValidPixId_andKeepsATakenOne() {
        val binding = FragmentPixIdSetupBinding.inflate(inflater)
        activity.setContentView(binding.root)
        val form = PixIdForm(binding, { calls += "check:$it" }, { calls += "save:$it" }, { calls += "edited" })

        binding.pixIdInput.setText("Test User!")
        assertFalse(binding.saveButton.isEnabled)
        assertFalse(binding.checkButton.isEnabled)

        binding.pixIdInput.setText("test2")
        binding.saveButton.performClick()
        form.render(AccountUiState(error = AccountException(AccountError.PIX_ID_TAKEN)))

        assertEquals(listOf("save:test2"), calls)
        assertEquals("This Pix ID is taken", binding.pixIdRule.text.toString())
        assertEquals("test2", binding.pixIdInput.text.toString())
    }

    @Test
    fun pixIdSetup_check_asksAndShowsTheAnswer() {
        val binding = FragmentPixIdSetupBinding.inflate(inflater)
        activity.setContentView(binding.root)
        val form = PixIdForm(binding, { calls += "check:$it" }, { calls += "save:$it" }, { calls += "edited" })
        binding.pixIdInput.setText("test5")

        binding.checkButton.performClick()
        form.render(AccountUiState(pixIdCheck = PixIdCheck("test5", available = true)))

        assertEquals(listOf("check:test5"), calls)
        assertEquals("This Pix ID is available", binding.pixIdRule.text.toString())
        assertEquals(green, binding.pixIdRule.currentTextColor)
    }

    @Test
    fun signIn_needsBothFields_andShowsARefusal() {
        val binding = FragmentSignInBinding.inflate(inflater)
        activity.setContentView(binding.root)
        val form =
            SignInForm(binding, { id, _ -> calls += "logIn:$id" }, { calls += "google" }, { calls += "signUp" }, {})

        binding.pixIdInput.setText("test1")
        assertFalse(binding.signInButton.isEnabled)
        binding.passwordInput.setText("wrong1234")
        binding.signInButton.performClick()
        form.render(AccountUiState(error = AccountException(AccountError.INVALID_CREDENTIALS)))

        assertEquals(listOf("logIn:test1"), calls)
        assertEquals("The Pix ID or the password is wrong.", binding.errorText.text.toString())
    }

    @Test
    fun signIn_whileGoogleWaitsToBeAdded_offersOnlyThePixIdAndPassword() {
        val binding = FragmentSignInBinding.inflate(inflater)
        activity.setContentView(binding.root)
        val form = SignInForm(binding, { _, _ -> }, {}, {}, {})

        form.render(AccountUiState(linkingGoogle = true))

        assertFalse(binding.googleButton.shown || binding.signUpButton.shown)
        assertEquals(activity.getString(R.string.link_google_body), binding.bodyText.text.toString())
    }

    private fun addFriend(): Pair<FragmentAddFriendBinding, AddFriendForm> {
        val binding = FragmentAddFriendBinding.inflate(inflater)
        activity.setContentView(binding.root)
        val form = AddFriendForm(
            binding,
            onSearch = { calls += "search:$it" },
            onAdd = { calls += "add" },
            onAccept = { calls += "accept" },
            onWithdraw = { calls += "withdraw" },
            onEdited = { calls += "edited" },
        )
        return binding to form
    }

    private val testAccount3 = PixUser("u_3", "test3", "Test Account 3")

    @Test
    fun addFriend_field_keepsOnlyWhatAPixIdHolds() {
        val (binding, _) = addFriend()

        binding.pixIdInput.setText("Test3!")

        assertEquals("test3", binding.pixIdInput.text.toString())
        binding.searchButton.performClick()
        assertEquals(listOf("search:test3"), calls)
    }

    @Test
    fun addFriend_eachRelation_showsItsButtonOrLabel() {
        val (binding, form) = addFriend()
        binding.pixIdInput.setText("test3")
        val shown = Relation.entries.associateWith { relation ->
            form.render(
                SearchUiState(SearchPhase.FOUND, "test3", UserLookup.Found(testAccount3, relation, "fr_1")),
            )
            val visible = if (binding.resultButton.shown) binding.resultButton else binding.relationText
            visible.text.toString()
        }

        assertEquals(
            mapOf(
                Relation.NONE to "Add",
                Relation.SELF to "This is you",
                Relation.FRIEND to "Friends",
                Relation.REQUESTED to "Requested",
                Relation.INCOMING to "Accept",
            ),
            shown,
        )
        assertTrue(binding.withdrawButton.shown.not())
    }

    @Test
    fun addFriend_resultButtons_addAcceptAndWithdraw() {
        val (binding, form) = addFriend()
        binding.pixIdInput.setText("test3")
        for (relation in listOf(Relation.NONE, Relation.INCOMING, Relation.REQUESTED)) {
            form.render(SearchUiState(SearchPhase.FOUND, "test3", UserLookup.Found(testAccount3, relation, "fr_1")))
            if (relation ==
                Relation.REQUESTED
            ) {
                binding.withdrawButton.performClick()
            } else {
                binding.resultButton.performClick()
            }
        }

        assertEquals(listOf("add", "accept", "withdraw"), calls)
    }

    @Test
    fun addFriend_notFound_offersSearchAgainWithTheTextKept() {
        val (binding, form) = addFriend()
        binding.pixIdInput.setText("test99")

        form.render(SearchUiState(SearchPhase.NOT_FOUND, "test99"))

        assertTrue(binding.notFoundSection.shown)
        assertEquals("Search again", binding.searchButton.text.toString())
        binding.searchButton.performClick()
        assertEquals(listOf("edited"), calls)
        assertEquals("test99", binding.pixIdInput.text.toString())
    }

    @Test
    fun addFriend_editAfterASearch_dropsTheResult() {
        val (binding, form) = addFriend()
        binding.pixIdInput.setText("test3")
        form.render(SearchUiState(SearchPhase.FOUND, "test3", UserLookup.Found(testAccount3, Relation.NONE, null)))

        binding.pixIdInput.setText("testAccount3")

        assertEquals(listOf("edited"), calls)
    }
}
