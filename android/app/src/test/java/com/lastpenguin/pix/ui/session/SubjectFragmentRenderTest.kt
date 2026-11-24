// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
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
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.ExposureBarView
import com.lastpenguin.pix.ui.camera.FocusBrightness
import com.lastpenguin.pix.ui.camera.REFERENCE_HIDDEN_ALPHA
import com.lastpenguin.pix.ui.camera.REFERENCE_PEEK_DP
import com.lastpenguin.pix.ui.camera.fitViewfinderFrame
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
import org.robolectric.shadows.ShadowToast

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
 * and small when it is back; #237), a mirror through each change of its size; a flash tap; remote focus with the
 * brightness beside it (FR-7.7, FR-7.3, #273): a tap inside the image (the focus asked for at the photographer's x, the
 * box there, the bar beside it), beside the image and in guide-edit mode (nothing), on a camera without point focus
 * (the bar alone); a drag on the bar (steps, then the final value; both gone after the idle time); a drag on the video
 * without the bar up (a tap first, nothing sent until the tap's answer, then steps from the brightness it gave); the
 * photographer's own focus (the box alone at its place on the mirror; this phone's bar up, which goes, or held under a
 * finger, where nothing moves; while a drag still waits for its tap's answer, which ends that drag); TalkBack's action
 * on the video (the focus at the image's middle and the bar, and nothing in guide-edit mode); the live view on mobile data (FR-6.13); the Guide button (FR-6.15) disabled until connected, adding or
 * changing the guide, opening Add a pose guide, and "Couldn't send the guide" for a guide that did not go; Leave, the
 * back button's question, and the session ending by a lost connection (also with Add a pose guide open over it) or
 * the photographer, named or before the name arrived. Save guide (FR-6.16): hidden without a guide, then saving it
 * and filled; the photographer's set (FR-6.15): what the photographer is doing in place of "camera paused", the set's
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
 * The UAT's opinions (#271): the slot where Camera's shutter is empty until connected, then whose camera this is and
 * "Live", given up to a step's button, with no notice saying it as well; *Leave* red; the guide-edit toggle one person icon, unlit with the guide plain and lit with it
 * filled. The grid on by default and off from Settings.
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
            assertEquals(View.GONE, binding.flashButton.visibility)
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
            assertEquals(View.VISIBLE, binding.flashButton.visibility)
            assertTrue(binding.flashButton.isEnabled)
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
            assertEquals(string(R.string.flash_auto), binding.flashButton.contentDescription)
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
    fun theShuttersSlot_isEmptyUntilConnected_thenSaysWhoseCameraItIsAndThatItIsLive() {
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
            assertTrue(binding.cameraCaptionDot.isShown)
            assertFalse(binding.cameraCaptionSpinner.isShown)
            // The slot names the camera for the whole session, so no notice says it as well.
            assertFalse(binding.toggleLabel.isShown)
        }

        // The link drops: the video says so, and the slot is empty again until it is back.
        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.cameraCaption.visibility) }
    }

    @Test
    fun leave_isRed_asEndSessionIsOnCamera() {
        launch()

        // The danger style's text; its outline is an app attribute, which this test's host does not apply.
        onScreen { binding, _ -> assertEquals(color(R.color.pix_danger_text), binding.leaveButton.currentTextColor) }
    }

    @Test
    fun theGrid_isOnByDefault_andOffFromSettings() {
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
    fun theFlashTap_sendsARequest() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ -> binding.flashButton.performClick() }
        idle()

        assertTrue(modules.session.sent.any { it == SessionMessage.FlashSet(FlashMode.AUTO) })
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
        onScreen { binding, _ -> assertEquals(View.GONE, binding.saveGuideButton.visibility) }

        modules.mirror.setGuide(guide(withReference = false))
        idle()
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.saveGuideButton.visibility)
            assertTrue(binding.saveGuideButton.isEnabled)
            assertEquals(string(R.string.save_guide), binding.saveGuideButton.contentDescription)
            binding.saveGuideButton.performClick()
        }
        idle()

        assertEquals(listOf("guide-1"), modules.saved.saved.value.map { it.id })
        onScreen { binding, _ ->
            // At the top of the video, as on the Camera screen (Design 2.8).
            assertTrue(binding.toggleLabel.isShown)
            assertEquals(string(R.string.guide_saved_notice), binding.toggleLabel.text.toString())
            assertEquals(string(R.string.guide_saved), binding.saveGuideButton.contentDescription)
            binding.saveGuideButton.performClick()
        }
        idle()

        // A second tap takes it out of the saved guides again (FR-6.16).
        assertTrue(modules.saved.saved.value.isEmpty())
        onScreen { binding, _ ->
            assertEquals(string(R.string.guide_removed_notice), binding.toggleLabel.text.toString())
            assertEquals(string(R.string.save_guide), binding.saveGuideButton.contentDescription)
        }
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
            // The step's button takes the slot where the camera's name is (#271).
            assertEquals(View.GONE, binding.cameraCaption.visibility)
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
            ShadowToast.getTextOfLatestToast(),
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

        assertEquals(string(R.string.peer_closed_poses, "Dongje"), ShadowToast.getTextOfLatestToast())
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

        assertEquals(string(R.string.peer_used_guide, "Dongje"), ShadowToast.getTextOfLatestToast())
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

        assertEquals(string(R.string.peer_closed_describe, "Dongje"), ShadowToast.getTextOfLatestToast())
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

        assertEquals(string(R.string.guide_offer_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun leave_returnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)

        onScreen { binding, _ -> binding.leaveButton.performClick() }
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
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

        assertEquals(string(R.string.mobile_data_notice), ShadowToast.getTextOfLatestToast())
        assertTrue(runBlocking { modules.mobileData.wasShown() })
    }

    @Test
    fun aMobileDataNoticeWaitingBeforeTheScreenOpens_isShownWhenItOpens() {
        val modules = TestAppContainer().install()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.session.setPath(IcePath("srflx", "srflx", "cellular", null))
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()

        assertEquals(string(R.string.mobile_data_notice), ShadowToast.getTextOfLatestToast())
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

        assertEquals(string(R.string.peer_ended_session, "Dongje"), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    @Test
    fun photographerEndsTheSession_beforeTheirNameArrived_saysSoWithoutOne() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)

        modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        idle()

        assertEquals(string(R.string.peer_ended_session_unnamed), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
