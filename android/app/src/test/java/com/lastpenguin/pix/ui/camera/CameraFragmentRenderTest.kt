// AI-generated with Claude Code, 2026-10-27, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * The Camera screen's render paths (R&S 6.2, Testing Documentation 2.1.6), on the real Fragment under
 * FragmentScenario with a container of fakes. Partitions, one test each: camera permission denied or granted;
 * camera status STARTING, READY; a guide added, hidden, removed by the shutter's end of edit mode; a photo saved or
 * failed; the flash and brightness controls with and without the capability; the aspect ratio 4:3 and 16:9; the
 * scene photo step framing and reviewing; a session connected and a peer leaving.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFragmentRenderTest {
    private var scenario: FragmentScenario<CameraFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    // ---- Setup ---------------------------------------------------------------

    private fun launch(
        camera: FakeCameraController = FakeCameraController(),
        granted: Boolean = true,
    ): TestAppContainer {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (granted) shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer(camera = camera).install()
        scenario = launchFragmentInContainer<CameraFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    /** Runs the main looper: ViewModel coroutines on Dispatchers.Main and the posted UI work. */
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> onScreen(block: (FragmentCameraBinding, CameraFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result = block(FragmentCameraBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    private fun bitmap(width: Int = 40, height: Int = 80): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun guide(withReference: Boolean = true) = ReferenceGuide(
        id = "guide-1",
        cutout = bitmap(),
        outline = bitmap(),
        aspect = 0.5f,
        source = GuideSource.GALLERY,
        reference = if (withReference) bitmap(60, 80) else null,
    )

    private fun string(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    // ---- Permission and camera status ------------------------------------------

    @Test
    fun withoutCameraPermission_showsTheMessageAndDisablesTheShutter() {
        launch(granted = false)

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.cameraMessagePanel.visibility)
            assertEquals(string(R.string.camera_permission_message), controls.cameraMessage.text.toString())
            assertEquals(string(R.string.open_settings), controls.cameraActionButton.text.toString())
            assertFalse(controls.shutterButton.isEnabled)
            assertEquals(View.GONE, controls.compositionOverlay.visibility)
            assertEquals(View.GONE, controls.guideOverlay.visibility)
        }
    }

    @Test
    fun cameraReady_showsTheViewfinderAndEnablesTheShutter() {
        launch()

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.cameraMessagePanel.visibility)
            assertEquals(View.GONE, controls.cameraProgress.visibility)
            assertEquals(View.VISIBLE, controls.compositionOverlay.visibility)
            assertEquals(View.VISIBLE, controls.guideOverlay.visibility)
            assertTrue(controls.shutterButton.isEnabled)
            assertEquals(string(R.string.zoom_ratio, "1.0"), controls.zoomReadout.text.toString())
            assertTrue(controls.zoomReadout.isEnabled)
            // No guide yet: no guide controls, no edit-mode toggle, the hint says to add one.
            assertEquals(View.GONE, controls.guideControls.visibility)
            assertEquals(View.GONE, controls.editGuideButton.visibility)
            assertEquals(string(R.string.hint_add_guide), controls.hintText.text.toString())
            // No flash and no exposure range in the capabilities: both controls are hidden (FR-1.11, FR-1.12).
            assertEquals(View.GONE, controls.flashButton.visibility)
            assertEquals(View.GONE, controls.brightnessButton.visibility)
            assertFalse(controls.galleryButton.isEnabled)
        }
    }

    @Test
    fun cameraStarting_showsProgressAndDisablesTheShutter() {
        val camera = FakeCameraController().apply { setStatus(CameraStatus.STARTING) }
        launch(camera)

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.cameraProgress.visibility)
            assertFalse(controls.shutterButton.isEnabled)
            assertFalse(controls.zoomReadout.isEnabled)
            assertEquals(View.GONE, controls.compositionOverlay.visibility)
        }

        camera.setStatus(CameraStatus.READY)
        idle()

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.cameraProgress.visibility)
            assertTrue(controls.shutterButton.isEnabled)
        }
    }

    // ---- Guide -----------------------------------------------------------------

    @Test
    fun newGuide_turnsEditModeOnAndShowsItsControlsAndThumbnail() {
        val modules = launch()

        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.guideControls.visibility)
            assertEquals(View.VISIBLE, controls.editGuideButton.visibility)
            assertTrue(controls.editGuideButton.isChecked)
            assertTrue(controls.editGuideButton.isEnabled)
            assertTrue(controls.guideOverlay.editable)
            assertTrue(controls.opacitySlider.isEnabled)
            assertEquals(string(R.string.guide_opacity_percent, 50), controls.opacityValue.text.toString())
            assertEquals(string(R.string.hint_edit_guide), controls.hintText.text.toString())
            assertEquals(string(R.string.guide_change_description), controls.guideButton.contentDescription)
            assertTrue(controls.guideButton.isActivated)
            assertEquals(View.VISIBLE, controls.referenceThumbnail.visibility)
        }
    }

    @Test
    fun editModeToggle_turnsTheModeOffAndThePinchZoomsAgain() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ -> controls.editGuideButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertFalse(controls.editGuideButton.isChecked)
            assertFalse(controls.guideOverlay.editable)
            assertEquals(string(R.string.hint_pinch_zoom), controls.hintText.text.toString())
        }
    }

    @Test
    fun hiddenGuide_disablesItsControlsAndEndsEditMode() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ -> controls.hideGuideButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertFalse(modules.guides.state.value.visible)
            assertFalse(controls.opacitySlider.isEnabled)
            assertFalse(controls.opacityValue.isEnabled)
            assertFalse(controls.editGuideButton.isChecked)
            assertFalse(controls.editGuideButton.isEnabled)
            assertEquals(string(R.string.show_guide), controls.hideGuideButton.contentDescription)
            assertEquals(string(R.string.hint_guide_hidden), controls.hintText.text.toString())
            // The reference stays in its corner while the guide is hidden (FR-3.9).
            assertEquals(View.VISIBLE, controls.referenceThumbnail.visibility)
        }

        onScreen { controls, _ -> controls.hideGuideButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.opacitySlider.isEnabled)
            assertEquals(string(R.string.hide_guide), controls.hideGuideButton.contentDescription)
        }
    }

    @Test
    fun removeGuide_hidesTheControlsAndTheThumbnail() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ -> controls.removeGuideButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.guideControls.visibility)
            assertEquals(View.GONE, controls.editGuideButton.visibility)
            assertEquals(View.GONE, controls.referenceThumbnail.visibility)
            assertEquals(string(R.string.hint_add_guide), controls.hintText.text.toString())
            assertFalse(controls.guideButton.isActivated)
        }
    }

    // ---- Shutter and the capture status -----------------------------------------

    @Test
    fun shutter_savesAndEndsEditMode_andTheNoticeGoesAfterThreeSeconds() {
        val camera = FakeCameraController().apply { photo = Uri.parse("content://media/external/images/1") }
        val modules = launch(camera)
        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.captureStatus.visibility)
            assertEquals(string(R.string.photo_saved), controls.captureStatus.text.toString())
            assertFalse(controls.editGuideButton.isChecked)
            assertEquals(string(R.string.hint_pinch_zoom), controls.hintText.text.toString())
            assertTrue(controls.galleryButton.isEnabled)
        }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CameraViewModel.NOTICE_MS + 1))

        onScreen { controls, _ -> assertEquals(View.GONE, controls.captureStatus.visibility) }
    }

    @Test
    fun failedPhoto_keepsTheReasonOnScreen() {
        launch(FakeCameraController())

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CameraViewModel.NOTICE_MS + 1))

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.captureStatus.visibility)
            assertEquals(string(R.string.photo_save_failed), controls.captureStatus.text.toString())
            assertTrue(controls.shutterButton.isEnabled)
            assertFalse(controls.galleryButton.isEnabled)
        }
    }

    // ---- Flash, brightness, and the aspect ratio ------------------------------------

    @Test
    fun flashCapability_showsTheButtonAndATapCyclesTheMode() {
        val camera = FakeCameraController(hasFlash = true)
        launch(camera)

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.flashButton.visibility)
            assertEquals(string(R.string.flash_off), controls.flashButton.contentDescription)
            controls.flashButton.performClick()
        }
        idle()

        onScreen { controls, _ ->
            assertEquals(listOf(FlashMode.AUTO), camera.flashRequests)
            assertEquals(string(R.string.flash_auto), controls.flashButton.contentDescription)
        }
    }

    @Test
    fun exposureRange_showsTheBrightnessButtonAndATapOpensThePanel() {
        launch(FakeCameraController(exposureRange = -4..4))

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.brightnessButton.visibility)
            assertEquals(View.GONE, controls.brightnessPanel.visibility)
            controls.brightnessButton.performClick()
        }
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.brightnessButton.isChecked)
            assertEquals(View.VISIBLE, controls.brightnessPanel.visibility)
            assertTrue(controls.brightnessSlider.isEnabled)
            assertEquals("0", controls.brightnessValue.text.toString())
        }
    }

    @Test
    fun aspectRatio_followsTheCameraStateOnTheButtonAndTheOverlays() {
        val camera = FakeCameraController()
        launch(camera)

        onScreen { controls, _ ->
            assertEquals(string(R.string.aspect_4_3), controls.aspectButton.text.toString())
            assertEquals(AspectRatio.R4_3.frameAspect, controls.compositionOverlay.frameAspect)
        }

        camera.state.value = camera.state.value.copy(aspect = AspectRatio.R16_9)
        idle()

        onScreen { controls, _ ->
            assertEquals(string(R.string.aspect_16_9), controls.aspectButton.text.toString())
            assertEquals(AspectRatio.R16_9.frameAspect, controls.compositionOverlay.frameAspect)
            assertEquals(AspectRatio.R16_9.frameAspect, controls.guideOverlay.frameAspect)
        }
    }

    // ---- The scene photo for poses ----------------------------------------------

    @Test
    fun scenePhoto_hidesTheGuideAndTheSettingsWhileFraming_andShowsReviewAfterTheShutter() {
        val camera = FakeCameraController(hasFlash = true).apply { frame = bitmap(300, 400) }
        val modules = launch(camera)
        modules.guides.setGuide(guide())
        idle()

        onScreen { _, fragment -> generationViewModel(fragment).beginScene() }
        idle()

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.sceneTitle.visibility)
            assertEquals(string(R.string.scene_title), controls.sceneTitle.text.toString())
            assertEquals(View.GONE, controls.cameraSettings.visibility)
            assertEquals(View.GONE, controls.guideOverlay.visibility)
            assertEquals(View.GONE, controls.guideControls.visibility)
            assertEquals(View.GONE, controls.editGuideButton.visibility)
            assertEquals(View.GONE, controls.referenceThumbnail.visibility)
            assertEquals(View.INVISIBLE, controls.guideButton.visibility)
            assertEquals(View.VISIBLE, controls.captureControls.visibility)
            controls.shutterButton.performClick()
        }
        idle()

        onScreen { controls, _ ->
            assertEquals(string(R.string.scene_review_title), controls.sceneTitle.text.toString())
            assertEquals(View.VISIBLE, controls.sceneStill.visibility)
            assertEquals(View.VISIBLE, controls.sceneReviewControls.visibility)
            assertEquals(View.GONE, controls.captureControls.visibility)
            assertEquals(View.INVISIBLE, controls.zoomReadout.visibility)
            assertEquals(View.GONE, controls.captureStatus.visibility)
            controls.shootAgainButton.performClick()
        }
        idle()

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.sceneStill.visibility)
            assertEquals(View.VISIBLE, controls.captureControls.visibility)
            controls.sceneCancelButton.performClick()
        }
        idle()

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.sceneTitle.visibility)
            assertEquals(View.VISIBLE, controls.guideOverlay.visibility)
            assertEquals(View.VISIBLE, controls.referenceThumbnail.visibility)
            assertEquals(View.VISIBLE, controls.cameraSettings.visibility)
        }
    }

    // ---- A session on the photographer's phone -----------------------------------------

    @Test
    fun connectedSession_showsTheLiveLightAndEndSession_andAPeerLeavingShowsANotice() {
        val modules = launch()

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        idle()

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.liveDot.visibility)
            assertEquals(string(R.string.live_badge, "Junhyeong"), controls.liveDot.contentDescription.toString())
            assertEquals(View.VISIBLE, controls.endSessionButton.visibility)
            assertEquals(View.GONE, controls.shootTogetherButton.visibility)
        }

        modules.session.deliver(SessionMessage.Leave(EndReason.LEFT))
        idle()

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
            assertEquals(string(R.string.peer_left, "Junhyeong"), controls.noticeText.text.toString())
        }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CameraViewModel.NOTICE_MS + 1))

        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    @Test
    fun theLiveLight_goesOutWhileTheCameraCannotStream_andComesBack() {
        val camera = FakeCameraController()
        val modules = launch(camera)
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        idle()

        camera.setStatus(CameraStatus.UNAVAILABLE)
        idle()
        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.cameraMessagePanel.visibility)
            assertEquals(View.GONE, controls.liveDot.visibility)
        }

        // A rebind, which holds the last frame, keeps it on.
        camera.setStatus(CameraStatus.STARTING)
        idle()
        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.liveDot.visibility) }
        camera.setStatus(CameraStatus.READY)
        idle()
        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.liveDot.visibility) }
    }

    // ---- The frame held while the ratio changes -------------------------------------------

    @Test
    fun aHeldOneByOneFrame_keepsTheOutsideDimmed_andOtherRatiosStayAsTheyAre() {
        fun white() = bitmap(30, 40).apply { eraseColor(Color.WHITE) }

        // 30 x 40, the 4:3 area: the square is rows 5 to 35.
        val square = heldFrame(white(), AspectRatio.R1_1)
        assertEquals(Color.WHITE, square.getPixel(15, 20))
        assertEquals(Color.WHITE, square.getPixel(15, 6))
        val dimmed = square.getPixel(15, 2)
        assertEquals(Color.red(dimmed), Color.blue(dimmed))
        assertTrue(Color.red(dimmed) in 95..110)
        assertEquals(dimmed, square.getPixel(15, 38))

        for (aspect in listOf(AspectRatio.R4_3, AspectRatio.R16_9)) {
            val frame = white()
            assertTrue(heldFrame(frame, aspect) === frame)
            assertEquals(Color.WHITE, frame.getPixel(15, 2))
        }
    }

    /** The screen's shared GenerationViewModel, the activity-scoped one it uses for the scene photo step. */
    private fun generationViewModel(fragment: CameraFragment): GenerationViewModel =
        ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[GenerationViewModel::class.java]
}
