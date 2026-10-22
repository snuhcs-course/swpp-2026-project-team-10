// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.account

import android.content.DialogInterface
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.databinding.SheetAccountBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/*
 * Account on the real bottom sheet (R&S 6.8; FR-5.12, FR-5.14), with the account fakes behind the ViewModel.
 * Partitions: an account with a password only (Add Google offered), with Google only and no Pix ID (neither; a
 * password needs a Pix ID first), with Google and a Pix ID (Add a password), with both (neither); Edit name with a
 * name that breaks the rule (Save waits) and one that holds (saved, a message); Add a password equal to the Pix ID
 * (refused) and a valid one; Add Google; a refused change (the message on the sheet); Sign out, which closes it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountSheetTest {
    private var scenario: FragmentScenario<AccountSheet>? = null
    private lateinit var sheet: AccountSheet

    private val password = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(account: Account = password, modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.auth.signInAs(account)
        modules.install()
        scenario = launchFragment<AccountSheet>(themeResId = R.style.Theme_Pix)
        scenario!!.onFragment { sheet = it }
        idle()
        return modules
    }

    private fun <T> onSheet(block: (SheetAccountBinding) -> T): T = block(SheetAccountBinding.bind(sheet.requireView()))

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun dialogInput(dialog: AlertDialog): EditText = dialog.findViewById(R.id.input)!!

    @Test
    fun aPasswordAccount_offersGoogle() {
        launch()

        onSheet { binding ->
            assertEquals("Test Account 1", binding.nameText.text.toString())
            assertEquals(string(R.string.at_pix_id, "test1"), binding.pixIdText.text.toString())
            assertEquals(string(R.string.signs_in_with_password), binding.methodsText.text.toString())
            assertEquals(View.VISIBLE, binding.addGoogleOption.visibility)
            assertEquals(View.GONE, binding.addPasswordOption.visibility)
        }
    }

    @Test
    fun aGoogleAccount_offersAPasswordOnlyWithAPixId() {
        launch(password.copy(pixId = null, hasPassword = false, hasGoogle = true))
        onSheet { binding ->
            assertEquals(View.GONE, binding.pixIdText.visibility)
            assertEquals(string(R.string.signs_in_with_google), binding.methodsText.text.toString())
            assertEquals(View.GONE, binding.addGoogleOption.visibility)
            assertEquals(View.GONE, binding.addPasswordOption.visibility)
        }
        scenario!!.close()

        launch(password.copy(hasPassword = false, hasGoogle = true))
        onSheet { assertEquals(View.VISIBLE, it.addPasswordOption.visibility) }
        scenario!!.close()

        launch(password.copy(hasGoogle = true))
        onSheet { binding ->
            assertEquals(string(R.string.signs_in_with_both), binding.methodsText.text.toString())
            assertEquals(View.GONE, binding.addGoogleOption.visibility)
            assertEquals(View.GONE, binding.addPasswordOption.visibility)
        }
    }

    @Test
    fun editName_waitsForAValidName_thenSavesIt() {
        val modules = launch()

        onSheet { it.editNameButton.performClick() }
        idle()
        val dialog = latestDialog()
        val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertEquals("Test Account 1", dialogInput(dialog).text.toString())
        assertTrue(save.isEnabled)

        dialogInput(dialog).setText("x".repeat(31))
        idle()
        assertFalse(save.isEnabled)
        assertEquals(string(R.string.name_rule), dialog.findViewById<TextView>(R.id.ruleText)!!.text.toString())

        dialogInput(dialog).setText("New Name")
        idle()
        save.performClick()
        idle()

        assertTrue(modules.auth.calls.contains("setDisplayName:New Name"))
        onSheet { assertEquals("New Name", it.nameText.text.toString()) }
        assertEquals(string(R.string.name_saved), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun addPassword_refusesThePixId_thenSavesAValidOne() {
        // A Pix ID long enough to be a password, so only the "same as the Pix ID" rule can refuse it.
        val modules = launch(password.copy(pixId = "testuser1", hasPassword = false, hasGoogle = true))

        onSheet { it.addPasswordOption.performClick() }
        idle()
        val dialog = latestDialog()
        val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        assertFalse(save.isEnabled)

        dialogInput(dialog).setText("testuser1")
        idle()
        assertFalse(save.isEnabled)
        assertEquals(
            string(R.string.password_same_as_pix_id),
            dialog.findViewById<TextView>(R.id.ruleText)!!.text.toString(),
        )

        dialogInput(dialog).setText("secret12")
        idle()
        assertTrue(save.isEnabled)
        save.performClick()
        idle()

        assertTrue(modules.auth.calls.contains("addPassword"))
        assertEquals(string(R.string.password_added), ShadowToast.getTextOfLatestToast())
        onSheet { assertEquals(View.GONE, it.addPasswordOption.visibility) }
    }

    @Test
    fun addGoogle_addsIt() {
        val modules = launch()

        onSheet { it.addGoogleOption.performClick() }
        idle()

        assertTrue(modules.auth.calls.contains("addGoogle:google-token"))
        assertEquals(string(R.string.google_added), ShadowToast.getTextOfLatestToast())
        onSheet { assertEquals(View.GONE, it.addGoogleOption.visibility) }
    }

    @Test
    fun aRefusedChange_showsTheMessageOnTheSheet() {
        val modules = TestAppContainer()
        modules.auth.failures["addGoogle"] = AccountException(AccountError.GOOGLE_IN_USE)
        launch(modules = modules)

        onSheet { it.addGoogleOption.performClick() }
        idle()

        onSheet { binding ->
            assertEquals(View.VISIBLE, binding.errorText.visibility)
            assertEquals(string(R.string.google_in_use), binding.errorText.text.toString())
            assertTrue(binding.signOutButton.isEnabled)
        }
    }

    @Test
    fun signOut_closesTheSheet() {
        val modules = launch()

        onSheet { it.signOutButton.performClick() }
        idle()

        assertTrue(modules.auth.calls.contains("signOut"))
        assertEquals(string(R.string.signed_out), ShadowToast.getTextOfLatestToast())
        assertFalse(sheet.isAdded)
    }
}
