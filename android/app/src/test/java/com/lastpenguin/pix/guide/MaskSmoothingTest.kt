// AI-generated with Claude Code, 2026-10-13, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskSmoothingTest {
    @Test
    fun theBlurRadiusFollowsThePersonsHeight() {
        assertEquals(9, smoothRadius(1100))
        assertEquals(1, smoothRadius(20))
    }

    @Test
    fun aSquareKeepsItsEdgeWhereItWas() {
        val field = smoothMask(square(80, 80, 20, 20, 60, 60), 80, 80, radius = 3)

        assertEquals(1f, field[40 * 80 + 40], 0.001f)
        assertEquals(0f, field[2 * 80 + 2], 0.001f)
        // The 0.5 level runs between the last person pixel and the first background pixel.
        assertTrue(field[40 * 80 + 20] >= 0.5f)
        assertTrue(field[40 * 80 + 19] < 0.5f)
        assertTrue(field[40 * 80 + 59] >= 0.5f)
        assertTrue(field[40 * 80 + 60] < 0.5f)
    }

    @Test
    fun aJaggedEdgeBecomesStraight() {
        // A square whose right edge has a one-pixel tooth on every third row.
        val confidence = square(60, 60, 10, 10, 40, 50)
        for (y in 10 until 50 step 3) confidence[y * 60 + 40] = 1f

        val field = smoothMask(confidence, 60, 60, radius = 3)

        // Away from the corners, every row's edge lands on the same column.
        val edges = (20 until 40).map { y -> (0 until 60).last { field[y * 60 + it] >= 0.5f } }.toSet()
        assertEquals(1, edges.size)
    }

    @Test
    fun aSpeckBesideThePersonIsDropped() {
        val confidence = square(100, 100, 10, 10, 60, 90)
        fill(confidence, 100, 80, 50, 84, 54) // 16 px, under 1 % of the person

        val field = smoothMask(confidence, 100, 100, radius = 1)

        assertTrue((48 until 56).all { y -> (78 until 86).all { x -> field[y * 100 + x] < 0.5f } })
        assertTrue(field[50 * 100 + 30] >= 0.5f)
    }

    @Test
    fun twoPeopleAreBothKept() {
        // A couple photo keeps every person (FR-2.5).
        val confidence = square(100, 60, 5, 10, 40, 50)
        fill(confidence, 100, 60, 10, 95, 50)

        val field = smoothMask(confidence, 100, 60, radius = 2)

        assertTrue(field[30 * 100 + 20] >= 0.5f)
        assertTrue(field[30 * 100 + 80] >= 0.5f)
    }

    @Test
    fun aPinholeIsFilledButTheGapBetweenArmAndBodyStays() {
        val confidence = square(120, 120, 10, 10, 110, 110)
        clear(confidence, 120, 30, 30, 33, 33) // 9 px pinhole
        clear(confidence, 120, 60, 40, 80, 80) // 800 px gap, like the space between an arm and the body

        val field = smoothMask(confidence, 120, 120, radius = 1)

        assertTrue(field[31 * 120 + 31] >= 0.5f)
        assertTrue(field[60 * 120 + 70] < 0.5f)
    }

    @Test
    fun backgroundAtThePhotosEdgeIsNotAHole() {
        // The person fills the photo except a small bite at the top edge, which must stay background.
        val confidence = square(60, 60, 0, 0, 60, 60)
        clear(confidence, 60, 28, 0, 31, 2)

        val field = smoothMask(confidence, 60, 60, radius = 1)

        assertTrue(field[0 * 60 + 29] < 0.5f)
    }

    @Test
    fun aPhotoOfOnlySpecksHasNoPersonLeft() {
        val confidence = square(100, 100, 10, 10, 60, 90)
        val field = smoothMask(confidence, 100, 100, radius = 1)
        assertNotNull(foregroundBounds(field, 100, 100))

        val specks = FloatArray(100 * 100)
        fill(specks, 100, 10, 10, 11, 11)
        assertNull(foregroundBounds(smoothMask(specks, 100, 100, radius = 2), 100, 100))
    }

    @Test
    fun alphaKeepsTheHalfLevelAndRampsSteeply() {
        assertEquals(128, smoothedAlpha(0.5f, radius = 9))
        assertEquals(0, smoothedAlpha(0.3f, radius = 9))
        assertEquals(255, smoothedAlpha(0.7f, radius = 9))
        assertTrue(smoothedAlpha(0.45f, radius = 9) in 1 until 128)
    }

    private fun square(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int): FloatArray =
        FloatArray(width * height).also { fill(it, width, left, top, right, bottom) }

    private fun fill(field: FloatArray, width: Int, left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top until bottom) for (x in left until right) field[y * width + x] = 1f
    }

    private fun clear(field: FloatArray, width: Int, left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top until bottom) for (x in left until right) field[y * width + x] = 0f
    }
}
