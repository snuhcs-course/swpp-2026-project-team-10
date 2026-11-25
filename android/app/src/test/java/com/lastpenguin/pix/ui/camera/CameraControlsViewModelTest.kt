// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park, Sungmin Jo, Dongje Park, and Joonhyung Han
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
import com.lastpenguin.pix.session.PhotographerAction
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
 * Aspect ratio (chosen from the ratios under the top bar since Iter3-A12; before, each tap moved to the next): each
 * ratio chosen goes to the camera, is saved, and closes the ratios; the ratio the camera has only closes them; a choice
 * while a photo is saved is ignored, and the shutter closes the ratios; a saved ratio is restored at start, and none
 * leaves the default; a ratio change re-clamps a guide and sends a final change, and closes an open brightness bar.
 * Exposure: an index inside the range, beyond each end (clamped), and a camera without compensation (ignored); the
 * brightness slider opens only with compensation.
 * Focus: a tap inside the frame on a ready camera that supports it; a camera that is not ready, a camera without a
 * focus point, and a point outside the frame are ignored.
 * What the photographer did, told for the subject's phone (#292): a pinch (nothing at its steps, its last value
 * clamped to the camera's range) and a quick step (its step); a drag for the brightness (nothing at its steps, its end
 * clamped) and each tap on the flash; not the base brightness a tap to focus puts back; a gesture on the guide that
 * moved it (once, at its end) and one that did not (nothing); a camera without a flash or without brightness
 * (nothing).
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
    fun onAspectChosen_eachRatio_goesToTheCameraIsSaved_andClosesTheRatios() = runTest(dispatcher) {
        start()
        runCurrent()

        for (aspect in listOf(AspectRatio.R1_1, AspectRatio.R16_9, AspectRatio.R4_3)) {
            model.setAspectsOpen(true)
            model.onAspectChosen(aspect)
            runCurrent()
            assertFalse(model.uiState.value.aspectsOpen)
        }

        assertEquals(listOf(AspectRatio.R1_1, AspectRatio.R16_9, AspectRatio.R4_3), camera.aspectRequests)
        assertEquals(AspectRatio.R4_3, settings.aspectRatio())
    }

    @Test
    fun onAspectChosen_theRatioTheCameraHas_onlyClosesTheRatios() = runTest(dispatcher) {
        start()
        runCurrent()
        model.setAspectsOpen(true)

        model.onAspectChosen(AspectRatio.R4_3)
        runCurrent()

        assertTrue(camera.aspectRequests.isEmpty())
        assertFalse(model.uiState.value.aspectsOpen)
    }

    @Test
    fun onAspectChosen_whileAPhotoIsSaved_isIgnored_andTheShutterClosesTheRatios() = runTest(dispatcher) {
        start()
        runCurrent()
        model.setAspectsOpen(true)
        model.onShutter()
        assertFalse(model.uiState.value.aspectsOpen)

        model.onAspectChosen(AspectRatio.R16_9)
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
        model.onAspectChosen(AspectRatio.R16_9)
        runCurrent()
        assertEquals(-0.14f, guides.state.value.cx, EPSILON)

        // 16:9 → 1:1: height .7 at aspect .5 is only .35 of the frame's width, so the center moves in to -.105.
        model.onAspectChosen(AspectRatio.R1_1)
        runCurrent()

        assertEquals(-0.105f, guides.state.value.cx, EPSILON)
        assertEquals(0.7f, guides.state.value.height, EPSILON)
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.final })
    }

    @Test
    fun ratioChange_withTheBrightnessBarOpen_closesIt() = runTest(dispatcher) {
        start()
        runCurrent()
        model.setBrightnessOpen(true)

        // The rebind drops the focus point the bar stands beside.
        model.onAspectChosen(AspectRatio.R1_1)
        runCurrent()

        assertFalse(model.uiState.value.brightnessOpen)
    }

    @Test
    fun onExposureChange_indexOutsideTheRange_isClampedToTheNearestEnd() = runTest(dispatcher) {
        start()
        runCurrent()

        model.onExposureChange(3)
        model.onExposureChange(-40)
        model.onExposureChange(40)

        // The first is the base brightness (0 EV), set once the range is known.
        assertEquals(listOf(0, 3, -12, 12), camera.exposureRequests)
    }

    @Test
    fun theBaseBrightness_0Ev_isSetWhenTheRangeIsKnown_atEachTapToFocus_andForANewRange() = runTest(dispatcher) {
        model = CameraViewModel(camera, guides, settings)
        store.put("camera", model)
        runCurrent()
        assertEquals(listOf(0), camera.exposureRequests)

        // A drag changes it, and a tap to focus takes that away again.
        model.onExposureChange(9)
        model.onFocusTap(0.5f, 0.5f)
        assertEquals(listOf(0, 9, 0), camera.exposureRequests)

        // Another camera's range, without 0: the base again, clamped to it.
        camera.capabilities.value = capabilities(exposureRange = 2..8)
        runCurrent()
        assertEquals(2, camera.exposureRequests.last())
    }

    @Test
    fun aTapOnACameraThatCannotFocusOnAPoint_stillTakesTheBrightnessBackToTheBase() = runTest(dispatcher) {
        camera.capabilities.value = capabilities(supportsFocusPoint = false)
        model = CameraViewModel(camera, guides, settings)
        store.put("camera", model)
        runCurrent()
        model.onExposureChange(8)

        model.onFocusTap(0.5f, 0.5f)

        assertTrue(camera.focusRequests.isEmpty())
        assertEquals(listOf(0, 8, 0), camera.exposureRequests)
    }

    @Test
    fun theBaseBrightness_withoutExposureCompensation_setsNothing() = runTest(dispatcher) {
        camera.capabilities.value = capabilities(exposureRange = 0..0)
        model = CameraViewModel(camera, guides, settings)
        store.put("camera", model)
        runCurrent()
        model.onFocusTap(0.5f, 0.5f)

        assertTrue(camera.exposureRequests.isEmpty())
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
    fun onFocusTap_tellsWhereItFocused_onlyWhenTheCameraTookThePoint() = runTest(dispatcher) {
        val told = mutableListOf<Pair<Float, Float>>()
        model = CameraViewModel(camera, guides, settings, onFocused = { x, y -> told += x to y })
        store.put("camera", model)
        runCurrent()

        model.onFocusTap(0.25f, 0.75f)
        assertEquals(listOf(0.25f to 0.75f), told)

        // Outside the frame, and on a camera that cannot focus on a point: nothing focused, so nothing told.
        model.onFocusTap(1.2f, 0.5f)
        camera.capabilities.value = capabilities(supportsFocusPoint = false)
        runCurrent()
        model.onFocusTap(0.5f, 0.5f)
        assertEquals(1, told.size)
    }

    private fun startTelling(): MutableList<PhotographerAction> {
        val did = mutableListOf<PhotographerAction>()
        model = CameraViewModel(camera, guides, settings, onDid = { did += it })
        store.put("camera", model)
        return did
    }

    @Test
    fun aPinch_isToldOnce_atItsLastValue_andAQuickStepAtItsStep() = runTest(dispatcher) {
        val did = startTelling()
        runCurrent()

        model.onZoomChanged(1.4f, final = false)
        model.onZoomChanged(1.8f, final = false)
        assertTrue(did.isEmpty())
        // Its last value, as the camera can show it.
        model.onZoomChanged(9f, final = true)
        assertEquals(listOf<PhotographerAction>(PhotographerAction.Zoom(4f)), did)

        did.clear()
        model.onZoomTap()
        assertEquals(listOf<PhotographerAction>(PhotographerAction.Zoom(2f)), did)
    }

    @Test
    fun aDragForTheBrightness_isToldOnce_atItsEnd_andTheFlashAtEachTap() = runTest(dispatcher) {
        val did = startTelling()
        runCurrent()

        model.onExposureChange(3, final = false)
        model.onExposureChange(5, final = false)
        assertTrue(did.isEmpty())
        model.onExposureChange(40, final = true)
        assertEquals(listOf<PhotographerAction>(PhotographerAction.Brightness(12)), did)

        did.clear()
        model.onFlashTap()
        assertEquals(listOf<PhotographerAction>(PhotographerAction.Flash(FlashMode.AUTO)), did)
    }

    @Test
    fun theBaseBrightnessAtATapToFocus_isNotTold_asABrightnessTheUserSet() = runTest(dispatcher) {
        val did = startTelling()
        runCurrent()

        model.onFocusTap(0.5f, 0.5f)

        assertTrue(did.none { it is PhotographerAction.Brightness })
    }

    @Test
    fun aGestureThatMovedTheGuide_isToldOnce_atItsEnd_andOneThatDidNotIsNot() = runTest(dispatcher) {
        val did = startTelling()
        runCurrent()
        guides.setGuide(guide())
        runCurrent()

        model.onGuideGesture(0.01f, 0f, 1f, final = false)
        model.onGuideGesture(0.01f, 0f, 1f, final = false)
        assertTrue(did.isEmpty())
        model.onGuideGesture(0f, 0f, 1f, final = true)
        assertEquals(listOf<PhotographerAction>(PhotographerAction.GuideMove), did)

        // A touch on the guide that moved nothing.
        did.clear()
        model.onGuideGesture(0f, 0f, 1f, final = true)
        assertTrue(did.isEmpty())
    }

    @Test
    fun aCameraWithoutAFlash_orWithoutBrightness_tellsNothingForThem() = runTest(dispatcher) {
        val did = startTelling()
        camera.capabilities.value = capabilities(hasFlash = false, exposureRange = 0..0)
        runCurrent()

        model.onFlashTap()
        model.onExposureChange(2, final = true)

        assertTrue(did.isEmpty())
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
        override suspend fun takePhoto(onCaptured: () -> Unit): Result<Uri> = awaitCancellation()
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
