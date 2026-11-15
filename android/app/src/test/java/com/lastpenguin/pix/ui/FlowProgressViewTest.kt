// AI-generated with Claude Code, 2026-11-15, reviewed by Jaewan Park
package com.lastpenguin.pix.ui

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/*
 * The pose flow's bar (FlowProgressView, R&S 6.3, #210). Partitions: a step inside the flow, none, and past either end
 * (boundary); the screens that show it from their layout, Generate poses, the set being generated, and Pick a pose,
 * each at its step and across the screen's width, past the layout's padding. The camera's scene photo steps are in
 * CameraFragmentRenderTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class FlowProgressViewTest {
    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Pix)

    @Test
    fun aStep_fillsItsShareOfTheFlow_andStaysInIt() {
        val bar = FlowProgressView(context)
        assertEquals(0, bar.step)
        assertEquals(0f, bar.fraction, 0f)

        bar.step = 3
        assertEquals(0.6f, bar.fraction, 0.001f)

        bar.step = FlowProgressView.STEPS + 4
        assertEquals(FlowProgressView.STEPS, bar.step)
        assertEquals(1f, bar.fraction, 0f)

        bar.step = -1
        assertEquals(0, bar.step)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, bar.importantForAccessibility)
    }

    @Test
    fun generatePoses_generating_andPickAPose_showTheirStep_acrossTheScreen() {
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (705 * density).toInt()
        for ((layout, step) in listOf(
            R.layout.fragment_describe_pose to 3,
            R.layout.fragment_generating to 4,
            R.layout.fragment_pick_pose to 5,
        )) {
            val root = LayoutInflater.from(context).inflate(layout, null) as ViewGroup
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, width, height)
            val bar = root.findViewById<FlowProgressView>(R.id.flowProgress)
            val box = Rect(0, 0, bar.width, bar.height).also { root.offsetDescendantRectToMyCoords(bar, it) }

            assertEquals("step of $layout", step, bar.step)
            assertEquals("left of $layout", 0, box.left)
            assertEquals("right of $layout", width, box.right)
            assertEquals("height of $layout", (3 * density).toInt(), box.height())
        }
    }
}
