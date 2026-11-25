// AI-generated with Claude Code, 2026-11-25, reviewed by Dongje Park
package com.lastpenguin.pix.camera

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * A tap's glide from one zoom to another (FR-1.3, #310; Testing Documentation 2.1.6), plain JVM.
 * Time (boundary values): before the start and at it (the first ratio), a frame before its end (not there yet), a
 * nanosecond before it (not over), and at its end and after it (the step itself, exactly, and over).
 * Direction: in (rising at every frame) and out (falling), never past either end.
 * Easing: it moves from its first frame, the first quarter of the time covering more than a quarter of the way on the
 * logarithm and each later quarter less than the one before, so it is fastest at the tap and slow into the step.
 * Worth a glide: a step of more than a hundredth of the ratio; a pinch's last value where the picture already is, and
 * a ratio that is none, are not.
 */
class ZoomGlideTest {
    private val start = 5_000_000_000L
    private val length = ZoomGlide.DURATION_MS * 1_000_000L

    private fun wayAt(glide: ZoomGlide, share: Double): Double {
        val ratio = glide.at(start + (length * share).toLong())
        return (ln(ratio.toDouble()) - ln(glide.from.toDouble())) /
            (ln(glide.to.toDouble()) - ln(glide.from.toDouble()))
    }

    @Test
    fun itStartsAtTheFirstRatio_isHalfwayOnTheLogarithmAtItsMiddle_andEndsAtTheStepItself() {
        val glide = ZoomGlide(1f, 2f, start)

        assertEquals(1f, glide.at(start - 1_000), 0f)
        assertEquals(1f, glide.at(start), 0f)
        // A frame before its end it is not there yet; a nanosecond before, it is not over, though a Float shows 2×.
        assertTrue(glide.at(start + length - 16_666_667L) < 2f)
        assertFalse(glide.endsBy(start + length - 1))
        assertEquals(2f, glide.at(start + length), 0f)
        assertTrue(glide.endsBy(start + length))
        assertEquals(2f, glide.at(start + length * 10), 0f)
    }

    @Test
    fun itRisesAtEveryFrameOnTheWayIn_andFallsOnTheWayOut_neverPastEitherEnd() {
        val frame = 16_666_667L
        for ((from, to) in listOf(1f to 2f, 2f to 0.6f, 0.6f to 1f)) {
            val glide = ZoomGlide(from, to, start)
            var last = from
            var now = start + frame
            while (!glide.endsBy(now)) {
                val ratio = glide.at(now)
                if (to > from) assertTrue("$from to $to at $now", ratio > last) else assertTrue(ratio < last)
                assertTrue(ratio in minOf(from, to)..maxOf(from, to))
                last = ratio
                now += frame
            }
            assertEquals(to, glide.at(now), 0f)
        }
    }

    @Test
    fun itMovesFromItsFirstFrame_fastestAtTheTap_andSlowIntoTheStep() {
        val glide = ZoomGlide(1f, 2f, start)

        // One display frame after the tap the picture has already moved, more than its share of the time.
        assertTrue(wayAt(glide, 16.0 / ZoomGlide.DURATION_MS) > 16.0 / ZoomGlide.DURATION_MS)
        val quarters = listOf(0.25, 0.5, 0.75, 1.0).map { wayAt(glide, it) }
        val covered = quarters.zip(listOf(0.0) + quarters).map { (now, before) -> now - before }
        assertTrue(covered[0] > 0.25)
        assertTrue(covered[0] > covered[1] && covered[1] > covered[2] && covered[2] > covered[3])
        assertTrue(covered[3] < 0.1)
    }

    @Test
    fun aStepOfMoreThanAHundredthIsWorthAGlide_andAPinchsLastValueWhereThePictureIsIsNot() {
        assertTrue(ZoomGlide.worth(1f, 2f))
        assertTrue(ZoomGlide.worth(2f, 0.6f))
        assertTrue(ZoomGlide.worth(1f, 1.02f))
        assertFalse(ZoomGlide.worth(2f, 2f))
        assertFalse(ZoomGlide.worth(2f, 2.01f))
        assertFalse(ZoomGlide.worth(0f, 2f))
        assertFalse(ZoomGlide.worth(1f, Float.NaN))
    }
}
