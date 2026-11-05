// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.MirrorPoseSet
import com.lastpenguin.pix.session.PoseSetEnd
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.StartedScene
import com.lastpenguin.pix.session.protocol.PoseSetStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The subject's *Generating poses* (R&S 6.6, Iter3-A13): the photographer's set as it is created. */
data class SubjectGeneratingUiState(
    /** The set this screen opened with; null before it is there. */
    val setId: String? = null,
    /** The slots in the photographer's order, each with what arrived for it: a copy, a failure, or nothing yet. */
    val slots: List<Pair<String, CandidateEvent?>> = emptyList(),
    /** Whether the photographer's phone said its slots; until then their number is not known. */
    val planned: Boolean = false,
    /** The scene photo the poses are made from, when its copy arrived. */
    val scene: Bitmap? = null,
    /** The set asks for suggested poses, which are chosen first. */
    val suggested: Boolean = false,
    /** The set moved on to *Pick a pose* with copies here: the screen goes on to the subject's *Pick a pose*. */
    val picking: Boolean = false,
    /** The set this screen opened with ended, was replaced, or is not there: the screen goes back to Subject view. */
    val gone: Boolean = false,
    val photographerName: String? = null,
) {
    val readyCount: Int get() = slots.count { it.second is CandidateEvent.Ready }

    /** Every slot failed: the photographer's phone shows *Couldn't create poses*. */
    val allFailed: Boolean get() = planned && slots.isNotEmpty() && slots.all { it.second is CandidateEvent.Failed }
}

/**
 * The subject's *Generating poses* (Design 2.6.5, Iter3-A13): the photographer's GENERATING set from [poses], slot for
 * slot as on their phone, with the scene the set started from ([descriptions]). It only shows: cancelling the set
 * stays the photographer's, and the screen follows the set to *Pick a pose* or back to Subject view.
 * Owner: Shared steps (Iter3-A13).
 */
class SubjectGeneratingViewModel(
    private val session: SessionManager,
    private val poses: PoseSyncer,
    private val descriptions: DescriptionSyncer?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectGeneratingUiState())
    val uiState: StateFlow<SubjectGeneratingUiState> = _uiState.asStateFlow()

    /** Each set that ended while this screen was in front, for its notice. */
    val ended: Flow<PoseSetEnd> = poses.ended

    init {
        viewModelScope.launch {
            combine(poses.mirror, descriptions?.startedScene ?: flowOf(null), session.state) { set, scene, state ->
                Triple(set, scene, state)
            }.collect { (set, scene, state) -> _uiState.update { ui -> next(ui, set, scene, state) } }
        }
    }

    private fun next(
        ui: SubjectGeneratingUiState,
        set: MirrorPoseSet?,
        scene: StartedScene?,
        state: SessionState,
    ): SubjectGeneratingUiState {
        val name = (state as? SessionState.Connected)?.peer?.displayName ?: ui.photographerName
        return when {
            ui.gone || ui.picking -> ui

            // The set this screen showed is over, another one took its place, or there is none, as after the app was
            // restarted on this screen.
            set == null || (ui.setId != null && set.setId != ui.setId) -> ui.copy(gone = true)

            // Pick a pose has what it shows only with copies; without any, the photographer picks on its own phone.
            set.step == PoseSetStep.PICKING ->
                if (set.ready.isEmpty()) ui.copy(gone = true) else ui.copy(setId = set.setId, picking = true)

            else -> {
                // A photographer of before Iter3-A13 says no slots: its copies fill in as they arrive.
                val ids = set.slots.ifEmpty { set.candidates.keys.toList() }
                ui.copy(
                    setId = set.setId,
                    slots = ids.map { id -> id to eventOf(set, id) },
                    planned = set.slots.isNotEmpty(),
                    scene = scene?.scene,
                    suggested = scene?.suggested == true,
                    photographerName = name,
                )
            }
        }
    }

    /** What arrived for the slot [id]; no reason comes with a failure, which every slot shows alike. */
    private fun eventOf(set: MirrorPoseSet, id: String): CandidateEvent? = set.candidates[id]
        ?.let { CandidateEvent.Ready(id, it) }
        ?: CandidateEvent.Failed(id, GenerationError.SERVER).takeIf { id in set.failed }
}
