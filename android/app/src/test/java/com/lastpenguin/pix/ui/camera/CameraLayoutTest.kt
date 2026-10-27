// AI-generated with Claude Code, 2026-10-27, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.session.ShadowSurfaceViewRenderer
import com.lastpenguin.pix.ui.session.SubjectFragment
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The camera screens' layout on the Galaxy S22's app area, 360 x 705 dp (R&S 6.2, 6.6; Testing Documentation 2.1.6).
 * Ratios: 4:3, 16:9, and 1:1, each with the preview right below the top bar, the shutter's row in one place, and the
 * zoom row at the bottom of the image; 16:9 with every control of that row above the image's bottom edge. States of
 * the top bar: no guide, a guide with a pose tip, and a session, at font scale 1 and 1.3, none overlapping. A guide's
 * panel in the space below a 4:3 image. The frame held while the ratio changes, in its place and the old ratio's
 * area until the new one streams; a held 1:1 frame dimmed once. The zoom readout's tap
 * area, the row's height, and every control's, at least 48 dp (NFR-16). Subject view's video placed the same way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    qualifiers = "w360dp-h705dp-xxhdpi",
    shadows = [ShadowSurfaceViewRenderer::class],
    instrumentedPackages = ["org.webrtc"],
)
class CameraLayoutTest {
    private class Camera(
        val activity: AppCompatActivity,
        val fragment: CameraFragment,
        val controls: FragmentCameraBinding,
        val modules: TestAppContainer,
    )

