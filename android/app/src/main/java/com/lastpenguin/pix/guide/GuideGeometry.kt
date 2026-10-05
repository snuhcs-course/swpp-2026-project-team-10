package com.lastpenguin.pix.guide

import android.graphics.PointF
import android.util.SizeF

/**
 * Maps between normalized coordinates of the 3:4 frame and view pixels (Design 2.6.2).
 * The frame spans 0..1 on each axis; a partially off-frame guide can have its center outside that range.
 * Both phones use it, so the guide lands on the same part of the image.
 * Owner: Camera/Overlay (#6).
 */
object GuideGeometry {

    /**
     * [p] in frame coordinates → pixels in a view showing [frame], fitted and centered.
     * Call after layout: both sizes must be positive and finite, and [p] must be finite.
     */
    fun frameToView(p: PointF, frame: SizeF, view: SizeF): PointF {
        val transform = GuideFrameTransform.fit(frame.width, frame.height, view.width, view.height)
        val mapped = transform.frameToView(p.x, p.y)
        return PointF(mapped.x, mapped.y)
    }

    /** The inverse of [frameToView], with the same input requirements. Letterbox points map outside 0..1. */
    fun viewToFrame(p: PointF, frame: SizeF, view: SizeF): PointF {
        val transform = GuideFrameTransform.fit(frame.width, frame.height, view.width, view.height)
        val mapped = transform.viewToFrame(p.x, p.y)
        return PointF(mapped.x, mapped.y)
    }

    /**
     * Limits height and opacity, keeping 20% of each guide dimension inside the frame (FR-3.2–3.4).
     * If a dimension exceeds five frame lengths, keep the entire frame covered on that axis instead:
     * showing 20% is then impossible without breaking the scale range. [aspect] must be positive and finite.
     * Non-finite state values reset to their defaults; identity, style, and visibility are preserved.
     */
    fun clamp(state: GuideState, aspect: Float): GuideState {
        require(aspect.isFinite() && aspect > 0f) { "Guide aspect must be positive and finite" }
        val height = (state.height.takeIf { it.isFinite() } ?: GuideState.DEFAULT_HEIGHT)
            .coerceIn(GuideState.MIN_HEIGHT, GuideState.MAX_HEIGHT)
        // Height is relative to frame height; width must be relative to the narrower 3:4 frame width.
        val width = height.toDouble() * aspect * 4.0 / 3.0
        return state.copy(
            cx = clampCenter(state.cx, width),
            cy = clampCenter(state.cy, height.toDouble()),
            height = height,
            opacity = (state.opacity.takeIf { it.isFinite() } ?: 0.5f)
                .coerceIn(GuideState.MIN_OPACITY, GuideState.MAX_OPACITY),
        )
    }

    private fun clampCenter(center: Float, extent: Double): Float {
        val visible = minOf(extent * 0.2, 1.0)
        val half = extent / 2.0
        return (center.takeIf { it.isFinite() } ?: 0.5f).toDouble()
            .coerceIn(visible - half, 1.0 - visible + half).toFloat()
    }
}

/** Pure coordinate math so JVM tests exercise the same transform without Android's stub PointF/SizeF. */
internal data class GuidePoint(val x: Float, val y: Float)

@ConsistentCopyVisibility
internal data class GuideFrameTransform private constructor(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun frameToView(x: Float, y: Float): GuidePoint {
        require(x.isFinite() && y.isFinite()) { "Frame coordinates must be finite" }
        return GuidePoint(left + x * width, top + y * height)
    }

    fun viewToFrame(x: Float, y: Float): GuidePoint {
        require(x.isFinite() && y.isFinite()) { "View coordinates must be finite" }
        return GuidePoint((x - left) / width, (y - top) / height)
    }

    companion object {
        fun fit(frameWidth: Float, frameHeight: Float, viewWidth: Float, viewHeight: Float): GuideFrameTransform {
            require(frameWidth.isFinite() && frameWidth > 0f && frameHeight.isFinite() && frameHeight > 0f) {
                "Frame size must be positive and finite"
            }
            require(viewWidth.isFinite() && viewWidth > 0f && viewHeight.isFinite() && viewHeight > 0f) {
                "View size must be positive and finite; wait until layout"
            }
            val scale = minOf(viewWidth.toDouble() / frameWidth, viewHeight.toDouble() / frameHeight)
            val width = (frameWidth * scale).toFloat()
            val height = (frameHeight * scale).toFloat()
            require(width > 0f && height > 0f) { "Fitted frame is too small to represent" }
            return GuideFrameTransform((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
        }
    }
}
