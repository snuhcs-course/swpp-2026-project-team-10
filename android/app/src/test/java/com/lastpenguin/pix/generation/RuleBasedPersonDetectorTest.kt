// AI-generated with Claude Code, 2026-11-03, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The person check's rule (FR-4.13, Design 2.6.3), with ML Kit's signals faked. Partitions: a pose with all 33
 * landmarks in the photo; a selfie's 13; exactly 8 and 7 at the likelihood 0.5, and 8 just under it; no pose, with and
 * without a face; a weak pose with a face. Boundaries: 8 landmarks, the likelihood 0.5. Faces are asked for only when
 * the pose found no one.
 */
class RuleBasedPersonDetectorTest {
    private val scene = testBitmap()

    private class Signals(private val likelihoods: List<Float>, private val faces: Int) : PersonSignals {
        var faceChecks = 0

        override suspend fun poseLikelihoods(scene: Bitmap): List<Float> = likelihoods

        override suspend fun faceCount(scene: Bitmap): Int {
            faceChecks++
            return faces
        }
    }

    private fun pose(visible: Int, likelihood: Float = 0.9f) =
        List(visible) { likelihood } + List(33 - visible) { 0.1f }

    @Test
    fun aWholePoseIsAPerson_withoutAskingForFaces() = runTest {
        val signals = Signals(pose(33), faces = 0)
        assertTrue(RuleBasedPersonDetector(signals).findsPerson(scene))
        assertEquals(0, signals.faceChecks)
    }

    @Test
    fun aSelfiesHeadAndShouldersAreAPerson() = runTest {
        assertTrue(RuleBasedPersonDetector(Signals(pose(13), faces = 0)).findsPerson(scene))
    }

    @Test
    fun eightLandmarksAtTheLikelihoodAreAPerson_sevenAreNot() = runTest {
        assertTrue(RuleBasedPersonDetector(Signals(pose(8, likelihood = 0.5f), faces = 0)).findsPerson(scene))
        assertFalse(RuleBasedPersonDetector(Signals(pose(7, likelihood = 0.5f), faces = 0)).findsPerson(scene))
        assertFalse(RuleBasedPersonDetector(Signals(pose(8, likelihood = 0.49f), faces = 0)).findsPerson(scene))
    }

    @Test
    fun withoutAPoseAFaceIsAPerson() = runTest {
        val signals = Signals(emptyList(), faces = 1)
        assertTrue(RuleBasedPersonDetector(signals).findsPerson(scene))
        assertEquals(1, signals.faceChecks)
        // A weak pose, like the accurate model's on bare rocks in the trial, with a face.
        assertTrue(RuleBasedPersonDetector(Signals(pose(5), faces = 2)).findsPerson(scene))
    }

    @Test
    fun neitherAPoseNorAFaceIsNoPerson() = runTest {
        assertFalse(RuleBasedPersonDetector(Signals(emptyList(), faces = 0)).findsPerson(scene))
        assertFalse(RuleBasedPersonDetector(Signals(pose(5), faces = 0)).findsPerson(scene))
    }
}
