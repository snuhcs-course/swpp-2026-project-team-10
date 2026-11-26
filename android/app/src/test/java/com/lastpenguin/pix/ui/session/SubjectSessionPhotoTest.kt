// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park, Joonhyung Han, and Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.IntentCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
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
import com.lastpenguin.pix.ui.Motion
import com.lastpenguin.pix.ui.ScreenTestSupport.attachNav
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.shownNotice
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.TestAppContainer
import com.lastpenguin.pix.ui.camera.ZoomablePhotoView
import com.lastpenguin.pix.ui.camera.doubleTapPhoto
import com.lastpenguin.pix.ui.camera.dragPhotoDown
import com.lastpenguin.pix.ui.camera.headerShown
import com.lastpenguin.pix.ui.camera.pagePhoto
import com.lastpenguin.pix.ui.camera.pageReads
import com.lastpenguin.pix.ui.camera.pageSaysUnavailable
import com.lastpenguin.pix.ui.camera.pageShows
import com.lastpenguin.pix.ui.camera.photoViewerOn
import com.lastpenguin.pix.ui.camera.stripCount
import com.lastpenguin.pix.ui.camera.stripSelected
import com.lastpenguin.pix.ui.camera.tapPhoto
import com.lastpenguin.pix.ui.camera.tapStrip
import com.lastpenguin.pix.ui.camera.textsShown
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
 * The thumbnail (state-based): empty and taking no tap before the first photo, with no viewer built yet, and for one
 * the album could not save; the first photo's, then a newer one's in its place.
 * The photo on its way (state-based, #271): from the photographer's shutter, the slot where Camera's shutter is says
 * it is coming, with a spinner, until it is saved, or the album could not save it; two shutters wait for both photos.
 * The viewer (state-based): the session's photos over the video, opened at the newest under Back, Share, and Delete,
 * with no title and no counter, with the session going on and the screen staying; a swipe shows the earlier ones, with
 * the strip of thumbnails under the photo, the one on screen selected, a tap on one showing it, TalkBack reading which
 * photo of how many, and each opening starts at the newest again; one photo has no strip; a newer photo while it is
 * open is one more page, the page on screen staying, and the thumbnail's; its Back and the system's close it without
 * asking to leave; a photo that cannot be read says so; the photographer's step that starts while it is open waits
 * until it closes; the session's end returns to Camera.
 * As a gallery app (state-based, boundary): a tap on the photo hides the header and the strip and another shows them,
 * and each opening shows them; a drag down past the line, or a flick, closes it, and a short slow one goes back; a
 * double tap zooms in and another out, and a page swiped away is whole again. Share sends the photo on screen through
 * the share sheet with leave to read it. Delete asks first: Cancel keeps it; Delete takes it from the pages, the strip,
 * and the thumbnail, the last one closing the viewer; a delete that fails keeps it and says so.
 * Turned with this phone (equivalence, #207): held sideways, the viewer is a landscape screen, the photo on screen
 * stays, and nothing is sent to the photographer.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowSurfaceViewRenderer::class], instrumentedPackages = ["org.webrtc"])
