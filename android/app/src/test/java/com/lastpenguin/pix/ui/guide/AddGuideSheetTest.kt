// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.ItemSituationTileBinding
import com.lastpenguin.pix.databinding.SheetAddGuideBinding
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import kotlinx.coroutines.runBlocking
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
 * Add a pose guide (R&S 6.2, FR-3.6, FR-4.2), on the real bottom sheet. Partitions: no guide on the camera (What are
 * you taking?, no Cancel) or a guide (Replace, with Keep the current guide); the button with consent already given
 * (the scene photo step starts, the sheet closes) or not yet (the notice first: Not now sends nothing, Agree starts
 * it), and the notice's buttons at Material's 12 dp padding, not the 20 dp of the theme's button style; Cancel. My
 * library (FR-3.8, FR-3.11): nothing saved (no count); the row, which opens the library and closes the sheet. The
 * situations (FR-4.8): a tile tapped is chosen with a check; Selfie's off and dimmed without a front camera. From
 * Subject view (FR-6.15): no situations and no button,
 * Add a pose guide as the title, and Replace only for a guide on the video, not one on this phone's camera.
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

    private fun launch(modules: TestAppContainer = TestAppContainer(), fromSubject: Boolean = false): TestAppContainer {
        modules.install()
        scenario = launchFragment<AddGuideSheet>(
            fragmentArgs = bundleOf(AddGuideSheet.ARG_FROM_SUBJECT to fromSubject),
            themeResId = R.style.Theme_Pix,
        )
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
    fun withoutAGuide_asksWhatToTake() {
        launch()

        onSheet { binding ->
            assertEquals(string(R.string.situation_question), binding.titleText.text.toString())
            assertFalse(binding.cancelButton.isShown)
        }
    }

    @Test
    fun fromSubjectView_offersNoPosesAndAsksToReplaceTheGuideOnTheVideo() {
        val modules = TestAppContainer()
        modules.guides.setGuide(guide())
        launch(modules, fromSubject = true)
        onSheet { binding ->
            assertEquals(View.GONE, binding.newPosesSection.visibility)
            assertEquals(View.GONE, binding.generateButton.visibility)
            assertEquals(string(R.string.add_guide_title), binding.titleText.text.toString())
        }
        scenario?.close()

        val replacing = TestAppContainer()
        replacing.mirror.setGuide(guide())
        launch(replacing, fromSubject = true)
        onSheet { binding -> assertEquals(string(R.string.replace_guide_title), binding.titleText.text.toString()) }
    }

    @Test
    fun withAGuideOnTheCamera_asksToReplaceIt() {
        val modules = TestAppContainer()
        modules.guides.setGuide(guide())
        launch(modules)

        onSheet { binding ->
            assertEquals(string(R.string.replace_guide_title), binding.titleText.text.toString())
            assertEquals(string(R.string.keep_current_guide), binding.cancelButton.text.toString())
            assertTrue(binding.cancelButton.isShown)
        }
    }

    @Test
    fun aTileTapped_isChosenWithACheck_andSelfiesIsOffWithoutAFrontCamera() {
        val modules = launch(TestAppContainer().apply { camera.hasFrontCamera.value = false })
        val (landmark, selfie) = onSheet { binding ->
            ItemSituationTileBinding.bind(binding.situationRowBottom.getChildAt(1)) to
                ItemSituationTileBinding.bind(binding.situationRowBottom.getChildAt(2))
        }

        landmark.root.performClick()
        idle()
        assertEquals(Situation.LANDMARK, generation.situationChoice.value)
        assertTrue(landmark.root.isSelected)
        assertTrue(landmark.situationCheck.isShown)
        assertFalse(selfie.root.isEnabled)
        assertEquals(0.38f, selfie.root.alpha, 0.001f)

        modules.camera.hasFrontCamera.value = true
        idle()
        assertTrue(selfie.root.isEnabled)
        assertEquals(1f, selfie.root.alpha, 0.001f)
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
    fun consentNotice_buttonsKeepMaterialsDialogPadding() {
        launch(TestAppContainer().apply { consent.given = false })

        onSheet { binding -> binding.generateButton.performClick() }
        idle()

        val notice = ShadowDialog.getLatestDialog() as AlertDialog
        val density = notice.context.resources.displayMetrics.density
        for (which in listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE)) {
            val button = notice.getButton(which)
            assertEquals(12f, button.paddingStart / density, 0.01f)
            assertEquals(12f, button.paddingEnd / density, 0.01f)
        }
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
    fun myLibrary_nothingSaved_showsNoCount() {
        launch()

        onSheet { binding ->
            assertTrue(binding.galleryRow.isShown)
            assertTrue(binding.libraryRow.isShown)
            assertFalse(binding.libraryCount.isShown)
        }
    }

    @Test
    fun myLibrary_row_opensTheLibraryAndCloses() {
        val modules = TestAppContainer()
        runBlocking { modules.saved.save(guide(), GuideState()) }
        launch(modules)
        val nav = scenario!!.attachNav(R.id.addGuideSheet)

        onSheet { binding -> binding.libraryRow.performClick() }
        idle()

        assertEquals(R.id.libraryFragment, nav.currentDestination?.id)
        assertFalse(nav.backStack.any { it.destination.id == R.id.addGuideSheet })
    }

    @Test
    fun cancel_closesTheSheet() {
        launch()

        onSheet { binding -> binding.cancelButton.performClick() }
        idle()

        assertFalse(sheet.isAdded)
    }
}
