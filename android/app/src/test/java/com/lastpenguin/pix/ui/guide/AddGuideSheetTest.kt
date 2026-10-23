// AI-generated with Claude Code, 2026-10-22, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.guide

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/*
 * Add a pose guide (R&S 6.2, FR-3.6, FR-4.2), on the real bottom sheet. Partitions: no guide on the camera (Add)
 * or a guide (Replace, with Keep the current guide); Generate poses here with consent already given (the scene
 * photo step starts, the sheet closes) or not yet (the notice first: Not now sends nothing, Agree starts it);
 * Cancel.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddGuideSheetTest {
    private var scenario: FragmentScenario<AddGuideSheet>? = null
    private lateinit var sheet: AddGuideSheet

    /** The activity's GenerationViewModel, taken while the sheet is attached; it outlives the sheet's dismissal. */
    private lateinit var generation: GenerationViewModel

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        scenario = launchFragment<AddGuideSheet>(themeResId = R.style.Theme_Pix)
        scenario!!.onFragment {
            sheet = it
            generation = it.requireActivity().viewModel()
        }
        idle()
        return modules
    }

    private fun <T> onSheet(block: (SheetAddGuideBinding) -> T): T = block(
        SheetAddGuideBinding.bind(sheet.requireView()),
    )

    @Test
    fun withoutAGuide_offersToAddOne() {
        launch()

        onSheet { binding ->
            assertEquals(string(R.string.add_guide_title), binding.titleText.text.toString())
            assertEquals(string(R.string.cancel), binding.cancelButton.text.toString())
        }
    }

    @Test
    fun withAGuideOnTheCamera_asksToReplaceIt() {
        val modules = TestAppContainer()
        modules.guides.setGuide(guide())
        launch(modules)

        onSheet { binding ->
            assertEquals(string(R.string.replace_guide_title), binding.titleText.text.toString())
            assertEquals(string(R.string.replace_guide_body), binding.bodyText.text.toString())
            assertEquals(string(R.string.keep_current_guide), binding.cancelButton.text.toString())
        }
    }

    @Test
    fun generate_withConsentGiven_startsTheScenePhotoAndCloses() {
        launch()

        onSheet { binding -> binding.generateButton.performClick() }
        idle()

        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertFalse(sheet.isAdded)
    }

    @Test
    fun generate_withoutConsent_asksFirst_notNowSendsNothing_agreeStarts() {
        val modules = TestAppContainer().apply { consent.given = false }
        launch(modules)

        onSheet { binding -> binding.generateButton.performClick() }
        idle()

        val notice = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(notice)
        assertTrue(notice.isShowing)
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)

        notice.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)
        assertFalse(modules.consent.given)
        assertTrue(sheet.isAdded)

        onSheet { binding -> binding.generateButton.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertTrue(modules.consent.given)
        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertFalse(sheet.isAdded)
    }

    @Test
    fun cancel_closesTheSheet() {
        launch()

        onSheet { binding -> binding.cancelButton.performClick() }
        idle()

        assertFalse(sheet.isAdded)
    }
}
