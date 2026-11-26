// AI-generated with Claude Code, 2026-11-24, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.imageview.ShapeableImageView
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.session.FakeCameraController
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.OwnActivities
import com.lastpenguin.pix.ui.PixViewModels
import com.lastpenguin.pix.ui.ScreenTestSupport.guide
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.generation.GenerationViewModel
import java.time.Duration
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
 * in, but not the one there as the screen opens. The scene photo for poses (#210) starting and ending: the title and the
 * panel at their first frame, the flow's bar from empty, the shutter turning, the preview staying where it is under
 * the camera's own bar (#231), a change of a bar's height placing it at once, and the panel fading out at a touch. The
 * reference thumbnail (#237): hidden, shown again, enlarged, and made small, each from where it was, at its first
 * frame and at its end. With the phone's animations off, each change is at its end at once, the thumbnail's too.
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

    @get:Rule
    val activities = OwnActivities()

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private lateinit var modules: TestAppContainer

    private fun camera(): Camera {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        modules = TestAppContainer(camera = FakeCameraController(hasFlash = true, exposureRange = -6..6)).install()
        val activity = activities.start(R.style.Theme_Pix)
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

    /** The preview's top as its margin sets it, in dp: where it is laid out, before any slide. */
    private fun Camera.previewTop(): Float =
        (controls.previewContainer.layoutParams as ViewGroup.MarginLayoutParams).topMargin / density()

    /** The scene photo for poses, as a situation's tile on *Add a pose guide* starts it (#210, #243). */
    private fun Camera.generation(): GenerationViewModel =
        ViewModelProvider(fragment.requireActivity(), PixViewModels.Factory)[GenerationViewModel::class.java]

    private fun Camera.beginScene() {
        generation().beginScene()
        idle()
    }

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
    fun theScenePhotoStep_startsAndEnds_asAChange() {
        val screen = camera()
        val controls = screen.controls
        val top = screen.previewTop()
        ShadowChoreographer.setPaused(true)

        screen.beginScene()
        // Its first frame: the title and the panel not in yet, the panel lower, the flow's bar empty, and the shutter
        // starting to turn.
        assertEquals(0f, controls.sceneTitle.alpha, 0f)
        assertEquals(0f, controls.sceneGuide.alpha, 0f)
        assertEquals(12f, controls.sceneGuide.translationY / screen.density(), 0.01f)
        assertEquals(1, controls.sceneProgress.step)
        assertEquals(0f, controls.sceneProgress.filled, 0f)
        assertTrue(checkNotNull(screen.fragment.shutterTurn).isRunning)
        // The bar is the camera's own (#231): the preview stays where it is.
        play(16)
        assertEquals(top, screen.previewTop(), 0.5f)
        assertEquals(0f, controls.previewContainer.translationY, 0f)

        play(500)
        assertEquals(top, screen.previewTop(), 0.5f)
        assertEquals(1f, controls.sceneTitle.alpha, 0f)
        assertEquals(1f, controls.sceneGuide.alpha, 0f)
        assertEquals(0f, controls.sceneGuide.translationY, 0f)
        assertEquals(0.25f, controls.sceneProgress.filled, 0.001f)
        assertEquals(R.drawable.bg_shutter_scene, controls.shutterButton.tag)
        assertTrue(controls.shutterButton.background is StateListDrawable)

        // Back out to the camera: the flow's bar and the panel go at once, the shutter turns white, and the preview
        // stays where it is.
        ShadowChoreographer.setPaused(true)
        controls.sceneBackButton.performClick()
        idle()
        assertFalse(controls.sceneProgress.isVisible())
        assertFalse(controls.sceneGuide.isVisible())
        assertTrue(checkNotNull(screen.fragment.shutterTurn).isRunning)
        play(16)
        assertEquals(top, screen.previewTop(), 0.5f)
        assertEquals(0f, controls.previewContainer.translationY, 0f)

        play(500)
        assertEquals(R.drawable.bg_shutter, controls.shutterButton.tag)
        assertTrue(controls.shutterButton.background is StateListDrawable)

        // A change of the bars' heights, a larger font for one, places the preview at once.
        controls.topBar.minHeight = controls.topBar.height + (8 * screen.density()).toInt()
        play(32)
        assertEquals(top + 8f, screen.previewTop(), 0.5f)
        assertEquals(0f, controls.previewContainer.translationY, 0f)
    }

    // The scene photo's panel at a touch (#231): it fades out in Motion.HIDE_MS and is gone, also when the touch
    // changes something else on the step, and at once with the phone's animations off.
    @Test
    fun theScenePhotosPanel_fadesOutAtATouch() {
        val screen = camera()
        val controls = screen.controls
        screen.beginScene()
        play(500)
        assertEquals(1f, controls.sceneGuide.alpha, 0f)
        ShadowChoreographer.setPaused(true)

        screen.generation().dismissSceneHint()
        idle()
        // Its first frame: still there, on its way out.
        assertTrue(controls.sceneGuide.isVisible())

        // The same touch turned the light on, which renders the step again: the fade goes on.
        screen.generation().toggleLight()
        idle()
        assertTrue(controls.sceneGuide.isVisible())

        play(500)
        assertFalse(controls.sceneGuide.isVisible())
    }

    // The reference thumbnail (#237): it settles into its place, its size, and its fade from where it was, in
    // Motion.THUMBNAIL_MS, laid out at its new size at once and drawn at its old one first; halfway at its middle; a
    // render for the same place leaves the glide running, and a new place starts a new one.

    /** A guide with a reference photo on the camera: its small thumbnail, 54 x 72 dp, in the top-left corner. */
    private fun Camera.thumbnail(): View {
        modules.guides.setGuide(guide())
        play(500)
        assertEquals(12f * density(), controls.referenceThumbnail.translationX, 0f)
        return controls.referenceThumbnail
    }

    @Test
    fun theReferenceThumbnail_hiddenAndShown_slidesAndFadesFromWhereItWas() {
        val screen = camera()
        val thumbnail = screen.thumbnail()
        val corner = 12f * screen.density()
        val hidden = (16f - 54f) * screen.density()
        ShadowChoreographer.setPaused(true)

        assertTrue(ViewCompat.performAccessibilityAction(thumbnail, R.id.hide_reference, null))
        idle()
        // Its first frame: still whole in its corner.
        assertEquals(corner, thumbnail.translationX, 0f)
        assertEquals(1f, thumbnail.alpha, 0f)
        // At its end: only its peek in the preview, see-through, under its handle.
        play(500)
        assertEquals(hidden, thumbnail.translationX, 0f)
        assertEquals(REFERENCE_HIDDEN_ALPHA, thumbnail.alpha, 0f)
        assertTrue(screen.controls.referenceHandle.isVisible())

        ShadowChoreographer.setPaused(true)
        thumbnail.performClick()
        idle()
        assertEquals(hidden, thumbnail.translationX, 0f)
        play(500)
        assertEquals(corner, thumbnail.translationX, 0f)
        assertEquals(1f, thumbnail.alpha, 0f)
        assertFalse(screen.controls.referenceHandle.isVisible())
    }

    @Test
    fun theReferenceThumbnail_enlargedAndMadeSmall_growsAndShrinksFromTheSizeItHad() {
        val screen = camera()
        val thumbnail = screen.thumbnail()
        val small = thumbnail.layoutParams.width
        ShadowChoreographer.setPaused(true)

        thumbnail.performClick()
        idle()
        // Laid out at its new size at once, and drawn at its old size until the first frame.
        val large = thumbnail.layoutParams.width
        assertEquals(180f * screen.density(), large.toFloat(), 1f)
        assertEquals(small.toFloat() / large, thumbnail.scaleX, 0.001f)
        assertEquals(thumbnail.scaleX, thumbnail.scaleY, 0.001f)
        // It scales about its middle, so it is placed for its left side to stay in the corner.
        assertEquals(12f * screen.density(), thumbnail.translationX + (large - small) / 2f, 0.5f)
        play(500)
        assertEquals(1f, thumbnail.scaleX, 0f)

        // Made small, it stays laid out large and is drawn smaller on the way, and is laid out small at its end.
        ShadowChoreographer.setPaused(true)
        thumbnail.performClick()
        idle()
        assertEquals(large, thumbnail.layoutParams.width)
        assertEquals(1f, thumbnail.scaleX, 0.001f)
        play(500)
        assertEquals(small, thumbnail.layoutParams.width)
        assertEquals(1f, thumbnail.scaleX, 0f)
    }

    @Test
    fun theReferenceThumbnailsGlide_isHalfwayAtItsMiddle_andARenderForTheSamePlaceLeavesItRunning() {
        val screen = camera()
        val thumbnail = screen.thumbnail()
        val corner = 12f * screen.density()
        val small = thumbnail.layoutParams.width
        ShadowChoreographer.setPaused(true)

        thumbnail.performClick()
        idle()
        val glide = checkNotNull(thumbnail.referenceGlide)
        val large = thumbnail.layoutParams.width
        // Halfway through its time, eased: its size and its left side are as far along as the curve is.
        glide.setCurrentFraction(0.5f)
        val along = Motion.STANDARD.getInterpolation(0.5f)
        val width = small + (large - small) * along
        assertEquals(width / large, thumbnail.scaleX, 0.001f)
        assertEquals(corner, thumbnail.translationX + (large - width) / 2f, 0.5f)

        // Another state change renders the screen again: the glide goes on, not anew from where it is.
        modules.guides.update(final = true) { it.copy(opacity = 0.7f) }
        idle()
        assertSame(glide, thumbnail.referenceGlide)
        // A new place is a new glide.
        assertTrue(ViewCompat.performAccessibilityAction(thumbnail, R.id.move_reference_top_right, null))
        idle()
        assertNotSame(glide, thumbnail.referenceGlide)

        play(500)
        assertNull(thumbnail.referenceGlide)
        assertEquals(1f, thumbnail.scaleX, 0f)
        assertEquals(screen.controls.previewContainer.width - corner - large, thumbnail.translationX, 0f)
    }

    /** Where the thumbnail is drawn: its layout size at its scale, about its middle. */
    private fun View.drawn(): RectF {
        val width = layoutParams.width * abs(scaleX)
        val height = layoutParams.height * scaleY
        val left = translationX + (layoutParams.width - width) / 2f
        val top = translationY + (layoutParams.height - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    /** The thumbnail's corner radius and line as drawn: the shape's own, at the scale the view is drawn at. */
    private fun View.drawnShape(): Pair<Float, Float> {
        val image = this as ShapeableImageView
        val box = RectF(0f, 0f, layoutParams.width.toFloat(), layoutParams.height.toFloat())
        return image.shapeAppearanceModel.topLeftCornerSize.getCornerSize(box) * scaleY to image.strokeWidth * scaleY
    }

    private fun tap(view: View, action: Int) {
        val event = MotionEvent.obtain(1_000, 1_000, action, 5f, 5f, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun theReferenceThumbnail_enlargedAndMadeSmallAgainAndAgain_keepsItsCornerInPlace_alsoTurnedBackHalfway() {
        val screen = camera()
        val thumbnail = screen.thumbnail()
        val corners = listOf(
            null to { r: RectF -> r.left to r.top },
            R.id.move_reference_top_right to { r: RectF -> r.right to r.top },
            R.id.move_reference_bottom_right to { r: RectF -> r.right to r.bottom },
            R.id.move_reference_bottom_left to { r: RectF -> r.left to r.bottom },
        )
        for ((move, corner) in corners) {
            ShadowChoreographer.setPaused(false)
            if (move != null) assertTrue(ViewCompat.performAccessibilityAction(thumbnail, move, null))
            play(500)
            val small = thumbnail.drawn()
            val held = corner(small)
            val shape = thumbnail.drawnShape()
            assertEquals(12f * screen.density(), shape.first, 0.01f)
            assertEquals(1f * screen.density(), shape.second, 0.01f)
            fun assertHeld(what: String) {
                val now = corner(thumbnail.drawn())
                assertEquals("$move $what: x", held.first, now.first, 0.6f)
                assertEquals("$move $what: y", held.second, now.second, 0.6f)
                // Its corners stay as round and its line as thin as at rest: nothing changes at the tap but its size.
                assertEquals("$move $what: corner radius", shape.first, thumbnail.drawnShape().first, 0.05f)
                assertEquals("$move $what: line", shape.second, thumbnail.drawnShape().second, 0.05f)
            }
            ShadowChoreographer.setPaused(true)

            // Larger: it grows out of its corner, which stays where it is, with no step at the start.
            tap(thumbnail, MotionEvent.ACTION_DOWN)
            tap(thumbnail, MotionEvent.ACTION_UP)
            idle()
            val grow = checkNotNull(thumbnail.referenceGlide)
            assertEquals(small.width(), thumbnail.drawn().width(), 0.6f)
            assertHeld("growing at its start")
            val largeLayout = thumbnail.layoutParams.width
            var last = small.width()
            for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                grow.setCurrentFraction(fraction)
                assertHeld("growing at $fraction")
                assertTrue(thumbnail.drawn().width() > last)
                last = thumbnail.drawn().width()
            }

            // Tapped again halfway: the press does not stop it, and it turns back from the size it has then.
            grow.setCurrentFraction(0.5f)
            val halfway = thumbnail.drawn()
            tap(thumbnail, MotionEvent.ACTION_DOWN)
            assertSame(grow, thumbnail.referenceGlide)
            tap(thumbnail, MotionEvent.ACTION_UP)
            idle()
            val shrink = checkNotNull(thumbnail.referenceGlide)
            assertNotSame(grow, shrink)
            assertEquals(halfway.width(), thumbnail.drawn().width(), 0.6f)
            assertEquals(halfway.height(), thumbnail.drawn().height(), 0.6f)
            assertHeld("turned back")
            shrink.setCurrentFraction(0.5f)
            assertHeld("shrinking")
            // On the way down it stays laid out large and is drawn smaller, so the photo is never a small one blown up.
            assertEquals(largeLayout, thumbnail.layoutParams.width)
            assertTrue(thumbnail.drawn().width() < halfway.width())
            assertTrue(thumbnail.drawn().width() > small.width())

            ShadowChoreographer.setPaused(false)
            play(500)
            assertEquals(small, thumbnail.drawn())
            assertEquals(small.width().toInt(), thumbnail.layoutParams.width)
            assertEquals(1f, abs(thumbnail.scaleX), 0f)
            assertHeld("small again")
        }
    }

    @Test
    fun withAnimationsOff_theReferenceThumbnailIsInItsPlaceAndSizeAtOnce() {
        val screen = camera()
        val thumbnail = screen.thumbnail()
        setAnimatorScale(0f)
        // Nothing may wait for a display frame.
        ShadowChoreographer.setPaused(true)

        assertTrue(ViewCompat.performAccessibilityAction(thumbnail, R.id.hide_reference, null))
        idle()
        assertEquals((16f - 54f) * screen.density(), thumbnail.translationX, 0f)
        assertEquals(REFERENCE_HIDDEN_ALPHA, thumbnail.alpha, 0f)

        thumbnail.performClick()
        idle()
        assertEquals(12f * screen.density(), thumbnail.translationX, 0f)
        assertEquals(1f, thumbnail.alpha, 0f)

        thumbnail.performClick()
        idle()
        assertEquals(180f * screen.density(), thumbnail.layoutParams.width.toFloat(), 1f)
        assertEquals(1f, thumbnail.scaleX, 0f)
        assertEquals(1f, thumbnail.scaleY, 0f)
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

        // The scene photo for poses (#210): its title, panel, bar, and shutter, and the preview where it was (#231).
        screen.beginScene()
        assertEquals(1f, screen.controls.sceneTitle.alpha, 0f)
        assertEquals(1f, screen.controls.sceneGuide.alpha, 0f)
        assertEquals(0f, screen.controls.sceneGuide.translationY, 0f)
        assertEquals(0.25f, screen.controls.sceneProgress.filled, 0.001f)
        assertEquals(null, screen.fragment.shutterTurn)
        assertTrue(screen.controls.shutterButton.background is StateListDrawable)
        play(16)
        assertEquals(0f, screen.controls.previewContainer.translationY, 0f)

        // The panel at a touch: gone at once.
        screen.generation().dismissSceneHint()
        idle()
        assertFalse(screen.controls.sceneGuide.isVisible())
    }
}
