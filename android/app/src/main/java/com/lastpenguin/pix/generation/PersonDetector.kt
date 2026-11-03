// AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap

/**
 * Looks for a person in the scene photo on the phone, before anything is sent (FR-4.13, Design 2.6.3): poses are made
 * of the person in the photo, so a photo without one is not sent.
 * Owner: Server/AI/Sync (#7).
 */
fun interface PersonDetector {
    /** Whether [scene] shows a person. Throws when the check cannot be made; the photo then goes on. */
    suspend fun findsPerson(scene: Bitmap): Boolean
}

/** What on-device models see in a photo, for [RuleBasedPersonDetector]. */
interface PersonSignals {
    /** How likely each landmark of the pose found in [scene] is inside the photo, 0..1; empty without a pose. */
    suspend fun poseLikelihoods(scene: Bitmap): List<Float>

    /** How many faces [scene] shows. */
    suspend fun faceCount(scene: Bitmap): Int
}

/**
 * A person is a pose with at least [MIN_VISIBLE_LANDMARKS] landmarks in the photo, or else a face (Design 2.6.3). Pose
 * detection alone missed a person sitting with the head down and two people far away, each of whom showed a face;
 * the faces are asked for only when the pose found no one.
 */
class RuleBasedPersonDetector(private val signals: PersonSignals) : PersonDetector {
    override suspend fun findsPerson(scene: Bitmap): Boolean {
        val visible = signals.poseLikelihoods(scene).count { it >= MIN_LIKELIHOOD }
        return visible >= MIN_VISIBLE_LANDMARKS || signals.faceCount(scene) > 0
    }

    companion object {
        /** A landmark counts as in the photo from this likelihood on. */
        const val MIN_LIKELIHOOD = 0.5f

        /**
         * A selfie shows 13 of the 33 landmarks (the face and the shoulders), the fewest of any photo with a person in
         * the trial on 2026-11-03; a weak pose, like the 5 landmarks the accurate model found on bare rocks, is none.
         */
        const val MIN_VISIBLE_LANDMARKS = 8
    }
}
