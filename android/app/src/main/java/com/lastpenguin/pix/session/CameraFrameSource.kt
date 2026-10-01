package com.lastpenguin.pix.session

import androidx.camera.core.ImageProxy
import com.lastpenguin.pix.camera.FrameSink

/**
 * Feeds camera frames into the WebRTC video source (Design 2.6.4).
 * The photographer passes it to CameraController.setFrameSink while connected.
 * Owner: Real-time (#8).
 */
class CameraFrameSource : FrameSink {
    override fun onFrame(image: ImageProxy) {
        TODO("#8: copy into an I420 buffer with the rotation, then close the image")
    }
}
