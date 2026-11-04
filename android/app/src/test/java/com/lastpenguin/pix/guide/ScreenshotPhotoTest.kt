// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.guide

import com.lastpenguin.pix.guide.FakeScreenshot.Companion.DARK
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.POST_TOP
import com.lastpenguin.pix.guide.FakeScreenshot.Companion.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * The photo of a feed post in a screenshot (FR-2.10, Design 2.6.1, step 1), on screenshots drawn in pixels.
 * Cut (equivalence partitioning): a feed post in light and in dark mode at 1:1, 4:5, 3:4, and 1.91:1 (the photo's exact
 * rows); a photo with flat white bars inside its post, as Instagram shows a landscape photo in a portrait post (the
 * photo alone).
 * Whole: a camera photo at 3:4 and 9:16; a photo with no flat background around it (20:9); a story, whose photo fills
 * the screen; a profile grid; two photos side by side; a photo inside the app's margins; two posts of a similar size;
 * a photo that runs off the screen's bottom.
 * Boundaries: the screenshot's shape at 1.9:1 (whole) and one row taller; a photo of 2:1 of the width (cut) and one row
 * shorter, of 3:2 (cut) and one row taller; a sky 13 from the background on every channel (the photo's) and 12 (the app's,
 * so the photo starts under it); a second post of 80 % of the first (whole) and one row shorter (the first).
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
            fill(0, POST_TOP, width, POST_TOP + 158, FakeScreenshot.WHITE)
            fill(0, POST_TOP + 563, width, POST_TOP + 720, FakeScreenshot.WHITE)
        }

        assertEquals(PixelRect(0, POST_TOP + 158, 540, POST_TOP + 563), screen.find())
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
    fun aStory_isUsedWhole() {
        // A 9:16 photo filling the screen between the progress bar and the reply field.
        val story = FakeScreenshot(background = rgb(0, 0, 0)).apply {
            text(20)
            photo(80, 960)
            text(1100)
        }

        assertNull(story.find())
    }

    @Test
    fun aProfileGrid_isUsedWhole() {
        // Three columns of 3:4 tiles, 2 px apart, under the profile's header.
        val grid = FakeScreenshot().apply {
            text(56)
            text(120)
            for (row in 0 until 3) {
                for (column in 0 until 3) {
                    val left = column * 180
                    photo(
                        200 + row * 242,
                        240,
                        left + (
                            if (column ==
                                0
                            ) {
                                0
                            } else {
                                1
                            }
                            ),
                        left + (if (column == 2) 180 else 179),
                    )
                }
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
    fun aPhotoInsideTheAppsMargins_isUsedWhole() {
        val screen = FakeScreenshot().apply {
            text(56)
            photo(POST_TOP, 600, 16, 524)
            text(POST_TOP + 620)
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
    fun theSmallestPhoto_isHalfTheWidth() {
        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 270), FakeScreenshot().apply { post(270) }.find())
        assertNull(FakeScreenshot().apply { post(269) }.find())
    }

    @Test
    fun theTallestPhoto_isOneAndAHalfTheWidth() {
        assertEquals(PixelRect(0, POST_TOP, 540, POST_TOP + 810), FakeScreenshot().apply { post(810) }.find())
        assertNull(FakeScreenshot().apply { post(811) }.find())
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
