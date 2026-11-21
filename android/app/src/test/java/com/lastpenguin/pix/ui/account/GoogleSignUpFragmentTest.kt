// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

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
import com.lastpenguin.pix.databinding.FragmentGoogleSignUpBinding
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
 * Sign up with Google on the real Fragment (R&S 6.4; FR-5.13, FR-5.18), opened from Sign in's question as the app
 * opens it, over Share my camera and Sign in. Partitions: the agreement (not given: *Sign up* waits; given: it makes
 * the account from the waiting Google account and opens Pix ID setup in the place of Sign in and this screen; taken
 * back: it waits again); a refused sign-up (the reason shows, the screen stays); the notice (it names the Google
 * account, not a password; its link opens the privacy policy); Back (to Sign in, the Google account forgotten); a new
 * process with no waiting Google account (straight back).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoogleSignUpFragmentTest {
    private var scenario: FragmentScenario<GoogleSignUpFragment>? = null
    private lateinit var nav: TestNavHostController
    private lateinit var accountViewModel: AccountViewModel

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Share my camera, Sign in, and this screen, with the Google account waiting unless [waiting] is false. */
    private fun launch(modules: TestAppContainer = TestAppContainer(), waiting: Boolean = true): TestAppContainer {
        modules.auth.googleResult = GoogleSignIn.NotRegistered
        modules.install()
        // Created without its view, so the shared ViewModel can hold the Google account before the screen reads it.
        scenario = launchFragmentInContainer<GoogleSignUpFragment>(
            themeResId = R.style.Theme_Pix,
            initialState = Lifecycle.State.CREATED,
        )
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        scenario!!.onFragment { fragment ->
            val activity: FragmentActivity = fragment.requireActivity()
            nav.setGraph(R.navigation.nav_graph)
            nav.navigate(R.id.friendsFragment)
            nav.navigate(R.id.signInFragment)
            nav.navigate(R.id.action_signIn_to_googleSignUp)
            Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
            accountViewModel = activity.viewModel()
            if (waiting) accountViewModel.continueWithGoogle(activity)
        }
        idle()
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentGoogleSignUpBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentGoogleSignUpBinding.bind(it.requireView())) }
        return result!!
    }

    @Test
    fun signUp_waitsForTheAgreement_thenMakesTheAccountAndGoesToTheSetup() {
        val modules = launch()
        assertFalse(onScreen { it.signUpButton.isEnabled })

        onScreen { it.notice.agreeCheck.performClick() }
        assertTrue(onScreen { it.signUpButton.isEnabled })
        onScreen { it.notice.agreeCheck.performClick() }
        assertFalse(onScreen { it.signUpButton.isEnabled })
        assertEquals(listOf("google:google-token"), modules.auth.calls)

        onScreen { it.notice.agreeCheck.performClick() }
        onScreen { it.signUpButton.performClick() }
        idle()

        assertEquals("googleSignUp:google-token", modules.auth.calls.last())
        assertEquals(R.id.pixIdSetupFragment, nav.currentDestination?.id)
        assertEquals(R.id.friendsFragment, nav.previousBackStackEntry?.destination?.id)
    }

    @Test
    fun aRefusedSignUp_showsTheReason_andStays() {
        val modules = TestAppContainer()
        modules.auth.failures["googleSignUp"] = AccountException(AccountError.OFFLINE)
        launch(modules)

        onScreen { it.notice.agreeCheck.performClick() }
        onScreen { it.signUpButton.performClick() }
        idle()

        assertEquals(string(R.string.offline_error), onScreen { it.errorText.text.toString() })
        assertEquals(R.id.googleSignUpFragment, nav.currentDestination?.id)
        assertTrue(accountViewModel.waitingGoogle)
    }

    @Test
    fun theNotice_namesTheGoogleAccount_andOpensThePrivacyPolicy() {
        launch()

        assertEquals(string(R.string.sign_up_notice_what_google), onScreen { it.notice.whatText.text.toString() })
        onScreen { it.notice.policyButton.performClick() }
        idle()

        assertEquals(R.id.privacyPolicyFragment, nav.currentDestination?.id)
    }

    @Test
    fun back_goesToSignIn_andForgetsTheGoogleAccount() {
        launch()

        onScreen { it.backButton.performClick() }
        idle()

        assertEquals(R.id.signInFragment, nav.currentDestination?.id)
        assertFalse(accountViewModel.waitingGoogle)
    }

    @Test
    fun withoutAWaitingGoogleAccount_goesStraightBack() {
        val modules = launch(waiting = false)

        assertEquals(R.id.signInFragment, nav.currentDestination?.id)
        assertEquals(emptyList<String>(), modules.auth.calls)
    }
}
