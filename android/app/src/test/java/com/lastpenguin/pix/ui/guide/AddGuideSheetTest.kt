// AI-generated with Claude Code, 2026-11-19, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.children
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/*
 * Add a pose guide (R&S 6.2, FR-3.6, FR-4.2), on the real bottom sheet. Partitions: no guide on the camera (Add a pose
 * guide, no Cancel) or a guide (Replace, with Keep the current guide); the button with consent already given
 * (the scene photo step starts, the sheet closes) or not yet (the notice first: Not now sends nothing, Agree starts
 * it), and the notice's buttons at Material's 12 dp padding, not the 20 dp of the theme's button style; Cancel. My
 * library (FR-3.8, FR-3.11): nothing saved (no count); the row, which opens the library and closes the sheet. The
 * gallery's picker (FR-2.7, #243): closed without a photo (the sheet stays open, the guide on the camera unchanged)
 * or with one (Reference confirm). The situations (FR-4.8): a tile tapped is chosen with a check; Selfie's off and
 * dimmed without a front camera. The order at the Galaxy S22's 360 × 705 dp, at font scale 1.0 and 1.3 (boundary,
 * #243): the title, then What are you taking? with the tiles and their button right under them, then the guide from
 * a photo with its two rows, each whole and clear of the next. From Subject view (FR-6.15): no situations and no
 * button, Add a pose guide as the title, and Replace only for a guide on the video, not one on this phone's camera.
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
        RuntimeEnvironment.setFontScale(1f)
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

    /** The view under [this] that shows [text], if any. */
    private fun View.showing(text: String): View? = when {
        this is TextView && this.text.toString() == text -> this
        this is ViewGroup -> children.firstNotNullOfOrNull { it.showing(text) }
        else -> null
    }

    private fun View.topInWindow(): Int = IntArray(2).also(::getLocationInWindow)[1]

    private fun View.bottomInWindow(): Int = topInWindow() + height

    /** Opens the gallery's picker from the sheet and closes it with [resultCode] and [data], as the system's would. */
    private fun answerThePicker(resultCode: Int, data: Intent?) {
        onSheet { binding -> binding.galleryRow.performClick() }
        scenario!!.onFragment { fragment ->
            val activity = shadowOf(fragment.requireActivity())
            val picker = checkNotNull(activity.nextStartedActivityForResult) { "no picker opened" }
            activity.receiveResult(picker.intent, resultCode, data)
        }
        idle()
    }

    @Test
    fun withoutAGuide_isAddAPoseGuide() {
        launch()

        onSheet { binding ->
            assertEquals(string(R.string.add_guide_title), binding.titleText.text.toString())
            assertFalse(binding.cancelButton.isShown)
        }
    }

    /**
     * At [scale]: the title, then What are you taking? with the tiles and their button right under them, then the
     * guide from a photo, each whole and above the next; the button is closer to the tiles than to the guide from a
     * photo, so it reads as theirs.
     */
    private fun assertTheSituationsSitRightAboveTheirButton(scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        launch()
        val label = "font scale $scale"

        onSheet { binding ->
            val question = checkNotNull(binding.root.showing(string(R.string.situation_question))) {
                "$label: no \"What are you taking?\""
            }
            val order = listOf(
                "the title" to binding.titleText,
                "What are you taking?" to question,
                "the top tiles" to binding.situationRowTop,
                "the bottom tiles" to binding.situationRowBottom,
                "Take a scene photo for poses" to binding.generateButton,
                "Guide from a photo" to binding.fromPhotoLabel,
                "Choose from the gallery" to binding.galleryRow,
                "My library" to binding.libraryRow,
            )
            for ((upper, lower) in order.zipWithNext()) {
                assertTrue(
                    "$label: ${upper.first} ends at ${upper.second.bottomInWindow()}, below the top of " +
                        "${lower.first} at ${lower.second.topInWindow()}",
                    upper.second.isShown && upper.second.bottomInWindow() <= lower.second.topInWindow(),
                )
            }
            val toTheTiles = binding.generateButton.topInWindow() - binding.situationRowBottom.bottomInWindow()
            val toThePhotoRows = binding.fromPhotoLabel.topInWindow() - binding.generateButton.bottomInWindow()
            assertTrue(
                "$label: $toTheTiles px to the tiles, $toThePhotoRows px to the rows",
                toTheTiles < toThePhotoRows,
            )
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h705dp-xxhdpi")
    fun theSituations_sitRightAboveTheirButton_apartFromTheGuideFromAPhoto() =
        assertTheSituationsSitRightAboveTheirButton(1f)

    @Test
    @Config(qualifiers = "en-w360dp-h705dp-xxhdpi")
    fun theSituations_sitRightAboveTheirButton_atFontScale1_3() = assertTheSituationsSitRightAboveTheirButton(1.3f)

    @Test
    fun closingThePicker_withoutAPhoto_keepsTheSheetOpen_andTheGuideOnTheCamera() {
        val modules = TestAppContainer()
        val onCamera = guide()
        modules.guides.setGuide(onCamera)
        launch(modules)
        val nav = scenario!!.attachNav(R.id.addGuideSheet)

        answerThePicker(Activity.RESULT_CANCELED, null)

        assertTrue(sheet.isAdded)
        assertTrue(sheet.requireDialog().isShowing)
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)
        assertTrue(modules.guides.guide.value === onCamera)
        onSheet { binding -> assertEquals(string(R.string.replace_guide_title), binding.titleText.text.toString()) }
    }

    @Test
    fun aPhotoPicked_opensReferenceConfirm() {
        launch()
        val nav = scenario!!.attachNav(R.id.addGuideSheet)

        answerThePicker(Activity.RESULT_OK, Intent().setData(Uri.parse("content://photos/1")))

        assertEquals(R.id.referenceConfirmFragment, nav.currentDestination?.id)
    }

    @Test
    fun fromSubjectView_offersNoPosesAndAsksToReplaceTheGuideOnTheVideo() {
        val modules = TestAppContainer()
        modules.guides.setGuide(guide())
        launch(modules, fromSubject = true)
        onSheet { binding ->
            assertEquals(View.GONE, binding.newPosesSection.visibility)
            assertFalse(binding.generateButton.isShown)
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
