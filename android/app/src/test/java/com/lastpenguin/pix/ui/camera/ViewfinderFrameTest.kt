// AI-generated with ChatGPT Codex and Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
