// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.Manifest
import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.guide.GuideColor
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.session.ShadowSurfaceViewRenderer
import com.lastpenguin.pix.ui.session.SubjectFragment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * What the settings change on the camera screens (FR-10.1, FR-10.3, FR-3.10). Partitions for the camera menu's Settings
 * (FR-1.17): the menu's button shown on the camera, also during a session (connected or reconnecting, #204), and
 * during the scene photo without Settings (#210), and Settings in the menu opening that screen and closing the menu; on Subject view, the same
 * item opening Settings; Settings over Subject view goes back to it when the session ends, and over the camera stays.
 * For the menu's grid and horizon bar: each tap turns the setting off and on again, on the overlay and in the item's
 * accent, on both screens (the horizon bar on the camera only). For the latency indicator: off (even in a
 * session), on without a session, connected with no number yet, with a delay at each color's edge (150 and 300 ms) and
 * above, reconnecting, and during the scene photo; on Subject view, off and on. For the guide: the color of the
 * settings on the overlay, on both screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SettingsOnScreensTest {
    private var camera: FragmentScenario<CameraFragment>? = null
    private var subject: FragmentScenario<SubjectFragment>? = null
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        camera?.close()
        subject?.close()
    }

    private fun launchCamera(): TestAppContainer {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer().install()
        camera = launchFragmentInContainer<CameraFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun launchSubject(): TestAppContainer {
        val modules = TestAppContainer().install()
        subject = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun <T> onCamera(block: (FragmentCameraBinding, CameraFragment) -> T): T {
        var result: Result<T>? = null
        camera!!.onFragment { result = runCatching { block(FragmentCameraBinding.bind(it.requireView()), it) } }
        idle()
        return checkNotNull(result).getOrThrow()
    }

    private fun <T> onSubject(block: (FragmentSubjectBinding) -> T): T {
        var result: Result<T>? = null
        subject!!.onFragment { result = runCatching { block(FragmentSubjectBinding.bind(it.requireView())) } }
        idle()
        return checkNotNull(result).getOrThrow()
    }

    private fun TestAppContainer.showLatency() = runBlocking { appSettings.setShowLatency(true) }

    private fun TextView.dotColor(): Int? = TextViewCompat.getCompoundDrawableTintList(this)?.defaultColor

    private fun color(id: Int): Int = ContextCompat.getColor(app, id)

    private val connected = SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)

    // ---- The camera menu's Settings (FR-10.1, FR-1.17) --------------------------------------------------------------

    @Test
    fun theMenusSettings_opensSettings_andTheMenuStaysDuringASession_andDuringTheScenePhotoWithoutSettings() {
        val modules = launchCamera()
        val nav = camera!!.attachNav(R.id.cameraFragment)

        onCamera { controls, _ -> controls.cameraMenuButton.performClick() }
        onCamera { controls, _ -> controls.menuSettings.performClick() }
        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
        onCamera { controls, _ -> assertEquals(View.GONE, controls.cameraMenu.visibility) }

        modules.session.setState(connected)
        idle()
        onCamera { controls, _ ->
            assertEquals(View.VISIBLE, controls.cameraMenuButton.visibility)
            assertEquals(View.VISIBLE, controls.endSessionButton.visibility)
        }
        modules.session.setState(SessionState.Reconnecting("s1", since = 0L))
        idle()
        onCamera { controls, _ -> assertEquals(View.VISIBLE, controls.cameraMenuButton.visibility) }
        modules.session.setState(SessionState.Idle)
        idle()
        onCamera { controls, _ -> assertEquals(View.VISIBLE, controls.cameraMenuButton.visibility) }

        // The scene photo keeps the menu, without Settings, which would leave the step (#210).
        onCamera { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        idle()
        onCamera { controls, _ ->
            assertEquals(View.VISIBLE, controls.cameraMenuButton.visibility)
            assertEquals(View.GONE, controls.menuSettings.visibility)
        }
    }

    @Test
    fun theMenusSettings_onSubjectView_opensSettings() {
        val modules = launchSubject()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.SUBJECT))
        idle()
        val nav = subject!!.attachNav(R.id.subjectFragment)

        onSubject { it.cameraMenuButton.performClick() }
        onSubject { assertEquals(View.VISIBLE, it.menuSettings.visibility) }
        onSubject { it.menuSettings.performClick() }
        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
    }

    // ---- The camera menu's grid and horizon bar (FR-1.9, FR-1.10, FR-1.17) ------------------------------------------

    @Test
    fun theMenusGridAndHorizonBar_onCamera_turnTheSettingsOffAndOnAgain() {
        val modules = launchCamera()
        onCamera { controls, _ -> controls.cameraMenuButton.performClick() }
        onCamera { controls, _ ->
            assertTrue(controls.menuGrid.isActivated)
            assertTrue(controls.menuLevel.isActivated)
            controls.menuGrid.performClick()
            controls.menuLevel.performClick()
        }

        onCamera { controls, _ ->
            assertFalse(modules.appSettings.showGrid.value)
            assertFalse(modules.appSettings.showLevel.value)
            assertFalse(controls.compositionOverlay.showGrid)
            assertFalse(controls.compositionOverlay.showLevel)
            assertFalse(controls.menuGrid.isActivated)
            assertEquals(string(R.string.grid_off), controls.menuGrid.contentDescription)
            assertEquals(string(R.string.level_off), controls.menuLevel.contentDescription)
            // A choice keeps the menu open.
            assertEquals(View.VISIBLE, controls.cameraMenu.visibility)
            controls.menuGrid.performClick()
        }
        onCamera { controls, _ ->
            assertTrue(modules.appSettings.showGrid.value)
            assertTrue(controls.compositionOverlay.showGrid)
            assertTrue(controls.menuGrid.isActivated)
        }
    }

    @Test
    fun theMenusGrid_onSubjectView_turnsTheGridOff() {
        val modules = launchSubject()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.SUBJECT))
        idle()

        onSubject { it.cameraMenuButton.performClick() }
        onSubject { it.menuGrid.performClick() }

        onSubject {
            assertFalse(modules.appSettings.showGrid.value)
            assertFalse(it.compositionOverlay.showGrid)
            assertFalse(it.menuGrid.isActivated)
        }
    }

    @Test
    fun settings_overSubjectView_goesBackToItWhenTheSessionEnds() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.SUBJECT))
        val (scenario, nav) = launchPrepared<SettingsFragment>(R.id.subjectFragment) {}
        nav.navigate(R.id.action_subject_to_settings)
        idle()
        try {
            assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
            modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
            idle()
            assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun settings_overTheCamera_staysWhenTheSessionEnds() {
        val modules = TestAppContainer().install()
        modules.session.setState(connected)
        val (scenario, nav) = launchPrepared<SettingsFragment>(R.id.cameraFragment) {}
        nav.navigate(R.id.action_camera_to_settings)
        idle()
        try {
            modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
            idle()
            assertEquals(R.id.settingsFragment, nav.currentDestination?.id)
        } finally {
            scenario.close()
        }
    }

    // ---- The latency indicator (FR-10.3) --------------------------------------------------------------------------

    @Test
    fun latency_offByDefault_evenInASession() {
        val modules = launchCamera()
        modules.session.setState(connected)
        modules.session.setLatency(120)
        idle()

        onCamera { controls, _ -> assertEquals(View.GONE, controls.latencyChip.visibility) }
    }

    @Test
    fun latency_on_showsTheDelayInASessionOnly_coloredByHowLong() {
        val modules = launchCamera()
        modules.showLatency()
        idle()
        onCamera { controls, _ -> assertEquals(View.GONE, controls.latencyChip.visibility) }

        modules.session.setState(connected)
        idle()
        onCamera { controls, _ ->
            assertEquals(View.VISIBLE, controls.latencyChip.visibility)
            assertEquals(string(R.string.latency_unknown), controls.latencyChip.text.toString())
            assertEquals(color(R.color.pix_text_faint), controls.latencyChip.dotColor())
        }

        for ((ms, dot) in listOf(150 to R.color.pix_live, 300 to R.color.pix_accent, 301 to R.color.pix_danger)) {
            modules.session.setLatency(ms)
            idle()
            onCamera { controls, _ ->
                assertEquals(string(R.string.latency_value, ms), controls.latencyChip.text.toString())
                assertEquals(color(dot), controls.latencyChip.dotColor())
            }
        }

        // The number is the old link's while reconnecting, so only the dash shows.
        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        idle()
        onCamera { controls, _ ->
            assertEquals(View.VISIBLE, controls.latencyChip.visibility)
            assertEquals(string(R.string.latency_unknown), controls.latencyChip.text.toString())
        }
    }

    @Test
    fun latency_givesWayToTheScenePhotosTitle() {
        val modules = launchCamera()
        modules.showLatency()
        modules.session.setState(connected)
        modules.session.setLatency(80)
        idle()

        onCamera { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        onCamera { controls, _ ->
            assertEquals(View.VISIBLE, controls.sceneTitle.visibility)
            assertEquals(View.GONE, controls.latencyChip.visibility)
        }

        onCamera { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().cancel() }
        onCamera { controls, _ -> assertEquals(View.VISIBLE, controls.latencyChip.visibility) }
    }

    @Test
    fun latency_onSubjectView_followsTheSetting() {
        val modules = launchSubject()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.session.setLatency(210)
        idle()
        onSubject { assertEquals(View.GONE, it.latencyChip.visibility) }

        modules.showLatency()
        idle()
        onSubject { binding ->
            assertEquals(View.VISIBLE, binding.latencyChip.visibility)
            assertEquals(string(R.string.latency_value, 210), binding.latencyChip.text.toString())
        }
    }

    // ---- The guide's look (FR-3.10) -------------------------------------------------------------------------------

    @Test
    fun theGuide_isDrawnInTheSettingsColor_onCameraAndSubjectView() {
        val modules = launchCamera()
        modules.guides.setGuide(guide())
        runBlocking { modules.appSettings.setGuideColor(GuideColor.YELLOW) }
        idle()
        onCamera { controls, _ -> assertEquals(GuideColor.YELLOW.argb, controls.guideOverlay.lineColor) }
        camera?.close()
        camera = null

        val subjectModules = launchSubject()
        runBlocking { subjectModules.appSettings.setGuideColor(GuideColor.BLACK) }
        idle()
        onSubject { assertEquals(GuideColor.BLACK.argb, it.guideOverlay.lineColor) }
    }
}
