// AI-generated with Claude Code, 2026-10-27, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
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
import org.robolectric.shadows.ShadowToast

/*
 * Connection lost's render paths (R&S 6.6, FR-6.11; Testing Documentation 2.1.6), on the real Fragment.
 * Partitions: the screen as opened; Reconnect without a kept code (back to the camera) and with one (joins the
 * same code again, Reconnect disabled while connecting); the reconnect connecting (to Subject view), finding the
 * room gone (a toast and the camera), or failing otherwise (Reconnect enabled again); Leave.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectionLostFragmentTest {
    private var scenario: FragmentScenario<ConnectionLostFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<ConnectionLostFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentFailureBinding, ConnectionLostFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result =
                block(FragmentFailureBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    /** The join that was lost, so the session keeps its code for *Reconnect* (FR-6.11). */
    private fun lostAfterJoining(modules: TestAppContainer, code: String = "482915") {
        onScreen { _, fragment ->
            ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[SessionViewModel::class.java]
                .joinRoom(code)
        }
        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()
    }

    @Test
    fun opens_withTheReasonReconnectAndLeave() {
        launch()

        onScreen { binding, _ ->
            assertEquals(string(R.string.connection_lost_title), binding.failureTitle.text.toString())
            assertEquals(string(R.string.connection_lost_message), binding.failureMessage.text.toString())
            assertEquals(string(R.string.reconnect), binding.primaryButton.text.toString())
            assertTrue(binding.primaryButton.isEnabled)
            assertEquals(string(R.string.leave), binding.secondaryButton.text.toString())
        }
    }

    @Test
    fun reconnect_withoutAKeptCode_returnsToTheCamera() {
        launch()
        val nav = scenario!!.attachNav(R.id.connectionLostFragment)

        onScreen { binding, _ -> binding.primaryButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun reconnect_rejoinsTheSameSession_andOpensSubjectViewWhenConnecting() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.connectionLostFragment)
        lostAfterJoining(modules)

        onScreen { binding, _ -> binding.primaryButton.performClick() }
        idle()

        onScreen { binding, _ ->
            assertFalse(binding.primaryButton.isEnabled)
            assertEquals(string(R.string.connecting), binding.failureMessage.text.toString())
        }
        assertEquals(SessionEntry.Rejoin("482915") to Role.SUBJECT, modules.session.starts.last())

        modules.session.setState(SessionState.Connecting("s1"))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun reconnect_roomGone_saysTheSessionEndedAndReturnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.connectionLostFragment)
        lostAfterJoining(modules)
        onScreen { binding, _ -> binding.primaryButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.NOT_FOUND))
        idle()

        assertEquals(string(R.string.session_ended), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun reconnect_otherFailure_enablesReconnectAgain() {
        val modules = launch()
        lostAfterJoining(modules)
        onScreen { binding, _ -> binding.primaryButton.performClick() }
        idle()

        modules.session.setState(SessionState.Ended(EndReason.ERROR))
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.primaryButton.isEnabled)
            assertEquals(string(R.string.connection_lost_message), binding.failureMessage.text.toString())
        }
    }

    @Test
    fun leave_returnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.connectionLostFragment)
        lostAfterJoining(modules)

        onScreen { binding, _ -> binding.secondaryButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
