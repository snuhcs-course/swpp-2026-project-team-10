// AI-generated with Claude Code, 2026-11-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.guide.ReferenceGuideMaker
import com.lastpenguin.pix.guide.SavedGuideStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A candidate of Pick a pose by its set and its pose: pose ids repeat in every set. */
data class PoseKey(val setId: String, val poseId: String)

/** A candidate's ♥ (FR-4.14): its guide being made and saved, or saved in the library as [guideId]. */
sealed interface Heart {
    data object Saving : Heart

    data class Saved(val guideId: String) : Heart
}

/** What a ♥ did (FR-4.14), shown as a short notice on Pick a pose. */
enum class PoseSaveNotice {
    SAVED,

    /** Saved, and the oldest saved guide was deleted to keep [SavedGuideStore.MAX_SAVED]. */
    SAVED_OLDEST_REMOVED,
    REMOVED,

    /** No person was found in the candidate, so nothing was saved. */
    NO_PERSON,
    FAILED,
}

/** A ♥'s [notice], for the set [setId]: a screen shows only its own set's, not one that ended after it was left. */
data class PoseSaveResult(val setId: String, val notice: PoseSaveNotice)

/** The hearts of the set [setId], by pose id. */
fun Map<PoseKey, Heart>.inSet(setId: String?): Map<String, Heart> =
    entries.filter { it.key.setId == setId }.associate { it.key.poseId to it.value }

/**
 * The ♥ on both phones' Pick a pose (FR-4.14, Design 2.6.3): cuts the person out of a candidate on this phone, as *Use
 * this pose* does, and saves the guide to this phone's library as a generated pose; a tap on a filled heart deletes it
 * again. Shared by the activity, so a save goes on after the screen is left, and nothing of it is sent to the other
 * phone. A heart is filled while the library has its guide: one deleted there, or as the oldest, is empty again.
 * Owner: the library (#171).
 */
class PoseSaveViewModel(private val maker: ReferenceGuideMaker, private val store: SavedGuideStore) : ViewModel() {

    private val marked = MutableStateFlow<Map<PoseKey, Heart>>(emptyMap())

    /** The guides the hearts saved, for *Use this pose*: only the latest set's are kept. */
    private val made = mutableMapOf<PoseKey, ReferenceGuide>()

    /** Each candidate's heart; a candidate without one has an empty heart. */
    val hearts: StateFlow<Map<PoseKey, Heart>> = combine(marked, store.saved) { hearts, saved ->
        val kept = saved.mapTo(HashSet()) { it.id }
        hearts.filterValues { it !is Heart.Saved || it.guideId in kept }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _results = Channel<PoseSaveResult>(Channel.BUFFERED)

    /** What each ♥ did once it finished, read once. */
    val results: Flow<PoseSaveResult> = _results.receiveAsFlow()

    /** The ♥ on the candidate [key], whose image is [image]: saves it, or deletes it once saved; ignored while saving. */
    fun toggle(key: PoseKey, image: Bitmap) {
        when (val heart = heartOf(key)) {
            Heart.Saving -> Unit
            is Heart.Saved -> remove(key, heart.guideId)
            null -> save(key, image)
        }
    }

    /**
     * The guide the ♥ on [key] saved, while the library still has it: *Use this pose* goes on with it, so the person is
     * not cut out again and *Save guide* finds it saved.
     */
    fun savedGuide(key: PoseKey): ReferenceGuide? {
        val heart = heartOf(key) as? Heart.Saved ?: return null
        return made[key]?.takeIf { it.id == heart.guideId }
    }

    /** [key]'s heart now, as [hearts] has it a moment later: a second tap must see the first one's. */
    private fun heartOf(key: PoseKey): Heart? {
        val heart = marked.value[key]
        return if (heart is Heart.Saved && store.saved.value.none { it.id == heart.guideId }) null else heart
    }

    private fun save(key: PoseKey, image: Bitmap) {
        // A new set's candidates cannot reach the earlier ones', so their guides are let go.
        made.keys.removeAll { it.setId != key.setId }
        marked.update { hearts -> hearts.filterKeys { it.setId == key.setId } + (key to Heart.Saving) }
        val full = store.saved.value.size >= SavedGuideStore.MAX_SAVED
        viewModelScope.launch {
            val notice = try {
                val guide = maker.make(image, GuideSource.GENERATED).getOrThrow()
                store.save(guide, GuideState(guideId = guide.id)).getOrThrow()
                made[key] = guide
                marked.update { it + (key to Heart.Saved(guide.id)) }
                if (full) PoseSaveNotice.SAVED_OLDEST_REMOVED else PoseSaveNotice.SAVED
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                marked.update { it - key }
                if (error is NoPersonFoundException) PoseSaveNotice.NO_PERSON else PoseSaveNotice.FAILED
            }
            _results.send(PoseSaveResult(key.setId, notice))
        }
    }

    private fun remove(key: PoseKey, guideId: String) {
        marked.update { it - key }
        made.remove(key)
        viewModelScope.launch {
            store.delete(guideId)
            _results.send(PoseSaveResult(key.setId, PoseSaveNotice.REMOVED))
        }
    }
}
