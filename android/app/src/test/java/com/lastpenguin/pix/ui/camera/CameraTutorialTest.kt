// AI-generated with Claude Code, 2026-11-13, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.closeTips
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.tutorialOver
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.settings.RECHECK_MS
import com.lastpenguin.pix.ui.settings.TutorialFragment
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * When Camera shows its tutorial tips (FR-1.1, FR-10.4, Design 2.6.8), with a real Camera screen and the fakes.
 * Due or not (equivalence partitioning): Camera's tips never seen show; seen, or the stored answer still loading,
 * does not. What must come first (state-based, boundary): a camera that is READY with no picture yet holds it; the
 * preview streaming shows it just after its moment and not just before; a preview that stops during that moment holds
 * it until it shows again; no camera access holds it too. What it must not cover: a session keeps it away, also one
 * that starts during that moment, and it comes once the session has ended; the scene photo keeps it away; another
 * screen in front keeps it away, as an invitation does that a link opened at the first start. Over Camera, not in
 * place of it: Camera stays the screen, started, so the camera and a session's video run on under the tips.
 * The guide's tips: none without a guide; a guide on the camera shows them after the preview's moment, also in a
 * session, and not once seen; with Camera's tips also due, Camera's come first and the guide's once they close.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraTutorialTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var scenario: FragmentScenario<CameraFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(
        seen: Set<TutorialScreen>? = emptySet(),
        camera: FakeCameraController = FakeCameraController(),
        granted: Boolean = true,
        prepare: (TestAppContainer) -> Unit = {},
    ): TestAppContainer {
        if (granted) shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer(camera = camera, tutorialSeen = seen).install()
        prepare(modules)
        val (launched, controller) = launchPrepared<CameraFragment>(R.id.cameraFragment) {}
        scenario = launched
        nav = controller
        idle()
        return modules
    }

    /** The preview's first frame reaches the screen, which a test's camera never does by itself. */
    private fun previewStreams() = previewIs(PreviewView.StreamState.STREAMING)

    private fun previewIs(state: PreviewView.StreamState) {
        scenario!!.onFragment { fragment ->
            val preview = FragmentCameraBinding.bind(fragment.requireView()).previewView
            val field = PreviewView::class.java.getDeclaredField("mPreviewStreamStateLiveData")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(preview) as MutableLiveData<PreviewView.StreamState>).value = state
        }
        idle()
    }

    private fun waitMs(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
    }

    private fun previewStreamsAndItsMomentPasses() {
        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
    }

    private fun tips(): TutorialScreen? {
        var shown: TutorialScreen? = null
        scenario!!.onFragment { shown = tutorialOver(it) }
        return shown
    }

    private fun assertOnCamera() {
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        assertNull(tips())
    }

    private fun assertOnTutorial(screen: TutorialScreen = TutorialScreen.CAMERA) {
        assertEquals(screen, tips())
        // Over Camera, which stays the screen and keeps its camera bound to its started view.
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        scenario!!.onFragment { fragment ->
            assertTrue(fragment.viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
    }

    /** Closes the tips over Camera with a tap anywhere. */
    private fun gotIt() {
        scenario!!.onFragment { closeTips(it) }
        idle()
    }

    @Test
    fun neverSeen_opensIt() {
        launch()
        previewStreamsAndItsMomentPasses()
        assertOnTutorial()
    }

    @Test
    fun seen_doesNotOpenIt() {
        launch(seen = setOf(TutorialScreen.CAMERA))
        previewStreamsAndItsMomentPasses()
        assertOnCamera()
    }

    @Test
    fun whileTheAnswerLoads_itDoesNotOpen() {
        launch(seen = null)
        previewStreamsAndItsMomentPasses()
        assertOnCamera()
    }

    @Test
    fun aReadyCameraWithNoPictureYet_holdsIt_untilThePreviewShows() {
        launch()
        waitMs(TutorialFragment.AFTER_PICTURE_MS * 2)
        assertOnCamera()

        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS - 1)
        assertOnCamera()

        waitMs(1)
        assertOnTutorial()
    }

    @Test
    fun aPreviewThatStopsDuringItsMoment_holdsIt_untilItShowsAgain() {
        launch()
        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS - 1)

        previewIs(PreviewView.StreamState.IDLE)
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertOnCamera()

        previewStreamsAndItsMomentPasses()
        assertOnTutorial()
    }

    @Test
    fun theScenePhotoStep_keepsItAway() {
        launch()
        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS - 1)

        scenario!!.onFragment { fragment ->
            ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[GenerationViewModel::class.java]
                .beginScene()
        }
        idle()
        waitMs(1)

        assertOnCamera()
    }

    @Test
    fun aSessionStartingDuringThePreviewsMoment_keepsItAway() {
        val modules = launch()
        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS - 1)

        connected(modules)
        idle()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)

        assertOnCamera()
    }

    @Test
    fun withoutCameraAccess_itDoesNotOpen() {
        val camera = FakeCameraController().apply { setStatus(CameraStatus.UNAVAILABLE) }
        launch(camera = camera, granted = false)
        waitMs(TutorialFragment.AFTER_PICTURE_MS * 2)
        assertOnCamera()
    }

    @Test
    fun aSession_keepsItAway_andItComesOnceTheSessionHasEnded() {
        val modules = launch(prepare = { connected(it) })
        previewStreamsAndItsMomentPasses()
        assertOnCamera()

        modules.session.setState(SessionState.Ended(EndReason.LEFT))
        idle()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)

        assertOnTutorial()
    }

    private fun connected(modules: TestAppContainer) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
    }

    @Test
    fun anotherScreenInFront_keepsItAway() {
        val modules = launch()
        nav.setCurrentDestination(R.id.settingsFragment)

        previewStreamsAndItsMomentPasses()

        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
        assertNull(tips())
        // Tips shown anyway would cover the screen in front, and this would then miss it.
        connected(modules)
        idle()
        scenario!!.onFragment { fragment ->
            assertTrue(FragmentCameraBinding.bind(fragment.requireView()).endSessionButton.isVisible)
        }
    }

    @Test
    fun anotherScreenInFront_thenClosed_showsTheTips() {
        launch()
        nav.setCurrentDestination(R.id.settingsFragment)
        previewStreamsAndItsMomentPasses()
        assertNull(tips())

        // Nothing the tips wait for changes when the screen in front closes; they check again on their own.
        nav.setCurrentDestination(R.id.cameraFragment)
        waitMs(RECHECK_MS)

        assertOnTutorial(TutorialScreen.CAMERA)
    }

    // ---- The guide's tips -----------------------------------------------------------------

    private fun guideOn(modules: TestAppContainer) {
        modules.guides.setGuide(guide())
        idle()
    }

    @Test
    fun withoutAGuide_theGuidesTipsDoNotShow() {
        launch(seen = setOf(TutorialScreen.CAMERA))
        previewStreamsAndItsMomentPasses()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertOnCamera()
    }

    @Test
    fun aGuideOnTheCamera_showsTheGuidesTips_afterThePreviewsMoment() {
        val modules = launch(seen = setOf(TutorialScreen.CAMERA))
        guideOn(modules)
        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS - 1)
        assertOnCamera()

        waitMs(1)
        assertOnTutorial(TutorialScreen.GUIDE_ON_CAMERA)
    }

    @Test
    fun aHiddenGuide_holdsTheGuidesTips_untilItShows() {
        val modules = launch(seen = setOf(TutorialScreen.CAMERA))
        guideOn(modules)
        modules.guides.update(final = true) { it.copy(visible = false) }
        idle()
        previewStreamsAndItsMomentPasses()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertOnCamera()

        modules.guides.update(final = true) { it.copy(visible = true) }
        idle()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertOnTutorial(TutorialScreen.GUIDE_ON_CAMERA)
    }

    @Test
    fun theGuideHiddenDuringThePreviewsMoment_holdsTheGuidesTips() {
        val modules = launch(seen = setOf(TutorialScreen.CAMERA))
        guideOn(modules)
        previewStreams()
        waitMs(TutorialFragment.AFTER_PICTURE_MS - 1)
        modules.guides.update(final = true) { it.copy(visible = false) }
        idle()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertOnCamera()
    }

    @Test
    fun aGuideInASession_showsTheGuidesTips() {
        val modules = launch(seen = setOf(TutorialScreen.CAMERA), prepare = { connected(it) })
        guideOn(modules)
        previewStreamsAndItsMomentPasses()
        assertOnTutorial(TutorialScreen.GUIDE_ON_CAMERA)
    }

    @Test
    fun theGuidesTipsSeen_doNotShowAgain() {
        val modules = launch(seen = setOf(TutorialScreen.CAMERA, TutorialScreen.GUIDE_ON_CAMERA))
        guideOn(modules)
        previewStreamsAndItsMomentPasses()
        assertOnCamera()
    }

    @Test
    fun bothDue_camerasTipsComeFirst_andTheGuidesOnceTheyClose() {
        val modules = launch()
        guideOn(modules)
        previewStreamsAndItsMomentPasses()
        assertOnTutorial(TutorialScreen.CAMERA)

        gotIt()
        assertNull(tips())
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        assertOnTutorial(TutorialScreen.GUIDE_ON_CAMERA)

        gotIt()
        waitMs(TutorialFragment.AFTER_PICTURE_MS * 2)
        assertOnCamera()
        assertEquals(
            setOf(TutorialScreen.CAMERA, TutorialScreen.GUIDE_ON_CAMERA),
            modules.appSettings.tutorialSeen.value,
        )
    }
}
