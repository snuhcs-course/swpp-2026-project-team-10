// AI-generated with Claude Code, 2026-10-30, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.guide.ReferenceGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

/** A guide's tip, with the guide it is for, so a screen never shows it with another guide (FR-4.10). */
data class GuideTip(val guideId: String, val state: TipState)

/**
 * The tip for the guide on Camera (FR-4.10; Design 2.6.3, Pose tips). [prepare] asks for the tip of a guide made from
 * a candidate while the user looks at it on Reference confirm; when that guide goes on Camera, its tip comes with it,
 * and when another guide does, or none, there is no tip, so a guide from the gallery or a saved guide never has one,
 * even a saved copy of the guide on Camera, which keeps its id. Only the newest guide's tip and the one on Camera are
 * asked for, and [discard] drops the newest when that guide is not used. A tip kept is asked again when the screens' language changes, so
 * Camera never shows it in the language it had before (FR-10.2). Kept in memory only, like the candidates.
 * Owner: Pose tips.
 */
class PoseTips(
    private val tipper: PoseTipper,
    /** The guide on Camera (`GuideRepository.guide`). */
    guideOnCamera: StateFlow<ReferenceGuide?>,
    private val scope: CoroutineScope,
    /** The screens' language ([screenLanguages]), which the tipper writes the tip in. */
    private val language: StateFlow<String> = MutableStateFlow(ENGLISH),
) {
    /** What a guide's tip was asked from, and in which language, so it can be asked again in another. */
    private class Asked(val candidate: Bitmap, val source: TipSource, val language: String)

    /** The tips kept: the one of the guide on Camera and the one being prepared, by guide id. */
    private data class Tips(
        val onCamera: String? = null,
        val pending: String? = null,
        val states: Map<String, TipState> = emptyMap(),
        val asked: Map<String, Asked> = emptyMap(),
    ) {
        fun kept(): Tips {
            val keep = { id: String -> id == onCamera || id == pending }
            return copy(states = states.filterKeys(keep), asked = asked.filterKeys(keep))
        }
    }

    private val tips = MutableStateFlow(Tips())

    /** The requests in flight by guide id; [prepare] runs on the main thread and a language change on [scope]. */
    private val asking = mutableMapOf<String, Job>()

    /**
     * The tip of the guide on Camera, or null when it has none. It names its guide: this follows [guideOnCamera] on
     * [scope], so a screen that watches the guide too can see the new guide while this still holds the old one's tip.
     */
    val onCamera: StateFlow<GuideTip?> =
        tips.map { tips -> tips.onCamera?.let { id -> tips.states[id]?.let { GuideTip(id, it) } } }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            // Each guide put on Camera, not each id: a saved guide keeps the id of the guide it was saved from.
            guideOnCamera.distinctUntilChanged { old, new -> old === new }.collect { guide ->
                val id = guide?.id
                // Only the guide the tip was prepared for brings one; the one on Camera before loses its own.
                tips.update {
                    Tips(
                        onCamera = id,
                        pending = it.pending.takeIf { pending -> pending != id },
                        states = it.states.filterKeys { key -> key == it.pending },
                        asked = it.asked.filterKeys { key -> key == it.pending },
                    )
                }
            }
        }
        scope.launch {
            language.drop(1).collect { now ->
                for ((id, asked) in tips.value.asked) {
                    if (asked.language != now) ask(id, Asked(asked.candidate, asked.source, now))
                }
            }
        }
    }

    /**
     * Asks for the tip of [guide], made from [candidate]. A tip still being asked for an older guide is dropped, but not
     * the one of the guide on Camera, which would stay *Preparing* if this guide is not used.
     */
    fun prepare(guide: ReferenceGuide, candidate: Bitmap, source: TipSource) {
        val onCamera = tips.value.onCamera
        synchronized(asking) {
            val older = asking.keys.filter { it != onCamera }
            older.forEach { id -> asking.remove(id)?.cancel() }
        }
        tips.update { it.copy(pending = guide.id).kept() }
        ask(guide.id, Asked(candidate, source, language.value))
    }

    private fun ask(guideId: String, asked: Asked) {
        tips.update {
            it.copy(states = it.states + (guideId to TipState.Preparing), asked = it.asked + (guideId to asked))
        }
        val job = scope.launch {
            val state = tipper.tip(asked.source.scene, asked.candidate, asked.source.pose).fold(
                onSuccess = { TipState.Ready(it) },
                onFailure = { TipState.Failed },
            )
            // A guide that left in the meantime, unused or replaced, keeps no tip, and one asked again keeps the newer.
            tips.update { if (it.asked[guideId] === asked) it.copy(states = it.states + (guideId to state)) else it }
        }
        synchronized(asking) { asking.put(guideId, job) }?.cancel()
    }

    /**
     * Drops the tip asked for the guide [guideId] if that guide has not gone on Camera: Reference confirm was left
     * without it, or another photo took its place. Its request is cancelled, which the server passes on to the model.
     */
    fun discard(guideId: String) {
        var dropped = false
        tips.update {
            dropped = it.pending == guideId
            if (dropped) it.copy(pending = null).kept() else it
        }
        if (dropped) synchronized(asking) { asking.remove(guideId) }?.cancel()
    }
}
