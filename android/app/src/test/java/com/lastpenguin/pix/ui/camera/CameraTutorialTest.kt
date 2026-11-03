// AI-generated with Claude Code, 2026-11-02, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.os.Looper
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.FragmentScenario
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
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * When Camera opens the first-use tutorial (FR-1.1, FR-10.4, Design 2.6.8), with a real Camera screen and the fakes.
 * Due or not (equivalence partitioning): never seen opens it; seen, or the stored answer still loading, does not.
 * What must come first (state-based, boundary): a camera that is READY with no picture yet holds it, since leaving then
 * would release the camera before its first frame; the preview streaming opens it just after its moment and not just
 * before; a preview that stops during that moment holds it until it shows again; no camera access holds it too.
 * What it must not cover: a session keeps it away, also one that starts during that moment, and it comes once the
 * session has ended; the scene photo keeps it away; another screen in front keeps it away, as an invitation does that a
 * link opened at the first start.
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
        seen: Boolean? = false,
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
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS)
    }

    private fun assertOnCamera() = assertEquals(R.id.cameraFragment, nav.currentDestination?.id)

    private fun assertOnTutorial() = assertEquals(R.id.tutorialFragment, nav.currentDestination?.id)

    @Test
    fun neverSeen_opensIt() {
        launch()
        previewStreamsAndItsMomentPasses()
        assertOnTutorial()
    }

    @Test
    fun seen_doesNotOpenIt() {
        launch(seen = true)
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
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS * 2)
        assertOnCamera()

        previewStreams()
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS - 1)
        assertOnCamera()

        waitMs(1)
        assertOnTutorial()
    }

    @Test
    fun aPreviewThatStopsDuringItsMoment_holdsIt_untilItShowsAgain() {
        launch()
        previewStreams()
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS - 1)

        previewIs(PreviewView.StreamState.IDLE)
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS)
        assertOnCamera()

        previewStreamsAndItsMomentPasses()
        assertOnTutorial()
    }

    @Test
    fun theScenePhotoStep_keepsItAway() {
        launch()
        previewStreams()
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS - 1)

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
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS - 1)

        connected(modules)
        idle()
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS)

        assertOnCamera()
    }

    @Test
    fun withoutCameraAccess_itDoesNotOpen() {
        val camera = FakeCameraController().apply { setStatus(CameraStatus.UNAVAILABLE) }
        launch(camera = camera, granted = false)
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS * 2)
        assertOnCamera()
    }

    @Test
    fun aSession_keepsItAway_andItComesOnceTheSessionHasEnded() {
        val modules = launch(prepare = { connected(it) })
        previewStreamsAndItsMomentPasses()
        assertOnCamera()

        modules.session.setState(SessionState.Ended(EndReason.LEFT))
        idle()
        waitMs(CameraFragment.TUTORIAL_AFTER_PREVIEW_MS)

        assertOnTutorial()
    }

    private fun connected(modules: TestAppContainer) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
    }

    @Test
    fun anotherScreenInFront_keepsItAway() {
        val modules = launch()
        nav.setCurrentDestination(R.id.settingsFragment)

        previewStreamsAndItsMomentPasses()

        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
        // An action Camera's position does not have would throw and stop the screen, which this would then miss.
        connected(modules)
        idle()
        scenario!!.onFragment { fragment ->
            assertTrue(FragmentCameraBinding.bind(fragment.requireView()).endSessionButton.isVisible)
        }
    }
}
