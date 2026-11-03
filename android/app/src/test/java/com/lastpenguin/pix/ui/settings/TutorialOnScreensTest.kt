// AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.settings

import android.os.Looper
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.fakeBitmap
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.sceneBegin
import com.lastpenguin.pix.session.sceneChunk
import com.lastpenguin.pix.session.sceneEnd
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.closeTips
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.tutorialOver
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.DescribePoseFragment
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.guide.AddGuideSheet
import com.lastpenguin.pix.ui.session.ShadowSurfaceViewRenderer
import com.lastpenguin.pix.ui.session.SubjectDescribeFragment
import com.lastpenguin.pix.ui.session.SubjectFragment
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * When the tutorial's tips show on the screens other than Camera (FR-10.4, Design 2.6.8; Camera's in
 * CameraTutorialTest), on the real screens with the fakes. Per screen (equivalence partitioning): its tips due show
 * over it; its tips seen, with the others still due, do not.
 * Add a pose guide: over the sheet as it opens from Camera, which stays open under them and is there once they close;
 * none from Subject view, whose sheet has no poses.
 * Generate poses: over it as it opens, on the photographer's phone and on the subject's; a set the other phone starts
 * goes on under them; the screen's view going takes the tips along, unseen, and they come back with the view.
 * Subject view (state-based): none while connecting or before the first frame; over the video a moment after the video
 * shows, Subject view staying the screen; none while another screen, such as Add a pose guide, is in front.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class TutorialOnScreensTest {
    private var scenario: FragmentScenario<out Fragment>? = null
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    private fun tips(): TutorialScreen? {
        var shown: TutorialScreen? = null
        scenario!!.onFragment { shown = tutorialOver(it) }
        return shown
    }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    private fun gotIt() {
        scenario!!.onFragment { closeTips(it) }
        idle()
    }

    /** A camera whose frame is a scene photo, for Generate poses. */
    private fun camera() = FakeCameraController().apply { frame = bitmap(300, 400) }

    private fun noneSeen() = TestAppContainer(camera = camera(), tutorialSeen = emptySet())

    /** Only [screen]'s tips seen, so the others are still due. */
    private fun seenOnly(screen: TutorialScreen) = TestAppContainer(camera = camera(), tutorialSeen = setOf(screen))

    // ---- Add a pose guide -----------------------------------------------------------------------------------------

    private fun openSheet(modules: TestAppContainer, fromSubject: Boolean = false): TestAppContainer {
        modules.install()
        scenario = launchFragment<AddGuideSheet>(
            fragmentArgs = bundleOf(AddGuideSheet.ARG_FROM_SUBJECT to fromSubject),
            themeResId = R.style.Theme_Pix,
        )
        idle()
        return modules
    }

    private fun assertSheetOpen() {
        scenario!!.onFragment { sheet -> assertTrue((sheet as AddGuideSheet).requireDialog().isShowing) }
    }

    @Test
    fun addAPoseGuide_fromCamera_showsItsTipsOverTheSheet_whichStaysOpen() {
        val modules = openSheet(noneSeen())

        assertEquals(TutorialScreen.ADD_GUIDE, tips())
        assertSheetOpen()

        gotIt()
        assertNull(tips())
        assertSheetOpen()
        assertEquals(setOf(TutorialScreen.ADD_GUIDE), modules.appSettings.tutorialSeen.value)
    }

    @Test
    fun addAPoseGuide_seen_showsNoTips() {
        openSheet(seenOnly(TutorialScreen.ADD_GUIDE))
        assertNull(tips())
    }

    @Test
    fun addAPoseGuide_fromSubjectView_showsNoTips() {
        openSheet(noneSeen(), fromSubject = true)
        assertNull(tips())
    }

    // ---- Generate poses -------------------------------------------------------------------------------------------

    private fun describe(modules: TestAppContainer): TestNavHostController {
        modules.install()
        val (launched, nav) = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { activity ->
            activity.viewModel<GenerationViewModel>().apply {
                beginScene()
                takeScene()
                useScene()
            }
        }
        scenario = launched
        idle()
        return nav
    }

    @Test
    fun generatePoses_showsItsTipsAsItOpens() {
        val nav = describe(noneSeen())

        assertEquals(TutorialScreen.GENERATE_POSES, tips())
        assertEquals(R.id.describePoseFragment, nav.currentDestination?.id)
    }

    @Test
    fun generatePoses_seen_showsNoTips() {
        describe(seenOnly(TutorialScreen.GENERATE_POSES))
        assertNull(tips())
    }

    @Test
    fun generatePoses_aSetStartedUnderTheTips_goesOn() {
        val nav = describe(noneSeen())
        assertEquals(TutorialScreen.GENERATE_POSES, tips())

        // As the subject's Generate 4 poses does in a session: the set starts, and the screen goes on by itself.
        scenario!!.onFragment { it.requireActivity().viewModel<GenerationViewModel>().generate("wave") }
        idle()

        assertEquals(R.id.generatingFragment, nav.currentDestination?.id)
    }

    @Test
    fun generatePoses_leftUnderTheTips_takesThemAlong_unseen_andBringsThemBack() {
        val modules = noneSeen()
        describe(modules)
        assertEquals(TutorialScreen.GENERATE_POSES, tips())

        // The screen's view goes, as when the screen after it opens, and comes back, as on Back.
        scenario!!.moveToState(Lifecycle.State.CREATED)
        idle()
        assertFalse(TutorialScreen.GENERATE_POSES in modules.appSettings.tutorialSeen.value.orEmpty())

        scenario!!.moveToState(Lifecycle.State.RESUMED)
        idle()
        assertEquals(TutorialScreen.GENERATE_POSES, tips())
    }

    @Test
    fun generatePoses_onTheSubjectsPhone_showsItsTips() {
        val modules = noneSeen().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.descriptions.startAsReceiver(receiving)
        idle()
        modules.session.deliver(SessionMessage.PoseDescription("d1", DescriptionStep.OPEN, "", null))
        val copy = checkNotNull(FakePoseCandidateCodec().encode("d1", fakeBitmap()))
        modules.session.deliver(copy.sceneBegin("d1"))
        for (i in copy.chunks.indices) modules.session.deliver(copy.sceneChunk("d1", i))
        modules.session.deliver(copy.sceneEnd("d1"))
        idle()
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.setGraph(R.navigation.nav_graph)
        nav.setCurrentDestination(R.id.subjectFragment)
        nav.navigate(R.id.action_subject_to_describe)
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_Pix) {
            SubjectDescribeFragment().also { fragment ->
                fragment.viewLifecycleOwnerLiveData.observeForever { owner ->
                    if (owner != null) Navigation.setViewNavController(fragment.requireView(), nav)
                }
            }
        }
        idle()

        assertEquals(TutorialScreen.GENERATE_POSES, tips())
        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
    }

    // ---- Subject view ---------------------------------------------------------------------------------------------

    private fun subjectView(modules: TestAppContainer): TestNavHostController {
        modules.install()
        val launched = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        scenario = launched
        val nav = launched.attachNav(R.id.subjectFragment)
        idle()
        return nav
    }

    /** Connected, with the photographer's first frame or not; the screen checks its video every 500 ms. */
    private fun connect(modules: TestAppContainer, firstFrame: Boolean = true) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        if (firstFrame) modules.video.deliverFrame()
        waitMs(600)
    }

    @Test
    fun subjectView_showsItsTips_aMomentAfterTheVideoShows() {
        val modules = noneSeen()
        val nav = subjectView(modules)
        waitMs(TutorialFragment.AFTER_PICTURE_MS * 2)
        assertNull(tips())

        connect(modules, firstFrame = false)
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertNull(tips())

        modules.video.deliverFrame()
        waitMs(500 + TutorialFragment.AFTER_PICTURE_MS)
        assertEquals(TutorialScreen.SUBJECT_VIEW, tips())
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    @Test
    fun subjectView_seen_showsNoTips() {
        val modules = seenOnly(TutorialScreen.SUBJECT_VIEW)
        subjectView(modules)
        connect(modules)
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertNull(tips())
    }

    @Test
    fun subjectView_withAnotherScreenInFront_showsNoTips() {
        val modules = noneSeen()
        val nav = subjectView(modules)
        nav.navigate(R.id.action_subject_to_addGuide)

        connect(modules)
        waitMs(TutorialFragment.AFTER_PICTURE_MS)

        assertNull(tips())
    }
}
