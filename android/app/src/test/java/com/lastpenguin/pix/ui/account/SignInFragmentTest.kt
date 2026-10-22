// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.account

import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.Account
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.GoogleSignIn
import com.lastpenguin.pix.databinding.FragmentSignInBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
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
 * Sign in on the real Fragment (R&S 6.4, 6.8; FR-5.2, FR-5.13), with the account fakes behind the ViewModel;
 * `AccountFormsTest` covers the form's fields. Partitions here: a sign-in that succeeds (back to Friends) or whose
 * account has no Pix ID yet (to Pix ID setup); one refused (the message, cleared on the next edit); Continue with
 * Google for a registered account, and for one no Pix account uses (the question: Yes keeps the screen in its
 * linking state and the next sign-in adds Google, with the failure of that step as a message; No makes an account);
 * Sign up; Back, which forgets the waiting Google account.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInFragmentTest {
    private var scenario: FragmentScenario<SignInFragment>? = null
    private lateinit var nav: TestNavHostController
    private lateinit var accountViewModel: AccountViewModel

    private val account = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Sign in with Friends and Camera below it, as the app opens it. */
    private fun launch(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        scenario = launchFragmentInContainer<SignInFragment>(themeResId = R.style.Theme_Pix)
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        scenario!!.onFragment { fragment ->
            nav.setGraph(R.navigation.nav_graph)
            nav.navigate(R.id.friendsFragment)
            nav.navigate(R.id.signInFragment)
            Navigation.setViewNavController(fragment.requireView(), nav)
            accountViewModel = fragment.requireActivity().viewModel()
        }
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentSignInBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentSignInBinding.bind(it.requireView())) }
        return result!!
    }

    private fun signIn(pixId: String = "test1", password: String = "secret12") = onScreen { binding ->
        binding.pixIdInput.setText(pixId)
        binding.passwordInput.setText(password)
        binding.signInButton.performClick()
    }

    @Test
    fun aSignIn_goesBackToFriends() {
        val modules = launch()

        signIn()
        idle()

        assertEquals(listOf("logIn:test1"), modules.auth.calls)
        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun anAccountWithoutAPixId_goesToTheSetup() {
        val modules = TestAppContainer()
        modules.auth.account = account.copy(pixId = null, hasGoogle = true)
        launch(modules)

        signIn(pixId = "anything")
        idle()

        assertEquals(R.id.pixIdSetupFragment, nav.currentDestination?.id)
    }

    @Test
    fun aRefusedSignIn_showsTheMessage_untilTheNextEdit() {
        val modules = TestAppContainer()
        modules.auth.failure = AccountException(AccountError.INVALID_CREDENTIALS)
        launch(modules)

        signIn()
        idle()
        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.errorText.visibility)
            assertEquals(string(R.string.wrong_credentials), binding.errorText.text.toString())
            binding.passwordInput.setText("secret13")
        }
        idle()

        onScreen { assertEquals(View.GONE, it.errorText.visibility) }
        assertEquals(R.id.signInFragment, nav.currentDestination?.id)
    }

    @Test
    fun googleForARegisteredAccount_signsIn() {
        launch()

        onScreen { it.googleButton.performClick() }
        idle()

        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun googleForANewAccount_asks_andYesAddsItToTheNextSignIn() {
        val modules = TestAppContainer()
        modules.auth.googleResult = GoogleSignIn.NotRegistered
        launch(modules)

        onScreen { it.googleButton.performClick() }
        idle()
        val question = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(R.id.signInFragment, nav.currentDestination?.id)

        question.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        onScreen { binding ->
            assertEquals(string(R.string.link_google_body), binding.bodyText.text.toString())
            assertEquals(View.GONE, binding.googleButton.visibility)
            assertEquals(View.GONE, binding.signUpButton.visibility)
        }

        signIn()
        idle()
        assertTrue(modules.auth.calls.contains("addGoogle:google-token"))
        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun googleNotAddedAfterTheSignIn_isAMessage_andTheSignInStands() {
        val modules = TestAppContainer()
        modules.auth.googleResult = GoogleSignIn.NotRegistered
        modules.auth.failures["addGoogle"] = AccountException(AccountError.GOOGLE_IN_USE)
        launch(modules)
        onScreen { it.googleButton.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        signIn()
        idle()

        assertEquals(
            string(R.string.google_not_added, string(R.string.google_in_use)),
            ShadowToast.getTextOfLatestToast(),
        )
        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun googleForANewAccount_noMakesOne_andGoesToTheSetup() {
        val modules = TestAppContainer()
        modules.auth.googleResult = GoogleSignIn.NotRegistered
        launch(modules)
        onScreen { it.googleButton.performClick() }
        idle()

        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()

        assertTrue(modules.auth.calls.contains("googleSignUp:google-token"))
        assertEquals(R.id.pixIdSetupFragment, nav.currentDestination?.id)
    }

    @Test
    fun signUp_opensSignUp() {
        launch()

        onScreen { it.signUpButton.performClick() }
        idle()

        assertEquals(R.id.signUpFragment, nav.currentDestination?.id)
    }

    @Test
    fun back_forgetsTheWaitingGoogleAccount_andLeaves() {
        val modules = TestAppContainer()
        modules.auth.googleResult = GoogleSignIn.NotRegistered
        launch(modules)
        onScreen { it.googleButton.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue(accountViewModel.uiState.value.linkingGoogle)

        onScreen { it.backButton.performClick() }
        idle()

        assertFalse(accountViewModel.uiState.value.linkingGoogle)
        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }
}
