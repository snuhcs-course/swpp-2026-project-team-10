// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpenguin.pix.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/*
 * What the grid view draws (FR-1.9, FR-1.10) with the switches of Settings (#271), on a 300 × 400 view in 3:4, whose
 * grid lines are at x = 100, 200 and y ≈ 133, 267, and whose bar, level, runs through the middle cell at y = 200.
 * Partitions: both on (grid and bar); the grid off (bar only); the bar off (grid only, also with a level set); both off
 * (nothing, and nothing for TalkBack to read); both off with 1:1's dim, which stays.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraCompositionViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bitmaps = mutableListOf<Bitmap>()

    @After
    fun recycleBitmaps() = onMain {
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    @Test
    fun bothOn_drawTheGridAndTheBar() = onMain {
        val view = view(level = true)

        val drawn = snapshot(view)

        assertDrawn(drawn, GRID_X, ABOVE_BAR_Y)
        assertDrawn(drawn, BAR_X, BAR_Y)
    }

    @Test
    fun theGridOff_leavesTheBarAlone() = onMain {
        val view = view(level = true)

        view.showGrid = false
        val drawn = snapshot(view)

        assertClear(drawn, GRID_X, ABOVE_BAR_Y)
        assertDrawn(drawn, BAR_X, BAR_Y)
        assertEquals(string(R.string.camera_level_aligned), view.contentDescription)
    }

    @Test
    fun theBarOff_leavesTheGridAlone_evenWithALevelSet() = onMain {
        val view = view(level = true)

        view.showLevel = false
        val drawn = snapshot(view)

        assertDrawn(drawn, GRID_X, ABOVE_BAR_Y)
        assertClear(drawn, BAR_X, BAR_Y)
        // TalkBack reads the grid, not a level it cannot see.
        assertEquals(string(R.string.camera_grid_description), view.contentDescription)
    }

    @Test
    fun bothOff_drawNothing() = onMain {
        val view = view(level = true)

        view.showGrid = false
        view.showLevel = false
        val drawn = snapshot(view)

        assertTrue(pixels(drawn).all { Color.alpha(it) == 0 })
        assertNull(view.contentDescription)
    }

    @Test
    fun bothOff_keepOneToOnesDim() = onMain {
        val view = view(level = false)
        view.frameAspect = 1f
        view.dimOutside = true

        view.showGrid = false
        view.showLevel = false
        val drawn = snapshot(view)

        // 1:1 fills the width: the frame is y = 50..350, dimmed above and below, clear inside.
        assertDrawn(drawn, 150, 20)
        assertClear(drawn, 100, 150)
    }

    private fun view(level: Boolean): CameraCompositionView =
        CameraCompositionView(instrumentation.targetContext).apply {
            layout(0, 0, WIDTH, HEIGHT)
            if (level) setLevel(CameraLevel(0f, true))
        }

    private fun snapshot(view: CameraCompositionView): Bitmap =
        Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).also(bitmaps::add).apply {
            eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(this))
        }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun assertDrawn(bitmap: Bitmap, x: Int, y: Int) {
        assertTrue("Something drawn at ($x, $y)", Color.alpha(bitmap.getPixel(x, y)) > 0)
    }

    private fun assertClear(bitmap: Bitmap, x: Int, y: Int) {
        assertEquals("Alpha at ($x, $y)", 0, Color.alpha(bitmap.getPixel(x, y)))
    }

    private fun string(id: Int): String = instrumentation.targetContext.getString(id)

    private fun onMain(block: () -> Unit) {
        // Report assertion failures to JUnit instead of crashing the app's main thread.
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 400

        // On the first vertical grid line, above the middle cell's bar.
        const val GRID_X = 100
        const val ABOVE_BAR_Y = 60

        // On the bar's middle segment, between the vertical lines.
        const val BAR_X = 150
        const val BAR_Y = 200
    }
}
