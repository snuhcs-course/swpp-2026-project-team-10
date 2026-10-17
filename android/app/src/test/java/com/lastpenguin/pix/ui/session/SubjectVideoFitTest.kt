// AI-generated with Claude Code, 2026-10-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.session

import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.guide.GuideOverlayView
import com.lastpenguin.pix.ui.camera.PinchZoomController
import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/*
 * Partitions for Subject view's video against its overlays (#86, Design 2.6.4), with the real layout.
 * The video in each ratio the photographer can choose (4:3, 16:9, 1:1): it covers exactly the rectangle that the grid
 * and the guide fit. Before the first frame: it covers the whole area. A pinch that starts in the margin around a
 * video of another shape: it still zooms, because the whole area takes the pinch.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubjectVideoFitTest {
    private var eventTime = 1_000L

    @Test
    fun liveView_videoInEachRatio_coversExactlyTheRectangleTheOverlaysFit() {
        for (ratio in AspectRatio.entries) {
            val screen = screen()
            // The stream keeps the frame's shape (#80): 960×720, 1280×720, or 720×720, upright on the subject.
            val (width, height) = when (ratio) {
                AspectRatio.R4_3 -> 720 to 960
                AspectRatio.R16_9 -> 720 to 1280
                AspectRatio.R1_1 -> 720 to 720
            }
            screen.video.onFrameResolutionChanged(width, height, 0)
            screen.layOut()

            val area = screen.container
            val expected = fitViewfinderFrame(area.width.toFloat(), area.height.toFloat(), ratio.frameAspect)
            val video = screen.video
            assertEquals("$ratio left", expected.left, video.left.toFloat(), 1f)
            assertEquals("$ratio top", expected.top, video.top.toFloat(), 1f)
            assertEquals("$ratio width", expected.width, video.width.toFloat(), 1f)
            assertEquals("$ratio height", expected.height, video.height.toFloat(), 1f)
        }
    }

    @Test
    fun liveView_beforeTheFirstFrame_coversTheWholeArea() {
        val screen = screen()

        screen.layOut()

        assertEquals(screen.container.width, screen.video.width)
        assertEquals(screen.container.height, screen.video.height)
    }

    @Test
    fun pinch_startingInTheMarginAboveASquareVideo_zoomsTheCamera() {
        val screen = screen()
        screen.video.onFrameResolutionChanged(720, 720, 0)
        screen.layOut()
        val requests = mutableListOf<Float>()
        // As SubjectFragment wires it: the pinch on the whole area, the guide overlay passing touches through.
        PinchZoomController(screen.container, range = { 1f..8f }, current = { 1f }) { ratio, _ -> requests += ratio }
        val margin = screen.video.top / 2f
        assertTrue("a square video leaves a margin above it", margin > 20f)

        pinch(screen.container, firstX = 100f, y = margin)

        assertTrue(requests.isNotEmpty())
        assertTrue(requests.last() > 1f)
    }

    private class Screen(val root: View, val container: FrameLayout, val video: SurfaceViewRenderer) {
        fun layOut() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 1080, 2340)
        }
    }

    /** Subject view's layout, with the renderer's scaling as SubjectFragment sets it. */
    private fun screen(): Screen {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val root = LayoutInflater.from(activity).inflate(R.layout.fragment_subject, null)
        activity.setContentView(root)
        val video = root.findViewById<SurfaceViewRenderer>(R.id.liveView)
        video.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        root.findViewById<GuideOverlayView>(R.id.guideOverlay).editable = false
        return Screen(root, root.findViewById(R.id.videoContainer), video)
    }

    /** Two fingers 400 px apart from [firstX], spreading past the slop and then to twice as far. */
    private fun pinch(view: View, firstX: Float, y: Float) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop * 2f + 2f
        val second = firstX + 400f
        touch(view, MotionEvent.ACTION_DOWN, firstX to y)
        touch(view, MotionEvent.ACTION_POINTER_DOWN, firstX to y, second to y, actionIndex = 1)
        touch(view, MotionEvent.ACTION_MOVE, firstX to y, second + slop to y)
        touch(view, MotionEvent.ACTION_MOVE, firstX to y, firstX + 800f to y)
        touch(view, MotionEvent.ACTION_POINTER_UP, firstX to y, firstX + 800f to y, actionIndex = 1)
        touch(view, MotionEvent.ACTION_UP, firstX to y)
    }

    private fun touch(view: View, action: Int, vararg points: Pair<Float, Float>, actionIndex: Int = 0) {
        val properties = points.indices.map { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coordinates = points.map { (x, y) ->
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            1_000L,
            eventTime.also { eventTime += 16L },
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            points.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
