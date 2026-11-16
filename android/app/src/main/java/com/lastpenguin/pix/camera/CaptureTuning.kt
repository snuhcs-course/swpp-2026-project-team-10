// AI-generated with Claude Code, 2026-11-16, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector

/**
 * How Pix asks CameraX for a photo: every setting of the still capture, in one place, with what each was measured to
 * do on the Galaxy S22 (Design 2.8; Testing Documentation, NFR-19). A phone that lacks what a setting asks for gets
 * CameraX's own fallback, which is the photo Pix took before.
 */
internal object CaptureTuning {
    /**
     * `MINIMIZE_LATENCY`, not `MAXIMIZE_QUALITY`. `MAXIMIZE_QUALITY` locks the focus again before every photo, so the
     * picture was taken about 0.7 s after the press; without the lock it is 0.26 s (front) to 0.40 s (rear, in the dark).
     * The photos are the same: the mode changes nothing else but the JPEG quality, which [JPEG_QUALITY] keeps.
     *
     * `ZERO_SHUTTER_LAG` is not used. It was faster still (0.18 s, and the preview stopped for 0.1 s instead of 0.35 s),
     * but its photos carry twice the noise, and CameraX turns it off on the Galaxy S22's US models for its colors
     * (`ZslDisablerQuirk`), a list the Korean model is not on.
     */
    const val CAPTURE_MODE = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY

    /** As before, when `MAXIMIZE_QUALITY` set it. 95, the mode's own, halves the file and saves 0.14 s sooner. */
    const val JPEG_QUALITY = 100

    /**
     * The largest photo, also a size the camera gives more slowly. On the S22 it changes nothing: 4080 × 3060 on the
     * rear camera and 3648 × 2736 on the front one either way, with the same timings.
     */
    const val RESOLUTION_MODE = ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
}
