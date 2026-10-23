// AI-generated with Claude Code, 2026-10-23, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

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

    companion object {
        const val MAX_SAVED = 20
    }
}
