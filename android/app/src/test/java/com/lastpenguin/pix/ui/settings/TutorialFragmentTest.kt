// AI-generated with Claude Code, 2026-11-02, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.app.Application
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.account.FakeAuthRepository
import com.lastpenguin.pix.databinding.FragmentTutorialBinding
import com.lastpenguin.pix.settings.AppLanguage
import com.lastpenguin.pix.settings.AppSettings
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * The first-use tutorial (FR-10.4, Design 2.6.8).
 * Going through it (state-based): the first slide with *Skip* and *Next*; *Next* to each slide, the dots following; the
 * last slide with *Start* and no *Skip*.
 * Closing it (equivalence partitioning): *Start*, *Skip*, and system Back each mark it seen and go back.
 * TalkBack: the row of dots says the slide and all its captions, and changes with the slide.
 * The drawings (consistency): every slide's image exists in English and Korean, nothing else is there, and each drawing
 * holds every caption TalkBack reads, since the words on a slide are pixels.
 * Whether it is due: not while the stored answer loads, and not again once closed, before the stored answer has
 * caught up.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TutorialFragmentTest {
    private var scenario: FragmentScenario<TutorialFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer(tutorialSeen = false).install()
        val (launched, controller) = launchPrepared<TutorialFragment>(R.id.tutorialFragment) {}
        scenario = launched
        nav = controller
        return modules
    }

    private fun <T> onScreen(block: (FragmentTutorialBinding) -> T): T {
        var result: Result<T>? = null
        scenario!!.onFragment { result = runCatching { block(FragmentTutorialBinding.bind(it.requireView())) } }
        idle()
        return checkNotNull(result).getOrThrow()
    }

    private fun FragmentTutorialBinding.selectedDots() =
        (0 until dots.childCount).filter { dots.getChildAt(it).isSelected }

    private fun toLastSlide() {
        repeat(TUTORIAL_SLIDES.lastIndex) { onScreen { it.nextButton.performClick() } }
    }

    @Test
    fun theFirstSlide_offersSkipAndNext() {
        launch()

        onScreen { binding ->
            assertEquals(0, binding.slides.currentItem)
            assertEquals(TUTORIAL_SLIDES.size, binding.dots.childCount)
            assertEquals(listOf(0), binding.selectedDots())
            assertTrue(binding.skipButton.isVisible)
            assertEquals(string(R.string.tutorial_next), binding.nextButton.text.toString())
        }
    }

    @Test
    fun next_goesThroughTheSlides_andTheLastOneOffersStart() {
        launch()

        for (slide in 1..TUTORIAL_SLIDES.lastIndex) {
            onScreen { it.nextButton.performClick() }
            onScreen { binding ->
                assertEquals(slide, binding.slides.currentItem)
                assertEquals(listOf(slide), binding.selectedDots())
            }
        }

        onScreen { binding ->
            assertEquals(string(R.string.tutorial_start), binding.nextButton.text.toString())
            assertTrue(binding.skipButton.isInvisible)
        }
    }

    @Test
    fun start_closesIt_andItIsSeen() {
        val modules = launch()
        toLastSlide()

        onScreen { it.nextButton.performClick() }

        assertNotEquals(R.id.tutorialFragment, nav.currentDestination?.id)
        assertEquals(true, modules.appSettings.tutorialSeen.value)
    }

    @Test
    fun next_beforeTheLastSlide_doesNotClose() {
        val modules = launch()

        onScreen { it.nextButton.performClick() }

        assertEquals(R.id.tutorialFragment, nav.currentDestination?.id)
        assertEquals(false, modules.appSettings.tutorialSeen.value)
    }

    @Test
    fun skip_closesIt_andItIsSeen() {
        val modules = launch()

        onScreen { it.skipButton.performClick() }

        assertNotEquals(R.id.tutorialFragment, nav.currentDestination?.id)
        assertEquals(true, modules.appSettings.tutorialSeen.value)
    }

    @Test
    fun back_closesIt_andItIsSeen() {
        val modules = launch()

        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        assertNotEquals(R.id.tutorialFragment, nav.currentDestination?.id)
        assertEquals(true, modules.appSettings.tutorialSeen.value)
    }

    @Test
    fun talkBack_readsTheSlideItIsOn() {
        launch()

        val first = onScreen { it.dots.contentDescription.toString() }
        assertTrue(first, first.startsWith(string(R.string.tutorial_page, 1, TUTORIAL_SLIDES.size)))
        assertTrue(first, TUTORIAL_SLIDES[0].captions.all { string(it) in first })

        onScreen { it.nextButton.performClick() }
        val second = onScreen { it.dots.contentDescription.toString() }
        assertTrue(second, second.startsWith(string(R.string.tutorial_page, 2, TUTORIAL_SLIDES.size)))
        assertTrue(second, TUTORIAL_SLIDES[1].captions.all { string(it) in second })
    }

    private fun slideNames() = TUTORIAL_SLIDES.map {
        ApplicationProvider.getApplicationContext<Application>().resources.getResourceEntryName(it.image)
    }

    private fun images(folder: String) =
        File("src/main/res/$folder").list().orEmpty().map { it.removeSuffix(".webp") }.sorted()

    @Test
    fun everySlide_isDrawnInBothLanguages() {
        assertEquals(slideNames().sorted(), images("drawable-nodpi"))
        assertEquals(slideNames().sorted(), images("drawable-ko-nodpi"))
    }

    @Test
    fun everyDrawing_holdsWhatTalkBackReads() {
        for ((slide, name) in TUTORIAL_SLIDES.zip(slideNames())) {
            val drawing = File("../../wiki/design/tutorial/$name.dc.html").readText()
            for (caption in slide.captions) {
                assertTrue("$name lacks ${string(caption)}", ">${string(caption)}<" in drawing)
            }
        }
    }

    @Test
    fun itIsNotDue_whileTheAnswerLoads_orOnceSeen() = runBlocking {
        val auth = FakeAuthRepository()
        val loading = SettingsViewModel(AppSettings.InMemory(tutorial = null), AppLanguage.InMemory(), auth)
        assertFalse(loading.tutorialDue.first())

        val seen = SettingsViewModel(AppSettings.InMemory(tutorial = true), AppLanguage.InMemory(), auth)
        assertFalse(seen.tutorialDue.first())

        val notSeen = SettingsViewModel(AppSettings.InMemory(tutorial = false), AppLanguage.InMemory(), auth)
        assertTrue(notSeen.tutorialDue.first())
    }

    @Test
    fun onceClosed_itIsNotDue_evenBeforeTheStoredAnswerCatchesUp() = runBlocking {
        val slowStore = object : AppSettings by AppSettings.InMemory(tutorial = false) {
            override suspend fun setTutorialSeen() = Unit
        }
        val model = SettingsViewModel(slowStore, AppLanguage.InMemory(), FakeAuthRepository())

        model.tutorialFinished()

        assertFalse(model.tutorialDue.first())
    }
}
