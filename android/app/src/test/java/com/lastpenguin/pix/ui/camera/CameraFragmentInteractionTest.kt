// AI-generated with Claude Code, 2026-10-23, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MutableLiveData
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/*
 * The Camera screen's interactions, beyond the render paths of CameraFragmentRenderTest (R&S 6.2, 6.3; FR-1.4,
 * FR-1.5, FR-1.7, FR-3.9, FR-4.1, FR-6.8, FR-7.1–7.4): the permission flow (denied, kept across a recreation, the
 * request's answer binding the camera; Open settings and Try again); the clicks that navigate (Add a guide, Shoot
 * together, Use this photo) and system back during the scene photo step; End session; the gallery button with and
 * without an app to open the photo; each capture failure's message; the subject's zoom, brightness, flash, and guide
 * moves as notices, and a lost connection; the zoom accessibility actions; the reference thumbnail's tap, drag,
 * and TalkBack moves; the sliders, a pinch, a tap to focus, and the ratio button; the preview's stream state;
 * *Save guide* (FR-3.8): a guide not saved, then saved, and a second tap.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFragmentInteractionTest {
    private var scenario: FragmentScenario<CameraFragment>? = null
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var eventTime = 1_000L

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(
        camera: FakeCameraController = FakeCameraController(),
        granted: Boolean = true,
    ): TestAppContainer {
        if (granted) shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer(camera = camera).install()
        scenario = launchFragmentInContainer<CameraFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentCameraBinding, CameraFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentCameraBinding.bind(it.requireView()), it) }
        return result!!
    }

    private fun generation(): GenerationViewModel = onScreen { _, fragment -> fragment.requireActivity().viewModel() }

    private fun cameraViewModel(): CameraViewModel = onScreen { _, fragment ->
        androidx.lifecycle.ViewModelProvider(
            fragment,
            com.lastpenguin.pix.ui.PixViewModels.Factory,
        )[CameraViewModel::class.java]
    }

    private fun nextIntent(): Intent? {
        var intent: Intent? = null
        scenario!!.onFragment { intent = shadowOf(it.requireActivity()).nextStartedActivity }
        return intent
    }

    /** Whether a view under [root] shows [text]. */
    private fun shows(root: View, text: String): Boolean = when (root) {
        is TextView -> root.text.toString() == text
        is ViewGroup -> (0 until root.childCount).any { shows(root.getChildAt(it), text) }
        else -> false
    }

    // ---- Permission ---------------------------------------------------------------

    @Test
    fun permissionDenied_isRememberedAcrossARecreation_andTheGrantBindsTheCamera() {
        launch(granted = false)
        scenario!!.recreate()
        idle()
        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.cameraMessagePanel.visibility) }

        // The person grants it in the system dialog; the request then answers at once with the permission held.
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        onScreen { controls, _ -> controls.requestPermissionButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertEquals(View.GONE, controls.cameraMessagePanel.visibility)
            assertTrue(controls.shutterButton.isEnabled)
        }
    }

    @Test
    fun theMessagePanelsButton_opensTheAppsSettingsWhenDenied_andTriesAgainWhenUnavailable() {
        launch(granted = false)

        onScreen { controls, _ -> controls.cameraActionButton.performClick() }
        val settings = nextIntent()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings?.action)
        assertEquals("package:${app.packageName}", settings?.data.toString())
        scenario!!.close()

        val camera = FakeCameraController().apply { setStatus(CameraStatus.UNAVAILABLE) }
        launch(camera)
        onScreen { controls, _ ->
            assertEquals(string(R.string.camera_unavailable), controls.cameraMessage.text.toString())
            assertEquals(string(R.string.try_again), controls.cameraActionButton.text.toString())
            controls.cameraActionButton.performClick()
        }
        idle()
        // Try again binds the camera again; no activity is started.
        assertTrue(generateSequence { nextIntent() }.none { it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS })
    }

    // ---- Navigation and the scene photo's back ---------------------------------------

    @Test
    fun addGuideAndShootTogether_navigate() {
        launch()
        val nav: TestNavHostController = scenario!!.attachNav(R.id.cameraFragment)

        onScreen { controls, _ -> controls.guideButton.performClick() }
        idle()
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)

        nav.setCurrentDestination(R.id.cameraFragment)
        onScreen { controls, _ -> controls.shootTogetherButton.performClick() }
        idle()
        assertEquals(R.id.friendsFragment, nav.currentDestination?.id)
    }

    @Test
    fun scenePhoto_backCancelsOrShootsAgain_andUseThisPhotoOpensGeneratePoses() {
        launch(FakeCameraController().apply { frame = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888) })
        val nav = scenario!!.attachNav(R.id.cameraFragment)
        val generation = generation()

        generation.beginScene()
        idle()
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)

        generation.beginScene()
        idle()
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        assertEquals(GenerationUiState.Phase.REVIEWING, generation.uiState.value.phase)
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        onScreen { controls, _ -> controls.usePhotoButton.performClick() }
        idle()
        assertEquals(R.id.describePoseFragment, nav.currentDestination?.id)
        assertEquals(GenerationUiState.Phase.DESCRIBING, generation.uiState.value.phase)
    }

    @Test
    fun aScenePhotoThatCannotBeTaken_saysSo() {
        launch(FakeCameraController())
        val generation = generation()

        generation.beginScene()
        idle()
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()

        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        onScreen { controls, _ ->
            assertEquals(View.VISIBLE, controls.captureStatus.visibility)
            assertEquals(string(R.string.scene_shot_failed), controls.captureStatus.text.toString())
        }
    }

    // ---- Session ---------------------------------------------------------------------

    @Test
    fun endSession_leaves_andTheSubjectsActionsAreNotices() {
        val camera = FakeCameraController(exposureRange = -4..4, hasFlash = true)
        val modules = launch(camera)
        modules.guides.setGuide(guide())
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        idle()

        modules.session.deliver(SessionMessage.ZoomSet(2f, final = true))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_zoom_notice, "Junhyeong", "2"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.ExposureSet(2, final = true))
        idle()
        onScreen { controls, _ ->
            val ev = ExposureScale.formatEv(2, 0.5f)
            assertEquals(
                string(R.string.remote_brightness_notice, "Junhyeong", ev),
                controls.noticeText.text.toString(),
            )
        }

        modules.session.deliver(SessionMessage.FlashSet(FlashMode.ON))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_on_notice, "Junhyeong"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.FlashSet(FlashMode.AUTO))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_auto_notice, "Junhyeong"), controls.noticeText.text.toString())
        }
        modules.session.deliver(SessionMessage.FlashSet(FlashMode.OFF))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_off_notice, "Junhyeong"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.GuideMove("guide-1", 0.5f, 0.5f, 0.8f, final = true))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_guide_move_notice, "Junhyeong"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.Leave(EndReason.CONNECTION_LOST))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_disconnected, "Junhyeong"), controls.noticeText.text.toString())
        }

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        idle()
        onScreen { controls, _ -> controls.endSessionButton.performClick() }
        idle()
        assertEquals(listOf(EndReason.LEFT), modules.session.leaves)
    }

    // ---- The gallery button and capture failures ------------------------------------------

    @Test
    fun galleryButton_opensTheLastPhoto_orSaysNoAppCan() {
        val uri = Uri.parse("content://media/external/images/7")
        launch(FakeCameraController().apply { photo = uri })

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        // The thumbnail is read off the main thread; let that run (it finds no provider and keeps the placeholder).
        Thread.sleep(300)
        idle()
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        val view = nextIntent()
        assertEquals(Intent.ACTION_VIEW, view?.action)
        assertEquals(uri, view?.data)

        shadowOf(app).checkActivities(true)
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        idle()
        onScreen { _, fragment ->
            assertTrue(shows(fragment.requireActivity().window.decorView, string(R.string.gallery_unavailable)))
        }
    }

    @Test
    fun eachCaptureFailure_hasItsOwnMessage() {
        val camera = FakeCameraController()
        launch(camera)
        val cases = listOf(
            SecurityException() to R.string.camera_permission_message,
            ImageCaptureException(ImageCapture.ERROR_FILE_IO, "full", null) to R.string.photo_storage_failed,
            ImageCaptureException(ImageCapture.ERROR_CAMERA_CLOSED, "closed", null) to R.string.photo_camera_closed,
        )

        for ((failure, message) in cases) {
            camera.photoFailure = failure
            onScreen { controls, _ -> controls.shutterButton.performClick() }
            idle()
            onScreen { controls, _ -> assertEquals(string(message), controls.captureStatus.text.toString()) }
        }
    }

    // ---- Zoom, sliders, focus, and the ratio -----------------------------------------------

    @Test
    fun zoomAccessibilityActions_zoomInAndOut_onlyWhileReady() {
        val camera = FakeCameraController()
        launch(camera)
        val forward = AccessibilityActionCompat.ACTION_SCROLL_FORWARD.id
        val backward = AccessibilityActionCompat.ACTION_SCROLL_BACKWARD.id

        onScreen { controls, _ ->
            assertTrue(ViewCompat.performAccessibilityAction(controls.zoomReadout, forward, null))
            assertEquals(1.1f, camera.requests.last(), 1e-4f)
            assertTrue(ViewCompat.performAccessibilityAction(controls.zoomReadout, backward, null))
            assertEquals(1f, camera.requests.last(), 1e-4f)
        }

        camera.setStatus(CameraStatus.STARTING)
        idle()
        onScreen { controls, _ ->
            assertFalse(ViewCompat.performAccessibilityAction(controls.zoomReadout, forward, null))
        }
    }

    @Test
    fun aPinchOnThePreview_zooms_andATapFocusesThere() {
        val camera = FakeCameraController(supportsFocusPoint = true)
        launch(camera)

        onScreen { controls, _ ->
            assertTrue(controls.previewView.width > 0)
            pinch(controls.previewView, from = 300f, to = 600f)
        }
        idle()
        assertTrue(camera.requests.isNotEmpty())
        assertTrue(camera.requests.last() > 1f)

        onScreen { controls, _ ->
            val preview = controls.previewView
            touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, preview.width / 2f, preview.height / 2f))
            touch(preview, MotionEvent.ACTION_UP, Pointer(1, preview.width / 2f, preview.height / 2f))
        }
        idle()
        assertEquals(1, camera.focusPoints.size)
        // The ring is placed on the tap; its animation may already have run to its end under Robolectric.
        onScreen { controls, _ ->
            val ring = controls.focusRing
            assertEquals(controls.previewView.width / 2f - ring.layoutParams.width / 2f, ring.translationX, 0.5f)
        }
    }

    @Test
    fun theSliders_sendOpacityAndBrightness_andTheRatioButtonCyclesTheRatio() {
        val camera = FakeCameraController(exposureRange = -4..4)
        val modules = launch(camera)
        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ ->
            controls.brightnessButton.performClick()
        }
        idle()
        onScreen { controls, _ ->
            slide(controls.opacitySlider, 0.8f)
            slide(controls.brightnessSlider, ExposureScale.sliderValue(2, -4..4))
        }
        idle()
        assertEquals(0.8f, modules.guides.state.value.opacity, 0.02f)
        assertEquals(2, camera.exposureRequests.last())

        onScreen { controls, _ -> controls.aspectButton.performClick() }
        idle()
        assertEquals(AspectRatio.R16_9, runBlocking { modules.settings.aspectRatio() })

        camera.state.value = camera.state.value.copy(aspect = AspectRatio.R1_1)
        idle()
        onScreen { controls, _ -> assertEquals(string(R.string.aspect_1_1), controls.aspectButton.text.toString()) }
    }

    // ---- Save guide --------------------------------------------------------------------

    @Test
    fun saveGuide_fillsTheBookmarkAndSaysWhereItIs_andASecondTapSaysItIsSaved() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.save_guide), controls.saveGuideButton.contentDescription.toString())
        }

        onScreen { controls, _ -> controls.saveGuideButton.performClick() }
        idle()

        assertEquals(listOf("guide-1"), modules.saved.saved.value.map { it.id })
        onScreen { controls, _ ->
            assertEquals(string(R.string.guide_saved), controls.saveGuideButton.contentDescription.toString())
            assertTrue(controls.noticeText.isShown)
            assertEquals(string(R.string.guide_saved_notice), controls.noticeText.text.toString())
        }

        onScreen { controls, _ -> controls.saveGuideButton.performClick() }
        idle()

        assertEquals(1, modules.saved.saved.value.size)
        onScreen { controls, _ ->
            assertEquals(string(R.string.guide_already_saved_notice), controls.noticeText.text.toString())
        }
    }

    // ---- The reference thumbnail ---------------------------------------------------------

    @Test
    fun referenceThumbnail_tapEnlarges_dragAndTalkBackMoveIt() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        val viewModel = cameraViewModel()

        onScreen { controls, _ -> controls.referenceThumbnail.performClick() }
        idle()
        assertTrue(viewModel.uiState.value.referenceExpanded)
        onScreen { controls, _ ->
            assertEquals(View.INVISIBLE, controls.hintText.visibility)
            val info = controls.referenceThumbnail.createAccessibilityNodeInfo()
            val labels = info.actionList.map { it.label?.toString() }
            assertTrue(labels.contains(string(R.string.shrink_reference)))
            assertTrue(labels.contains(string(R.string.move_reference_top_right)))
            assertFalse(labels.contains(string(R.string.move_reference_top_left)))
            assertTrue(
                ViewCompat.performAccessibilityAction(controls.referenceThumbnail, R.id.move_reference_top_right, null),
            )
        }
        idle()
        assertEquals(ReferenceCorner.TOP_RIGHT, viewModel.uiState.value.referenceCorner)

        onScreen { controls, _ ->
            val thumbnail = controls.referenceThumbnail
            val far = ViewConfiguration.get(thumbnail.context).scaledTouchSlop * 4f + 600f
            touch(thumbnail, MotionEvent.ACTION_DOWN, Pointer(1, 5f, 5f))
            touch(thumbnail, MotionEvent.ACTION_MOVE, Pointer(1, 5f, 5f + far))
            touch(thumbnail, MotionEvent.ACTION_UP, Pointer(1, 5f, 5f + far))
        }
        idle()
        assertEquals(ReferenceCorner.BOTTOM_RIGHT, viewModel.uiState.value.referenceCorner)
    }

    // ---- The preview's stream state ------------------------------------------------------

    @Test
    fun thePreviewStreaming_isMarkedOnce_andKeepsTheScreenRendering() {
        launch()

        onScreen { controls, _ ->
            val stream = streamState(controls.previewView)
            stream.value = PreviewView.StreamState.STREAMING
            stream.value = PreviewView.StreamState.IDLE
            stream.value = PreviewView.StreamState.STREAMING
        }
        idle()

        onScreen { controls, _ -> assertTrue(controls.shutterButton.isEnabled) }
    }

    /** PreviewView's stream state is set by its surface; without one, the test sets the LiveData behind it. */
    @Suppress("UNCHECKED_CAST")
    private fun streamState(preview: PreviewView): MutableLiveData<PreviewView.StreamState> {
        val field = PreviewView::class.java.getDeclaredField("mPreviewStreamStateLiveData")
        field.isAccessible = true
        return field.get(preview) as MutableLiveData<PreviewView.StreamState>
    }

    // ---- Touch helpers ---------------------------------------------------------------------

    private data class Pointer(val id: Int, val x: Float, val y: Float)

    private fun slide(slider: com.google.android.material.slider.Slider, value: Float) {
        assertTrue(slider.trackWidth > 0)
        val fraction = (value - slider.valueFrom) / (slider.valueTo - slider.valueFrom)
        val x = slider.trackSidePadding + fraction * slider.trackWidth
        touch(slider, MotionEvent.ACTION_DOWN, Pointer(1, x, slider.height / 2f))
        touch(slider, MotionEvent.ACTION_MOVE, Pointer(1, x, slider.height / 2f))
        touch(slider, MotionEvent.ACTION_UP, Pointer(1, x, slider.height / 2f))
    }

    /** A horizontal pinch from [from] apart to [to] apart, past the detector's slop so that it begins. */
    private fun pinch(view: View, from: Float, to: Float) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop * 2f + 2f
        touch(view, MotionEvent.ACTION_DOWN, Pointer(7, 100f, 200f))
        touch(
            view,
            MotionEvent.ACTION_POINTER_DOWN,
            Pointer(7, 100f, 200f),
            Pointer(31, 100f + from, 200f),
            actionIndex = 1,
        )
        touch(view, MotionEvent.ACTION_MOVE, Pointer(7, 100f, 200f), Pointer(31, 100f + from + slop, 200f))
        val spread = 100f + (from + slop) * (to / from)
        touch(view, MotionEvent.ACTION_MOVE, Pointer(7, 100f, 200f), Pointer(31, spread, 200f))
        touch(view, MotionEvent.ACTION_POINTER_UP, Pointer(7, 100f, 200f), Pointer(31, spread, 200f), actionIndex = 1)
        touch(view, MotionEvent.ACTION_UP, Pointer(7, 100f, 200f))
    }

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
            0,
            0,
        )
        try {
            return view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
