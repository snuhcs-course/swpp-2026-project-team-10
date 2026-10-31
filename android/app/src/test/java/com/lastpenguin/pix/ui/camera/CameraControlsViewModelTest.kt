// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.CameraSettings
import com.lastpenguin.pix.camera.CameraState
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.guide.GuideChange
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.InMemoryGuideRepository
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Partitions.
 * Flash: a phone with a flash (each of off, auto, on is followed by the next) and without one (the tap is ignored).
 * Aspect ratio: each ratio is followed by the next and saved; a tap while a photo is saved is ignored; a saved ratio
 * is restored at start, and none leaves the default; a ratio change re-clamps a guide and sends a final change.
 * Exposure: an index inside the range, beyond each end (clamped), and a camera without compensation (ignored); the
 * brightness slider opens only with compensation.
 * Focus: a tap inside the frame on a ready camera that supports it; a camera that is not ready, a camera without a
 * focus point, and a point outside the frame are ignored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraControlsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val camera = FakeCamera()
    private val settings = CameraSettings.InMemory()
    private val guides = InMemoryGuideRepository { camera.state.value.aspect.frameAspect }
    private lateinit var model: CameraViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun start() {
        model = CameraViewModel(camera, guides, settings)
        store.put("camera", model)
    }

    @Test
    fun onFlashTap_phoneWithFlash_cyclesOffAutoOn() = runTest(dispatcher) {
        start()
        runCurrent()

        repeat(3) {
            model.onFlashTap()
            runCurrent()
        }

        assertEquals(listOf(FlashMode.AUTO, FlashMode.ON, FlashMode.OFF), camera.flashRequests)
        assertEquals(FlashMode.OFF, model.uiState.value.flash)
    }

    @Test
    fun onFlashTap_phoneWithoutFlash_isIgnored() = runTest(dispatcher) {
        camera.capabilities.value = capabilities(hasFlash = false)
        start()
        runCurrent()

        model.onFlashTap()

        assertTrue(camera.flashRequests.isEmpty())
        assertFalse(model.uiState.value.hasFlash)
    }

    @Test
    fun onAspectTap_eachRatio_movesToTheNextAndSavesIt() = runTest(dispatcher) {
        start()
        runCurrent()

        repeat(3) {
            model.onAspectTap()
            runCurrent()
        }

        assertEquals(listOf(AspectRatio.R16_9, AspectRatio.R1_1, AspectRatio.R4_3), camera.aspectRequests)
        assertEquals(AspectRatio.R4_3, settings.aspectRatio())
    }

    @Test
    fun onAspectTap_whileAPhotoIsSaved_isIgnored() = runTest(dispatcher) {
        start()
        runCurrent()
        model.onShutter()

        model.onAspectTap()
        runCurrent()

        assertTrue(camera.aspectRequests.isEmpty())
    }

    @Test
    fun start_savedRatio_isRestored() = runTest(dispatcher) {
        settings.saveAspectRatio(AspectRatio.R1_1)
        start()
        runCurrent()

        assertEquals(listOf(AspectRatio.R1_1), camera.aspectRequests)
        assertEquals(AspectRatio.R1_1, model.uiState.value.aspect)
    }

    @Test
    fun start_noSavedRatio_keepsTheDefault() = runTest(dispatcher) {
        start()
        runCurrent()

        assertTrue(camera.aspectRequests.isEmpty())
        assertEquals(AspectRatio.R4_3, model.uiState.value.aspect)
    }

    @Test
    fun ratioChange_guideOnTheCamera_isClampedToTheNewFrameWithAFinalChange() = runTest(dispatcher) {
        start()
        guides.setGuide(guide())
        guides.update(final = true) { it.copy(cx = -10f) }
        runCurrent()
        val changes = mutableListOf<GuideChange>()
        backgroundScope.launch { guides.changes.collect { changes += it } }
        runCurrent()

        // 4:3 → 16:9: the guide is a wider part of the narrower frame, so -.14 stays allowed.
        model.onAspectTap()
        runCurrent()
        assertEquals(-0.14f, guides.state.value.cx, EPSILON)

        // 16:9 → 1:1: height .7 at aspect .5 is only .35 of the frame's width, so the center moves in to -.105.
        model.onAspectTap()
        runCurrent()

        assertEquals(-0.105f, guides.state.value.cx, EPSILON)
        assertEquals(0.7f, guides.state.value.height, EPSILON)
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.final })
    }

    @Test
    fun onExposureChange_indexOutsideTheRange_isClampedToTheNearestEnd() = runTest(dispatcher) {
        start()
        runCurrent()

        model.onExposureChange(3)
        model.onExposureChange(-40)
        model.onExposureChange(40)

        assertEquals(listOf(3, -12, 12), camera.exposureRequests)
    }

    @Test
    fun onExposureChange_cameraWithoutCompensation_isIgnoredAndTheSliderStaysClosed() = runTest(dispatcher) {
        camera.capabilities.value = capabilities(exposureRange = 0..0)
        start()
        runCurrent()

        model.onExposureChange(2)
        model.setBrightnessOpen(true)

        assertTrue(camera.exposureRequests.isEmpty())
        assertFalse(model.uiState.value.brightnessOpen)
    }

    @Test
    fun setBrightnessOpen_cameraWithCompensation_opensAndCloses() = runTest(dispatcher) {
        start()
        runCurrent()

        model.setBrightnessOpen(true)
        assertTrue(model.uiState.value.brightnessOpen)
        model.setBrightnessOpen(false)
        assertFalse(model.uiState.value.brightnessOpen)
    }

    @Test
    fun onFocusTap_insideTheFrameOnAReadyCamera_focusesThere() = runTest(dispatcher) {
        start()
        runCurrent()

        model.onFocusTap(0.25f, 0.75f)

        assertEquals(listOf(0.25f to 0.75f), camera.focusRequests)
    }

    @Test
    fun onFocusTap_cameraNotReadyOrWithoutFocusPointOrOutsideTheFrame_isIgnored() = runTest(dispatcher) {
        start()
        runCurrent()
        model.onFocusTap(1.2f, 0.5f)
        model.onFocusTap(Float.NaN, 0.5f)

        camera.status.value = CameraStatus.STARTING
        runCurrent()
        model.onFocusTap(0.5f, 0.5f)

        camera.status.value = CameraStatus.READY
        camera.capabilities.value = capabilities(supportsFocusPoint = false)
        runCurrent()
        model.onFocusTap(0.5f, 0.5f)

        assertTrue(camera.focusRequests.isEmpty())
    }

    private fun capabilities(
        hasFlash: Boolean = true,
        exposureRange: IntRange = -12..12,
        supportsFocusPoint: Boolean = true,
    ) = CameraCapabilities(
        minZoom = 1f,
        maxZoom = 4f,
        zoomStops = listOf(1f, 2f),
        exposureRange = exposureRange,
        exposureStep = 1f / 6f,
        hasFlash = hasFlash,
        aspectRatios = AspectRatio.entries,
        supportsFocusPoint = supportsFocusPoint,
    )

    private fun guide(): ReferenceGuide {
        // Android's JVM stubs cannot create a Bitmap; the repository and this screen only keep the reference.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val bitmap = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, Bitmap::class.java) as Bitmap
        return ReferenceGuide("tall", bitmap, bitmap, 0.5f, GuideSource.GALLERY)
    }

    /** Applies flash and aspect ratio at once, as the camera publishes them in its state; records every request. */
    private inner class FakeCamera : CameraController {
        override val status = MutableStateFlow(CameraStatus.READY)
        override val capabilities = MutableStateFlow<CameraCapabilities?>(capabilities())
        override val state = MutableStateFlow(CameraState(zoom = 1f))
        override val zoom = MutableStateFlow(1f)
        val flashRequests = mutableListOf<FlashMode>()
        val aspectRequests = mutableListOf<AspectRatio>()
        val exposureRequests = mutableListOf<Int>()
        val focusRequests = mutableListOf<Pair<Float, Float>>()

        override fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) = Unit
        override fun setZoom(ratio: Float, final: Boolean) = Unit
        override fun setFocusPoint(x: Float, y: Float) {
            focusRequests += x to y
        }
        override fun setExposure(index: Int) {
            exposureRequests += index
        }
        override fun setFlash(mode: FlashMode) {
            flashRequests += mode
            state.update { it.copy(flash = mode) }
        }
        override fun setTorch(on: Boolean) = Unit
        override fun setAspectRatio(ratio: AspectRatio) {
            aspectRequests += ratio
            state.update { it.copy(aspect = ratio) }
        }

        // Never completes, so a shutter press keeps the screen saving.
        override suspend fun takePhoto(): Result<Uri> = awaitCancellation()
        override suspend fun grabFrame(): Result<Bitmap> = Result.failure(UnsupportedOperationException())
        override fun setFrameSink(sink: FrameSink?) = Unit
        override val facing: StateFlow<CameraFacing> = MutableStateFlow(CameraFacing.BACK)
        override val hasFrontCamera: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setFacing(facing: CameraFacing) = Unit
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
