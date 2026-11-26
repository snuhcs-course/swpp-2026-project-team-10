// AI-generated with Claude Code, 2026-11-25, reviewed by Joonhyung Han and Sungmin Jo
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import com.lastpenguin.pix.generation.Situation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A guide kept on the phone (FR-3.8), as the library lists it: where it came from, for its kind and the library's
 * filters (FR-3.11), the situation a generated pose was made for, and the user's memo (#270), empty when none. A guide
 * that ♥ or *Save guide* marked is a [favorite]; the others are the history every pose and photo guide leaves (#297).
 * Its thumbnail is read apart, by [SavedGuideStore.thumbnail], so a long history never sits in memory at once.
 */
data class SavedGuide(
    val id: String,
    val savedAt: Long,
    val source: GuideSource,
    val situation: Situation? = null,
    val memo: String = "",
    val favorite: Boolean = true,
)

/**
 * The library (Design 2.1, FR-3.8, AD-16): every guide kept on the phone, in the app's private storage only, never in
 * the gallery or on the server (NFR-13), and still there after the app restarts. A guide keeps its
 * [ReferenceGuide.id], so saving one that is already kept keeps the one copy. Nothing goes unless the user deletes it
 * (#297).
 * Owner: Guide controls (#58); the history and favorites (#297).
 */
interface SavedGuideStore {
    /** Newest first; empty until the storage has been read once. */
    val saved: StateFlow<List<SavedGuide>>

    /**
     * Keeps [guide] with [state], as a favorite or as history; a guide already kept stays as it is, favorite or not.
     * Fails when the storage cannot be written, and nothing is listed then.
     */
    suspend fun save(guide: ReferenceGuide, state: GuideState, favorite: Boolean = true): Result<SavedGuide>

    /** The guide as it was saved, with the state it was saved with; fails for an unknown id or unreadable files. */
    suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>>

    /**
     * The guide's thumbnail: the middle of its reference photo cut to 3:4, or of its outline when it has none, at most
     * [THUMBNAIL_WIDTH] × [THUMBNAIL_HEIGHT]. Read from the storage at each call, for the screens to keep what they
     * show; null for an unknown id or unreadable files.
     */
    suspend fun thumbnail(id: String): Bitmap?

    /** Removes the guides [ids]; an unknown id does nothing. */
    suspend fun delete(ids: Collection<String>)

    /** Removes a kept guide; an unknown id does nothing. */
    suspend fun delete(id: String) = delete(listOf(id))

    /**
     * Sets the memo of a kept guide (FR-3.11, #270), as [memoOf] keeps it; an empty one removes it. Fails for an
     * unknown id or when the storage cannot be written, and the memo stays as it was then.
     */
    suspend fun setMemo(id: String, memo: String): Result<Unit>

    /**
     * Marks a kept guide a favorite, or history again (#297), as ♥ and *Save guide* do. Fails for an unknown id or when
     * the storage cannot be written, and the guide stays as it was then.
     */
    suspend fun setFavorite(id: String, favorite: Boolean): Result<Unit>

    /**
     * Keeps guides for the life of the process only, otherwise like the real store (one copy per id); for tests and
     * screens that do not keep guides. The reference, or the outline, is the thumbnail, uncut.
     */
    class InMemory(private val clock: () -> Long = System::currentTimeMillis) : SavedGuideStore {
        private class Kept(val saved: SavedGuide, val guide: ReferenceGuide, val state: GuideState)

        private var kept = emptyList<Kept>()
        private val _saved = MutableStateFlow<List<SavedGuide>>(emptyList())
        override val saved: StateFlow<List<SavedGuide>> = _saved.asStateFlow()

        override suspend fun save(guide: ReferenceGuide, state: GuideState, favorite: Boolean): Result<SavedGuide> {
            kept.firstOrNull { it.saved.id == guide.id }?.let { return Result.success(it.saved) }
            val saved = SavedGuide(guide.id, clock(), guide.source, guide.situation, favorite = favorite)
            publish(listOf(Kept(saved, guide, state)) + kept)
            return Result.success(saved)
        }

        override suspend fun load(id: String): Result<Pair<ReferenceGuide, GuideState>> =
            kept.firstOrNull { it.saved.id == id }?.let { Result.success(it.guide to it.state) }
                ?: Result.failure(NoSuchElementException("No saved guide $id"))

        override suspend fun thumbnail(id: String): Bitmap? =
            kept.firstOrNull { it.saved.id == id }?.guide?.let { it.reference ?: it.outline }

        override suspend fun delete(ids: Collection<String>) {
            val gone = ids.toSet()
            publish(kept.filterNot { it.saved.id in gone })
        }

        override suspend fun setMemo(id: String, memo: String): Result<Unit> = change(id) {
            it.copy(memo = memoOf(memo))
        }

        override suspend fun setFavorite(id: String, favorite: Boolean): Result<Unit> =
            change(id) { it.copy(favorite = favorite) }

        private fun change(id: String, change: (SavedGuide) -> SavedGuide): Result<Unit> {
            if (kept.none { it.saved.id == id }) return Result.failure(NoSuchElementException("No saved guide $id"))
            publish(kept.map { if (it.saved.id == id) Kept(change(it.saved), it.guide, it.state) else it })
            return Result.success(Unit)
        }

        private fun publish(next: List<Kept>) {
            kept = next
            _saved.value = next.map { it.saved }
        }
    }

    companion object {
        /** A memo's length in characters (#270): two lines under a library cell. */
        const val MAX_MEMO = 40

        /** [memo] as a guide keeps it: on one line, without spaces around it, and at most [MAX_MEMO] characters. */
        fun memoOf(memo: String): String =
            memo.replace(Regex("\\s+"), " ").trim().let { text ->
                // Never cut a character made of two UTF-16 units in half.
                if (text.length <= MAX_MEMO) {
                    text
                } else {
                    text.take(MAX_MEMO).let {
                        if (it.last().isHighSurrogate()) it.dropLast(1) else it
                    }.trimEnd()
                }
            }

        /** A library cell's 3:4 on a 3x screen; a reference photo, at most 512 px long, is never enlarged to it. */
        const val THUMBNAIL_WIDTH = 384
        const val THUMBNAIL_HEIGHT = 512
    }
}
