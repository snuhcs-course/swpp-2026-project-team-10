// AI-generated with Claude Code, 2026-11-18, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.settings

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * Where the tutorial rings a control (`boundsOnScreen`, FR-10.4), as the control is drawn (#207).
 * Partitions (equivalence): a control upright; one inside a block turned a quarter either way and a half, as Guide's icon
 * is when the phone is held sideways; one moved and one grown by its own transform; one not shown.
 */
@RunWith(AndroidJUnit4::class)
class TutorialBoundsTest {
    /** An icon 40 px square at the top of a 100 x 60 px block, the block at (100, 100) on a frame. */
    private class Screen(val frame: FrameLayout, val block: FrameLayout, val icon: View)

    private fun screen(): Screen {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val frame = FrameLayout(activity)
        val block = FrameLayout(activity)
        val icon = View(activity)
        block.addView(icon, FrameLayout.LayoutParams(40, 40).apply { leftMargin = 30 })
        frame.addView(
            block,
            FrameLayout.LayoutParams(100, 60).apply {
                leftMargin = 100
                topMargin = 100
            },
        )
        activity.setContentView(frame)
        idle()
        return Screen(frame, block, icon)
    }

    /** [left], [top], [right], and [bottom] on the frame, on screen. */
    private fun Screen.onScreen(left: Int, top: Int, right: Int, bottom: Int): Rect {
        val at = IntArray(2).also(frame::getLocationOnScreen)
        return Rect(left + at[0], top + at[1], right + at[0], bottom + at[1])
    }

    @Test
    fun anUprightControl_isWhereItIsLaidOut() {
        val screen = screen()

        assertEquals(screen.onScreen(130, 100, 170, 140), boundsOnScreen(screen.icon))
    }

    @Test
    fun aControlInATurnedBlock_isWhereItIsDrawn() {
        val screen = screen()
        // The block turns about its middle, (150, 130); the icon's middle, 10 px above it, goes round with it.
        val expected = mapOf(
            -90f to screen.onScreen(120, 110, 160, 150),
            90f to screen.onScreen(140, 110, 180, 150),
            180f to screen.onScreen(130, 120, 170, 160),
        )
        for ((degrees, box) in expected) {
            screen.block.rotation = degrees

            assertEquals("$degrees", box, boundsOnScreen(screen.icon))
        }
    }

    @Test
    fun aMovedOrGrownControl_isWhereItIsDrawn() {
        val screen = screen()

        screen.icon.translationX = 20f
        assertEquals(screen.onScreen(150, 100, 190, 140), boundsOnScreen(screen.icon))

        screen.icon.translationX = 0f
        screen.icon.scaleX = 1.5f
        screen.icon.scaleY = 1.5f
        assertEquals(screen.onScreen(120, 90, 180, 150), boundsOnScreen(screen.icon))
    }

    @Test
    fun aControlNotShown_hasNoPlace() {
        val screen = screen()

        screen.block.visibility = View.INVISIBLE

        assertNull(boundsOnScreen(screen.icon))
    }
}
