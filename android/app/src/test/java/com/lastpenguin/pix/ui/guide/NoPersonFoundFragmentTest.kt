// AI-generated with Claude Code, 2026-11-10, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.guide

import android.view.View
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

/*
 * No person found (R&S 6.2, FR-2.4): the texts; the top bar (#199), whose Choose another photo opens the picker and
 * whose Back goes to the step before, the camera here; no buttons at the bottom.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoPersonFoundFragmentTest {
    @Test
    fun opens_withTheTexts_chooseAnotherOpensThePicker_backReturnsToTheStepBefore() {
        TestAppContainer().install()
        val scenario = launchFragmentInContainer<NoPersonFoundFragment>(themeResId = R.style.Theme_Pix)
        val nav = scenario.attachNav(R.id.noPersonFoundFragment)
        idle()

        scenario.onFragment { fragment ->
            val binding = FragmentFailureBinding.bind(fragment.requireView())
            assertEquals(string(R.string.no_person_title), binding.failureTitle.text.toString())
            assertEquals(string(R.string.no_person_message), binding.failureMessage.text.toString())
            assertEquals(string(R.string.choose_another_photo), binding.topActionButton.text.toString())
            assertEquals(View.GONE, binding.primaryButton.visibility)
            assertEquals(View.GONE, binding.secondaryButton.visibility)
            binding.topActionButton.performClick()
            // The system photo picker for one image (FR-2.1); Pix never reads the gallery itself.
            assertNotNull(shadowOf(fragment.requireActivity()).nextStartedActivity)
            binding.backButton.performClick()
        }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        scenario.close()
    }
}
