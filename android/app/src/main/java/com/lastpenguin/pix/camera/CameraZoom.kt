// AI-generated with ChatGPT Codex, 2026-10-04, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

/** Advertise only ratios this camera can actually apply; never imply an unavailable physical lens. */
internal fun cameraZoomStops(minZoom: Float, maxZoom: Float): List<Float> =
    listOf(0.5f, 0.6f, 1f, 2f, 3f).filter { it in minZoom..maxZoom }

/** Invalid external input is ignored instead of reaching CameraControl as NaN/infinity. */
internal fun clampedCameraZoom(ratio: Float, minZoom: Float, maxZoom: Float): Float? =
    ratio.takeIf { it.isFinite() }?.coerceIn(minZoom, maxZoom)
