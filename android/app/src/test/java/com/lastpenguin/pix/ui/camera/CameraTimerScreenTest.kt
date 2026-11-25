// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
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
import com.lastpenguin.pix.ui.TestAppContainer
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
 * The camera menu and the shutter's timer on the Camera screen (FR-1.17, R&S 6.2), on the real Fragment under
 * FragmentScenario with a container of fakes. Partitions: the menu opened by its button and closed by Back, by a touch
 * on the preview, and by its button again; the ratio's button and the menu's each in the bar's pill while its own panel
 * is open, the menu taking the ratios' place; the timer chosen in the menu (its icon beside the menu's button, its notice,
 * the shutter's spoken label) and off again (both gone); the countdown on the screen (the seconds over the preview,
 * alone, the other controls dimmed and waiting, the shutter's label for cancelling), its end (one photo, all back),
 * the shutter's second tap (no photo), and the screen stopping (no photo); in a session, each second and the end told
 * to the subject's phone, and End session waiting, dimmed, until the end.
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
        pass(1_000)
        onScreen { controls, _ -> assertEquals("2", controls.countdownNumber.text.toString()) }
        assertTrue(camera.photoTurns.isEmpty())

        // Past the last second, which ends as this time does.
        pass(2_100)

        assertEquals(1, camera.photoTurns.size)
        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.countdownPanel.visibility)
            assertTrue(controls.guideButton.isEnabled)
            assertEquals(1f, controls.guideButton.alpha)
        }
    }

    @Test
    fun theShuttersSecondTap_cancelsTheCountdown_withoutAPhoto() {
        launch()
        chooseThreeSeconds()
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        pass(1_000)

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        pass(5_000)

        assertTrue(camera.photoTurns.isEmpty())
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
