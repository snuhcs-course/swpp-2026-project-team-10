// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap

/**
 * How much bigger the person is in the candidate than in the scene, which the phone measures from the two outlines
 * it cuts (Design 2.6.3, Pose tips).
 */
enum class TipFraming { CLOSER, BACK, KEEP }

/** How to hold the phone for a generated pose, in plain words: the sentence Camera shows (FR-4.10). */
data class PoseTip(val text: String)

/**
 * Asks how to hold the phone for the candidate the user took (Design 2.6.3, Pose tips): once per *Use this pose*,
 * with the scene photo the candidate was made from and the pose it was made with. A failure only means there is no
 * tip; it is never an error on screen.
 * Owner: Pose tips.
 */
interface PoseTipper {
    /** [framing] is what the phone measured, or null when it could not; the server's model then judges it. */
    suspend fun tip(scene: Bitmap, candidate: Bitmap, pose: String, framing: TipFraming?): Result<PoseTip>
}
