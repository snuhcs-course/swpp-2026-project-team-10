// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.SavedGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The library's page (FR-3.11), a pure function. Partitions: nothing saved (every count 0), both kinds (All keeps
 * every guide in the store's order, newest first), the tabs' order (All, then Favorites).
 * Situations (#270): none saved (no chips), several (each counted once, in the tiles' order), one chosen (its guides
 * only, within the tab), one chosen that no guide has any longer (filters nothing, none chosen).
 * Favorites and selection (#297): the favorites only, with their count; none yet (empty, its count 0); a selection
 * keeps the listed guides only; none while not selecting.
 */
class LibraryPageTest {
    private fun saved(id: String, source: GuideSource, situation: Situation? = null) =
        SavedGuide(id, savedAt = 0L, source = source, situation = situation)

    private val photo1 = saved("photo-1", GuideSource.GALLERY)
    private val pose = saved("pose", GuideSource.GENERATED)
    private val photo2 = saved("photo-2", GuideSource.GALLERY)

    @Test
    fun nothingSaved_isEmptyWithEveryCountZero() {
        val page = libraryPage(emptyList(), LibraryFilter.ALL)

        assertTrue(page.guides.isEmpty())
        LibraryFilter.entries.forEach { assertEquals(0, page.count(it)) }
    }

    @Test
    fun bothKinds_allKeepsEveryGuideInTheStoresOrder() {
        val newestFirst = listOf(photo2, pose, photo1)

        val page = libraryPage(newestFirst, LibraryFilter.ALL)

        assertEquals(newestFirst, page.guides)
        assertEquals(3, page.count(LibraryFilter.ALL))
    }

    @Test
    fun tabs_areAllThenFavorites() {
        assertEquals(listOf(LibraryFilter.ALL, LibraryFilter.FAVORITES), LibraryFilter.entries)
    }

    @Test
    fun noFavoriteYet_favoritesIsEmpty() {
        val history = listOf(photo2.copy(favorite = false), pose.copy(favorite = false))

        val page = libraryPage(history, LibraryFilter.FAVORITES)

        assertTrue(page.guides.isEmpty())
        assertEquals(0, page.count(LibraryFilter.FAVORITES))
        assertEquals(2, page.count(LibraryFilter.ALL))
    }

    private val landmark1 = saved("landmark-1", GuideSource.GENERATED, Situation.LANDMARK)
    private val selfie = saved("selfie", GuideSource.GENERATED, Situation.SELFIE)
    private val landmark2 = saved("landmark-2", GuideSource.GENERATED, Situation.LANDMARK)
    private val background = saved("background", GuideSource.GENERATED, Situation.BACKGROUND)
    private val halfBody = saved("half-body", GuideSource.GENERATED, Situation.HALF_BODY)

    @Test
    fun noSituationSaved_hasNoSituations() {
        val page = libraryPage(listOf(photo1, pose), LibraryFilter.ALL)

        assertTrue(page.situations.isEmpty())
        assertNull(page.situation)
    }

    @Test
    fun severalSituations_eachIsCountedInTheTilesOrder() {
        val page = libraryPage(listOf(landmark2, selfie, background, photo1, halfBody, landmark1), LibraryFilter.ALL)

        // Half body comes before Background on the sheet, though not among the situations' names on the server.
        assertEquals(
            listOf(Situation.HALF_BODY to 1, Situation.BACKGROUND to 1, Situation.LANDMARK to 2, Situation.SELFIE to 1),
            page.situations.toList(),
        )
    }

    @Test
    fun situationChosen_keepsItsGuidesWithinTheTab() {
        val newestFirst = listOf(landmark2, selfie, photo1, landmark1.copy(favorite = false))

        val all = libraryPage(newestFirst, LibraryFilter.ALL, Situation.LANDMARK)
        val favorites = libraryPage(newestFirst, LibraryFilter.FAVORITES, Situation.LANDMARK)

        assertEquals(listOf(landmark2, newestFirst[3]), all.guides)
        assertEquals(Situation.LANDMARK, all.situation)
        assertEquals(listOf(landmark2), favorites.guides)
        assertEquals(4, all.count(LibraryFilter.ALL))
    }

    @Test
    fun situationChosenThatNoGuideHasAnyLonger_filtersNothing() {
        val page = libraryPage(listOf(landmark1, photo1), LibraryFilter.ALL, Situation.SELFIE)

        assertEquals(listOf(landmark1, photo1), page.guides)
        assertNull(page.situation)
    }

    @Test
    fun favorites_listsOnlyTheFavorites_andCountsThem() {
        val favorite = SavedGuide("fav", savedAt = 0L, source = GuideSource.GENERATED, favorite = true)
        val history = SavedGuide("hist", savedAt = 0L, source = GuideSource.GENERATED, favorite = false)

        val page = libraryPage(listOf(history, favorite), LibraryFilter.FAVORITES)

        assertEquals(listOf(favorite), page.guides)
        assertEquals(1, page.count(LibraryFilter.FAVORITES))
        assertEquals(2, page.count(LibraryFilter.ALL))
    }

    @Test
    fun selection_keepsOnlyTheGuidesListed_andNoneIsNotSelecting() {
        val newestFirst = listOf(photo2, pose, photo1)

        val history = listOf(photo2, pose.copy(favorite = false), photo1)
        val favorites = libraryPage(history, LibraryFilter.FAVORITES, selection = setOf("pose", "photo-1"))
        val notSelecting = libraryPage(newestFirst, LibraryFilter.ALL)

        // The pose is hidden by the tab, so it is not selected: nothing hidden is ever deleted.
        assertEquals(setOf("photo-1"), favorites.selection)
        assertTrue(favorites.selecting)
        assertNull(notSelecting.selection)
        assertFalse(notSelecting.selecting)
    }
}
