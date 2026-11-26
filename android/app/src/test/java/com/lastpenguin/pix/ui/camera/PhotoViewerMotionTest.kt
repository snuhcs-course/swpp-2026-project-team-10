// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.camera

import android.Manifest
import android.animation.ValueAnimator
import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.PhoneAlbum
import com.lastpenguin.pix.databinding.FragmentCameraBinding
import com.lastpenguin.pix.databinding.ViewPhotoViewerBinding
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.OwnActivities
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.TestAppContainer
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

/*
 * The photo viewer's motion outside a session (FR-1.6, Design 2.6.5), over Camera on the Galaxy S22's app area.
 * Partitions (state-based, each change at its start and at its end): the viewer opening, grown out of the gallery
 * thumbnail, and closing back into it; a photo picked from the grid, its page grown out of its square, and Back, the
 * page shrunk back into it; an album opened from the Albums tab, its grid slid in from the right over the albums, and
 * Back, the albums slid in from the left; the tabs, the Albums tab from the right. With the phone's animations off,
 * each change at its end at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h705dp-xxhdpi")
class PhotoViewerMotionTest {
    @get:Rule
    val activities = OwnActivities()

    private lateinit var modules: TestAppContainer
    private lateinit var fragment: CameraFragment

    private val camera = listOf(TestAppContainer.savedPhoto("c1"), TestAppContainer.savedPhoto("c2"))
    private val shot = TestAppContainer.savedPhoto("s1")

    @After
    fun tearDown() {
        ShadowChoreographer.setPaused(false)
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, scale)
    }

    /** Camera with three photos in two albums, the thumbnail and the squares read as small pictures. */
    private fun camera(): FragmentCameraBinding {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        modules = TestAppContainer().install()
        val activity = activities.start(R.style.Theme_Pix)
        val nav = TestNavHostController(activity).apply { setGraph(R.navigation.nav_graph) }
        Navigation.setViewNavController(activity.findViewById(android.R.id.content), nav)
        fragment = CameraFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        fragment.thumbnailOf = { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        fragment.cellOf = { _, _ -> Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
        fragment.photoOf = { Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888) }
        modules.album.value = listOf(camera[0], shot, camera[1])
        modules.albums.value = listOf(
            PhoneAlbum("camera", "Camera", "DCIM/Camera/", camera),
            PhoneAlbum("shots", "Screenshots", "Pictures/Screenshots/", listOf(shot)),
        )
        settleReads()
        return FragmentCameraBinding.bind(fragment.requireView())
    }

    private fun settleReads() {
        idle()
        Thread.sleep(300)
        idle()
    }

    /**
     * The layout and the pre-draw of a frame, run by hand while the frames are paused: what waits for them starts, and
     * stands at its first step, where a frame would catch up to its end at once.
     */
    private fun layOutPaused() {
        val window = fragment.requireActivity().window.decorView
        window.measure(
            View.MeasureSpec.makeMeasureSpec(window.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(window.height, View.MeasureSpec.EXACTLY),
        )
        window.layout(window.left, window.top, window.right, window.bottom)
        window.viewTreeObserver.dispatchOnPreDraw()
    }

    /** Every motion to its end. */
    private fun toTheEnd() {
        ShadowChoreographer.setPaused(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Motion.MOVE_MS * 2))
        settleReads()
    }

    private fun FragmentCameraBinding.viewer(): ViewPhotoViewerBinding = photoViewerOn(root)

    @Test
    fun theViewer_growsOutOfTheThumbnail_andShrinksBackIntoIt() {
        val screen = camera()
        ShadowChoreographer.setPaused(true)
        screen.galleryButton.performClick()
        idle()

        // Smaller and see-through at first, growing from the thumbnail's middle.
        val viewer = screen.viewer().root
        val thumbnail = IntArray(2).also(screen.galleryButton::getLocationInWindow)
        val holder = IntArray(2).also(screen.photoViewerHolder::getLocationInWindow)
        assertTrue(viewer.isVisible())
        assertEquals(0.9f, viewer.scaleX, 0.01f)
        assertEquals(0f, viewer.alpha, 0.01f)
        assertEquals(thumbnail[0] + screen.galleryButton.width / 2f - holder[0], viewer.pivotX, 1f)
        assertEquals(thumbnail[1] + screen.galleryButton.height / 2f - holder[1], viewer.pivotY, 1f)

        toTheEnd()
        assertEquals(1f, viewer.scaleX, 0f)
        assertEquals(1f, viewer.alpha, 0f)

        // Back from the grid: on its way back into the thumbnail, then gone.
        ShadowChoreographer.setPaused(true)
        fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
        idle()
        assertTrue(viewer.isVisible())
        assertEquals(thumbnail[0] + screen.galleryButton.width / 2f - holder[0], viewer.pivotX, 1f)
        toTheEnd()
        assertFalse(viewer.isVisible())
        assertEquals(1f, viewer.scaleX, 0f)
    }

    @Test
    fun aPhoto_growsOutOfItsSquare_andBackShrinksItIntoTheSquareAgain() {
        val screen = camera()
        screen.galleryButton.performClick()
        settleReads()
        val viewer = screen.viewer()

        // At the square's size, a fifth of the screen's width, over the grid as it fades, see-through at first.
        ShadowChoreographer.setPaused(true)
        viewer.tapGrid(0)
        idle()
        layOutPaused()
        val pager = viewer.photoViewerPages
        val square = viewer.photoViewerGrid.findViewHolderForAdapterPosition(0)!!.itemView.width
        assertEquals(square.toFloat() / pager.width, pager.scaleX, 0.01f)
        assertEquals(0f, pager.alpha, 0f)
        assertTrue(pager.translationZ > viewer.photoViewerAlbum.translationZ)
        toTheEnd()
        assertEquals(1f, pager.scaleX, 0f)
        assertEquals(0f, pager.translationX, 0f)
        assertEquals(0f, pager.translationZ, 0f)
        assertTrue(viewer.pagesShown())

        // Back: the page stays over the grid as it comes back, on its way into the square.
        ShadowChoreographer.setPaused(true)
        fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
        idle()
        layOutPaused()
        assertTrue(pager.isShown)
        assertTrue(pager.translationZ > viewer.photoViewerAlbum.translationZ)
        toTheEnd()
        assertTrue(viewer.gridShown())
        assertEquals(1f, pager.scaleX, 0f)
    }

    @Test
    fun anAlbum_slidesInFromTheRight_andBackSlidesTheAlbumsInFromTheLeft() {
        val screen = camera()
        screen.galleryButton.performClick()
        settleReads()
        val viewer = screen.viewer()

        // The Albums tab comes from the right, as the grid goes left.
        ShadowChoreographer.setPaused(true)
        viewer.photoViewerTabAlbums.performClick()
        idle()
        assertTrue(viewer.photoViewerAlbums.translationX > 0f)
        assertTrue(viewer.photoViewerGrid.translationX <= 0f)
        toTheEnd()
        assertEquals(2, viewer.albumsCount())
        assertFalse(viewer.photoViewerGrid.isShown)

        ShadowChoreographer.setPaused(true)
        viewer.tapAlbum(0)
        idle()
        assertTrue(viewer.photoViewerGrid.translationX > 0f)
        assertTrue(viewer.photoViewerAlbums.isShown)
        toTheEnd()
        assertEquals(0f, viewer.photoViewerGrid.translationX, 0f)
        assertFalse(viewer.photoViewerAlbums.isShown)

        ShadowChoreographer.setPaused(true)
        fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
        idle()
        assertTrue(viewer.photoViewerAlbums.translationX < 0f)
        toTheEnd()
        assertEquals(0f, viewer.photoViewerAlbums.translationX, 0f)
        assertFalse(viewer.photoViewerGrid.isShown)
    }

    @Test
    fun withAnimationsOff_eachChangeIsAtItsEndAtOnce() {
        val screen = camera()
        setAnimatorScale(0f)
        ShadowChoreographer.setPaused(true)
        screen.galleryButton.performClick()
        idle()
        val viewer = screen.viewer()
        assertEquals(1f, viewer.root.scaleX, 0f)
        assertEquals(1f, viewer.root.alpha, 0f)

        viewer.photoViewerTabAlbums.performClick()
        idle()
        assertEquals(0f, viewer.photoViewerAlbums.translationX, 0f)
        assertEquals(View.GONE, viewer.photoViewerGrid.visibility)
    }

    private fun View.isVisible() = visibility == View.VISIBLE
}
