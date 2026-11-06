// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentLibraryBinding
import com.lastpenguin.pix.databinding.ItemLibraryGuideBinding
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.SavedGuideStore
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.PeerInfo
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.ui.ScreenTestSupport.bitmap
import com.lastpenguin.pix.ui.ScreenTestSupport.idle
import com.lastpenguin.pix.ui.ScreenTestSupport.idleUntil
import com.lastpenguin.pix.ui.ScreenTestSupport.launchPrepared
import com.lastpenguin.pix.ui.ScreenTestSupport.string
import com.lastpenguin.pix.ui.ScreenTestSupport.viewModel
import com.lastpenguin.pix.ui.TestAppContainer
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/*
 * The library (FR-3.11, R&S 6.2), on the real screen. Partitions: nothing saved (the empty state, no filters); guides
 * of both kinds (one cell each, newest first, with its kind's icon, its thumbnail, and TalkBack's name; the filters
 * with their counts, All chosen); a filter (only its kind; a kind with no guide says so, without the hint to save one);
 * a tap (the guide on the camera, back to the camera), a guide that cannot be opened (a message, the library stays),
 * the library left while a guide opens (the camera's guide kept); a long press, which TalkBack names *Delete saved
 * guide* (asks first: Cancel keeps it, Delete removes it); *Add photo* (a picked photo opens Reference confirm over the
 * library); from Subject view (a tap goes back to Subject view, and so does the session ending).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryFragmentTest {
    private var scenario: FragmentScenario<LibraryFragment>? = null
    private lateinit var nav: TestNavHostController

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** The library over the camera, as *All* on Add a pose guide opens it. */
    private fun launch(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        val (launched, controller) = launchPrepared<LibraryFragment>(R.id.addGuideSheet) {
            it.viewModel<ReferenceViewModel>().openFor(fromSubject = false)
        }
        scenario = launched
        nav = controller
        nav.navigate(R.id.action_addGuide_to_library)
        idle()
        return modules
    }

    /** The library over Subject view, as its *Guide* button and *All* open it. */
    private fun launchFromSubject(modules: TestAppContainer = TestAppContainer()): TestAppContainer {
        modules.install()
        val (launched, controller) = launchPrepared<LibraryFragment>(R.id.libraryFragment) {
            it.viewModel<ReferenceViewModel>().openFor(fromSubject = true)
        }
        scenario = launched
        nav = controller
        nav.setCurrentDestination(R.id.subjectFragment)
        nav.navigate(R.id.action_subject_to_addGuide)
        nav.navigate(R.id.action_addGuide_to_library)
        modules.session.setState(SessionState.Connected("s1", PeerInfo("Dongje"), Role.SUBJECT))
        idle()
        return modules
    }

    private fun <T> onScreen(block: (FragmentLibraryBinding) -> T): T {
        var result: T? = null
        scenario!!.onFragment { result = block(FragmentLibraryBinding.bind(it.requireView())) }
        return result!!
    }

    private fun cell(index: Int): ItemLibraryGuideBinding = onScreen { binding ->
        val holder = binding.libraryGrid.findViewHolderForAdapterPosition(index) as LibraryAdapter.Holder
        holder.binding
    }

    private fun itemCount(): Int = onScreen { it.libraryGrid.adapter?.itemCount ?: 0 }

    private fun saved(id: String, source: GuideSource = GuideSource.GALLERY) =
        ReferenceGuide(id, bitmap(), bitmap(), 0.5f, source, reference = bitmap(60, 80))

    /** Two photos and one AI pose, saved in that order, so the pose is the newest but one. */
    private fun withThree(store: SavedGuideStore = SavedGuideStore.InMemory()): TestAppContainer {
        runBlocking {
            store.save(saved("photo-1"), GuideState())
            store.save(saved("pose", GuideSource.GENERATED), GuideState())
            store.save(saved("photo-2"), GuideState())
        }
        return TestAppContainer(saved = store)
    }

    @Test
    fun nothingSaved_showsTheEmptyStateWithoutFilters() {
        launch()

        onScreen { binding ->
            assertTrue(binding.emptyState.isShown)
            assertEquals(string(R.string.library_empty), binding.emptyTitle.text.toString())
            assertTrue(binding.emptyBody.isShown)
            assertFalse(binding.filterRow.isShown)
            assertTrue(binding.addPhotoButton.isShown)
            assertEquals("0", binding.countText.text.toString())
        }
        assertEquals(0, itemCount())
    }

    @Test
    fun guidesSaved_showsEachWithItsKindAndTheFiltersWithTheirCounts() {
        launch(withThree())

        assertEquals(3, itemCount())
        onScreen { binding ->
            assertFalse(binding.emptyState.isShown)
            assertEquals("3", binding.countText.text.toString())
            assertEquals(string(R.string.library_title_description, 3), binding.titleText.contentDescription)
            assertEquals("All 3", binding.filterAll.text.toString())
            assertEquals("Photos 2", binding.filterPhotos.text.toString())
            assertEquals("AI poses 1", binding.filterGenerated.text.toString())
            assertTrue(binding.filterAll.isSelected)
            assertFalse(binding.filterGenerated.isSelected)
        }
        assertEquals(R.drawable.ic_image, shadowOf(cell(0).kindIcon.drawable).createdFromResId)
        assertEquals(R.drawable.ic_sparkle, shadowOf(cell(1).kindIcon.drawable).createdFromResId)
        assertNotNull(cell(1).thumbnail.drawable)
        val name = cell(1).thumbnail.contentDescription.toString()
        assertTrue(name, name.startsWith("AI pose guide saved "))
    }

    @Test
    fun filter_aiPoses_showsOnlyThem_andAKindWithoutGuidesSaysSo() {
        launch(withThree())

        onScreen { it.filterGenerated.performClick() }
        idleUntil { itemCount() == 1 }

        assertEquals(1, itemCount())
        assertTrue(onScreen { it.filterGenerated.isSelected })
        assertEquals(R.drawable.ic_sparkle, shadowOf(cell(0).kindIcon.drawable).createdFromResId)
        scenario?.close()

        val photosOnly = TestAppContainer()
        runBlocking { photosOnly.saved.save(saved("photo"), GuideState()) }
        launch(photosOnly)
        onScreen { it.filterGenerated.performClick() }
        idleUntil { itemCount() == 0 }

        onScreen { binding ->
            assertTrue(binding.emptyState.isShown)
            assertEquals(string(R.string.library_empty_filter), binding.emptyTitle.text.toString())
            assertFalse(binding.emptyBody.isShown)
            assertTrue(binding.filterRow.isShown)
        }
    }

    @Test
    fun tap_putsTheGuideOnTheCameraAndGoesBack() {
        val modules = launch(withThree())

        cell(1).thumbnail.performClick()
        idle()

        assertEquals("pose", modules.guides.guide.value?.id)
        assertEquals(R.id.cameraFragment, nav.currentDestination?.id)
    }

    /** A store whose loads fail, or wait until [open] completes. */
    private class GatedStore(
        private val fails: Boolean = false,
        private val inner: SavedGuideStore = SavedGuideStore.InMemory(),
    ) : SavedGuideStore by inner {
        val open = CompletableDeferred<Unit>()

        override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> {
            if (fails) return Result.failure(IOException("unreadable"))
            open.await()
            return inner.load(id)
        }
    }

    @Test
    fun aGuideThatCannotBeOpened_saysSoAndStays() {
        val modules = launch(withThree(GatedStore(fails = true)))

        cell(0).thumbnail.performClick()
        idle()

        assertEquals(string(R.string.saved_guide_open_failed), ShadowToast.getTextOfLatestToast())
        assertEquals(R.id.libraryFragment, nav.currentDestination?.id)
        assertNull(modules.guides.guide.value)
    }

    @Test
    fun leavingWhileAGuideOpens_keepsTheCamerasGuide() {
        val store = GatedStore()
        val modules = launch(withThree(store))
        cell(0).thumbnail.performClick()
        idle()

        scenario!!.moveToState(Lifecycle.State.DESTROYED)
        store.open.complete(Unit)
        idle()

        assertNull(modules.guides.guide.value)
    }

    @Test
    fun longPress_asksFirst_cancelKeepsIt_deleteRemovesIt() {
        val modules = launch(withThree())
        val node = AccessibilityNodeInfo.obtain()
        cell(0).thumbnail.onInitializeAccessibilityNodeInfo(node)
        val longClick = node.actionList.single { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
        assertEquals(string(R.string.delete_saved_guide), longClick.label)

        cell(0).thumbnail.performLongClick()
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(3, modules.saved.saved.value.size)

        cell(0).thumbnail.performLongClick()
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleUntil { itemCount() == 2 }

        assertEquals(listOf("pose", "photo-1"), modules.saved.saved.value.map { it.id })
        assertEquals(2, itemCount())
        assertEquals("All 2", onScreen { it.filterAll.text.toString() })
        assertEquals(R.id.libraryFragment, nav.currentDestination?.id)
    }

    @Test
    fun addPhoto_aPickedPhoto_opensReferenceConfirmOverTheLibrary() {
        launch()

        onScreen { it.addPhotoButton.performClick() }
        scenario!!.onFragment { fragment ->
            val activity = shadowOf(fragment.requireActivity())
            val picker = activity.nextStartedActivityForResult
            activity.receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(Uri.parse("content://photos/1")))
        }
        idle()

        assertEquals(R.id.referenceConfirmFragment, nav.currentDestination?.id)
        assertEquals(R.id.libraryFragment, nav.previousBackStackEntry?.destination?.id)
    }

    @Test
    fun fromSubjectView_aTapGoesBackToSubjectView_notToThisPhonesCamera() {
        val modules = launchFromSubject(withThree())

        cell(0).thumbnail.performClick()
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
        assertNull(modules.guides.guide.value)
    }

    @Test
    fun fromSubjectView_theSessionEnding_goesBackToSubjectView() {
        val modules = launchFromSubject()

        modules.session.setState(SessionState.Ended(EndReason.CONNECTION_LOST))
        idle()

        assertEquals(R.id.subjectFragment, nav.currentDestination?.id)
    }
}
