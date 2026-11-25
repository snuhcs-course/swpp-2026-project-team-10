// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.guide

import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.SavedGuide
import com.lastpenguin.pix.ui.generation.SHEET_SITUATIONS

/**
 * The library's tabs (FR-3.11, #297), in their order: every guide, which every pose and used photo guide joins by
 * itself, and the favorites that a ♥ or *Save guide* marked.
 */
enum class LibraryFilter {
    ALL,
    FAVORITES,
    ;

    fun matches(guide: SavedGuide): Boolean = when (this) {
        ALL -> true
        FAVORITES -> guide.favorite
    }
}

/**
 * What the library shows: the guides of [filter] and, when one is chosen, of [situation], newest first; how many there
 * are for each filter; and the situations that saved guides have, each with its count, in the order of the tiles on
 * *Add a pose guide* (#270). While selecting (#297), [selection] holds the ids of the selected guides that are listed.
 */
data class LibraryPage(
    val filter: LibraryFilter,
    val guides: List<SavedGuide>,
    val counts: Map<LibraryFilter, Int>,
    val situation: Situation? = null,
    val situations: Map<Situation, Int> = emptyMap(),
    val selection: Set<String>? = null,
) {
    fun count(filter: LibraryFilter): Int = counts[filter] ?: 0

    val selecting: Boolean get() = selection != null
}

/**
 * [saved], newest first as the store lists it, through [filter] and [situation]; the counts are of every saved guide.
 * A situation no saved guide has any longer, as after its last guide was deleted, filters nothing. Of [selection], only
 * the guides listed stay selected, so a filter changed while selecting never deletes what it hides.
 */
fun libraryPage(
    saved: List<SavedGuide>,
    filter: LibraryFilter,
    situation: Situation? = null,
    selection: Set<String>? = null,
): LibraryPage {
    val situations = SHEET_SITUATIONS
        .associateWith { kind -> saved.count { it.situation == kind } }
        .filterValues { it > 0 }
    val chosen = situation?.takeIf { it in situations }
    val guides = saved.filter { filter.matches(it) && (chosen == null || it.situation == chosen) }
    val listed = guides.mapTo(HashSet()) { it.id }
    return LibraryPage(
        filter = filter,
        guides = guides,
        counts = LibraryFilter.entries.associateWith { kind -> saved.count(kind::matches) },
        situation = chosen,
        situations = situations,
        selection = selection?.filterTo(HashSet()) { it in listed },
    )
}
