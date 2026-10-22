// AI-generated with ChatGPT Codex and Claude Code, 2026-10-22, reviewed by Sungmin Jo
package com.lastpenguin.pix.camera

/** CameraX ratios are relative to each lens; the app's ratios are relative to the primary lens. */
internal class LensZoomRange private constructor(
    val intrinsicZoom: Float,
    val minNativeZoom: Float,
    val maxNativeZoom: Float,
) {
    val minZoom: Float = intrinsicZoom * minNativeZoom
    val maxZoom: Float = intrinsicZoom * maxNativeZoom

    fun nativeZoom(ratio: Float): Float =
        (ratio.coerceIn(minZoom, maxZoom) / intrinsicZoom).coerceIn(minNativeZoom, maxNativeZoom)

    fun appZoom(nativeRatio: Float): Float =
        (nativeRatio.coerceIn(minNativeZoom, maxNativeZoom) * intrinsicZoom).coerceIn(minZoom, maxZoom)

    companion object {
        fun create(intrinsic: Float, min: Float, max: Float): LensZoomRange? {
            if (!intrinsic.isFinite() || !min.isFinite() || !max.isFinite()) return null
            if (intrinsic <= 0f || min <= 0f || max < min) return null
            val appMin = intrinsic * min
            val appMax = intrinsic * max
            if (!appMin.isFinite() || !appMax.isFinite() || appMin <= 0f || appMax <= 0f) return null
            return LensZoomRange(intrinsic, min, max)
        }
    }
}

internal enum class ZoomLens {
    PRIMARY,
    ULTRAWIDE,
}

/**
 * Prefer the primary logical camera wherever it can zoom itself. A separately exposed ultrawide is
 * usable only when its range joins the primary range without an unsupported gap. Never advertise a
 * manufactured 0.5x minimum on hardware whose widest available field of view is 0.6x.
 */
internal class CameraZoomPlan(
    val primary: LensZoomRange,
    ultrawide: LensZoomRange? = null,
) {
    val ultrawide: LensZoomRange? =
        ultrawide?.takeIf { it.minZoom < primary.minZoom && it.maxZoom >= primary.minZoom }

    val minZoom: Float = this.ultrawide?.minZoom ?: primary.minZoom
    val maxZoom: Float = primary.maxZoom

    fun clamp(ratio: Float): Float? = clampedCameraZoom(ratio, minZoom, maxZoom)

    fun lensFor(ratio: Float): ZoomLens =
        if (ultrawide != null && ratio < primary.minZoom) ZoomLens.ULTRAWIDE else ZoomLens.PRIMARY

    /**
     * The lens for [ratio] while [current] is bound. Each switch rebinds the camera, so during a pinch ([final] false)
     * the lens stays until the ratio is [HOLD_BAND] past the primary's minimum: a pinch that wavers around 1× keeps
     * one lens, the primary showing its minimum and the ultrawide cropping up to the band. The last value of a pinch
     * settles on [lensFor].
     */
    fun lensFor(ratio: Float, current: ZoomLens, final: Boolean): ZoomLens {
        val ultrawide = ultrawide
        if (final || ultrawide == null) return lensFor(ratio)
        return when (current) {
            ZoomLens.PRIMARY ->
                if (ratio < primary.minZoom * (1f - HOLD_BAND)) ZoomLens.ULTRAWIDE else ZoomLens.PRIMARY

            ZoomLens.ULTRAWIDE ->
                if (ratio > primary.minZoom * (1f + HOLD_BAND) || ratio > ultrawide.maxZoom) {
                    ZoomLens.PRIMARY
                } else {
                    ZoomLens.ULTRAWIDE
                }
        }
    }

    fun rangeFor(lens: ZoomLens): LensZoomRange =
        when (lens) {
            ZoomLens.PRIMARY -> primary
            ZoomLens.ULTRAWIDE -> requireNotNull(ultrawide) { "No usable ultrawide camera" }
        }

    companion object {
        /** How far past the primary's minimum, as a share of it, a pinch goes before the lens changes. */
        const val HOLD_BAND = 0.05f
    }
}
