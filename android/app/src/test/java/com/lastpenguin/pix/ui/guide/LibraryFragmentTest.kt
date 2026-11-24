// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.guide

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.lastpenguin.pix.R
import com.lastpenguin.pix.databinding.FragmentLibraryBinding
import com.lastpenguin.pix.databinding.ItemLibraryGuideBinding
import com.lastpenguin.pix.generation.Situation
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
import org.robolectric.shadows.ShadowPopupMenu
import org.robolectric.shadows.ShadowToast

/*
 * The library (FR-3.11, R&S 6.2), on the real screen. Partitions: nothing saved (the empty state, no filters); guides
 * of both kinds (one cell each, newest first, with its kind's icon, its thumbnail, and TalkBack's name; the filters
 * with their counts, All chosen); a filter (only its kind; a kind with no guide says so, without the hint to save one);
 * a tap (the guide on the camera, back to the camera), a guide that cannot be opened (a message, the library stays),
 * the library left while a guide opens (the camera's guide kept); *Add photo* (a picked photo opens Reference confirm over the
 * library); from Subject view (a tap goes back to Subject view, and so does the session ending).
 * Situations and memos (#270): no guide with a situation (no chips, no words under the cells); guides with situations
 * and a memo (each cell's situation and memo, TalkBack's name with both, a chip per situation with its count); a chip
 * (its guides only, checked) and the same chip again (every guide); the pencil (the memo in the field, Save keeps the
 * new one under the cell, Cancel keeps the old, an empty one removes it), a memo the storage cannot keep (a message).
 * Favorites and selection (#297): a favorite (its tab with its count, its cell's heart, TalkBack's name); a long press,
 * which TalkBack names *Select* (selecting with that guide: the count, its mark, Select all, Delete for Add photo, no
 * pencil), a tap while selecting (selects another, never uses it; on a selected one, takes it out), Select all,
 * none selected (Delete off), Delete (asks with the count: Cancel keeps them, Delete removes them and stops selecting),
 * ✕ and Back (stop selecting, the library stays); ⋮ with Select (selecting with none) and Delete all (every guide the
 * filter lists, after asking).
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

    private fun saved(id: String, source: GuideSource = GuideSource.GALLERY, situation: Situation? = null) =
        ReferenceGuide(id, bitmap(), bitmap(), 0.5f, source, reference = bitmap(60, 80), situation = situation)

    /** A photo with a memo and two AI poses, a landmark's and a selfie's, saved in that order. */
    private fun withSituations(store: SavedGuideStore = SavedGuideStore.InMemory()): TestAppContainer {
        runBlocking {
            store.save(saved("photo"), GuideState())
            store.setMemo("photo", "Mom at the beach")
            store.save(saved("landmark", GuideSource.GENERATED, Situation.LANDMARK), GuideState())
            store.save(saved("selfie", GuideSource.GENERATED, Situation.SELFIE), GuideState())
        }
        return TestAppContainer(saved = store)
    }

    private fun chips(): List<Chip> = onScreen { binding ->
        (0 until binding.situationChips.childCount).map { binding.situationChips.getChildAt(it) as Chip }
    }

    private fun memoDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun memoField(): EditText = memoDialog().findViewById(R.id.input)!!

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
    fun noSituationOrMemo_showsNoChipsAndNoWordsUnderTheCells() {
        launch(withThree())

        assertFalse(onScreen { it.situationScroll.isShown })
        assertFalse(cell(1).situationText.isShown)
        assertFalse(cell(1).memoText.isShown)
        assertTrue(cell(1).memoButton.isShown)
    }

    @Test
    fun situationsAndAMemo_showUnderTheCellsAndAsChips() {
        launch(withSituations())

        assertEquals(listOf("Landmark 1", "Selfie 1"), chips().map { it.text.toString() })
        assertFalse(chips().any { it.isChecked })
        assertEquals("Selfie", cell(0).situationText.text.toString())
        assertFalse(cell(0).memoText.isShown)
        assertFalse(cell(2).situationText.isShown)
        assertEquals("Mom at the beach", cell(2).memoText.text.toString())
        assertTrue(cell(2).memoText.isShown)
        val landmark = cell(1).thumbnail.contentDescription.toString()
        assertTrue(landmark, landmark.endsWith(", for Landmark"))
        val photo = cell(2).thumbnail.contentDescription.toString()
        assertTrue(photo, photo.endsWith(". Memo: Mom at the beach"))
    }

    @Test
    fun situationChip_showsItsGuidesOnly_andASecondTapShowsAllAgain() {
        launch(withSituations())

        chips()[0].performClick()
        idleUntil { itemCount() == 1 }
        assertEquals("Landmark", cell(0).situationText.text.toString())
        assertTrue(chips()[0].isChecked)
        assertFalse(chips()[1].isChecked)

        chips()[0].performClick()
        idleUntil { itemCount() == 3 }
        assertFalse(chips().any { it.isChecked })
    }

    @Test
    fun pencil_editsTheMemo_saveKeepsIt_cancelDoesNot_andAnEmptyOneRemovesIt() {
        val modules = launch(withSituations())

        cell(2).memoButton.performClick()
        idle()
        assertEquals("Mom at the beach", memoField().text.toString())
        memoField().setText("Dad")
        memoDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals("Mom at the beach", modules.saved.saved.value.last().memo)

        cell(2).memoButton.performClick()
        idle()
        memoField().setText("Dad on the bridge")
        memoDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleUntil { cell(2).memoText.text.toString() == "Dad on the bridge" }
        assertEquals("Dad on the bridge", modules.saved.saved.value.last().memo)

        cell(0).memoButton.performClick()
        idle()
        assertEquals("", memoField().text.toString())
        cell(2).memoButton.performClick()
        idle()
        memoField().setText("")
        memoDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleUntil { !cell(2).memoText.isShown }
        assertEquals("", modules.saved.saved.value.last().memo)
    }

    @Test
    fun pencil_memoTheStorageCannotKeep_saysSo() {
        val store = object : SavedGuideStore by SavedGuideStore.InMemory() {
            override suspend fun setMemo(id: String, memo: String): Result<Unit> = Result.failure(IOException("full"))
        }
        launch(withThree(store))

        cell(0).memoButton.performClick()
        idle()
        memoField().setText("Lost")
        memoDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertEquals(string(R.string.library_memo_failed), ShadowToast.getTextOfLatestToast())
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
    fun longPress_selects_tapsSelectMore_andDeleteAsksFirst_cancelKeepsThem_deleteRemovesThem() {
        val modules = launch(withThree())
        val node = AccessibilityNodeInfo.obtain()
        cell(0).thumbnail.onInitializeAccessibilityNodeInfo(node)
        val longClick = node.actionList.single { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
        assertEquals(string(R.string.library_select), longClick.label)

        cell(0).thumbnail.performLongClick()
        idle()
        assertEquals("1 selected", onScreen { it.titleText.text.toString() })
        assertTrue(cell(0).selectMark.isShown)
        assertEquals(string(R.string.library_guide_selected), ViewCompat.getStateDescription(cell(0).thumbnail))
        assertFalse(cell(0).memoButton.isShown)
        onScreen { binding ->
            assertTrue(binding.selectAllButton.isShown)
            assertFalse(binding.moreButton.isShown)
            assertFalse(binding.addPhotoButton.isShown)
            assertTrue(binding.deleteSelectedButton.isEnabled)
        }

        // A tap selects another, and does not put it on the camera.
        cell(2).thumbnail.performClick()
        idle()
        assertNull(modules.guides.guide.value)
        assertEquals("2 selected", onScreen { it.titleText.text.toString() })

        onScreen { it.deleteSelectedButton.performClick() }
        idle()
        val ask = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(
            "Delete 2 guides?",
            ask.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text.toString(),
        )
        ask.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(3, modules.saved.saved.value.size)

        onScreen { it.deleteSelectedButton.performClick() }
        idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleUntil { itemCount() == 1 }

        assertEquals(listOf("pose"), modules.saved.saved.value.map { it.id })
        assertEquals("Library", onScreen { it.titleText.text.toString() })
        assertTrue(onScreen { it.addPhotoButton.isShown })
        assertEquals(R.id.libraryFragment, nav.currentDestination?.id)
    }

    @Test
    fun selecting_selectAllSelectsEveryGuideListed_andBackStopsSelectingWithoutLeaving() {
        launch(withThree())
        cell(1).thumbnail.performLongClick()
        idle()

        onScreen { it.selectAllButton.performClick() }
        idle()
        assertEquals("3 selected", onScreen { it.titleText.text.toString() })
        assertFalse(onScreen { it.selectAllButton.isEnabled })

        // A tap on a selected guide takes it out again.
        cell(1).thumbnail.performClick()
        idle()
        assertEquals("2 selected", onScreen { it.titleText.text.toString() })

        scenario!!.onFragment { it.requireActivity().onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(R.id.libraryFragment, nav.currentDestination?.id)
        assertEquals("Library", onScreen { it.titleText.text.toString() })
        assertFalse(cell(0).selectMark.isShown)
        assertTrue(cell(0).memoButton.isShown)
    }

    @Test
    fun selecting_noGuideSelected_turnsDeleteOff_andCloseStopsSelecting() {
        launch(withThree())
        cell(0).thumbnail.performLongClick()
        idle()
        cell(0).thumbnail.performClick()
        idle()

        assertEquals("0 selected", onScreen { it.titleText.text.toString() })
        assertFalse(onScreen { it.deleteSelectedButton.isEnabled })
        assertEquals(string(R.string.library_close_selection), onScreen { it.backButton.contentDescription })
        onScreen { it.backButton.performClick() }
        idle()
        assertEquals("Library", onScreen { it.titleText.text.toString() })
    }

    @Test
    fun more_selectStartsSelecting_andDeleteAllDeletesEveryGuideTheFilterLists_afterAsking() {
        val modules = launch(withThree())

        onScreen { it.moreButton.performClick() }
        idle()
        clickMenuItem(R.id.selectGuides)
        assertEquals("0 selected", onScreen { it.titleText.text.toString() })
        onScreen { it.backButton.performClick() }
        idle()

        onScreen { it.filterPhotos.performClick() }
        idleUntil { itemCount() == 2 }
        onScreen { it.moreButton.performClick() }
        idle()
        clickMenuItem(R.id.deleteAllGuides)
        val ask = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(
            "Delete 2 guides?",
            ask.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text.toString(),
        )
        ask.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleUntil { itemCount() == 0 }

        // Only what the filter listed: the AI pose stays.
        assertEquals(listOf("pose"), modules.saved.saved.value.map { it.id })
    }

    @Test
    fun favorites_haveTheirTab_andAHeartOnTheirCell() {
        val store = SavedGuideStore.InMemory()
        runBlocking {
            store.save(saved("photo-1"), GuideState(), favorite = false)
            store.save(saved("pose", GuideSource.GENERATED), GuideState(), favorite = true)
            store.save(saved("photo-2"), GuideState(), favorite = false)
        }
        launch(TestAppContainer(saved = store))

        assertEquals("Favorites 1", onScreen { it.filterFavorites.text.toString() })
        assertTrue(cell(1).favoriteIcon.isShown)
        assertFalse(cell(0).favoriteIcon.isShown)
        val name = cell(1).thumbnail.contentDescription.toString()
        assertTrue(name, name.endsWith(", in Favorites"))

        onScreen { it.filterFavorites.performClick() }
        idleUntil { itemCount() == 1 }
        assertTrue(onScreen { it.filterFavorites.isSelected })
        assertEquals(R.drawable.ic_sparkle, shadowOf(cell(0).kindIcon.drawable).createdFromResId)
    }

    /** Taps [id] in the ⋮ menu that is open. */
    private fun clickMenuItem(id: Int) {
        val menu = ShadowPopupMenu.getLatestPopupMenu()
        shadowOf(menu).onMenuItemClickListener.onMenuItemClick(menu.menu.findItem(id))
        idle()
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
