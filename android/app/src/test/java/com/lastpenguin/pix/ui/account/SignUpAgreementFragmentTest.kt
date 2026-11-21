// AI-generated with Claude Code, 2026-11-21, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.account

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
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
 * Share my camera and Sign in. Partitions: the consents (none, some, all three given by hand, all through Agree to all,
 * one taken back; Agree and continue waits for all three, and Agree to all follows them); the details (hidden at first,
 * shown and hidden by their chevron, the password or the Google account among what is collected); a recreated screen
 * (the boxes kept); Sign up's first page (Agree and continue opens the form over it and calls nothing; an error from the
 * form does not show here); Sign up with Google (it makes the account from the waiting Google account and opens Pix ID
 * setup in the place of Sign in and this screen; a refused sign-up shows the reason and stays; Back goes to Sign in and
 * forgets the Google account; a new process with no waiting Google account goes straight back); the privacy policy's
 * row, from either.
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

    private fun details(details: LinearLayout): List<String> =
        (0 until details.childCount).map { details.getChildAt(it) }.mapNotNull { line ->
            line.findViewById<TextView>(R.id.detailValue)?.text?.toString()
        }

    @Test
    fun agreeAndContinue_waitsForAllThreeConsents_andAgreeToAllFollowsThem() {
        launch(google = false)
        assertFalse(onScreen { it.agreeButton.isEnabled })

        onScreen { it.ageCheck.performClick() }
        onScreen { it.collectionCheck.performClick() }
        assertFalse(onScreen { it.agreeButton.isEnabled || it.allCheck.isChecked })
        onScreen { it.transferCheck.performClick() }
        assertTrue(onScreen { it.agreeButton.isEnabled && it.allCheck.isChecked })

        onScreen { it.collectionCheck.performClick() }
        assertFalse(onScreen { it.agreeButton.isEnabled || it.allCheck.isChecked })

        onScreen { it.allCheck.performClick() }
        assertTrue(onScreen { it.ageCheck.isChecked && it.collectionCheck.isChecked && it.transferCheck.isChecked })
        assertTrue(onScreen { it.agreeButton.isEnabled })
        onScreen { it.allCheck.performClick() }
        assertFalse(onScreen { it.ageCheck.isChecked || it.collectionCheck.isChecked || it.transferCheck.isChecked })
        assertFalse(onScreen { it.agreeButton.isEnabled })
    }

    @Test
    fun theDetails_showUnderTheirChevron_andNameThePassword() {
        launch(google = false)
        assertFalse(onScreen { it.collectionDetails.isVisible || it.transferDetails.isVisible })
        assertTrue(onScreen { it.ageCheck.text.startsWith(string(R.string.sign_up_consent_required)) })

        onScreen { it.collectionToggle.performClick() }
        assertTrue(onScreen { it.collectionDetails.isVisible })
        assertEquals(
            string(R.string.sign_up_consent_details_hide, string(R.string.sign_up_consent_collection)),
            onScreen { it.collectionToggle.contentDescription.toString() },
        )
        assertEquals(
            listOf(
                R.string.sign_up_consent_items_password,
                R.string.sign_up_consent_purpose,
                R.string.sign_up_consent_period,
            ).map {
                string(it)
            },
            onScreen { details(it.collectionDetails) },
        )
        onScreen { it.transferToggle.performClick() }
        assertEquals(5, onScreen { details(it.transferDetails).size })

        onScreen { it.collectionToggle.performClick() }
        assertFalse(onScreen { it.collectionDetails.isVisible })
    }

    @Test
    fun aRecreatedScreen_keepsTheBoxes() {
        launch(google = false)
        onScreen { it.ageCheck.performClick() }
        onScreen { it.transferCheck.performClick() }

        scenario!!.recreate()
        idle()

        assertEquals(
            listOf(true, false, true, false),
            onScreen {
                listOf(it.ageCheck, it.collectionCheck, it.transferCheck, it.allCheck).map { box -> box.isChecked }
            },
        )
        assertFalse(onScreen { it.agreeButton.isEnabled })
    }

    @Test
    fun signUp_agreeAndContinue_opensTheFormOverIt() {
        val modules = launch(google = false)
        assertEquals(string(R.string.sign_up_title), onScreen { it.titleText.text.toString() })

        onScreen { it.allCheck.performClick() }
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
    }

    @Test
    fun google_agreeAndContinue_makesTheAccountAndGoesToTheSetup() {
        val modules = launch(google = true)
        assertEquals(string(R.string.google_sign_up_title), onScreen { it.titleText.text.toString() })
        onScreen { it.collectionToggle.performClick() }
        assertEquals(string(R.string.sign_up_consent_items_google), onScreen { details(it.collectionDetails).first() })
        assertEquals(listOf("google:google-token"), modules.auth.calls)

        onScreen { it.allCheck.performClick() }
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

        onScreen { it.allCheck.performClick() }
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
    fun thePolicyRow_opensThePrivacyPolicy_fromEither() {
        for (google in listOf(false, true)) {
            launch(google)

            onScreen { it.policyRow.root.performClick() }
            idle()

            assertEquals(R.id.privacyPolicyFragment, nav.currentDestination?.id)
            scenario!!.close()
        }
    }
}
