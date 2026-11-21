// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.GoogleSignIn
import com.lastpenguin.pix.databinding.FragmentSignUpAgreementBinding
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

/*
 * The sign-up agreement on the real Fragment (R&S 6.4; FR-5.13, FR-5.18), opened from Sign in as the app opens it, over
 * Share my camera and Sign in. Partitions: Sign up's first page (the notice names the password; Agree opens the form
 * over it and calls nothing; an error from the form does not show here); Sign up with Google (the notice names the
 * Google account; Agree makes the account from the waiting Google account and opens Pix ID setup in the place of Sign
 * in and this screen; a refused sign-up shows the reason and stays; Back goes to Sign in and forgets the Google account;
 * a new process with no waiting Google account goes straight back); the privacy policy's link, from either.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignUpAgreementFragmentTest {
    private var scenario: FragmentScenario<SignUpAgreementFragment>? = null
    private lateinit var nav: TestNavHostController
    private lateinit var accountViewModel: AccountViewModel

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Share my camera, Sign in, and this page: Sign up's, or Sign up with Google's with the account waiting. */
    private fun launch(
        google: Boolean,
        modules: TestAppContainer = TestAppContainer(),
        waiting: Boolean = google,
    ): TestAppContainer {
        modules.auth.googleResult = GoogleSignIn.NotRegistered
        modules.install()
        // Created without its view, so the shared ViewModel can hold the Google account before the screen reads it.
        scenario = launchFragmentInContainer<SignUpAgreementFragment>(
            fragmentArgs = bundleOf(SignUpAgreementFragment.ARG_GOOGLE to google),
            themeResId = R.style.Theme_Pix,
            initialState = Lifecycle.State.CREATED,
        )
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        scenario!!.onFragment { fragment ->
            val activity: FragmentActivity = fragment.requireActivity()
            nav.setGraph(R.navigation.nav_graph)
            nav.navigate(R.id.friendsFragment)
            nav.navigate(R.id.signInFragment)
            nav.navigate(if (google) R.id.action_signIn_to_googleSignUp else R.id.action_signIn_to_signUp)
            Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
            accountViewModel = activity.viewModel()
            if (waiting) accountViewModel.continueWithGoogle(activity)
        }
        idle()
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentSignUpAgreementBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentSignUpAgreementBinding.bind(it.requireView())) }
        return result!!
    }

    @Test
    fun signUp_namesThePassword_andAgreeOpensTheFormOverIt() {
        val modules = launch(google = false)
        assertEquals(string(R.string.sign_up_title), onScreen { it.titleText.text.toString() })
        assertEquals(string(R.string.sign_up_notice_what_password), onScreen { it.whatText.text.toString() })

        onScreen { it.agreeButton.performClick() }
        idle()

        assertEquals(R.id.signUpFragment, nav.currentDestination?.id)
        assertEquals(R.id.signUpAgreementFragment, nav.previousBackStackEntry?.destination?.id)
        assertEquals(emptyList<String>(), modules.auth.calls)
    }

    @Test
    fun signUp_anErrorFromTheForm_doesNotShowHere() {
        val modules = TestAppContainer()
        modules.auth.failures["signUp"] = AccountException(AccountError.OFFLINE)
        launch(google = false, modules = modules)

        accountViewModel.signUp("test1", "Test Account 1", "sunset2026")
        idle()

        assertFalse(onScreen { it.errorText.visibility == View.VISIBLE })
        assertTrue(onScreen { it.agreeButton.isEnabled })
    }

    @Test
    fun google_agree_makesTheAccountAndGoesToTheSetup() {
        val modules = launch(google = true)
        assertEquals(string(R.string.google_sign_up_title), onScreen { it.titleText.text.toString() })
        assertEquals(string(R.string.sign_up_notice_what_google), onScreen { it.whatText.text.toString() })
        assertEquals(listOf("google:google-token"), modules.auth.calls)

        onScreen { it.agreeButton.performClick() }
        idle()

        assertEquals("googleSignUp:google-token", modules.auth.calls.last())
        assertEquals(R.id.pixIdSetupFragment, nav.currentDestination?.id)
        assertEquals(R.id.friendsFragment, nav.previousBackStackEntry?.destination?.id)
    }

    @Test
    fun google_aRefusedSignUp_showsTheReason_andStays() {
        val modules = TestAppContainer()
        modules.auth.failures["googleSignUp"] = AccountException(AccountError.OFFLINE)
        launch(google = true, modules = modules)

        onScreen { it.agreeButton.performClick() }
        idle()

        assertEquals(string(R.string.offline_error), onScreen { it.errorText.text.toString() })
        assertEquals(R.id.signUpAgreementFragment, nav.currentDestination?.id)
        assertTrue(accountViewModel.waitingGoogle)
    }

    @Test
    fun google_back_goesToSignIn_andForgetsTheGoogleAccount() {
        launch(google = true)

        onScreen { it.backButton.performClick() }
        idle()

        assertEquals(R.id.signInFragment, nav.currentDestination?.id)
        assertFalse(accountViewModel.waitingGoogle)
    }

    @Test
    fun google_withoutAWaitingGoogleAccount_goesStraightBack() {
        val modules = launch(google = true, waiting = false)

        assertEquals(R.id.signInFragment, nav.currentDestination?.id)
        assertEquals(emptyList<String>(), modules.auth.calls)
    }

    @Test
    fun thePolicyLink_opensThePrivacyPolicy_fromEither() {
        for (google in listOf(false, true)) {
            launch(google)

            onScreen { it.policyButton.performClick() }
            idle()

            assertEquals(R.id.privacyPolicyFragment, nav.currentDestination?.id)
            scenario!!.close()
        }
    }
}
