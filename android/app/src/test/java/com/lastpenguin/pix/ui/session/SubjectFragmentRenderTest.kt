// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han, Jaewan Park, and Dongje Park
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
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import java.time.Duration
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
 * stopped for 2 s ("camera paused") / streaming again; the photographer's capabilities with and
 * without a flash and an exposure range; the camera state's zoom, flash, and ratio; a guide arriving, the edit-mode
 * toggle, a hidden guide; the photographer's reference photo with its guide, enlarged, kept while the guide is hidden,
 * gone with a replaced or removed guide, and moved to another corner on this phone only; the brightness panel and a
 * flash tap; the live view on mobile data (FR-6.13); the Guide button (FR-6.15) disabled until connected, adding or
 * changing the guide, opening Add a pose guide, and "Couldn't send the guide" for a guide that did not go; Leave, the
 * back button's question, and the session ending by a lost connection (also with Add a pose guide open over it) or
 * the photographer, named or before the
 * name arrived.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SubjectFragmentRenderTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
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
