package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.GuideStyle
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraGuideGestureTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val camera = FakeCamera()
    private val guides = InMemoryGuideRepository()
    private lateinit var model: CameraViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        model = CameraViewModel(camera, guides)
        store.put("camera", model)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun rapidStepsAccumulateAgainstTheRepositoryBeforeRenderingCatchesUp() = runTest {
        guides.setGuide(guide())

        model.onGuideGesture(0.1f, -0.1f, 1.5f, final = false)
        model.onGuideGesture(0.2f, 0.2f, 0.5f, final = false)
        model.onGuideGesture(-0.1f, 0.1f, 2f, final = true)

        assertNull(model.uiState.value.guide)
        assertEquals(0.7f, guides.state.value.cx, EPSILON)
        assertEquals(0.7f, guides.state.value.cy, EPSILON)
        assertEquals(1.05f, guides.state.value.height, EPSILON)
        runCurrent()
        assertEquals(guides.state.value, model.uiState.value.guideState)
    }

    @Test
    fun pinchingAndDraggingRespectRepositoryScaleAndVisibilityLimits() {
        guides.setGuide(guide())

        model.onGuideGesture(100f, -100f, 100f, final = false)

        assertEquals(GuideState.MAX_HEIGHT, guides.state.value.height, EPSILON)
        assertEquals(1.42f, guides.state.value.cx, EPSILON)
        assertEquals(-0.63f, guides.state.value.cy, EPSILON)

        model.onGuideGesture(-100f, 100f, 0.001f, final = true)

        assertEquals(GuideState.MIN_HEIGHT, guides.state.value.height, EPSILON)
        assertEquals(-0.042f, guides.state.value.cx, EPSILON)
        assertEquals(1.063f, guides.state.value.cy, EPSILON)
    }

    @Test
    fun identityEndStepPublishesTheSameClampedStateAsFinal() = runTest {
        guides.setGuide(guide())
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onGuideGesture(100f, 0f, 1f, final = false)
        val atEdge = guides.state.value
        model.onGuideGesture(0f, 0f, 1f, final = true)
        runCurrent()

        assertEquals(listOf(GuideChange(atEdge, false), GuideChange(atEdge, true)), changes)
        assertEquals(atEdge, guides.state.value)
    }

    @Test
    fun finalStepAppliesItsRemainingMovementBeforePublishing() = runTest {
        guides.setGuide(guide())
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onGuideGesture(0.1f, -0.2f, 2f, final = true)
        runCurrent()

        val expected = GuideState(guideId = "guide", cx = 0.6f, cy = 0.3f, height = 1.4f)
        assertEquals(expected, guides.state.value)
        assertEquals(listOf(GuideChange(expected, true)), changes)
    }

    @Test
    fun invalidStepsLeaveStateAndChangeStreamUntouched() = runTest {
        guides.setGuide(guide())
        guides.update(final = true) { it.copy(cx = 0.3f, cy = 0.7f, height = 0.9f) }
        val before = guides.state.value
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            model.onGuideGesture(invalid, 0.1f, 1.1f, final = false)
            model.onGuideGesture(0.1f, invalid, 1.1f, final = true)
            model.onGuideGesture(0.1f, 0.1f, invalid, final = true)
        }
        model.onGuideGesture(0.1f, 0.1f, 0f, final = true)
        model.onGuideGesture(0.1f, 0.1f, -1f, final = true)
        runCurrent()

        assertEquals(before, guides.state.value)
        assertTrue(changes.isEmpty())
        assertTrue(camera.zoomRequests.isEmpty())
    }

    @Test
    fun gesturesWithoutAGuideDoNotCreateStateOrEvents() = runTest {
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onGuideGesture(0.1f, 0.2f, 2f, final = false)
        model.onGuideGesture(0f, 0f, 1f, final = true)
        runCurrent()

        assertNull(guides.guide.value)
        assertEquals(GuideState(), guides.state.value)
        assertTrue(changes.isEmpty())
        assertTrue(camera.zoomRequests.isEmpty())
    }

    @Test
    fun guideGesturesPreserveOtherPropertiesAndDoNotZoomTheCamera() {
        val guide = guide()
        guides.setGuide(guide)
        guides.update(final = true) { it.copy(opacity = 0.8f, style = GuideStyle.CUTOUT, visible = false) }
        val before = guides.state.value

        model.onGuideGesture(0.1f, -0.1f, 2f, final = true)

        assertEquals(before.copy(cx = 0.6f, cy = 0.4f, height = 1.4f), guides.state.value)
        assertSame(guide, guides.guide.value)
        assertEquals(1f, camera.zoom.value)
        assertTrue(camera.zoomRequests.isEmpty())
    }

    @Test
    fun opacityIsKeptBetweenTenAndNinetyPercentAndPublishesTheFinalStep() = runTest {
        guides.setGuide(guide())
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onOpacityChange(0.3f, final = false)
        model.onOpacityChange(0.02f, final = false)
        model.onOpacityChange(0.95f, final = true)
        runCurrent()

        assertEquals(listOf(0.3f, 0.1f, 0.9f), changes.map { it.state.opacity })
        assertEquals(listOf(false, false, true), changes.map { it.final })
        assertEquals(GuideState(guideId = "guide", opacity = 0.9f), guides.state.value)
    }

    @Test
    fun nonFiniteOpacityIsIgnored() = runTest {
        guides.setGuide(guide())
        model.onOpacityChange(0.3f, final = true)
        val before = guides.state.value
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            model.onOpacityChange(invalid, final = true)
        }
        runCurrent()

        assertEquals(before, guides.state.value)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun styleToggleSwitchesBothWaysAndKeepsPositionSizeAndOpacity() = runTest {
        guides.setGuide(guide())
        guides.update(final = true) { it.copy(cx = 0.3f, cy = 0.6f, height = 1.2f, opacity = 0.8f) }
        val placed = guides.state.value
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onStyleToggle()
        assertEquals(placed.copy(style = GuideStyle.CUTOUT), guides.state.value)
        model.onStyleToggle()
        runCurrent()

        assertEquals(placed, guides.state.value)
        assertEquals(listOf(true, true), changes.map { it.final })
    }

    @Test
    fun opacityAndStyleWithoutAGuideDoNothing() = runTest {
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        model.onOpacityChange(0.3f, final = true)
        model.onStyleToggle()
        runCurrent()

        assertEquals(GuideState(), guides.state.value)
        assertTrue(changes.isEmpty())
    }

    private fun guide(): ReferenceGuide {
        // JVM Android stubs cannot create bitmaps; this test only stores their references.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val bitmap = unsafeClass.getMethod(
            "allocateInstance",
            Class::class.java,
        ).invoke(unsafe, Bitmap::class.java) as Bitmap
        return ReferenceGuide("guide", bitmap, bitmap, 0.5f, GuideSource.GALLERY)
    }

    private class FakeCamera : CameraController {
        override val status = MutableStateFlow(CameraStatus.READY)
        override val capabilities = MutableStateFlow<CameraCapabilities?>(
            CameraCapabilities(1f, 4f, listOf(1f, 2f, 3f)),
        )
        override val zoom = MutableStateFlow(1f)
        val zoomRequests = mutableListOf<Float>()

        override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit
        override fun setZoom(ratio: Float) {
            zoomRequests += ratio
        }
        override suspend fun takePhoto(): Result<Uri> = error("Guide gestures must not capture photos")
        override suspend fun grabFrame(): Result<Bitmap> = error("Guide gestures must not capture frames")
        override fun setFrameSink(sink: FrameSink?) = Unit
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
