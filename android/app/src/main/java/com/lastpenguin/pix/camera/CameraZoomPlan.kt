// AI-generated with ChatGPT Codex, 2026-10-05, reviewed by Joonhyung Han
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

    fun rangeFor(lens: ZoomLens): LensZoomRange =
        when (lens) {
            ZoomLens.PRIMARY -> primary
            ZoomLens.ULTRAWIDE -> requireNotNull(ultrawide) { "No usable ultrawide camera" }
        }
}
