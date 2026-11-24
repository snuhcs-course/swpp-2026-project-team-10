// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.ui.generation

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.databinding.FragmentDescribePoseBinding
import com.lastpenguin.pix.ui.OwnActivities
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * Partitions for the scene photo coming into its place on Generate poses (#296), on the real layout at the Galaxy
 * S22's 360 × 705 dp, with the display's frames paused: a 3:4 photo starts as wide as the screen, right under the
 * flow's bar, in its own shape, with square corners; it stays there for the hold, as the screenshot's cut holds its
 * photo; halfway through the move it is halfway, eased in and out; it ends on its thumbnail, with the thumbnail's
 * round corners, and stops; a photo taller than the room under the bar starts as high as that room, in the middle;
 * with the phone's animations off nothing is drawn and nothing plays. A photo taken with the phone held sideways,
 * upright here and so landscape: it starts as the camera held it, the frame's shape and turned back by the quarter
 * turn, to the left or to the right, and is upright on its thumbnail; a photo taken upside down starts turned half
 * round; an upright one is not turned.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class SceneArrivalTest {
    @get:Rule
    val activities = OwnActivities()

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    /** Generate poses laid out with a scene photo of [width] × [height] in its place. */
    private fun screen(width: Int, height: Int): Pair<FragmentDescribePoseBinding, Bitmap> {
        val activity = activities.start()
        val binding = FragmentDescribePoseBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        val scene = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        binding.sceneImage.showScene(scene)
        shadowOf(Looper.getMainLooper()).idle()
        return binding to scene
    }

    private fun FragmentDescribePoseBinding.placeOf(view: View): Rect =
        Rect(0, 0, view.width, view.height).also { root.offsetDescendantRectToMyCoords(view, it) }

    @Test
    fun aPhoto_staysAsWideAsTheScreenUnderTheBarForAMoment_thenGoesToItsThumbnail_roundingItsCorners() {
        val (binding, scene) = screen(768, 1024)
        ShadowChoreographer.setPaused(true)

        val arrival = checkNotNull(binding.root.showArriving(scene, binding.sceneImage, binding.flowProgress))

        // Where the camera held it: the screen's width, under the header, in the photo's own shape.
        val held = Rect(arrival.photo.bounds)
        assertEquals(0, held.left)
        assertEquals(binding.root.width, held.width())
        assertEquals(binding.placeOf(binding.flowProgress).bottom, held.top)
        assertEquals(held.width() * 1024 / 768, held.height())
        assertTrue(arrival.animator.isStarted)
        assertEquals(SceneArrival.HOLD_MS + SceneArrival.MOVE_MS, arrival.animator.duration)
        val photo = arrival.photo
        assertEquals(0f, photo.cornerRadius, 0f)
        assertEquals(0f, photo.turn, 0f)

        // Held where the camera showed it, long enough to be seen, as the screenshot's cut holds its photo.
        arrival.animator.currentPlayTime = SceneArrival.HOLD_MS
        assertEquals(held, arrival.photo.bounds)

        // Halfway through the move it is halfway there: slow to start and slow to settle.
        val place = binding.placeOf(binding.sceneImage)
        arrival.animator.currentPlayTime = SceneArrival.HOLD_MS + SceneArrival.MOVE_MS / 2
        assertEquals((held.width() + place.width()) / 2f, arrival.photo.bounds.width().toFloat(), 2f)
        arrival.animator.currentPlayTime = SceneArrival.HOLD_MS + SceneArrival.MOVE_MS / 10
        assertTrue("slow to start", arrival.photo.bounds.width() > held.width() - (held.width() - place.width()) / 10)

        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SceneArrival.HOLD_MS + SceneArrival.MOVE_MS + 100))

        // On its thumbnail, with the thumbnail's corners.
        assertEquals(place, arrival.photo.bounds)
        assertEquals(12 * binding.root.resources.displayMetrics.density, photo.cornerRadius, 0.5f)
        assertFalse(arrival.animator.isRunning)
    }

    @Test
    fun aPhotoTallerThanTheRoomUnderTheBar_startsAsHighAsThatRoom_inTheMiddle() {
        // 1:4 is taller than any phone's screen: the room's height bounds it.
        val (binding, scene) = screen(256, 1024)
        ShadowChoreographer.setPaused(true)

        val arrival = checkNotNull(binding.root.showArriving(scene, binding.sceneImage, binding.flowProgress))

        val held = arrival.photo.bounds
        val top = binding.placeOf(binding.flowProgress).bottom
        assertEquals(top, held.top)
        assertTrue("$held", held.bottom <= binding.root.height)
        assertTrue("$held", held.width() < binding.root.width)
        assertEquals((binding.root.width - held.width()) / 2, held.left)
        assertNotNull(arrival.animator)
    }

    @Test
    fun aPhotoTakenSideways_startsAsTheCameraHeldIt_andTurnsUprightOnItsWay() {
        // Held a quarter turn: the frame was 3:4 on the screen, and the photo, turned upright, is 4:3.
        for ((turn, startsAt) in listOf(90 to -90f, 270 to 90f)) {
            val (binding, scene) = screen(1024, 768)
            ShadowChoreographer.setPaused(true)

            val arrival =
                checkNotNull(binding.root.showArriving(scene, binding.sceneImage, binding.flowProgress, turn))

            // The frame as the camera held it: as wide as the screen and 4:3 high, under the bar. The photo is drawn
            // into it turned back, so its own box has the frame's sides swapped around the frame's middle.
            val top = binding.placeOf(binding.flowProgress).bottom
            val frame = Rect(0, top, binding.root.width, top + binding.root.width * 1024 / 768)
            assertEquals("$turn", frame, arrival.photo.covers())
            assertEquals("$turn", startsAt, arrival.photo.turn, 0f)
            assertEquals("$turn", frame.height(), arrival.photo.bounds.width())
            assertEquals("$turn", frame.width(), arrival.photo.bounds.height())

            ShadowChoreographer.setPaused(false)
            shadowOf(
                Looper.getMainLooper(),
            ).idleFor(Duration.ofMillis(SceneArrival.HOLD_MS + SceneArrival.MOVE_MS + 100))

            // Upright on its thumbnail, which is landscape.
            assertEquals("$turn", binding.placeOf(binding.sceneImage), arrival.photo.bounds)
            assertEquals("$turn", 0f, arrival.photo.turn, 0f)
            setAnimatorScale(1f)
        }
    }

    @Test
    fun aPhotoTakenUpsideDown_startsTurnedHalfRound_inTheFramesOwnShape() {
        val (binding, scene) = screen(768, 1024)
        ShadowChoreographer.setPaused(true)

        val arrival = checkNotNull(binding.root.showArriving(scene, binding.sceneImage, binding.flowProgress, 180))

        assertEquals(180f, kotlin.math.abs(arrival.photo.turn), 0f)
        assertEquals(arrival.photo.bounds, arrival.photo.covers())
        assertEquals(binding.root.width, arrival.photo.bounds.width())
    }

    @Test
    fun withAnimationsOff_nothingPlays() {
        val (binding, scene) = screen(768, 1024)
        setAnimatorScale(0f)

        assertNull(binding.root.showArriving(scene, binding.sceneImage, binding.flowProgress))
    }
}
