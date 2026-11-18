// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.camera.CameraCapabilities
import com.lastpenguin.pix.databinding.FragmentSubjectBinding
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.counterReads
import com.lastpenguin.pix.ui.camera.pageSaysUnavailable
import com.lastpenguin.pix.ui.camera.pageShows
import com.lastpenguin.pix.ui.camera.photoViewerOn
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/*
 * The session's photos on Subject view (FR-6.14, R&S 6.6; Iter4-A2), on the real Fragment with a container of fakes
 * and the real PhotoSyncer, whose album saves each photo the photographer sends.
 * The thumbnail (state-based): none before the first photo, nor any viewer built yet, nor for one the album could not
 * save; the first photo's, then a newer one's in its place.
 * The viewer (state-based): the session's photos over the video, opened at the newest and named after the
 * photographer, with the session going on and the screen staying; a swipe shows the earlier ones, with which one of
 * how many, and each opening starts at the newest again; a newer photo while it is open is one more page, the page on
 * screen staying, and the thumbnail's; its Back and the system's close it without asking to leave; a photo that cannot
 * be read says so; the photographer's step that starts while it is open waits until it closes; the session's end
 * returns to Camera.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SubjectSessionPhotoTest {
    private var scenario: FragmentScenario<SubjectFragment>? = null

    /** Where the subject's syncers run, as the session would run them. */
    private val receiving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** What MediaStore would give for each saved photo: a thumbnail and the photo to view. */
    private val thumbnails = HashMap<Uri, Bitmap>()
    private val pictures = HashMap<Uri, Bitmap>()

    @After
    fun tearDown() {
        scenario?.close()
        receiving.cancel()
    }

    private fun picture(): Bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)

    /** Subject view, connected to Dongje with the video streaming, receiving photos. */
    private fun launch(): TestAppContainer {
        val modules = TestAppContainer().install()
        scenario = launchFragmentInContainer<SubjectFragment>(themeResId = R.style.Theme_Pix)
        idle()
        scenario!!.onFragment { fragment ->
            fragment.thumbnailOf = { uri -> thumbnails.getOrPut(uri, ::picture) }
            fragment.photoOf = { uri -> pictures.getOrPut(uri, ::picture) }
        }
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        modules.video.deliverFrame()
        modules.session.deliver(SessionMessage.Capabilities(CameraCapabilities(0.6f, 10f, listOf(1f, 2f))))
        modules.photos.startAsReceiver(receiving)
        modules.poses.startAsReceiver(receiving)
        // The "Connecting…" check runs every 500 ms on the main looper; let one more round see the session.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        return modules
    }

    private fun <T> onScreen(block: (FragmentSubjectBinding, SubjectFragment) -> T): T {
        var result: T? = null
        scenario!!.onFragment { fragment ->
            result =
                block(FragmentSubjectBinding.bind(fragment.requireView()), fragment)
        }
        return result!!
    }

    /** The photographer's photo [photoId] arrives and is saved here; its pictures are read off the main thread. */
    private fun TestAppContainer.photo(photoId: String) {
        deliverPhoto(photoId)
        settle()
    }

    /** Lets the screen's reads on Dispatchers.IO finish and their results reach the screen. */
    private fun settle() {
        idle()
        Thread.sleep(200)
        idle()
    }

    private fun FragmentSubjectBinding.thumbnailShows(): Bitmap? =
        (galleryThumbnail.drawable as? BitmapDrawable)?.bitmap

    private fun FragmentSubjectBinding.viewer() = photoViewerOn(root)

    private fun FragmentSubjectBinding.viewerShows(): Bitmap? = viewer().pageShows()

    private fun saved(photoId: String) = TestAppContainer.savedPhoto(photoId)

    private fun openThumbnail() {
        onScreen { binding, _ -> binding.galleryButton.performClick() }
        settle()
    }

    // ---- The thumbnail ----------------------------------------------------------------------

    @Test
    fun beforeAnyPhoto_thereIsNoThumbnail() {
        launch()

        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.galleryButton.visibility)
            // The viewer is built when it first opens.
            assertNull(binding.root.findViewById(R.id.photoViewer))
        }
    }

    @Test
    fun aPhotoSavedHere_isTheThumbnail_andANewerOneTakesItsPlace() {
        val modules = launch()

        modules.photo("p1")
        onScreen { binding, _ ->
            assertTrue(binding.galleryButton.isShown)
            assertSame(thumbnails[saved("p1")], binding.thumbnailShows())
        }

        modules.photo("p2")
        onScreen { binding, _ -> assertSame(thumbnails[saved("p2")], binding.thumbnailShows()) }
    }

    @Test
    fun aPhotoTheAlbumCouldNotSave_showsNoThumbnail() {
        val modules = launch()
        modules.albumFails = true

        modules.photo("p1")

        onScreen { binding, _ -> assertEquals(View.GONE, binding.galleryButton.visibility) }
    }

    // ---- The viewer -------------------------------------------------------------------------

    @Test
    fun theThumbnail_opensTheNewestPhotoOverTheVideo_andTheSessionGoesOn() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        modules.photo("p1")
        modules.photo("p2")

        openThumbnail()

        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertSame(pictures[saved("p2")], binding.viewerShows())
            assertEquals("2 / 2", binding.viewer().counterReads())
            assertEquals(
                string(R.string.session_photo_title, "Dongje"),
                binding.viewer().photoViewerTitle.text.toString(),
            )
            assertFalse(binding.viewer().pageSaysUnavailable())
        }
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertTrue(modules.session.leaves.isEmpty())
    }

    @Test
    fun aSwipe_showsTheEarlierPhotos_andTheNextOpeningStartsAtTheNewest() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().photoViewerPages.setCurrentItem(0, false) }
        settle()
        onScreen { binding, _ ->
            assertSame(pictures[saved("p1")], binding.viewerShows())
            assertEquals("1 / 2", binding.viewer().counterReads())
        }

        onScreen { binding, _ -> binding.viewer().photoViewerBack.performClick() }
        settle()
        openThumbnail()
        onScreen { binding, _ ->
            assertSame(pictures[saved("p2")], binding.viewerShows())
            assertEquals("2 / 2", binding.viewer().counterReads())
        }
    }

    @Test
    fun aNewerPhotoWhileTheViewerIsOpen_isOneMorePage_andThePageOnScreenStays() {
        val modules = launch()
        modules.photo("p1")
        openThumbnail()
        onScreen { binding, _ -> assertNull(binding.viewer().counterReads()) }

        modules.photo("p2")

        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertNotNull(binding.viewerShows())
            assertSame(pictures[saved("p1")], binding.viewerShows())
            assertEquals("1 / 2", binding.viewer().counterReads())
            assertSame(thumbnails[saved("p2")], binding.thumbnailShows())
        }
    }

    @Test
    fun theViewersBack_andTheSystemBack_closeIt_withoutAskingToLeave() {
        val modules = launch()
        modules.photo("p1")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().photoViewerBack.performClick() }
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.viewer().root.visibility) }

        openThumbnail()
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()

        onScreen { binding, _ -> assertEquals(View.GONE, binding.viewer().root.visibility) }
        assertNull(ShadowDialog.getLatestDialog())
        assertTrue(modules.session.leaves.isEmpty())
    }

    @Test
    fun aPhotoThatCannotBeRead_saysSoInTheViewer() {
        val modules = launch()
        onScreen { _, fragment -> fragment.photoOf = { throw IOException("Deleted from the gallery") } }
        modules.photo("p1")

        openThumbnail()

        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertTrue(binding.viewer().pageSaysUnavailable())
            assertNull(binding.viewerShows())
        }
    }

    @Test
    fun thePhotographersStep_waitsWhileTheViewerIsOpen_andOpensOnceItCloses() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        modules.photo("p1")
        openThumbnail()

        modules.session.deliver(SessionMessage.PoseStep("set1", PoseSetStep.GENERATING))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        onScreen { binding, _ -> assertTrue(binding.viewer().root.isShown) }

        onScreen { binding, _ -> binding.viewer().photoViewerBack.performClick() }
        idle()

        assertEquals(R.id.subjectGeneratingFragment, nav.currentDestination?.id)
    }

    @Test
    fun theSessionEndingWhileTheViewerIsOpen_returnsToTheCamera() {
        val modules = launch()
        val nav = scenario!!.attachNav(R.id.subjectFragment)
        modules.photo("p1")
        openThumbnail()

        modules.session.setState(SessionState.Ended(EndReason.PEER_LEFT))
        idle()

        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }
}