class SubjectSessionPhotoTest {
    /** How far down a drag must take the photo to close the viewer, in px. */
    private val dismissPx: Float
        get() = ZoomablePhotoView.DISMISS_DP *
            ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density

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
    private fun TestAppContainer.photo(photoId: String, shot: String? = null) {
        deliverPhoto(photoId, shot)
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

    /** What TalkBack reads of a page: photo [position] of [count]. */
    private fun of(position: Int, count: Int) = string(R.string.photo_page_counter_description, position, count)

    private fun openThumbnail() {
        onScreen { binding, _ -> binding.galleryButton.performClick() }
        settle()
    }

    /** Whether a view under [root] shows [text]. */
    private fun textShown(root: View, text: String): Boolean = when {
        !root.isShown -> false
        root is TextView -> root.text?.toString() == text
        root is ViewGroup -> (0 until root.childCount).any { textShown(root.getChildAt(it), text) }
        else -> false
    }

    // ---- The thumbnail ----------------------------------------------------------------------

    @Test
    fun beforeAnyPhoto_theThumbnailIsEmpty_andATapOpensNothing() {
        launch()

        onScreen { binding, _ ->
            assertTrue(binding.galleryButton.isShown)
            assertFalse(binding.galleryButton.isEnabled)
            assertNull(binding.thumbnailShows())
            binding.galleryButton.performClick()
        }
        settle()

        // The viewer is built when it first opens.
        onScreen { binding, _ -> assertNull(binding.root.findViewById(R.id.photoViewer)) }
    }

    @Test
    fun aPhotoSavedHere_isTheThumbnail_andANewerOneTakesItsPlace() {
        val modules = launch()

        modules.photo("p1")
        onScreen { binding, _ ->
            assertTrue(binding.galleryButton.isEnabled)
            assertSame(thumbnails[saved("p1")], binding.thumbnailShows())
        }

        modules.photo("p2")
        onScreen { binding, _ -> assertSame(thumbnails[saved("p2")], binding.thumbnailShows()) }
    }

    @Test
    fun aPhotoTheAlbumCouldNotSave_leavesTheThumbnailEmpty() {
        val modules = launch()
        modules.albumFails = true

        modules.photo("p1")

        onScreen { binding, _ ->
            assertFalse(binding.galleryButton.isEnabled)
            assertNull(binding.thumbnailShows())
        }
    }

    // ---- The photo on its way ---------------------------------------------------------------

    private fun FragmentSubjectBinding.receivingShown(): Boolean {
        val receiving = cameraCaptionStatus.text.toString() == string(R.string.subject_receiving_photo)
        assertEquals(receiving, cameraCaptionSpinner.isShown)
        assertEquals(!receiving, cameraCaptionDot.isShown)
        return receiving && cameraCaption.isShown
    }

    @Test
    fun thePhotographersShutter_saysAPhotoIsComing_untilItIsSaved() {
        val modules = launch()
        onScreen { binding, _ -> assertFalse(binding.receivingShown()) }

        modules.session.deliver(SessionMessage.PhotoShutter("a"))
        idle()
        onScreen { binding, _ -> assertTrue(binding.receivingShown()) }

        modules.photo("p1", shot = "a")
        onScreen { binding, _ ->
            assertFalse(binding.receivingShown())
            assertEquals(string(R.string.subject_live), binding.cameraCaptionStatus.text.toString())
            assertSame(thumbnails[saved("p1")], binding.thumbnailShows())
        }
    }

    @Test
    fun twoShutters_waitForBothPhotos() {
        val modules = launch()

        modules.session.deliver(SessionMessage.PhotoShutter("a"))
        modules.session.deliver(SessionMessage.PhotoShutter("b"))
        modules.photo("p1", shot = "a")
        onScreen { binding, _ -> assertTrue(binding.receivingShown()) }

        modules.photo("p2", shot = "b")
        onScreen { binding, _ -> assertFalse(binding.receivingShown()) }
    }

    @Test
    fun aPhotoTheAlbumCouldNotSave_endsTheSign() {
        val modules = launch()
        modules.albumFails = true

        modules.session.deliver(SessionMessage.PhotoShutter("a"))
        idle()
        onScreen { binding, _ -> assertTrue(binding.receivingShown()) }

        modules.photo("p1", shot = "a")
        onScreen { binding, _ -> assertFalse(binding.receivingShown()) }
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
            assertEquals(of(2, 2), binding.viewer().pageReads())
            // The strip under the photo, the newest selected.
            assertEquals(2, binding.viewer().stripCount())
            assertEquals(1, binding.viewer().stripSelected())
            // No title and no counter: Back, Share, Delete, the photo, and the strip.
            assertEquals(emptyList<String>(), binding.viewer().textsShown())
            assertTrue(binding.viewer().headerShown())
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
            assertEquals(of(1, 2), binding.viewer().pageReads())
            assertEquals(0, binding.viewer().stripSelected())
        }

        onScreen { binding, _ -> binding.viewer().photoViewerBack.performClick() }
        settle()
        openThumbnail()
        onScreen { binding, _ ->
            assertSame(pictures[saved("p2")], binding.viewerShows())
            assertEquals(of(2, 2), binding.viewer().pageReads())
        }
    }

