// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.SavedGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The library's page (FR-3.11), a pure function. Partitions: nothing saved (every count 0), both kinds (each filter
 * keeps its kind in the store's order, newest first), one kind only (the other filter empty, its count 0).
 */
class LibraryPageTest {
    private fun saved(id: String, source: GuideSource) = SavedGuide(id, savedAt = 0L, source = source)

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
    fun bothKinds_eachFilterKeepsItsKindInTheStoresOrder() {
        val newestFirst = listOf(photo2, pose, photo1)

        assertEquals(newestFirst, libraryPage(newestFirst, LibraryFilter.ALL).guides)
        assertEquals(listOf(photo2, photo1), libraryPage(newestFirst, LibraryFilter.PHOTOS).guides)
        assertEquals(listOf(pose), libraryPage(newestFirst, LibraryFilter.GENERATED).guides)
        val counts = libraryPage(newestFirst, LibraryFilter.GENERATED)
        assertEquals(3 to 2, counts.count(LibraryFilter.ALL) to counts.count(LibraryFilter.PHOTOS))
        assertEquals(1, counts.count(LibraryFilter.GENERATED))
    }

    @Test
    fun oneKindOnly_theOtherFilterIsEmpty() {
        val page = libraryPage(listOf(photo2, photo1), LibraryFilter.GENERATED)

        assertTrue(page.guides.isEmpty())
        assertEquals(0, page.count(LibraryFilter.GENERATED))
        assertEquals(2, page.count(LibraryFilter.PHOTOS))
    }
}
