// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Looper
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
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers

/*
 * The Camera screen's changes (Design 2.6.2, Iter3-A12) on the Galaxy S22's app area, 360 x 705 dp, with the display's
 * frames paused where a test looks inside an animation, since idle() plays each to its end (Testing 2.1.8).
 * A ratio change over a streaming preview: the frame and the held frame at the start, halfway, and at the end, the
 * held frame dimmed and blurred, the grid and the guide hidden throughout, then the held frame fading into the new preview with
 * the grid and the guide back; a stream that never comes back, which the time limit ends; and a view made anew in the
 * middle of a move, which shows the new ratio with the grid and the guide. A lens switch: the grid and the guide stay,
 * and the held frame turns over, top over bottom (at its start, in each half, and at its end), blurred, or only dimmed
 * on Android 10 and 11. A lens change of the zoom:
 * the held frame stays as it is and goes at once (NFR-19). The shutter: a blink, and the new photo's thumbnail growing
 * in, but not the one there as the screen opens. With the phone's animations off, each change is at its end at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class CameraTransitionTest {
    private class Camera(
        val fragment: CameraFragment,
        val controls: FragmentCameraBinding,
        val camera: FakeCameraController,
    )

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private fun camera(): Camera {
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
        val camera = Camera(fragment, FragmentCameraBinding.bind(fragment.requireView()), modules.camera)
        camera.streaming()
        return camera
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

    /** A ratio chosen under the top bar, and the controller's new ratio that follows it. */
    private fun Camera.tapRatio(aspect: AspectRatio) {
        when (aspect) {
            AspectRatio.R4_3 -> controls.aspect34Option
            AspectRatio.R16_9 -> controls.aspect916Option
            AspectRatio.R1_1 -> controls.aspect11Option
        }.performClick()
        camera.state.value = camera.state.value.copy(aspect = aspect)
        idle()
    }

    /** The camera's status in a rebind: STARTING, then READY, which lets a held frame go while the preview streams. */
    private fun Camera.rebind(status: CameraStatus) {
        camera.setStatus(status)
        idle()
    }

    /** The area's height as its margins set it, in dp: what a move sets before the next layout. */
    private fun Camera.areaHeight(): Float {
        val params = controls.previewContainer.layoutParams as ViewGroup.MarginLayoutParams
        return (controls.root.height - params.topMargin - params.bottomMargin) / density()
    }

    private fun Camera.density() = controls.root.resources.displayMetrics.density

    private fun Camera.still(): FloatArray = FloatArray(9).also { controls.aspectStill.imageMatrix.getValues(it) }

    private fun Camera.overlaysShown() = controls.guideOverlay.isVisible() && controls.compositionOverlay.isVisible()

    private fun View.isVisible() = visibility == View.VISIBLE

    private fun play(ms: Long) {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    /** ValueAnimator's duration scale, which the phone's Remove animations sets to 0. */
    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    @Test
    fun aRatioChange_movesTheFrameAndTheHeldFrameWithTheScene_whileTheCameraRebinds() {
        val screen = camera()
        assertEquals(480f, screen.areaHeight(), 0.5f)
        ShadowChoreographer.setPaused(true)

        screen.tapRatio(AspectRatio.R16_9)

        // At the start: the 4:3 frame, the held frame where the preview was, the grid and the guide gone.
        assertTrue(screen.controls.aspectStill.isVisible())
        assertEquals(480f, screen.areaHeight(), 0.5f)
        assertEquals(1f, screen.still()[Matrix.MSCALE_X], 1e-4f)
        assertTrue(screen.controls.previewScrim.isVisible())
        assertFalse(screen.overlaysShown())

        // Halfway: between the two frames, the scene between its two sizes. The still's matrix applies once it is
        // laid out, which a paused display has not done.
        checkNotNull(screen.fragment.frameMove).setCurrentFraction(0.5f)
        assertTrue(screen.areaHeight() in 490f..630f)
        checkNotNull(screen.fragment.frameMove).pause()
        play(50)
        assertTrue(screen.still()[Matrix.MSCALE_X] in 1.05f..1.3f)
        checkNotNull(screen.fragment.frameMove).resume()

        // At the end, while the camera still rebinds: the 9:16 frame, and the scene a third larger and centered in it,
        // dimmed; the grid and the guide wait for the new preview.
        play(500)
        assertEquals(640f, screen.areaHeight(), 0.5f)
        val still = screen.still()
        assertEquals(4f / 3f, still[Matrix.MSCALE_X], 1e-4f)
        assertEquals(-60f, still[Matrix.MTRANS_X] / screen.density(), 0.5f)
        assertEquals(0f, still[Matrix.MTRANS_Y], 0.5f)
        assertEquals(0.5f, screen.controls.previewScrim.alpha, 0.01f)
        assertEquals(16f * screen.density(), screen.fragment.stillBlur, 0.01f)
        assertEquals(0f, screen.controls.aspectStill.rotationX, 0f)
        assertTrue(screen.controls.aspectStill.isVisible())
        assertFalse(screen.overlaysShown())

        // The new preview streams: the held frame fades out over it, and the grid and the guide fade in.
        ShadowChoreographer.setPaused(true)
        screen.rebind(CameraStatus.STARTING)
        screen.rebind(CameraStatus.READY)
        assertTrue(screen.controls.aspectStill.isVisible())
        assertTrue(screen.overlaysShown())
        assertEquals(0f, screen.controls.guideOverlay.alpha, 0f)
        play(500)
        assertFalse(screen.controls.aspectStill.isVisible())
        assertFalse(screen.controls.previewScrim.isVisible())
        assertEquals(1f, screen.controls.guideOverlay.alpha, 0f)
        assertEquals(1f, screen.controls.compositionOverlay.alpha, 0f)
    }

    @Test
    fun aRatioChangeWhoseStreamNeverComesBack_endsAtTheTimeLimit() {
        val screen = camera()
        screen.tapRatio(AspectRatio.R1_1)
        screen.rebind(CameraStatus.STARTING)
        assertTrue(screen.controls.aspectStill.isVisible())

        play(2_500)

        assertFalse(screen.controls.aspectStill.isVisible())
        assertEquals(480f, screen.areaHeight(), 0.5f)
    }

    @Test
    fun aViewMadeAnewDuringAMove_showsTheGridAndTheGuideWithoutAHeldFrame() {
        val screen = camera()
        ShadowChoreographer.setPaused(true)
        screen.tapRatio(AspectRatio.R16_9)
        // The new ratio streams before the frame is in its place, and the screen goes then, as for another screen.
        screen.rebind(CameraStatus.STARTING)
        screen.rebind(CameraStatus.READY)
        val fragments = screen.fragment.parentFragmentManager
        fragments.beginTransaction().detach(screen.fragment).commitNow()
        ShadowChoreographer.setPaused(false)
        fragments.beginTransaction().attach(screen.fragment).commitNow()
        play(500)

        val controls = FragmentCameraBinding.bind(screen.fragment.requireView())
        assertFalse(controls.aspectStill.isVisible())
        assertTrue(controls.guideOverlay.isVisible() && controls.compositionOverlay.isVisible())
        assertEquals(640f, Camera(screen.fragment, controls, screen.camera).areaHeight(), 0.5f)
    }

    @Test
    fun aLensSwitch_keepsTheGridAndTheGuide_andTurnsTheHeldFrameOverBlurred() {
        val screen = camera()
        ShadowChoreographer.setPaused(true)

        screen.controls.switchCameraButton.performClick()
        idle()

        // Flat at the start, toward its edge in the first half, back from the other edge in the second.
        val still = screen.controls.aspectStill
        val flip = checkNotNull(screen.fragment.stillFlip)
        assertEquals(0f, still.rotationX, 0f)
        flip.setCurrentFraction(0.25f)
        assertTrue(still.rotationX in 1f..89f)
        flip.setCurrentFraction(0.75f)
        assertTrue(still.rotationX in -89f..-1f)

        play(500)
        screen.rebind(CameraStatus.STARTING)
        assertEquals(0f, still.rotationX, 0f)
        assertTrue(still.isVisible())
        assertTrue(screen.controls.stillGround.isVisible())
        assertTrue(screen.overlaysShown())
        assertEquals(16f * screen.density(), screen.fragment.stillBlur, 0.01f)
        assertEquals(0.5f, screen.controls.previewScrim.alpha, 0.01f)

        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isVisible())
        assertEquals(0f, screen.fragment.stillBlur, 0f)
    }

    @Test
    fun aLensSwitchOnAndroid10Or11_onlyDimsTheHeldFrame() {
        val screen = camera()

        // RenderEffect is Android 12's: before it, the switch only dims.
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)
        try {
            screen.controls.switchCameraButton.performClick()
            idle()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }

        assertTrue(screen.controls.aspectStill.isVisible())
        assertEquals(0f, screen.fragment.stillBlur, 0f)
        assertEquals(0.5f, screen.controls.previewScrim.alpha, 0.01f)
    }

    @Test
    fun aLensChangeOfTheZoom_leavesTheHeldFrameAsItIs_andTakesItDownAtOnce() {
        val screen = camera()
        ShadowChoreographer.setPaused(true)

        // The controller leaves READY for STARTING while the preview streams, as a pinch across a lens does.
        screen.rebind(CameraStatus.STARTING)
        assertTrue(screen.controls.aspectStill.isVisible())
        assertFalse(screen.controls.previewScrim.isVisible())

        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isVisible())
    }

    @Test
    fun theShutter_blinksThePreview_andTheNewPhotosThumbnailGrowsIn() {
        val screen = camera()
        screen.fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        screen.camera.photo = Uri.parse("content://media/external/images/media/1")
        ShadowChoreographer.setPaused(true)

        screen.controls.shutterButton.performClick()
        assertTrue(screen.controls.previewScrim.isVisible())
        // The thumbnail loads off the main thread.
        Thread.sleep(100)
        idle()
        assertEquals(0.6f, screen.controls.galleryThumbnail.scaleX, 0.01f)

        play(500)
        assertFalse(screen.controls.previewScrim.isVisible())
        assertEquals(1f, screen.controls.galleryThumbnail.scaleX, 0f)
        assertEquals(1f, screen.controls.galleryThumbnail.alpha, 0f)
    }

    @Test
    fun theLastPhotosThumbnail_asTheScreenOpens_justShows() {
        val screen = camera()
        screen.fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        screen.camera.photo = Uri.parse("content://media/external/images/media/1")
        screen.controls.shutterButton.performClick()
        Thread.sleep(100)
        idle()
        play(500)

        // Back from another screen: the view is made again with the photo already there.
        val activity = screen.fragment.requireActivity()
        activity.supportFragmentManager.beginTransaction().detach(screen.fragment).commitNow()
        ShadowChoreographer.setPaused(true)
        activity.supportFragmentManager.beginTransaction().attach(screen.fragment).commitNow()
        screen.fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        Thread.sleep(100)
        idle()

        val thumbnail = FragmentCameraBinding.bind(screen.fragment.requireView()).galleryThumbnail
        assertEquals(1f, thumbnail.scaleX, 0f)
        assertEquals(1f, thumbnail.alpha, 0f)
    }

    @Test
    fun withAnimationsOff_eachChangeIsAtItsEndAtOnce() {
        val screen = camera()
        setAnimatorScale(0f)
        // Nothing may wait for a display frame.
        ShadowChoreographer.setPaused(true)

        screen.tapRatio(AspectRatio.R16_9)
        assertEquals(640f, screen.areaHeight(), 0.5f)
        assertEquals(0.5f, screen.controls.previewScrim.alpha, 0f)
        assertFalse(screen.overlaysShown())
        // The still's matrix applies once it is laid out.
        play(50)
        assertEquals(4f / 3f, screen.still()[Matrix.MSCALE_X], 1e-4f)
        ShadowChoreographer.setPaused(true)

        screen.rebind(CameraStatus.STARTING)
        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isVisible())
        assertFalse(screen.controls.previewScrim.isVisible())
        assertTrue(screen.overlaysShown())
        assertEquals(1f, screen.controls.guideOverlay.alpha, 0f)

        // The other camera: blurred and dimmed at once, without turning over.
        screen.controls.switchCameraButton.performClick()
        assertTrue(screen.controls.aspectStill.isVisible())
        assertEquals(null, screen.fragment.stillFlip)
        assertEquals(0f, screen.controls.aspectStill.rotationX, 0f)
        assertEquals(16f * screen.density(), screen.fragment.stillBlur, 0.01f)
        assertEquals(0.5f, screen.controls.previewScrim.alpha, 0f)
        screen.rebind(CameraStatus.STARTING)
        screen.rebind(CameraStatus.READY)
        assertFalse(screen.controls.aspectStill.isVisible())

        // No blink at the shutter.
        screen.camera.photo = Uri.parse("content://media/external/images/media/1")
        screen.controls.shutterButton.performClick()
        assertFalse(screen.controls.previewScrim.isVisible())
    }
}