    @Test
    fun aNewerPhotoWhileTheViewerIsOpen_isOneMorePage_andThePageOnScreenStays() {
        val modules = launch()
        modules.photo("p1")
        openThumbnail()
        // One photo: no strip, and TalkBack reads "Photo".
        onScreen { binding, _ ->
            assertEquals(string(R.string.session_photo), binding.viewer().pageReads())
            assertEquals(0, binding.viewer().stripCount())
        }

        modules.photo("p2")

        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertNotNull(binding.viewerShows())
            assertSame(pictures[saved("p1")], binding.viewerShows())
            assertEquals(of(1, 2), binding.viewer().pageReads())
            assertEquals(2, binding.viewer().stripCount())
            assertEquals(0, binding.viewer().stripSelected())
            assertSame(thumbnails[saved("p2")], binding.thumbnailShows())
        }
    }

    @Test
    fun aTapOnTheStrip_showsThatPhoto() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        modules.photo("p3")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().tapStrip(0) }
        settle()

        onScreen { binding, _ ->
            assertSame(pictures[saved("p1")], binding.viewerShows())
            assertEquals(of(1, 3), binding.viewer().pageReads())
            assertEquals(0, binding.viewer().stripSelected())
        }
    }

    // ---- As a gallery app --------------------------------------------------------------------

    @Test
    fun aTapOnThePhoto_hidesTheHeaderAndTheStrip_anotherShowsThem_andEachOpeningShowsThem() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().tapPhoto() }
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertFalse(binding.viewer().headerShown())
            assertEquals(0, binding.viewer().stripCount())
        }

        onScreen { binding, _ -> binding.viewer().tapPhoto() }
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.viewer().headerShown())
            assertEquals(2, binding.viewer().stripCount())
        }

        onScreen { binding, _ -> binding.viewer().tapPhoto() }
        idle()
        onScreen { _, fragment -> fragment.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        openThumbnail()
        onScreen { binding, _ ->
            assertTrue(binding.viewer().headerShown())
            assertEquals(2, binding.viewer().stripCount())
        }
    }

    @Test
    fun aDragDownOnThePhoto_pastTheLine_closesTheViewer_aShortOneGoesBack_andAFlickCloses() {
        val modules = launch()
        modules.photo("p1")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().dragPhotoDown(dismissPx - 20f) }
        idle()
        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertEquals(0f, binding.viewer().pagePhoto().translationY)
            assertTrue(binding.viewer().headerShown())
        }

        onScreen { binding, _ -> binding.viewer().dragPhotoDown(dismissPx + 20f) }
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.viewer().root.visibility) }

        openThumbnail()
        onScreen { binding, _ ->
            // Shown anew, in its place.
            assertEquals(0f, binding.viewer().pagePhoto().translationY)
            binding.viewer().dragPhotoDown(dismissPx / 2, flick = true)
        }
        idle()
        onScreen { binding, _ -> assertEquals(View.GONE, binding.viewer().root.visibility) }
        assertTrue(modules.session.leaves.isEmpty())
    }

    @Test
    fun aDoubleTap_zoomsIn_aPageSwipedAwayIsWholeAgain_andAnotherDoubleTapZoomsOut() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().doubleTapPhoto() }
        onScreen { binding, _ ->
            assertEquals(ZoomablePhotoView.DOUBLE_TAP_ZOOM, binding.viewer().pagePhoto().zoom, 0.01f)
            // Two taps are no tap: the header stays.
            assertTrue(binding.viewer().headerShown())
        }

        onScreen { binding, _ -> binding.viewer().photoViewerPages.setCurrentItem(0, false) }
        settle()
        onScreen { binding, _ -> binding.viewer().photoViewerPages.setCurrentItem(1, false) }
        settle()
        onScreen { binding, _ -> assertEquals(1f, binding.viewer().pagePhoto().zoom) }
        onScreen { binding, _ -> binding.viewer().doubleTapPhoto() }
        onScreen { binding, _ ->
            assertEquals(ZoomablePhotoView.DOUBLE_TAP_ZOOM, binding.viewer().pagePhoto().zoom, 0.01f)
            binding.viewer().doubleTapPhoto()
        }
        onScreen { binding, _ -> assertEquals(1f, binding.viewer().pagePhoto().zoom) }
    }

    @Test
    fun share_sendsThePhotoOnScreen_throughThePhonesShareSheet() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        openThumbnail()
        onScreen { binding, _ -> binding.viewer().photoViewerPages.setCurrentItem(0, false) }
        settle()

        onScreen { binding, _ -> binding.viewer().photoViewerShare.performClick() }

        val chooser = onScreen { _, fragment -> shadowOf(fragment.requireActivity()).nextStartedActivity }
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        // The type MediaStore says, which has no row for it here.
        assertEquals("image/*", send.type)
        assertEquals(saved("p1"), IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // The session and the viewer stay under the share sheet.
        onScreen { binding, _ -> assertTrue(binding.viewer().root.isShown) }
        assertTrue(modules.session.leaves.isEmpty())
    }

    @Test
    fun delete_asksFirst_cancelKeepsThePhoto_andDeleteTakesItFromThePagesTheStripAndTheThumbnail() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        modules.photo("p3")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().photoViewerDelete.performClick() }
        idle()
        val asking = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(asking.isShowing)
        assertTrue(textShown(asking.window!!.decorView, string(R.string.delete_photo_title)))
        asking.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertTrue(modules.deleted.isEmpty())
        onScreen { binding, _ -> assertEquals(3, binding.viewer().stripCount()) }

        onScreen { binding, _ -> binding.viewer().photoViewerDelete.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settle()

        assertEquals(listOf(saved("p3")), modules.deleted)
        onScreen { binding, _ ->
            assertTrue(binding.viewer().root.isShown)
            assertEquals(2, binding.viewer().stripCount())
            assertEquals(1, binding.viewer().stripSelected())
            assertSame(pictures[saved("p2")], binding.viewerShows())
            assertEquals(of(2, 2), binding.viewer().pageReads())
            assertSame(thumbnails[saved("p2")], binding.thumbnailShows())
        }
    }

    @Test
    fun deletingTheOnlyPhoto_closesTheViewer_andEmptiesTheThumbnail() {
        val modules = launch()
        modules.photo("p1")
        openThumbnail()

        onScreen { binding, _ -> binding.viewer().photoViewerDelete.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settle()

        onScreen { binding, _ ->
            assertEquals(View.GONE, binding.viewer().root.visibility)
            assertFalse(binding.galleryButton.isEnabled)
            assertNull(binding.thumbnailShows())
        }
        assertTrue(modules.session.leaves.isEmpty())
    }

    @Test
    fun aDeleteThatFails_keepsThePhoto_andSaysSo() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        openThumbnail()
        modules.deleteFails = true

        onScreen { binding, _ -> binding.viewer().photoViewerDelete.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settle()

        assertEquals(string(R.string.photo_not_deleted), shownNotice())
        onScreen { binding, _ ->
            assertEquals(2, binding.viewer().stripCount())
            assertSame(pictures[saved("p2")], binding.viewerShows())
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

    // ---- Turned with the phone --------------------------------------------------------------

    @Test
    fun heldSideways_theViewerTurnsWithThisPhone_andNothingIsSent() {
        val modules = launch()
        modules.photo("p1")
        modules.photo("p2")
        openThumbnail()
        val sent = modules.session.sent.toList()

        modules.turns.set(90)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Motion.TURN_MS * 2))
        settle()

        onScreen { binding, _ ->
            val viewer = binding.viewer()
            assertEquals(-90f, viewer.photoViewerContent.rotation, 0f)
            assertTrue(viewer.root.sideways)
            // A landscape screen: the screen's height across.
            assertEquals(viewer.root.height, viewer.photoViewerContent.width)
            assertSame(pictures[saved("p2")], binding.viewerShows())
        }
        assertEquals(sent, modules.session.sent)
    }
}
