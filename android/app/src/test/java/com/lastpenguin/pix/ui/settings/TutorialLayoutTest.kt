// AI-generated with Claude Code, 2026-11-04, reviewed by Jaewan Park and Sungmin Jo
package com.lastpenguin.pix.ui.settings

import android.Manifest
import android.app.Application
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.settings.TutorialScreen
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.tipsOver
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.CameraFragment
import com.lastpenguin.pix.ui.generation.DescribePoseFragment
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import com.lastpenguin.pix.ui.guide.AddGuideSheet
import com.lastpenguin.pix.ui.session.ShadowSurfaceViewRenderer
import com.lastpenguin.pix.ui.session.SubjectFragment
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The tutorial's tips laid out over each real screen (FR-10.4, NFR-16), on the Galaxy S22's 360 × 705 dp, in English
 * and in Korean, at font scale 1.0 and 1.3 (boundary): every control the screen shows has its ring; every caption, the
 * title, and *Got it* lie inside the screen, whole, and clear of each other and of every ring. Camera, the guide on
 * Camera, and Subject view show every control their tips name (a guide on the video for its edit-mode toggle), and
 * Add a pose guide its saved guides, so each is laid out at its fullest. A ring is a circle around a round control up to
 * the shutter's size and a capsule around a larger one, such as *Add photo*'s tile (boundary).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    qualifiers = "en-w360dp-h705dp-xxhdpi",
    shadows = [ShadowSurfaceViewRenderer::class],
    instrumentedPackages = ["org.webrtc"],
)
class TutorialLayoutTest {
    private var scenario: FragmentScenario<out Fragment>? = null
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        scenario?.close()
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    /** Only [screen]'s tips due. */
    private fun dueOnly(screen: TutorialScreen, camera: FakeCameraController = FakeCameraController()) =
        TestAppContainer(camera = camera, tutorialSeen = TutorialScreen.entries.toSet() - screen).install()

    private fun layer(): CoachMarkLayout {
        var layer: CoachMarkLayout? = null
        scenario!!.onFragment { layer = tipsOver(it) }
        return checkNotNull(layer) { "no tips over the screen" }
    }

    // ---- The screens, each with its tips up -------------------------------------------------------------------------

