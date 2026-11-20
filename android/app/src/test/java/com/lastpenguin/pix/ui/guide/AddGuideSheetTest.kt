// AI-generated with Claude Code, 2026-11-19, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import android.animation.ValueAnimator
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraFacing
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
import com.lastpenguin.pix.ui.generation.SHEET_SITUATIONS
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * guide, no Cancel) or a guide (Replace, with Keep the current guide); Cancel. The situations (FR-4.1, FR-4.8, #243):
 * each tile is the button, so no other button shows and no tile shows as chosen, and Auto's alone is marked Recommended
 * (#243); a tile with consent already given (the scene photo step starts for its situation, the sheet closes) or not
 * yet (the notice first: Not now sends nothing and leaves the sheet open, Agree starts it for the tile tapped), and the
 * notice's buttons at Material's 12 dp padding, not the 20 dp of the theme's button style; Selfie's off and dimmed
 * without a front camera, and a tap on it then starts nothing. A tile pressed (state-based, #243): a touch dims it and
 * shrinks it to 97 % within 100 ms, at once with animations off; a touch that leaves undoes it, release goes on with
 * the tile still pressed, and under the consent notice it is itself again; once a tap goes on, every other tap on the
 * sheet is ignored. My library (FR-3.8, FR-3.11): nothing saved (no count); the row, which opens the library and closes
 * the sheet, once however often it is tapped. The gallery's picker (FR-2.7, #243): closed without a photo (the sheet
 * stays open and takes taps again, the guide on the camera unchanged) or with one (Reference confirm). The order at the
 * Galaxy S22's 360 × 705 dp, at font scale 1.0 and 1.3 (boundary, #243): the title, then What are you taking? with the
 * tiles, Auto's badge whole on its tile, then the guide from a photo with its two rows, each whole and clear of the
 * next. From Subject view (FR-6.15): no situations, Add a pose guide as the title, and Replace only for a guide on the
 * video, not one on this phone's camera.
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
        setAnimatorScale(1f)
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    /** A finger's [action] at the middle of [this], of a touch that went down at [downTime]. */
    private fun View.touch(action: Int, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, width / 2f, height / 2f, 0)
        dispatchTouchEvent(event)
        event.recycle()
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

    /** Every view under [this], itself included. */
    private fun View.everyView(): Sequence<View> = sequenceOf(this) +
        ((this as? ViewGroup)?.children?.flatMap { it.everyView() } ?: emptySequence())

    /** [situation]'s tile, where SituationTiles puts it: the first three in the top row, the rest in the bottom one. */
    private fun tile(situation: Situation): ItemSituationTileBinding = onSheet { binding ->
        val index = SHEET_SITUATIONS.indexOf(situation)
        val row = if (index < SHEET_SITUATIONS.size / 2) binding.situationRowTop else binding.situationRowBottom
        ItemSituationTileBinding.bind(row.getChildAt(index % (SHEET_SITUATIONS.size / 2)))
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

    /** At [scale]: the title, then What are you taking? with the tiles, then the guide from a photo, each whole. */
    private fun assertTheSituationsComeFirst(scale: Float) {
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
        }
        val auto = tile(Situation.DEFAULT)
        val badge = auto.situationBadge
        assertTrue("$label: no badge on Auto", badge.isShown)
        assertEquals("$label: the badge is cut", 0, badge.layout.getEllipsisCount(0))
        assertTrue(
            "$label: the badge leaves its tile",
            badge.right <= auto.root.width && badge.bottom <= auto.root.height,
        )
    }

    @Test
    @Config(qualifiers = "en-w360dp-h705dp-xxhdpi")
    fun theSituations_comeFirst_thenTheGuideFromAPhoto() = assertTheSituationsComeFirst(1f)

    @Test
    @Config(qualifiers = "en-w360dp-h705dp-xxhdpi")
    fun theSituations_comeFirst_atFontScale1_3() = assertTheSituationsComeFirst(1.3f)

    @Test
    fun theTiles_areTheButtons_noOtherButtonShows_andNoneShowsAsChosen() {
        launch(TestAppContainer().apply { camera.facing.value = CameraFacing.FRONT })

        onSheet { binding ->
            val buttons = binding.root.everyView().filter { it is Button && it.isShown }.toList()
            assertEquals(emptyList<View>(), buttons)
        }
        for (situation in SHEET_SITUATIONS) {
            assertFalse("$situation", tile(situation).root.isSelected)
        }
    }

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
        // The sheet takes taps again.
        tile(Situation.DEFAULT).root.performClick()
        idle()
        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
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
    fun auto_alone_isMarkedRecommended() {
        launch()

        for (situation in SHEET_SITUATIONS) {
            assertEquals("$situation", situation == Situation.DEFAULT, tile(situation).situationBadge.isShown)
        }
        val auto = tile(Situation.DEFAULT)
        assertEquals(string(R.string.situation_recommended), auto.situationBadge.text.toString())
        assertEquals(
            string(R.string.situation_recommended_description, string(R.string.situation_default)),
            auto.root.contentDescription,
        )
    }

    @Test
    fun aTileTouched_dimsAndShrinksTo97Percent_within100ms_andATouchThatLeavesUndoesIt() {
        launch()
        val landmark = tile(Situation.LANDMARK)
        val down = SystemClock.uptimeMillis()

        landmark.root.touch(MotionEvent.ACTION_DOWN, down)
        waitMs(PRESSED_WITHIN_MS)
        assertEquals(0.97f, landmark.root.scaleX, 0.001f)
        assertEquals(0.97f, landmark.root.scaleY, 0.001f)
        assertEquals(1f, landmark.situationDim.alpha, 0.001f)
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)

        landmark.root.touch(MotionEvent.ACTION_CANCEL, down)
        waitMs(PRESSED_WITHIN_MS)
        assertEquals(1f, landmark.root.scaleX, 0.001f)
        assertEquals(0f, landmark.situationDim.alpha, 0.001f)
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)
        assertTrue(sheet.isAdded)
    }

    @Test
    fun withAnimationsOff_aPress_showsAndGoesAtOnce() {
        setAnimatorScale(0f)
        launch()
        val fullBody = tile(Situation.FULL_BODY)
        val down = SystemClock.uptimeMillis()

        fullBody.root.touch(MotionEvent.ACTION_DOWN, down)
        assertEquals(0.97f, fullBody.root.scaleX, 0.001f)
        assertEquals(1f, fullBody.situationDim.alpha, 0.001f)
        fullBody.root.touch(MotionEvent.ACTION_CANCEL, down)
        assertEquals(1f, fullBody.root.scaleX, 0.001f)
        assertEquals(0f, fullBody.situationDim.alpha, 0.001f)
    }

    @Test
    fun aTileReleased_goesOn_stillPressed() {
        launch()
        val landmark = tile(Situation.LANDMARK)
        val down = SystemClock.uptimeMillis()

        landmark.root.touch(MotionEvent.ACTION_DOWN, down)
        waitMs(PRESSED_WITHIN_MS)
        landmark.root.touch(MotionEvent.ACTION_UP, down)
        idle()

        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertEquals(Situation.LANDMARK, generation.uiState.value.situation)
        assertFalse(sheet.isAdded)
        assertEquals(0.97f, landmark.root.scaleX, 0.001f)
    }

    @Test
    fun aTileThatAsksForConsent_isItselfAgainUnderTheNotice() {
        launch(TestAppContainer().apply { consent.given = false })
        val auto = tile(Situation.DEFAULT)
        val down = SystemClock.uptimeMillis()

        auto.root.touch(MotionEvent.ACTION_DOWN, down)
        waitMs(PRESSED_WITHIN_MS)
        auto.root.touch(MotionEvent.ACTION_UP, down)
        idle()
        waitMs(PRESSED_WITHIN_MS)

        assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        assertEquals(1f, auto.root.scaleX, 0.001f)
        assertEquals(0f, auto.situationDim.alpha, 0.001f)
    }

    @Test
    fun onceATileGoesOn_everyOtherTapOnTheSheetIsIgnored() {
        launch()
        val nav = scenario!!.attachNav(R.id.addGuideSheet)
        val activity = sheet.requireActivity()

        tile(Situation.LANDMARK).root.performClick()
        tile(Situation.HALF_BODY).root.performClick()
        onSheet { binding ->
            binding.libraryRow.performClick()
            binding.galleryRow.performClick()
        }
        idle()

        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertEquals(Situation.LANDMARK, generation.uiState.value.situation)
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test
    fun withoutAFrontCamera_selfiesTile_isOffAndDimmed_andStartsNothing() {
        val modules = launch(TestAppContainer().apply { camera.hasFrontCamera.value = false })
        val selfie = tile(Situation.SELFIE)
        assertFalse(selfie.root.isEnabled)
        assertEquals(0.38f, selfie.root.alpha, 0.001f)

        selfie.root.performClick()
        idle()
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)
        assertTrue(sheet.isAdded)

        modules.camera.hasFrontCamera.value = true
        idle()
        assertTrue(selfie.root.isEnabled)
        assertEquals(1f, selfie.root.alpha, 0.001f)
    }

    @Test
    fun aTile_withConsentGiven_startsTheScenePhotoForItsSituation_andCloses() {
        launch()

        tile(Situation.LANDMARK).root.performClick()
        idle()

        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertEquals(Situation.LANDMARK, generation.uiState.value.situation)
        assertFalse(sheet.isAdded)
    }

    @Test
    fun consentNotice_buttonsKeepMaterialsDialogPadding() {
        launch(TestAppContainer().apply { consent.given = false })

        tile(Situation.DEFAULT).root.performClick()
        idle()

        val notice = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("no consent notice", notice)
        notice!!
        val density = notice.context.resources.displayMetrics.density
        for (which in listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE)) {
            val button = notice.getButton(which)
            assertEquals(12f, button.paddingStart / density, 0.01f)
            assertEquals(12f, button.paddingEnd / density, 0.01f)
        }
    }

    @Test
    fun aTile_withoutConsent_asksFirst_notNowSendsNothing_agreeStartsItsSituation() {
        val modules = TestAppContainer().apply { consent.given = false }
        launch(modules)

        tile(Situation.FULL_BODY).root.performClick()
        idle()

        val notice = ShadowDialog.getLatestDialog() as? AlertDialog
        assertNotNull("no consent notice", notice)
        assertTrue(notice!!.isShowing)
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)

        notice.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)
        assertFalse(modules.consent.given)
        assertTrue(sheet.isAdded)

        tile(Situation.FULL_BODY).root.performClick()
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertTrue(modules.consent.given)
        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertEquals(Situation.FULL_BODY, generation.uiState.value.situation)
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
    fun myLibrary_tappedTwice_opensTheLibraryOnce() {
        val modules = TestAppContainer()
        runBlocking { modules.saved.save(guide(), GuideState()) }
        launch(modules)
        val nav = scenario!!.attachNav(R.id.addGuideSheet)

        onSheet { binding ->
            binding.libraryRow.performClick()
            binding.libraryRow.performClick()
        }
        idle()

        assertEquals(R.id.libraryFragment, nav.currentDestination?.id)
        assertEquals(1, nav.backStack.count { it.destination.id == R.id.libraryFragment })
    }

    @Test
    fun cancel_closesTheSheet() {
        launch()

        onSheet { binding -> binding.cancelButton.performClick() }
        idle()

        assertFalse(sheet.isAdded)
    }
}

/** How soon a pressed tile has its pressed look: the 100 ms asked for, and one display frame. */
private const val PRESSED_WITHIN_MS = 117L
