// AI-generated with Claude Code, 2026-10-26, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.guide

import android.net.Uri
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentReferenceConfirmBinding
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/*
 * Reference confirm's render paths (R&S 6.2, FR-2.3, FR-2.4; Testing Documentation 2.1.6), on the real Fragment.
 * Partitions on the reference state: nothing yet, working (the person is being found), ready (the outline shown
 * and Use this guide enabled, which puts the guide on the camera), no person found (to that screen), and a photo
 * that could not be used (a toast and the camera). Back on a generated pose's guide, or another photo from the
 * gallery in its place, stops the ask for its tip.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferenceConfirmFragmentTest {
    private var scenario: FragmentScenario<ReferenceConfirmFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<ReferenceConfirmFragment>(themeResId = R.style.Theme_Pix)
        nav = scenario!!.attachNav(R.id.referenceConfirmFragment)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentReferenceConfirmBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentReferenceConfirmBinding.bind(it.requireView())) }
        return result!!
    }

    private fun pickPhoto() {
        scenario!!.onFragment {
            it.requireActivity().viewModel<ReferenceViewModel>().onPhotoPicked(Uri.parse("content://photos/1"))
        }
        idle()
    }

    @Test
    fun working_showsProgressAndFindingThePerson_withUseDisabled() {
        launch()
        onScreen { binding -> assertFalse(binding.useGuideButton.isEnabled) }

        pickPhoto()

        onScreen { binding ->
            assertEquals(View.VISIBLE, binding.referenceProgress.visibility)
            assertEquals(View.GONE, binding.referenceImage.visibility)
            assertEquals(string(R.string.finding_person), binding.referenceStatus.text.toString())
            assertFalse(binding.useGuideButton.isEnabled)
        }
    }

    @Test
    fun ready_showsTheOutline_andUseThisGuidePutsItOnTheCamera() {
        val modules = launch()
        pickPhoto()
        val made = guide()

        modules.maker.finish(Result.success(made))
        idle()

        onScreen { binding ->
            assertEquals(View.GONE, binding.referenceProgress.visibility)
            assertEquals(View.VISIBLE, binding.referenceImage.visibility)
            assertEquals(string(R.string.person_found), binding.referenceStatus.text.toString())
            assertTrue(binding.useGuideButton.isEnabled)
            binding.useGuideButton.performClick()
        }
        idle()

        assertSame(made, modules.guides.guide.value)
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    /** A generated pose's guide on the screen, with its tip asked for. */
    private fun generatedPose(modules: TestAppContainer): TestAppContainer.ScriptedPoseTipper.Request {
        scenario!!.onFragment {
            it.requireActivity().viewModel<ReferenceViewModel>()
                .onCandidatePicked(bitmap(), TipSource(bitmap(), "Walk past the railing"))
        }
        idle()
        modules.maker.finish(Result.success(guide()))
        idle()
        return modules.tipper.requests.single()
    }

    @Test
    fun backOnAGeneratedPose_stopsTheAskForItsTip() {
        val modules = launch()
        val request = generatedPose(modules)

        onScreen { it.backButton.performClick() }
        idle()

        assertTrue(request.cancelled)
    }

    @Test
    fun anotherPhotoInPlaceOfAGeneratedPose_stopsTheAskForItsTip() {
        val modules = launch()
        val request = generatedPose(modules)

        pickPhoto()

        assertTrue(request.cancelled)
    }

    @Test
    fun noPersonFound_opensThatScreen() {
        val modules = launch()
        pickPhoto()

        modules.maker.finish(Result.failure(NoPersonFoundException()))
        idle()

        assertEquals(R.id.noPersonFoundFragment, nav.currentDestination?.id)
    }

    @Test
    fun aPhotoThatCannotBeUsed_saysSoAndReturnsToTheCamera() {
        val modules = launch()
        pickPhoto()

        modules.maker.finish(Result.failure(IOException("unreadable")))
        idle()

        assertEquals(string(R.string.couldnt_use_photo), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
