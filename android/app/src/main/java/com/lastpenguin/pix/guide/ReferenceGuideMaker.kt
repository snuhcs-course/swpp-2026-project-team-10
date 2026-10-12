// AI-generated with Claude Code, 2026-10-01, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.net.Uri

/** No person could be separated from the background (FR-2.4). */
class NoPersonFoundException : Exception("No person found in the reference photo")

/**
 * Turns a photo into a [ReferenceGuide] on the device (Design 2.6.1).
 * Fails with [NoPersonFoundException] when there is no person.
 */
interface ReferenceGuideMaker {
    /** A photo picked from the gallery. */
    suspend fun make(source: Uri): Result<ReferenceGuide>

    /** A selected pose candidate. */
    suspend fun make(bitmap: Bitmap, source: GuideSource): Result<ReferenceGuide>
}
