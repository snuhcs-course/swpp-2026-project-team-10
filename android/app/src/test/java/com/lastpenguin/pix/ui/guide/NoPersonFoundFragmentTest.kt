// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.guide

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentFailureBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/* No person found (R&S 6.2, FR-2.4): the texts, Choose another photo opens the picker, Back to camera. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoPersonFoundFragmentTest {
    @Test
    fun opens_withTheTexts_chooseAnotherOpensThePicker_backReturnsToTheCamera() {
        TestAppContainer().install()
        val scenario = launchFragmentInContainer<NoPersonFoundFragment>(themeResId = R.style.Theme_Pix)
        val nav = scenario.attachNav(R.id.noPersonFoundFragment)
        idle()

        scenario.onFragment { fragment ->
            val binding = FragmentFailureBinding.bind(fragment.requireView())
            assertEquals(string(R.string.no_person_title), binding.failureTitle.text.toString())
            assertEquals(string(R.string.no_person_message), binding.failureMessage.text.toString())
            assertEquals(string(R.string.choose_another_photo), binding.primaryButton.text.toString())
            assertEquals(string(R.string.back_to_camera), binding.secondaryButton.text.toString())
            binding.primaryButton.performClick()
            // The system photo picker for one image (FR-2.1); Pix never reads the gallery itself.
            assertNotNull(shadowOf(fragment.requireActivity()).nextStartedActivity)
            binding.secondaryButton.performClick()
        }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        scenario.close()
    }
}
