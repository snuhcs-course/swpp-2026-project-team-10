// AI-generated with Claude Code, 2026-10-06, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.lastpenguin.pix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Exercises Material's actual callback ordering, including touch and virtual accessibility actions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackedSliderControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var eventTime = 1_000L

    @Test
    fun renderingUsesTheHardwareRangeAndNeverSendsZoomRequests() = onMain {
        val fixture = fixture()

        fixture.controller.render(0.6f, 8f, 2f, enabled = true)

        assertEquals(0.6f, fixture.slider.valueFrom, 0f)
        assertEquals(8f, fixture.slider.valueTo, 0f)
        assertEquals(2f, fixture.slider.value, 0f)
        assertEquals(0f, fixture.slider.stepSize, 0f)
        assertTrue(fixture.slider.isEnabled)
        assertTrue(fixture.slider.hasLabelFormatter())

        fixture.controller.render(2f, 5f, 20f, enabled = true)
        assertEquals(5f, fixture.slider.value, 0f)
        fixture.controller.render(0.5f, 1.5f, 0.1f, enabled = false)
        assertEquals(0.5f, fixture.slider.value, 0f)
        assertFalse(fixture.slider.isEnabled)
        assertTrue(fixture.requests.isEmpty())
        draw(fixture.slider)
    }

    @Test
    fun unavailableOrSingleZoomRangesStayDisabledAndSafeForMaterialToDraw() = onMain {
        val fixture = fixture()
        val unavailableRanges = listOf(
            1f to 1f,
            4f to 2f,
            0f to 4f,
            Float.NaN to 4f,
            1f to Float.POSITIVE_INFINITY,
        )

        for ((minimum, maximum) in unavailableRanges) {
            fixture.controller.render(minimum, maximum, 1f, enabled = true)

            assertFalse(fixture.slider.isEnabled)
            assertFalse(fixture.controller.isTracking)
            assertTrue(fixture.slider.valueFrom < fixture.slider.valueTo)
            assertTrue(fixture.slider.value in fixture.slider.valueFrom..fixture.slider.valueTo)
            draw(fixture.slider)
        }
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun delayedAppliedZoomDoesNotPullTheThumbDuringADragAndReleaseSendsTheLatestValue() = onMain {
        val fixture = fixture()
        fixture.controller.render(0.6f, 4f, 1f, enabled = true)

        touch(fixture.slider, MotionEvent.ACTION_DOWN, 1f)
        touch(fixture.slider, MotionEvent.ACTION_MOVE, 3f)
        assertTrue(fixture.controller.isTracking)
        assertTrue(fixture.requests.isNotEmpty())
        assertTrue(fixture.requests.none { it.final })

        fixture.controller.render(0.6f, 4f, 1.2f, enabled = true)

        assertEquals(3f, fixture.slider.value, TOLERANCE)
        touch(fixture.slider, MotionEvent.ACTION_UP, 3.5f)
        assertFalse(fixture.controller.isTracking)
        assertFinal(fixture, 3.5f)

        val requestCount = fixture.requests.size
        fixture.controller.render(0.6f, 4f, 3.4f, enabled = true)
        assertEquals(3.4f, fixture.slider.value, TOLERANCE)
        assertEquals(requestCount, fixture.requests.size)
    }

    @Test
    fun tappingTheTrackDoesNotSendAFinalBeforeTheFingerIsReleased() = onMain {
        val fixture = fixture()
        fixture.controller.render(0.6f, 4f, 1f, enabled = true)

        // Material updates the value on DOWN before invoking its onStartTrackingTouch listener.
        touch(fixture.slider, MotionEvent.ACTION_DOWN, 3f)
        assertTrue(fixture.controller.isTracking)
        assertTrue(fixture.requests.isNotEmpty())
        assertTrue(fixture.requests.none { it.final })
        touch(fixture.slider, MotionEvent.ACTION_UP, 3f)

        assertFinal(fixture, 3f)
        assertFalse(fixture.controller.isTracking)
    }

    @Test
    fun cancellationAndLifecycleFinishEachSendOneFinalAndIgnoreDuplicateTermination() = onMain {
        for (cancelTouch in listOf(true, false)) {
            val fixture = fixture()
            fixture.controller.render(0.6f, 4f, 1f, enabled = true)
            touch(fixture.slider, MotionEvent.ACTION_DOWN, 1f)
            touch(fixture.slider, MotionEvent.ACTION_MOVE, 2.5f)

            if (cancelTouch) {
                touch(fixture.slider, MotionEvent.ACTION_CANCEL, 2.5f)
            } else {
                fixture.controller.finishInteraction()
                touch(fixture.slider, MotionEvent.ACTION_UP, 2.5f)
            }
            fixture.controller.finishInteraction()
            fixture.controller.finishInteraction()

            assertFalse(fixture.controller.isTracking)
            assertFinal(fixture, 2.5f)
        }
    }

    @Test
    fun disablingOrChangingRangeFinishesTheOldRequestBeforeUpdatingTheSlider() = onMain {
        for (changeRange in listOf(true, false)) {
            val fixture = fixture()
            fixture.controller.render(0.6f, 4f, 1f, enabled = true)
            touch(fixture.slider, MotionEvent.ACTION_DOWN, 1f)
            touch(fixture.slider, MotionEvent.ACTION_MOVE, 2.5f)

            if (changeRange) {
                fixture.controller.render(5f, 10f, 2f, enabled = true)
                assertEquals(5f, fixture.slider.value, TOLERANCE)
                touch(fixture.slider, MotionEvent.ACTION_MOVE, 8f)
                touch(fixture.slider, MotionEvent.ACTION_UP, 9f)
                assertEquals(5f, fixture.slider.value, TOLERANCE)
            } else {
                fixture.controller.render(0.6f, 4f, 1f, enabled = false)
                assertFalse(fixture.slider.isEnabled)
            }

            assertFalse(fixture.controller.isTracking)
            assertFinal(fixture, 2.5f)
            fixture.controller.finishInteraction()
            assertFinal(fixture, 2.5f)
            draw(fixture.slider)
        }
    }

    @Test
    fun accessibilityAndKeyboardChangesSendFinalValuesWithoutATouchGesture() = onMain {
        val fixture = fixture()
        fixture.controller.render(0.6f, 4f, 1f, enabled = true)
        val arguments = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 3f)
        }

        // Material exposes its single thumb as virtual view 0, rather than the host View's progress.
        assertTrue(
            checkNotNull(fixture.slider.accessibilityNodeProvider).performAction(
                0,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                arguments,
            ),
        )

        assertEquals(1, fixture.requests.size)
        assertEquals(3f, fixture.requests.single().ratio, TOLERANCE)
        assertTrue(fixture.requests.single().final)
        assertFalse(fixture.controller.isTracking)

        fixture.slider.requestFocus()
        assertTrue(fixture.slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        fixture.slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))

        assertEquals(2, fixture.requests.size)
        assertTrue(fixture.requests.last().ratio < 3f)
        assertTrue(fixture.requests.last().final)
        assertFalse(fixture.controller.isTracking)
    }

    private data class Request(val ratio: Float, val final: Boolean)

    private data class Fixture(
        val slider: Slider,
        val controller: TrackedSliderController,
        val requests: MutableList<Request>,
    )

    private fun fixture(): Fixture {
        val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Pix)
        val slider = Slider(context)
        val host = FrameLayout(context).apply {
            addView(slider, FrameLayout.LayoutParams(600, 160))
            measure(
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 600, 160)
        }
        assertEquals(host, slider.parent)
        val requests = mutableListOf<Request>()
        val controller = TrackedSliderController.zoom(slider) { ratio, final -> requests += Request(ratio, final) }
        return Fixture(slider, controller, requests)
    }

    private fun touch(slider: Slider, action: Int, ratio: Float) {
        val fraction = (ratio - slider.valueFrom) / (slider.valueTo - slider.valueFrom)
        val x = slider.trackSidePadding + fraction * slider.trackWidth
        val event = MotionEvent.obtain(1_000L, eventTime, action, x, slider.height / 2f, 0)
        eventTime += 16L
        try {
            assertTrue(slider.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

    private fun assertFinal(fixture: Fixture, ratio: Float) {
        assertEquals(1, fixture.requests.count { it.final })
        assertEquals(ratio, fixture.requests.last().ratio, TOLERANCE)
        assertTrue(fixture.requests.last().final)
    }

    private fun draw(slider: Slider) {
        val bitmap = Bitmap.createBitmap(slider.width, slider.height, Bitmap.Config.ARGB_8888)
        try {
            slider.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
