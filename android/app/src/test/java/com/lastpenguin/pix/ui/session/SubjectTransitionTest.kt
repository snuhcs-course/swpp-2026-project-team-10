// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.animation.ValueAnimator
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * Subject view's changes (Design 2.6.4, Iter3-A12) on the Galaxy S22's app area, 360 x 705 dp, with the display's
 * frames paused where a test looks inside a fade, since idle() plays each to its end (Testing 2.1.8). Frames come in
 * the photographer's shape, 720 x 960 for 4:3 and 720 x 1280 for 16:9.
 * The first frame: the video clears at once, without waiting for the next check of the video. A text over the video
 * (reconnecting, the camera paused) dims it, and it clears when the video is back. The photographer's new ratio: the
 * area moves to it, the video dims, and the grid and the guide wait for a frame of the new shape, or for the time
 * limit. The photographer's shutter: a blink, but none over a dimmed video. With the phone's animations off, each
 * change is at its end at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    qualifiers = "w360dp-h705dp-xxhdpi",
    shadows = [ShadowSurfaceViewRenderer::class],
    instrumentedPackages = ["org.webrtc"],
)
class SubjectTransitionTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null

    /** Where the subject's photo syncer runs in the shutter's tests, as the session would run it. */
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
        scenario?.close()
        receiving.cancel()
    }

    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        return modules
    }

    private fun screen(): FragmentSubjectBinding {
        var binding: FragmentSubjectBinding? = null
        scenario!!.onFragment { binding = FragmentSubjectBinding.bind(it.requireView()) }
        return binding!!
    }

    private fun connect(modules: TestAppContainer, firstFrame: Boolean = true) {
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        if (firstFrame) modules.video.deliverFrame(720, 960)
        // The video's check runs every 500 ms on the main looper.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    private fun TestAppContainer.photographerRatio(aspect: AspectRatio) {
        session.deliver(SessionMessage.CameraStateUpdate(1f, Role.PHOTOGRAPHER, final = true, aspect = aspect))
        idle()
    }

    /** The video's area as its margins set it, in dp: what a move sets before the next layout. */
    private fun FragmentSubjectBinding.areaHeight(): Float {
        val params = videoContainer.layoutParams as ViewGroup.MarginLayoutParams
        return (root.height - params.topMargin - params.bottomMargin) / root.resources.displayMetrics.density
    }

    private fun FragmentSubjectBinding.overlaysShown() = guideOverlay.isShown && compositionOverlay.isShown

    private fun play(ms: Long) {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    /**
     * The display's next frame, frames paused again after it: a layout the moved area asked for holds the main
     * thread's other messages back until then, the frame's shape among them, as on a phone.
     */
    private fun layOut() {
        play(FRAME_MS)
        ShadowChoreographer.setPaused(true)
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    @Test
    fun theFirstFrame_clearsTheVideoAtOnce_withoutWaitingForTheNextCheck() {
        val modules = launch()
        connect(modules, firstFrame = false)
        screen().let {
            assertTrue(it.videoScrim.isShown)
            assertEquals(1f, it.videoScrim.alpha, 0f)
            assertTrue(it.connectingText.isShown)
        }
        ShadowChoreographer.setPaused(true)

        modules.video.deliverFrame(720, 960)
        idle()

        // "Connecting…" goes before the 500 ms check comes round, and the video starts to fade in.
        screen().let {
            assertFalse(it.connectingText.isShown)
            assertTrue(it.videoScrim.isShown)
        }
        play(500)
        assertEquals(View.GONE, screen().videoScrim.visibility)
    }

    @Test
    fun aTextOverTheVideo_dimsIt_andTheVideoClearsWhenItIsBack() {
        val modules = launch()
        connect(modules)
        assertEquals(View.GONE, screen().videoScrim.visibility)

        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        play(600)
        screen().let {
            assertTrue(it.connectingText.isShown)
            assertTrue(it.videoScrim.isShown)
            assertEquals(0.6f, it.videoScrim.alpha, 0.01f)
        }

        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame(720, 960)
        play(600)
        assertEquals(View.GONE, screen().videoScrim.visibility)

        // No frame for longer than the 2 s stall limit: "camera paused" dims it as well.
        Thread.sleep(2_100)
        play(600)
        assertEquals(0.6f, screen().videoScrim.alpha, 0.01f)
    }

    @Test
    fun aNewRatio_movesTheVideosArea_andTheGridAndTheGuideWaitForItsShape() {
        val modules = launch()
        connect(modules)
        assertEquals(480f, screen().areaHeight(), 0.5f)
        ShadowChoreographer.setPaused(true)

        modules.photographerRatio(AspectRatio.R16_9)

        // At the start: the 4:3 area, the video dimming, the grid and the guide gone.
        screen().let {
            assertEquals(480f, it.areaHeight(), 0.5f)
            assertTrue(it.videoScrim.isShown)
            assertFalse(it.guideOverlay.isShown)
            assertFalse(it.compositionOverlay.isShown)
        }

        // At the end, while the last 4:3 frame still shows: the 16:9 area, dimmed.
        play(500)
        screen().let {
            assertEquals(640f, it.areaHeight(), 0.5f)
            assertEquals(0.6f, it.videoScrim.alpha, 0.01f)
            assertFalse(it.overlaysShown())
        }

        // A frame of the new shape: the video clears, and the grid and the guide fade back in.
        modules.video.deliverFrame(720, 1280)
        play(500)
        screen().let {
            assertEquals(View.GONE, it.videoScrim.visibility)
            assertTrue(it.overlaysShown())
            assertEquals(1f, it.guideOverlay.alpha, 0f)
            assertEquals(1f, it.compositionOverlay.alpha, 0f)
        }
    }

    @Test
    fun aNewRatioWhoseFramesDoNotCome_endsAtTheTimeLimit() {
        val modules = launch()
        connect(modules)

        modules.photographerRatio(AspectRatio.R1_1)
        assertFalse(screen().overlaysShown())

        play(2_500)
        screen().let {
            assertTrue(it.overlaysShown())
            assertEquals(View.GONE, it.videoScrim.visibility)
        }
    }

    @Test
    fun thePhotographersShutter_blinksTheVideo_butNotADimmedOne() {
        val modules = launch()
        connect(modules)
        modules.photos.startAsReceiver(receiving)
        ShadowChoreographer.setPaused(true)

        modules.session.deliver(SessionMessage.PhotoShutter)
        idle()
        assertTrue(screen().videoScrim.isShown)
        play(500)
        assertEquals(View.GONE, screen().videoScrim.visibility)

        modules.session.setState(SessionState.Reconnecting("s1", 0L))
        play(600)
        modules.session.deliver(SessionMessage.PhotoShutter)
        play(500)
        assertEquals(0.6f, screen().videoScrim.alpha, 0.01f)
    }

    @Test
    fun withAnimationsOff_eachChangeIsAtItsEndAtOnce() {
        val modules = launch()
        setAnimatorScale(0f)
        // Nothing may wait for a display frame.
        ShadowChoreographer.setPaused(true)
        connect(modules, firstFrame = false)

        modules.video.deliverFrame(720, 960)
        idle()
        assertEquals(View.GONE, screen().videoScrim.visibility)

        modules.photographerRatio(AspectRatio.R16_9)
        screen().let {
            assertEquals(640f, it.areaHeight(), 0.5f)
            assertEquals(0.6f, it.videoScrim.alpha, 0f)
        }
        layOut()

        modules.video.deliverFrame(720, 1280)
        idle()
        screen().let {
            assertEquals(View.GONE, it.videoScrim.visibility)
            assertTrue(it.overlaysShown())
            assertEquals(1f, it.guideOverlay.alpha, 0f)
        }

        // No blink at the shutter.
        modules.photos.startAsReceiver(receiving)
        modules.session.deliver(SessionMessage.PhotoShutter)
        idle()
        assertEquals(View.GONE, screen().videoScrim.visibility)
    }

    private companion object {
        // One display frame, and a little more.
        const val FRAME_MS = 20L
    }
}
