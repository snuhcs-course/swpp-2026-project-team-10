// AI-generated with ChatGPT Codex and Claude Code, 2026-11-05, reviewed by Dongje Park and Sungmin Jo
package com.lastpenguin.pix.ui.camera

import com.lastpenguin.pix.camera.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// previewArea's partitions (R&S 6.2): an image that fits above the controls, one that reaches under them, one taller
// than the rest of the screen, and a size or a ratio that is not there. The area of each ratio, 1:1 in the 4:3 one.
// A ratio change's move (Design 2.6.2, Iter3-A12): each ratio's place, with what of its area shows; a place halfway;
// and where the scene goes between each pair of ratios the button steps through, also on a screen that narrows 9:16.
class ViewfinderFrameTest {
    @Test
    fun tallPreviewExcludesTopAndBottomLetterbox() {
        val frame = fitViewfinderFrame(1080f, 1800f)
        assertEquals(ViewfinderFrame(0f, 180f, 1080f, 1440f), frame)
        assertEquals(360f, frame.width / 3f, 0.001f)
        assertEquals(480f, frame.height / 3f, 0.001f)
        assertEquals(540f, frame.centerX, 0.001f)
        assertEquals(900f, frame.centerY, 0.001f)
    }

    @Test
    fun widePreviewExcludesSideLetterbox() {
        assertEquals(ViewfinderFrame(300f, 0f, 600f, 800f), fitViewfinderFrame(1200f, 800f))
    }

    @Test
    fun exactAspectUsesWholePreview() {
        assertEquals(ViewfinderFrame(0f, 0f, 900f, 1200f), fitViewfinderFrame(900f, 1200f))
    }

    @Test
    fun otherRatiosFitTheirOwnFrame() {
        // 9:16 in a 3:4-shaped preview fills the height; 1:1 fills the width.
        assertEquals(ViewfinderFrame(112.5f, 0f, 675f, 1200f), fitViewfinderFrame(900f, 1200f, 9f / 16f))
        assertEquals(ViewfinderFrame(0f, 150f, 900f, 900f), fitViewfinderFrame(900f, 1200f, 1f))
    }

    @Test
    fun missingSizeHasNoDrawableArea() {
        val empty = ViewfinderFrame(0f, 0f, 0f, 0f)
        assertEquals(empty, fitViewfinderFrame(0f, 100f))
        assertEquals(empty, fitViewfinderFrame(100f, -1f))
        assertEquals(empty, fitViewfinderFrame(Float.NaN, 100f))
        assertEquals(empty, fitViewfinderFrame(100f, 100f, 0f))
    }

    @Test
    fun previewArea_fitsBelowTheTopBar_withoutReachingTheControls() {
        // The S22's app area: 360 x 705 dp, a 44 dp top bar, 112 dp of controls; 4:3 is 480 dp tall.
        assertEquals(PreviewArea(44f, 524f, 0f), previewArea(360f, 705f, 44f, 112f, 3f / 4f))
    }

    @Test
    fun previewArea_tallerThanTheSpaceAboveTheControls_reachesUnderThem() {
        // 16:9 is 640 dp tall: from 44 to 684, 91 dp of it under the controls that start at 593.
        assertEquals(PreviewArea(44f, 684f, 91f), previewArea(360f, 705f, 44f, 112f, 9f / 16f))
    }

    @Test
    fun previewArea_tallerThanTheRestOfTheScreen_stopsAtItsBottom() {
        assertEquals(PreviewArea(44f, 600f, 112f), previewArea(360f, 600f, 44f, 112f, 9f / 16f))
    }

    @Test
    fun previewArea_withoutAWidthOrARatio_isTheSpaceBetweenTheBars() {
        assertEquals(PreviewArea(44f, 593f, 0f), previewArea(0f, 705f, 44f, 112f, 3f / 4f))
        assertEquals(PreviewArea(44f, 593f, 0f), previewArea(360f, 705f, 44f, 112f, Float.NaN))
    }

    @Test
    fun previewAspect_isTheRatiosOwn_except1x1_whichShowsInTheFourByThreeArea() {
        assertEquals(3f / 4f, AspectRatio.R4_3.previewAspect, 0f)
        assertEquals(9f / 16f, AspectRatio.R16_9.previewAspect, 0f)
        assertEquals(3f / 4f, AspectRatio.R1_1.previewAspect, 0f)
    }

    // The S22's app area, as above: 360 x 705 dp, a 44 dp top bar, 112 dp of controls.
    private fun place(aspect: AspectRatio, screenHeight: Float = 705f) =
        framePlace(360f, screenHeight, 44f, 112f, aspect)

