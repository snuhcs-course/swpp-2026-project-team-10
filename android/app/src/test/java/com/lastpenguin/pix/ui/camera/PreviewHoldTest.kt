// AI-generated with Claude Code, 2026-10-22, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import com.lastpenguin.pix.camera.CameraStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewHoldTest {
    private var frame = true
    private var shown = 0
    private var hidden = 0
    private val hold = PreviewHold(
        show = {
            if (frame) shown++
            frame
        },
        hide = { hidden++ },
    )

    private fun streamingAndReady() {
        hold.onStream(true)
        hold.onStatus(CameraStatus.READY)
    }

    @Test
    fun aLensSwitchHoldsTheFrameUntilTheReboundPreviewStreams() {
        streamingAndReady()

        hold.onStatus(CameraStatus.STARTING)
        assertTrue(hold.holding)
        assertEquals(1, shown)

        hold.onStream(false)
        hold.onStatus(CameraStatus.READY)
        // READY can come before the new preview's first frame: the frame stays until the stream is back.
        assertTrue(hold.holding)
        hold.onStream(true)
        assertFalse(hold.holding)
        assertEquals(1, hidden)
    }

    @Test
    fun aRatioChangeHoldsWhileTheCameraStillReportsReady() {
        streamingAndReady()

        hold.hold()
        hold.onStatus(CameraStatus.READY)
        hold.onStatus(CameraStatus.STARTING)
        assertTrue(hold.holding)
        assertEquals(1, shown)

        hold.onStream(false)
        hold.onStream(true)
        assertFalse(hold.holding)
        assertEquals(1, hidden)
    }

    @Test
    fun aStartingThatNeverStoppedThePreviewEndsAtReady() {
        streamingAndReady()

        hold.onStatus(CameraStatus.STARTING)
        // The same stream state again is not a restart.
        hold.onStream(true)
        assertTrue(hold.holding)
        hold.onStatus(CameraStatus.READY)
        assertFalse(hold.holding)
        assertEquals(1, hidden)
    }

    @Test
    fun nothingIsHeldWithoutAStreamingPreviewOrAFrame() {
        hold.onStatus(CameraStatus.READY)
        hold.onStatus(CameraStatus.STARTING)
        assertFalse(hold.holding)

        hold.onStream(true)
        hold.onStatus(CameraStatus.READY)
        frame = false
        hold.onStatus(CameraStatus.STARTING)
        assertFalse(hold.holding)
        hold.hold()
        assertFalse(hold.holding)
        assertEquals(0, shown)
    }

    @Test
    fun startingUpIsNotHeld() {
        hold.onStatus(CameraStatus.IDLE)
        hold.onStatus(CameraStatus.STARTING)
        hold.onStream(true)
        hold.onStatus(CameraStatus.READY)

        assertFalse(hold.holding)
        assertEquals(0, shown)
    }

    @Test
    fun aCameraThatStopsOrFailsDropsTheFrame() {
        for (end in listOf(CameraStatus.UNAVAILABLE, CameraStatus.IDLE)) {
            streamingAndReady()
            hold.onStatus(CameraStatus.STARTING)
            assertTrue(hold.holding)
            hold.onStatus(end)
            assertFalse(hold.holding)
        }
        assertEquals(2, hidden)
    }

    @Test
    fun releaseIsIdempotentAndASecondHoldDoesNotShowAgain() {
        streamingAndReady()
        hold.hold()
        hold.hold()
        assertEquals(1, shown)

        hold.release()
        hold.release()
        assertEquals(1, hidden)
        assertFalse(hold.holding)
    }
}
