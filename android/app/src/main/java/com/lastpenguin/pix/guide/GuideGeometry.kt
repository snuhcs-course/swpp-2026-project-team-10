package com.lastpenguin.pix.guide

import android.graphics.PointF
import android.util.SizeF

/**
 * Maps between frame coordinates (0..1 of the 3:4 frame) and view pixels (Design 2.6.2).
 * Both phones use it, so the guide lands on the same part of the image.
 * Owner: Camera/Overlay (#6).
 */
object GuideGeometry {

    /** [p] in frame coordinates → pixels in a view of size [view] that shows a frame of size [frame], fitted and centered. */
    fun frameToView(p: PointF, frame: SizeF, view: SizeF): PointF =
        TODO("#6: x = ox + cx·W·s, y = oy + cy·H·s")

    /** The inverse of [frameToView]. */
    fun viewToFrame(p: PointF, frame: SizeF, view: SizeF): PointF =
        TODO("#6: cx = (x − ox) / (W·s), cy = (y − oy) / (H·s)")

    /** Limits height to 0.21–2.1 and keeps at least 20% of the guide inside the frame (FR-3.2, FR-3.3). */
    fun clamp(state: GuideState, aspect: Float): GuideState =
        TODO("#6")
}
