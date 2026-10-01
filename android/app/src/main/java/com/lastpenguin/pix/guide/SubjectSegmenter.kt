package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect

/** What the segmenter found in one photo (Design 2.6.1). */
class Segmentation(
    /** The person with a transparent background, same size as the input. */
    val foreground: Bitmap,
    /** Foreground confidence as alpha (ALPHA_8), same size as the input. */
    val mask: Bitmap,
    /** Bounding box of the pixels with confidence ≥ 0.5. */
    val bounds: Rect,
)

/**
 * ML Kit subject segmentation (Design 2.6.1, steps 2–3).
 * Owner: PM (#5).
 */
class SubjectSegmenter(private val context: Context) {
    /** Fails with [NoPersonFoundException] if fewer than 2% of the pixels have confidence ≥ 0.5. */
    suspend fun segment(bitmap: Bitmap): Result<Segmentation> =
        TODO("#5: ML Kit subject segmenter with the foreground bitmap and confidence mask")
}
