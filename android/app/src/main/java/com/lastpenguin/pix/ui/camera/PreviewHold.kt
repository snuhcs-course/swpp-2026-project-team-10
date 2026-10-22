// AI-generated with Claude Code, 2026-10-22, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import com.lastpenguin.pix.camera.CameraStatus

/**
 * When the Camera screen keeps the preview's last frame on screen instead of a blank preview and a spinner (NFR-19,
 * Design 2.6.2, 2.6.5): from a ratio change, or from the moment the camera leaves READY for STARTING while the
 * preview streams, as a lens switch does, until the rebound preview streams again. The camera reports STARTING
 * before it unbinds, so the frame is still the old one.
 *
 * [show] puts the frame up and returns false when there is none; [hide] takes it down. A time limit is the
 * screen's: it calls [release].
 */
internal class PreviewHold(
    private val show: () -> Boolean,
    private val hide: () -> Unit,
) {
    var holding = false
        private set

    private var status: CameraStatus? = null
    private var streaming = false
    private var sawIdle = false

    /** Before a ratio change, which rebinds the camera while it still reports READY. */
    fun hold() {
        if (holding || !streaming || !show()) return
        holding = true
        sawIdle = false
    }

    fun onStatus(next: CameraStatus) {
        val previous = status
        status = next
        when {
            previous == CameraStatus.READY && next == CameraStatus.STARTING -> hold()

            !holding -> Unit

            next == CameraStatus.IDLE || next == CameraStatus.UNAVAILABLE -> release()

            // The preview streamed on through STARTING, as when a zoom is restored after a reopen: nothing to wait for.
            previous != CameraStatus.READY && next == CameraStatus.READY && streaming && !sawIdle -> release()
        }
    }

    /** The preview's stream state: streaming or idle. */
    fun onStream(isStreaming: Boolean) {
        streaming = isStreaming
        if (!holding) return
        if (!isStreaming) {
            sawIdle = true
        } else if (sawIdle) {
            release()
        }
    }

    fun release() {
        if (!holding) return
        holding = false
        hide()
    }
}
