// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * The camera menu and the shutter's timer on the Camera screen (FR-1.17, R&S 6.2), on the real Fragment under
 * FragmentScenario with a container of fakes. Partitions: the menu opened by its button and closed by Back, by a touch
 * on the preview, and by its button again; the ratio's button and the menu's each in the bar's pill while its own panel
 * is open, the menu taking the ratios' place; a tap on the timer's icon beside the button, taken as the button's (the
 * menu opened and closed), and one on the ratio beside it, taken as the ratio's; the timer chosen in the menu (its icon beside the menu's button, its notice,
 * the shutter's spoken label) and off again (both gone); the countdown on the screen (the seconds over the preview,
 * alone, the other controls dimmed and waiting, the shutter a stop square in the timer's ring, which fills on with the
 * clock and starts again from each second's place, and its label for cancelling), its end (one photo, all
 * back, the white shutter again), the shutter's second tap (no photo, the white shutter again), and the screen
 * stopping (no photo); in a session, each second and the end told to the subject's phone, and End session waiting,
 * dimmed, until the end. The scene photo's countdown: the ring on its accent shutter, which comes back at the end; at
 * its end, the shutter waits while the frame is taken, so a tap then starts no second countdown.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraTimerScreenTest {
    private var scenario: FragmentScenario<CameraFragment>? = null
    private val camera = FakeCameraController(hasFlash = true)

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(): TestAppContainer {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer(camera = camera).install()
        scenario = launchFragmentInContainer<CameraFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun <T> onScreen(block: (FragmentCameraBinding, CameraFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentCameraBinding.bind(it.requireView()), it) }
        idle()
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun string(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    /** The timer at 3 s, chosen in the menu, which then closes. */
    private fun chooseThreeSeconds() {
        onScreen { controls, _ -> controls.cameraMenuButton.performClick() }
        onScreen { controls, _ -> controls.menuTimer.performClick() }
        onScreen { controls, _ -> controls.cameraMenuButton.performClick() }
    }

    /** A tap on [view]'s middle, through the screen's root, which watches every touch's end. */
    private fun tap(root: View, view: View) {
        val at = IntArray(2).also(view::getLocationInWindow)
        val x = at[0] + view.width / 2f
        val y = at[1] + view.height / 2f
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(down, down, action, x, y, 0).also { root.dispatchTouchEvent(it) }.recycle()
        }
    }

    // ---- The menu ------------------------------------------------------------------------------------------------

    @Test
    fun theRatio_isInTheBarsPill_whileTheRatiosAreOpen_asTheMenusButtonIsWhileTheMenuIs() {
        launch()

        onScreen { controls, _ -> controls.aspectButton.performClick() }
        onScreen { controls, _ ->
            assertTrue(controls.aspectPanel.isShown)
            assertTrue(controls.aspectButton.isActivated)
            assertFalse(controls.cameraMenuButton.isActivated)
            controls.cameraMenuButton.performClick()
        }
        // The menu takes the ratios' place, and its button the pill.
        onScreen { controls, _ ->
            assertFalse(controls.aspectPanel.isShown)
            assertFalse(controls.aspectButton.isActivated)
            assertTrue(controls.cameraMenu.isShown)
            assertTrue(controls.cameraMenuButton.isActivated)
        }
    }

    @Test
    fun theMenu_opensUnderTheBar_andClosesAtBack_atATouchOnThePreview_andAtItsButton() {
        launch()

        onScreen { controls, _ -> controls.cameraMenuButton.performClick() }
        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.cameraMenu.visibility)
            assertTrue(controls.cameraMenuButton.isActivated)
        }
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        onScreen { controls, _ ->
            assertEquals("Back", View.GONE, controls.cameraMenu.visibility)
            controls.cameraMenuButton.performClick()
        }
        onScreen { controls, _ -> tap(controls.root, controls.previewContainer) }
        onScreen { controls, _ ->
            assertEquals("a touch on the preview", View.GONE, controls.cameraMenu.visibility)
            controls.cameraMenuButton.performClick()
        }
        onScreen { controls, _ -> controls.cameraMenuButton.performClick() }

        onScreen { controls, _ -> assertEquals("its button again", View.GONE, controls.cameraMenu.visibility) }
    }

    @Test
    fun theTimer_chosenInTheMenu_showsBesideItsButton_saysSo_andNamesTheShutter_andOffTakesItAway() {
        launch()

        chooseThreeSeconds()

        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.timerIndicator.visibility)
            assertEquals(R.drawable.ic_timer_3, controls.timerIndicator.tag)
            assertEquals(string(R.string.timer_seconds, 3), controls.noticeText.text.toString())
            assertEquals(string(R.string.shutter_timer, 3), controls.shutterButton.contentDescription)
            controls.cameraMenuButton.performClick()
        }
        // 10 s, then off.
        onScreen { controls, _ -> controls.menuTimer.performClick() }
        onScreen { controls, _ -> controls.menuTimer.performClick() }

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.timerIndicator.visibility)
            assertFalse(controls.menuTimer.isActivated)
            assertEquals(string(R.string.timer_off), controls.noticeText.text.toString())
            assertEquals(string(R.string.shutter), controls.shutterButton.contentDescription)
        }
    }

    @Test
    fun aTapOnTheTimersIcon_isATapOnTheMenusButton_andOpensTheMenu() {
        launch()
        chooseThreeSeconds()
        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.timerIndicator.visibility)
            assertEquals(View.GONE, controls.cameraMenu.visibility)
            // The icon is 16 dp, so it takes no tap of its own: the button's area reaches over it (NFR-16, #318).
            assertFalse(controls.timerIndicator.isClickable)
            tap(controls.root, controls.timerIndicator)
        }

        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.cameraMenu.visibility) }
        // And again closes it, as the button does.
        onScreen { controls, _ -> tap(controls.root, controls.timerIndicator) }
        onScreen { controls, _ -> assertEquals(View.GONE, controls.cameraMenu.visibility) }
    }

    @Test
    fun aTapOnTheRatioBesideTheIcons_stillOpensTheRatios() {
        launch()
        chooseThreeSeconds()

        // The button's area ends at the last icon: the ratio right after it keeps its own taps.
        onScreen { controls, _ -> tap(controls.root, controls.aspectButton) }

        onScreen { controls, _ ->
            assertTrue(controls.aspectPanel.isShown)
            assertEquals(View.GONE, controls.cameraMenu.visibility)
        }
    }

    // ---- The countdown --------------------------------------------------------------------------------------------

    @Test
    fun theCountdown_showsTheSecondsOverThePreview_dimsTheOtherControls_andEndsInOnePhoto() {
        launch()
        chooseThreeSeconds()

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.countdownPanel.visibility)
            assertEquals("3", controls.countdownNumber.text.toString())
            // The number alone: that the shutter cancels is its name here and the tutorial's tip, not a line.
            assertEquals(1, controls.countdownPanel.childCount)
            assertEquals(string(R.string.shutter_cancel_timer), controls.shutterButton.contentDescription)
            assertTrue(controls.shutterButton.isEnabled)
            for (waiting in listOf(controls.guideButton, controls.galleryButton, controls.switchCameraButton)) {
                assertFalse(waiting.isEnabled)
                assertTrue(waiting.alpha < 1f)
            }
            assertFalse(controls.cameraMenuButton.isEnabled)
            assertFalse(controls.aspectButton.isEnabled)
        }
        assertRing(fill = 0f)
        pass(500)
        assertRing(fill = 1f / 6)
        pass(500)
        onScreen { controls, _ -> assertEquals("2", controls.countdownNumber.text.toString()) }
        assertRing(fill = 1f / 3)
        assertTrue(camera.photoTurns.isEmpty())

        // Past the last second, which ends as this time does.
        pass(2_100)

        assertEquals(1, camera.photoTurns.size)
        onScreen { controls, fragment ->
            assertEquals(View.GONE, controls.countdownPanel.visibility)
            assertTrue(controls.guideButton.isEnabled)
            assertEquals(1f, controls.guideButton.alpha)
            assertEquals(R.drawable.bg_shutter, controls.shutterButton.tag)
            assertFalse(checkNotNull(fragment.shutterRing).drawable.filling)
        }
    }

    /** The shutter shows the timer's ring, filled [fill] now and filling on. */
    private fun assertRing(fill: Float) = onScreen { controls, fragment ->
        val ring = checkNotNull(fragment.shutterRing).drawable
        assertEquals(CameraFragment.COUNTDOWN_SHUTTER, controls.shutterButton.tag)
        assertSame(ring, controls.shutterButton.background)
        assertEquals(fill, ring.fill, 0.001f)
        assertTrue(ring.filling)
    }

    @Test
    fun theShuttersSecondTap_cancelsTheCountdown_withoutAPhoto() {
        launch()
        chooseThreeSeconds()
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        pass(1_000)

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        onScreen { controls, fragment ->
            assertEquals(R.drawable.bg_shutter, controls.shutterButton.tag)
            assertFalse(checkNotNull(fragment.shutterRing).drawable.filling)
        }
        pass(5_000)

        assertTrue(camera.photoTurns.isEmpty())
        onScreen { controls, _ -> assertEquals(View.GONE, controls.countdownPanel.visibility) }
    }

    @Test
    fun theScenePhotosShutter_waitsWhileItsFrameIsTaken_soATapStartsNoSecondCountdown() {
        val modules = launch()
        chooseThreeSeconds()
        camera.frame = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        camera.grabbing = CompletableDeferred()
        // The photo's check waits, so the step stays on Camera.
        modules.persons.gate = CompletableDeferred()
        val generation = onScreen { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>() }
        generation.beginScene()
        idle()

        // The countdown ends, and the frame is being taken; meanwhile the ring was on the shutter, which is the accent's
        // again after it.
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        onScreen { controls, _ -> assertEquals(CameraFragment.COUNTDOWN_SHUTTER, controls.shutterButton.tag) }
        pass(3_500)
        assertTrue(generation.uiState.value.shooting)
        onScreen { controls, _ -> assertEquals(R.drawable.bg_shutter_scene, controls.shutterButton.tag) }

        // A tap meanwhile starts nothing.
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        pass(500)
        onScreen { controls, fragment ->
            assertEquals(View.GONE, controls.countdownPanel.visibility)
            assertNull(fragment.requireActivity().viewModel<CameraViewModel>().uiState.value.countdown)
        }

        // The frame comes, and its check goes on, with no countdown over it.
        camera.grabbing!!.complete(Unit)
        idle()
        assertEquals(GenerationUiState.Phase.CHECKING, generation.uiState.value.phase)
        pass(5_000)
        onScreen { controls, _ -> assertEquals(View.GONE, controls.countdownPanel.visibility) }
    }

    @Test
    fun theScreenStopping_cancelsTheCountdown_withoutAPhoto() {
        launch()
        chooseThreeSeconds()
        onScreen { controls, _ -> controls.shutterButton.performClick() }

        scenario!!.moveToState(Lifecycle.State.CREATED)
        pass(5_000)

        assertTrue(camera.photoTurns.isEmpty())
    }

    @Test
    fun inASession_eachSecondAndTheEnd_areToldToTheSubjectsPhone_andEndSessionWaits() {
        val modules = launch()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 2"), Role.PHOTOGRAPHER))
        idle()
        chooseThreeSeconds()

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        onScreen { controls, _ ->
            assertTrue(controls.endSessionButton.isShown)
            assertFalse(controls.endSessionButton.isEnabled)
            assertTrue(controls.endSessionButton.alpha < 1f)
        }
        pass(3_100)
        onScreen { controls, _ ->
            assertTrue(controls.endSessionButton.isEnabled)
            assertEquals(1f, controls.endSessionButton.alpha)
        }

        assertEquals(
            listOf(3, 2, 1, 0),
            modules.session.sent.filterIsInstance<SessionMessage.Countdown>().map { it.left },
        )
    }
}
