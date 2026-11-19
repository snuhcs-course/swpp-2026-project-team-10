// AI-generated with Claude Code, 2026-11-19, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Looper
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.OwnActivities
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.settings.boundsOnScreen
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/*
 * The photo viewer turning with the phone (FR-1.16, #207, R&S 6.2, Design 2.6.5), over Camera on the Galaxy S22's app
 * area, 360 x 705 dp, with Pix's album of three landscape photos.
 * Partitions (equivalence): the phone held at 0, 90, 180, and 270 degrees. Upright and upside down, the viewer is laid
 * out as the screen, the photo between the header and the strip; held sideways, it is a landscape screen, the photo
 * over all of it and the header and the strip over the photo. Each way, Back is at the top left as the user sees it,
 * and the photo on screen stays. The first turn, as the viewer first opens, is there at once. A drag down the photo as
 * the user sees it closes the viewer, also held sideways. Sideways, the header has a scrim behind its icons.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class PhotoViewerTurnTest {
    @get:Rule
    val activities = OwnActivities()

    private lateinit var modules: TestAppContainer
    private lateinit var fragment: CameraFragment

    /** Pix's album, oldest first, each a 16:9 landscape photo. */
    private val album = (1..3).map { TestAppContainer.savedPhoto("p$it") }
    private val pictures = album.associateWith { Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888) }

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    /**
     * The Camera screen with the phone held at [turn], and Pix's album open in the viewer at its newest; with [paused],
     * the display's frames stop just before it opens, so nothing has played or been laid out since.
     */
    private fun viewer(turn: Int = 0, paused: Boolean = false): Pair<FragmentCameraBinding, ViewPhotoViewerBinding> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        modules = TestAppContainer().install()
        modules.turns.set(turn)
        val activity = activities.start(R.style.Theme_Pix)
        val nav = TestNavHostController(activity).apply { setGraph(R.navigation.nav_graph) }
        Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
        fragment = CameraFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        fragment.photoOf = { pictures[it] }
        modules.album.value = album
        settleReads()
        val camera = FragmentCameraBinding.bind(fragment.requireView())
        if (paused) ShadowChoreographer.setPaused(true)
        camera.galleryButton.performClick()
        if (paused) idle() else settleReads()
        return camera to photoViewerOn(camera.root)
    }

    /** The photos and the thumbnails are read off the main thread. */
    private fun settleReads() {
        idle()
        Thread.sleep(300)
        idle()
    }

    private fun turn(to: Int) {
        modules.turns.set(to)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Motion.TURN_MS * 2))
        settleReads()
    }

    /** [box] in the viewer's place on the screen. */
    private fun ViewPhotoViewerBinding.onViewer(box: Rect): Rect {
        val at = IntArray(2).also(root::getLocationOnScreen)
        return Rect(box).apply { offset(-at[0], -at[1]) }
    }

    /** Where Back is on the screen: within a quarter of the screen from its [left] or right and [top] or bottom. */
    private fun ViewPhotoViewerBinding.assertBackNear(left: Boolean, top: Boolean, why: String) {
        val back = onViewer(checkNotNull(boundsOnScreen(photoViewerBack)))
        val width = root.width
        val height = root.height
        assertTrue("$why: $back", if (left) back.right < width / 4 else back.left > width * 3 / 4)
        assertTrue("$why: $back", if (top) back.bottom < height / 4 else back.top > height * 3 / 4)
    }

    private fun ViewPhotoViewerBinding.assertUpright(degrees: Float) {
        val content = photoViewerContent
        assertEquals(degrees, content.rotation, 0f)
        assertEquals(root.width, content.width)
        assertEquals(root.height, content.height)
        // Between the header and the strip.
        assertTrue(photoViewerPages.top >= photoViewerHeader.bottom)
        assertTrue(photoViewerPages.bottom <= photoViewerStrip.top)
        assertNull(photoViewerHeader.background)
    }

    private fun ViewPhotoViewerBinding.assertSideways(degrees: Float) {
        val content = photoViewerContent
        assertEquals(degrees, content.rotation, 0f)
        // A landscape screen: the screen's height across, and its width down.
        assertEquals(root.height, content.width)
        assertEquals(root.width, content.height)
        // The photo over all of it, as tall as the screen is wide, and the header and the strip over the photo.
        assertEquals(Rect(0, 0, content.width, content.height), photoViewerPages.run { Rect(left, top, right, bottom) })
        assertEquals(content.height.toFloat(), pagePhoto().shownRect().height(), 1f)
        assertTrue(photoViewerHeader.top < photoViewerPages.top + photoViewerHeader.height)
        assertTrue(photoViewerStrip.bottom > photoViewerPages.bottom - photoViewerStrip.height * 2)
        // A scrim behind the header's icons, which lie over the photo.
        assertNotNull(photoViewerHeader.background)
    }

    @Test
    fun heldEachWay_theViewerStandsUpright_asALandscapeScreenWhileSideways_andKeepsThePhoto() {
        val (camera, viewer) = viewer()
        val newest = pictures.getValue(album.last())
        viewer.assertUpright(0f)
        viewer.assertBackNear(left = true, top = true, why = "upright")

        // The phone's top to the user's right: the user's top left is the screen's bottom left.
        turn(90)
        viewer.assertSideways(-90f)
        viewer.assertBackNear(left = true, top = false, why = "90")
        assertSame(newest, viewer.pageShows())
        assertEquals(2, viewer.photoViewerPages.currentItem)
        assertEquals(2, viewer.stripSelected())

        turn(270)
        viewer.assertSideways(90f)
        viewer.assertBackNear(left = false, top = true, why = "270")
        assertSame(newest, viewer.pageShows())

        turn(180)
        viewer.assertUpright(180f)
        viewer.assertBackNear(left = false, top = false, why = "180")

        turn(0)
        viewer.assertUpright(0f)
        viewer.assertBackNear(left = true, top = true, why = "upright again")
        assertSame(newest, viewer.pageShows())
        assertTrue(camera.root.isShown)
    }

    @Test
    fun openedSideways_isThereAtOnce() {
        val (_, viewer) = viewer(turn = 90, paused = true)

        // Before any frame: the first turn takes no animation, so the viewer opens as the phone is held.
        assertEquals(-90f, viewer.photoViewerContent.rotation, 0f)
        assertTrue(viewer.root.sideways)

        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
        settleReads()
        viewer.assertSideways(-90f)
    }

    private companion object {
        const val FRAME_MS = 17L
    }

    @Test
    fun heldSideways_aDragDownThePhotoAsSeen_closesTheViewer() {
        val (_, viewer) = viewer(turn = 270)
        val density = viewer.root.resources.displayMetrics.density

        viewer.dragPhotoDown(ZoomablePhotoView.DISMISS_DP * density + 40f)

        assertFalse(viewer.root.isShown)
    }
}
