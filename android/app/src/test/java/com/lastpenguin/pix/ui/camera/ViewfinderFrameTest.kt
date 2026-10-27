// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import com.lastpenguin.pix.camera.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Test

// previewArea's partitions (R&S 6.2): an image that fits above the controls, one that reaches under them, one taller
// than the rest of the screen, and a size or a ratio that is not there. The area of each ratio, 1:1 in the 4:3 one.
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
}
