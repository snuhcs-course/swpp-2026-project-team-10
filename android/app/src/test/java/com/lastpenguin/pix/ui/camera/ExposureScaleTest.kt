// AI-generated with Claude Code, 2026-10-16, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Partitions: the slider's ends and middle against the exposure range's ends and zero, both ways; EV labels for a
 * positive, a negative, and a zero index, and for a camera without a step.
 */
class ExposureScaleTest {
    private val range = -4..4

    @Test
    fun sliderPositions_mapToIndexesAndBack_fromOne() {
        assertEquals(9f, ExposureScale.sliderMax(range))
        assertEquals(1f, ExposureScale.sliderValue(-4, range))
        assertEquals(5f, ExposureScale.sliderValue(0, range))
        assertEquals(9f, ExposureScale.sliderValue(4, range))
        assertEquals(-4, ExposureScale.index(1f, range))
        assertEquals(0, ExposureScale.index(5f, range))
        assertEquals(4, ExposureScale.index(9f, range))
        assertEquals(-4, ExposureScale.index(1.4f, range))
    }

    @Test
    fun evLabels_showTheSign_andZeroWithoutOne() {
        assertEquals("0", ExposureScale.formatEv(0, 0.5f))
        assertEquals("+1.0", ExposureScale.formatEv(2, 0.5f))
        assertEquals("-0.5", ExposureScale.formatEv(-1, 0.5f))
        assertEquals("0", ExposureScale.formatEv(3, 0f))
    }
}
