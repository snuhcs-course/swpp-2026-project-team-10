// AI-generated with Claude Code, 2026-10-13, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two-finger MotionEvents through the framework's ScaleGestureDetector, as the preview and live video receive them. */
@RunWith(AndroidJUnit4::class)
class PinchZoomControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var eventTime = 1_000L

    @Test
    fun aPinchAccumulatesFromTheCurrentZoomAndCommitsOnceOnRelease() = onMain {
        val fixture = fixture(current = 2f)

        pinch(fixture.view, from = 300f, to = 600f)

        assertTrue(fixture.requests.isNotEmpty())
        assertTrue(fixture.requests.dropLast(1).none { it.final })
        assertEquals(1, fixture.requests.count { it.final })
        // The span doubled after the pinch began, so the requested zoom doubled from where it started.
        assertEquals(4f, fixture.requests.last().ratio, TOLERANCE)
        assertFalse(fixture.controller.isTracking)
    }

    @Test
    fun requestsStayInsideTheRangeAndNeverFollowAsynchronousZoomChanges() = onMain {
        val fixture = fixture(current = 3.5f, range = 1f..4f)

        pinch(fixture.view, from = 200f, to = 800f) { fixture.current = 1f }

        assertTrue(fixture.requests.all { it.ratio in 1f..4f })
        assertEquals(4f, fixture.requests.last().ratio, TOLERANCE)
        assertTrue(fixture.requests.last().final)
    }

    @Test
    fun cancellationAndFinishEachCommitOnceAndADuplicateIsIgnored() = onMain {
        for (cancelTouch in listOf(true, false)) {
            val fixture = fixture(current = 1f)
            val view = fixture.view
            touch(view, MotionEvent.ACTION_DOWN, point(7, 100f, 200f))
            touch(view, MotionEvent.ACTION_POINTER_DOWN, point(7, 100f, 200f), point(31, 400f, 200f), actionIndex = 1)
            touch(view, MotionEvent.ACTION_MOVE, point(7, 80f, 200f), point(31, 420f, 200f))
            touch(view, MotionEvent.ACTION_MOVE, point(7, 0f, 200f), point(31, 510f, 200f))
            assertTrue(fixture.controller.isTracking)

            if (cancelTouch) {
                touch(view, MotionEvent.ACTION_CANCEL, point(7, 0f, 200f), point(31, 510f, 200f))
            } else {
                fixture.controller.finishInteraction()
            }
            fixture.controller.finishInteraction()

            assertFalse(fixture.controller.isTracking)
            assertEquals(1, fixture.requests.count { it.final })
            assertEquals(fixture.requests.last().ratio, fixture.requests.dropLast(1).last().ratio, TOLERANCE)
        }
    }

    @Test
    fun withoutARangeOrWhileDisabledNothingIsRequested() = onMain {
        val unavailable = fixture(current = 1f, range = null)
        pinch(unavailable.view, from = 300f, to = 600f)
        assertTrue(unavailable.requests.isEmpty())

        val disabled = fixture(current = 1f)
        disabled.controller.enabled = false
        assertFalse(touch(disabled.view, MotionEvent.ACTION_DOWN, point(7, 100f, 200f)))
        pinch(disabled.view, from = 300f, to = 600f)
        assertTrue(disabled.requests.isEmpty())
        assertFalse(disabled.controller.isTracking)
    }

    @Test
    fun disablingMidPinchCommitsTheLastValueAndIgnoresTheRestOfTheStream() = onMain {
        val fixture = fixture(current = 1f)
        val view = fixture.view
        touch(view, MotionEvent.ACTION_DOWN, point(7, 100f, 200f))
        touch(view, MotionEvent.ACTION_POINTER_DOWN, point(7, 100f, 200f), point(31, 400f, 200f), actionIndex = 1)
        touch(view, MotionEvent.ACTION_MOVE, point(7, 80f, 200f), point(31, 420f, 200f))
        touch(view, MotionEvent.ACTION_MOVE, point(7, 0f, 200f), point(31, 510f, 200f))
        val before = fixture.requests.size

        fixture.controller.enabled = false
        touch(view, MotionEvent.ACTION_MOVE, point(7, 0f, 200f), point(31, 700f, 200f))
        touch(view, MotionEvent.ACTION_POINTER_UP, point(7, 0f, 200f), point(31, 700f, 200f), actionIndex = 1)
        touch(view, MotionEvent.ACTION_UP, point(7, 0f, 200f))

        assertEquals(before + 1, fixture.requests.size)
        assertTrue(fixture.requests.last().final)
        assertEquals(fixture.requests[before - 1].ratio, fixture.requests.last().ratio, TOLERANCE)
    }

    @Test
    fun aRangeThatDisappearsMidPinchEndsTheGesture() = onMain {
        val fixture = fixture(current = 1f)
        val view = fixture.view
        touch(view, MotionEvent.ACTION_DOWN, point(7, 100f, 200f))
        touch(view, MotionEvent.ACTION_POINTER_DOWN, point(7, 100f, 200f), point(31, 400f, 200f), actionIndex = 1)
        touch(view, MotionEvent.ACTION_MOVE, point(7, 80f, 200f), point(31, 420f, 200f))
        touch(view, MotionEvent.ACTION_MOVE, point(7, 0f, 200f), point(31, 510f, 200f))

        fixture.range = null
        touch(view, MotionEvent.ACTION_MOVE, point(7, 0f, 200f), point(31, 700f, 200f))
        touch(view, MotionEvent.ACTION_UP, point(7, 0f, 200f), point(31, 700f, 200f))

        assertFalse(fixture.controller.isTracking)
        assertEquals(1, fixture.requests.count { it.final })
        // 300 px apart at the start, 340 px when the gesture began, 510 px at the last step: 1.5x.
        assertEquals(1.5f, fixture.requests.last().ratio, TOLERANCE)
    }

    private data class Request(val ratio: Float, val final: Boolean)

    private class Fixture(
        val view: View,
        val requests: MutableList<Request>,
        var current: Float,
        var range: ClosedFloatingPointRange<Float>?,
    ) {
        lateinit var controller: PinchZoomController
    }

    private fun fixture(current: Float, range: ClosedFloatingPointRange<Float>? = 1f..8f): Fixture {
        val view = View(instrumentation.targetContext).apply { layout(0, 0, 900, 1200) }
        val fixture = Fixture(view, mutableListOf(), current, range)
        fixture.controller = PinchZoomController(
            view,
            range = { fixture.range },
            current = { fixture.current },
        ) { ratio, final -> fixture.requests += Request(ratio, final) }
        return fixture
    }

    /**
     * A horizontal pinch: both fingers land [from] apart, move just past the detector's slop so that it begins,
     * then spread to [to] apart and lift. [between] runs after the gesture began and before the spread.
     */
    private fun pinch(view: View, from: Float, to: Float, between: () -> Unit = {}) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop * 2f + 2f
        touch(view, MotionEvent.ACTION_DOWN, point(7, 100f, 200f))
        touch(
            view,
            MotionEvent.ACTION_POINTER_DOWN,
            point(7, 100f, 200f),
            point(31, 100f + from, 200f),
            actionIndex = 1,
        )
        // The first movement past the span slop begins the gesture, at the span it reports as the starting span.
        touch(view, MotionEvent.ACTION_MOVE, point(7, 100f, 200f), point(31, 100f + from + slop, 200f))
        between()
        touch(view, MotionEvent.ACTION_MOVE, point(7, 100f, 200f), point(31, 100f + (from + slop) * (to / from), 200f))
        touch(
            view,
            MotionEvent.ACTION_POINTER_UP,
            point(7, 100f, 200f),
            point(31, 100f + (from + slop) * (to / from), 200f),
            actionIndex = 1,
        )
        touch(view, MotionEvent.ACTION_UP, point(7, 100f, 200f))
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

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
