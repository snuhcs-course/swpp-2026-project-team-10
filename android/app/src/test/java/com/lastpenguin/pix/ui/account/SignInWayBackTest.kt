// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.account

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * Where Sign in, Sign up, and Pix ID setup go once they are done (R&S 6.4, 6.8; FR-5.2, FR-10.5): back to the screen
 * that opened Sign in, which is Share my camera, Invitation, or Settings (#188; until then, always Friends). Each runs
 * on the real Fragment opened from Settings, with the account fakes behind the shared AccountViewModel; Sign in from
 * Share my camera is in `SignInFragmentTest`. Partitions: a sign-in; Sign up over Sign in; Pix ID setup in Sign in's
 * place, saved or left with Back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInWayBackTest {
    private var scenario: FragmentScenario<out Fragment>? = null
    private lateinit var nav: TestNavHostController
    private lateinit var accountViewModel: AccountViewModel

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** [F] over Settings and Camera, reached by [moves] from Settings, as the app opens it. */
    private inline fun <reified F : Fragment> launch(vararg moves: Int) {
        TestAppContainer().install()
        val launched = launchFragmentInContainer<F>(themeResId = R.style.Theme_Pix)
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        launched.onFragment { fragment ->
            nav.setGraph(R.navigation.nav_graph)
            nav.navigate(R.id.settingsFragment)
            for (move in moves) nav.navigate(move)
            Navigation.setViewNavController(fragment.requireView(), nav)
            accountViewModel = fragment.requireActivity().viewModel()
        }
        scenario = launched
        idle()
    }

    private fun screens() = nav.currentBackStack.value.map { it.destination.id }.filter { it != R.id.nav_graph }

    @Test
    fun aSignIn_goesBackToSettings() {
        launch<SignInFragment>(R.id.action_settings_to_signIn)

        accountViewModel.logIn("test1", "secret12")
        idle()

        assertEquals(listOf(R.id.cameraFragment, R.id.settingsFragment), screens())
    }

    @Test
    fun signUp_closesWithSignIn_backToSettings() {
        launch<SignUpFragment>(R.id.action_settings_to_signIn, R.id.action_signIn_to_signUp)

        accountViewModel.signUp("test9", "Test Account 9", "secret12")
        idle()

        assertEquals(listOf(R.id.cameraFragment, R.id.settingsFragment), screens())
    }

    @Test
    fun pixIdSetup_takesSignInsPlace_andGoesBackToSettings_savedOrNot() {
        launch<PixIdSetupFragment>(R.id.action_settings_to_signIn, R.id.action_signIn_to_pixIdSetup)
        assertEquals(listOf(R.id.cameraFragment, R.id.settingsFragment, R.id.pixIdSetupFragment), screens())

        accountViewModel.savePixId("test9")
        idle()
        assertEquals(listOf(R.id.cameraFragment, R.id.settingsFragment), screens())

        scenario!!.close()
        launch<PixIdSetupFragment>(R.id.action_settings_to_signIn, R.id.action_signIn_to_pixIdSetup)
        scenario!!.onFragment { it.requireView().findViewById<View>(R.id.backButton).performClick() }
        idle()
        assertEquals(listOf(R.id.cameraFragment, R.id.settingsFragment), screens())
    }
}
