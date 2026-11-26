// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Dongje Park, and Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.string
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
 * The pose tip on the Camera screen (R&S 6.2, FR-4.10; Testing Documentation 2.1.6), on the real Fragment with the
 * test modules' PoseTips. The banner: preparing, then the tip in place, gone with a failure after its notice. What
 * closes it: only the tip button, lit and named "Close tip" while the tip is open; a touch on the image, on another
 * control (which still does what it does), or on the banner itself (which reaches no image under it) leaves it open.
 * The guide: a generated one with a tip, hidden and shown again, one from the gallery. The scene photo step hides the
 * banner and the button. A ratio chosen with taps on its button and its panel leaves the tip open, since the guide
 * stays.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraTipScreenTest {
    private var scenario: FragmentScenario<CameraFragment>? = null
    private lateinit var modules: TestAppContainer

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(camera: FakeCameraController = FakeCameraController()) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        modules = TestAppContainer(camera = camera.apply { frame = bitmap(300, 400) }).install()
        scenario = launchFragmentInContainer<CameraFragment>(themeResId = R.style.Theme_Pix)
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> onScreen(block: (FragmentCameraBinding, CameraFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentCameraBinding.bind(it.requireView()), it) }
        return result!!
    }

    private fun guide(id: String, source: GuideSource = GuideSource.GENERATED) = ReferenceGuide(
        id = id,
        mask = bitmap(),
        outline = bitmap(),
        aspect = 0.5f,
        source = source,
        anchor = NormalizedRect(0.3f, 0.1f, 0.7f, 0.9f),
    )

    /** A generated guide goes on the camera with its tip being asked for. */
    private fun generatedGuideOnCamera(id: String = "generated"): ReferenceGuide {
        val guide = guide(id)
        modules.tips.prepare(guide, bitmap(), TipSource(bitmap(), "Walk past the railing"))
        modules.guides.setGuide(guide)
        idle()
        return guide
    }

    /**
     * A tap that starts and ends at [view]'s middle, or [down] of its height down, dispatched from the screen's root as a
     * finger's would be.
     */
    private fun tapThroughTheRoot(controls: FragmentCameraBinding, view: View, down: Float = 0.5f) {
        val at = IntArray(2).also(view::getLocationInWindow)
        val origin = IntArray(2).also(controls.root::getLocationInWindow)
        val x = (at[0] - origin[0] + view.width / 2).toFloat()
        val y = at[1] - origin[1] + view.height * down
        val now = SystemClock.uptimeMillis()
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun aGeneratedGuidesTip_preparesThenShowsInPlace_withTheTipButton() {
        launch()
        generatedGuideOnCamera()

        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(controls.root.context.getString(R.string.tip_preparing), controls.tipText.text.toString())
            assertTrue(controls.tipButtonFrame.isShown)
        }
        modules.tipper.requests.single().tip(TIP)
        idle()

        onScreen { controls, _ ->
            assertEquals(TIP, controls.tipText.text.toString())
            assertEquals(View.GONE, controls.tipDot.visibility)
        }
    }

    @Test
    fun aTouchOnTheImage_focusesThere_andLeavesTheTipOpen() {
        launch(FakeCameraController(supportsFocusPoint = true))
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()

        // Above the open tip, which may lie over the image's lower half on this small screen.
        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.previewContainer, down = 0.25f) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DOUBLE_TAP_WAIT_MS))

        assertEquals(1, modules.camera.focusPoints.size)
        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(TIP, controls.tipText.text.toString())
        }
    }

    @Test
    fun aTouchOnAnotherControl_doesWhatItDoes_andLeavesTheTipOpenWhenItsGuideShowsAgain() {
        launch()
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.hideGuideButton) }
        idle()
        // The tap hid the guide, and a hidden guide's tip is hidden with it.
        assertFalse(modules.guides.state.value.visible)
        onScreen { controls, _ ->
            assertFalse(controls.tipBanner.isShown)
            assertFalse(controls.tipButtonFrame.isShown)
        }

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.hideGuideButton) }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipButtonFrame.isShown)
            assertTrue(controls.tipBanner.isShown)
        }
    }

    @Test
    fun aTapOnTheOpenTip_leavesItOpen_andFocusesNothingUnderIt() {
        launch(FakeCameraController(supportsFocusPoint = true))
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()
        onScreen { controls, _ ->
            // The banner lies over the image here, so a tap that went through it would focus the camera.
            val banner = IntArray(2).also(controls.tipBanner::getLocationInWindow)
            val preview = IntArray(2).also(controls.previewView::getLocationInWindow)
            val middle = banner[1] + controls.tipBanner.height / 2
            assertTrue(middle in preview[1] until preview[1] + controls.previewView.height)
        }

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.tipBanner) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DOUBLE_TAP_WAIT_MS))

        onScreen { controls, _ -> assertTrue(controls.tipBanner.isShown) }
        assertEquals(emptyList<Any>(), modules.camera.focusPoints)
    }

    @Test
    fun aRatioChosenWithTaps_leavesTheTipOpen() {
        launch()
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.aspectButton) }
        idle()
        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.aspect11Option) }
        idle()
        modules.camera.state.value = modules.camera.state.value.copy(aspect = AspectRatio.R1_1)
        idle()

        assertEquals(listOf(AspectRatio.R1_1), modules.camera.aspectRequests)
        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(TIP, controls.tipText.text.toString())
            assertTrue(controls.tipButtonFrame.isShown)
        }
    }

    @Test
    fun aFingersTapOnTheTipButton_closesAnOpenTip_andTheNextOpensIt() {
        launch()
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()
        onScreen { controls, _ -> assertTrue(controls.tipBanner.isShown) }

        onScreen { controls, _ ->
            assertTrue("lit while the tip is open", controls.tipButton.isActivated)
            assertEquals(string(R.string.close_tip), controls.tipButton.contentDescription)
        }

        // Through the root, as a finger lands.
        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.tipButton) }
        idle()
        onScreen { controls, _ ->
            assertFalse(controls.tipBanner.isShown)
            assertFalse(controls.tipButton.isActivated)
            assertEquals(string(R.string.show_tip), controls.tipButton.contentDescription)
        }

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.tipButton) }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertTrue(controls.tipButton.isActivated)
        }
    }

    @Test
    fun aTipThatArrivesWhileClosedShowsADot() {
        launch()
        generatedGuideOnCamera()
        onScreen { controls, _ -> controls.tipButton.performClick() }
        idle()

        modules.tipper.requests.single().tip(TIP)
        idle()

        onScreen { controls, _ ->
            assertFalse(controls.tipBanner.isShown)
            assertTrue(controls.tipDot.isShown)
            controls.tipButton.performClick()
        }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(View.GONE, controls.tipDot.visibility)
        }
    }

    @Test
    fun aTipThatCannotBeHadIsSaidOnce_thenTheBannerAndTheButtonGo() {
        launch()
        generatedGuideOnCamera()

        modules.tipper.requests.single().fail()
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(controls.root.context.getString(R.string.tip_failed), controls.tipText.text.toString())
            assertFalse(controls.tipButtonFrame.isShown)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CameraViewModel.NOTICE_MS + 100))

        onScreen { controls, _ -> assertFalse(controls.tipBanner.isShown) }
    }

    @Test
    fun aGuideFromTheGalleryHasNoTip() {
        launch()
        modules.guides.setGuide(guide("gallery", GuideSource.GALLERY))
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.guideControls.isShown)
            assertFalse(controls.tipBanner.isShown)
            assertFalse(controls.tipButtonFrame.isShown)
        }
        assertEquals(0, modules.tipper.requests.size)
    }

    @Test
    fun theScenePhotoStepHidesTheTip() {
        launch()
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()

        onScreen { _, fragment ->
            ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[GenerationViewModel::class.java]
                .beginScene()
        }
        idle()

        onScreen { controls, _ ->
            assertFalse(controls.tipBanner.isShown)
            assertFalse(controls.tipButtonFrame.isShown)
        }
    }

    private companion object {
        /** Past the wait for a second tap, after which a tap on the image focuses there. */
        const val DOUBLE_TAP_WAIT_MS = 400L

        const val TIP = "Zoom in or take a step closer, hold the phone at your chest and keep it level - " +
            "the body keeps its proportions."
    }
}
