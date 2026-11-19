// AI-generated with Claude Code, 2026-11-18, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.graphics.drawable.RotateDrawable
import android.os.Looper
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.Turnable
import com.lastpenguin.pix.ui.camera.TurningButton
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Subject view's icons turning with the subject's own phone (FR-1.15, #207, R&S 6.6, Design 2.6.2) on the Galaxy
 * S22's app area, 360 x 705 dp.
 * Partitions (equivalence): the phone held at 0, 90, 180, and 270 degrees. What turns: the top bar's icons, the
 * brightness, tip, save, and guide-edit buttons' icons, the zoom readout as a word, Guide and Pick a pose each with
 * its label, and the photo thumbnail. What stays: Leave, the video and what is drawn over it, the reference
 * thumbnail, and every control's place and size. The turn is this phone's own: nothing is sent to the photographer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    qualifiers = "w360dp-h705dp-xxhdpi",
    shadows = [ShadowSurfaceViewRenderer::class],
    instrumentedPackages = ["org.webrtc"],
)
class SubjectControlTurnTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun subject(): Pair<TestAppContainer, FragmentSubjectBinding> {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame(720, 960)
        // The video's check runs every 500 ms on the main looper.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        var binding: FragmentSubjectBinding? = null
        scenario!!.onFragment { binding = FragmentSubjectBinding.bind(it.requireView()) }
        return modules to binding!!
    }

    /** The controls that turn what they draw: the buttons' icons and the zoom readout. */
    private fun FragmentSubjectBinding.turningButtons(): List<View> =
        listOf(flashButton, settingsButton, brightnessButton, tipButton, saveGuideButton, editGuideButton, zoomReadout)

    /** Guide and Pick a pose turn with their labels, each as one block; the thumbnail turns whole. */
    private fun FragmentSubjectBinding.turningViews(): List<View> = listOf(
        guideButton,
        pickPoseButton,
        galleryThumbnail,
    )

    private fun FragmentSubjectBinding.stayingViews(): List<View> = listOf(
        leaveButton,
        zoomReadout,
        galleryButton,
        videoContainer,
        guideOverlay,
        compositionOverlay,
        referenceThumbnail,
        topBar,
        bottomPanel,
    )

    private fun FragmentSubjectBinding.places(): List<List<Int>> =
        (turningButtons() + turningViews() + stayingViews()).map { listOf(it.left, it.top, it.right, it.bottom) }

    private fun TurningButton.drawnAt(): Float = (icon as RotateDrawable).level * 360f / 10_000f

    @Test
    fun heldEachWay_theIconsStandUpright_theRestStays_andNothingIsSent() {
        val (modules, screen) = subject()
        val places = screen.places()
        val sent = modules.session.sent.toList()

        for ((turn, degrees) in listOf(90 to -90f, 180 to 180f, 270 to 90f, 0 to 0f)) {
            modules.turns.set(turn)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Motion.TURN_MS * 2))

            for (control in screen.turningButtons()) {
                val name = screen.root.resources.getResourceEntryName(control.id)
                assertEquals("$turn $name", degrees, (control as Turnable).turn, 0f)
                if (control is TurningButton) {
                    assertEquals("$turn $name", Math.floorMod(degrees.toInt(), 360).toFloat(), control.drawnAt(), 0.05f)
                }
            }
            for (view in screen.turningViews()) {
                assertEquals("$turn", degrees, view.rotation, 0f)
            }
            for (view in screen.stayingViews()) {
                assertEquals("$turn ${screen.root.resources.getResourceEntryName(view.id)}", 0f, view.rotation, 0f)
            }
            assertEquals("$turn", places, screen.places())
        }
        assertEquals(sent, modules.session.sent)
    }
}
