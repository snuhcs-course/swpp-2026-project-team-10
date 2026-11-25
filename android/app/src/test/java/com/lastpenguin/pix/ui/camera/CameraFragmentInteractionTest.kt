// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MutableLiveData
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.PhoneAlbum
import com.lastpenguin.pix.camera.PhotoAccess
import com.lastpenguin.pix.camera.photoPermissions
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.IcePath
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.RtcSessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.session.shoot
import com.lastpenguin.pix.ui.ScreenTestSupport
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNotice
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationUiState
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.generation.SceneHandOff
import com.lastpenguin.pix.ui.session.SessionViewModel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog

/*
 * The Camera screen's interactions, beyond the render paths of CameraFragmentRenderTest (R&S 6.2, 6.3; FR-1.4, FR-1.5,
 * FR-1.7, FR-3.9, FR-4.1, FR-6.8, FR-7.1–7.4): the permission flow (denied, kept across a recreation, the request's
 * answer binding the camera; Open settings and Try again); the clicks that navigate (Add a guide, Shoot together, Use
 * this photo, which opens nothing for a photo without a person) and system back during the scene photo step; End
 * session, which asks first (#298): confirmed, cancelled, and the question gone once the subject left; the gallery button: outside a session the album's newest, which opens Pix's album in Pix's viewer as a
 * grid of every photo, the newest first, where a tap opens that photo's page, then a swipe to an older one and a tap
 * on the strip of thumbnails, and system Back to the grid, with the photo on screen in it, and again to the camera,
 * and a photo just taken there at once; a photo deleted there, once asked, leaves it, the grid, and the thumbnail, and
 * one that cannot be deleted stays with a notice; the tabs: Photos at each opening, Albums listing the phone's albums,
 * each its newest photo, its name, and its count, and one tapped opening its grid under its name, from which a photo
 * opens its page, and Back goes back one step at a time to the camera; the grid's squares read at their own size; another app's photo, not asked about by Pix, goes to Android's
 * question, whose yes takes it away; while Pix may read only its own photos, the thumbnail, also with none, asks for
 * the phone's, and the answer opens the grid on them; the grid's line says which photos show, and offers the phone's
 * settings once Android would ask no more, or Android's question again for the chosen ones; in a session as the
 * photographer none until the session's first photo, then the session's photos in Pix's viewer, which system Back and
 * the session's end close, and Pix's album again after it (FR-1.6, Iter4-A2); each capture failure's message; the subject's
 * zoom, brightness, flash, and guide moves as notices, a lost connection, a photo not sent (FR-6.14), also after the
 * notice that the subject left, which it waits for (#238), and the live view
 * on mobile data (FR-6.13); the zoom accessibility actions and a tap on the readout through the quick steps (0.6×, 1×,
 * 2×, back to 0.6×), by touch and by TalkBack; the reference thumbnail's tap, drag, and TalkBack moves, and hiding it
 * at the side (#237: a drag mostly past the side, its strip with the handle over it, a tap on the handle, TalkBack's
 * *Hide at the side* and its tap on a hidden one, another guide; hidden while enlarged, the small strip, and small when
 * it is back); the sliders, a pinch, a tap to focus, and the ratio button with its ratios (opened by the button, closed
 * by a choice, the button, a touch elsewhere, and Back; the ratio the camera has changing nothing); the preview's
 * stream state; *Save guide* (FR-3.8): a guide not saved (an empty ♥), then saved (filled red), and a second tap that
 * empties it; its icon kept
 * while other state changes. Design 2.8's controls: the brightness bar beside the focus box after a tap to focus (its
 * place, a drag up and down, kept up while held, gone after the idle time), on the right and, near the image's right
 * edge, on the left; the focus box staying while the bar is up, held or not, both whole until the idle time and both
 * gone after it, or, on a camera without brightness, fading by itself; the toggle label for Hide / Show and a zoom step, replaced at once and gone after its time; the
 * pinch mode's label over the guide-edit toggle, Guide then Camera, since a new guide starts with the camera's (#304). A tap on the readout while the subject's pinch holds
 * the zoom: no zoom, and the label names the subject (FR-7.6, #236).
 * A photo taken with the phone held sideways, saved as held (FR-1.5, #206).
 * The subject's tap to focus (FR-7.7): the focus box where they tapped, alone, with its notice, which goes sooner than
 * the others; a tap soon after, the box without the notice; with this phone's own brightness bar up, which goes, or
 * held under a finger, where nothing moves.
 * The subject's pinch (#292): who is zooming from its first step, for as long as it, replaced by the ratio's notice;
 * taken back, the line goes after a little; another notice meanwhile keeps its own time, and the line is back after it.
 * A dropped link (#293, FR-6.12): who the phone reconnects with, for the whole wait, gone when the link is back;
 * replaced by "… disconnected" when the wait ends, and not back after it; another notice meanwhile keeps its own time,
 * and the line is back after it; a drop during the subject's pinch, where the pinch's end must not take the line down.
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
        // Who takes the photo? first (#188; until then, Shoot together opened Friends).
        onScreen { controls, _ -> controls.shootTogetherButton.performClick() }
        idle()
        assertEquals(R.id.shootTogetherSheet, nav.currentDestination?.id)
    }

    @Test
    fun scenePhoto_backCancelsOrShootsAgain_andAPhotoWithAPersonOpensGeneratePosesByItself() {
        val modules = launch(
            FakeCameraController().apply {
                frame =
                    Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            },
        )
        val nav = scenario!!.attachNav(R.id.cameraFragment)
        val generation = generation()

        generation.beginScene()
        idle()
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(GenerationUiState.Phase.IDLE, generation.uiState.value.phase)

        // Back while the photo is checked: the camera again, and nothing opens.
        generation.beginScene()
        idle()
        modules.persons.gate = CompletableDeferred()
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        assertEquals(GenerationUiState.Phase.CHECKING, generation.uiState.value.phase)
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)

        // One press of the shutter, with a person in the photo: Generate poses (#296).
        modules.persons.gate = null
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        assertEquals(R.id.describePoseFragment, nav.currentDestination?.id)
        assertEquals(GenerationUiState.Phase.DESCRIBING, generation.uiState.value.phase)
        // The step's look handed to Generate poses, which starts as it and unfolds from it (#210): Back's arrow, the
        // title, the flow's bar, and the held photo, where the camera has them.
        val handOff = checkNotNull(SceneHandOff.take())
        onScreen { controls, _ ->
            fun View.window() = IntArray(2).also(::getLocationInWindow)
            val back = controls.sceneBackButton as MaterialButton
            assertEquals(back.iconSize.toFloat(), handOff.backIcon, 0f)
            assertEquals(back.window()[0] + back.width / 2f, handOff.back.x, 0.5f)
            assertEquals(controls.sceneTitle.textSize, handOff.titleSize, 0f)
            assertEquals(controls.sceneTitle.window()[0].toFloat(), handOff.titleBox.left, 0f)
            assertEquals(controls.sceneTitle.width, handOff.title.width)
            assertEquals(controls.sceneProgress.window()[1].toFloat(), handOff.progressTop, 0f)
            // The photo's own frame, 3:4, as the view fits it: as high as the view, in its middle.
            val still = controls.sceneStill.window()
            val photo = handOff.photo
            assertEquals(still[1], photo.top)
            assertEquals(still[1] + controls.sceneStill.height, photo.bottom)
            assertEquals(still[0] + controls.sceneStill.width / 2f, photo.exactCenterX(), 1f)
            assertEquals(3f / 4f, photo.width().toFloat() / photo.height(), 0.01f)
        }
    }

    @Test
    fun scenePhoto_withoutAPerson_staysOnCamera_readyToShootAgain() {
        val modules = launch(
            FakeCameraController().apply {
                frame =
                    Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            },
        )
        modules.persons.found = false
        val nav = scenario!!.attachNav(R.id.cameraFragment)
        val generation = generation()

        generation.beginScene()
        idle()
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()

        // Nothing opens and nothing is sent (FR-4.13); the shutter is there for another photo.
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        assertEquals(GenerationUiState.Phase.FRAMING, generation.uiState.value.phase)
        assertTrue(generation.uiState.value.noPerson)
        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.captureControls.visibility) }
        assertTrue(modules.generator.runs.isEmpty())
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
    fun aPhotoThatIsNotSent_isANotice() {
        val modules = launch()
        onScreen { _, fragment -> fragment.requireActivity().viewModel<SessionViewModel>().startRoom() }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()

        modules.photos.shoot(Uri.parse("content://media/external/images/media/1"), takenAt = 1)
        idle()

        onScreen { controls, _ ->
            assertEquals(string(R.string.session_photo_not_sent, "Joonhyung"), controls.noticeText.text.toString())
        }
    }

    @Test
    fun aPhotoTakenWithThePhoneSideways_isSavedAsHeld() {
        val modules =
            launch(FakeCameraController().apply { photo = Uri.parse("content://media/external/images/media/1") })
        modules.turns.set(90)

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()

        assertEquals(listOf(90), modules.camera.photoTurns)
    }

    @Test
    fun twoPhotosNotSent_eachGetTheirFullTime() {
        val modules = launch()
        onScreen { _, fragment -> fragment.requireActivity().viewModel<SessionViewModel>().startRoom() }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        val photo = Uri.parse("content://media/external/images/media/1")

        modules.photos.shoot(photo, takenAt = 1)
        modules.photos.shoot(photo, takenAt = 2)
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS + 100, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertTrue(controls.noticeText.isVisible) }

        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS + 100, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertFalse(controls.noticeText.isVisible) }
    }

    @Test
    fun aPhotoNotSent_waitsForTheNoticeThatTheSubjectLeft() {
        val modules = launch()
        onScreen { _, fragment -> fragment.requireActivity().viewModel<SessionViewModel>().startRoom() }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()

        modules.session.deliver(SessionMessage.Leave(EndReason.PEER_LEFT))
        idle()
        modules.photos.shoot(Uri.parse("content://media/external/images/media/1"), takenAt = 1)
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS + 100, TimeUnit.MILLISECONDS)

        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_left, "Joonhyung"), controls.noticeText.text.toString())
        }

        shadowOf(Looper.getMainLooper())
            .idleFor(CameraViewModel.PEER_GONE_NOTICE_MS - CameraViewModel.NOTICE_MS, TimeUnit.MILLISECONDS)

        onScreen { controls, _ ->
            assertTrue(controls.noticeText.isVisible)
            assertEquals(string(R.string.session_photo_not_sent, "Joonhyung"), controls.noticeText.text.toString())
        }
    }

    @Test
    fun liveViewOnMobileData_isANotice() {
        val modules = launch()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()

        modules.session.setPath(IcePath("srflx", "relay", "cellular", null))
        idle()

        onScreen { controls, _ ->
            assertEquals(string(R.string.mobile_data_notice), controls.noticeText.text.toString())
        }
        assertTrue(runBlocking { modules.mobileData.wasShown() })
    }

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test
    fun theSubjectsPinch_saysWhoIsZooming_fromItsFirstStep_untilTheRatiosNoticeTakesItsPlace() {
        val modules = launch(FakeCameraController())
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        // The notice of who watches goes after its time.
        idleFor(CameraViewModel.NOTICE_MS)
        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }

        modules.session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.zoom_held_label, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }

        // It stays as long as the pinch does, beyond a notice's own time.
        repeat(4) {
            idleFor(1_000)
            modules.session.deliver(SessionMessage.ZoomSet(1.6f + it * 0.1f, final = false))
            idle()
        }
        onScreen { controls, _ ->
            assertEquals(string(R.string.zoom_held_label, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }

        modules.session.deliver(SessionMessage.ZoomSet(2f, final = true))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_zoom_notice, "Joonhyung", "2"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }
        // The ratio's notice keeps its own time.
        idleFor(CameraViewModel.NOTICE_MS - 100)
        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.noticeText.visibility) }
        idleFor(200)
        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    @Test
    fun anotherNoticeDuringThePinch_keepsItsOwnTime_andThenWhoIsZoomingIsBack() {
        val modules = launch(FakeCameraController(hasFlash = true))
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        idleFor(CameraViewModel.NOTICE_MS)
        modules.session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        idle()

        // Another notice of the session's, as the last pinch's ratio that came late.
        modules.session.deliver(SessionMessage.FlashSet(FlashMode.ON))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_on_notice, "Joonhyung"), controls.noticeText.text.toString())
        }
        // The pinch goes on meanwhile.
        repeat(3) {
            idleFor(1_000)
            modules.session.deliver(SessionMessage.ZoomSet(1.6f + it * 0.1f, final = false))
            idle()
        }
        idleFor(200)

        onScreen { controls, _ ->
            assertEquals(string(R.string.zoom_held_label, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }
        // And it still goes when the pinch is taken back.
        modules.session.deliver(SessionMessage.ZoomSet(1.9f, final = true, cancel = true))
        idle()
        idleFor(SessionViewModel.ZOOMING_LINGER_MS + 100)
        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    @Test
    fun aPinchTakenBack_takesItsLineDown_afterALittle() {
        val modules = launch(FakeCameraController())
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        idleFor(CameraViewModel.NOTICE_MS)
        modules.session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        idle()

        modules.session.deliver(SessionMessage.ZoomSet(1.5f, final = true, cancel = true))
        idle()
        onScreen { controls, _ -> assertEquals(View.VISIBLE, controls.noticeText.visibility) }
        idleFor(SessionViewModel.ZOOMING_LINGER_MS + 100)

        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    /** The photographer's drop as the session says it: with the subject's name and this phone's role (#293). */
    private val dropped =
        SessionState.Reconnecting("s1", 0L, peer = PeerInfo("Joonhyung"), role = Role.PHOTOGRAPHER)

    @Test
    fun aDroppedLink_saysWhoThePhoneReconnectsWith_forTheWholeWait_untilTheLinkIsBack() {
        val modules = launch(FakeCameraController())
        val connected = SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)
        modules.session.setState(connected)
        idle()
        idleFor(CameraViewModel.NOTICE_MS)

        modules.session.setState(dropped)
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_reconnecting, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }
        // Far beyond a notice's own time: it stays as long as the session tries.
        idleFor(RtcSessionManager.RECONNECT_BUDGET_MS - 1_000)
        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_reconnecting, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }

        modules.session.setState(connected)
        idle()
        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    @Test
    fun aLinkThatStaysDown_saysDisconnected_inPlaceOfTheReconnectingLine() {
        val modules = launch(FakeCameraController())
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        idleFor(CameraViewModel.NOTICE_MS)
        modules.session.setState(dropped)
        idle()

        // The session gives the wait up as it does: the subject is gone, then the room waits again.
        modules.session.deliver(SessionMessage.Leave(EndReason.CONNECTION_LOST))
        modules.session.setState(SessionState.Waiting("482915"))
        idle()

        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_disconnected, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }
        idleFor(CameraViewModel.PEER_GONE_NOTICE_MS + 100)
        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    @Test
    fun anotherNoticeDuringTheWait_keepsItsOwnTime_andThenTheReconnectingLineIsBack() {
        val modules = launch()
        onScreen { _, fragment -> fragment.requireActivity().viewModel<SessionViewModel>().startRoom() }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        idleFor(CameraViewModel.NOTICE_MS)
        modules.session.setState(dropped)
        idle()

        // A photo taken meanwhile that cannot be read is not sent, and says so.
        modules.photos.shoot(Uri.parse("content://media/external/images/media/1"), takenAt = 1)
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.session_photo_not_sent, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }
        idleFor(CameraViewModel.NOTICE_MS + 100)

        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_reconnecting, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }
    }

    @Test
    fun aDropDuringTheSubjectsPinch_keepsTheReconnectingLine_whenThePinchEndsWithIt() {
        val modules = launch(FakeCameraController())
        val connected = SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER)
        modules.session.setState(connected)
        idle()
        idleFor(CameraViewModel.NOTICE_MS)
        modules.session.deliver(SessionMessage.ZoomSet(1.5f, final = false))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.zoom_held_label, "Joonhyung"), controls.noticeText.text.toString())
        }

        // The drop ends the pinch too, whose line going must not take this one down.
        modules.session.setState(dropped)
        idle()
        idleFor(SessionViewModel.ZOOMING_LINGER_MS + 100)
        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_reconnecting, "Joonhyung"), controls.noticeText.text.toString())
            assertEquals(View.VISIBLE, controls.noticeText.visibility)
        }

        modules.session.setState(connected)
        idle()
        onScreen { controls, _ -> assertEquals(View.GONE, controls.noticeText.visibility) }
    }

    @Test
    fun endSession_leaves_andTheSubjectsActionsAreNotices() {
        val camera = FakeCameraController(exposureRange = -4..4, hasFlash = true)
        val modules = launch(camera)
        modules.guides.setGuide(guide())
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()

        modules.session.deliver(SessionMessage.ZoomSet(2f, final = true))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_zoom_notice, "Joonhyung", "2"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.ExposureSet(2, final = true))
        idle()
        onScreen { controls, _ ->
            val ev = ExposureScale.formatEv(2, 0.5f)
            assertEquals(
                string(R.string.remote_brightness_notice, "Joonhyung", ev),
                controls.noticeText.text.toString(),
            )
        }

        modules.session.deliver(SessionMessage.FlashSet(FlashMode.ON))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_on_notice, "Joonhyung"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.FlashSet(FlashMode.AUTO))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_auto_notice, "Joonhyung"), controls.noticeText.text.toString())
        }
        modules.session.deliver(SessionMessage.FlashSet(FlashMode.OFF))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_flash_off_notice, "Joonhyung"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.GuideMove("guide-1", 0.5f, 0.5f, 0.8f, final = true))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.remote_guide_move_notice, "Joonhyung"), controls.noticeText.text.toString())
        }

        modules.session.deliver(SessionMessage.Leave(EndReason.CONNECTION_LOST))
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.peer_disconnected, "Joonhyung"), controls.noticeText.text.toString())
        }

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        onScreen { controls, _ -> controls.endSessionButton.performClick() }
        idle()
        assertEquals(emptyList<EndReason>(), modules.session.leaves)
        endSessionQuestion().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals(listOf(EndReason.LEFT), modules.session.leaves)
    }

    @Test
    fun endSession_cancelled_keepsTheSession() {
        val modules = launch()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()

        onScreen { controls, _ -> controls.endSessionButton.performClick() }
        idle()
        val question = endSessionQuestion()
        assertEquals(
            string(R.string.end_session_title),
            question.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text.toString(),
        )
        question.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()

        assertFalse(question.isShowing)
        assertEquals(emptyList<EndReason>(), modules.session.leaves)
        onScreen { controls, _ -> assertTrue(controls.endSessionButton.isVisible) }
    }

    @Test
    fun endSessionQuestion_goesWhenTheSubjectLeavesFirst() {
        val modules = launch()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        onScreen { controls, _ -> controls.endSessionButton.performClick() }
        idle()
        val question = endSessionQuestion()

        modules.session.setState(SessionState.Waiting("123456"))
        idle()

        assertFalse(question.isShowing)
        assertEquals(emptyList<EndReason>(), modules.session.leaves)
    }

    private fun endSessionQuestion(): AlertDialog {
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        return dialog
    }

    // ---- The gallery button and capture failures ------------------------------------------

    @Test
    fun galleryButton_aPhotoJustTaken_opensInPix_beforeMediaStoreTellsOfIt() {
        val uri = Uri.parse("content://media/external/images/7")
        launch(FakeCameraController().apply { photo = uri })
        val picture = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        onScreen { _, fragment -> fragment.photoOf = { if (it == uri) picture else null } }

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        settleReads()
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()
        assertNull(nextIntent())
        onScreen { controls, _ ->
            assertTrue(photoViewerOn(controls.root).root.isShown)
            assertEquals(1, photoViewerOn(controls.root).gridCount())
            photoViewerOn(controls.root).tapGrid(0)
        }
        settleReads()

        onScreen { controls, _ -> assertSame(picture, photoViewerOn(controls.root).pageShows()) }
    }

    /** Lets the screen's reads on Dispatchers.IO, such as the thumbnail's, finish and reach the screen. */
    private fun settleReads() {
        idle()
        Thread.sleep(300)
        idle()
    }

    private fun FragmentCameraBinding.thumbnailShows(): Bitmap? = (galleryThumbnail.drawable as? BitmapDrawable)?.bitmap

    @Test
    fun galleryButton_outsideASession_showsTheAlbumsNewest_andOpensPixsAlbumInPix_asAGrid_thenThePhotoTapped() {
        val modules = launch()
        val thumbnails = (1..3).associate {
            TestAppContainer.savedPhoto("p$it") to Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        }
        val pictures = (1..3).associate {
            TestAppContainer.savedPhoto("p$it") to
                Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        }
        // Written from the reading threads, several at once.
        val read = CopyOnWriteArrayList<Uri>()
        val cellSizes = ConcurrentHashMap.newKeySet<Int>()
        onScreen { _, fragment ->
            fragment.thumbnailOf = { thumbnails[it] }
            fragment.cellOf = { uri, px ->
                cellSizes += px
                thumbnails[uri]
            }
            fragment.photoOf = {
                read += it
                pictures[it]
            }
        }

        // Taken here or received in earlier sessions, oldest first.
        modules.album.value = (1..3).map { TestAppContainer.savedPhoto("p$it") }
        settleReads()
        onScreen { controls, _ ->
            assertTrue(controls.galleryButton.isEnabled)
            assertSame(thumbnails[TestAppContainer.savedPhoto("p3")], controls.thumbnailShows())
            controls.galleryButton.performClick()
        }
        settleReads()

        // In Pix, not the gallery: every photo in a grid, the newest first, under the album's name; no photo read whole.
        assertNull(nextIntent())
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.root.isShown)
            assertTrue(viewer.gridShown())
            assertEquals(3, viewer.gridCount())
            assertSame(thumbnails[TestAppContainer.savedPhoto("p3")], viewer.gridShows(0))
            assertSame(thumbnails[TestAppContainer.savedPhoto("p1")], viewer.gridShows(2))
            assertEquals(string(R.string.photo_page_counter_description, 3, 3), viewer.gridReads(0))
            assertEquals(string(R.string.photo_page_counter_description, 1, 3), viewer.gridReads(2))
            assertEquals(listOf(string(R.string.gallery_photos), string(R.string.gallery_albums)), viewer.textsShown())
            assertFalse(viewer.photoViewerShare.isShown)
            assertFalse(viewer.photoViewerDelete.isShown)
            assertEquals(0, viewer.stripCount())
            assertEquals(emptyList<Uri>(), read)
            // Each square read at its own size: a fifth of the screen's width, rounded up.
            val screen = controls.root.resources.displayMetrics
            assertEquals(setOf((minOf(screen.widthPixels, screen.heightPixels) + 4) / 5), cellSizes)
            // A tap opens that photo.
            viewer.tapGrid(1)
        }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.pagesShown())
            assertSame(pictures[TestAppContainer.savedPhoto("p2")], viewer.pageShows())
            // No title and no counter; TalkBack reads which photo of how many.
            assertEquals(emptyList<String>(), viewer.textsShown())
            assertTrue(viewer.photoViewerShare.isShown)
            assertEquals(string(R.string.photo_page_counter_description, 2, 3), viewer.pageReads())
            assertEquals(3, viewer.stripCount())
            assertEquals(1, viewer.stripSelected())
            viewer.photoViewerPages.setCurrentItem(0, false)
        }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertSame(pictures[TestAppContainer.savedPhoto("p1")], viewer.pageShows())
            assertEquals(string(R.string.photo_page_counter_description, 1, 3), viewer.pageReads())
            assertEquals(0, viewer.stripSelected())
            // A tap on the strip, as in a gallery app.
            viewer.tapStrip(2)
        }
        settleReads()
        onScreen { controls, _ ->
            assertSame(pictures[TestAppContainer.savedPhoto("p3")], photoViewerOn(controls.root).pageShows())
        }

        // System Back goes back to the grid, and another photo tapped there opens.
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.root.isShown)
            assertTrue(viewer.gridShown())
            assertFalse(viewer.photoViewerPages.isShown)
            assertEquals(listOf(string(R.string.gallery_photos), string(R.string.gallery_albums)), viewer.textsShown())
            viewer.tapGrid(2)
        }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.pagesShown())
            assertSame(pictures[TestAppContainer.savedPhoto("p1")], viewer.pageShows())
            assertEquals(0, viewer.stripSelected())
        }

        // Back twice: the grid, then Camera as it was.
        onScreen { controls, _ -> photoViewerOn(controls.root).photoViewerBack.performClick() }
        settleReads()
        onScreen { controls, _ -> assertTrue(photoViewerOn(controls.root).gridShown()) }
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        onScreen { controls, _ -> assertEquals(View.GONE, photoViewerOn(controls.root).root.visibility) }

        // Opened again, it is the grid again, not the photo last shown.
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()
        onScreen { controls, _ -> assertTrue(photoViewerOn(controls.root).gridShown()) }
    }

    @Test
    fun photoViewer_outsideASession_deletesThePhotoOnScreen_onceAsked_orSaysItCouldNot() {
        val modules = launch()
        val photos = (1..2).map { TestAppContainer.savedPhoto("p$it") }
        val thumbnails = photos.associateWith { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        val pictures = photos.associateWith { Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888) }
        onScreen { _, fragment ->
            fragment.thumbnailOf = { thumbnails[it] }
            fragment.cellOf = { uri, _ -> thumbnails[uri] }
            fragment.photoOf = { pictures[it] }
        }
        modules.album.value = photos
        settleReads()
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()
        onScreen { controls, _ -> photoViewerOn(controls.root).tapGrid(0) }
        settleReads()

        // A photo that cannot be deleted stays, and the screen says so.
        modules.deleteFails = true
        onScreen { controls, _ -> photoViewerOn(controls.root).photoViewerDelete.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settleReads()
        assertEquals(string(R.string.photo_not_deleted), shownNotice())
        onScreen { controls, _ -> assertEquals(2, photoViewerOn(controls.root).stripCount()) }

        modules.deleteFails = false
        onScreen { controls, _ -> photoViewerOn(controls.root).photoViewerDelete.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settleReads()

        assertEquals(listOf(photos[1]), modules.deleted)
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.root.isShown)
            assertSame(pictures[photos[0]], viewer.pageShows())
            // One photo left: no strip.
            assertEquals(0, viewer.stripCount())
            assertSame(thumbnails[photos[0]], controls.thumbnailShows())
        }
        // And the grid has it alone.
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.gridShown())
            assertEquals(1, viewer.gridCount())
            assertSame(thumbnails[photos[0]], viewer.gridShows(0))
        }
    }

    @Test
    fun theGallerysTabs_photosFirst_thenTheAlbums_whoseTapOpensItsGrid_andBackGoesBackStepByStep() {
        val modules = launch()
        val camera = listOf(TestAppContainer.savedPhoto("c1"), TestAppContainer.savedPhoto("c2"))
        val shot = TestAppContainer.savedPhoto("s1")
        modules.album.value = listOf(camera[0], shot, camera[1])
        modules.albums.value = listOf(
            PhoneAlbum("camera", "Camera", "DCIM/Camera/", camera),
            PhoneAlbum("shots", "Screenshots", "Pictures/Screenshots/", listOf(shot)),
        )
        val picture = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        // Written from the reading threads, several at once.
        val covers = CopyOnWriteArrayList<Uri>()
        onScreen { _, fragment ->
            fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
            fragment.cellOf = { uri, _ ->
                covers += uri
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            }
            fragment.photoOf = { picture }
        }
        settleReads()
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()

        // Photos first, the tabs in the header's middle.
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            val tabs = viewer.photoViewerTabs
            assertTrue(tabs.isShown)
            assertEquals(viewer.photoViewerContent.width / 2f, tabs.left + tabs.width / 2f, 1f)
            assertTrue(viewer.photoViewerTabPhotos.isSelected)
            assertFalse(viewer.photoViewerTabAlbums.isSelected)
            assertEquals(3, viewer.gridCount())
            assertEquals(0, viewer.albumsCount())
            covers.clear()
            viewer.photoViewerTabAlbums.performClick()
        }
        settleReads()

        // The albums, the one with the newest photo first, each its newest photo, its name, and its count.
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.photoViewerTabAlbums.isSelected)
            assertFalse(viewer.photoViewerGrid.isShown)
            assertEquals(2, viewer.albumsCount())
            assertEquals(string(R.string.album_camera) to "2", viewer.albumShows(0))
            assertEquals(string(R.string.album_screenshots) to "1", viewer.albumShows(1))
            assertEquals(
                app.resources.getQuantityString(
                    R.plurals.photo_album_description,
                    1,
                    string(R.string.album_screenshots),
                    1,
                ),
                viewer.albumReads(1),
            )
            assertTrue("$covers", covers.containsAll(listOf(camera[1], shot)))
            viewer.tapAlbum(1)
        }
        settleReads()

        // Its grid, under its name and count, without the tabs; a photo of it opens its page.
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertFalse(viewer.photoViewerTabs.isShown)
            assertEquals(listOf(string(R.string.album_screenshots), "1"), viewer.textsShown())
            assertEquals(1, viewer.gridCount())
            viewer.tapGrid(0)
        }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.pagesShown())
            assertFalse(viewer.photoViewerAlbumName.isShown)
            assertEquals(0, viewer.stripCount())
        }

        // Back: the album's grid, then the albums, then the camera.
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        settleReads()
        onScreen { controls, _ ->
            assertEquals(1, photoViewerOn(controls.root).gridCount())
            assertTrue(photoViewerOn(controls.root).photoViewerAlbumName.isShown)
        }
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        settleReads()
        onScreen { controls, _ -> assertEquals(2, photoViewerOn(controls.root).albumsCount()) }
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        onScreen { controls, _ -> assertEquals(View.GONE, photoViewerOn(controls.root).root.visibility) }

        // Opened again: Photos.
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.photoViewerTabPhotos.isSelected)
            assertEquals(3, viewer.gridCount())
        }
    }

    @Test
    fun photoViewer_anotherAppsPhoto_goesToAndroidsQuestion_withoutPixsOwn_andItsYesTakesItAway() {
        val modules = launch()
        val photos = (1..2).map { TestAppContainer.savedPhoto("p$it") }
        onScreen { _, fragment ->
            fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
            fragment.photoOf = { Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888) }
        }
        modules.album.value = photos
        modules.othersPhotos = setOf(photos[1])
        settleReads()
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()
        onScreen { controls, _ -> photoViewerOn(controls.root).tapGrid(0) }
        settleReads()

        onScreen { controls, _ -> photoViewerOn(controls.root).photoViewerDelete.performClick() }
        idle()

        // Android asks, so Pix does not.
        assertNull(ShadowDialog.getLatestDialog())
        val question = onScreen { _, fragment -> shadowOf(fragment.requireActivity()).lastIntentSenderRequest }
        assertSame(modules.deleteQuestion, question.intentSender)
        // Its yes deleted it; MediaStore tells of it later.
        onScreen { _, fragment ->
            shadowOf(fragment.requireActivity()).callOnActivityResult(question.requestCode, Activity.RESULT_OK, null)
        }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.pagesShown())
            assertEquals(0, viewer.stripCount())
            assertEquals(string(R.string.session_photo), viewer.pageReads())
        }
        assertTrue(modules.deleted.isEmpty())
    }

    @Test
    fun galleryButton_withPixsOwnPhotosOnly_asksForThePhones_andTheAnswerOpensTheGridOnThem() {
        val modules = launch()
        modules.photoAccess.value = PhotoAccess.OWN
        val everyPhoto = (1..3).map { TestAppContainer.savedPhoto("p$it") }
        modules.onReread = {
            modules.photoAccess.value = PhotoAccess.ALL
            modules.album.value = everyPhoto
        }
        onScreen { _, fragment -> fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) } }
        settleReads()
        val rereadsAtStart = modules.rereads

        // No photo of Pix's yet, and still a tap: it asks for the phone's photos, and opens nothing until answered.
        onScreen { controls, _ ->
            assertTrue(controls.galleryButton.isEnabled)
            controls.galleryButton.performClick()
        }
        idle()
        val request = onScreen { _, fragment -> shadowOf(fragment.requireActivity()).lastRequestedPermission }
        assertEquals(photoPermissions().toList(), request.requestedPermissions.toList())
        onScreen { controls, _ -> assertFalse(controls.root.findViewById<View>(R.id.photoViewer)?.isShown == true) }

        onScreen { _, fragment ->
            val grants = IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_GRANTED }
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                grants,
            )
        }
        settleReads()

        assertEquals(rereadsAtStart + 1, modules.rereads)
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.gridShown())
            assertEquals(3, viewer.gridCount())
            // Every photo shows: no line about them.
            assertFalse(viewer.photoViewerAccess.isShown)
        }
    }

    @Test
    fun theGridsLine_saysWhichPhotosShow_andOffersTheSettings_orAndroidsQuestionForTheChosenOnes() {
        val modules = launch()
        modules.photoAccess.value = PhotoAccess.OWN
        modules.album.value = listOf(TestAppContainer.savedPhoto("p1"))
        onScreen { _, fragment -> fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) } }
        settleReads()
        // Refused: the answer opens the grid on Pix's own.
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        idle()
        onScreen { _, fragment ->
            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission
            val refused = IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED }
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                refused,
            )
        }
        settleReads()
        onScreen { controls, _ ->
            val viewer = photoViewerOn(controls.root)
            assertTrue(viewer.gridShown())
            assertTrue(viewer.photoViewerAccess.isShown)
            assertEquals(string(R.string.photos_access_own), viewer.photoViewerAccessText.text.toString())
            assertEquals(string(R.string.photos_access_allow), viewer.photoViewerAccessButton.text.toString())
            // Android would ask no more: its settings.
            viewer.photoViewerAccessButton.performClick()
        }
        idle()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, nextIntent()?.action)

        // The chosen photos: Android's question again, to choose others.
        modules.photoAccess.value = PhotoAccess.CHOSEN
        settleReads()
        onScreen { controls, fragment ->
            val viewer = photoViewerOn(controls.root)
            assertEquals(string(R.string.photos_access_chosen), viewer.photoViewerAccessText.text.toString())
            assertEquals(string(R.string.photos_access_change), viewer.photoViewerAccessButton.text.toString())
            val before = shadowOf(fragment.requireActivity()).lastRequestedPermission
            viewer.photoViewerAccessButton.performClick()
            assertNotSame(before, shadowOf(fragment.requireActivity()).lastRequestedPermission)
        }
        // Android's question, not the settings.
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", nextIntent()?.action)

        // Every photo: the line goes.
        modules.photoAccess.value = PhotoAccess.ALL
        settleReads()
        onScreen { controls, _ -> assertFalse(photoViewerOn(controls.root).photoViewerAccess.isShown) }
    }

    @Test
    fun galleryButton_inASessionAsThePhotographer_isEmptyUntilItsFirstPhoto_andOpensItsPhotosInPix() {
        val taken = Uri.parse("content://media/external/images/media/20")
        val modules = launch(FakeCameraController().apply { photo = taken })
        val thumbnail = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val picture = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        onScreen { _, fragment ->
            fragment.thumbnailOf = { thumbnail }
            fragment.photoOf = { picture }
        }
        modules.album.value = listOf(TestAppContainer.savedPhoto("p0"))
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.PHOTOGRAPHER))
        settleReads()
        onScreen { controls, _ ->
            assertFalse(controls.galleryButton.isEnabled)
            assertNull(controls.thumbnailShows())
        }

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        settleReads()
        onScreen { controls, _ ->
            assertTrue(controls.galleryButton.isEnabled)
            assertSame(thumbnail, controls.thumbnailShows())
            controls.galleryButton.performClick()
        }
        settleReads()

        assertNull(nextIntent())
        onScreen { controls, _ ->
            assertTrue(photoViewerOn(controls.root).root.isShown)
            assertSame(picture, photoViewerOn(controls.root).pageShows())
            // No grid in a session: its photo at once. No title, and one photo has no counter.
            assertFalse(photoViewerOn(controls.root).photoViewerGrid.isShown)
            assertEquals(emptyList<String>(), photoViewerOn(controls.root).textsShown())
        }

        // System Back closes it, and the session goes on.
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        onScreen { controls, _ -> assertEquals(View.GONE, photoViewerOn(controls.root).root.visibility) }
        assertTrue(modules.session.leaves.isEmpty())

        // The session's end closes the viewer, and the thumbnail opens Pix's album again, the photo of the session in it.
        onScreen { controls, _ -> controls.galleryButton.performClick() }
        settleReads()
        modules.album.value = listOf(TestAppContainer.savedPhoto("p0"), taken)
        modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        settleReads()
        onScreen { controls, _ ->
            assertEquals(View.GONE, photoViewerOn(controls.root).root.visibility)
            controls.galleryButton.performClick()
        }
        settleReads()
        assertNull(nextIntent())
        onScreen { controls, _ ->
            assertTrue(photoViewerOn(controls.root).root.isShown)
            assertEquals(2, photoViewerOn(controls.root).gridCount())
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

    // A photo being saved (NFR-19, #208): the shutter is free for the next photo, the ways off Camera and the camera
    // switch wait for the save, and nothing on screen says "saving".
    @Test
    fun whileAPhotoIsSaved_theShutterIsFree_andTheGuideAndTheSwitchWait_withNoStatus() {
        val camera = FakeCameraController().apply {
            photo = Uri.parse("content://media/external/images/media/1")
            saving = CompletableDeferred()
        }
        launch(camera)

        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.shutterButton.isEnabled)
            assertFalse(controls.guideButton.isEnabled)
            assertFalse(controls.switchCameraButton.isEnabled)
            assertFalse(controls.captureStatus.isVisible)
        }
        camera.saving?.complete(Unit)
        idle()
        onScreen { controls, _ -> assertTrue(controls.guideButton.isEnabled) }
    }

    // ---- The scene photo step (#231) --------------------------------------------------------
    // The panel that says what to frame: shown when the step starts; a touch anywhere, here on the image, takes it away
    // and still does what it does there; Back from the review keeps it away; another start shows it again. The ratio:
    // on the bar while framing, and chosen as on Camera.

    @Test
    fun scenePhoto_aTouchAnywhere_takesThePanelAway_andOnlyAnotherStartShowsItAgain() {
        val camera = FakeCameraController(hasFlash = true).apply { frame = ScreenTestSupport.bitmap(300, 400) }
        val modules = launch(camera)
        // The check of the photo is held, so Back can shoot again.
        modules.persons.gate = CompletableDeferred()
        onScreen { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        idle()
        onScreen { controls, _ -> assertTrue(controls.sceneGuide.isShown) }

        onScreen { controls, _ ->
            val at = Pointer(0, controls.root.width / 2f, controls.root.height / 3f)
            touch(controls.root, MotionEvent.ACTION_DOWN, at)
            touch(controls.root, MotionEvent.ACTION_UP, at)
        }
        idle()
        onScreen { controls, _ -> assertFalse(controls.sceneGuide.isShown) }

        // Shoot, and Back while the photo is checked: the camera again, without the panel.
        onScreen { controls, _ -> controls.shutterButton.performClick() }
        idle()
        onScreen { controls, _ -> controls.sceneBackButton.performClick() }
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.scene_title), controls.sceneTitle.text.toString())
            assertFalse(controls.sceneGuide.isShown)
        }

        onScreen { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        idle()
        onScreen { controls, _ -> assertTrue(controls.sceneGuide.isShown) }
    }

    @Test
    fun scenePhoto_whileFraming_theRatioIsChosenAsOnCamera_andNotWhileThePhotoIsHeld() {
        val camera = FakeCameraController(hasFlash = true).apply { frame = ScreenTestSupport.bitmap(300, 400) }
        val modules = launch(camera)
        modules.persons.gate = CompletableDeferred()
        onScreen { _, fragment -> fragment.requireActivity().viewModel<GenerationViewModel>().beginScene() }
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.aspectButton.isShown)
            controls.aspectButton.performClick()
        }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.aspectPanel.isShown)
            controls.aspect916Option.performClick()
        }
        idle()
        assertEquals(listOf(AspectRatio.R16_9), camera.aspectRequests)
        assertEquals(AspectRatio.R16_9, runBlocking { modules.settings.aspectRatio() })
        onScreen { controls, _ -> assertFalse(controls.aspectPanel.isShown) }

        // The photo is taken: its ratio is the photo's, so there is no ratio to choose while it is held.
        onScreen { controls, _ ->
            controls.aspectButton.performClick()
            controls.shutterButton.performClick()
        }
        idle()
        onScreen { controls, _ ->
            assertFalse(controls.aspectButton.isShown)
            assertFalse(controls.aspectPanel.isShown)
        }

        // The ratios were open at a shutter that no touch closed them for, as with TalkBack: Back while the photo is
        // checked shows the camera without them.
        onScreen { controls, _ -> controls.sceneBackButton.performClick() }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.aspectButton.isShown)
            assertFalse(controls.aspectPanel.isShown)
        }
    }

    // ---- Zoom, sliders, focus, and the ratio -----------------------------------------------

    @Test
    fun aTapOnTheZoomReadout_stepsThroughPointSixOneAndTwo_andBack() {
        val camera = FakeCameraController(minZoom = 0.6f, maxZoom = 10f)
        launch(camera)

        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        val click = AccessibilityActionCompat.ACTION_CLICK.id
        onScreen { controls, _ -> ViewCompat.performAccessibilityAction(controls.zoomReadout, click, null) }
        idle()

        assertEquals(listOf(2f, 0.6f, 1f), camera.requests)
        assertEquals(listOf(true, true, true), camera.requestFinals)
    }

    @Test
    fun quickTapsBeforeTheCameraAppliesAStep_goOnFromThatStep_andAPinchStartsAgainFromTheAppliedZoom() {
        val camera = FakeCameraController(minZoom = 0.6f, maxZoom = 10f).apply { applies = false }
        launch(camera)

        // Two taps at 1× before the camera applies the first: 2×, then the widest, not 2× twice.
        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        assertEquals(listOf(2f, 0.6f), camera.requests)

        // The first step applied late does not stop the third tap going on from the second.
        camera.applyLocally(2f)
        idle()
        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        assertEquals(listOf(2f, 0.6f, 1f), camera.requests)

        // Another zoom request (here TalkBack's Zoom in) forgets the pending step: the next tap goes on from the
        // applied zoom, 2×, to the widest, not from the pending 1× to 2×.
        val zoomIn = AccessibilityActionCompat.ACTION_SCROLL_FORWARD.id
        onScreen { controls, _ -> ViewCompat.performAccessibilityAction(controls.zoomReadout, zoomIn, null) }
        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        assertEquals(5, camera.requests.size)
        assertEquals(0.6f, camera.requests.last())
    }

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
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        val modules = launch(camera)
        // A tap to focus brings the brightness bar up; then the guide, whose edit mode takes the preview's touches.
        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 3f) }
        idle()
        modules.guides.setGuide(guide())
        idle()
        onScreen { controls, _ ->
            slide(controls.opacitySlider, 0.8f)
            drag(controls.exposureBar, steps = 2)
        }
        idle()
        assertEquals(0.8f, modules.guides.state.value.opacity, 0.02f)
        assertEquals(2, camera.exposureRequests.last())

        onScreen { controls, _ -> controls.aspectButton.performClick() }
        idle()
        onScreen { controls, _ -> controls.aspect916Option.performClick() }
        idle()
        assertEquals(AspectRatio.R16_9, runBlocking { modules.settings.aspectRatio() })

        camera.state.value = camera.state.value.copy(aspect = AspectRatio.R1_1)
        idle()
        onScreen { controls, _ -> assertEquals(string(R.string.aspect_1_1), controls.aspectButton.text.toString()) }
    }

    // ---- Save guide --------------------------------------------------------------------

    @Test
    fun saveGuide_fillsTheHeartRedAndSaysWhereItIs_andASecondTapEmptiesIt() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.save_guide), controls.saveGuideButton.contentDescription.toString())
            assertEquals(R.drawable.ic_heart, shadowOf(controls.saveGuideButton.plainIcon).createdFromResId)
            assertEquals(app.getColor(R.color.pix_text), controls.saveGuideButton.iconTint.defaultColor)
        }

        onScreen { controls, _ -> controls.saveGuideButton.performClick() }
        idle()

        assertEquals(listOf("guide-1"), modules.saved.saved.value.map { it.id })
        onScreen { controls, _ ->
            assertEquals(string(R.string.guide_saved), controls.saveGuideButton.contentDescription.toString())
            // A ♥ filled red, as on Pick a pose and in the library (#297).
            assertEquals(R.drawable.ic_heart_filled, shadowOf(controls.saveGuideButton.plainIcon).createdFromResId)
            assertEquals(app.getColor(R.color.pix_heart), controls.saveGuideButton.iconTint.defaultColor)
            assertTrue(controls.toggleLabel.isShown)
            assertEquals(string(R.string.guide_saved_notice), controls.toggleLabel.text.toString())
        }

        onScreen { controls, _ -> controls.saveGuideButton.performClick() }
        idle()

        // History again, still in the library (#297).
        assertEquals(listOf("guide-1" to false), modules.saved.saved.value.map { it.id to it.favorite })
        onScreen { controls, _ ->
            assertEquals(string(R.string.guide_removed_notice), controls.toggleLabel.text.toString())
            assertEquals(string(R.string.save_guide), controls.saveGuideButton.contentDescription.toString())
            assertEquals(R.drawable.ic_heart, shadowOf(controls.saveGuideButton.plainIcon).createdFromResId)
            assertEquals(app.getColor(R.color.pix_text), controls.saveGuideButton.iconTint.defaultColor)
        }
        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS + 100, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertFalse(controls.toggleLabel.isShown) }
    }

    // ---- Design 2.8: brightness beside the focus, toggle labels --------------------------

    @Test
    fun aTapToFocus_bringsTheBrightnessBarBesideTheBox_andADragSetsIt_untilItGoesWithoutATouch() {
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        launch(camera)
        onScreen { controls, _ -> assertFalse(controls.exposureBar.isShown) }

        val (x, y) = onScreen { controls, _ ->
            val preview = controls.previewView
            val x = preview.width / 3f
            val y = preview.height / 2f
            touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, x, y))
            touch(preview, MotionEvent.ACTION_UP, Pointer(1, x, y))
            x to y
        }
        idle()
        onScreen { controls, _ ->
            val bar = controls.exposureBar
            val density = bar.resources.displayMetrics.density
            assertTrue(bar.isShown)
            // On the box's right, its 48 dp touch area reaching 12 dp over the 64 dp box, and centered on the tap.
            assertEquals(x + 32f * density - 12f * density, bar.translationX, 1f)
            assertEquals(y - bar.height / 2f, bar.translationY, 1f)
            drag(bar, steps = 2)
        }
        idle()
        assertEquals(2, camera.exposureRequests.last())

        // Held up while a finger is on it, past the idle time.
        onScreen { controls, _ -> touch(controls.exposureBar, MotionEvent.ACTION_DOWN, Pointer(1, 24f, 64f)) }
        shadowOf(Looper.getMainLooper()).idleFor(CameraFragment.BRIGHTNESS_IDLE_MS + 500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ ->
            assertTrue(controls.exposureBar.isShown)
            touch(controls.exposureBar, MotionEvent.ACTION_MOVE, Pointer(1, 24f, 200f))
            touch(controls.exposureBar, MotionEvent.ACTION_UP, Pointer(1, 24f, 200f))
        }
        idle()
        assertTrue(camera.exposureRequests.last() < 2)
        shadowOf(Looper.getMainLooper()).idleFor(CameraFragment.BRIGHTNESS_IDLE_MS + 500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertFalse(controls.exposureBar.isShown) }
    }

    @Test
    fun theCameraStartsAt0Ev_andEachTapToFocusGoesBackToIt() {
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        launch(camera)
        assertEquals(0, camera.exposureRequests.last())

        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 3f) }
        idle()
        onScreen { controls, _ -> drag(controls.exposureBar, steps = 2) }
        idle()
        assertEquals(2, camera.exposureRequests.last())

        // A new focus drops what the drag added; past the double-tap time, or the detector would take the two taps for
        // one double tap.
        shadowOf(Looper.getMainLooper()).idleFor(ViewConfiguration.getDoubleTapTimeout() + 100L, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 2f) }
        idle()
        assertEquals(0, camera.exposureRequests.last())
        assertEquals(2, camera.focusPoints.size)
    }

    @Test
    fun aDragUpOrDownOnThePreview_focusesWhereItStarts_andMovesTheBrightness_thenGoesOnFromThereWhileTheBarIsUp() {
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        launch(camera)

        // Straight from the finger's down, without lifting: it focuses there and the bar's sun follows the drag.
        onScreen { controls, _ ->
            val preview = controls.previewView
            val bar = controls.exposureBar
            val x = preview.width / 3f
            val y = preview.height / 2f
            val dy = previewDrag(bar, steps = 2)
            touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, x, y))
            touch(preview, MotionEvent.ACTION_MOVE, Pointer(1, x, y - dy / 2f))
            touch(preview, MotionEvent.ACTION_MOVE, Pointer(1, x, y - dy))
            touch(preview, MotionEvent.ACTION_UP, Pointer(1, x, y - dy))
        }
        idle()
        assertEquals(1, camera.focusPoints.size)
        assertEquals(2, camera.exposureRequests.last())
        onScreen { controls, _ -> assertTrue(controls.exposureBar.isShown) }

        // With the bar up, another drag goes on from there, down this time, without focusing again.
        onScreen { controls, _ ->
            val preview = controls.previewView
            val dy = previewDrag(controls.exposureBar, steps = 3)
            touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, 40f, 300f))
            touch(preview, MotionEvent.ACTION_MOVE, Pointer(1, 40f, 300f + dy))
            touch(preview, MotionEvent.ACTION_UP, Pointer(1, 40f, 300f + dy))
        }
        idle()
        assertEquals(1, camera.focusPoints.size)
        assertEquals(-1, camera.exposureRequests.last())
    }

    @Test
    fun aSidewaysDragOnThePreview_isNoBrightness() {
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        launch(camera)
        val before = camera.exposureRequests.toList()

        onScreen { controls, _ ->
            val preview = controls.previewView
            touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, 100f, 300f))
            touch(preview, MotionEvent.ACTION_MOVE, Pointer(1, 400f, 320f))
            touch(preview, MotionEvent.ACTION_UP, Pointer(1, 400f, 320f))
        }
        idle()
        assertEquals(before, camera.exposureRequests)
        assertTrue(camera.focusPoints.isEmpty())
    }

    @Test
    fun talkBacksFocusOnTheBrightnessBar_keepsItUp_pastTheIdleTime() {
        launch(FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true))
        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 3f) }
        idle()

        onScreen { controls, _ ->
            controls.exposureBar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        }
        shadowOf(Looper.getMainLooper()).idleFor(CameraFragment.BRIGHTNESS_IDLE_MS + 500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ ->
            assertTrue(controls.exposureBar.isShown)
            controls.exposureBar.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)
            controls.exposureBar.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                null,
            )
        }
        shadowOf(Looper.getMainLooper()).idleFor(CameraFragment.BRIGHTNESS_IDLE_MS + 500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertFalse(controls.exposureBar.isShown) }
    }

    @Test
    fun theFocusBox_staysAsLongAsTheBrightnessBar_andTheTwoGoTogether() {
        launch(FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true))
        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 3f) }
        // Past the moment the box used to fade by itself.
        shadowOf(Looper.getMainLooper()).idleFor(1_500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ ->
            assertBoth(controls, shown = true)
            touch(controls.exposureBar, MotionEvent.ACTION_DOWN, Pointer(1, 24f, 64f))
        }

        // Held past the idle time: both stay.
        shadowOf(Looper.getMainLooper()).idleFor(CameraFragment.BRIGHTNESS_IDLE_MS + 500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ ->
            assertBoth(controls, shown = true)
            touch(controls.exposureBar, MotionEvent.ACTION_UP, Pointer(1, 24f, 64f))
        }

        // Let go: both whole until the idle time, and both gone right after it.
        shadowOf(Looper.getMainLooper()).idleFor(CameraFragment.BRIGHTNESS_IDLE_MS - 100, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertBoth(controls, shown = true) }
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertBoth(controls, shown = false) }

        // A second tap shows both again.
        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 2f) }
        shadowOf(Looper.getMainLooper()).idleFor(1_500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ ->
            assertBoth(controls, shown = true)
            assertEquals(1f, controls.focusRing.scaleX, 0f)
        }
    }

    @Test
    fun onACameraWithoutBrightness_theFocusBoxFadesByItself() {
        launch(FakeCameraController(supportsFocusPoint = true))
        // The display's frames held, so the box is seen before its animation runs to its end.
        ShadowChoreographer.setPaused(true)
        try {
            onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 3f) }
            idle()
            onScreen { controls, _ ->
                assertTrue(controls.focusRing.isShown)
                assertFalse(controls.exposureBar.isShown)
            }
        } finally {
            ShadowChoreographer.setPaused(false)
        }

        shadowOf(Looper.getMainLooper()).idleFor(1_500, TimeUnit.MILLISECONDS)

        onScreen { controls, _ -> assertFalse(controls.focusRing.isShown) }
    }

    @Test
    fun theSubjectsTapToFocus_showsTheBoxThereAlone_andItsNoticeBriefly_butNotAtEveryTap() {
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        val modules = launch(camera)
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()

        // The display's frames held, so the box is seen before its animation runs to its end.
        ShadowChoreographer.setPaused(true)
        try {
            modules.session.deliver(SessionMessage.FocusSet(0.25f, 0.5f))
            idle()
            onScreen { controls, _ ->
                val preview = controls.previewView
                val frame = fitViewfinderFrame(preview.width.toFloat(), preview.height.toFloat())
                val ring = controls.focusRing
                assertTrue(ring.isShown)
                assertEquals(frame.left + frame.width * 0.25f, ring.translationX + ring.layoutParams.width / 2f, 1f)
                assertEquals(frame.top + frame.height * 0.5f, ring.translationY + ring.layoutParams.height / 2f, 1f)
                // The bar is for this phone's own tap.
                assertFalse(controls.exposureBar.isShown)
                assertTrue(controls.noticeText.isShown)
                assertEquals(string(R.string.remote_focus_notice, "Joonhyung"), controls.noticeText.text.toString())
            }
        } finally {
            ShadowChoreographer.setPaused(false)
        }
        assertEquals(listOf(0.25f to 0.5f), camera.focusPoints)

        // The notice goes sooner than the other notices, and the box by itself.
        shadowOf(Looper.getMainLooper()).idleFor(SessionViewModel.FOCUS_NOTICE_MS + 100, TimeUnit.MILLISECONDS)
        onScreen { controls, _ ->
            assertFalse(controls.noticeText.isShown)
            assertFalse(controls.focusRing.isShown)
        }

        // A tap soon after: the box again, the notice not.
        ShadowChoreographer.setPaused(true)
        try {
            modules.session.deliver(SessionMessage.FocusSet(0.75f, 0.25f))
            idle()
            onScreen { controls, _ ->
                assertTrue(controls.focusRing.isShown)
                assertFalse(controls.noticeText.isShown)
            }
        } finally {
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test
    fun theSubjectsTapToFocus_takesDownThisPhonesBrightnessBar_unlessAFingerIsOnIt() {
        val camera = FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true)
        val modules = launch(camera)
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        onScreen { controls, _ -> tapPreview(controls, controls.previewView.width / 3f) }
        idle()

        // Held: nothing moves under the finger.
        val heldAt = onScreen { controls, _ ->
            assertBoth(controls, shown = true)
            touch(controls.exposureBar, MotionEvent.ACTION_DOWN, Pointer(1, 24f, 64f))
            controls.focusRing.translationX
        }
        modules.session.deliver(SessionMessage.FocusSet(0.9f, 0.1f))
        idle()
        onScreen { controls, _ ->
            assertBoth(controls, shown = true)
            assertEquals(heldAt, controls.focusRing.translationX, 0f)
            touch(controls.exposureBar, MotionEvent.ACTION_UP, Pointer(1, 24f, 64f))
        }

        // Let go: the bar goes at the next tap, and the box is where the subject tapped.
        val viewModel = cameraViewModel()
        ShadowChoreographer.setPaused(true)
        try {
            modules.session.deliver(SessionMessage.FocusSet(0.9f, 0.1f))
            idle()
            assertFalse(viewModel.uiState.value.brightnessOpen)
            onScreen { controls, _ ->
                val preview = controls.previewView
                val frame = fitViewfinderFrame(preview.width.toFloat(), preview.height.toFloat())
                val ring = controls.focusRing
                assertTrue(ring.isShown)
                assertEquals(1f, ring.alpha, 0f)
                assertEquals(frame.left + frame.width * 0.9f, ring.translationX + ring.layoutParams.width / 2f, 1f)
            }
        } finally {
            ShadowChoreographer.setPaused(false)
        }
        shadowOf(Looper.getMainLooper()).idleFor(1_500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertBoth(controls, shown = false) }
    }

    /** The focus box and the brightness bar, both whole and in view, or both gone. */
    private fun assertBoth(controls: FragmentCameraBinding, shown: Boolean) {
        assertEquals("focus box", shown, controls.focusRing.isShown)
        assertEquals("brightness bar", shown, controls.exposureBar.isShown)
        if (shown) {
            assertEquals(1f, controls.focusRing.alpha, 0f)
            assertEquals(1f, controls.exposureBar.alpha, 0f)
        }
    }

    /** A tap to focus at [x] on the preview's middle line. */
    private fun tapPreview(controls: FragmentCameraBinding, x: Float) {
        val preview = controls.previewView
        touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, x, preview.height / 2f))
        touch(preview, MotionEvent.ACTION_UP, Pointer(1, x, preview.height / 2f))
    }

    /** How far a finger moves for [steps] of the fake camera's 0.5 EV (`ExposureBarView.DP_PER_EV`). */
    private fun previewDrag(bar: ExposureBarView, steps: Int): Float =
        steps * FAKE_EV_STEP * ExposureBarView.DP_PER_EV * bar.resources.displayMetrics.density

    @Test
    fun aTapToFocusNearTheRightEdge_putsTheBrightnessBarOnTheBoxsLeft() {
        launch(FakeCameraController(exposureRange = -4..4, supportsFocusPoint = true))

        val x = onScreen { controls, _ ->
            val preview = controls.previewView
            val x = preview.width - 20f
            touch(preview, MotionEvent.ACTION_DOWN, Pointer(1, x, preview.height / 2f))
            touch(preview, MotionEvent.ACTION_UP, Pointer(1, x, preview.height / 2f))
            x
        }
        idle()
        onScreen { controls, _ ->
            val bar = controls.exposureBar
            val density = bar.resources.displayMetrics.density
            assertEquals(x - 32f * density + 12f * density - bar.width, bar.translationX, 1f)
        }
    }

    @Test
    fun hideAndShow_andAZoomStep_showALabelAtTheTop_thatTheNextReplaces_andThatGoes() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()

        onScreen { controls, _ -> controls.hideGuideButton.performClick() }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.toggleLabel.isShown)
            assertEquals(string(R.string.hint_guide_hidden), controls.toggleLabel.text.toString())
            // The persistent hint steps aside while the label says the same.
            assertFalse(controls.hintText.isShown)
        }

        onScreen { controls, _ -> controls.hideGuideButton.performClick() }
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.guide_shown_label), controls.toggleLabel.text.toString())
        }

        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS - 500, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        onScreen { controls, _ -> assertEquals("2×", controls.toggleLabel.text.toString()) }
        // The zoom label's own time, not the one before it.
        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertTrue(controls.toggleLabel.isShown) }
        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertFalse(controls.toggleLabel.isShown) }
    }

    @Test
    fun aTapOnTheReadout_whileTheSubjectZooms_namesTheSubject() {
        val camera = FakeCameraController()
        val modules = launch(camera)
        onScreen { _, fragment -> fragment.requireActivity().viewModel<SessionViewModel>().startRoom() }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Joonhyung"), Role.PHOTOGRAPHER))
        idle()
        modules.container.remoteControlHandler.zoomHold.take(Role.SUBJECT)
        val requests = camera.requests.size

        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.toggleLabel.isShown)
            assertEquals(string(R.string.zoom_held_label, "Joonhyung"), controls.toggleLabel.text.toString())
        }
        assertEquals(requests, camera.requests.size)
    }

    @Test
    fun openingTheRatios_takesTheToggleLabelDown_whichTheyWouldCover() {
        launch()

        onScreen { controls, _ -> controls.zoomReadout.performClick() }
        idle()
        onScreen { controls, _ -> assertTrue(controls.toggleLabel.isShown) }

        onScreen { controls, _ -> controls.tapThroughRoot(controls.aspectButton) }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.aspectPanel.isShown)
            assertFalse(controls.toggleLabel.isShown)
        }
    }

    @Test
    fun theGuideEditToggle_saysWhatAPinchMovesNow_overTheToggle() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        onScreen { controls, _ -> assertFalse(controls.pinchModeLabel.isShown) }

        // A new guide leaves the pinch to the camera (#304), so the first tap gives it to the guide.
        onScreen { controls, _ -> controls.editGuideButton.performClick() }
        idle()
        onScreen { controls, _ ->
            val label = controls.pinchModeLabel
            assertTrue(label.isShown)
            assertEquals(string(R.string.pinch_mode_guide), label.text.toString())
            assertEquals(string(R.string.pinch_mode_guide_description), label.contentDescription.toString())
            val toggle = IntArray(2).also(controls.editGuideButton::getLocationInWindow)
            val above = IntArray(2).also(label::getLocationInWindow)
            assertTrue(above[1] + label.height <= toggle[1])
        }

        onScreen { controls, _ -> controls.editGuideButton.performClick() }
        idle()
        onScreen { controls, _ ->
            assertEquals(string(R.string.pinch_mode_camera), controls.pinchModeLabel.text.toString())
        }
        shadowOf(Looper.getMainLooper()).idleFor(CameraViewModel.NOTICE_MS + 100, TimeUnit.MILLISECONDS)
        onScreen { controls, _ -> assertFalse(controls.pinchModeLabel.isShown) }
    }

    @Test
    fun guideButtonIcons_otherStateChanges_keepTheSameDrawable() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        val (save, hide) = onScreen { controls, _ -> controls.saveGuideButton.icon to controls.hideGuideButton.icon }

        modules.guides.update(final = true) { it.copy(opacity = 0.7f) }
        idle()

        onScreen { controls, _ ->
            assertSame(save, controls.saveGuideButton.icon)
            assertSame(hide, controls.hideGuideButton.icon)
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

    @Test
    fun referenceThumbnail_draggedMostlyPastTheSide_hidesThere_andATapOnItsHandleBringsItBack() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        val viewModel = cameraViewModel()

        // From the top-left corner, to the left until most of it is out of the preview.
        onScreen { controls, _ ->
            val thumbnail = controls.referenceThumbnail
            val out = thumbnail.width + ViewConfiguration.get(thumbnail.context).scaledTouchSlop * 4f
            touch(thumbnail, MotionEvent.ACTION_DOWN, Pointer(1, 5f, 5f))
            touch(thumbnail, MotionEvent.ACTION_MOVE, Pointer(1, 5f - out, 5f))
            touch(thumbnail, MotionEvent.ACTION_UP, Pointer(1, 5f - out, 5f))
        }
        settle()
        assertTrue(viewModel.uiState.value.referenceHidden)
        assertEquals(ReferenceCorner.TOP_LEFT, viewModel.uiState.value.referenceCorner)
        onScreen { controls, _ ->
            val thumbnail = controls.referenceThumbnail
            val peek = (REFERENCE_PEEK_DP * thumbnail.resources.displayMetrics.density).roundToInt()
            // Only its peek is inside, see-through, with the handle over it.
            assertEquals((peek - thumbnail.width).toFloat(), thumbnail.translationX)
            assertEquals(REFERENCE_HIDDEN_ALPHA, thumbnail.alpha)
            assertTrue(controls.referenceHandle.isShown)
            // TalkBack hears that it is hidden, and its tap shows it; there is nothing to move or to hide.
            val info = controls.referenceThumbnail.createAccessibilityNodeInfo()
            val labels = info.actionList.map { it.label?.toString() }
            assertEquals(string(R.string.reference_hidden), info.stateDescription?.toString())
            assertTrue(labels.contains(string(R.string.show_reference)))
            assertFalse(labels.contains(string(R.string.hide_reference)))
            assertFalse(labels.contains(string(R.string.move_reference_top_right)))

            touch(controls.referenceHandle, MotionEvent.ACTION_DOWN, Pointer(1, 5f, 5f))
            touch(controls.referenceHandle, MotionEvent.ACTION_UP, Pointer(1, 5f, 5f))
        }
        settle()
        assertFalse(viewModel.uiState.value.referenceHidden)
        onScreen { controls, _ ->
            val thumbnail = controls.referenceThumbnail
            assertTrue(thumbnail.translationX > 0f)
            assertEquals(1f, thumbnail.alpha)
            assertFalse(controls.referenceHandle.isShown)
            // Shown, TalkBack can hide it beside the moves.
            val labels = thumbnail.createAccessibilityNodeInfo().actionList.map { it.label?.toString() }
            assertTrue(labels.contains(string(R.string.hide_reference)))
            assertTrue(ViewCompat.performAccessibilityAction(thumbnail, R.id.hide_reference, null))
        }
        settle()
        assertTrue(viewModel.uiState.value.referenceHidden)

        // Another guide's thumbnail shows again, in the corner the last one had.
        modules.guides.setGuide(guide().copy(id = "another"))
        settle()
        assertFalse(viewModel.uiState.value.referenceHidden)
        onScreen { controls, _ ->
            assertEquals(1f, controls.referenceThumbnail.alpha)
            assertFalse(controls.referenceHandle.isShown)
        }
    }

    @Test
    fun referenceThumbnail_hiddenWhileEnlarged_leavesTheSmallStrip_andComesBackSmall() {
        val modules = launch()
        modules.guides.setGuide(guide())
        idle()
        val viewModel = cameraViewModel()
        var small = 0 to 0
        onScreen { controls, _ ->
            small = controls.referenceThumbnail.width to controls.referenceThumbnail.height
            controls.referenceThumbnail.performClick()
        }
        settle()
        assertTrue(viewModel.uiState.value.referenceExpanded)

        // Enlarged in the top-left corner, to the left until most of it is out of the preview.
        onScreen { controls, _ ->
            val thumbnail = controls.referenceThumbnail
            assertTrue(thumbnail.width > small.first * 2)
            val out = thumbnail.width + ViewConfiguration.get(thumbnail.context).scaledTouchSlop * 4f
            touch(thumbnail, MotionEvent.ACTION_DOWN, Pointer(1, 5f, 5f))
            touch(thumbnail, MotionEvent.ACTION_MOVE, Pointer(1, 5f - out, 5f))
            touch(thumbnail, MotionEvent.ACTION_UP, Pointer(1, 5f - out, 5f))
        }
        settle()
        assertTrue(viewModel.uiState.value.referenceHidden)
        assertFalse(viewModel.uiState.value.referenceExpanded)
        onScreen { controls, _ ->
            val thumbnail = controls.referenceThumbnail
            val peek = (REFERENCE_PEEK_DP * thumbnail.resources.displayMetrics.density).roundToInt()
            // What stays is the small thumbnail's strip, at its size and with its peek inside.
            assertEquals(small, thumbnail.width to thumbnail.height)
            assertEquals(1f, thumbnail.scaleX)
            assertEquals(1f, thumbnail.scaleY)
            assertEquals((peek - small.first).toFloat(), thumbnail.translationX)
            // The handle over it is no taller than a touch target, as over any small strip.
            assertEquals(maxOf(small.second, dp(REFERENCE_HANDLE_DP, thumbnail)), controls.referenceHandle.height)

            touch(controls.referenceHandle, MotionEvent.ACTION_DOWN, Pointer(1, 5f, 5f))
            touch(controls.referenceHandle, MotionEvent.ACTION_UP, Pointer(1, 5f, 5f))
        }
        settle()
        assertFalse(viewModel.uiState.value.referenceHidden)
        assertFalse(viewModel.uiState.value.referenceExpanded)
        onScreen { controls, _ ->
            assertEquals(small, controls.referenceThumbnail.width to controls.referenceThumbnail.height)
            assertEquals(1f, controls.referenceThumbnail.alpha)
        }
    }

    private fun dp(value: Float, view: View): Int = (value * view.resources.displayMetrics.density).roundToInt()

    /** Every change played to its end, the thumbnail's glide included. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))

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

    /** A drag up the brightness bar by [steps] exposure indexes (down for negative ones), from its middle. */
    private fun drag(bar: ExposureBarView, steps: Int) {
        assertTrue(bar.height > 0)
        val dy = previewDrag(bar, steps)
        touch(bar, MotionEvent.ACTION_DOWN, Pointer(1, bar.width / 2f, bar.height / 2f))
        touch(bar, MotionEvent.ACTION_MOVE, Pointer(1, bar.width / 2f, bar.height / 2f - dy))
        touch(bar, MotionEvent.ACTION_UP, Pointer(1, bar.width / 2f, bar.height / 2f - dy))
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

    /** A tap on [view] through the whole screen, from its root, as a finger would land on its middle. */
    private fun FragmentCameraBinding.tapThroughRoot(view: View) {
        val at = IntArray(2).also(view::getLocationInWindow)
        val origin = IntArray(2).also(root::getLocationInWindow)
        val x = at[0] - origin[0] + view.width / 2f
        val y = at[1] - origin[1] + view.height / 2f
        touch(root, MotionEvent.ACTION_DOWN, Pointer(0, x, y))
        touch(root, MotionEvent.ACTION_UP, Pointer(0, x, y))
    }

    @Test
    fun theRatioButton_opensTheRatios_andAChoiceTheButtonATouchElsewhereOrBackClosesThem() {
        val modules = launch()
        fun open() = onScreen { controls, _ -> controls.aspectPanel.isShown }
        fun tap(view: (FragmentCameraBinding) -> View) {
            onScreen { controls, _ -> controls.tapThroughRoot(view(controls)) }
            idle()
        }

        // The button opens them, the camera's ratio chosen and each named for TalkBack.
        tap { it.aspectButton }
        assertTrue(open())
        onScreen { controls, _ ->
            assertTrue(controls.aspect34Option.isSelected)
            assertFalse(controls.aspect916Option.isSelected)
            assertEquals(string(R.string.aspect_ratio_description, "9:16"), controls.aspect916Option.contentDescription)
        }

        // The button again closes them, and does not open them again.
        tap { it.aspectButton }
        assertFalse(open())

        // A touch on the preview closes them, and so does Back.
        tap { it.aspectButton }
        tap { it.previewView }
        assertFalse(open())
        tap { it.aspectButton }
        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertFalse(open())

        // The ratio the camera has only closes them; a new one goes to the camera and is kept.
        tap { it.aspectButton }
        tap { it.aspect34Option }
        assertFalse(open())
        assertEquals(null, runBlocking { modules.settings.aspectRatio() })
        tap { it.aspectButton }
        tap { it.aspect11Option }
        assertFalse(open())
        assertEquals(AspectRatio.R1_1, runBlocking { modules.settings.aspectRatio() })
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

    private companion object {
        /** The fake camera's exposure step with a range (FakeCameraController). */
        const val FAKE_EV_STEP = 0.5f
    }
}
