// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentJoinCodeBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
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

/*
 * Join with code's render paths (R&S 6.7, FR-8.2, FR-8.3, FR-8.5; Testing Documentation 2.1.6), on the real
 * Fragment. Partitions: the field empty, 5 digits, 6 digits; opened fresh or to correct a kept code; Join pressed,
 * then the session connecting (to Subject view), not found or expired (to Session not found), or failing otherwise
 * (the same-Wi-Fi message, Join enabled again); Back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JoinCodeFragmentTest {
    private var scenario: FragmentScenario<JoinCodeFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(keepCode: Boolean = false): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<JoinCodeFragment>(
            fragmentArgs = bundleOf("keepCode" to keepCode),
            themeResId = R.style.Theme_Pix,
        )
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentJoinCodeBinding, JoinCodeFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result =
                block(FragmentJoinCodeBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    private fun sessionViewModel(fragment: JoinCodeFragment): SessionViewModel =
        ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[SessionViewModel::class.java]

    private fun type(code: String) = onScreen { binding, _ -> binding.codeInput.setText(code) }

    @Test
    fun startsEmpty_withJoinDisabled() {
        launch()

        onScreen { binding, _ ->
            assertEquals("", binding.codeInput.text.toString())
            assertFalse(binding.joinButton.isEnabled)
            assertEquals(View.GONE, binding.statusText.visibility)
            assertEquals("", binding.digit1.text.toString())
        }
    }

    @Test
    fun sixDigits_enableJoinAndFillTheBoxes_fiveDoNot() {
        launch()

        type("48291")
        onScreen { binding, _ ->
            assertFalse(binding.joinButton.isEnabled)
            assertEquals("1", binding.digit5.text.toString())
            assertEquals("", binding.digit6.text.toString())
        }

        type("482915")
        onScreen { binding, _ ->
            assertTrue(binding.joinButton.isEnabled)
            assertEquals(
                listOf("4", "8", "2", "9", "1", "5"),
                listOf(
                    binding.digit1,
                    binding.digit2,
                    binding.digit3,
                    binding.digit4,
                    binding.digit5,
                    binding.digit6,
                ).map { it.text.toString() },
            )
        }
    }

    @Test
    fun openedToCorrectACode_offersTheKeptCode() {
        launch(keepCode = true)
        // Session not found keeps the code of the join that failed (FR-8.3); its Try again opens this screen as a
        // new instance, which recreate() stands for. The field's own saved state is turned off, because a new
        // instance has none, while a rotation would restore what was typed.
        onScreen { binding, fragment ->
            sessionViewModel(fragment).joinRoom("482915")
            binding.codeInput.isSaveEnabled = false
        }
        idle()

        scenario!!.recreate()
        idle()

        onScreen { binding, _ ->
            assertEquals("482915", binding.codeInput.text.toString())
            assertTrue(binding.joinButton.isEnabled)
        }
    }

    @Test
    fun join_showsConnecting_andAConnectingSessionOpensSubjectView() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)

        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.statusText.visibility)
            assertEquals(string(R.string.connecting), binding.statusText.text.toString())
            assertFalse(binding.joinButton.isEnabled)
        }
        assertEquals(SessionEntry.RoomCode("482915") to Role.SUBJECT, modules.session.starts.last())

        modules.session.setState(SessionState.Connecting("s1"))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun join_unknownCode_opensSessionNotFound() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)
        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        idle()

        assertEquals(R.id.sessionNotFoundFragment, nav.currentDestination?.id)
    }

    @Test
    fun join_otherFailure_showsTheWifiMessageAndEnablesJoinAgain() {
        val modules = launch()
        type("482915")
        onScreen { binding, _ -> binding.joinButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.ERROR))
        idle()

        onScreen { binding, _ ->
            assertEquals(string(R.string.couldnt_connect_wifi), binding.statusText.text.toString())
            assertEquals(View.VISIBLE, binding.statusText.visibility)
            assertTrue(binding.joinButton.isEnabled)
        }
    }

    @Test
    fun back_returnsToTheCamera() {
        launch()
        val nav = scenario!!.attachNav(R.id.joinCodeFragment)

        onScreen { binding, _ -> binding.backButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
