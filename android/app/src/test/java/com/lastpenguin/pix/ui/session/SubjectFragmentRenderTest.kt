// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.FakePoseCandidateCodec
import com.lastpenguin.pix.session.GuideReference
import com.lastpenguin.pix.session.IcePath
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.ConfirmStep
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.ScreenTestSupport.assertNoticeInTheZoomRowsPlace
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNotice
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNoticeView
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.ScreenTestSupport.windowBox
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.CountdownShutterDrawable
import com.lastpenguin.pix.ui.camera.ExposureBarView
import com.lastpenguin.pix.ui.camera.ExposureScale
import com.lastpenguin.pix.ui.camera.FocusBrightness
import com.lastpenguin.pix.ui.camera.REFERENCE_HIDDEN_ALPHA
import com.lastpenguin.pix.ui.camera.REFERENCE_PEEK_DP
import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
import com.lastpenguin.pix.ui.settings.TutorialFragment
import com.lastpenguin.pix.ui.showNoticeOnScreen
import java.time.Duration
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog

/*
 * Subject view's render paths (R&S 6.6, Testing Documentation 2.1.6), on the real Fragment under FragmentScenario
 * with a container of fakes. Partitions: not connected / connected before the first frame / streaming / frames
 * stopped for 2 s ("camera paused") / streaming again / reconnecting; the photographer's capabilities with and
 * without a flash and an exposure range; the camera state's zoom, flash, and ratio; the mirror (#201): the rear
 * camera's video, grid, guide, and reference photo shown as a mirror, the front camera's as sent, and a drag on the
 * mirrored guide moving it the way the finger goes; a guide arriving, the edit-mode
 * toggle, a hidden guide; the photographer's reference photo with its guide, enlarged, kept while the guide is hidden,
 * gone with a replaced or removed guide, moved to another corner on this phone only, and hidden at the video's side
 * by a flick and shown again by a tap on its handle, on this phone only, and hidden while enlarged (the small strip,
 * and small when it is back; #237), a mirror through each change of its size; the camera menu (FR-1.17): its flash's
 * tap, which keeps it open, and Back and its button closing it; a notice in the zoom row's place, above the line that
 * names the camera (#298); the photographer's countdown (FR-1.17): each second
 * with their name, the menu's flash disabled, and all gone at its end, and the shutter a stop square in the timer's
 * ring, from each second's place in the timer's length, also for a first second that comes midway, and white again at
 * its end; remote focus with the
 * brightness beside it (FR-7.7, FR-7.3, #273): a tap inside the image (the focus asked for at the photographer's x, the
 * box there, the bar beside it), beside the image and in guide-edit mode (nothing), on a camera without point focus
 * (the bar alone); a drag on the bar (steps, then the final value; both gone after the idle time); a drag on the video
 * without the bar up (a tap first, nothing sent until the tap's answer, then steps from the brightness it gave); a
 * drag on the bar itself before the tap's answer (nothing sent, then from the brightness it gave), and one on the
 * video with the bar already up before that answer (the same, and no second tap); the bar after the screen's view is
 * made again (gone); the
 * photographer's own focus (the box alone at its place on the mirror; this phone's bar up, which goes, or held under a
 * finger, where nothing moves; while a drag still waits for its tap's answer, which ends that drag); TalkBack's action
 * on the video (the focus at the image's middle and the bar, and nothing in guide-edit mode); a tap and TalkBack's
 * action while the photographer's camera is paused (nothing), and once frames come again; a tap while their camera
 * rebinds for a new ratio (nothing), and once a frame of the new shape comes; the live view on mobile data (FR-6.13); the Guide button (FR-6.15) disabled until connected, adding or
 * changing the guide, opening Add a pose guide, and "Couldn't send the guide" for a guide that did not go; Leave and the
 * back button, which both ask first (#298: confirmed, cancelled), and the session ending by a lost connection (also with Add a pose guide open over it) or
 * the photographer, named or before the name arrived. Save guide (FR-6.16): hidden without a guide, then saving it
 * and filled (an empty ♥, then filled red); the photographer's set (FR-6.15): what the photographer is doing in place of "camera paused", the set's
 * Generating poses opened once per set (Iter3-A13), which goes on to Pick a pose by itself, so after Back each opens by
 * the button, a new set opening its Generating poses again, a set with no copy here, which opens nothing, a set the
 * photographer closes after Back, which says so, and a
 * set kept through a reconnect, with "Reconnecting…" in place of what the photographer is doing; the photographer's
 * Reference confirm (FR-6.15): "checking the guide", Your reference opened once per confirm and then by the same
 * button, a set still picking shown before the confirm that follows it, and a guide the photographer uses after Back,
 * which says so; the photographer's Generate poses (FR-6.15): "describing a pose" in place of "camera paused", Generate
 * poses opened once per description and then by the same button, opened once Add a pose guide closes when it came
 * under the sheet, a set held here shown before it, and a close after Back, which says so. A tap on the readout while
 * the photographer's pinch holds the zoom: nothing sent, and the label names the photographer (FR-7.6, #236).
 * The UAT's opinions (#271): the line above the shutter (#314; in the shutter's place until then) empty until
 * connected, then whose camera this is and "Live" on one line, the name alone while a step's button shows beside the
 * thumbnail, with no notice saying it as well; *Leave* red; the guide-edit toggle one person icon, unlit with the guide plain and lit with it
 * filled. The grid on by default and kept off. The tip and guide-edit buttons on glass, the toggle's tinted while on
 * (#298). The guide's panel (FR-7.2): hidden without a guide; with one, above the line that names the camera, Hide and
 * ✕ asking the photographer's phone; a hidden guide (Show, the opacity off). 1:1 (R&S 6.6): the 4:3 around the frame
 * dimmed; 3:4 not.
 * What the photographer did (#292): each action a line with their name where the labels show, for a label's time and
 * the focus's for its shorter one; the photographer's pinch named for as long as it, replaced by the ratio's line, gone
 * when it ends without one; another label meanwhile keeps its own time, and the line is back after it.
 * This phone's shutter (FR-7.8, #314; state-based): dimmed before the session, connected without a frame, while the
 * link reconnects, and during the photographer's step; while the photographer's timer counts down it cancels, said
 * in its name and in the tutorial's tip, not under the number, with no refusal for the timer's answer; with the video showing a press asks once
 * and holds the shutter;
 * answered taken (free, no line), not taken (free, a line for a label's time), and not answered (the line after the
 * wait).
 * A result under a sheet: a start's answer and a refused press that come while Add a pose guide covers the screen,
 * said once it is gone; and under this screen's own dialogs, "Leave the session?" and the tutorial's tips.
 * The photographer's timer (#318): the menu's Timer between Flash and Grid, off and each value, a tap sending the
 * request with the menu left open; the timer's and the flash's icons beside the menu's button while on, in Camera's
 * order, each value's icon, a tap opening the menu; the shutter named for the timer; the menu's Timer waiting during
 * the countdown; the photographer's change said where the lines show, set and off.
 * This phone's start of AI poses (FR-6.15, #276): the answer said where the lines show, begun (who takes the scene
 * photo), not begun, or the photographer asked to agree first; no answer, said after the wait.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SubjectFragmentRenderTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null

    /** Where the subject's pose syncer runs in the tests of the photographer's set, as the session would run it. */
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        scenario?.close()
        receiving.cancel()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    /** Subject view with its navigation in place before it starts, as in the app, so it knows what covers it. */
    private fun launchWithNav(): Pair<TestAppContainer, TestNavHostController> {
        val modules = TestAppContainer().install()
        val (launched, nav) = launchPrepared<SubjectFragment>(R.id.subjectFragment) {}
        scenario = launched
        return modules to nav
    }

    private fun <T> onScreen(block: (FragmentSubjectBinding, SubjectFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result =
                block(FragmentSubjectBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    private fun connect(
        modules: TestAppContainer,
        name: String = "Dongje",
        exposureRange: IntRange = 0..0,
        hasFlash: Boolean = false,
        firstFrame: Boolean = true,
        focuses: Boolean = false,
    ) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo(name), Role.SUBJECT))
        if (firstFrame) modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(
                CameraCapabilities(
                    0.6f,
                    10f,
                    listOf(1f, 2f),
                    exposureRange,
                    0.5f,
                    hasFlash,
                    supportsFocusPoint = focuses,
                ),
            ),
        )
        // The "Connecting…" check runs every 500 ms on the main looper; let one more round see the session.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    @Test
    fun notConnected_showsConnectingAndDisablesTheControls() {
        launch()

        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.liveDot.visibility)
            assertEquals(string(R.string.subject_title_placeholder), binding.liveDot.contentDescription.toString())
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.connecting), binding.connectingText.text.toString())
            assertEquals(View.GONE, binding.compositionOverlay.visibility)
            assertFalse(binding.zoomReadout.isEnabled)
            assertEquals(View.GONE, binding.editGuideButton.visibility)
            assertEquals(View.GONE, binding.menuFlash.visibility)
            assertFalse(binding.exposureBar.isShown)
            // The tutorial shows the pinch (FR-10.4); TalkBack hears it from the video, which has no caption under it.
            assertEquals(string(R.string.subject_video_description), binding.liveView.contentDescription.toString())
        }
    }

    @Test
    fun connecting_staysUntilTheFirstFrame_andPausedOnlyWhenFramesStop() {
        val modules = launch()

        connect(modules, firstFrame = false)
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.connecting), binding.connectingText.text.toString())
        }

        modules.video.deliverFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ -> assertEquals(View.GONE, binding.connectingText.visibility) }

        // No frame for longer than the 2 s stall limit: the camera is paused.
        Thread.sleep(2_100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.camera_paused), binding.connectingText.text.toString())
        }

        modules.video.deliverFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ -> assertEquals(View.GONE, binding.connectingText.visibility) }
    }

    @Test
    fun reconnecting_saysSoOverTheLastFrame_andDisablesTheControls() {
        val modules = launch()
        connect(modules)

        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.reconnecting), binding.connectingText.text.toString())
            assertEquals(View.GONE, binding.liveDot.visibility)
            assertFalse(binding.zoomReadout.isEnabled)
        }

        // Back with the link: the video plays on, without the caption.
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ -> assertEquals(View.GONE, binding.connectingText.visibility) }
    }

    @Test
    fun connected_showsTheLiveLightTheGridAndTheCapabilities() {
        val modules = launch()

        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.liveDot.visibility)
            assertEquals(string(R.string.subject_title, "Dongje"), binding.liveDot.contentDescription.toString())
            assertEquals(View.GONE, binding.connectingText.visibility)
            assertEquals(View.VISIBLE, binding.compositionOverlay.visibility)
            assertTrue(binding.zoomReadout.isEnabled)
            assertEquals(View.VISIBLE, binding.menuFlash.visibility)
            assertTrue(binding.menuFlash.isEnabled)
            // The brightness comes up beside a tap on the video, not before it.
            assertFalse(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }
    }

    @Test
    fun cameraState_updatesTheReadoutTheFlashAndTheFrame() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        modules.session.deliver(
            SessionMessage.CameraStateUpdate(
                zoom = 2f,
                by = Role.PHOTOGRAPHER,
                final = true,
                exposure = 2,
                flash = FlashMode.AUTO,
                aspect = AspectRatio.R16_9,
            ),
        )
        idle()

        onScreen { binding, _ ->
            assertEquals(string(R.string.zoom_ratio, "2.0"), binding.zoomReadout.text.toString())
            assertEquals(string(R.string.flash_auto), binding.menuFlash.contentDescription)
            assertTrue(binding.menuFlash.isActivated)
            assertEquals(AspectRatio.R16_9.frameAspect, binding.compositionOverlay.frameAspect)
            assertEquals(AspectRatio.R16_9.frameAspect, binding.guideOverlay.frameAspect)
            assertEquals(2, binding.exposureBar.index)
        }
    }

    // ---- The mirror (#201) ------------------------------------------------------------------------------------

    private fun cameraState(facing: CameraFacing) =
        SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, facing = facing)

    private fun FragmentSubjectBinding.mirrorShown(): List<Any> = listOf(
        Shadow.extract<ShadowSurfaceViewRenderer>(liveView).mirror,
        compositionOverlay.scaleX,
        guideOverlay.scaleX,
        referenceThumbnail.scaleX,
    )

    @Test
    fun theRearCamera_showsAMirror_andTheFrontOne_asSent() {
        val modules = launch()
        connect(modules)
        modules.session.deliver(cameraState(CameraFacing.BACK))
        idle()
        onScreen { binding, _ -> assertEquals(listOf(true, -1f, -1f, -1f), binding.mirrorShown()) }

        // The photographer switches to the front camera, whose video is a mirror already.
        modules.session.deliver(cameraState(CameraFacing.FRONT))
        idle()
        onScreen { binding, _ -> assertEquals(listOf(false, 1f, 1f, 1f), binding.mirrorShown()) }
    }

    @Test
    fun theMirroredReferencePhoto_staysAMirror_shownEnlargedAndWhileItChangesSize() {
        val modules = launch()
        connect(modules)
        modules.session.deliver(cameraState(CameraFacing.BACK))
        modules.mirror.setGuide(guide(withReference = false))
        showReference(modules)
        var small = 0
        var left = 0f
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            assertEquals(-1f, thumbnail.scaleX)
            small = thumbnail.width
            left = thumbnail.translationX
            ShadowChoreographer.setPaused(true)
            thumbnail.performClick()
        }
        idle()
        // The first frame of growing: drawn as small as it was, flipped, with its left side where it was.
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            val large = thumbnail.layoutParams.width
            assertTrue(large > small * 2)
            assertEquals(-small.toFloat() / large, thumbnail.scaleX, 0.001f)
            assertEquals(left, thumbnail.translationX + (large - small) / 2f, 0.5f)
        }
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        onScreen { binding, _ ->
            assertEquals(-1f, binding.referenceThumbnail.scaleX)
            assertEquals(left, binding.referenceThumbnail.translationX)
        }

        // The front camera's video is a mirror already: the photo is as sent, at once.
        modules.session.deliver(cameraState(CameraFacing.FRONT))
        idle()
        onScreen { binding, _ -> assertEquals(1f, binding.referenceThumbnail.scaleX) }
    }

    @Test
    fun aDragOnTheMirroredGuide_movesItTheWayTheFingerGoes() {
        val modules = launch()
        connect(modules)
        modules.session.deliver(cameraState(CameraFacing.BACK))
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        onScreen { binding, _ -> binding.editGuideButton.performClick() }
        idle()

        val moves = mutableListOf<Float>()
        onScreen { binding, _ ->
            binding.guideOverlay.onGesture = { dx, _, _, _ -> moves += dx }
            val container = binding.videoContainer
            val x = container.width / 2f
            val y = container.height / 2f
            val down = SystemClock.uptimeMillis()
            for ((action, at) in listOf(MotionEvent.ACTION_DOWN to x, MotionEvent.ACTION_MOVE to x + 40f)) {
                val event = MotionEvent.obtain(down, down, action, at, y, 0)
                container.dispatchTouchEvent(event)
                event.recycle()
            }
        }

        // To the right on this mirror is to the photographer's left: the guide's x goes down.
        assertTrue(moves.toString(), moves.isNotEmpty() && moves.sum() < 0f)
    }

    // ---- The photographer's reference photo (FR-3.9) -------------------------------------------------------

    private fun showReference(modules: TestAppContainer, id: String = "guide-1"): Bitmap {
        val photo = bitmap(60, 80)
        modules.reference.set(GuideReference(id, photo))
        idle()
        return photo
    }

    @Test
    fun theReferencePhoto_showsWithItsGuide_aTapEnlargesIt_andItStaysWhileTheGuideIsHidden() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.referenceThumbnail.visibility) }

        val photo = showReference(modules)
        val small = onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.referenceThumbnail.visibility)
            assertSame(photo, (binding.referenceThumbnail.drawable as BitmapDrawable).bitmap)
            binding.referenceThumbnail.layoutParams.width
        }

        onScreen { binding, _ -> binding.referenceThumbnail.performClick() }
        idle()
        onScreen { binding, _ -> assertTrue(binding.referenceThumbnail.layoutParams.width > small) }

        // The photographer hides the guide: the thumbnail stays, as on their phone.
        modules.mirror.update(final = true) { it.copy(visible = false) }
        idle()
        onScreen { binding, _ -> assertEquals(View.VISIBLE, binding.referenceThumbnail.visibility) }
    }

    @Test
    fun theReferencePhoto_goesWhenItsGuideIsReplacedOrRemoved() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        showReference(modules)

        // Another guide before its own reference arrives: the old photo is not its pose.
        modules.mirror.setGuide(guide(withReference = false).copy(id = "guide-2"))
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.referenceThumbnail.visibility) }

        showReference(modules, id = "guide-2")
        onScreen { binding, _ -> assertEquals(View.VISIBLE, binding.referenceThumbnail.visibility) }
        modules.mirror.setGuide(null)
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.referenceThumbnail.visibility) }
    }

    @Test
    fun theReferenceThumbnail_movesToAnotherCorner_onThisPhoneOnly() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        showReference(modules)
        val sent = modules.session.sent.size

        onScreen { binding, _ ->
            assertTrue(
                ViewCompat.performAccessibilityAction(binding.referenceThumbnail, R.id.move_reference_top_right, null),
            )
        }
        idle()
        val top = onScreen { binding, _ ->
            assertTrue(binding.referenceThumbnail.translationX > binding.videoContainer.width / 2f)
            binding.referenceThumbnail.translationY
        }

        // A drag down drops it into the corner below, above the zoom row.
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            val far = ViewConfiguration.get(thumbnail.context).scaledTouchSlop * 4f + binding.videoContainer.height
            drag(thumbnail, 0f, far)
        }
        idle()
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            assertTrue(thumbnail.translationX > binding.videoContainer.width / 2f)
            assertTrue(thumbnail.translationY > top)
            val zoomRowTop = binding.zoomRow.top + binding.zoomRow.translationY - binding.videoContainer.top
            assertTrue(thumbnail.translationY + thumbnail.height <= zoomRowTop)
        }
        // Nothing goes to the photographer: each phone keeps its own corner.
        assertEquals(sent, modules.session.sent.size)
    }

    @Test
    fun theReferenceThumbnail_hidesAtTheVideosSide_andComesBackAtATap_onThisPhoneOnly() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        showReference(modules)
        val sent = modules.session.sent.size

        // A flick to the left, from its corner at the top left.
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            drag(thumbnail, -thumbnail.width - ViewConfiguration.get(thumbnail.context).scaledTouchSlop * 4f, 0f)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            val peek = (REFERENCE_PEEK_DP * thumbnail.resources.displayMetrics.density).roundToInt()
            // Only its peek is in the video, see-through, under a handle a touch target wide.
            assertEquals((peek - thumbnail.width).toFloat(), thumbnail.translationX)
            assertEquals(REFERENCE_HIDDEN_ALPHA, thumbnail.alpha)
            assertTrue(binding.referenceHandle.isShown)
            assertEquals(0f, binding.referenceHandle.translationX)
            // A tap on the handle is a tap on the strip.
            drag(binding.referenceHandle, 0f, 0f)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        onScreen { binding, _ ->
            assertTrue(binding.referenceThumbnail.translationX > 0f)
            assertEquals(1f, binding.referenceThumbnail.alpha)
            assertFalse(binding.referenceHandle.isShown)
        }
        // Nothing goes to the photographer: hidden is this phone's alone, like the corner.
        assertEquals(sent, modules.session.sent.size)
    }

    @Test
    fun theReferenceThumbnail_hiddenWhileEnlarged_leavesTheSmallStrip_andComesBackSmall() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        showReference(modules)
        var small = 0 to 0
        onScreen { binding, _ ->
            small = binding.referenceThumbnail.width to binding.referenceThumbnail.height
            binding.referenceThumbnail.performClick()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))

        // Enlarged in the top-left corner, dragged to the left until most of it is out of the video.
        onScreen { binding, _ ->
            val thumbnail = binding.referenceThumbnail
            assertTrue(thumbnail.width > small.first * 2)
            drag(thumbnail, -thumbnail.width - ViewConfiguration.get(thumbnail.context).scaledTouchSlop * 4f, 0f)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        onScreen { binding, fragment ->
            val thumbnail = binding.referenceThumbnail
            val peek = (REFERENCE_PEEK_DP * thumbnail.resources.displayMetrics.density).roundToInt()
            // What stays is the small thumbnail's strip, and TalkBack hears a hidden thumbnail.
            assertEquals(small, thumbnail.width to thumbnail.height)
            // Still the rear camera's mirror, like the video, at its full size.
            assertEquals(-1f, thumbnail.scaleX)
            assertEquals(1f, thumbnail.scaleY)
            assertEquals((peek - small.first).toFloat(), thumbnail.translationX)
            assertEquals(REFERENCE_HIDDEN_ALPHA, thumbnail.alpha)
            assertEquals(
                fragment.getString(R.string.reference_hidden),
                thumbnail.createAccessibilityNodeInfo().stateDescription?.toString(),
            )
            drag(binding.referenceHandle, 0f, 0f)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        onScreen { binding, _ ->
            assertEquals(small, binding.referenceThumbnail.width to binding.referenceThumbnail.height)
            assertEquals(1f, binding.referenceThumbnail.alpha)
            assertFalse(binding.referenceHandle.isShown)
        }
    }

    private fun drag(view: View, dx: Float, dy: Float) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float, y: Float, at: Long) =
            MotionEvent.obtain(down, at, action, x, y, 0).also { view.dispatchTouchEvent(it) }.recycle()
        event(MotionEvent.ACTION_DOWN, 5f, 5f, down)
        event(MotionEvent.ACTION_MOVE, 5f + dx / 2, 5f + dy / 2, down + 16)
        event(MotionEvent.ACTION_MOVE, 5f + dx, 5f + dy, down + 32)
        event(MotionEvent.ACTION_UP, 5f + dx, 5f + dy, down + 48)
    }

    @Test
    fun guideArrives_showsTheToggleOff_andATapTurnsEditingOn() {
        val modules = launch()
        connect(modules)

        modules.mirror.setGuide(guide(withReference = false))
        idle()

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.editGuideButton.visibility)
            assertTrue(binding.editGuideButton.isEnabled)
            // One person icon, unlit while a pinch zooms, and the guide plain (#271).
            assertFalse(binding.editGuideButton.isChecked)
            assertEquals(R.drawable.ic_guide_mode, shadowOf(binding.editGuideButton.plainIcon).createdFromResId)
            assertEquals(color(R.color.pix_text), binding.editGuideButton.shownIconColor())
            assertFalse(binding.guideOverlay.editable)
            assertFalse(binding.guideOverlay.filled)
            binding.editGuideButton.performClick()
        }
        idle()

        onScreen { binding, _ ->
            // Lit in the guide color, with the guide's inside tinted, while drags and pinches move it.
            assertTrue(binding.editGuideButton.isChecked)
            assertEquals(R.drawable.ic_guide_mode, shadowOf(binding.editGuideButton.plainIcon).createdFromResId)
            assertEquals(color(R.color.pix_guide), binding.editGuideButton.shownIconColor())
            assertTrue(binding.guideOverlay.editable)
            assertTrue(binding.guideOverlay.filled)
        }

        // The photographer hides the guide: it cannot be edited here until it is shown again (FR-7.2).
        modules.mirror.update(final = true) { it.copy(visible = false) }
        idle()

        onScreen { binding, _ ->
            assertFalse(binding.editGuideButton.isEnabled)
            assertFalse(binding.editGuideButton.isChecked)
            assertFalse(binding.guideOverlay.editable)
            assertFalse(binding.guideOverlay.filled)
        }
    }

    // ---- The shutter's slot, the notice, and Leave (#271) --------------------------------------

    private fun color(id: Int): Int = scenarioContext().getColor(id)

    private fun scenarioContext() = onScreen { _, fragment -> fragment.requireContext() }

    /** The toggle's icon color in the state it is in. */
    private fun MaterialButton.shownIconColor(): Int? =
        iconTint?.getColorForState(drawableState, 0)

    @Test
    // At the Galaxy S22's width, where the name fits whole; CameraLayoutTest checks a name that does not.
    @Config(qualifiers = "w360dp-h705dp-xxhdpi")
    fun theLineAboveTheShutter_isEmptyUntilConnected_thenSaysWhoseCameraItIsAndThatItIsLive() {
        val modules = launch()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.cameraCaption.visibility) }

        connect(modules, name = "Test Account 1")

        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.cameraCaption.visibility)
            assertEquals(
                string(R.string.subject_title, "Test Account 1"),
                binding.cameraCaptionTitle.text.toString(),
            )
            assertEquals(string(R.string.subject_live), binding.cameraCaptionStatus.text.toString())
            assertTrue(binding.cameraCaptionStatus.isShown)
            assertTrue(binding.cameraCaptionDot.isShown)
            assertFalse(binding.cameraCaptionSpinner.isShown)
            // One line, the name and "Live" with a dot between them, whole above the shutter (#314).
            assertEquals(LinearLayout.HORIZONTAL, binding.cameraCaption.orientation)
            assertTrue(binding.cameraCaptionSeparator.isShown)
            val caption = IntArray(2).also { binding.cameraCaption.getLocationInWindow(it) }
            val shutter = IntArray(2).also { binding.shutterButton.getLocationInWindow(it) }
            assertTrue(caption[1] + binding.cameraCaption.height <= shutter[1])
            // The line names the camera for the whole session, so no notice says it as well.
            assertFalse(binding.toggleLabel.isShown)
        }

        // The link drops: the video says so, and the line is gone until it is back.
        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.cameraCaption.visibility) }
    }

    // ---- This phone's shutter (FR-7.8, #314) ---------------------------------------------------------------------

    private fun presses(modules: TestAppContainer) = modules.session.sent.count { it is SessionMessage.ShutterPress }

    /** The answer to the press sent last, which names that press. */
    private fun answer(modules: TestAppContainer, taken: Boolean, timer: Boolean = false) {
        val press = modules.session.sent.filterIsInstance<SessionMessage.ShutterPress>().last().press
        modules.session.deliver(SessionMessage.ShutterAnswer(taken, press, timer))
    }

    /** Connected, with the photographer's video showing: the video's state is looked at every 500 ms. */
    private fun connectWithVideo(modules: TestAppContainer, name: String = "Dongje") {
        connect(modules, name = name)
        waitMs(600)
    }

    @Test
    fun theShutter_isDimmedUntilThePhotographersVideoShows() {
        val modules = launch()
        onScreen { binding, _ ->
            assertTrue(binding.shutterButton.isShown)
            assertFalse(binding.shutterButton.isEnabled)
        }

        // Connected, and no frame yet: "Connecting…" over a dark video.
        connect(modules, firstFrame = false)
        waitMs(600)
        onScreen { binding, _ -> assertFalse(binding.shutterButton.isEnabled) }

        modules.video.deliverFrame()
        waitMs(600)
        onScreen { binding, _ ->
            assertTrue(binding.shutterButton.isEnabled)
            assertEquals(
                string(R.string.subject_shutter, "Dongje"),
                binding.shutterButton.contentDescription.toString(),
            )
        }
    }

    @Test
    fun aPressOfTheShutter_asksThePhotographersPhoneOnce_andIsHeldUntilTheAnswer() {
        val modules = launch()
        connectWithVideo(modules)

        onScreen { binding, _ -> binding.shutterButton.performClick() }
        idle()
        assertEquals(1, presses(modules))
        // Held: a second tap asks nothing.
        onScreen { binding, _ ->
            assertFalse(binding.shutterButton.isEnabled)
            binding.shutterButton.performClick()
        }
        idle()
        assertEquals(1, presses(modules))

        answer(modules, taken = true)
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.shutterButton.isEnabled)
            // The photo says itself: the blink, "Receiving photo…", and the saved photo. No line of its own.
            assertFalse(binding.toggleLabel.isShown)
        }
    }

    @Test
    fun aPressThatTookNoPhoto_isSaidWhereTheLinesShow_andFreesTheShutter() {
        val modules = launch()
        connectWithVideo(modules)
        onScreen { binding, _ -> binding.shutterButton.performClick() }
        idle()

        answer(modules, taken = false)
        idle()

        assertEquals(string(R.string.subject_shutter_refused) to true, label())
        onScreen { binding, _ -> assertTrue(binding.shutterButton.isEnabled) }
        waitMs(SubjectFragment.LABEL_MS + 100)
        assertFalse(label().second)
    }

    @Test
    fun aPressWithoutAnAnswer_isSaidAfterItsWait_andFreesTheShutter() {
        val modules = launch()
        connectWithVideo(modules)
        onScreen { binding, _ -> binding.shutterButton.performClick() }
        idle()

        waitMs(SubjectViewModel.SHUTTER_WAIT_MS + 100)

        assertEquals(string(R.string.subject_shutter_refused) to true, label())
        onScreen { binding, _ -> assertTrue(binding.shutterButton.isEnabled) }
    }

    @Test
    fun whileTheLinkReconnects_theShutterIsDimmed_andTakesNoPress() {
        val modules = launch()
        connectWithVideo(modules)

        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        waitMs(600)

        onScreen { binding, _ ->
            assertFalse(binding.shutterButton.isEnabled)
            binding.shutterButton.performClick()
        }
        idle()
        assertEquals(0, presses(modules))
    }

    @Test
    fun whileTheTimerCountsDown_theShutterCancelsIt_andIsNamedSo_withNoLineOfItsOwn() {
        val modules = launch()
        connectWithVideo(modules)

        modules.session.deliver(SessionMessage.Countdown(3))
        idle()
        onScreen { binding, _ ->
            // Either phone's shutter stops the countdown (FR-7.8). The tutorial's tip says so, not a line here:
            // under the number is whose photo it is, and nothing else.
            assertTrue(binding.shutterButton.isEnabled)
            assertEquals(
                string(R.string.shutter_cancel_timer),
                binding.shutterButton.contentDescription.toString(),
            )
            assertEquals(2, binding.countdownPanel.childCount)
            binding.shutterButton.performClick()
        }
        idle()
        assertEquals(
            listOf(true),
            modules.session.sent.filterIsInstance<SessionMessage.ShutterPress>().map { it.cancel },
        )

        // Stopped: the press is answered as the timer's, and the countdown goes with its 0.
        answer(modules, taken = false, timer = true)
        modules.session.deliver(SessionMessage.Countdown(0))
        idle()
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.countdownPanel.visibility)
            assertTrue(binding.shutterButton.isEnabled)
            assertEquals(
                string(R.string.subject_shutter, "Dongje"),
                binding.shutterButton.contentDescription.toString(),
            )
            // No "Can't take a photo right now": nothing was asked to be taken.
            assertFalse(binding.toggleLabel.isShown)
        }
    }

    // ---- This phone starts AI poses (FR-6.15, #276) --------------------------------------------------------------

    /** This phone picks a situation, as its sheet does, and the photographer's phone answers that request. */
    private fun askAndAnswer(modules: TestAppContainer, started: Boolean, asking: Boolean = false) {
        onScreen { _, fragment ->
            fragment.requireActivity().viewModel<SessionViewModel>().askForPoses(Situation.FULL_BODY)
        }
        idle()
        val request = modules.session.sent.filterIsInstance<SessionMessage.PoseStart>().last().request
        modules.session.deliver(SessionMessage.PoseStartAnswer(started, request, asking))
        idle()
    }

    @Test
    fun anAnswerThatComesUnderAddAPoseGuide_isSaidOnceTheSheetIsGone() {
        val (modules, nav) = launchWithNav()
        connectWithVideo(modules)
        // The photographer reads the AI notice; meanwhile this phone's user opens the sheet again.
        askAndAnswer(modules, started = false, asking = true)
        waitMs(SubjectFragment.LABEL_MS + 100)
        onScreen { binding, _ -> binding.guideButton.performClick() }
        idle()
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)

        val request = modules.session.sent.filterIsInstance<SessionMessage.PoseStart>().last().request
        modules.session.deliver(SessionMessage.PoseStartAnswer(started = true, request = request))
        idle()
        // Under the sheet the line would come and go unseen: it waits.
        assertFalse(label().second)
        waitMs(SubjectFragment.LABEL_MS + 100)

        nav.popBackStack()
        idle()
        assertEquals(string(R.string.subject_poses_started, "Dongje") to true, label())
    }

    @Test
    fun aRefusedPressThatIsAnsweredUnderAddAPoseGuide_isSaidOnceTheSheetIsGone() {
        val (modules, nav) = launchWithNav()
        connectWithVideo(modules)
        onScreen { binding, _ -> binding.shutterButton.performClick() }
        idle()
        onScreen { binding, _ -> binding.guideButton.performClick() }
        idle()
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)

        answer(modules, taken = false)
        idle()
        assertFalse(label().second)

        nav.popBackStack()
        idle()
        assertEquals(string(R.string.subject_shutter_refused) to true, label())
    }

    @Test
    fun aRefusedPressThatIsAnsweredUnderLeaveTheSession_isSaidOnceTheQuestionIsGone() {
        val modules = launch()
        connectWithVideo(modules)
        onScreen { binding, _ -> binding.shutterButton.performClick() }
        idle()
        onScreen { binding, _ -> binding.leaveButton.performClick() }
        idle()

        answer(modules, taken = false)
        idle()
        // The question covers the line's place, and a touch on it would answer the question.
        assertFalse(label().second)

        leaveQuestion().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(string(R.string.subject_shutter_refused) to true, label())
    }

    @Test
    fun anAnswerThatComesUnderTheTutorialsTips_isSaidOnceTheyAreClosed() {
        val modules = launch()
        connectWithVideo(modules)
        onScreen { _, fragment -> TutorialFragment.show(fragment, TutorialScreen.SUBJECT_VIEW) }
        idle()

        askAndAnswer(modules, started = true)
        assertFalse(label().second)

        onScreen { _, fragment -> TutorialFragment.over(fragment)!!.dismiss() }
        idle()
        assertEquals(string(R.string.subject_poses_started, "Dongje") to true, label())
    }

    @Test
    fun aSituationThePhotographersPhoneTookUp_saysWhoTakesTheScenePhoto() {
        val modules = launch()
        connectWithVideo(modules)

        askAndAnswer(modules, started = true)

        assertEquals(string(R.string.subject_poses_started, "Dongje") to true, label())
        waitMs(SubjectFragment.LABEL_MS + 100)
        assertFalse(label().second)
    }

    @Test
    fun aSituationThePhotographersPhoneCouldNotTakeUp_saysSo() {
        val modules = launch()
        connectWithVideo(modules)

        askAndAnswer(modules, started = false)

        assertEquals(string(R.string.subject_poses_refused) to true, label())
    }

    @Test
    fun whileThePhotographerIsAskedToAgree_theSubjectIsToldTheyWait() {
        val modules = launch()
        connectWithVideo(modules)

        askAndAnswer(modules, started = false, asking = true)

        assertEquals(string(R.string.subject_poses_asking, "Dongje") to true, label())
    }

    @Test
    fun aSituationThatGetsNoAnswer_saysAfterItsWaitThatThePosesCannotStart() {
        val modules = launch()
        connectWithVideo(modules)
        onScreen { _, fragment ->
            fragment.requireActivity().viewModel<SessionViewModel>().askForPoses(Situation.FULL_BODY)
        }
        idle()
        assertFalse(label().second)

        waitMs(SessionViewModel.POSE_START_WAIT_MS + 100)

        assertEquals(string(R.string.subject_poses_refused) to true, label())
    }

    @Test
    fun leave_isRed_asEndSessionIsOnCamera() {
        launch()

        // The danger style's text; its outline is an app attribute, which this test's host does not apply.
        onScreen { binding, _ -> assertEquals(color(R.color.pix_danger_text), binding.leaveButton.currentTextColor) }
    }

    @Test
    fun theButtonsOverTheVideo_areOnGlass_theToggleTintedWhileOn() {
        launch()

        onScreen { binding, _ ->
            for (button in listOf(binding.tipButton, binding.editGuideButton)) {
                val name = button.resources.getResourceEntryName(button.id)
                assertEquals(name, color(R.color.pix_glass), button.backgroundTintList?.defaultColor)
                assertEquals(name, color(R.color.pix_glass_stroke), button.strokeColor?.defaultColor)
            }
            val on = intArrayOf(android.R.attr.state_checked)
            assertEquals(
                color(R.color.pix_guide_glass),
                binding.editGuideButton.backgroundTintList?.getColorForState(on, 0),
            )
        }
    }

    @Test
    fun theGrid_isOnByDefault_andOffWhenKeptOff() {
        val modules = launch()
        connect(modules)
        onScreen { binding, _ -> assertTrue(binding.compositionOverlay.showGrid) }

        runBlocking { modules.appSettings.setShowGrid(false) }
        idle()

        onScreen { binding, _ ->
            assertFalse(binding.compositionOverlay.showGrid)
            // Subject view has no horizon bar.
            assertEquals(View.VISIBLE, binding.compositionOverlay.visibility)
        }
    }

    @Test
    fun theMenusFlash_sendsARequest_andTheMenuStaysOpen() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ -> binding.cameraMenuButton.performClick() }
        idle()
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.cameraMenu.visibility)
            assertTrue(binding.cameraMenuButton.isActivated)
            binding.menuFlash.performClick()
        }
        idle()

        assertTrue(modules.session.sent.any { it == SessionMessage.FlashSet(FlashMode.AUTO) })
        onScreen { binding, _ -> assertEquals(View.VISIBLE, binding.cameraMenu.visibility) }
    }

    // ---- The photographer's timer in the menu and beside its button (#318) ----------------------------------------

    private fun state(modules: TestAppContainer, timer: Int = 0, flash: FlashMode = FlashMode.OFF) {
        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, flash = flash, timer = timer),
        )
        idle()
    }

    @Test
    fun theMenusTimer_isBetweenFlashAndGrid_andSetsThePhotographersTimer() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)
        onScreen { binding, _ -> binding.cameraMenuButton.performClick() }
        idle()

        onScreen { binding, _ ->
            // Camera's order: Flash, Timer, Grid.
            val items = (0 until binding.cameraMenu.childCount).map { binding.cameraMenu.getChildAt(it) }
            assertEquals(items.indexOf(binding.menuFlash) + 1, items.indexOf(binding.menuTimer))
            assertEquals(items.indexOf(binding.menuTimer) + 1, items.indexOf(binding.menuGrid))
            assertTrue(binding.menuTimer.isShown)
            assertFalse(binding.menuTimer.isActivated)
            assertEquals(string(R.string.timer_off), binding.menuTimer.contentDescription.toString())
            assertEquals(R.drawable.ic_menu_timer_off, binding.menuTimerIcon.tag)
            binding.menuTimer.performClick()
        }
        idle()

        assertEquals(listOf(3), modules.session.sent.filterIsInstance<SessionMessage.TimerSet>().map { it.seconds })
        onScreen { binding, _ ->
            // The menu stays open, and its item says what the timer is now.
            assertEquals(View.VISIBLE, binding.cameraMenu.visibility)
            assertTrue(binding.menuTimer.isActivated)
            assertEquals(string(R.string.timer_seconds, 3), binding.menuTimer.contentDescription.toString())
            assertEquals(R.drawable.ic_menu_timer_3, binding.menuTimerIcon.tag)
        }
        state(modules, timer = 10)
        onScreen { binding, _ -> assertEquals(R.drawable.ic_menu_timer_10, binding.menuTimerIcon.tag) }
    }

    @Test
    fun theTimer_andTheFlash_showBesideTheMenusButtonWhileOn_asOnCamera_andATapOpensTheMenu() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)
        onScreen { binding, _ ->
            assertFalse(binding.timerIndicator.isShown)
            assertFalse(binding.flashIndicator.isShown)
        }

        state(modules, timer = 3)
        onScreen { binding, _ ->
            assertTrue(binding.timerIndicator.isShown)
            assertEquals(R.drawable.ic_timer_3, binding.timerIndicator.tag)
            assertFalse(binding.flashIndicator.isShown)
        }
        state(modules, timer = 10, flash = FlashMode.AUTO)
        onScreen { binding, _ ->
            assertEquals(R.drawable.ic_timer_10, binding.timerIndicator.tag)
            assertTrue(binding.flashIndicator.isShown)
            assertEquals(R.drawable.ic_flash_auto, binding.flashIndicator.tag)
            // The flash first, then the timer, as in Camera's bar.
            val flash = IntArray(2).also { binding.flashIndicator.getLocationInWindow(it) }
            val timer = IntArray(2).also { binding.timerIndicator.getLocationInWindow(it) }
            assertTrue(flash[0] + binding.flashIndicator.width <= timer[0])
            // The icons are 16 dp: what takes the tap is an area of its own over them (NFR-16).
            assertFalse(binding.timerIndicator.isClickable)
            assertTrue(binding.indicatorsTouch.isShown)
            binding.indicatorsTouch.performClick()
        }
        idle()
        onScreen { binding, _ -> assertEquals(View.VISIBLE, binding.cameraMenu.visibility) }

        state(modules)
        onScreen { binding, _ ->
            assertFalse(binding.timerIndicator.isShown)
            assertFalse(binding.flashIndicator.isShown)
            assertFalse(binding.indicatorsTouch.isShown)
        }
    }

    @Test
    fun withTheTimerOn_theShutterIsNamedForIt_andTheMenusTimerWaitsWhileItCountsDown() {
        val modules = launch()
        connectWithVideo(modules)
        state(modules, timer = 3)
        onScreen { binding, _ ->
            assertEquals(
                string(R.string.subject_shutter_timer, "Dongje", 3),
                binding.shutterButton.contentDescription.toString(),
            )
            assertTrue(binding.menuTimer.isEnabled)
        }

        modules.session.deliver(SessionMessage.Countdown(3))
        idle()
        onScreen { binding, _ ->
            // As Camera's own Timer waits while it counts down (FR-1.17).
            assertFalse(binding.menuTimer.isEnabled)
            binding.menuTimer.performClick()
        }
        idle()
        assertTrue(modules.session.sent.none { it is SessionMessage.TimerSet })
    }

    @Test
    fun whatThePhotographerSetTheTimerTo_isSaidWhereTheLinesShow() {
        val modules = launch()
        connectWithVideo(modules)

        modules.session.deliver(SessionMessage.PhotographerDid("timer", seconds = 3))
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.toggleLabel.isShown)
            assertEquals(string(R.string.remote_timer_notice, "Dongje", 3), binding.toggleLabel.text.toString())
        }

        modules.session.deliver(SessionMessage.PhotographerDid("timer", seconds = 0))
        idle()
        onScreen { binding, _ ->
            assertEquals(string(R.string.remote_timer_off_notice, "Dongje"), binding.toggleLabel.text.toString())
        }
    }

    @Test
    fun theMenu_closesAtBack_andAtItsButtonAgain() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ -> binding.cameraMenuButton.performClick() }
        idle()
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.cameraMenu.visibility)
            binding.cameraMenuButton.performClick()
        }
        idle()
        onScreen { binding, _ -> binding.cameraMenuButton.performClick() }
        idle()

        onScreen { binding, _ -> assertEquals(View.GONE, binding.cameraMenu.visibility) }
    }

    // ---- A notice (#298) -------------------------------------------------------------------------------------------

    @Test
    fun aNotice_takesTheZoomRowsPlace_aboveTheLineThatNamesTheCamera() {
        val modules = launch()
        connect(modules)

        onScreen { _, fragment -> fragment.showNoticeOnScreen("A notice") }
        idle()

        onScreen { binding, fragment ->
            assertEquals(binding.noticeLine, fragment.noticeAnchor)
            val notice = checkNotNull(shownNoticeView())
            assertNoticeInTheZoomRowsPlace(notice, binding.zoomRow, binding.zoomReadout, binding.noticeLine)
            assertTrue(binding.cameraCaption.isShown)
            assertTrue("above the camera's name", windowBox(notice).bottom <= windowBox(binding.cameraCaption).top)
        }
    }

    // ---- The photographer's timer (FR-1.17) ----------------------------------------------------------------------

    @Test
    fun thePhotographersCountdown_showsEachSecondWithTheirName_andGoesAtItsEnd() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)
        onScreen { binding, _ -> assertEquals(View.GONE, binding.countdownPanel.visibility) }

        modules.session.deliver(SessionMessage.Countdown(3))
        idle()
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.countdownPanel.visibility)
            assertEquals("3", binding.countdownNumber.text.toString())
            assertEquals(string(R.string.photographer_timer_label, "Dongje"), binding.countdownLine.text.toString())
            // The photographer's flash waits, as on their phone.
            assertFalse(binding.menuFlash.isEnabled)
        }
        modules.session.deliver(SessionMessage.Countdown(2))
        idle()
        onScreen { binding, _ -> assertEquals("2", binding.countdownNumber.text.toString()) }

        modules.session.deliver(SessionMessage.Countdown(0))
        idle()
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.countdownPanel.visibility)
            assertTrue(binding.menuFlash.isEnabled)
        }
    }

    @Test
    fun thePhotographersCountdown_putsTheTimersRingOnTheShutter_fromEachSecondsPlace_andTheWhiteShutterAfter() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)
        state(modules, timer = 3)

        modules.session.deliver(SessionMessage.Countdown(3))
        idle()
        assertRing(fill = 0f)
        waitMs(500)
        assertRing(fill = 1f / 6)

        modules.session.deliver(SessionMessage.Countdown(2))
        idle()
        assertRing(fill = 1f / 3)

        modules.session.deliver(SessionMessage.Countdown(0))
        idle()
        onScreen { binding, fragment ->
            assertFalse(binding.shutterButton.background is CountdownShutterDrawable)
            assertFalse(checkNotNull(fragment.shutterRing).drawable.filling)
        }
    }

    @Test
    fun aFirstSecondThatComesMidway_putsTheRingAtItsPlaceInTheTimersLength() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)
        state(modules, timer = 10)

        // The seconds before it were lost, as when the link came back during the countdown.
        modules.session.deliver(SessionMessage.Countdown(4))
        idle()

        assertRing(fill = 0.6f)
        // It fills the 0.4 left in the 4 s left.
        waitMs(1_000)
        assertRing(fill = 0.7f)
    }

    /** The shutter shows the timer's ring, filled [fill] now and filling on. */
    private fun assertRing(fill: Float) = onScreen { binding, fragment ->
        val ring = checkNotNull(fragment.shutterRing).drawable
        assertSame(ring, binding.shutterButton.background)
        assertEquals(fill, ring.fill, 0.001f)
        assertTrue(ring.filling)
    }

    // ---- Remote focus, with the brightness beside it (FR-7.7, FR-7.3, #273) -----------------------------------

    private fun touch(view: View, action: Int, x: Float, y: Float, down: Long, at: Long = down) =
        MotionEvent.obtain(down, at, action, x, y, 0).also { view.dispatchTouchEvent(it) }.recycle()

    private fun tap(view: View, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, x, y, down)
        touch(view, MotionEvent.ACTION_UP, x, y, down, down + 16)
    }

    /** How far a finger moves for [steps] of the photographer's 0.5 EV per index (`ExposureBarView.DP_PER_EV`). */
    private fun wayFor(view: View, steps: Int): Float =
        steps * 0.5f * ExposureBarView.DP_PER_EV * view.resources.displayMetrics.density

    private fun focuses(modules: TestAppContainer) = modules.session.sent.filterIsInstance<SessionMessage.FocusSet>()

    private fun brightnesses(modules: TestAppContainer) =
        modules.session.sent.filterIsInstance<SessionMessage.ExposureSet>()

    /** Taps the video a quarter across the image and halfway down it; where that is in the video's area. */
    private fun tapTheImage(): Pair<Float, Float> = onScreen { binding, _ ->
        val area = binding.videoContainer
        val frame = fitViewfinderFrame(area.width.toFloat(), area.height.toFloat())
        val x = frame.left + frame.width * 0.25f
        val y = frame.top + frame.height * 0.5f
        tap(area, x, y)
        x to y
    }

    @Test
    fun aTapOnTheVideo_asksForTheFocusThere_andBringsTheBoxWithTheBrightnessBarBesideIt() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)

        val (x, y) = tapTheImage()
        idle()

        // The rear camera's video is a mirror here: a quarter from its left is three quarters across the
        // photographer's frame.
        val asked = focuses(modules).single()
        assertEquals(0.75f, asked.x, 0.01f)
        assertEquals(0.5f, asked.y, 0.01f)
        onScreen { binding, _ ->
            val density = binding.root.resources.displayMetrics.density
            val ring = binding.focusRing
            val bar = binding.exposureBar
            assertTrue(ring.isShown)
            assertTrue(bar.isShown)
            assertEquals(x, ring.translationX + ring.layoutParams.width / 2f, 1f)
            assertEquals(y, ring.translationY + ring.layoutParams.height / 2f, 1f)
            // As on Camera: on the box's right, its 48 dp touch area reaching 12 dp over the 64 dp box.
            assertEquals(x + 32f * density - 12f * density, bar.translationX, 1f)
            assertEquals(y - bar.height / 2f, bar.translationY, 1f)
        }
    }

    @Test
    fun aDragOnTheBar_sendsTheBrightness_andTheBarAndTheBoxGoAfterAWhileWithoutATouch() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        tapTheImage()
        idle()
        // The tap's answer: the camera is at its base, 0.
        modules.session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, focused = true))
        idle()

        onScreen { binding, _ ->
            val bar = binding.exposureBar
            assertTrue(bar.isShown)
            val down = SystemClock.uptimeMillis()
            val x = bar.width / 2f
            val y = bar.height / 2f
            touch(bar, MotionEvent.ACTION_DOWN, x, y, down)
            touch(bar, MotionEvent.ACTION_MOVE, x, y - wayFor(bar, 2), down, down + 16)
            touch(bar, MotionEvent.ACTION_UP, x, y - wayFor(bar, 2), down, down + 32)
        }
        idle()

        val sent = brightnesses(modules)
        assertEquals(SessionMessage.ExposureSet(2), sent.first())
        assertEquals(SessionMessage.ExposureSet(2, final = true), sent.last())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FocusBrightness.IDLE_MS + 500))
        onScreen { binding, _ ->
            assertFalse(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }
    }

    @Test
    fun aDragOnTheBarBeforeTheTapsAnswer_waitsForIt_andStartsFromTheBrightnessItGave() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        // Left at +1.5 EV by an earlier drag.
        modules.session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3))
        idle()
        tapTheImage()
        idle()

        val down = SystemClock.uptimeMillis()
        var y = 0f
        onScreen { binding, _ ->
            val bar = binding.exposureBar
            assertTrue(bar.isShown)
            y = bar.height / 2f
            touch(bar, MotionEvent.ACTION_DOWN, bar.width / 2f, y, down)
            y -= wayFor(bar, 2)
            touch(bar, MotionEvent.ACTION_MOVE, bar.width / 2f, y, down, down + 16)
        }
        idle()
        assertTrue(brightnesses(modules).isEmpty())

        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 0, focused = true),
        )
        idle()
        onScreen { binding, _ ->
            val bar = binding.exposureBar
            y -= 1f
            touch(bar, MotionEvent.ACTION_MOVE, bar.width / 2f, y, down, down + 32)
            y -= wayFor(bar, 2)
            touch(bar, MotionEvent.ACTION_MOVE, bar.width / 2f, y, down, down + 48)
            touch(bar, MotionEvent.ACTION_UP, bar.width / 2f, y, down, down + 64)
        }
        idle()
        assertEquals(SessionMessage.ExposureSet(2, final = true), brightnesses(modules).last())
        assertTrue(brightnesses(modules).none { it.index > 2 })
    }

    @Test
    fun aDragOnTheVideoWithTheBarUp_beforeTheTapsAnswer_waitsForItToo() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        modules.session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3))
        idle()
        tapTheImage()
        idle()
        onScreen { binding, _ -> assertTrue(binding.exposureBar.isShown) }

        // A drag on the video, away from the bar, before the tap is answered.
        val down = SystemClock.uptimeMillis() + 500
        var y = 0f
        onScreen { binding, _ ->
            val area = binding.videoContainer
            val x = area.width * 0.8f
            y = area.height * 0.8f
            touch(area, MotionEvent.ACTION_DOWN, x, y, down)
            y -= ViewConfiguration.get(area.context).scaledTouchSlop + 2f
            touch(area, MotionEvent.ACTION_MOVE, x, y, down, down + 16)
            y -= wayFor(area, 2)
            touch(area, MotionEvent.ACTION_MOVE, x, y, down, down + 32)
        }
        idle()
        // The bar was up, so the drag is no second tap; and nothing is sent from the brightness before the tap.
        assertEquals(1, focuses(modules).size)
        assertTrue(brightnesses(modules).isEmpty())

        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 0, focused = true),
        )
        idle()
        onScreen { binding, _ ->
            val area = binding.videoContainer
            val x = area.width * 0.8f
            y -= 1f
            touch(area, MotionEvent.ACTION_MOVE, x, y, down, down + 48)
            y -= wayFor(area, 2)
            touch(area, MotionEvent.ACTION_MOVE, x, y, down, down + 64)
            touch(area, MotionEvent.ACTION_UP, x, y, down, down + 80)
        }
        idle()
        assertEquals(SessionMessage.ExposureSet(2, final = true), brightnesses(modules).last())
        assertTrue(brightnesses(modules).none { it.index > 2 })
    }

    @Test
    fun theBar_isGoneAfterTheScreensViewIsMadeAgain() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        tapTheImage()
        idle()
        onScreen { binding, _ -> assertTrue(binding.exposureBar.isShown) }

        // As when another screen, such as Settings, was in front: the view goes and is made again.
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        onScreen { binding, fragment ->
            assertFalse(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }
        // And it stays gone: no bar is left up without its time running.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FocusBrightness.IDLE_MS + 500))
        onScreen { binding, _ -> assertFalse(binding.exposureBar.isShown) }
    }

    @Test
    fun aTapBesideTheImage_orInGuideEditMode_asksForNothing() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        // A square image leaves bands of the video's area above and below it.
        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, aspect = AspectRatio.R1_1),
        )
        idle()
        onScreen { binding, _ ->
            val area = binding.videoContainer
            val frame = fitViewfinderFrame(area.width.toFloat(), area.height.toFloat(), AspectRatio.R1_1.frameAspect)
            assertTrue("a band above the image", frame.top > 16f)
            tap(area, area.width / 2f, frame.top / 2f)
        }
        idle()
        assertTrue(focuses(modules).isEmpty())
        onScreen { binding, _ -> assertFalse(binding.exposureBar.isShown) }

        // Guide-edit mode: the touch is the guide's.
        modules.mirror.setGuide(guide())
        idle()
        onScreen { binding, _ -> binding.editGuideButton.performClick() }
        idle()
        onScreen { binding, _ ->
            val area = binding.videoContainer
            tap(area, area.width / 2f, area.height / 2f)
        }
        idle()
        assertTrue(focuses(modules).isEmpty())
        onScreen { binding, _ -> assertFalse(binding.exposureBar.isShown) }
    }

    private fun label(): Pair<String, Boolean> =
        onScreen { binding, _ -> binding.toggleLabel.text.toString() to binding.toggleLabel.isShown }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun whatThePhotographerDid_isALineWithTheirName_whereTheLabelsShow_forItsTime() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true, focuses = true)

        val lines = listOf(
            SessionMessage.PhotographerDid("zoom", zoom = 2f) to string(R.string.remote_zoom_notice, "Dongje", "2"),
            SessionMessage.PhotographerDid("brightness", exposure = 2) to
                string(R.string.remote_brightness_notice, "Dongje", ExposureScale.formatEv(2, 0.5f)),
            SessionMessage.PhotographerDid("flash", flash = FlashMode.ON) to
                string(R.string.remote_flash_on_notice, "Dongje"),
            SessionMessage.PhotographerDid("flash", flash = FlashMode.AUTO) to
                string(R.string.remote_flash_auto_notice, "Dongje"),
            SessionMessage.PhotographerDid("flash", flash = FlashMode.OFF) to
                string(R.string.remote_flash_off_notice, "Dongje"),
            SessionMessage.PhotographerDid("guide.move") to string(R.string.remote_guide_move_notice, "Dongje"),
            SessionMessage.PhotographerDid("guide") to string(R.string.remote_guide_notice, "Dongje"),
            SessionMessage.Focused(0.5f, 0.5f) to string(R.string.remote_focus_notice, "Dongje"),
        )
        for ((message, text) in lines) {
            modules.session.deliver(message)
            idle()
            assertEquals(text to true, label())
        }

        // The last one, the focus, for its shorter time; any other for a label's.
        waitMs(SessionViewModel.FOCUS_NOTICE_MS + 100)
        assertFalse(label().second)
        modules.session.deliver(SessionMessage.PhotographerDid("guide.move"))
        idle()
        waitMs(SubjectFragment.LABEL_MS - 100)
        assertTrue(label().second)
        waitMs(200)
        assertFalse(label().second)
    }

    @Test
    fun thePhotographersPinch_saysWhoIsZooming_untilItsRatiosLineTakesItsPlace() {
        val modules = launch()
        connect(modules)
        val zooming = string(R.string.zoom_held_label, "Dongje")

        modules.session.deliver(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER))
        idle()
        assertEquals(zooming to true, label())
        // As long as the pinch, beyond a label's own time.
        waitMs(SubjectFragment.LABEL_MS + 2_000)
        assertEquals(zooming to true, label())

        // The pinch's end: the ratio first, then the zoom free again.
        modules.session.deliver(SessionMessage.PhotographerDid("zoom", zoom = 2f))
        modules.session.deliver(SessionMessage.ZoomHoldUpdate(null))
        idle()
        assertEquals(string(R.string.remote_zoom_notice, "Dongje", "2") to true, label())
        waitMs(SubjectFragment.LABEL_MS + 100)
        assertFalse(label().second)
    }

    @Test
    fun anotherLabelDuringThePhotographersPinch_keepsItsOwnTime_andThenWhoIsZoomingIsBack() {
        val modules = launch()
        connect(modules, hasFlash = true)
        val zooming = string(R.string.zoom_held_label, "Dongje")
        modules.session.deliver(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER))
        idle()

        modules.session.deliver(SessionMessage.PhotographerDid("flash", flash = FlashMode.ON))
        idle()
        assertEquals(string(R.string.remote_flash_on_notice, "Dongje") to true, label())
        waitMs(SubjectFragment.LABEL_MS + 100)

        assertEquals(zooming to true, label())
        // And it still goes when the pinch ends.
        modules.session.deliver(SessionMessage.ZoomHoldUpdate(null))
        idle()
        assertFalse(label().second)
    }

    @Test
    fun aPinchOfThePhotographersThatEndsWithoutARatio_takesItsLineDown() {
        val modules = launch()
        connect(modules)

        modules.session.deliver(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER))
        idle()
        assertTrue(label().second)
        modules.session.deliver(SessionMessage.ZoomHoldUpdate(null))
        idle()

        assertFalse(label().second)
    }

    @Test
    fun thePhotographersOwnFocus_showsTheBoxAloneAtItsPlaceOnTheMirror_andTakesDownThisPhonesBar() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)

        // The display's frames held, so the box is seen before its animation runs to its end.
        ShadowChoreographer.setPaused(true)
        try {
            modules.session.deliver(SessionMessage.Focused(0.25f, 0.5f))
            idle()
            onScreen { binding, _ ->
                val area = binding.videoContainer
                val frame = fitViewfinderFrame(area.width.toFloat(), area.height.toFloat())
                val ring = binding.focusRing
                assertTrue(ring.isShown)
                // A quarter across the photographer's frame is three quarters across its mirror.
                assertEquals(frame.left + frame.width * 0.75f, ring.translationX + ring.layoutParams.width / 2f, 1f)
                assertEquals(frame.top + frame.height * 0.5f, ring.translationY + ring.layoutParams.height / 2f, 1f)
                assertFalse(binding.exposureBar.isShown)
            }
        } finally {
            ShadowChoreographer.setPaused(false)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
        onScreen { binding, _ -> assertFalse(binding.focusRing.isShown) }

        // This phone's own bar up: held, nothing moves under the finger; let go, it goes at the next one.
        val (x, _) = tapTheImage()
        idle()
        val down = SystemClock.uptimeMillis()
        onScreen { binding, _ -> touch(binding.exposureBar, MotionEvent.ACTION_DOWN, 24f, 64f, down) }
        modules.session.deliver(SessionMessage.Focused(0.5f, 0.2f))
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.exposureBar.isShown)
            assertEquals(x, binding.focusRing.translationX + binding.focusRing.layoutParams.width / 2f, 1f)
            touch(binding.exposureBar, MotionEvent.ACTION_UP, 24f, 64f, down, down + 16)
        }
        modules.session.deliver(SessionMessage.Focused(0.5f, 0.2f))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
        onScreen { binding, _ ->
            assertFalse(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }
    }

    @Test
    fun aDragStillWaitingForItsTapsAnswer_endsWhenThePhotographerFocusesMeanwhile() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        val down = SystemClock.uptimeMillis()
        var y = 0f
        onScreen { binding, _ ->
            val area = binding.videoContainer
            y = area.height / 2f
            touch(area, MotionEvent.ACTION_DOWN, area.width / 2f, y, down)
            y -= ViewConfiguration.get(area.context).scaledTouchSlop + 2f
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 16)
        }
        idle()
        assertEquals(1, focuses(modules).size)

        // The photographer's own focus takes the bar down before the tap's answer comes.
        modules.session.deliver(SessionMessage.Focused(0.5f, 0.2f))
        idle()
        modules.session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, focused = true))
        idle()
        onScreen { binding, _ ->
            val area = binding.videoContainer
            y -= 1f
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 32)
            y -= wayFor(area, 2)
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 48)
            touch(area, MotionEvent.ACTION_UP, area.width / 2f, y, down, down + 64)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))

        // No brightness from a bar that is not there.
        assertTrue(brightnesses(modules).isEmpty())
        onScreen { binding, _ -> assertFalse(binding.exposureBar.isShown) }
    }

    @Test
    fun talkBacksActionOnTheVideo_asksForTheFocusAtTheImagesMiddle_andBringsTheBar() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)

        onScreen { binding, _ ->
            val info = binding.liveView.createAccessibilityNodeInfo()
            val click = info.actionList.single { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            assertEquals(string(R.string.focus_and_brightness_action), click.label.toString())
            assertTrue(binding.liveView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        }
        idle()

        val asked = focuses(modules).single()
        assertEquals(0.5f, asked.x, 0.01f)
        assertEquals(0.5f, asked.y, 0.01f)
        onScreen { binding, _ ->
            assertTrue(binding.exposureBar.isShown)
            assertTrue(binding.focusRing.isShown)
        }

        // In guide-edit mode the action does nothing, as a tap does.
        modules.mirror.setGuide(guide())
        idle()
        onScreen { binding, _ -> binding.editGuideButton.performClick() }
        idle()
        onScreen { binding, _ ->
            assertFalse(binding.liveView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        }
        assertEquals(1, focuses(modules).size)
    }

    @Test
    fun whileThePhotographersCameraIsPaused_aTapAsksForNothing_untilFramesComeAgain() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)

        // No frame for longer than the 2 s stall limit: the camera is paused, and would drop the request.
        Thread.sleep(2_100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ ->
            assertEquals(string(R.string.camera_paused), binding.connectingText.text.toString())
        }
        tapTheImage()
        idle()
        onScreen { binding, _ ->
            assertFalse(binding.liveView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        }
        idle()
        assertTrue(focuses(modules).isEmpty())
        onScreen { binding, _ ->
            assertFalse(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }

        modules.video.deliverFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        tapTheImage()
        idle()
        assertEquals(1, focuses(modules).size)
        onScreen { binding, _ -> assertTrue(binding.exposureBar.isShown) }
    }

    @Test
    fun whileThePhotographersCameraRebindsForANewRatio_aTapAsksForNothing_untilAFrameOfTheNewShape() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        modules.video.deliverFrame(720, 960)
        idle()

        // The photographer's new ratio: their camera rebinds and drops a tap until it is ready again.
        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, aspect = AspectRatio.R16_9),
        )
        idle()
        tapTheImage()
        idle()
        assertTrue(focuses(modules).isEmpty())
        onScreen { binding, _ ->
            assertFalse(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }

        modules.video.deliverFrame(720, 1280)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        tapTheImage()
        idle()
        assertEquals(1, focuses(modules).size)
    }

    @Test
    fun onACameraWithoutPointFocus_aTapBringsTheBarWithoutTheBox() {
        val modules = launch()
        connect(modules, exposureRange = -4..4)

        tapTheImage()
        idle()

        // The tap still puts the photographer's brightness back to its base.
        assertEquals(1, focuses(modules).size)
        onScreen { binding, _ ->
            assertTrue(binding.exposureBar.isShown)
            assertFalse(binding.focusRing.isShown)
        }
    }

    @Test
    fun aDragOnTheVideo_tapsFirst_andMovesTheBrightnessFromWhereTheCameraWentBackTo() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, focuses = true)
        // Left at +1.5 EV by an earlier drag.
        modules.session.deliver(SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 3))
        idle()

        val down = SystemClock.uptimeMillis()
        var y = 0f
        // Up past the touch slop: the drag begins, and taps where it began.
        onScreen { binding, _ ->
            val area = binding.videoContainer
            val slop = ViewConfiguration.get(area.context).scaledTouchSlop
            y = area.height / 2f
            touch(area, MotionEvent.ACTION_DOWN, area.width / 2f, y, down)
            y -= slop + 2f
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 16)
        }
        idle()
        assertEquals(1, focuses(modules).size)
        onScreen { binding, _ -> assertTrue(binding.exposureBar.isShown) }

        // The tap is not answered yet, so the finger's way changes nothing.
        onScreen { binding, _ ->
            val area = binding.videoContainer
            y -= wayFor(area, 2)
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 32)
        }
        idle()
        assertTrue(brightnesses(modules).isEmpty())

        // The answer: the camera is back at its base, 0. The drag goes on from there, by the way after it.
        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.SUBJECT, final = true, exposure = 0, focused = true),
        )
        idle()
        onScreen { binding, _ ->
            val area = binding.videoContainer
            assertEquals(0, binding.exposureBar.index)
            y -= 1f
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 48)
            y -= wayFor(area, 2)
            touch(area, MotionEvent.ACTION_MOVE, area.width / 2f, y, down, down + 64)
            touch(area, MotionEvent.ACTION_UP, area.width / 2f, y, down, down + 80)
        }
        idle()
        assertEquals(SessionMessage.ExposureSet(2, final = true), brightnesses(modules).last())
        assertTrue(brightnesses(modules).none { it.index > 2 })
    }

    @Test
    fun guideButton_addsOrChangesTheGuideOnceConnected_andOpensAddAPoseGuide() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        onScreen { binding, _ -> assertFalse(binding.guideButton.isEnabled) }

        connect(modules)
        onScreen { binding, _ ->
            assertTrue(binding.guideButton.isEnabled)
            assertFalse(binding.guideButton.isActivated)
            assertEquals(string(R.string.guide_add_description), binding.guideButton.contentDescription)
        }
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.guideButton.isActivated)
            assertEquals(string(R.string.guide_change_description), binding.guideButton.contentDescription)
            binding.guideButton.performClick()
        }
        idle()

        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)
        assertEquals(true, nav.backStack.last().arguments?.getBoolean("fromSubject"))
    }

    @Test
    fun saveGuide_showsWithAGuide_savesItOnThisPhone_andIsFilledOnceSaved() {
        val modules = launch()
        connect(modules)
        onScreen { binding, _ -> assertFalse(binding.saveGuideButton.isShown) }

        modules.mirror.setGuide(guide(withReference = false))
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.saveGuideButton.isShown)
            assertTrue(binding.saveGuideButton.isEnabled)
            assertEquals(string(R.string.save_guide), binding.saveGuideButton.contentDescription)
            assertEquals(R.drawable.ic_heart, shadowOf(binding.saveGuideButton.plainIcon).createdFromResId)
            assertEquals(binding.root.context.getColor(R.color.pix_text), binding.saveGuideButton.iconTint.defaultColor)
            binding.saveGuideButton.performClick()
        }
        idle()

        assertEquals(listOf("guide-1"), modules.saved.saved.value.map { it.id })
        onScreen { binding, _ ->
            // At the top of the video, as on the Camera screen (Design 2.8).
            assertTrue(binding.toggleLabel.isShown)
            assertEquals(string(R.string.guide_saved_notice), binding.toggleLabel.text.toString())
            assertEquals(string(R.string.guide_saved), binding.saveGuideButton.contentDescription)
            // A ♥ filled red, as on Camera (#297).
            assertEquals(R.drawable.ic_heart_filled, shadowOf(binding.saveGuideButton.plainIcon).createdFromResId)
            assertEquals(
                binding.root.context.getColor(R.color.pix_heart),
                binding.saveGuideButton.iconTint.defaultColor,
            )
            binding.saveGuideButton.performClick()
        }
        idle()

        // A second tap makes it history again, still on this phone (FR-6.16, #297).
        assertEquals(listOf("guide-1" to false), modules.saved.saved.value.map { it.id to it.favorite })
        onScreen { binding, _ ->
            assertEquals(string(R.string.guide_removed_notice), binding.toggleLabel.text.toString())
            assertEquals(string(R.string.save_guide), binding.saveGuideButton.contentDescription)
        }
    }

    @Test
    fun theGuidePanel_showsWithAGuideAboveTheCamerasName_andAsksThePhotographersPhone() {
        val modules = launch()
        connect(modules)
        onScreen { binding, _ -> assertFalse(binding.guideControls.isShown) }

        modules.mirror.setGuide(guide(withReference = false))
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.guideControls.isShown)
            assertTrue(windowBox(binding.guideControls).bottom <= windowBox(binding.cameraCaption).top)
            assertTrue(binding.opacitySlider.isEnabled)
            assertEquals(string(R.string.guide_opacity_percent, 50), binding.opacityValue.text.toString())
            binding.hideGuideButton.performClick()
            binding.removeGuideButton.performClick()
        }
        idle()

        assertEquals(
            listOf(SessionMessage.GuideVisibleSet("guide-1", false), SessionMessage.GuideRemove("guide-1")),
            modules.session.sent.filter { it is SessionMessage.GuideVisibleSet || it is SessionMessage.GuideRemove },
        )
    }

    @Test
    fun theGuidePanel_ofAHiddenGuide_offersShow_andItsOpacityIsOff() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        modules.mirror.update(final = true) { it.copy(visible = false, opacity = 0.3f) }
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.guideControls.isShown)
            assertEquals(string(R.string.show_guide), binding.hideGuideButton.contentDescription)
            assertEquals(R.drawable.ic_visibility, shadowOf(binding.hideGuideButton.plainIcon).createdFromResId)
            assertFalse(binding.opacitySlider.isEnabled)
            assertEquals(string(R.string.guide_opacity_percent, 30), binding.opacityValue.text.toString())
        }
    }

    @Test
    fun oneByOne_showsTheWhole4By3AreaWithTheOutsideDimmed_asOnCamera() {
        val modules = launch()
        connect(modules)
        onScreen { binding, _ -> assertFalse(binding.compositionOverlay.dimOutside) }

        modules.session.deliver(
            SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, aspect = AspectRatio.R1_1),
        )
        idle()

        onScreen { binding, _ -> assertTrue(binding.compositionOverlay.dimOutside) }
    }

    @Test
    fun theGuideEditToggle_andAZoomStep_sayWhatTheyDid() {
        val modules = launch()
        connect(modules)
        modules.mirror.setGuide(guide(withReference = false))
        idle()

        onScreen { binding, _ -> binding.editGuideButton.performClick() }
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.pinchModeLabel.isShown)
            assertEquals(string(R.string.pinch_mode_guide), binding.pinchModeLabel.text.toString())
            binding.zoomReadout.performClick()
        }
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.toggleLabel.isShown)
            assertTrue(binding.toggleLabel.text.toString().endsWith("×"))
        }
    }

    @Test
    fun aTapOnTheReadout_whileThePhotographerZooms_namesThePhotographer() {
        val modules = launch()
        connect(modules)
        modules.session.deliver(SessionMessage.ZoomHoldUpdate(Role.PHOTOGRAPHER))
        idle()

        onScreen { binding, _ -> binding.zoomReadout.performClick() }
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.toggleLabel.isShown)
            assertEquals(string(R.string.zoom_held_label, "Dongje"), binding.toggleLabel.text.toString())
        }
        assertTrue(modules.session.sent.none { it is SessionMessage.ZoomSet })
    }

    private fun poseStep(modules: TestAppContainer, step: PoseSetStep, poseIds: List<String> = emptyList()) {
        modules.session.deliver(SessionMessage.PoseStep("set1", step, poseIds))
        // The status check runs every 500 ms on the main looper.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    /** The photographer's set ready to pick from on this phone: pose-1's copy, then PICKING naming it. */
    private fun picking(modules: TestAppContainer) {
        val copy = checkNotNull(FakePoseCandidateCodec().encode("pose-1", bitmap()))
        modules.session.deliver(copy.begin("set1"))
        for (i in copy.chunks.indices) modules.session.deliver(copy.chunk("set1", i))
        modules.session.deliver(copy.end("set1"))
        idle()
        poseStep(modules, PoseSetStep.PICKING, listOf("pose-1"))
    }

    @Test
    fun thePhotographersSet_saysWhatTheyAreDoing_andOpensItsGeneratingPosesOncePerSet() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()

        poseStep(modules, PoseSetStep.GENERATING)
        assertEquals(R.id.subjectGeneratingFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.peer_creating_poses, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.VISIBLE, binding.pickPoseButton.visibility)
            assertEquals(string(R.string.generating_title), binding.pickPoseLabel.text.toString())
            // The step's button has its own place, beside the thumbnail (#314), so the camera's name stays; "Live"
            // goes, since the video's text says what the photographer does, and so does the shutter's use.
            assertEquals(View.VISIBLE, binding.cameraCaption.visibility)
            assertTrue(binding.cameraCaptionTitle.isShown)
            assertFalse(binding.cameraCaptionStatus.isShown)
            // The red dot says "live", as the light over the video does: it goes with "Live".
            assertFalse(binding.cameraCaptionDot.isShown)
            assertFalse(binding.shutterButton.isEnabled)
            val step = IntArray(2).also { binding.pickPoseButton.getLocationInWindow(it) }
            val shutter = IntArray(2).also { binding.shutterButton.getLocationInWindow(it) }
            val thumbnail = IntArray(2).also { binding.galleryThumbnail.getLocationInWindow(it) }
            assertTrue(shutter[0] + binding.shutterButton.width <= step[0])
            assertTrue(step[0] < thumbnail[0])
        }

        // Back keeps it closed, though Subject view renders again; its button opens it again.
        nav.popBackStack()
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ -> binding.pickPoseButton.performClick() }
        idle()
        assertEquals(R.id.subjectGeneratingFragment, nav.currentDestination?.id)

        // After Back, the set's Pick a pose waits for the button: Generating poses would have gone on to it.
        nav.popBackStack()
        picking(modules)
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_picking_pose, "Dongje"), binding.connectingText.text.toString())
            assertEquals(string(R.string.pick_pose_title), binding.pickPoseLabel.text.toString())
            binding.pickPoseButton.performClick()
        }
        idle()
        assertEquals(R.id.subjectPickPoseFragment, nav.currentDestination?.id)

        // The next set opens its Generating poses again.
        nav.popBackStack()
        modules.session.deliver(SessionMessage.PoseStep("set2", PoseSetStep.GENERATING))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(R.id.subjectGeneratingFragment, nav.currentDestination?.id)
    }

    @Test
    fun aSetWithNoCopyHere_saysTheyArePicking_andOpensNothing() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)
        // Generating poses goes back to Subject view by itself for such a set (SubjectGeneratingFragmentTest).
        nav.popBackStack()

        // No copy fitted the subject's limit, so PICKING names none (Codex's review).
        poseStep(modules, PoseSetStep.PICKING)

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_picking_pose, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.GONE, binding.pickPoseButton.visibility)
        }
    }

    @Test
    fun aSetThatFailsWhileSubjectViewIsInFront_saysWhy() {
        val modules = launch()
        scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)

        modules.session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.CLOSED, failure = "OFFLINE"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        // The same reason as on the photographer's phone, though the subject left its Generating poses (#203).
        assertEquals(
            string(R.string.peer_poses_failed, "Dongje") + "\n" + string(R.string.generation_offline_message),
            shownNotice(),
        )
    }

    @Test
    fun aSetThePhotographerClosesAfterBack_saysSo_andTheStatusGoes() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)
        picking(modules)
        nav.popBackStack()
        idle()

        poseStep(modules, PoseSetStep.CLOSED)

        assertEquals(string(R.string.peer_closed_poses, "Dongje"), shownNotice())
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.pickPoseButton.visibility)
            assertEquals(View.GONE, binding.connectingText.visibility)
        }
    }

    @Test
    fun aSetKeptThroughAReconnect_saysReconnectingMeanwhile_andIsNotOpenedAgain() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)
        picking(modules)
        nav.popBackStack()
        idle()

        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ ->
            assertEquals(string(R.string.reconnecting), binding.connectingText.text.toString())
            assertEquals(View.GONE, binding.pickPoseButton.visibility)
        }

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_picking_pose, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.VISIBLE, binding.pickPoseButton.visibility)
        }
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }

    private fun confirmStep(modules: TestAppContainer, step: ConfirmStep, by: Role? = null) {
        modules.session.deliver(SessionMessage.GuideConfirm("c1", step, by = by))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    @Test
    fun thePhotographersReference_saysTheyAreChecking_andOpensYourReferenceOncePerConfirm() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.confirms.startAsReceiver(receiving)
        idle()

        confirmStep(modules, ConfirmStep.FINDING)
        assertEquals(R.id.subjectConfirmFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_checking_guide, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.VISIBLE, binding.pickPoseButton.visibility)
            assertEquals(string(R.string.pose_guide_title), binding.pickPoseLabel.text.toString())
            assertEquals(string(R.string.pose_guide_title), binding.pickPoseButton.contentDescription)
        }

        // Back keeps it closed, though Subject view renders again; its button opens it again.
        nav.popBackStack()
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ -> binding.pickPoseButton.performClick() }
        idle()
        assertEquals(R.id.subjectConfirmFragment, nav.currentDestination?.id)
    }

    @Test
    fun aSetStillPickingHere_isShownBeforeTheConfirmThatFollowsIt() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        modules.confirms.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)
        picking(modules)
        nav.popBackStack()
        idle()

        // The confirm's first step comes before the set's end.
        confirmStep(modules, ConfirmStep.FINDING)
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_picking_pose, "Dongje"), binding.connectingText.text.toString())
            assertEquals(string(R.string.pick_pose_title), binding.pickPoseLabel.text.toString())
        }

        modules.session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.CHOSEN, by = Role.PHOTOGRAPHER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(R.id.subjectConfirmFragment, nav.currentDestination?.id)
    }

    @Test
    fun aGuideThePhotographerUsesAfterBack_saysSo_andTheStatusGoes() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.confirms.startAsReceiver(receiving)
        idle()
        confirmStep(modules, ConfirmStep.FINDING)
        nav.popBackStack()
        idle()

        confirmStep(modules, ConfirmStep.USED, Role.PHOTOGRAPHER)

        assertEquals(string(R.string.peer_used_guide, "Dongje"), shownNotice())
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.pickPoseButton.visibility)
            assertEquals(View.GONE, binding.connectingText.visibility)
        }
    }

    private fun describeStep(modules: TestAppContainer, step: DescriptionStep, id: String = "d1") {
        modules.session.deliver(SessionMessage.PoseDescription(id, step))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    @Test
    fun thePhotographersGeneratePoses_saysTheyAreDescribing_andOpensItOncePerDescription() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.descriptions.startAsReceiver(receiving)
        idle()

        describeStep(modules, DescriptionStep.OPEN)
        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_describing_pose, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.VISIBLE, binding.pickPoseButton.visibility)
            assertEquals(string(R.string.describe_title), binding.pickPoseLabel.text.toString())
        }

        // Back keeps it closed, though Subject view renders again; its button opens it again.
        nav.popBackStack()
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ -> binding.pickPoseButton.performClick() }
        idle()
        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
    }

    @Test
    fun aSetHeldHere_isShownBeforeTheDescriptionThatFollowsIt() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        modules.descriptions.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)
        picking(modules)
        nav.popBackStack()
        idle()

        // Try other poses: the new description comes before the set's end.
        describeStep(modules, DescriptionStep.OPEN)
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(string(R.string.pick_pose_title), onScreen { binding, _ -> binding.pickPoseLabel.text.toString() })

        poseStep(modules, PoseSetStep.CLOSED)
        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
    }

    @Test
    fun aStepThatComesUnderAddAPoseGuide_opensOnceTheSheetCloses() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.descriptions.startAsReceiver(receiving)
        idle()
        onScreen { binding, _ -> binding.guideButton.performClick() }
        idle()

        describeStep(modules, DescriptionStep.OPEN)
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)

        // Closing the sheet changes nothing Subject view renders from (Codex's review).
        nav.popBackStack()
        idle()
        assertEquals(R.id.subjectDescribeFragment, nav.currentDestination?.id)
    }

    @Test
    fun generatePosesThePhotographerClosesAfterBack_saysSo_andTheStatusGoes() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.descriptions.startAsReceiver(receiving)
        idle()
        describeStep(modules, DescriptionStep.OPEN)
        nav.popBackStack()
        idle()

        describeStep(modules, DescriptionStep.CLOSED)

        assertEquals(string(R.string.peer_closed_describe, "Dongje"), shownNotice())
        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.pickPoseButton.visibility)
            assertEquals(View.GONE, binding.connectingText.visibility)
        }
    }

    @Test
    fun guideThatDidNotGo_saysSo() {
        val modules = launch()

        // Not connected yet, so the guide cannot go to the photographer.
        modules.syncer.offer(guide(withReference = false))
        idle()

        assertEquals(string(R.string.guide_offer_failed), shownNotice())
    }

    @Test
    fun leave_asksFirst_thenReturnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        onScreen { binding, _ -> binding.leaveButton.performClick() }
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        leaveQuestion().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
        assertEquals(listOf(EndReason.LEFT), modules.session.leaves)
    }

    @Test
    fun leave_cancelled_staysInTheSession() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        onScreen { binding, _ -> binding.leaveButton.performClick() }
        idle()
        val question = leaveQuestion()
        assertEquals(
            string(R.string.leave_session_title),
            question.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text.toString(),
        )
        question.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()

        assertFalse(question.isShowing)
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertEquals(emptyList<EndReason>(), modules.session.leaves)
    }

    private fun leaveQuestion(): AlertDialog {
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        return dialog
    }

    @Test
    fun back_asksBeforeLeaving() {
        val modules = launch()
        connect(modules)

        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
    }

    @Test
    fun liveViewOnMobileData_saysSo() {
        val modules = launch()
        connect(modules)

        modules.session.setPath(IcePath("relay", "srflx", "cellular", "udp"))
        idle()

        assertEquals(string(R.string.mobile_data_notice), shownNotice())
        assertTrue(runBlocking { modules.mobileData.wasShown() })
    }

    @Test
    fun aMobileDataNoticeWaitingBeforeTheScreenOpens_isShownWhenItOpens() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.session.setPath(IcePath("srflx", "srflx", "cellular", null))
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()

        assertEquals(string(R.string.mobile_data_notice), shownNotice())
        assertTrue(runBlocking { modules.mobileData.wasShown() })
    }

    @Test
    fun connectionLost_opensConnectionLost() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.connectionLostFragment, nav.currentDestination?.id)
    }

    @Test
    fun connectionLost_whileAddAPoseGuideIsOpen_closesItAndOpensConnectionLost() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        onScreen { binding, _ -> binding.guideButton.performClick() }
        idle()
        assertEquals(R.id.addGuideSheet, nav.currentDestination?.id)

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.connectionLostFragment, nav.currentDestination?.id)
    }

    @Test
    fun photographerEndsTheSession_saysSoAndReturnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        idle()

        assertEquals(string(R.string.peer_ended_session, "Dongje"), shownNotice())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun photographerEndsTheSession_beforeTheirNameArrived_saysSoWithoutOne() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)

        modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        idle()

        assertEquals(string(R.string.peer_ended_session_unnamed), shownNotice())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
