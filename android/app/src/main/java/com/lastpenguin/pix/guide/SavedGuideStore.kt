// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A guide kept on the phone (FR-3.8), as *Add a pose guide* lists it: [thumbnail] is a small copy of its reference. */
data class SavedGuide(val id: String, val savedAt: Long, val thumbnail: Bitmap)

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

    /** Removes a saved guide; an unknown id does nothing. */
    suspend fun delete(id: String)

    /**
     * Keeps guides for the life of the process only, otherwise like the real store (one copy per id, the oldest
     * going first); for tests and screens that do not keep guides. The reference, or the cutout, is the thumbnail.
     */
    class InMemory(private val clock: () -> Long = System::currentTimeMillis) : SavedGuideStore {
        private class Kept(val saved: SavedGuide, val guide: ReferenceGuide, val state: GuideState)

        private var kept = emptyList<Kept>()
        private val _saved = MutableStateFlow<List<SavedGuide>>(emptyList())
        override val saved: StateFlow<List<SavedGuide>> = _saved.asStateFlow()

        override suspend fun save(guide: ReferenceGuide, state: GuideState): Result<SavedGuide> {
            kept.firstOrNull { it.saved.id == guide.id }?.let { return Result.success(it.saved) }
            val entry = Kept(SavedGuide(guide.id, clock(), guide.reference ?: guide.cutout), guide, state)
            publish((listOf(entry) + kept).take(MAX_SAVED))
            return Result.success(entry.saved)
        }

        override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> =
            kept.firstOrNull { it.saved.id == id }?.let { Result.success(it.guide to it.state) }
                ?: Result.failure(NoSuchElementException("No saved guide $id"))

        override suspend fun delete(id: String) {
            publish(kept.filterNot { it.saved.id == id })
        }

        private fun publish(next: List<Kept>) {
            kept = next
            _saved.value = next.map { it.saved }
        }
    }

    companion object {
        const val MAX_SAVED = 20
    }
}
