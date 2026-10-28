// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
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
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
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
 * test modules' PoseTips. The banner: preparing, then the tip in place, closed by a touch that still reaches the
 * button it lands on, opened again by the tip button, gone with a failure after its notice. The guide: a generated one
 * with a tip, hidden and shown again, one from the gallery. The scene photo step hides the banner and the button.
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

    private fun launch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        modules = TestAppContainer(camera = FakeCameraController().apply { frame = bitmap(300, 400) }).install()
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

    /** A tap that starts and ends at [view]'s middle, dispatched from the screen's root as a finger's would be. */
    private fun tapThroughTheRoot(controls: FragmentCameraBinding, view: View) {
        val at = IntArray(2).also(view::getLocationInWindow)
        val origin = IntArray(2).also(controls.root::getLocationInWindow)
        val x = (at[0] - origin[0] + view.width / 2).toFloat()
        val y = (at[1] - origin[1] + view.height / 2).toFloat()
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
    fun aTouchAnywhereClosesTheBanner_andStillDoesWhatItDoes_andTheTipButtonOpensItAgain() {
        launch()
        generatedGuideOnCamera()
        modules.tipper.requests.single().tip(TIP)
        idle()

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.hideGuideButton) }
        idle()
        onScreen { controls, _ -> assertFalse(controls.tipBanner.isShown) }
        // The same tap hid the guide, and a hidden guide's tip is hidden with it.
        assertFalse(modules.guides.state.value.visible)
        onScreen { controls, _ -> assertFalse(controls.tipButtonFrame.isShown) }

        onScreen { controls, _ -> controls.hideGuideButton.performClick() }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipButtonFrame.isShown)
            assertFalse(controls.tipBanner.isShown)
            controls.tipButton.performClick()
        }
        idle()
        onScreen { controls, _ -> assertTrue(controls.tipBanner.isShown) }
    }

    @Test
    fun aTipThatArrivesWhileClosedShowsADot() {
        launch()
        generatedGuideOnCamera()
        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.previewContainer) }
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
        const val TIP = "Zoom in or take a step closer, hold the phone at your chest and keep it level - " +
            "the body keeps its proportions."
    }
}
