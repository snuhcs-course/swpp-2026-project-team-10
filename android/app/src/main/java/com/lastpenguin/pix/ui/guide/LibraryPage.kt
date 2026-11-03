// AI-generated with Claude Code, 2026-11-03, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.SavedGuide

/** The library's filters (FR-3.11): every saved guide, or those of one kind. */
enum class LibraryFilter {
    ALL,
    PHOTOS,
    GENERATED,
    ;

    fun matches(guide: SavedGuide): Boolean = when (this) {
        ALL -> true
        PHOTOS -> guide.source == GuideSource.GALLERY
        GENERATED -> guide.source == GuideSource.GENERATED
    }
}

/** What the library shows: the guides of [filter], newest first, and how many there are for each filter. */
data class LibraryPage(val filter: LibraryFilter, val guides: List<SavedGuide>, val counts: Map<LibraryFilter, Int>) {
    fun count(filter: LibraryFilter): Int = counts[filter] ?: 0
}

/** [saved], newest first as the store lists it, through [filter]; the counts are of every saved guide. */
fun libraryPage(saved: List<SavedGuide>, filter: LibraryFilter): LibraryPage = LibraryPage(
    filter = filter,
    guides = saved.filter(filter::matches),
    counts = LibraryFilter.entries.associateWith { kind -> saved.count(kind::matches) },
)
