// AI-generated with Claude Code, 2026-10-26, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.session

import android.os.Looper
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/*
 * Subject view's render paths (R&S 6.6, Testing Documentation 2.1.6), on the real Fragment under FragmentScenario
 * with a container of fakes. Partitions: not connected / connected before the first frame / streaming / frames
 * stopped for 2 s ("camera paused") / streaming again; the photographer's capabilities with and
 * without a flash and an exposure range; the camera state's zoom, flash, and ratio; a guide arriving, the edit-mode
 * toggle, a hidden guide; the brightness panel and a flash tap; Leave, the back button's question, and the session
 * ending by a lost connection or the photographer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SubjectFragmentRenderTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentSubjectBinding, SubjectFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result =
                block(FragmentSubjectBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    private fun connect(
        modules: TestAppContainer,
        name: String = "Dongje",
        exposureRange: IntRange = 0..0,
        hasFlash: Boolean = false,
        firstFrame: Boolean = true,
    ) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo(name), Role.SUBJECT))
        if (firstFrame) modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(
                CameraCapabilities(0.6f, 10f, listOf(1f, 2f), exposureRange, 0.5f, hasFlash),
            ),
        )
        // The "Connecting…" check runs every 500 ms on the main looper; let one more round see the session.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    @Test
    fun notConnected_showsConnectingAndDisablesTheControls() {
        launch()

        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.liveDot.visibility)
            assertEquals(string(R.string.subject_title_placeholder), binding.liveDot.contentDescription.toString())
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.connecting), binding.connectingText.text.toString())
            assertEquals(View.GONE, binding.compositionOverlay.visibility)
            assertFalse(binding.zoomReadout.isEnabled)
            assertEquals(View.GONE, binding.editGuideButton.visibility)
            assertEquals(View.GONE, binding.flashButton.visibility)
            assertEquals(View.GONE, binding.brightnessButton.visibility)
        }
    }

    @Test
    fun connecting_staysUntilTheFirstFrame_andPausedOnlyWhenFramesStop() {
        val modules = launch()

        connect(modules, firstFrame = false)
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.connecting), binding.connectingText.text.toString())
        }

        modules.video.deliverFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ -> assertEquals(View.GONE, binding.connectingText.visibility) }

        // No frame for longer than the 2 s stall limit: the camera is paused.
        Thread.sleep(2_100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.camera_paused), binding.connectingText.text.toString())
        }

        modules.video.deliverFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ -> assertEquals(View.GONE, binding.connectingText.visibility) }
    }

    @Test
    fun connected_showsTheLiveLightTheGridAndTheCapabilities() {
        val modules = launch()

        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.liveDot.visibility)
            assertEquals(string(R.string.subject_title, "Dongje"), binding.liveDot.contentDescription.toString())
            assertEquals(View.GONE, binding.connectingText.visibility)
            assertEquals(View.VISIBLE, binding.compositionOverlay.visibility)
            assertTrue(binding.zoomReadout.isEnabled)
            assertEquals(View.VISIBLE, binding.flashButton.visibility)
            assertTrue(binding.flashButton.isEnabled)
            assertEquals(View.VISIBLE, binding.brightnessButton.visibility)
            assertEquals(View.GONE, binding.brightnessPanel.visibility)
        }
    }

    @Test
    fun cameraState_updatesTheReadoutTheFlashAndTheFrame() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        modules.session.deliver(
            SessionMessage.CameraStateUpdate(
                zoom = 2f,
                by = Role.PHOTOGRAPHER,
                final = true,
                exposure = 2,
                flash = FlashMode.AUTO,
                aspect = AspectRatio.R16_9,
            ),
        )
        idle()

        onScreen { binding, _ ->
            assertEquals(string(R.string.zoom_ratio, "2.0"), binding.zoomReadout.text.toString())
            assertEquals(string(R.string.flash_auto), binding.flashButton.contentDescription)
            assertEquals(AspectRatio.R16_9.frameAspect, binding.compositionOverlay.frameAspect)
            assertEquals(AspectRatio.R16_9.frameAspect, binding.guideOverlay.frameAspect)
            assertEquals("+1.0", binding.brightnessValue.text.toString())
        }
    }

    @Test
    fun guideArrives_showsTheToggleOff_andATapTurnsEditingOn() {
        val modules = launch()
        connect(modules)

        modules.mirror.setGuide(guide(withReference = false))
        idle()

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.editGuideButton.visibility)
            assertTrue(binding.editGuideButton.isEnabled)
            assertFalse(binding.editGuideButton.isChecked)
            assertFalse(binding.guideOverlay.editable)
            binding.editGuideButton.performClick()
        }
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.editGuideButton.isChecked)
            assertTrue(binding.guideOverlay.editable)
        }

        // The photographer hides the guide: it cannot be edited here until it is shown again (FR-7.2).
        modules.mirror.update(final = true) { it.copy(visible = false) }
        idle()

        onScreen { binding, _ ->
            assertFalse(binding.editGuideButton.isEnabled)
            assertFalse(binding.editGuideButton.isChecked)
            assertFalse(binding.guideOverlay.editable)
        }
    }

    @Test
    fun brightnessButton_opensThePanel_andTheFlashTapSendsARequest() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ ->
            binding.brightnessButton.performClick()
            binding.flashButton.performClick()
        }
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.brightnessButton.isChecked)
            assertEquals(View.VISIBLE, binding.brightnessPanel.visibility)
            assertTrue(binding.brightnessSlider.isEnabled)
        }
        assertTrue(modules.session.sent.any { it == SessionMessage.FlashSet(FlashMode.AUTO) })
    }

    @Test
    fun leave_returnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        onScreen { binding, _ -> binding.leaveButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun back_asksBeforeLeaving() {
        val modules = launch()
        connect(modules)

        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
    }

    @Test
    fun connectionLost_opensConnectionLost() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.connectionLostFragment, nav.currentDestination?.id)
    }

    @Test
    fun photographerEndsTheSession_saysSoAndReturnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        idle()

        assertEquals(string(R.string.peer_ended_session, "Dongje"), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
