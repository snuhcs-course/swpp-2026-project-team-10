package com.lastpenguin.pix.guide

import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideGeometryTest {
    @Test
    fun tallViewMapsIntoTheImageBelowTheLetterbox() {
        val transform = GuideFrameTransform.fit(720f, 960f, 1080f, 1920f)
        assertPoint(0f, 240f, transform.frameToView(0f, 0f))
        assertPoint(1080f, 1680f, transform.frameToView(1f, 1f))
        assertPoint(270f, 960f, transform.frameToView(0.25f, 0.5f))
        assertEquals(1008f, GuideState.DEFAULT_HEIGHT * transform.height, EPSILON)
    }

    @Test
    fun wideViewMapsIntoTheImageBetweenSideBars() {
        val transform = GuideFrameTransform.fit(720f, 960f, 1200f, 800f)
        assertPoint(300f, 0f, transform.frameToView(0f, 0f))
        assertPoint(900f, 800f, transform.frameToView(1f, 1f))
        assertPoint(600f, 400f, transform.frameToView(0.5f, 0.5f))
    }

    @Test
    fun matchingAspectUsesTheWholeView() {
        val transform = GuideFrameTransform.fit(720f, 960f, 900f, 1200f)
        assertPoint(0f, 0f, transform.frameToView(0f, 0f))
        assertPoint(900f, 1200f, transform.frameToView(1f, 1f))
        assertPoint(0.25f, 0.75f, transform.viewToFrame(225f, 900f))
    }

    @Test
    fun conversionSupportsTheSuppliedFrameAspect() {
        val transform = GuideFrameTransform.fit(1600f, 900f, 800f, 800f)
        assertPoint(0f, 175f, transform.frameToView(0f, 0f))
        assertPoint(800f, 625f, transform.frameToView(1f, 1f))
    }

    @Test
    fun inversePreservesPositionsAcrossDifferentViewsIncludingOffFrameCenters() {
        val points = listOf(GuidePoint(0f, 0f), GuidePoint(0.32f, 0.55f), GuidePoint(-0.2f, 1.4f))
        for ((width, height) in listOf(1080f to 1920f, 1200f to 800f, 900f to 1200f)) {
            val transform = GuideFrameTransform.fit(720f, 960f, width, height)
            for (point in points) {
                val pixels = transform.frameToView(point.x, point.y)
                assertPoint(point.x, point.y, transform.viewToFrame(pixels.x, pixels.y))
            }
        }
    }

    @Test
    fun letterboxTouchesMapOutsideTheFrameInsteadOfClamping() {
        val transform = GuideFrameTransform.fit(720f, 960f, 1080f, 1920f)
        assertPoint(0.5f, -1f / 6f, transform.viewToFrame(540f, 0f))
        assertPoint(0.5f, 7f / 6f, transform.viewToFrame(540f, 1920f))
    }

    @Test
    fun frameResolutionDoesNotChangePlacementAndMatchesCompositionGuides() {
        for ((width, height) in listOf(1080f to 1920f, 1200f to 800f, 900f to 1200f)) {
            val transform = GuideFrameTransform.fit(720f, 960f, width, height)
            assertEquals(transform, GuideFrameTransform.fit(3f, 4f, width, height))
            val composition = fitViewfinderFrame(width, height)
            assertEquals(composition.left, transform.left, EPSILON)
            assertEquals(composition.top, transform.top, EPSILON)
            assertEquals(composition.width, transform.width, EPSILON)
            assertEquals(composition.height, transform.height, EPSILON)
        }
    }

    @Test
    fun invalidSizesAndPointsAreRejectedBeforeDivision() {
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { GuideFrameTransform.fit(invalid, 960f, 1080f, 1920f) }
            assertThrows(IllegalArgumentException::class.java) { GuideFrameTransform.fit(720f, invalid, 1080f, 1920f) }
            assertThrows(IllegalArgumentException::class.java) { GuideFrameTransform.fit(720f, 960f, invalid, 1920f) }
            assertThrows(IllegalArgumentException::class.java) { GuideFrameTransform.fit(720f, 960f, 1080f, invalid) }
        }
        val transform = GuideFrameTransform.fit(720f, 960f, 1080f, 1920f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { transform.frameToView(invalid, 0f) }
            assertThrows(IllegalArgumentException::class.java) { transform.frameToView(0f, invalid) }
            assertThrows(IllegalArgumentException::class.java) { transform.viewToFrame(invalid, 0f) }
            assertThrows(IllegalArgumentException::class.java) { transform.viewToFrame(0f, invalid) }
        }
    }

    @Test
    fun validStateIsPreservedIncludingIdentityStyleAndVisibility() {
        val state = GuideState(guideId = "person", cx = 0.3f, cy = 0.6f, style = GuideStyle.CUTOUT, visible = false)
        assertEquals(state, GuideGeometry.clamp(state, 0.5f))
        assertEquals(GuideState(), GuideGeometry.clamp(GuideState(), 0.5f))
    }

    @Test
    fun scaleAndOpacityAreClampedToTheirAllowedRanges() {
        val small = GuideGeometry.clamp(GuideState(height = -1f, opacity = -1f), 0.5f)
        assertEquals(0.21f, small.height, EPSILON)
        assertEquals(0.1f, small.opacity, EPSILON)
        val large = GuideGeometry.clamp(GuideState(height = 100f, opacity = 100f), 0.5f)
        assertEquals(2.1f, large.height, EPSILON)
        assertEquals(0.9f, large.opacity, EPSILON)
    }

    @Test
    fun positionBoundsUseTheClampedSizeAndThePortraitFrameAspect() {
        val state = GuideGeometry.clamp(GuideState(cx = -10f, cy = 10f, height = 0f), 1f)
        // A square at height .21 occupies .28 of the 3:4 frame width.
        assertEquals(-0.084f, state.cx, EPSILON)
        assertEquals(1.063f, state.cy, EPSILON)
        assertEquals(0.21f, state.height, EPSILON)
    }

    @Test
    fun draggingToEveryCornerKeepsTwentyPercentOfEachDimensionVisible() {
        for (height in listOf(0.21f, 0.7f, 2.1f)) {
            for (aspect in listOf(0.3f, 0.75f, 1.5f)) {
                val width = height * aspect * 4f / 3f
                for (cx in listOf(-100f, 100f)) {
                    for (cy in listOf(-100f, 100f)) {
                        val result = GuideGeometry.clamp(GuideState(cx = cx, cy = cy, height = height), aspect)
                        assertEquals(width * 0.2f, overlap(result.cx, width), EPSILON)
                        assertEquals(height * 0.2f, overlap(result.cy, height), EPSILON)
                        assertEquals(result, GuideGeometry.clamp(result, aspect))
                    }
                }
            }
        }
    }

    @Test
    fun veryWideGuidesCoverTheWholeFrameWhenTwentyPercentCannotFit() {
        // At height 1.5, these aspects give widths of exactly five and six frame widths.
        for (aspect in listOf(2.5f, 3f)) {
            for (cx in listOf(-100f, 0.5f, 100f)) {
                val result = GuideGeometry.clamp(GuideState(cx = cx, height = 1.5f), aspect)
                assertEquals(1.5f, result.height, EPSILON)
                assertEquals(1f, overlap(result.cx, result.height * aspect * 4f / 3f), EPSILON)
            }
        }
    }

    @Test
    fun nonFiniteStateValuesResetToDefaultsWithoutChangingOtherFields() {
        val expected = GuideState(guideId = "person", style = GuideStyle.CUTOUT, visible = false)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val input = expected.copy(cx = invalid, cy = invalid, height = invalid, opacity = invalid)
            assertEquals(expected, GuideGeometry.clamp(input, 0.5f))
        }
    }

    @Test
    fun invalidAspectIsRejected() {
        for (aspect in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { GuideGeometry.clamp(GuideState(), aspect) }
        }
    }

    @Test
    fun largeFiniteAspectDoesNotOverflowThePositionBounds() {
        val result = GuideGeometry.clamp(GuideState(cx = Float.MAX_VALUE, height = 2.1f), Float.MAX_VALUE)
        assertTrue(result.cx.isFinite())
        assertEquals(2.1f, result.height, EPSILON)
        assertEquals(result, GuideGeometry.clamp(result, Float.MAX_VALUE))
    }

    private fun overlap(center: Float, extent: Float): Float =
        (minOf(1f, center + extent / 2f) - maxOf(0f, center - extent / 2f)).coerceAtLeast(0f)

    private fun assertPoint(x: Float, y: Float, point: GuidePoint) {
        assertEquals(x, point.x, EPSILON)
        assertEquals(y, point.y, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
