// AI-generated with Claude Code, 2026-10-16, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import com.lastpenguin.pix.session.fakeGuide
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Partitions for where a new guide starts (FR-3.1, Design 2.6.2).
 * Anchor: none (the subject's copy), a box inside the photo, a box smaller than the minimum height, and non-finite
 * values. Reference ratio: the same as the frame, and another (a landscape photo in a portrait frame, a tall photo
 * in a 1:1 frame); the frame is 3:4, 9:16, or 1:1.
 */
class GuideStartTest {
    @Test
    fun start_noAnchor_startsCenteredAtTheDefaultHeight() {
        val state = GuideGeometry.start(fakeGuide("g", aspect = 0.5f))

        assertEquals(GuideState(guideId = "g"), state)
    }

    @Test
    fun start_boxInAPhotoOfTheFramesRatio_keepsTheBoxHeightAndCenter() {
        // A 960×1280 photo with the person's box 288×960 px: aspect .3.
        val guide = fakeGuide("g", aspect = 0.3f).copy(anchor = NormalizedRect(0.3f, 0.2f, 0.6f, 0.95f))

        val state = GuideGeometry.start(guide, frameAspect = 3f / 4f)

        assertState(cx = 0.45f, cy = 0.575f, height = 0.75f, state)
        assertEquals("g", state.guideId)
    }

    @Test
    fun start_referenceOfAnotherRatio_keepsTheHeightFractionAndTheCenterInEveryFrame() {
        // A landscape 1280×960 photo with the person's box 256×768 px: aspect 1/3.
        val landscape = fakeGuide("wide", aspect = 1f / 3f).copy(anchor = NormalizedRect(0.6f, 0.1f, 0.8f, 0.9f))
        // A 720×1280 photo with the person's box 432×640 px: aspect .675.
        val tall = fakeGuide("tall", aspect = 0.675f).copy(anchor = NormalizedRect(0.1f, 0.4f, 0.7f, 0.9f))

        for (frameAspect in listOf(3f / 4f, 9f / 16f, 1f)) {
            assertState(cx = 0.7f, cy = 0.5f, height = 0.8f, GuideGeometry.start(landscape, frameAspect))
            assertState(cx = 0.4f, cy = 0.65f, height = 0.5f, GuideGeometry.start(tall, frameAspect))
        }
    }

    @Test
    fun start_personSmallerThanTheMinimum_startsAtTheMinimumHeightAroundTheSameCenter() {
        val far = fakeGuide("far", aspect = 0.4f).copy(anchor = NormalizedRect(0.7f, 0.02f, 0.76f, 0.12f))

        val state = GuideGeometry.start(far)

        assertState(cx = 0.73f, cy = 0.07f, height = GuideState.MIN_HEIGHT, state)
    }

    @Test
    fun start_nonFiniteAnchor_fallsBackToTheDefaultPlace() {
        val broken = fakeGuide("g").copy(anchor = NormalizedRect(Float.NaN, 0.2f, 0.6f, Float.POSITIVE_INFINITY))

        assertEquals(GuideState(guideId = "g"), GuideGeometry.start(broken))
    }

    private fun assertState(cx: Float, cy: Float, height: Float, state: GuideState) {
        assertEquals(cx, state.cx, EPSILON)
        assertEquals(cy, state.cy, EPSILON)
        assertEquals(height, state.height, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
