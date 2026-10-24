// AI-generated with ChatGPT Codex and Claude Code, 2026-10-22, reviewed by Sungmin Jo
package com.lastpenguin.pix.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CameraZoomPlanTest {
    @Test
    fun usesLogicalCameraForNativeHalfMagnification() {
        val primary = range(1f, 0.5f, 8f)
        val plan = CameraZoomPlan(primary)

        assertEquals(0.5f, plan.minZoom)
        assertEquals(8f, plan.maxZoom)
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.5f))
        assertEquals(0.5f, plan.rangeFor(ZoomLens.PRIMARY).nativeZoom(0.5f))
        assertEquals(0.75f, primary.appZoom(0.75f))
    }

    @Test
    fun translatesSeparateUltrawideRatiosIntoPrimaryLensUnits() {
        val primary = range(1f, 1f, 10f)
        val ultra = range(0.5f, 1f, 8f)
        val plan = CameraZoomPlan(primary, ultra)

        assertEquals(0.5f, plan.minZoom)
        assertEquals(4f, ultra.maxZoom)
        assertEquals(10f, plan.maxZoom)
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.75f))
        assertSame(ultra, plan.rangeFor(ZoomLens.ULTRAWIDE))
        assertEquals(1.5f, ultra.nativeZoom(0.75f))
        assertEquals(0.75f, ultra.appZoom(1.5f))
    }

    @Test
    fun switchesToPrimaryExactlyAtItsMinimumAndAbove() {
        val primary = range(1f, 1f, 10f)
        val plan = CameraZoomPlan(primary, range(0.5f, 1f, 8f))

        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.999f))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1f))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(2f))
        assertSame(primary, plan.rangeFor(ZoomLens.PRIMARY))
    }

    @Test
    fun preservesHardwareMinimumWhenOnlyPointSixIsAvailable() {
        val plan = CameraZoomPlan(range(1f, 1f, 10f), range(0.6f, 1f, 8f))

        assertEquals(0.6f, plan.minZoom)
        assertEquals(0.6f, plan.clamp(0.5f))
        assertEquals(0.6f, plan.clamp(-1f))
        assertEquals(10f, plan.clamp(30f))
    }

    @Test
    fun doesNotAdvertiseUltrawideWhenUnavailable() {
        val plan = CameraZoomPlan(range(1f, 1f, 8f))

        assertEquals(1f, plan.minZoom)
        assertEquals(1f, plan.clamp(0.5f))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.5f))
        assertNull(plan.ultrawide)
    }

    @Test
    fun discardsUltrawideWithUnsupportedGapToPrimary() {
        val plan = CameraZoomPlan(range(1f, 1f, 8f), range(0.5f, 1f, 1.5f))

        assertNull(plan.ultrawide)
        assertEquals(1f, plan.minZoom)
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.75f))
    }

    @Test
    fun acceptsUltrawideWhoseMaximumExactlyMeetsPrimaryMinimum() {
        val plan = CameraZoomPlan(range(1f, 1f, 8f), range(0.5f, 1f, 2f))

        assertEquals(0.5f, plan.minZoom)
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.75f))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1f))
    }

    @Test
    fun doesNotSwitchLensesWhenPrimaryAlreadyOffersTheSameMinimum() {
        val plan = CameraZoomPlan(range(1f, 0.5f, 8f), range(0.5f, 1f, 8f))

        assertNull(plan.ultrawide)
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.5f))
    }

    @Test
    fun doesNotExtendMaximumUsingSecondaryLens() {
        val plan = CameraZoomPlan(range(1f, 1f, 4f), range(0.5f, 1f, 20f))

        assertEquals(4f, plan.maxZoom)
        assertEquals(4f, plan.clamp(10f))
    }

    @Test
    fun clampsConversionsToEachLensesOwnRange() {
        val ultra = range(0.5f, 1f, 8f)

        assertEquals(1f, ultra.nativeZoom(0.1f))
        assertEquals(8f, ultra.nativeZoom(10f))
        assertEquals(0.5f, ultra.appZoom(0.1f))
        assertEquals(4f, ultra.appZoom(10f))
    }

    @Test
    fun rejectsNonFiniteRequestsBeforeRouting() {
        val plan = CameraZoomPlan(range(1f, 1f, 8f), range(0.5f, 1f, 8f))

        assertNull(plan.clamp(Float.NaN))
        assertNull(plan.clamp(Float.POSITIVE_INFINITY))
        assertNull(plan.clamp(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun rejectsNonPositiveOrInvertedMetadata() {
        assertNull(LensZoomRange.create(0f, 1f, 8f))
        assertNull(LensZoomRange.create(-0.5f, 1f, 8f))
        assertNull(LensZoomRange.create(0.5f, 0f, 8f))
        assertNull(LensZoomRange.create(0.5f, -1f, 8f))
        assertNull(LensZoomRange.create(0.5f, 8f, 1f))
    }

    @Test
    fun rejectsNonFiniteMetadataInEveryField() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertNull(LensZoomRange.create(it, 1f, 8f))
            assertNull(LensZoomRange.create(0.5f, it, 8f))
            assertNull(LensZoomRange.create(0.5f, 1f, it))
        }
    }

    @Test
    fun rejectsNormalizedRangeOverflowAndUnderflow() {
        assertNull(LensZoomRange.create(Float.MAX_VALUE, 1f, 2f))
        assertNull(LensZoomRange.create(Float.MIN_VALUE, 0.5f, 1f))
    }

    @Test
    fun acceptsFixedRatioLensWhenItJoinsPrimaryRange() {
        val primary = range(1f, 1f, 1f)
        val plan = CameraZoomPlan(primary, range(0.5f, 1f, 2f))

        assertEquals(0.5f, plan.minZoom)
        assertEquals(1f, plan.maxZoom)
        assertEquals(1f, primary.nativeZoom(3f))
    }

    @Test
    fun aPinchKeepsThePrimaryUntilWellBelowItsMinimum() {
        val plan = CameraZoomPlan(range(1f, 1f, 10f), range(0.5f, 1f, 8f))

        assertEquals(ZoomLens.PRIMARY, plan.lensFor(2f, ZoomLens.PRIMARY, final = false))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.97f, ZoomLens.PRIMARY, final = false))
        // The band's edge still holds; just past it the ultrawide takes over.
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.95f, ZoomLens.PRIMARY, final = false))
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.94f, ZoomLens.PRIMARY, final = false))
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.5f, ZoomLens.PRIMARY, final = false))
    }

    @Test
    fun aPinchKeepsTheUltrawideUntilWellAboveThePrimaryMinimum() {
        val plan = CameraZoomPlan(range(1f, 1f, 10f), range(0.5f, 1f, 8f))

        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.7f, ZoomLens.ULTRAWIDE, final = false))
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(1f, ZoomLens.ULTRAWIDE, final = false))
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(1.05f, ZoomLens.ULTRAWIDE, final = false))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1.06f, ZoomLens.PRIMARY, final = false))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1.06f, ZoomLens.ULTRAWIDE, final = false))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(3f, ZoomLens.ULTRAWIDE, final = false))
    }

    @Test
    fun aPinchLeavesAnUltrawideThatCannotReachTheBand() {
        // 0.5 × 2.04 = 1.02: the ultrawide cannot crop as far as 1.05.
        val plan = CameraZoomPlan(range(1f, 1f, 10f), range(0.5f, 1f, 2.04f))

        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(1.01f, ZoomLens.ULTRAWIDE, final = false))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1.03f, ZoomLens.ULTRAWIDE, final = false))
    }

    @Test
    fun thePinchsLastValueSettlesOnItsOwnLens() {
        val plan = CameraZoomPlan(range(1f, 1f, 10f), range(0.5f, 1f, 8f))

        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.97f, ZoomLens.PRIMARY, final = true))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1.03f, ZoomLens.ULTRAWIDE, final = true))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(1f, ZoomLens.ULTRAWIDE, final = true))
        assertEquals(ZoomLens.ULTRAWIDE, plan.lensFor(0.6f, ZoomLens.ULTRAWIDE, final = true))
    }

    @Test
    fun withoutAnUltrawideEveryPinchStaysOnThePrimary() {
        val plan = CameraZoomPlan(range(1f, 0.5f, 8f))

        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.5f, ZoomLens.PRIMARY, final = false))
        assertEquals(ZoomLens.PRIMARY, plan.lensFor(0.5f, ZoomLens.PRIMARY, final = true))
    }

    private fun range(intrinsic: Float, min: Float, max: Float): LensZoomRange =
        requireNotNull(LensZoomRange.create(intrinsic, min, max))
}
