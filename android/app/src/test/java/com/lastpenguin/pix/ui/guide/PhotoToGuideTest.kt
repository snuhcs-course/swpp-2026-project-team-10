// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.guide.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Reference confirm's picked image turning into its guide (FR-2.3, FR-2.10, R&S 6.2, #295), its frames in a 1000 × 1400
 * px card with 50 px of padding, a 180 px corner, and a 35 px margin. Partitions on what was picked: a photo used whole
 * (1.3 s: it fills the card, holds, its outline is drawn on its person, then it goes to the corner while the outline goes
 * to its place) and a screenshot cut to its photo (2 s: the cut first, the screenshot whole, dimmed around the photo,
 * which then fills the card; then the same). Progress, state-based on each timeline: the start, the photo filling the
 * card before the line, the line drawn halfway and whole, and the end (the photo in the bottom right corner, the outline
 * in its place, as the outline's view shows it). The photo's shape: a 3:4 post (its height fills the corner) and a
 * 1.91:1 one (its width does).
 */
class PhotoToGuideTest {
    /** A 540 × 1170 screenshot whose 3:4 photo is rows 170–890, its person in the middle of it. */
    private val screenshot = turning(540, 1170, NormalizedRect(0f, 170f / 1170, 1f, 890f / 1170), cut = true)

    /** A 600 × 800 photo used whole, its person in the middle of it. */
    private val photo = turning(600, 800, WHOLE, cut = false, person = NormalizedRect(0.25f, 0.1f, 0.75f, 0.9f))

    @Test
    fun aPhoto_takes1300ms_andAScreenshot2s() {
        assertEquals(1300L, photo.durationMs)
        assertEquals(2000L, screenshot.durationMs)
    }

    @Test
    fun aPhoto_startsFillingTheCard_withoutTheLine() {
        val frame = photo.at(0f)

        assertBox(CutBox(50f, 100f, 950f, 1300f), photo.photoIn(frame.screen))
        assertEquals(0f, frame.outside, DELTA)
        assertEquals(0f, frame.frame, DELTA)
        assertEquals(1f, frame.photo, DELTA)
        assertEquals(0f, frame.line, DELTA)
        assertEquals(0f, frame.corner, DELTA)
        // Still nothing drawn at the hold's end, 300 ms in.
        assertEquals(0f, photo.at(300f / 1300).line, DELTA)
    }

    @Test
    fun aPhotosLine_isDrawnOnItsPerson_whileThePhotoDims() {
        val half = photo.at(550f / 1300)
        assertEquals(0.5f, half.line, DELTA)

        val drawn = photo.at(800f / 1300)

        assertEquals(1f, drawn.line, DELTA)
        assertEquals(0.5f, drawn.photo, DELTA)
        assertEquals(0f, drawn.corner, DELTA)
        // The person's box in the photo that fills the card.
        assertBox(CutBox(275f, 220f, 725f, 1180f), drawn.lineBox)
    }

    @Test
    fun atTheEnd_thePhotoIsInTheCorner_andTheLineInItsPlace() {
        val frame = photo.at(1f)

        // 135 × 180: the corner's 180 px is the 3:4 photo's height.
        assertBox(CutBox(830f, 1185f, 965f, 1365f), photo.photoIn(frame.screen))
        assertEquals(1f, frame.photo, DELTA)
        assertEquals(1f, frame.line, DELTA)
        assertEquals(1f, frame.corner, DELTA)
        // The person's 300 × 640 box fits the card as the outline's view fits the outline.
        assertBox(CutBox(195.3f, 50f, 804.7f, 1350f), frame.lineBox)
    }

    @Test
    fun aScreenshot_startsWhole_inTheCard() {
        val frame = screenshot.at(0f)

        assertBox(CutBox(200f, 50f, 800f, 1350f), frame.screen)
        assertEquals(1f, frame.outside, DELTA)
        assertEquals(0f, frame.frame, DELTA)
        assertEquals(0f, frame.line, DELTA)
        assertEquals(0f, frame.corner, DELTA)
    }

    @Test
    fun aScreenshotsCut_endsWithItsPhotoFillingTheCard_framed_withoutTheRest_beforeTheLine() {
        val frame = screenshot.at(0.5f)

        assertBox(CutBox(50f, 100f, 950f, 1300f), screenshot.photoIn(frame.screen))
        assertEquals(0f, frame.outside, DELTA)
        assertEquals(1f, frame.frame, DELTA)
        assertEquals(0f, frame.line, DELTA)
        assertEquals(0f, frame.corner, DELTA)
    }

    @Test
    fun aScreenshot_thenGoesOnAsAPhoto() {
        val drawn = screenshot.at(0.75f)
        assertEquals(1f, drawn.line, DELTA)
        assertEquals(0f, drawn.frame, DELTA)
        assertEquals(0.5f, drawn.photo, DELTA)
        assertBox(CutBox(275f, 220f, 725f, 1180f), drawn.lineBox)

        val end = screenshot.at(1f)

        assertBox(CutBox(830f, 1185f, 965f, 1365f), screenshot.photoIn(end.screen))
        assertEquals(0f, end.outside, DELTA)
        assertEquals(1f, end.corner, DELTA)
        // The person's 270 × 576 box in the 540 × 720 photo.
        assertBox(CutBox(195.3f, 50f, 804.7f, 1350f), end.lineBox)
    }

    @Test
    fun aLandscapePhoto_takesTheCornersWidth() {
        val landscape = turning(540, 1170, NormalizedRect(0f, 170f / 1170, 1f, 453f / 1170), cut = true)

        val photo = landscape.photoIn(landscape.at(1f).screen)

        // 540 × 283 is 1.91:1, so 180 × 94.3.
        assertBox(CutBox(785f, 1270.7f, 965f, 1365f), photo)
    }

    private fun turning(
        width: Int,
        height: Int,
        photo: NormalizedRect,
        cut: Boolean,
        person: NormalizedRect = NormalizedRect(0.25f, 0.1f, 0.75f, 0.9f),
    ): PhotoToGuide {
        // The outline is the person's box: its shape in the photo's pixels.
        val aspect = width * (photo.right - photo.left) * (person.right - person.left) /
            (height * photo.height * person.height)
        return PhotoToGuide(
            width,
            height,
            photo,
            person,
            aspect,
            cut,
            1000f,
            1400f,
            padding = 50f,
            corner = 180f,
            margin = 35f,
        )
    }

    private fun assertBox(expected: CutBox, actual: CutBox) {
        assertEquals("left of $actual", expected.left, actual.left, 0.5f)
        assertEquals("top of $actual", expected.top, actual.top, 0.5f)
        assertEquals("right of $actual", expected.right, actual.right, 0.5f)
        assertEquals("bottom of $actual", expected.bottom, actual.bottom, 0.5f)
    }

    private companion object {
        const val DELTA = 1e-4f
        val WHOLE = NormalizedRect(0f, 0f, 1f, 1f)
    }
}
