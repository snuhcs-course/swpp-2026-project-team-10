package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.session.protocol.SessionMessage

/**
 * Photographer: applies the subject's remote zoom and echoes the result (Design 2.6.5).
 * Owner: Real-time (#10).
 */
class RemoteControlHandler(
    private val session: SessionManager,
    private val camera: CameraController,
) {
    /** Handles `camera.zoom.set`: clamp, camera.setZoom, then reply with `camera.state`. */
    fun handle(message: SessionMessage) {
        TODO("#10")
    }
}
