// AI-generated with Claude Code, 2026-10-05, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideSizesTest {
    @Test
    fun sampleSizeKeepsTheLongSideAtLeastTheTarget() {
        assertEquals(3, decodeSampleSize(4000, 3000))
        assertEquals(3, decodeSampleSize(3000, 4000))
        assertEquals(2, decodeSampleSize(2560, 1440))
        assertEquals(1, decodeSampleSize(1000, 800))
    }

    @Test
    fun longSideIsFittedWithoutUpscaling() {
        assertEquals(PixelSize(1280, 960), fitLongSide(4000, 3000))
        assertEquals(PixelSize(960, 1280), fitLongSide(1333, 1777))
        assertEquals(PixelSize(800, 600), fitLongSide(800, 600))
        assertEquals(PixelSize(1280, 1), fitLongSide(2560, 1))
    }

    @Test
    fun cropAddsTwoPercentOfTheLongerSideAndStaysInsideThePhoto() {
        assertEquals(
            PixelRect(92, 42, 308, 458),
            cropWithMargin(PixelRect(100, 50, 300, 450), 1000, 1000),
        )
        assertEquals(
            PixelRect(0, 0, 11, 51),
            cropWithMargin(PixelRect(0, 0, 10, 50), 100, 100),
        )
        assertEquals(
            PixelRect(0, 0, 100, 100),
            cropWithMargin(PixelRect(0, 0, 100, 100), 100, 100),
        )
    }

    @Test
    fun strokeScalesWithTheCutoutHeight() {
        val atScreenSize = (GuideState.DEFAULT_HEIGHT * 1440f).toInt()
        assertEquals(8f, outlineStrokePx(atScreenSize), 0.01f)
        assertEquals(4f, outlineStrokePx(atScreenSize / 2), 0.01f)
    }
}
