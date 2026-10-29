// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
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
import java.time.Duration
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/*
 * Subject view's render paths (R&S 6.6, Testing Documentation 2.1.6), on the real Fragment under FragmentScenario
 * with a container of fakes. Partitions: not connected / connected before the first frame / streaming / frames
 * stopped for 2 s ("camera paused") / streaming again / reconnecting; the photographer's capabilities with and
 * without a flash and an exposure range; the camera state's zoom, flash, and ratio; a guide arriving, the edit-mode
 * toggle, a hidden guide; the photographer's reference photo with its guide, enlarged, kept while the guide is hidden,
 * gone with a replaced or removed guide, and moved to another corner on this phone only; the brightness panel and a
 * flash tap; the live view on mobile data (FR-6.13); the Guide button (FR-6.15) disabled until connected, adding or
 * changing the guide, opening Add a pose guide, and "Couldn't send the guide" for a guide that did not go; Leave, the
 * back button's question, and the session ending by a lost connection (also with Add a pose guide open over it) or
 * the photographer, named or before the name arrived. Save guide (FR-6.16): hidden without a guide, then saving it
 * and filled; the photographer's set (FR-6.15): what the photographer is doing in place of "camera paused", Pick a
 * pose opened once per set and then by its button, a set the photographer closes after Back, which says so, and a
 * set kept through a reconnect, with "Reconnecting…" in place of what the photographer is doing; the photographer's
 * Reference confirm (FR-6.15): "checking the guide", Your reference opened once per confirm and then by the same
 * button, a set still picking shown before the confirm that follows it, and a guide the photographer uses after Back,
 * which says so; the photographer's Generate poses (FR-6.15): "describing a pose" in place of "camera paused", Generate
 * poses opened once per description and then by the same button, a set held here shown before it, and a close after
 * Back, which says so.
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
    ) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo(name), Role.SUBJECT))
        if (firstFrame) modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(
                CameraCapabilities(0.6f, 10f, listOf(1f, 2f), exposureRange, 0.5f, hasFlash),
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
            assertEquals(View.GONE, binding.brightnessButton.visibility)
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
            assertEquals(View.VISIBLE, binding.brightnessButton.visibility)
            assertEquals(View.GONE, binding.brightnessPanel.visibility)
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
            assertEquals("+1.0", binding.brightnessValue.text.toString())
        }
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
            assertFalse(binding.editGuideButton.isChecked)
            assertFalse(binding.guideOverlay.editable)
            binding.editGuideButton.performClick()
        }
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.editGuideButton.isChecked)
            assertTrue(binding.guideOverlay.editable)
        }

        // The photographer hides the guide: it cannot be edited here until it is shown again (FR-7.2).
        modules.mirror.update(final = true) { it.copy(visible = false) }
        idle()

        onScreen { binding, _ ->
            assertFalse(binding.editGuideButton.isEnabled)
            assertFalse(binding.editGuideButton.isChecked)
            assertFalse(binding.guideOverlay.editable)
        }
    }

    @Test
    fun brightnessButton_opensThePanel_andTheFlashTapSendsARequest() {
        val modules = launch()
        connect(modules, exposureRange = -4..4, hasFlash = true)

        onScreen { binding, _ ->
            binding.brightnessButton.performClick()
            binding.flashButton.performClick()
        }
        idle()

        onScreen { binding, _ ->
            assertTrue(binding.brightnessButton.isChecked)
            assertEquals(View.VISIBLE, binding.brightnessPanel.visibility)
            assertTrue(binding.brightnessSlider.isEnabled)
        }
        assertTrue(modules.session.sent.any { it == SessionMessage.FlashSet(FlashMode.AUTO) })
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
        assertEquals(string(R.string.guide_saved_notice), ShadowToast.getTextOfLatestToast())
        onScreen { binding, _ ->
            assertEquals(string(R.string.guide_saved), binding.saveGuideButton.contentDescription)
        }
    }

    private fun poseStep(modules: TestAppContainer, step: PoseSetStep) {
        modules.session.deliver(SessionMessage.PoseStep("set1", step))
        // The status check runs every 500 ms on the main looper.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    @Test
    fun thePhotographersSet_saysWhatTheyAreDoing_andOpensPickAPoseOncePerSet() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()

        poseStep(modules, PoseSetStep.GENERATING)
        onScreen { binding, _ ->
            assertEquals(View.VISIBLE, binding.connectingText.visibility)
            assertEquals(string(R.string.peer_creating_poses, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.GONE, binding.pickPoseButton.visibility)
        }
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)

        poseStep(modules, PoseSetStep.PICKING)
        assertEquals(R.id.subjectPickPoseFragment, nav.currentDestination?.id)
        onScreen { binding, _ ->
            assertEquals(string(R.string.peer_picking_pose, "Dongje"), binding.connectingText.text.toString())
            assertEquals(View.VISIBLE, binding.pickPoseButton.visibility)
        }

        // Back keeps it closed, though Subject view renders again; its button opens it again.
        nav.popBackStack()
        modules.mirror.setGuide(guide(withReference = false))
        idle()
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ -> binding.pickPoseButton.performClick() }
        idle()
        assertEquals(R.id.subjectPickPoseFragment, nav.currentDestination?.id)
    }

    @Test
    fun aSetThePhotographerClosesAfterBack_saysSo_andTheStatusGoes() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        connect(modules)
        modules.poses.startAsReceiver(receiving)
        idle()
        poseStep(modules, PoseSetStep.GENERATING)
        poseStep(modules, PoseSetStep.PICKING)
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
        poseStep(modules, PoseSetStep.PICKING)
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
            assertEquals(string(R.string.reference_title), binding.pickPoseLabel.text.toString())
            assertEquals(string(R.string.reference_title), binding.pickPoseButton.contentDescription)
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
        poseStep(modules, PoseSetStep.PICKING)
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
        poseStep(modules, PoseSetStep.PICKING)
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
