// AI-generated with Claude Code, 2026-11-03, reviewed by Joonhyung Han and Sungmin Jo
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A guide kept on the phone (FR-3.8), as the library lists it: where it came from, for its kind and the library's
 * filters (FR-3.11). Its thumbnail is read apart, by [SavedGuideStore.thumbnail], so a hundred guides never sit in
 * memory at once.
 */
data class SavedGuide(val id: String, val savedAt: Long, val source: GuideSource)

/**
 * Saved guides (Design 2.1, FR-3.8, AD-16): in the app's private storage only, never in the gallery or on the server
 * (NFR-13), and still there after the app restarts. A guide keeps its [ReferenceGuide.id], so saving one that is
 * already saved keeps the one copy; at most [MAX_SAVED] are kept, and the oldest goes first.
 * Owner: Guide controls (#58).
 */
interface SavedGuideStore {
    /** Newest first; empty until the storage has been read once. */
    val saved: StateFlow<List<SavedGuide>>

    /** Keeps [guide] with [state]; fails when the storage cannot be written, and nothing is listed then. */
    suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide>

    /** The guide as it was saved, with the state it was saved with; fails for an unknown id or unreadable files. */
    suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>>

    /**
     * The guide's thumbnail: the middle of its reference photo cut to 3:4, or of its outline when it has none, at most
     * [THUMBNAIL_WIDTH] × [THUMBNAIL_HEIGHT]. Read from the storage at each call, for the screens to keep what they
     * show; null for an unknown id or unreadable files.
     */
    suspend fun thumbnail(id: String): Bitmap?

    /** Removes a saved guide; an unknown id does nothing. */
    suspend fun delete(id: String)

    /**
     * Keeps guides for the life of the process only, otherwise like the real store (one copy per id, the oldest
     * going first); for tests and screens that do not keep guides. The reference, or the outline, is the thumbnail,
     * uncut.
     */
    class InMemory(private val clock: () -> Long = System::currentTimeMillis) : SavedGuideStore {
        private class Kept(val saved: SavedGuide, val guide: ReferenceGuide, val state: GuideState)

        private var kept = emptyList<Kept>()
        private val _saved = MutableStateFlow<List<SavedGuide>>(emptyList())
        override val saved: StateFlow<List<SavedGuide>> = _saved.asStateFlow()

        override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> {
            kept.firstOrNull { it.saved.id == guide.id }?.let { return Result.success(it.saved) }
            val entry = Kept(SavedGuide(guide.id, clock(), guide.source), guide, state)
            publish((listOf(entry) + kept).take(MAX_SAVED))
            return Result.success(entry.saved)
        }

        override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> =
            kept.firstOrNull { it.saved.id == id }?.let { Result.success(it.guide to it.state) }
                ?: Result.failure(NoSuchElementException("No saved guide $id"))

        override suspend fun thumbnail(id: String): Bitmap? =
            kept.firstOrNull { it.saved.id == id }?.guide?.let { it.reference ?: it.outline }

        override suspend fun delete(id: String) {
            publish(kept.filterNot { it.saved.id == id })
        }

        private fun publish(next: List<Kept>) {
            kept = next
            _saved.value = next.map { it.saved }
        }
    }

    companion object {
        /** The library's size (FR-3.8): saving one more removes the oldest. */
        const val MAX_SAVED = 100

        /** A library cell's 3:4 on a 3x screen; a reference photo, at most 512 px long, is never enlarged to it. */
        const val THUMBNAIL_WIDTH = 384
        const val THUMBNAIL_HEIGHT = 512
    }
}
