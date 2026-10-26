// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap

/** How to hold the phone for a generated pose, in plain words: the sentence Camera shows (FR-4.10). */
data class PoseTip(val text: String)

/**
 * Asks how to hold the phone for the candidate the user took (Design 2.6.3, Pose tips): once per *Use this pose*,
 * with the scene photo the candidate was made from and the pose it was made with. A failure only means there is no
 * tip; it is never an error on screen.
 * Owner: Pose tips.
 */
interface PoseTipper {
    suspend fun tip(scene: Bitmap, candidate: Bitmap, pose: String): Result<PoseTip>
}