    private fun previewStreams() {
        scenario!!.onFragment { fragment ->
            val preview = FragmentCameraBinding.bind(fragment.requireView()).previewView
            val field = PreviewView::class.java.getDeclaredField("mPreviewStreamStateLiveData")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(preview) as MutableLiveData<PreviewView.StreamState>).value = PreviewView.StreamState.STREAMING
        }
        idle()
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
    }

    private fun camera(withGuide: Boolean): CoachMarkLayout {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = dueOnly(if (withGuide) TutorialScreen.GUIDE_ON_CAMERA else TutorialScreen.CAMERA)
        if (withGuide) modules.guides.setGuide(guide())
        scenario = launchPrepared<CameraFragment>(R.id.cameraFragment) {}.first
        idle()
        previewStreams()
        return layer()
    }

    private fun sheet(): CoachMarkLayout {
        val modules = dueOnly(TutorialScreen.ADD_GUIDE)
        runBlocking { modules.saved.save(guide(), GuideState()) }
        scenario = launchFragment<AddGuideSheet>(
            fragmentArgs = bundleOf(AddGuideSheet.ARG_FROM_SUBJECT to false),
            themeResId = R.style.Theme_Pix,
        )
        idleUntil { layer().placed.count { it != null } == 3 }
        return layer()
    }

    private fun describe(): CoachMarkLayout {
        dueOnly(TutorialScreen.GENERATE_POSES, FakeCameraController().apply { frame = bitmap(300, 400) })
        scenario = launchPrepared<DescribePoseFragment>(R.id.describePoseFragment) { activity ->
            activity.viewModel<GenerationViewModel>().apply {
                beginScene()
                takeScene()
                useScene()
            }
        }.first
        idle()
        return layer()
    }

    private fun subjectView(): CoachMarkLayout {
        val modules = dueOnly(TutorialScreen.SUBJECT_VIEW)
        val launched = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        scenario = launched
        launched.attachNav(R.id.subjectFragment)
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Test Account 1"), Role.SUBJECT))
        modules.mirror.setGuide(guide(withReference = false))
        modules.video.deliverFrame()
        // The photographer's camera with a flash and an exposure range, so every control shows.
        modules.session.deliver(
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(1f, 2f), -4..4, 0.5f, true)),
        )
        // The screen checks its video every 500 ms, then the tips wait their moment.
        waitMs(600)
        waitMs(TutorialFragment.AFTER_PICTURE_MS)
        return layer()
    }

    // ---- The checks -------------------------------------------------------------------------------------------------

    private fun View.rect(): RectF {
        val origin = IntArray(2).also((parent as View)::getLocationInWindow)
        val at = IntArray(2).also(::getLocationInWindow)
        val left = (at[0] - origin[0]).toFloat()
        val top = (at[1] - origin[1]).toFloat()
        return RectF(left, top, left + width, top + height)
    }

    private fun View.label(): String = (this as? TextView)?.text?.toString() ?: "Got it"

    /** At font scale 1.0 and 1.3, the tips [show] puts up have [rings] rings and fit (see the class comment). */
    private fun assertFits(rings: Int, show: () -> CoachMarkLayout) {
        for (scale in listOf(1f, 1.3f)) {
            RuntimeEnvironment.setFontScale(scale)
            val layer = show()
            val label = "font scale $scale"
            assertTrue(label, layer.width > 0 && layer.height > 0)
            val screen = RectF(0f, 0f, layer.width.toFloat(), layer.height.toFloat())
            val ringed = layer.placed.filterNotNull()
            assertEquals(label, rings, ringed.size)
            val texts = layer.children.filter { it.isVisible }.toList()
            for (text in texts) {
                val rect = text.rect()
                assertTrue("$label: \"${text.label()}\" at $rect is off the screen", screen.contains(rect))
                if (text is TextView) {
                    val drawn = text.layout.getLineBottom(text.lineCount - 1)
                    assertTrue("$label: \"${text.label()}\" is cut", drawn <= text.height)
                }
                for (mark in ringed) {
                    assertFalse(
                        "$label: \"${text.label()}\" covers the ring of \"${mark.caption.text}\"",
                        RectF.intersects(rect, mark.ring),
                    )
                }
            }
            for ((index, one) in texts.withIndex()) {
                for (other in texts.drop(index + 1)) {
                    assertFalse(
                        "$label: \"${one.label()}\" and \"${other.label()}\" overlap",
                        RectF.intersects(one.rect(), other.rect()),
                    )
                }
            }
            scenario?.close()
            scenario = null
        }
    }

    /** The ring of the tip whose caption is [caption], among those [layer] placed. */
    private fun ringOf(layer: CoachMarkLayout, caption: Int): CoachMarkLayout.Placed =
        layer.placed.filterNotNull().single { it.caption.text == app.getString(caption) }

    @Test
    fun aRoundControl_getsACircle_upToTheShutter_andALargerOneACapsule() {
        assertTrue(ringOf(camera(withGuide = false), R.string.tutorial_shutter).round)
        scenario?.close()
        assertFalse(ringOf(sheet(), R.string.tutorial_upload).round)
    }

    @Test
    fun camera_fits() = assertFits(5) { camera(withGuide = false) }

    @Test
    @Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
    fun camera_fitsInKorean() = assertFits(5) { camera(withGuide = false) }

    @Test
    fun theGuideOnCamera_fits() = assertFits(4) { camera(withGuide = true) }

    @Test
    @Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
    fun theGuideOnCamera_fitsInKorean() = assertFits(4) { camera(withGuide = true) }

    @Test
    fun addAPoseGuide_fits() = assertFits(3, ::sheet)

    @Test
    @Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
    fun addAPoseGuide_fitsInKorean() = assertFits(3, ::sheet)

    @Test
    fun generatePoses_fits() = assertFits(3, ::describe)

    @Test
    @Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
    fun generatePoses_fitsInKorean() = assertFits(3, ::describe)

    @Test
    fun subjectView_fits() = assertFits(4, ::subjectView)

    @Test
    @Config(qualifiers = "ko-w360dp-h705dp-xxhdpi")
    fun subjectView_fitsInKorean() = assertFits(4, ::subjectView)
}
