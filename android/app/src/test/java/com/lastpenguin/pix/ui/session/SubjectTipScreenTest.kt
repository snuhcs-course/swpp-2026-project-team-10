// AI-generated with Claude Code, 2026-11-18, reviewed by Dongje Park
package com.lastpenguin.pix.ui.session

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
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

/*
 * The photographer's tip on Subject view (R&S 6.6, FR-4.10, #213; Testing Documentation 2.1.6), on the real Fragment
 * with the test modules' TipSyncer as the session runs it. The button (equivalence partitioning of what the guide on
 * the video has): no guide, a guide without a tip, a tip of another guide, a tip of the guide on the video, a hidden
 * guide, a tip the photographer's phone took back. The banner (state-based): closed when the tip comes, with the
 * button's dot; opened by the button with the tip's sentences, one on a line; closed by the button, by a touch anywhere, which still reaches the control it lands on, and by a click on itself;
 * closed again for another guide's tip.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SubjectTipScreenTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null
    private lateinit var modules: TestAppContainer

    /** Where the subject's tip syncer runs, as the session would run it. */
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Subject view in a live session with the photographer's video, listening for their tip. */
    private fun launch() {
        modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(1f, 2f), -6..6, 0.5f, false)),
        )
        modules.tipSync.startAsReceiver(receiving)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
    }

    private fun <T> onScreen(block: (FragmentSubjectBinding, SubjectFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentSubjectBinding.bind(it.requireView()), it) }
        return result!!
    }

    private fun guideOnVideo(id: String = "guide-1") {
        modules.mirror.setGuide(guide(withReference = false).copy(id = id))
        idle()
    }

    private fun tipArrives(guideId: String = "guide-1", text: String = TEXT) {
        modules.session.deliver(SessionMessage.GuideTip(guideId, text))
        idle()
    }

    private fun string(id: Int) = onScreen { controls, _ -> controls.root.context.getString(id) }

    /** A tap that starts and ends at [view]'s middle, dispatched from the screen's root as a finger's would be. */
    private fun tapThroughTheRoot(controls: FragmentSubjectBinding, view: View) {
        val at = IntArray(2).also(view::getLocationInWindow)
        val origin = IntArray(2).also(controls.root::getLocationInWindow)
        val x = (at[0] - origin[0] + view.width / 2).toFloat()
        val y = (at[1] - origin[1] + view.height / 2).toFloat()
        val now = SystemClock.uptimeMillis()
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))
    }

    // ---- the button ---------------------------------------------------------------------------------------------

    @Test
    fun theTipButton_showsOnlyWhileTheGuideOnTheVideoHasATip() {
        launch()
        onScreen { controls, _ -> assertFalse(controls.tipButtonFrame.isShown) }

        guideOnVideo()
        onScreen { controls, _ -> assertFalse("a guide without a tip", controls.tipButtonFrame.isShown) }

        tipArrives(guideId = "another-guide")
        onScreen { controls, _ -> assertFalse("another guide's tip", controls.tipButtonFrame.isShown) }

        tipArrives()
        onScreen { controls, _ ->
            assertTrue(controls.tipButtonFrame.isShown)
            assertEquals(string(R.string.show_tip), controls.tipButton.contentDescription)
        }

        // The photographer's phone took the tip back, as when a saved copy of the guide replaced it.
        modules.session.deliver(SessionMessage.GuideTipClear)
        idle()
        onScreen { controls, _ -> assertFalse(controls.tipButtonFrame.isShown) }
    }

    @Test
    fun aTipThatComesBeforeItsGuide_showsItsButtonOnceTheGuideIsOnTheVideo() {
        launch()
        tipArrives()
        onScreen { controls, _ -> assertFalse(controls.tipButtonFrame.isShown) }

        guideOnVideo()
        onScreen { controls, _ -> assertTrue(controls.tipButtonFrame.isShown) }

        modules.mirror.setGuide(null)
        idle()
        onScreen { controls, _ -> assertFalse(controls.tipButtonFrame.isShown) }
    }

    @Test
    fun aHiddenGuide_hidesTheTipAndItsButton_untilItShowsAgain() {
        launch()
        guideOnVideo()
        tipArrives()
        onScreen { controls, _ -> controls.tipButton.performClick() }
        idle()

        modules.mirror.update(final = true) { it.copy(visible = false) }
        idle()
        onScreen { controls, _ ->
            assertFalse(controls.tipButtonFrame.isShown)
            assertFalse(controls.tipBanner.isShown)
        }

        modules.mirror.update(final = true) { it.copy(visible = true) }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipButtonFrame.isShown)
            assertTrue(controls.tipBanner.isShown)
        }
    }

    // ---- the banner ---------------------------------------------------------------------------------------------

    @Test
    fun theTip_staysClosedUntilTheButton_whichOpensItsSentences() {
        launch()
        guideOnVideo()
        tipArrives()

        onScreen { controls, _ ->
            assertFalse("never by itself", controls.tipBanner.isShown)
            assertTrue("a tip not yet looked at", controls.tipDot.isShown)
            controls.tipButton.performClick()
        }
        idle()

        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(View.GONE, controls.tipDot.visibility)
            // The photographer's tip as the server wrote it, a sentence on each line, beside the light bulb.
            assertEquals(TEXT, controls.tipText.text.toString())
            assertTrue(controls.tipText.isShown)
            assertTrue(controls.tipIcon.isShown)
            // A line for each sentence at least; a narrow screen wraps the longer ones.
            assertTrue(controls.tipText.lineCount >= 3)
        }
    }

    @Test
    fun theButton_closesAnOpenTip_andOpensItAgain() {
        launch()
        guideOnVideo()
        tipArrives()

        // As a finger's tap: the root sees the touch end first, which must not close what the button then opens.
        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.tipButton) }
        idle()
        onScreen { controls, _ -> assertTrue(controls.tipBanner.isShown) }

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.tipButton) }
        idle()
        onScreen { controls, _ -> assertFalse(controls.tipBanner.isShown) }

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.tipButton) }
        idle()
        onScreen { controls, _ ->
            assertTrue(controls.tipBanner.isShown)
            assertEquals(View.GONE, controls.tipDot.visibility)
        }
    }

    @Test
    fun aTouchAnywhere_closesTheTip_andStillReachesTheControlItLandsOn() {
        launch()
        guideOnVideo()
        tipArrives()
        onScreen { controls, _ -> controls.tipButton.performClick() }
        idle()

        onScreen { controls, _ -> tapThroughTheRoot(controls, controls.brightnessButton) }
        idle()

        onScreen { controls, _ ->
            assertFalse(controls.tipBanner.isShown)
            assertTrue("the brightness slider opened", controls.brightnessPanel.isShown)
            assertTrue("the button stays for another look", controls.tipButtonFrame.isShown)
        }
    }

    @Test
    fun aClickOnTheBanner_closesIt_asTalkBackDoes() {
        launch()
        guideOnVideo()
        tipArrives()
        onScreen { controls, _ -> controls.tipButton.performClick() }
        idle()

        onScreen { controls, _ -> controls.tipBanner.performClick() }
        idle()

        onScreen { controls, _ -> assertFalse(controls.tipBanner.isShown) }
    }

    @Test
    fun anotherGuidesTip_startsClosed_withItsDot() {
        launch()
        guideOnVideo()
        tipArrives()
        onScreen { controls, _ -> controls.tipButton.performClick() }
        idle()

        guideOnVideo("guide-2")
        onScreen { controls, _ ->
            assertFalse(controls.tipBanner.isShown)
            assertFalse(controls.tipButtonFrame.isShown)
        }

        tipArrives(guideId = "guide-2", text = "Step back a little.")
        onScreen { controls, _ ->
            assertTrue(controls.tipButtonFrame.isShown)
            assertTrue(controls.tipDot.isShown)
            assertFalse(controls.tipBanner.isShown)
        }
    }

    private companion object {
        const val TEXT = "Move a little closer.\nHold the phone at your waist, tilted up a little.\n" +
            "Leave almost no room above the head."
    }
}
