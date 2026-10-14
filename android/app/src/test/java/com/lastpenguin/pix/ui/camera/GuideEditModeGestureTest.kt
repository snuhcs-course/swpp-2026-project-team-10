// AI-generated with Claude Code, 2026-10-13, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lastpenguin.pix.guide.GuideOverlayView
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The preview container as Camera and Subject view build it: the pinch controller on the video, the guide overlay
 * above it. One pinch, two outcomes, decided by guide-edit mode alone (AD-17, #48).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideEditModeGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bitmaps = mutableListOf<Bitmap>()
    private var eventTime = 1_000L

    @After
    fun recycleBitmaps() = onMain {
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    @Test
    fun withTheModeOffAPinchZoomsTheCameraAndLeavesTheGuideAlone() = onMain {
        val fixture = fixture(editing = false)

        pinch(fixture.host)

        assertTrue(fixture.zoomRequests.isNotEmpty())
        assertEquals(1, fixture.zoomRequests.count { it.final })
        assertTrue(fixture.zoomRequests.last().ratio > 1f)
        assertTrue(fixture.guideSteps.isEmpty())
    }

    @Test
    fun withTheModeOnTheSamePinchResizesTheGuideAndNeverZooms() = onMain {
        val fixture = fixture(editing = true)

        pinch(fixture.host)

        assertTrue(fixture.guideSteps.any { it.scale > 1f })
        assertEquals(1, fixture.guideSteps.count { it.final })
        assertTrue(fixture.zoomRequests.isEmpty())
    }

    @Test
    fun turningTheModeOffMidGestureNeverHandsTheRemainingFingersToZoom() = onMain {
        val fixture = fixture(editing = true)
        val host = fixture.host

        touch(host, MotionEvent.ACTION_DOWN, point(7, 200f, 600f))
        touch(host, MotionEvent.ACTION_POINTER_DOWN, point(7, 200f, 600f), point(31, 600f, 600f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_MOVE, point(7, 180f, 600f), point(31, 640f, 600f))
        // The screen renders the mode change: the overlay stops editing and the pinch controller is enabled.
        fixture.overlay.editable = false
        fixture.pinch.enabled = true
        touch(host, MotionEvent.ACTION_MOVE, point(7, 100f, 600f), point(31, 800f, 600f))
        touch(host, MotionEvent.ACTION_POINTER_UP, point(7, 100f, 600f), point(31, 800f, 600f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_UP, point(7, 100f, 600f))

        assertTrue(fixture.zoomRequests.isEmpty())
        assertEquals(1, fixture.guideSteps.count { it.final })
    }

    private data class ZoomRequest(val ratio: Float, val final: Boolean)

    private data class GuideStep(val dx: Float, val dy: Float, val scale: Float, val final: Boolean)

    private class Fixture(
        val host: FrameLayout,
        val overlay: GuideOverlayView,
        val pinch: PinchZoomController,
        val zoomRequests: MutableList<ZoomRequest>,
        val guideSteps: MutableList<GuideStep>,
    )

    private fun fixture(editing: Boolean): Fixture {
        val context = instrumentation.targetContext
        val zoomRequests = mutableListOf<ZoomRequest>()
        val guideSteps = mutableListOf<GuideStep>()
        val video = View(context)
        val pinch = PinchZoomController(video, range = { 1f..8f }, current = { 1f }) { ratio, final ->
            zoomRequests += ZoomRequest(ratio, final)
        }
        pinch.enabled = !editing
        val overlay = GuideOverlayView(context).apply {
            editable = editing
            onGesture = { dx, dy, scale, final -> guideSteps += GuideStep(dx, dy, scale, final) }
        }
        val host = FrameLayout(context).apply {
            isMotionEventSplittingEnabled = false
            addView(video, FrameLayout.LayoutParams(900, 1200))
            addView(overlay, FrameLayout.LayoutParams(900, 1200))
            measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 900, 1200)
        }
        overlay.render(guide(), GuideState(guideId = "guide"))
        return Fixture(host, overlay, pinch, zoomRequests, guideSteps)
    }

    private fun guide(): ReferenceGuide {
        fun image(): Bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).also(bitmaps::add)
        return ReferenceGuide("guide", image(), image(), 0.5f, GuideSource.GALLERY)
    }

    /** Two fingers inside the image, 400 px apart, spreading to 800 px. */
    private fun pinch(host: View) {
        val slop = ViewConfiguration.get(host.context).scaledTouchSlop * 2f + 2f
        touch(host, MotionEvent.ACTION_DOWN, point(7, 200f, 600f))
        touch(host, MotionEvent.ACTION_POINTER_DOWN, point(7, 200f, 600f), point(31, 600f, 600f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_MOVE, point(7, 200f, 600f), point(31, 600f + slop, 600f))
        touch(host, MotionEvent.ACTION_MOVE, point(7, 100f, 600f), point(31, 900f, 600f))
        touch(host, MotionEvent.ACTION_POINTER_UP, point(7, 100f, 600f), point(31, 900f, 600f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_UP, point(7, 100f, 600f))
    }

    private data class Pointer(val id: Int, val x: Float, val y: Float)

    private fun point(id: Int, x: Float, y: Float): Pointer = Pointer(id, x, y)

    private fun touch(view: View, action: Int, vararg pointers: Pointer, actionIndex: Int = 0): Boolean {
        val properties = pointers.map { pointer ->
            MotionEvent.PointerProperties().apply {
                id = pointer.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coordinates = pointers.map { pointer ->
            MotionEvent.PointerCoords().apply {
                x = pointer.x
                y = pointer.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            1_000L,
            eventTime.also { eventTime += 16L },
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            pointers.size,
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
        return try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }
}
