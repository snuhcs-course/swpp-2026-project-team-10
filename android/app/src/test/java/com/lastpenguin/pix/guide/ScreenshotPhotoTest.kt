// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

import com.lastpenguin.pix.guide.FakeScreenshot.Companion.DARK
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.POST_TOP
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.VIEWER_PAGE
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.VIEWER_TOP
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.WHITE
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The photo in a screenshot (FR-2.10, Design 2.6.1, step 1), on screenshots drawn in pixels.
 * Cut (equivalence partitioning): a feed post in light and in dark mode at 1:1, 4:5, 3:4, and 1.91:1 (the photo's exact
 * rows); a photo with flat white bars inside its post, as Instagram shows a landscape photo in a portrait post (the
 * photo alone); a photo centered in an image viewer, on a panel more than the tolerance from the page, with an icon over
 * the panel, a scrollbar, and the page's header right above (its four edges); a full-width photo under a header with a
 * scrollbar (without the header); a photo between the app's margins (at its sides); a plain wall on one side of a photo
 * (kept); a white wall at a photo's edge (not a gap); a story whose photo fills its frame (the frame); a post shared in a
 * story, a card on a blurred copy (the card).
 * Whole: a camera photo at 3:4 and 9:16; a photo with no flat background around it (20:9); a profile grid; two photos
 * side by side; two posts of a similar size; a photo that runs off the screen's bottom. In a story, no card but the
 * frame: a sharp oval subject on a blur (no photo's shape), a card whose sides fade into the blur (no straight sides),
 * and a framed picture on a textured wall (no blur around it).
 * Boundaries: the screenshot's shape at 1.9:1 (whole) and one row taller; a photo half as tall as wide (cut) and one row
 * shorter, and twice as tall (cut) and one row taller; a photo 60 % of the screen's width (cut) and 2 px narrower; a sky
 * 13 from the background on every channel (the photo's) and 12 (the app's, so the photo starts under it); a second post
 * of 80 % of the first (whole) and one row shorter (the first).
 */
class ScreenshotPhotoTest {

    @Test
    fun aLightFeedPost_isCutToItsPhoto_atEachRatio() {
        for (rows in listOf(540, 675, 720, 283)) {
            val screen = FakeScreenshot().apply { post(rows) }

            assertEquals(
                "1:1, 4:5, 3:4, 1.91:1: $rows rows",
                PixelRect(0, POST_TOP, 540, POST_TOP + rows),
                screen.find(),
            )
        }
    }

    @Test
    fun aDarkFeedPost_isCutToItsPhoto_atEachRatio() {
        for (rows in listOf(540, 675, 720, 283)) {
            val screen = FakeScreenshot(background = DARK).apply { post(rows) }

            assertEquals(
                "1:1, 4:5, 3:4, 1.91:1: $rows rows",
                PixelRect(0, POST_TOP, 540, POST_TOP + rows),
                screen.find(),
            )
        }
    }

    @Test
    fun aPhotoWithWhiteBarsInItsPost_isCutWithoutTheBars() {
        // A 4:3 photo in a 3:4 post: 720 rows of post, the photo's 405 in the middle, white above and below.
        val screen = FakeScreenshot().apply {
            post(720)
            fill(0, POST_TOP, width, POST_TOP + 158, WHITE)
            fill(0, POST_TOP + 563, width, POST_TOP + 720, WHITE)
        }

        assertEquals(PixelRect(0, POST_TOP + 158, 540, POST_TOP + 563), screen.find())
    }

    @Test
    fun aPhotoCenteredInAViewer_isCutAtItsFourEdges() {
        val screen = FakeScreenshot(background = VIEWER_PAGE).apply { viewer(rows = 600, margin = 60) }

        assertEquals(PixelRect(60, VIEWER_TOP, 480, VIEWER_TOP + 600), screen.find())
    }

    @Test
    fun aFullWidthPhotoUnderAHeader_withAScrollbar_isCutWithoutTheHeader() {
        val screen = FakeScreenshot(background = VIEWER_PAGE).apply { viewer(rows = 400, margin = 0) }

        assertEquals(PixelRect(0, VIEWER_TOP, 540, VIEWER_TOP + 400), screen.find())
    }

    @Test
    fun aPhotoBetweenTheAppsMargins_isCutAtItsSides() {
        val screen = FakeScreenshot().apply {
            text(56)
            photo(POST_TOP, 600, 16, 524)
            text(POST_TOP + 620)
        }

        assertEquals(PixelRect(16, POST_TOP, 524, POST_TOP + 600), screen.find())
    }

    @Test
    fun aPlainWallOnOneSideOfAPhoto_staysInIt() {
        val screen = FakeScreenshot().apply {
            post(720)
            fill(420, POST_TOP, width, POST_TOP + 720, rgb(200, 210, 230))
        }

        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 720), screen.find())
    }

    @Test
    fun aWhiteWallAtAPhotosEdge_isNotAGap() {
        // White columns through the photo, outside its middle three fifths.
        val screen = FakeScreenshot().apply {
            post(400)
            fill(470, POST_TOP, 500, POST_TOP + 400, WHITE)
        }

        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 400), screen.find())
    }

    @Test
    fun aStoryFillingItsFrame_isCutToTheFrame() {
        // A 9:16 photo between the progress bar and the reply field.
        val story = FakeScreenshot(background = rgb(0, 0, 0)).apply {
            text(20)
            photo(80, 960)
            text(1100)
        }

        assertEquals(PixelRect(0, 80, 540, 1040), story.find())
    }

    @Test
    fun aPostSharedInAStory_isCutToItsCard() {
        // A card centered on a blurred copy, 60 px from the sides and 180 from the frame's top and bottom, with the
        // story's name over the blur. Its photo has upright stripes, so its only edges across are its top and bottom.
        val story = FakeScreenshot(background = rgb(0, 0, 0)).apply {
            blur(80, 960)
            text(100)
            for (x in 60 until 480) {
                fill(
                    x,
                    260,
                    x + 1,
                    860,
                    if (x / 6 % 2 ==
                        0
                    ) {
                        rgb(200, 120, 90)
                    } else {
                        rgb(60, 90, 140)
                    },
                )
            }
            text(1100)
        }

        assertEquals(PixelRect(60, 260, 480, 860), story.find())
    }

    @Test
    fun aSharpSubjectOnABlurInAStory_isNoCard() {
        // An oval of detail in the middle, as a person on a soft background: no straight sides.
        val story = FakeScreenshot(background = rgb(0, 0, 0)).apply {
            blur(80, 960)
            for (y in 260 until 860) {
                for (x in 60 until 480) {
                    val dx = (x - 270) / 210.0
                    val dy = (y - 560) / 300.0
                    if (dx * dx + dy * dy <= 1) pixels[y * width + x] = FakeScreenshot.scene(x, y)
                }
            }
            text(1100)
        }

        assertEquals(PixelRect(0, 80, 540, 1040), story.find())
    }

    @Test
    fun aCardWithSoftSidesInAStory_isNoCard() {
        // Sharp top and bottom, but sides that fade into the blur over 40 px: no straight sides.
        val story = FakeScreenshot(background = rgb(0, 0, 0)).apply {
            blur(80, 960)
            for (y in 260 until 860) {
                for (x in 60 until 480) {
                    val stripe = if (y / 6 % 2 == 0) rgb(200, 120, 90) else rgb(60, 90, 140)
                    val blurred = pixels[y * width + x]
                    val share = minOf(x - 60, 479 - x, 40) / 40.0
                    fun mix(shift: Int): Int {
                        val from = blurred shr shift and 0xFF
                        return (from + ((stripe shr shift and 0xFF) - from) * share).toInt()
                    }
                    pixels[y * width + x] = rgb(mix(16), mix(8), mix(0))
                }
            }
            text(1100)
        }

        assertEquals(PixelRect(0, 80, 540, 1040), story.find())
    }

    @Test
    fun aFramedPictureOnATexturedWallInAStory_isNoCard() {
        // Straight sides, but a textured wall around them, not a blurred copy: the story's own photo.
        val story = FakeScreenshot(background = rgb(0, 0, 0)).apply {
            for (y in 80 until 1040) {
                for (x in 0 until width) {
                    val speck = (x * 31 + y * 17) % 5 == 0
                    pixels[y * width + x] = if (speck) rgb(150, 150, 150) else rgb(120, 120, 120)
                }
            }
            photo(260, 600, 60, 480)
            text(1100)
        }

        assertEquals(PixelRect(0, 80, 540, 1040), story.find())
    }

    @Test
    fun aCameraPhoto_isUsedWhole() {
        for (height in listOf(720, 960)) {
            val photo = FakeScreenshot(height = height).apply { photo(0, height) }

            assertNull("540x$height", photo.find())
        }
    }

    @Test
    fun aPhotoWithNoFlatBackground_isUsedWhole() {
        // As tall as a screenshot (20:9), but a photo from edge to edge.
        val photo = FakeScreenshot(height = 1200).apply { photo(0, 1200) }

        assertNull(photo.find())
    }

    @Test
    fun aProfileGrid_isUsedWhole() {
        // Three columns of 3:4 tiles, 2 px apart, under the profile's header.
        val grid = FakeScreenshot().apply {
            text(56)
            text(120)
            for (row in 0 until 3) {
                photo(200 + row * 242, 240, 0, 179)
                photo(200 + row * 242, 240, 181, 359)
                photo(200 + row * 242, 240, 361, 540)
            }
        }

        assertNull(grid.find())
    }

    @Test
    fun twoPhotosSideBySide_areUsedWhole() {
        val screen = FakeScreenshot().apply {
            text(56)
            photo(POST_TOP, 400, 0, 268)
            photo(POST_TOP, 400, 272, 540)
            text(POST_TOP + 420)
        }

        assertNull(screen.find())
    }

    @Test
    fun twoPostsOfASimilarSize_areUsedWhole() {
        val screen = FakeScreenshot(height = 1400).apply {
            post(400)
            photo(POST_TOP + 400 + 120, 320)
        }

        assertNull(screen.find())
    }

    @Test
    fun aSecondPostOneRowUnder80Percent_leavesTheFirst() {
        val screen = FakeScreenshot(height = 1400).apply {
            post(400)
            photo(POST_TOP + 400 + 120, 319)
        }

        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 400), screen.find())
    }

    @Test
    fun aPhotoRunningOffTheBottom_isUsedWhole() {
        val screen = FakeScreenshot().apply {
            text(10)
            text(56)
            photo(800, 370)
        }

        assertNull(screen.find())
    }

    @Test
    fun aScreenshotOf19To10_isUsedWhole_andOneRowTaller_isCut() {
        // 540 × 1026 is exactly 1.9:1.
        val exact = FakeScreenshot(height = 1026).apply { post(540) }
        val taller = FakeScreenshot(height = 1027).apply { post(540) }

        assertNull(exact.find())
        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 540), taller.find())
    }

    @Test
    fun theShortestPhoto_isHalfAsTallAsWide() {
        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 270), FakeScreenshot().apply { post(270) }.find())
        assertNull(FakeScreenshot().apply { post(269) }.find())
    }

    @Test
    fun theTallestPhoto_isTwiceAsTallAsWide() {
        assertEquals(
            PixelRect(0, POST_TOP, 540, POST_TOP + 1080),
            FakeScreenshot(height = 1500).apply { post(1080) }.find(),
        )
        assertNull(FakeScreenshot(height = 1500).apply { post(1081) }.find())
    }

    @Test
    fun theNarrowestPhoto_is60PercentOfTheScreen() {
        // 540 × 60 % = 324: margins of 108 on each side; 109 leave 322.
        val narrowest = FakeScreenshot(background = VIEWER_PAGE).apply { viewer(rows = 400, margin = 108) }
        val narrower = FakeScreenshot(background = VIEWER_PAGE).apply { viewer(rows = 400, margin = 109) }

        assertEquals(PixelRect(108, VIEWER_TOP, 432, VIEWER_TOP + 400), narrowest.find())
        assertNull(narrower.find())
    }

    @Test
    fun aSkyJustOutsideTheTolerance_staysInThePhoto() {
        // 60 rows of a pale sky, 13 under white on every channel, at the top of a 3:4 photo.
        val screen = FakeScreenshot().apply {
            post(720)
            fill(0, POST_TOP, width, POST_TOP + 60, rgb(242, 242, 242))
        }

        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 720), screen.find())
    }

    @Test
    fun aSkyWithinTheTolerance_goesWithTheApp() {
        val screen = FakeScreenshot().apply {
            post(720)
            fill(0, POST_TOP, width, POST_TOP + 60, rgb(243, 243, 243))
        }

        assertEquals(PixelRect(0, POST_TOP + 60, 540, POST_TOP + 720), screen.find())
    }

    @Test
    fun aNightSkyJustOutsideTheTolerance_staysInTheDarkPhoto() {
        val screen = FakeScreenshot(background = DARK).apply {
            post(720)
            fill(0, POST_TOP, width, POST_TOP + 60, rgb(26, 29, 34))
        }

        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 720), screen.find())
    }
}
