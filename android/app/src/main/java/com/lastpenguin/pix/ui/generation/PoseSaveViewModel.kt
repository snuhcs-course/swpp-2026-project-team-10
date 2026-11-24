// AI-generated with Claude Code, 2026-11-25, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.generation.PoseLibrary
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.NoPersonFoundException
import com.lastpenguin.pix.guide.ReferenceGuide
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

/** A candidate's ♥ (FR-4.14): being marked, or a favorite in the library as [guideId]. */
sealed interface Heart {
    data object Saving : Heart

    data class Saved(val guideId: String) : Heart
}

/** What a ♥ did (FR-4.14), shown as a short notice on Pick a pose. */
enum class PoseSaveNotice {
    /** The pose is a favorite now. */
    SAVED,

    /** The pose is history again, still in the library (#297). */
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
 * The ♥ on both phones' Pick a pose (FR-4.14, Design 2.6.3): marks the candidate's guide a favorite in this phone's
 * library, and a tap on a filled heart makes it history again (#297). The guide is the one [library] keeps for the
 * candidate, cut out once on this phone, as *Use this pose* uses it, so nothing is kept twice. Shared by the activity,
 * so a ♥ goes on after the screen is left, and nothing of it is sent to the other phone. A heart is filled while the
 * library has its guide as a favorite: one deleted there, or no longer a favorite, is empty again. The guide of a scene
 * photo held sideways or upside down is kept turned back into the camera's portrait frame, as *Use this pose* puts it
 * on the camera, so the library opens it there too (Design 2.6.2, #206), with the set's situation (#270).
 * Owner: the library (#171, #297).
 */
class PoseSaveViewModel(private val library: PoseLibrary, private val store: SavedGuideStore) : ViewModel() {

    private val marked = MutableStateFlow<Map<PoseKey, Heart>>(emptyMap())

    /** Each candidate's heart; a candidate without one has an empty heart. */
    val hearts: StateFlow<Map<PoseKey, Heart>> = combine(marked, store.saved) { hearts, saved ->
        val favorites = saved.filter { it.favorite }.mapTo(HashSet()) { it.id }
        hearts.filterValues { it !is Heart.Saved || it.guideId in favorites }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _results = Channel<PoseSaveResult>(Channel.BUFFERED)

    /** What each ♥ did once it finished, read once. */
    val results: Flow<PoseSaveResult> = _results.receiveAsFlow()

    /**
     * The ♥ on the candidate [key], whose image is [image], of a scene photo held at [turn] (`PhoneTurns`) for
     * [situation]: marks it a favorite, or history again once it is one; ignored while marking.
     */
    fun toggle(key: PoseKey, image: Bitmap, turn: Int = 0, situation: Situation? = null) {
        when (val heart = heartOf(key)) {
            Heart.Saving -> Unit
            is Heart.Saved -> unmark(key, heart.guideId)
            null -> mark(key, image, turn, situation)
        }
    }

    /**
     * The guide the library keeps for [key], whose image is [image], waited for while it is cut out: *Use this pose*
     * goes on with it, so the person is not cut out again and *Save guide* finds it kept. It is the guide as cut from
     * the candidate, which Reference confirm shows on it and *Use this pose* turns back as the library has it.
     */
    suspend fun keptGuide(
        key: PoseKey,
        image: Bitmap,
        turn: Int = 0,
        situation: Situation? = null,
    ): Result<ReferenceGuide> =
        library.keep(key.setId, key.poseId, image, turn, situation).await().map { it.cut }

    /** [key]'s heart now, as [hearts] has it a moment later: a second tap must see the first one's. */
    private fun heartOf(key: PoseKey): Heart? {
        val heart = marked.value[key]
        val gone = heart is Heart.Saved && store.saved.value.none { it.id == heart.guideId && it.favorite }
        return if (gone) null else heart
    }

    private fun mark(key: PoseKey, image: Bitmap, turn: Int, situation: Situation?) {
        marked.update { hearts -> hearts.filterKeys { it.setId == key.setId } + (key to Heart.Saving) }
        viewModelScope.launch {
            val notice = try {
                val kept = library.keep(key.setId, key.poseId, image, turn, situation).await().getOrThrow()
                store.setFavorite(kept.saved.id, true).getOrThrow()
                marked.update { it + (key to Heart.Saved(kept.saved.id)) }
                PoseSaveNotice.SAVED
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                marked.update { it - key }
                if (error is NoPersonFoundException) PoseSaveNotice.NO_PERSON else PoseSaveNotice.FAILED
            }
            _results.send(PoseSaveResult(key.setId, notice))
        }
    }

    private fun unmark(key: PoseKey, guideId: String) {
        marked.update { it - key }
        viewModelScope.launch {
            store.setFavorite(guideId, false)
            _results.send(PoseSaveResult(key.setId, PoseSaveNotice.REMOVED))
        }
    }
}
