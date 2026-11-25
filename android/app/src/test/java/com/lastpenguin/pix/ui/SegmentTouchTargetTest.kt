// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The switches' parts take taps on 48 dp, the touch target of NFR-16 (#298), on the Galaxy S22's 360 dp. Partitions:
 * each switch built on `Widget.Pix.Button.Segment` (the library's filters, Settings' guide thickness), whole 48 dp
 * shapes; the header's small switch on Generate poses (`Segment.Small`), a 32 dp shape in a 48 dp touch area, on a
 * track that hugs the shapes by 2 dp.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class SegmentTouchTargetTest {
    @get:Rule
    val activities = OwnActivities()

    private val activity = activities.start(R.style.Theme_Pix)
    private val density = activity.resources.displayMetrics.density

    private fun dp(px: Int): Int = (px / density).roundToInt()

    /** [row] of [layout], shown and laid out at the screen's width. */
    private fun row(layout: Int, row: Int): ViewGroup {
        val root = LayoutInflater.from(activity).inflate(layout, null)
        activity.setContentView(root)
        val switch = root.findViewById<ViewGroup>(row)
        var view: View? = switch
        while (view != null) {
            view.visibility = View.VISIBLE
            view = view.parent as? View
        }
        switch.children.forEach { it.visibility = View.VISIBLE }
        root.measure(
            View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return switch
    }

    private fun parts(row: ViewGroup): List<MaterialButton> = row.children.filterIsInstance<MaterialButton>().toList()

    @Test
    fun theSegmentSwitches_takeTapsOn48dp_withWholeShapes() {
        val switches = mapOf(
            "the library's filters" to row(R.layout.fragment_library, R.id.filterRow),
            "Settings' guide thickness" to row(R.layout.fragment_settings, R.id.thicknessRow),
        )
        for ((name, switch) in switches) {
            val parts = parts(switch)
            assertTrue("$name: ${parts.size} parts", parts.size >= 3)
            for (part in parts) {
                assertTrue("$name: ${dp(part.height)} dp tall", dp(part.height) >= 48)
                assertEquals("$name: the shape fills the part", 0, part.insetTop + part.insetBottom)
            }
        }
    }

    @Test
    fun theHeadersSmallSwitch_takesTapsOn48dp_withA32dpShape_onATrackThatHugsIt() {
        val switch = row(R.layout.fragment_describe_pose, R.id.historyFilter)
        val parts = parts(switch)
        assertEquals(2, parts.size)
        for (part in parts) {
            assertTrue("${dp(part.height)} dp tall", dp(part.height) >= 48)
            assertEquals(32, dp(part.height - part.insetTop - part.insetBottom))
        }
        // The track is drawn 2 dp around the shapes: the switch's padding plus the buttons' insets, less its own inset.
        val track = Rect().also { switch.background.getPadding(it) }
        val shapeTop = switch.paddingTop + parts.first().insetTop
        assertEquals(2, dp(shapeTop - track.top))
    }
}
