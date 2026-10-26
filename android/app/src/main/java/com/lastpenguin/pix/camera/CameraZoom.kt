// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.camera

/** Advertise only ratios this camera can actually apply; never imply an unavailable physical lens. */
internal fun cameraZoomStops(minZoom: Float, maxZoom: Float): List<Float> =
    listOf(0.5f, 0.6f, 1f, 2f, 3f).filter { it in minZoom..maxZoom }

/**
 * The ratios a tap on the zoom readout steps through (FR-1.3): the widest of [stops] below 1×, then 1× and 2×, each
 * only where the camera has it.
 */
internal fun quickZoomSteps(stops: List<Float>): List<Float> =
    listOfNotNull(stops.filter { it < 1f }.minOrNull(), 1f.takeIf { it in stops }, 2f.takeIf { it in stops })

/**
 * The step a tap moves [zoom] to: the first of [steps] above it, or the first again from the last one or beyond.
 * A zoom within [STEP_TOLERANCE] of a step counts as that step. Null without steps.
 */
internal fun nextZoomStep(steps: List<Float>, zoom: Float): Float? =
    steps.firstOrNull { it > zoom * (1f + STEP_TOLERANCE) } ?: steps.firstOrNull()

private const val STEP_TOLERANCE = 0.02f

/** Invalid external input is ignored instead of reaching CameraControl as NaN/infinity. */
internal fun clampedCameraZoom(ratio: Float, minZoom: Float, maxZoom: Float): Float? =
    ratio.takeIf { it.isFinite() }?.coerceIn(minZoom, maxZoom)
