// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.camera

import kotlin.math.roundToInt

/** The exposure index nearest [ev] for a camera of [step] EV per index, within its [range]. */
fun exposureIndexOf(ev: Float, step: Float, range: IntRange): Int =
    if (step <= 0f || !ev.isFinite()) 0.coerceIn(range) else (ev / step).roundToInt().coerceIn(range)

/**
 * The exposure index of 0 EV on this camera, the brightness each tap to focus goes back to (FR-1.11), or null without
 * exposure compensation. One rule for a tap to focus on Camera and for the subject's tap (FR-7.7), so both set the same
 * value. A range that does not hold 0 gives its nearest end.
 */
fun CameraCapabilities.baseExposureIndex(): Int? =
    if (exposureRange.first >= exposureRange.last) null else exposureIndexOf(0f, exposureStep, exposureRange)
