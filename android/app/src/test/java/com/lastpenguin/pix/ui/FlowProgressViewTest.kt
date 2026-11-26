// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer

/*
 * The pose flow's bar (FlowProgressView, R&S 6.3, #210). Partitions: a step inside the flow, none, and past either end
 * (boundary); a move to another step, to the same step during a move, and with the phone's animations off; a step coming
 * in from the one before it, from a later one going back, and from itself (#210); the screens
 * that show it from their layout, Generate poses, the set being generated, and Pick a pose, each at its step and across
 * the screen's width, past the layout's padding, which the layout draws past, under a screen's header 64 dp from the
 * top, with Back and the title where every header has them, as the scene photo's camera bar has them too
 * (CameraLayoutTest); four steps, since the scene photo's review went (#296). The camera's scene photo step is in
 * CameraFragmentRenderTest, and the bar filling as the step starts in CameraTransitionTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class FlowProgressViewTest {
    private companion object {
        /** Where the flow's bar starts on every step: under a screen's header, 8 dp and 56 dp from the top. */
        const val STEPS_BAR_TOP_DP = 64
    }

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Pix)

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    private fun play(ms: Long) {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    @Test
    fun aStep_fillsItsShareOfTheFlow_andStaysInIt() {
        val bar = FlowProgressView(context)
        assertEquals(0, bar.step)
        assertEquals(0f, bar.fraction, 0f)

        bar.step = 3
        assertEquals(0.75f, bar.fraction, 0.001f)

        bar.step = FlowProgressView.STEPS + 4
        assertEquals(FlowProgressView.STEPS, bar.step)
        assertEquals(1f, bar.fraction, 0f)

        bar.step = -1
        assertEquals(0, bar.step)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, bar.importantForAccessibility)
    }

    @Test
    fun moveTo_takesTheStepAtOnce_andFillsTheBarThereFromWhereItShows() {
        val bar = FlowProgressView(context).apply { step = 1 }
        ShadowChoreographer.setPaused(true)

        bar.moveTo(2)
        assertEquals(2, bar.step)
        assertEquals(0.5f, bar.fraction, 0.001f)
        assertEquals(0.25f, bar.filled, 0.001f)
        // The same step again: the move goes on.
        bar.moveTo(2)
        play(500)
        assertEquals(0.5f, bar.filled, 0.001f)

        // Set, a step shows at once.
        bar.step = 3
        assertEquals(0.75f, bar.filled, 0.001f)

        setAnimatorScale(0f)
        bar.moveTo(1)
        assertEquals(0.25f, bar.filled, 0.001f)
    }

    @Test
    fun aStepComingIn_fillsOnFromWhereTheLastLeftTheBar_orIsThereAtOnceForTheSameStep() {
        // Forward from the scene photo to Generate poses, and back from Pick a pose to it.
        for ((last, next) in listOf(1 to 2, 4 to 2)) {
            FlowProgressView.shownStep = last
            val bar = FlowProgressView(context)
            bar.arrive(next)
            assertEquals("$last to $next", next, bar.step)
            assertEquals("$last to $next", last / 4f, bar.filled, 0.001f)
            assertEquals(next, FlowProgressView.shownStep)
            play(Motion.MOVE_MS * 2)
            assertEquals("$last to $next", next / 4f, bar.filled, 0.001f)
        }

        // The same step again, as after a new view of the same screen: there at once.
        val again = FlowProgressView(context)
        again.arrive(2)
        assertEquals(0.5f, again.filled, 0.001f)
        FlowProgressView.shownStep = 0
    }

    @Test
    fun generatePoses_generating_andPickAPose_showTheirStep_acrossTheScreen_underTheirHeader() {
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (705 * density).toInt()
        for ((layout, step) in listOf(
            R.layout.fragment_describe_pose to 2,
            R.layout.fragment_generating to 3,
            R.layout.fragment_pick_pose to 4,
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
            // Under a screen's header, 64 dp from the top, where the scene photo's camera bar has it too, so it never
            // jumps from step to step; Back in the header's middle 8 dp from the edge, and the title 4 dp after it at a
            // header's size (CameraLayoutTest).
            assertEquals("top of $layout", (STEPS_BAR_TOP_DP * density).toInt(), box.top)
            val back = root.findViewById<View>(R.id.backButton)
            val backBox = Rect(0, 0, back.width, back.height).also { root.offsetDescendantRectToMyCoords(back, it) }
            assertEquals("Back of $layout", 36f, backBox.exactCenterY() / density, 1f)
            assertEquals("Back of $layout", 8, (backBox.left / density).roundToInt())
            assertEquals("Back of $layout", 48, (backBox.width() / density).roundToInt())
            val title = (back.parent as ViewGroup).getChildAt(1) as TextView
            val titleBox = Rect(0, 0, title.width, title.height).also { root.offsetDescendantRectToMyCoords(title, it) }
            assertEquals("title of $layout", 60, (titleBox.left / density).roundToInt())
            assertEquals("title of $layout", 18f, title.textSize / context.resources.displayMetrics.scaledDensity, 0.5f)
            // The bar reaches past the layout's padding on screen too: drawn there, not only laid out.
            assertFalse("$layout clips to its padding", (bar.parent as ViewGroup).clipToPadding)
        }
    }
}
