// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.guide.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Reference confirm's cut of a screenshot (FR-2.10, R&S 6.2), its frames in a 1000 × 1400 px card with 50 px of padding,
 * a 180 px corner, and a 35 px margin. Progress (state-based on the timeline): 0 (the screenshot whole, fitted in the
 * card, nothing dimmed or framed), the hold at 0.6 (the photo fitting the card, framed, the rest gone), 1 (the photo in
 * the bottom right corner, the outline shown); the outline hidden until the photo leaves for the corner (0.75).
 * The photo's shape: a 3:4 post (its height fills the corner) and a 1.91:1 one (its width does).
 */
class ScreenshotCutTest {
    /** A 540 × 1170 screenshot whose 3:4 photo is rows 170–890. */
    private val portrait = cut(NormalizedRect(0f, 170f / 1170, 1f, 890f / 1170))

    @Test
    fun atTheStart_theScreenshotIsWholeInTheCard() {
        val frame = portrait.at(0f)

        assertBox(CutBox(200f, 50f, 800f, 1350f), frame.screen)
        assertEquals(1f, frame.outside, DELTA)
        assertEquals(0f, frame.frame, DELTA)
        assertEquals(0f, frame.corner, DELTA)
    }

    @Test
    fun atTheHold_thePhotoFillsTheCard_framed_withoutTheRest() {
        val frame = portrait.at(0.6f)

        assertBox(CutBox(50f, 100f, 950f, 1300f), portrait.photoIn(frame.screen))
        assertEquals(0f, frame.outside, DELTA)
        assertEquals(1f, frame.frame, DELTA)
        assertEquals(0f, frame.corner, DELTA)
    }

    @Test
    fun atTheEnd_thePhotoIsInTheCorner_andTheOutlineShows() {
        val frame = portrait.at(1f)

        // 135 × 180: the corner's 180 px is the 3:4 photo's height.
        assertBox(CutBox(830f, 1185f, 965f, 1365f), portrait.photoIn(frame.screen))
        assertEquals(0f, frame.outside, DELTA)
        assertEquals(0f, frame.frame, DELTA)
        assertEquals(1f, frame.corner, DELTA)
    }

    @Test
    fun theOutline_staysHiddenUntilThePhotoLeavesForTheCorner() {
        assertEquals(0f, ScreenshotCut.outlineAt(0.75f), DELTA)
        assertEquals(1f, ScreenshotCut.outlineAt(1f), DELTA)
        assertEquals(0.5f, ScreenshotCut.outlineAt(0.875f), DELTA)
    }

    @Test
    fun aLandscapePhoto_takesTheCornersWidth() {
        val landscape = cut(NormalizedRect(0f, 170f / 1170, 1f, 453f / 1170))

        val photo = landscape.photoIn(landscape.at(1f).screen)

        // 540 × 283 is 1.91:1, so 180 × 94.3.
        assertBox(CutBox(785f, 1270.7f, 965f, 1365f), photo)
    }

    private fun cut(photo: NormalizedRect) =
        ScreenshotCut(540, 1170, photo, 1000f, 1400f, padding = 50f, corner = 180f, margin = 35f)

    private fun assertBox(expected: CutBox, actual: CutBox) {
        assertEquals("left of $actual", expected.left, actual.left, 0.5f)
        assertEquals("top of $actual", expected.top, actual.top, 0.5f)
        assertEquals("right of $actual", expected.right, actual.right, 0.5f)
        assertEquals("bottom of $actual", expected.bottom, actual.bottom, 0.5f)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
