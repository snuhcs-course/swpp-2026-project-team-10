// AI-generated with Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a tip is asked from besides the candidate: the scene photo the candidate was made from, and its pose. */
data class TipSource(val scene: Bitmap, val pose: String)

/** Where a guide's tip is (FR-4.10). */
sealed interface TipState {
    /** Asked for; Camera says so until it arrives. */
    data object Preparing : TipState

    data class Ready(val tip: PoseTip) : TipState

    /** No tip can be had for this guide. */
    data object Failed : TipState
}

/**
 * The tip for the guide on Camera (FR-4.10; Design 2.6.3, Pose tips). [prepare] asks for the tip of a guide made from
 * a candidate while the user looks at it on Reference confirm; when that guide goes on Camera, its tip comes with it,
 * and when another guide does, or none, there is no tip, so a guide from the gallery or a saved guide never has one.
 * Only the newest guide's tip is asked for. Kept in memory only, like the candidates.
 * Owner: Pose tips.
 */
class PoseTips(
    private val tipper: PoseTipper,
    /** The guide on Camera (`GuideRepository.guide`). */
    guideOnCamera: StateFlow<ReferenceGuide?>,
    private val scope: CoroutineScope,
) {
    /** The tips kept: the one of the guide on Camera and the one being prepared, by guide id. */
    private data class Tips(
        val onCamera: String? = null,
        val pending: String? = null,
        val states: Map<String, TipState> = emptyMap(),
    ) {
        fun kept(): Tips = copy(states = states.filterKeys { it == onCamera || it == pending })
    }

    private val tips = MutableStateFlow(Tips())
    private var asking: Job? = null

    /** The tip of the guide on Camera, or null when it has none. */
    val onCamera: StateFlow<TipState?> =
        tips.map { it.states[it.onCamera] }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            guideOnCamera.map { it?.id }.distinctUntilChanged().collect { id ->
                tips.update { it.copy(onCamera = id, pending = it.pending.takeIf { pending -> pending != id }).kept() }
            }
        }
    }

    /** Asks for the tip of [guide], made from [candidate]; a tip still being asked for an older guide is dropped. */
    fun prepare(guide: ReferenceGuide, candidate: Bitmap, source: TipSource) {
        asking?.cancel()
        tips.update { it.copy(pending = guide.id, states = it.states + (guide.id to TipState.Preparing)).kept() }
        asking = scope.launch {
            val state = tipper.tip(source.scene, candidate, source.pose).fold(
                onSuccess = { TipState.Ready(it) },
                onFailure = { TipState.Failed },
            )
            // A guide that left in the meantime, unused or replaced, keeps no tip.
            tips.update { if (guide.id in it.states) it.copy(states = it.states + (guide.id to state)) else it }
        }
    }
}
