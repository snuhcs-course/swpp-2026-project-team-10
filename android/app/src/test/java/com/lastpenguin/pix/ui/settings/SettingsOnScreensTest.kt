// AI-generated with Claude Code, 2026-11-02, reviewed by Sungmin Jo
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
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * What the settings change on the camera screens (FR-10.1, FR-10.3, FR-3.10). Partitions for the gear: shown on the
 * camera, hidden during a session and the scene photo, and opening Settings. For the latency indicator: off (even in a
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

    private val connected = SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER)

    // ---- The gear (FR-10.1) ----------------------------------------------------------------------------------------

    @Test
    fun theGear_opensSettings_andHidesDuringASessionAndTheScenePhoto() {
        val modules = launchCamera()
        val nav = camera!!.attachNav(R.id.cameraFragment)

        onCamera { controls, _ -> assertEquals(View.VISIBLE, controls.settingsButton.visibility) }
        onCamera { controls, _ -> controls.settingsButton.performClick() }
        assertEquals(R.id.settingsFragment, nav.currentDestination?.id)

        modules.session.setState(connected)
        idle()
        onCamera { controls, _ -> assertEquals(View.GONE, controls.settingsButton.visibility) }
        modules.session.setState(SessionState.Idle)
        idle()
        onCamera { controls, _ -> assertEquals(View.VISIBLE, controls.settingsButton.visibility) }

        onCamera { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        onCamera { controls, _ -> assertEquals(View.GONE, controls.settingsButton.visibility) }
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