    private fun camera(fontScale: Float = 1f): Camera {
        if (fontScale != 1f) RuntimeEnvironment.setFontScale(fontScale)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val modules = TestAppContainer(camera = FakeCameraController(hasFlash = true, exposureRange = -6..6)).install()
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.Theme_Pix)
        }
            .setup().get()
        val nav = TestNavHostController(activity).apply { setGraph(R.navigation.nav_graph) }
        Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
        val fragment = CameraFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idle()
        return Camera(activity, fragment, FragmentCameraBinding.bind(fragment.requireView()), modules)
    }

    private fun Camera.ratio(aspect: AspectRatio) {
        val camera = modules.camera as FakeCameraController
        camera.state.value = camera.state.value.copy(aspect = aspect)
        idle()
    }

    /** A generated pose's guide with its tip arrived and the banner closed: the light bulb shows. */
    private fun Camera.guideWithTip() {
        val guide = ReferenceGuide(
            id = "g1",
            mask = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888),
            outline = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) },
            aspect = 0.5f,
            source = GuideSource.GENERATED,
            anchor = NormalizedRect(0.2f, 0.1f, 0.8f, 0.95f),
        )
        modules.tips.prepare(guide, bitmap(), TipSource(bitmap(), "Walk past the railing"))
        modules.guides.setGuide(guide)
        idle()
        modules.tipper.requests.last().tip("Hold the phone at your chest and keep it level.")
        idle()
        val now = SystemClock.uptimeMillis()
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 500f, 900f, 0))
        controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 500f, 900f, 0))
        idle()
    }

    private fun Camera.session() {
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Junhyeong"), Role.PHOTOGRAPHER))
        idle()
    }

    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    /** Where [view] is drawn on the screen, its translation included, in dp. */
    private fun View.box(): Rect {
        val at = IntArray(2)
        getLocationInWindow(at)
        val density = resources.displayMetrics.density
        fun dp(px: Number) = (px.toFloat() / density).roundToInt()
        return Rect(dp(at[0]), dp(at[1]), dp(at[0] + width), dp(at[1] + height))
    }

    // ---- Ratios -----------------------------------------------------------------------------

    @Test
    fun eachRatio_thePreviewBelowTheTopBar_andTheShuttersRowInOnePlace() {
        val screen = camera()
        val shutters = mutableListOf<Rect>()
        for ((aspect, height) in listOf(AspectRatio.R4_3 to 480, AspectRatio.R16_9 to 640, AspectRatio.R1_1 to 480)) {
            screen.ratio(aspect)
            val preview = screen.controls.previewContainer.box()
            // The bar's 48 dp touch height reaches 4 dp over the image, which starts below what the bar draws.
            assertEquals("$aspect", screen.controls.topBar.box().bottom - TOP_BAR_OVERHANG_DP.toInt(), preview.top)
            assertEquals("$aspect", 360, preview.width())
            assertEquals("$aspect", height, preview.height())
            shutters += screen.controls.shutterButton.box()
        }
        assertEquals(1, shutters.distinct().size)
        assertEquals(68, shutters.first().height())
    }

    @Test
    fun sixteenByNine_everyControlBelowEndsAboveTheImagesBottom() {
        val screen = camera()
        screen.ratio(AspectRatio.R16_9)

        val imageBottom = screen.controls.previewContainer.box().bottom
        val row = screen.controls.captureControls
        for (view in listOf(
            screen.controls.guideButton,
            screen.controls.shutterButton,
            screen.controls.galleryButton,
        )) {
            assertTrue(
                "${view.resources.getResourceEntryName(view.id)} ends above $imageBottom",
                view.box().bottom < imageBottom,
            )
        }
        assertTrue(row.box().top > screen.controls.previewContainer.box().top)
    }

    @Test
    fun theZoomRow_sitsAtTheBottomOfTheImage_inEachRatio() {
        val screen = camera()
        val margin = ZOOM_ROW_MARGIN_DP.toInt()
        screen.ratio(AspectRatio.R4_3)
        assertEquals(524 - margin, screen.controls.zoomRow.box().bottom)

        // 1:1 shows inside the 4:3 area: the row is at the bottom of the square, not of the area.
        screen.ratio(AspectRatio.R1_1)
        val area = screen.controls.previewContainer.box()
        val square = fitViewfinderFrame(area.width().toFloat(), area.height().toFloat(), AspectRatio.R1_1.frameAspect)
        assertEquals(area.top + square.bottom.roundToInt() - margin, screen.controls.zoomRow.box().bottom)

        // Under 16:9 the controls cover the image's bottom: the row stays above them.
        screen.ratio(AspectRatio.R16_9)
        assertTrue(screen.controls.zoomRow.box().bottom <= screen.controls.bottomPanel.box().top - margin)
    }

    @Test
    fun aGuidesPanel_fitsInTheSpaceBelowAFourByThreeImage_underTheZoomRow() {
        val screen = camera()
        screen.guideWithTip()

        val image = screen.controls.previewContainer.box()
        val panel = screen.controls.guideControls.box()
        assertTrue(panel.top >= image.bottom)
        assertTrue(panel.bottom <= screen.controls.bottomPanel.box().top)
        assertTrue(screen.controls.zoomRow.box().bottom <= image.bottom)
        assertTrue(screen.controls.tipButtonFrame.isShown)
    }

    @Test
    fun aHeldFrame_keepsItsPlace_whileANewRatioResizesThePreview() {
        val screen = camera()
        val still = screen.controls.aspectStill
        val area = screen.controls.previewContainer
        // The 4:3 preview's last frame, as PreviewHold puts it over the preview.
        val frame = Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        still.setImageBitmap(heldFrame(frame, AspectRatio.R4_3))
        still.visibility = View.VISIBLE

        screen.ratio(AspectRatio.R16_9)
        assertTrue(still.height > frame.height)
        val drawn = Bitmap.createBitmap(still.width, still.height, Bitmap.Config.ARGB_8888)
        still.draw(Canvas(drawn))

        // Where it was: from the top of the taller area down to the old bottom, and nothing below.
        assertEquals(Color.RED, drawn.getPixel(still.width / 2, 0))
        assertEquals(Color.RED, drawn.getPixel(still.width / 2, frame.height - 1))
        assertEquals(Color.TRANSPARENT, drawn.getPixel(still.width / 2, frame.height + 1))
    }

    /** A preview that streams and has a last frame to hold, which PreviewView has not without a surface. */
    @Suppress("UNCHECKED_CAST")
    private fun Camera.streaming() {
        val area = controls.previewContainer
        fragment.previewFrame = { Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888) }
        val field = PreviewView::class.java.getDeclaredField("mPreviewStreamStateLiveData")
        field.isAccessible = true
        (field.get(controls.previewView) as MutableLiveData<PreviewView.StreamState>).value =
            PreviewView.StreamState.STREAMING
        idle()
    }

    /** The camera's status in a rebind: STARTING, then READY, which lets a held frame go while the preview streams. */
    private fun Camera.rebind(status: CameraStatus) {
        (modules.camera as FakeCameraController).setStatus(status)
        idle()
    }

    @Test
    fun aRatioChange_keepsTheHeldFramesPlace_untilTheNewRatioStreams() {
        val screen = camera()
        screen.ratio(AspectRatio.R16_9)
        screen.streaming()

        // 16:9 to 1:1: the held 16:9 frame keeps its 640 dp, though 1:1 shows in the 480 dp 4:3 area.
        screen.controls.aspectButton.performClick()
        screen.ratio(AspectRatio.R1_1)
        screen.rebind(CameraStatus.STARTING)
        assertTrue(screen.controls.aspectStill.isShown)
        assertEquals(640, screen.controls.previewContainer.box().height())

        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isShown)
        assertEquals(480, screen.controls.previewContainer.box().height())
    }

    @Test
    fun aLensSwitchIn1To1_dimsTheHeldFramesOutsideOnce() {
        val screen = camera()
        screen.ratio(AspectRatio.R1_1)
        screen.streaming()
        assertTrue(screen.controls.compositionOverlay.dimOutside)

        // The held frame carries the dim, and the grid stays over it without its own.
        screen.rebind(CameraStatus.STARTING)
        assertTrue(screen.controls.aspectStill.isShown)
        assertTrue(screen.controls.compositionOverlay.isShown)
        assertFalse(screen.controls.compositionOverlay.dimOutside)

        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isShown)
        assertTrue(screen.controls.compositionOverlay.dimOutside)
    }

    // ---- The top bar ------------------------------------------------------------------------

    private fun Camera.assertTopBarFits(state: String) {
        val bar = controls.topBar.box()
        val shown = listOf(
            controls.flashButton,
            controls.aspectButton,
            controls.shootTogetherButton,
            controls.endSessionButton,
        ).filter { it.isShown }.map { it.resources.getResourceEntryName(it.id) to it.box() }
        assertTrue(state, shown.size >= 3)
        for ((name, box) in shown) assertTrue("$state: $name inside the bar", bar.contains(box))
        for (i in shown.indices) {
            for (j in i + 1 until shown.size) {
                assertFalse(
                    "$state: ${shown[i].first} and ${shown[j].first}",
                    Rect.intersects(shown[i].second, shown[j].second),
                )
            }
        }
    }

    @Test
    fun theTopBar_fitsWithoutOverlap_inEachState() {
        val screen = camera()
        screen.assertTopBarFits("no guide")
        screen.guideWithTip()
        screen.assertTopBarFits("a guide with a tip")
        screen.session()
        screen.assertTopBarFits("a session")
    }

    @Test
    fun theTopBar_fitsWithoutOverlap_atFontScale1_3() {
        val screen = camera(fontScale = 1.3f)
        screen.guideWithTip()
        screen.assertTopBarFits("a guide with a tip, font 1.3")
        screen.session()
        screen.assertTopBarFits("a session, font 1.3")
    }

    // ---- The zoom readout -------------------------------------------------------------------

    /** The readout takes a tap across the zoom row's height and at least 48 dp wide; its pill stays 28 dp tall. */
    private fun assertReadoutTapArea(readout: View, row: View) {
        val box = readout.box()
        assertEquals(row.box().height(), box.height())
        assertTrue("${box.width()} dp wide", box.width() >= 48)
        val inset = Rect().also { readout.background.getPadding(it) }
        val density = readout.resources.displayMetrics.density
        assertEquals(28, ((readout.height - inset.top - inset.bottom) / density).roundToInt())
    }

    @Test
    fun theZoomReadout_takesATapAcrossTheRowsHeight_aboveItsPill() {
        val screen = camera()
        assertReadoutTapArea(screen.controls.zoomReadout, screen.controls.zoomRow)

        // 4 dp below the readout's top: above the pill, which starts 10 dp down.
        val readout = IntArray(2).also { screen.controls.zoomReadout.getLocationInWindow(it) }
        val root = IntArray(2).also { screen.controls.root.getLocationInWindow(it) }
        val density = screen.controls.root.resources.displayMetrics.density
        val x = readout[0] - root[0] + screen.controls.zoomReadout.width / 2f
        val y = readout[1] - root[1] + 4 * density
        val now = SystemClock.uptimeMillis()
        screen.controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        screen.controls.root.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0))
        idle()

        assertEquals(listOf(2f), (screen.modules.camera as FakeCameraController).requests)
    }

    // ---- Touch targets (NFR-16) -------------------------------------------------------------

    /** Every control shown in [root] takes taps on at least 48 x 48 dp, whatever it draws. */
    private fun assertEveryControlAtLeast48(root: View, state: String) {
        val small = mutableListOf<String>()
        var controls = 0
        fun walk(view: View) {
            if (!view.isShown) return
            if (view.isClickable && view.id != View.NO_ID) {
                controls++
                val box = view.box()
                if (box.width() < 48 || box.height() < 48) {
                    small += "${view.resources.getResourceEntryName(view.id)} ${box.width()} x ${box.height()}"
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        assertTrue("$state: $controls controls", controls >= 5)
        assertTrue("$state: $small", small.isEmpty())
    }

    private fun Camera.assertEveryControlAtLeast48(font: String) {
        assertEveryControlAtLeast48(controls.root, "no guide, $font")
        guideWithTip()
        session()
        assertEveryControlAtLeast48(controls.root, "a guide with a tip and a session, $font")
    }

    @Test
    fun everyControl_takesTapsOnAtLeast48dp() = camera().assertEveryControlAtLeast48("font scale 1")

    @Test
    fun everyControl_takesTapsOnAtLeast48dp_atFontScale1_3() =
        camera(fontScale = 1.3f).assertEveryControlAtLeast48("font scale 1.3")

    // ---- Subject view -----------------------------------------------------------------------

    @Test
    fun subjectView_placesTheVideoLikeTheCamera_withTheZoomRowAboveTheCaption() {
        val modules = TestAppContainer().install()
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.Theme_Pix)
        }
            .setup().get()
        val fragment = SubjectFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.session.deliver(
            SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(0.6f, 1f, 2f), -6..6, 0.5f, true)),
        )
        modules.session.deliver(
            SessionMessage.CameraStateUpdate(
                zoom = 1f,
                by = Role.PHOTOGRAPHER,
                final = true,
                aspect = AspectRatio.R16_9,
            ),
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        val binding = FragmentSubjectBinding.bind(fragment.requireView())

        val video = binding.videoContainer.box()
        assertEquals(binding.topBar.box().bottom - TOP_BAR_OVERHANG_DP.toInt(), video.top)
        assertEquals(640, video.height())
        assertTrue(binding.zoomRow.box().bottom <= binding.bottomPanel.box().top)
        assertTrue(binding.liveDot.isShown)
        assertReadoutTapArea(binding.zoomReadout, binding.zoomRow)

        binding.editGuideButton.visibility = View.VISIBLE
        binding.brightnessButton.visibility = View.VISIBLE
        binding.brightnessPanel.visibility = View.VISIBLE
        idle()
        assertEveryControlAtLeast48(binding.root, "Subject view")
    }
}
