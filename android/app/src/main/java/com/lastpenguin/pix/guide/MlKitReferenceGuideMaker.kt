package com.lastpenguin.pix.guide

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * [ReferenceGuideMaker] on the device: load, segment, crop, outline (Design 2.6.1).
 * Owner: PM (#5).
 */
class MlKitReferenceGuideMaker(
    private val context: Context,
    private val segmenter: SubjectSegmenter,
    private val outlineExtractor: OutlineExtractor,
) : ReferenceGuideMaker {

    override suspend fun make(source: Uri): Result<ReferenceGuide> =
        TODO("#5: decode with EXIF rotation, long side 1280 px, then make(bitmap, GALLERY)")

    override suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide> =
        TODO("#5: segment, crop to bounds + 2% margin, outline; ≤ 2 s (NFR-3)")
}
