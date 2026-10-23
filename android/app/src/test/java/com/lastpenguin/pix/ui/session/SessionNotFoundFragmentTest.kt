// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/* Session not found (R&S 6.7, FR-8.3): the texts, Try again back to Join with code with the kept code, Back. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionNotFoundFragmentTest {
    @Test
    fun opens_withTheTexts_tryAgainOpensJoinWithCode_backReturnsToTheCamera() {
        TestAppContainer().install()
        val scenario = launchFragmentInContainer<SessionNotFoundFragment>(themeResId = R.style.Theme_Pix)
        val nav = scenario.attachNav(R.id.sessionNotFoundFragment)
        idle()

        scenario.onFragment { fragment ->
            val binding = FragmentFailureBinding.bind(fragment.requireView())
            assertEquals(string(R.string.session_not_found_title), binding.failureTitle.text.toString())
            assertEquals(string(R.string.session_not_found_message), binding.failureMessage.text.toString())
            assertEquals(string(R.string.try_again), binding.primaryButton.text.toString())
            assertEquals(string(R.string.back), binding.secondaryButton.text.toString())
            binding.primaryButton.performClick()
        }
        idle()
        assertEquals(R.id.joinCodeFragment, nav.currentDestination?.id)
        // The action carries keepCode, so the kept code is offered for editing (FR-8.3, #81).
        assertEquals(true, nav.currentBackStackEntry?.arguments?.getBoolean("keepCode"))

        nav.setCurrentDestination(R.id.sessionNotFoundFragment)
        scenario.onFragment { FragmentFailureBinding.bind(it.requireView()).secondaryButton.performClick() }
        idle()
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        scenario.close()
    }
}
