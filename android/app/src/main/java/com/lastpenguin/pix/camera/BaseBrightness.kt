// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import kotlin.math.roundToInt

/** The exposure index nearest [ev] for a camera of [step] EV per index, within its [range]. */
fun exposureIndexOf(ev: Float, step: Float, range: IntRange): Int =
    if (step <= 0f || !ev.isFinite()) 0.coerceIn(range) else (ev / step).roundToInt().coerceIn(range)

/**
 * The exposure index of the base brightness [ev] from Settings (FR-1.11) on this camera, or null without exposure
 * compensation. One rule for a tap to focus on Camera and for the subject's tap (FR-7.7), so both set the same value.
 */
fun CameraCapabilities.baseExposureIndex(ev: Float): Int? =
    if (exposureRange.first >= exposureRange.last) null else exposureIndexOf(ev, exposureStep, exposureRange)
