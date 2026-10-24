// AI-generated with Claude Code, 2026-10-22, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import com.lastpenguin.pix.camera.CameraStatus

/**
 * When the Camera screen keeps the preview's last frame on screen instead of a blank preview and a spinner (NFR-19,
 * Design 2.6.2, 2.6.5): from a ratio change, or from the moment the camera leaves READY for STARTING while the
 * preview streams, as a lens switch does, until the camera is READY again with the preview streaming. The camera
 * reports STARTING before it unbinds, so the frame is still the old one. Streaming alone is not enough: a new lens
 * streams at its own 1× before its zoom is restored, and only READY says the restored zoom is applied.
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

    // Whether the camera left READY or the preview stopped since the hold began; until then, READY with a streaming
    // preview is still the old binding.
    private var interrupted = false

    /**
     * Before a ratio change, which rebinds the camera while it still reports READY. A frame on screen is enough: the
     * preview's first STREAMING report can come after the camera is READY.
     */
    fun hold() {
        if (holding || !show()) return
        holding = true
        interrupted = false
    }

    fun onStatus(next: CameraStatus) {
        val previous = status
        status = next
        when {
            previous == CameraStatus.READY && next == CameraStatus.STARTING -> {
                if (streaming) hold()
                if (holding) interrupted = true
            }

            !holding -> Unit

            next == CameraStatus.IDLE || next == CameraStatus.UNAVAILABLE -> release()

            next != CameraStatus.READY -> interrupted = true

            else -> releaseIfBack()
        }
    }

    /** The preview's stream state: streaming or idle. */
    fun onStream(isStreaming: Boolean) {
        streaming = isStreaming
        if (!holding) return
        if (isStreaming) releaseIfBack() else interrupted = true
    }

    fun release() {
        if (!holding) return
        holding = false
        hide()
    }

    private fun releaseIfBack() {
        if (interrupted && status == CameraStatus.READY && streaming) release()
    }
}
