// AI-generated with Claude Code, 2026-11-04, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.graphics.RectF
import android.view.View
import androidx.activity.ComponentDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.settings.AppLanguage
import com.lastpenguin.pix.settings.AppSettings
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.tipsOver
import com.lastpenguin.pix.ui.ScreenTestSupport.tutorialOver
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.guide.AddGuideSheet
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The tutorial's tips over a live screen (FR-10.4, Design 2.6.8), here over Add a pose guide, the sheet they open over
 * as it opens from Camera. The layer (state-based): a ring around each control the screen shows, where it is; none and
 * no caption for a control the screen does not show (no saved guides); a control that appears while they are up gets
 * its ring, and one that moves takes its ring along; a row of saved guides wider than the screen is ringed as far as
 * it shows (boundary). The captions are text, which TalkBack reads, one per ring.
 * Closing (equivalence partitioning): Back on the dialog closes them and marks the screen seen; showing them while
 * they are there adds no second layer.
 * Whether a screen's tips are due: not while the stored answer loads or once seen, and still when another screen's are
 * seen; not again once closed, before the stored answer has caught up; every screen's again after *Show the tutorial
 * again*, those closed in this run too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class TutorialFragmentTest {
    private var scenario: FragmentScenario<AddGuideSheet>? = null
    private lateinit var modules: TestAppContainer

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Add a pose guide from Camera with its tips due, and [saved] guides kept. */
    private fun openSheet(saved: Int = 1) {
        modules = TestAppContainer(tutorialSeen = emptySet()).install()
        runBlocking { repeat(saved) { modules.saved.save(guide().copy(id = "guide-$it"), GuideState()) } }
        scenario = launchFragment<AddGuideSheet>(
            fragmentArgs = bundleOf(AddGuideSheet.ARG_FROM_SUBJECT to false),
            themeResId = R.style.Theme_Pix,
        )
        idleUntil { tips() != null && layer().placed.isNotEmpty() }
    }

    private fun tips(): TutorialScreen? {
        var shown: TutorialScreen? = null
        scenario!!.onFragment { shown = tutorialOver(it) }
        return shown
    }

    private fun layer(): CoachMarkLayout {
        var layer: CoachMarkLayout? = null
        scenario!!.onFragment { layer = tipsOver(it) }
        return checkNotNull(layer)
    }

    private fun sheetView(): View {
        var view: View? = null
        scenario!!.onFragment { view = it.requireView() }
        return checkNotNull(view)
    }

    /** Where the sheet's control [id] is in the layer's own coordinates. */
    private fun inLayer(id: Int): RectF {
        val control = sheetView().findViewById<View>(id)
        val at = IntArray(2).also(control::getLocationOnScreen)
        val origin = IntArray(2).also(layer()::getLocationOnScreen)
        val left = (at[0] - origin[0]).toFloat()
        val top = (at[1] - origin[1]).toFloat()
        return RectF(left, top, left + control.width, top + control.height)
    }

    /** The ring is around [control], close to it on every side. */
    private fun assertRingAround(ring: RectF, control: RectF) {
        assertTrue("$ring around $control", ring.contains(control))
        assertEquals(control.centerX(), ring.centerX(), 1.5f)
        assertEquals(control.centerY(), ring.centerY(), 1.5f)
        // A few dp around it: a capsule around a wide control, a circle around a round one.
        val longest = max(control.width(), control.height()) + 2 * RING_PAD_PX
        assertTrue("$ring hugs $control", ring.width() <= longest && ring.height() <= longest)
    }

    @Test
    fun eachControlShown_isRinged_whereItIs() {
        openSheet()

        val placed = layer().placed
        assertEquals(3, placed.count { it != null })
        assertRingAround(placed[0]!!.ring, inLayer(R.id.addPhotoTile))
        assertRingAround(placed[1]!!.ring, inLayer(R.id.generateButton))
        assertRingAround(placed[2]!!.ring, inLayer(R.id.savedGuidesList))
    }

    @Test
    fun aRowOfSavedGuidesWiderThanTheScreen_isRingedAsFarAsItShows() {
        openSheet(saved = 10)

        val row = inLayer(R.id.savedGuidesList)
        val shown = inLayer(R.id.savedGuidesScroll)
        assertTrue("$row runs past $shown", row.right > shown.right)
        val saved = RectF(row).apply { intersect(shown) }
        assertRingAround(layer().placed[2]!!.ring, saved)
    }

    @Test
    fun aControlNotShown_getsNoRingOrCaption() {
        openSheet(saved = 0)

        val placed = layer().placed
        assertNull(placed[2])
        assertEquals(2, placed.count { it != null })
        val captions = layer().shownTexts()
        assertFalse(string(R.string.tutorial_saved) in captions)
    }

    @Test
    fun aControlThatAppears_getsItsRingAndCaption() {
        openSheet(saved = 0)

        runBlocking { modules.saved.save(guide(), GuideState()) }
        idleUntil { layer().placed[2] != null }

        assertNotNull(layer().placed[2])
        assertTrue(string(R.string.tutorial_saved) in layer().shownTexts())
    }

    @Test
    fun aControlThatMoves_takesItsRingAlong() {
        openSheet()
        val before = RectF(layer().placed[0]!!.ring)

        sheetView().findViewById<View>(R.id.addPhotoTile).translationY = -60f
        idle()

        assertEquals(before.top - 60f, layer().placed[0]!!.ring.top, 1f)
        assertRingAround(layer().placed[0]!!.ring, inLayer(R.id.addPhotoTile))
    }

    @Test
    fun theCaptions_areTextTalkBackReads_onePerRing() {
        openSheet()

        val expected = listOf(
            R.string.tutorial_upload,
            R.string.tutorial_generate,
            R.string.tutorial_saved,
        ).map(::string)
        assertEquals(expected, layer().placed.map { it!!.caption.text.toString() })
        for (mark in layer().placed) {
            assertTrue(mark!!.caption.isVisible)
            assertTrue(mark.caption.isImportantForAccessibility)
        }
    }

    @Test
    fun back_closesTheTips_andTheScreenIsSeen() {
        openSheet()

        var dialog: ComponentDialog? = null
        scenario!!.onFragment { dialog = TutorialFragment.over(it)!!.requireDialog() as ComponentDialog }
        dialog!!.onBackPressedDispatcher.onBackPressed()
        idle()

        assertNull(tips())
        assertEquals(setOf(TutorialScreen.ADD_GUIDE), modules.appSettings.tutorialSeen.value)
    }

    @Test
    fun showingThemAgain_whileTheyAreThere_addsNoSecondLayer() {
        openSheet()

        scenario!!.onFragment { TutorialFragment.show(it, TutorialScreen.CAMERA) }
        idle()

        assertEquals(TutorialScreen.ADD_GUIDE, tips())
        scenario!!.onFragment { host ->
            assertEquals(1, host.childFragmentManager.fragments.count { it is TutorialFragment })
        }
    }

    @Test
    fun everyScreen_hasTips() {
        assertEquals(TutorialScreen.entries.toSet(), TUTORIAL_TIPS.keys)
        assertTrue(TUTORIAL_TIPS.values.all { it.tips.isNotEmpty() })
    }

    private fun CoachMarkLayout.shownTexts(): List<String> =
        placed.filterNotNull().map { it.caption.text.toString() }

    private fun model(settings: AppSettings) = SettingsViewModel(settings, AppLanguage.InMemory(), FakeAuthRepository())

    @Test
    fun aScreensTips_areDue_untilSeen_andNotWhileTheAnswerLoads() = runBlocking {
        val loading = model(AppSettings.InMemory(tutorial = null))
        assertFalse(loading.tutorialDue(TutorialScreen.ADD_GUIDE).first())

        val seen = model(AppSettings.InMemory(tutorial = setOf(TutorialScreen.ADD_GUIDE)))
        assertFalse(seen.tutorialDue(TutorialScreen.ADD_GUIDE).first())
        assertTrue(seen.tutorialDue(TutorialScreen.GENERATE_POSES).first())

        val none = model(AppSettings.InMemory(tutorial = emptySet()))
        assertTrue(none.tutorialDue(TutorialScreen.ADD_GUIDE).first())
    }

    @Test
    fun onceClosed_aScreensTipsAreNotDue_evenBeforeTheStoredAnswerCatchesUp() = runBlocking {
        val slowStore = object : AppSettings by AppSettings.InMemory(tutorial = emptySet()) {
            override suspend fun setTutorialSeen(screens: Set<TutorialScreen>) = Unit
        }
        val model = model(slowStore)

        model.tutorialSeen(setOf(TutorialScreen.CAMERA))

        assertFalse(model.tutorialDue(TutorialScreen.CAMERA).first())
        assertTrue(model.tutorialDue(TutorialScreen.GUIDE_ON_CAMERA).first())
    }

    @Test
    fun showTheTutorialAgain_makesEveryScreenDue_alsoOnesClosedInThisRun() = runBlocking {
        val model = model(AppSettings.InMemory())
        model.tutorialSeen(setOf(TutorialScreen.CAMERA))

        model.resetTutorial()

        for (screen in TutorialScreen.entries) assertTrue(screen.name, model.tutorialDue(screen).first())
    }

    private companion object {
        /** The ring's 5 dp outside its control at xxhdpi, and a pixel of rounding. */
        const val RING_PAD_PX = 5f * 3 + 1
    }
}