    @Test
    fun framePlace_ofEachRatio_isItsAreaAndFrame_and1x1ShowsTheWholeFourByThreeArea() {
        val fourByThree = place(AspectRatio.R4_3)
        assertEquals(PreviewArea(44f, 524f, 0f), fourByThree.area)
        assertEquals(ViewfinderFrame(0f, 44f, 360f, 480f), fourByThree.frame)
        assertEquals(fourByThree.frame, fourByThree.shows)
        assertTrue(fourByThree.showsArea)

        val sixteenByNine = place(AspectRatio.R16_9)
        assertEquals(ViewfinderFrame(0f, 44f, 360f, 640f), sixteenByNine.frame)
        assertTrue(sixteenByNine.showsArea)

        // The square in the middle of the 4:3 area, which shows whole with the outside dimmed.
        val square = place(AspectRatio.R1_1)
        assertEquals(fourByThree.area, square.area)
        assertEquals(ViewfinderFrame(0f, 104f, 360f, 360f), square.frame)
        assertEquals(ViewfinderFrame(0f, 44f, 360f, 480f), square.shows)
        assertTrue(square.showsArea)

        // 9:16 on a screen too short for it narrows, and only its frame shows.
        val narrowed = place(AspectRatio.R16_9, screenHeight = 600f)
        assertEquals(ViewfinderFrame(23.625f, 44f, 312.75f, 556f), narrowed.frame)
        assertFalse(narrowed.showsArea)
    }

    @Test
    fun lerp_isEachPlaceAtItsEnds_andBetweenThemHalfway() {
        val from = place(AspectRatio.R4_3)
        val to = place(AspectRatio.R16_9)
        assertEquals(from, lerp(from, to, 0f))
        assertEquals(to, lerp(from, to, 1f))

        val half = lerp(from, to, 0.5f)
        assertEquals(PreviewArea(44f, 604f, 45.5f), half.area)
        assertEquals(ViewfinderFrame(0f, 44f, 360f, 560f), half.frame)
        // Toward a narrowed 9:16, only what shows of the moving area is drawn.
        assertFalse(lerp(place(AspectRatio.R4_3, 600f), place(AspectRatio.R16_9, 600f), 0.5f).showsArea)
    }

    @Test
    fun sceneMove_fromFourByThreeToSixteenByNine_isAThirdLarger_andCenteredInTheTallerFrame() {
        val from = place(AspectRatio.R4_3)
        val to = place(AspectRatio.R16_9)

        assertEquals(SceneMove(1f, 180f, 284f, 180f, 284f), sceneMove(from, from))
        // 9:16 is cut from the sensor's full height: the same width shows three quarters of the scene's.
        val end = sceneMove(from, to)
        assertEquals(4f / 3f, end.scale, 1e-4f)
        assertEquals(180f, end.x, 0f)
        assertEquals(364f, end.y, 0f)
        // Halfway, on its way.
        val half = sceneMove(from, lerp(from, to, 0.5f))
        assertEquals(7f / 6f, half.scale, 1e-4f)
        assertEquals(324f, half.y, 0f)
    }

    @Test
    fun sceneMove_fromSixteenByNineTo1x1_isThreeQuartersAsLarge_andCenteredInTheFourByThreeArea() {
        val end = sceneMove(place(AspectRatio.R16_9), place(AspectRatio.R1_1))
        assertEquals(0.75f, end.scale, 1e-4f)
        assertEquals(364f, end.fromY, 0f)
        assertEquals(284f, end.y, 0f)
    }

    @Test
    fun sceneMove_between1x1AndFourByThree_staysAsItIs() {
        // Both take the sensor's full width, around the same center.
        val still = SceneMove(1f, 180f, 284f, 180f, 284f)
        assertEquals(still, sceneMove(place(AspectRatio.R1_1), place(AspectRatio.R4_3)))
        assertEquals(still, sceneMove(place(AspectRatio.R4_3), place(AspectRatio.R1_1)))
    }

    @Test
    fun sceneMove_toANarrowedSixteenByNine_followsItsWidth() {
        // 312.75 dp wide for 556 dp tall: the sensor's image is 417 dp wide around it.
        val end = sceneMove(place(AspectRatio.R4_3, 600f), place(AspectRatio.R16_9, 600f))
        assertEquals(417f / 360f, end.scale, 1e-4f)
    }

    @Test
    fun sceneMove_fromAPlaceWithNoSize_doesNotScale() {
        val none = framePlace(0f, 0f, 0f, 0f, AspectRatio.R4_3)
        assertEquals(1f, sceneMove(none, place(AspectRatio.R16_9)).scale, 0f)
    }
}
