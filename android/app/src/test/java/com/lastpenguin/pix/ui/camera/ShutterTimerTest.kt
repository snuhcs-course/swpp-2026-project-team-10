// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.FakeGuideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * The shutter's timer and the camera menu (FR-1.17) in CameraViewModel, in virtual time.
 * Partitions. The timer's choice: off, 3 s, and 10 s, each tap the next and off again after 10 s, each with its notice;
 * a tap while it counts down changes nothing. The shutter with the timer off: the photo at once, and no countdown.
 * With 3 s and with 10 s: the countdown from the timer's seconds to 1, one a second, each told to the subject's phone,
 * and at its end 0 told, one photo, and one blink; boundaries 1 ms before the end (no photo) and at it. A second tap
 * while it counts down: cancelled, no photo, 0 told. Leaving the screen (cancelCountdown): the same; with no countdown,
 * nothing told. A camera not ready at the shutter (nothing starts) and at the countdown's end (no photo, the camera's
 * notice). While it counts down: the camera switch, a ratio, the ratios, the menu, the flash, and the viewer are
 * refused. The menu and the ratios: each opening closes the other; the countdown's start closes both. The flash: each
 * tap with its notice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShutterTimerTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val camera = FakeCameraController(hasFlash = true)
    private val told = mutableListOf<Int>()
    private lateinit var model: CameraViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = CameraViewModel(camera, FakeGuideRepository(), onCountdown = { told += it })
        store.put("camera", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    /** The photos the camera was asked for. */
    private val photos: Int get() = camera.photoTurns.size

    /** Counts the blinks of the timer's photos while the test runs. */
    private fun TestScope.blinks(): () -> Int {
        var count = 0
        backgroundScope.launch { model.timedShots.collect { count++ } }
        return { count }
    }

    private fun TestScope.notices(): MutableList<MenuNotice> {
        val notices = mutableListOf<MenuNotice>()
        backgroundScope.launch { model.menuNotices.collect { notices += it } }
        return notices
    }

    private fun setTimer(timer: ShutterTimer) {
        while (model.uiState.value.timer != timer) model.onTimerTap()
    }

    // ---- The timer's choice -----------------------------------------------------------------------------------------

    @Test
    fun timerTaps_goOffTo3sTo10sAndOffAgain_eachWithItsNotice() = runTest {
        val notices = notices()
        runCurrent()
        assertEquals(ShutterTimer.OFF, model.uiState.value.timer)

        val chosen = (1..3).map {
            model.onTimerTap()
            runCurrent()
            model.uiState.value.timer
        }

        assertEquals(listOf(ShutterTimer.THREE, ShutterTimer.TEN, ShutterTimer.OFF), chosen)
        assertEquals(chosen.map { MenuNotice.Timer(it) }, notices)
    }

    @Test
    fun timerTap_whileItCountsDown_changesNothing() = runTest {
        runCurrent()
        setTimer(ShutterTimer.THREE)
        model.onShutter()
        runCurrent()

        model.onTimerTap()

        assertEquals(ShutterTimer.THREE, model.uiState.value.timer)
        assertEquals(3, model.uiState.value.countdown)
    }

    // ---- The shutter -----------------------------------------------------------------------------------------------

    @Test
    fun shutter_withTheTimerOff_takesThePhotoAtOnce_withoutACountdown() = runTest {
        runCurrent()

        val taken = model.onShutter()
        runCurrent()

        assertTrue(taken)
        assertEquals(1, photos)
        assertNull(model.uiState.value.countdown)
        assertTrue(told.isEmpty())
    }

    @Test
    fun shutter_with3s_countsDownASecondEach_andTakesOnePhotoAtItsEnd_notBefore() = runTest {
        val blinks = blinks()
        runCurrent()
        setTimer(ShutterTimer.THREE)

        assertFalse(model.onShutter())
        runCurrent()
        val shown = mutableListOf(model.uiState.value.countdown)
        repeat(2) {
            advanceTimeBy(CameraViewModel.COUNTDOWN_STEP_MS)
            runCurrent()
            shown += model.uiState.value.countdown
        }
        advanceTimeBy(CameraViewModel.COUNTDOWN_STEP_MS - 1)
        runCurrent()
        assertEquals("1 ms before its end", 0, photos)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(3, 2, 1), shown)
        assertEquals(1, photos)
        assertEquals(1, blinks())
        assertNull(model.uiState.value.countdown)
        assertEquals(listOf(3, 2, 1, 0), told)
    }

    @Test
    fun shutter_with10s_takesThePhotoAt10s_andNotBefore() = runTest {
        runCurrent()
        setTimer(ShutterTimer.TEN)

        model.onShutter()
        runCurrent()
        assertEquals(10, model.uiState.value.countdown)
        advanceTimeBy(10 * CameraViewModel.COUNTDOWN_STEP_MS - 1)
        runCurrent()
        assertEquals(0, photos)
        assertEquals(1, model.uiState.value.countdown)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, photos)
        assertEquals((10 downTo 0).toList(), told)
    }

    @Test
    fun secondTap_whileItCountsDown_cancels_withoutAPhoto() = runTest {
        val blinks = blinks()
        runCurrent()
        setTimer(ShutterTimer.THREE)
        model.onShutter()
        advanceTimeBy(1_500)
        runCurrent()

        assertFalse(model.onShutter())
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(0, photos)
        assertEquals(0, blinks())
        assertNull(model.uiState.value.countdown)
        assertEquals(listOf(3, 2, 0), told)
    }

    @Test
    fun leavingTheScreen_cancelsTheCountdown_withoutAPhoto() = runTest {
        runCurrent()
        setTimer(ShutterTimer.THREE)
        model.onShutter()
        runCurrent()

        model.cancelCountdown()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(0, photos)
        assertNull(model.uiState.value.countdown)
        assertEquals(listOf(3, 0), told)
    }

    @Test
    fun leavingTheScreen_withoutACountdown_tellsNothing() = runTest {
        runCurrent()

        model.cancelCountdown()

        assertTrue(told.isEmpty())
    }

    @Test
    fun shutter_onACameraNotReady_startsNoCountdown() = runTest {
        runCurrent()
        setTimer(ShutterTimer.THREE)
        camera.setStatus(CameraStatus.STARTING)
        runCurrent()

        model.onShutter()
        advanceTimeBy(10_000)
        runCurrent()

        assertNull(model.uiState.value.countdown)
        assertEquals(0, photos)
        assertTrue(told.isEmpty())
    }

    @Test
    fun countdownsEnd_onACameraThatWentAway_takesNoPhoto_andSaysSo() = runTest {
        runCurrent()
        setTimer(ShutterTimer.THREE)
        model.onShutter()
        runCurrent()
        camera.setStatus(CameraStatus.UNAVAILABLE)
        runCurrent()

        advanceTimeBy(3 * CameraViewModel.COUNTDOWN_STEP_MS)
        runCurrent()

        assertEquals(0, photos)
        assertEquals(CaptureNotice.CAMERA_UNAVAILABLE, model.uiState.value.captureNotice)
        assertEquals(listOf(3, 2, 1, 0), told)
    }

    // ---- While it counts down ---------------------------------------------------------------------------------------

    @Test
    fun whileItCountsDown_theSwitchTheRatiosTheMenuTheFlashAndTheViewer_areRefused() = runTest {
        runCurrent()
        setTimer(ShutterTimer.THREE)
        model.onShutter()
        runCurrent()

        model.onSwitchCamera()
        model.onAspectChosen(AspectRatio.R1_1)
        model.setAspectsOpen(true)
        model.setMenuOpen(true)
        model.onFlashTap()
        model.openPhotos()
        runCurrent()

        assertTrue(camera.facingRequests.isEmpty())
        assertTrue(camera.aspectRequests.isEmpty())
        assertTrue(camera.flashRequests.isEmpty())
        val state = model.uiState.value
        assertFalse(state.aspectsOpen)
        assertFalse(state.menuOpen)
        assertFalse(state.photosOpen)
        assertEquals(3, state.countdown)
    }

    // ---- The camera menu ------------------------------------------------------------------------------------------

    @Test
    fun theMenuAndTheRatios_eachOpeningClosesTheOther() = runTest {
        runCurrent()

        model.setMenuOpen(true)
        assertTrue(model.uiState.value.menuOpen)
        model.setAspectsOpen(true)
        assertTrue(model.uiState.value.aspectsOpen)
        assertFalse(model.uiState.value.menuOpen)
        model.setMenuOpen(true)

        assertTrue(model.uiState.value.menuOpen)
        assertFalse(model.uiState.value.aspectsOpen)
    }

    @Test
    fun theCountdownsStart_closesTheMenu() = runTest {
        runCurrent()
        setTimer(ShutterTimer.THREE)
        model.setMenuOpen(true)

        model.onShutter()
        runCurrent()

        assertFalse(model.uiState.value.menuOpen)
    }

    @Test
    fun flashTaps_cycleTheFlash_eachWithItsNotice() = runTest {
        val notices = notices()
        runCurrent()

        repeat(3) {
            model.onFlashTap()
            runCurrent()
        }

        val modes = listOf(FlashMode.AUTO, FlashMode.ON, FlashMode.OFF)
        assertEquals(modes, camera.flashRequests)
        assertEquals(modes.map { MenuNotice.Flash(it) }, notices)
    }
}
