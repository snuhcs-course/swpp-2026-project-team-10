package com.lastpenguin.pix.guide

import android.graphics.Bitmap

/**
 * Draws the person's outline from a mask (Design 2.6.1, step 5).
 * The subject runs the same step on the received cutout's alpha channel.
 * Owner: PM (#5).
 */
class OutlineExtractor {
    /** Edge pixels of [mask] (alpha ≥ 0.5) drawn as a light [strokePx] stroke on a transparent bitmap. */
    fun extract(mask: Bitmap, strokePx: Float): Bitmap =
        TODO("#5: threshold, 8-neighbor edge, dilate to 2–3 px")
}
