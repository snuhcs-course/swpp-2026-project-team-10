package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.sqrt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** MotionEvent dispatch through real Views, including ownership of multi-touch streams over the camera preview. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideOverlayGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bitmaps = mutableListOf<Bitmap>()
    private var eventTime = 1_000L

    @After
    fun recycleBitmaps() = onMain {
        bitmaps.forEach(Bitmap::recycle)
        bitmaps.clear()
    }

    @Test
    fun dragUsesTheFittedFrameAndIncludesMovementBeforeSlopAndOnRelease() = onMain {
        val fixture = fixture(500, 400)
        val view = fixture.view
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()

        assertTrue(touch(view, MotionEvent.ACTION_DOWN, point(7, 250f, 200f)))
        touch(view, MotionEvent.ACTION_MOVE, point(7, 250f + slop / 4f, 200f))
        assertTrue(fixture.steps.isEmpty())
        touch(view, MotionEvent.ACTION_MOVE, point(7, 310f, 280f))
        touch(view, MotionEvent.ACTION_UP, point(7, 340f, 320f))

        // The 500 x 400 View contains a 300 x 400 image, offset 100 px from the left.
        assertEquals(3, fixture.steps.size)
        assertStep(fixture.steps[0], dx = 0.2f, dy = 0.2f)
        assertStep(fixture.steps[1], dx = 0.1f, dy = 0.1f)
        assertStep(fixture.steps[2], final = true)
    }

    @Test
    fun aTapDoesNotPublishChangesOrAGestureEnd() = onMain {
        val fixture = fixture()

        assertTrue(touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f)))
        touch(fixture.view, MotionEvent.ACTION_UP, point(7, 150f, 200f))

        assertTrue(fixture.steps.isEmpty())
    }

    @Test
    fun cancelPublishesOneFinalValueAfterAManipulation() = onMain {
        val fixture = fixture()

        touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f))
        touch(fixture.view, MotionEvent.ACTION_MOVE, point(7, 210f, 240f))
        touch(fixture.view, MotionEvent.ACTION_CANCEL, point(7, 210f, 240f))
        touch(fixture.view, MotionEvent.ACTION_CANCEL, point(7, 210f, 240f))

        assertEquals(2, fixture.steps.size)
        assertStep(fixture.steps[0], dx = 0.2f, dy = 0.1f)
        assertStep(fixture.steps[1], final = true)
    }

    @Test
    fun pinchUsesEuclideanDistanceAndCentroidThenContinuesWithTheRemainingPointer() = onMain {
        val fixture = fixture()
        val view = fixture.view

        touch(view, MotionEvent.ACTION_DOWN, point(7, 130f, 200f))
        touch(view, MotionEvent.ACTION_POINTER_DOWN, point(7, 130f, 200f), point(31, 170f, 200f), actionIndex = 1)
        assertTrue(fixture.steps.isEmpty())
        touch(view, MotionEvent.ACTION_MOVE, point(7, 100f, 180f), point(31, 220f, 220f))
        touch(view, MotionEvent.ACTION_POINTER_UP, point(7, 100f, 180f), point(31, 220f, 220f), actionIndex = 0)
        assertEquals(1, fixture.steps.size)
        touch(view, MotionEvent.ACTION_MOVE, point(31, 250f, 260f))
        touch(view, MotionEvent.ACTION_UP, point(31, 250f, 260f))

        assertEquals(3, fixture.steps.size)
        assertStep(fixture.steps[0], dx = 10f / 300f, scale = sqrt(10f))
        assertStep(fixture.steps[1], dx = 0.1f, dy = 0.1f)
        assertStep(fixture.steps[2], final = true)
    }

    @Test
    fun extraPointersDoNotMoveTheGuideAndReplacingTheTrackedPairDoesNotJump() = onMain {
        val fixture = fixture()
        val view = fixture.view

        touch(view, MotionEvent.ACTION_DOWN, point(7, 130f, 200f))
        touch(view, MotionEvent.ACTION_POINTER_DOWN, point(7, 130f, 200f), point(31, 170f, 200f), actionIndex = 1)
        touch(
            view,
            MotionEvent.ACTION_POINTER_DOWN,
            point(7, 130f, 200f),
            point(31, 170f, 200f),
            point(5, 150f, 300f),
            actionIndex = 2,
        )
        assertTrue(fixture.steps.isEmpty())
        touch(view, MotionEvent.ACTION_MOVE, point(7, 90f, 160f), point(31, 210f, 160f), point(5, 280f, 380f))
        touch(
            view,
            MotionEvent.ACTION_POINTER_UP,
            point(7, 90f, 160f),
            point(31, 210f, 160f),
            point(5, 280f, 380f),
            actionIndex = 0,
        )
        assertEquals(1, fixture.steps.size)
        touch(view, MotionEvent.ACTION_MOVE, point(31, 240f, 200f), point(5, 310f, 420f))
        touch(view, MotionEvent.ACTION_CANCEL, point(31, 240f, 200f), point(5, 310f, 420f))

        assertEquals(3, fixture.steps.size)
        assertStep(fixture.steps[0], dy = -0.1f, scale = 3f)
        assertStep(fixture.steps[1], dx = 0.1f, dy = 0.1f)
        assertStep(fixture.steps[2], final = true)
    }

    @Test
    fun pointerChangesApplyTheirLastMovementBeforeRebasing() = onMain {
        val fixture = fixture()
        val view = fixture.view

        touch(view, MotionEvent.ACTION_DOWN, point(7, 100f, 200f))
        touch(view, MotionEvent.ACTION_POINTER_DOWN, point(7, 160f, 240f), point(31, 200f, 240f), actionIndex = 1)
        // No intervening MOVE: apply the old pair's last pan and scale before dropping pointer 7.
        touch(view, MotionEvent.ACTION_POINTER_UP, point(7, 170f, 280f), point(31, 250f, 280f), actionIndex = 0)
        touch(view, MotionEvent.ACTION_UP, point(31, 250f, 280f))

        assertEquals(3, fixture.steps.size)
        assertStep(fixture.steps[0], dx = 0.2f, dy = 0.1f)
        assertStep(fixture.steps[1], dx = 0.1f, dy = 0.1f, scale = 2f)
        assertStep(fixture.steps[2], final = true)
    }

    @Test
    fun onlyTheVisiblePartOfTheGuideAcceptsTheInitialTouch() = onMain {
        val fixture = fixture(500, 400)
        fixture.view.render(fixture.guide, fixture.state.copy(cx = 0f, height = 1f))

        // The guide spans x = 0..200, but only x = 100..200 falls inside the camera image.
        assertFalse(touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 50f, 200f)))
        assertFalse(touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 250f, 200f)))
        assertTrue(touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f)))
        touch(fixture.view, MotionEvent.ACTION_CANCEL, point(7, 150f, 200f))
        assertTrue(fixture.steps.isEmpty())
    }

    @Test
    fun unavailableOrReadonlyGuidesLetTouchesPassThrough() = onMain {
        val makeUnavailable: List<(Fixture) -> Unit> = listOf(
            { it.view.editable = false },
            { it.view.isEnabled = false },
            { it.view.onGesture = null },
            { it.view.render(null, it.state) },
            { it.view.render(it.guide, it.state.copy(visible = false)) },
            { it.view.render(it.guide, it.state.copy(guideId = "different")) },
        )

        for (change in makeUnavailable) {
            val fixture = fixture()
            change(fixture)
            assertFalse(touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f)))
            assertTrue(fixture.steps.isEmpty())
        }
    }

    @Test
    fun aGuideGestureNeverAlsoReachesThePreview() = onMain {
        val fixture = fixture()
        val previewActions = mutableListOf<Int>()
        val host = host(fixture.view, previewActions)

        touch(host, MotionEvent.ACTION_DOWN, point(7, 150f, 200f))
        touch(host, MotionEvent.ACTION_POINTER_DOWN, point(7, 150f, 200f), point(31, 20f, 200f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_MOVE, point(7, 210f, 240f), point(31, 0f, 240f))
        touch(host, MotionEvent.ACTION_POINTER_UP, point(7, 210f, 240f), point(31, 0f, 240f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_UP, point(7, 210f, 240f))

        assertTrue(previewActions.isEmpty())
        assertTrue(fixture.steps.any { !it.final })
        assertEquals(1, fixture.steps.count { it.final })
    }

    @Test
    fun aPreviewGestureKeepsOwnershipWhenTheSecondFingerLandsOnTheGuide() = onMain {
        val fixture = fixture()
        val previewActions = mutableListOf<Int>()
        val host = host(fixture.view, previewActions)

        touch(host, MotionEvent.ACTION_DOWN, point(7, 20f, 200f))
        touch(host, MotionEvent.ACTION_POINTER_DOWN, point(7, 20f, 200f), point(31, 150f, 200f), actionIndex = 1)
        touch(host, MotionEvent.ACTION_MOVE, point(7, 0f, 180f), point(31, 210f, 220f))
        touch(host, MotionEvent.ACTION_POINTER_UP, point(7, 0f, 180f), point(31, 210f, 220f), actionIndex = 0)
        touch(host, MotionEvent.ACTION_UP, point(31, 210f, 220f))

        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_UP,
            ),
            previewActions,
        )
        assertTrue(fixture.steps.isEmpty())
    }

    @Test
    fun invalidatingAnActiveGesturePreventsAnyFurtherMovementFromThatStream() = onMain {
        val invalidate: List<(Fixture) -> Unit> = listOf(
            { it.view.render(null, it.state) },
            { it.view.render(it.guide, it.state.copy(visible = false)) },
            { it.view.render(it.guide, it.state.copy(guideId = "different")) },
            { it.view.render(guide("replacement"), GuideState(guideId = "replacement")) },
            { it.view.editable = false },
            { it.view.isEnabled = false },
            { it.view.layout(0, 0, 500, 400) },
        )

        for (change in invalidate) {
            val fixture = fixture()
            touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f))
            touch(fixture.view, MotionEvent.ACTION_MOVE, point(7, 210f, 240f))
            assertTrue(fixture.steps.any { !it.final })
            change(fixture)
            val stepsAfterInvalidation = fixture.steps.toList()

            touch(fixture.view, MotionEvent.ACTION_MOVE, point(7, 240f, 280f))
            touch(fixture.view, MotionEvent.ACTION_UP, point(7, 240f, 280f))

            assertEquals(stepsAfterInvalidation, fixture.steps)
        }
    }

    @Test
    fun replacementDoesNotReceiveTheOldGesturesFinalUpdate() = onMain {
        val fixture = fixture()
        touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f))
        touch(fixture.view, MotionEvent.ACTION_MOVE, point(7, 210f, 240f))
        val before = fixture.steps.toList()

        fixture.view.render(guide("replacement"), GuideState(guideId = "replacement"))
        touch(fixture.view, MotionEvent.ACTION_UP, point(7, 210f, 240f))

        assertEquals(before, fixture.steps)
        assertTrue(fixture.steps.none { it.final })
        // The next sequence can manipulate the replacement normally.
        touch(fixture.view, MotionEvent.ACTION_DOWN, point(7, 150f, 200f))
        touch(fixture.view, MotionEvent.ACTION_MOVE, point(7, 210f, 240f))
        touch(fixture.view, MotionEvent.ACTION_UP, point(7, 210f, 240f))
        assertEquals(3, fixture.steps.size)
        assertStep(fixture.steps.last(), final = true)
    }

    private fun onMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }

    private data class Step(val dx: Float, val dy: Float, val scale: Float, val final: Boolean)

    private data class Fixture(
        val view: GuideOverlayView,
        val guide: ReferenceGuide,
        val state: GuideState,
        val steps: MutableList<Step>,
    )

    private fun fixture(width: Int = 300, height: Int = 400): Fixture {
        val guide = guide()
        val state = GuideState(guideId = guide.id)
        val steps = mutableListOf<Step>()
        val view = GuideOverlayView(instrumentation.targetContext).apply {
            layout(0, 0, width, height)
            render(guide, state)
            onGesture = { dx, dy, scale, final -> steps += Step(dx, dy, scale, final) }
        }
        return Fixture(view, guide, state, steps)
    }

    private fun guide(id: String = "guide"): ReferenceGuide {
        fun image(): Bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).also(bitmaps::add)
        return ReferenceGuide(id, image(), image(), 0.5f, GuideSource.GALLERY)
    }

    private fun host(overlay: GuideOverlayView, previewActions: MutableList<Int>): FrameLayout =
        FrameLayout(instrumentation.targetContext).apply {
            // The production host must also disable splitting, or a second pointer can enter a sibling View.
            isMotionEventSplittingEnabled = false
            addView(
                View(context).apply {
                    setOnTouchListener { _, event ->
                        previewActions += event.actionMasked
                        true
                    }
                },
                FrameLayout.LayoutParams(300, 400),
            )
            addView(overlay, FrameLayout.LayoutParams(300, 400))
            measure(
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 300, 400)
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

    private fun assertStep(
        actual: Step,
        dx: Float = 0f,
        dy: Float = 0f,
        scale: Float = 1f,
        final: Boolean = false,
    ) {
        assertEquals("dx", dx, actual.dx, 0.0001f)
        assertEquals("dy", dy, actual.dy, 0.0001f)
        assertEquals("scale", scale, actual.scale, 0.0001f)
        assertEquals("final", final, actual.final)
    }
}
