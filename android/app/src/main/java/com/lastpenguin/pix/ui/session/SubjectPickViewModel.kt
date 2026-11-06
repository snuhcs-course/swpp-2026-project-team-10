// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.session.PoseSetEnd
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.PoseSetStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The subject's *Pick a pose* (R&S 6.6): the photographer's candidates of one set. */
data class SubjectPickUiState(
    /** The set this screen opened with; null before it is there. */
    val setId: String? = null,
    /** The candidates whose copy arrived, in the photographer's order. */
    val candidates: List<Pair<String, Bitmap>> = emptyList(),
    /** The candidate on screen, which *Use this pose* sends; on this phone only. */
    val selectedPoseId: String? = null,
    /** *Use this pose* was tapped, and the photographer's phone has not answered yet. */
    val waiting: Boolean = false,
    /** The set this screen opened with ended or was replaced: the screen goes back to Subject view. */
    val gone: Boolean = false,
    /** The link is reconnecting (Design 2.6.7): the candidates stay, and *Use this pose* waits. */
    val reconnecting: Boolean = false,
    val photographerName: String? = null,
)

/**
 * The subject's *Pick a pose* (Design 2.6.5, *Pick a pose on both phones*): shows the photographer's PICKING set from
 * [poses], keeps the swipe on this phone, and sends *Use this pose* as a pick, which the photographer's phone decides.
 * Owner: Shared steps (Iter3-P16).
 */
class SubjectPickViewModel(
    private val session: SessionManager,
    private val poses: PoseSyncer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectPickUiState())
    val uiState: StateFlow<SubjectPickUiState> = _uiState.asStateFlow()

    /** Each set that ended while this screen was in front, for its notice (Design 2.6.5). */
    val ended: Flow<PoseSetEnd> = poses.ended

    /** Each pick that the photographer's phone did not answer in time, for "Couldn't send your pick". */
    val pickFailed: Flow<String> = poses.pickFailed

    init {
        viewModelScope.launch {
            combine(poses.mirror, poses.pending, session.state) { set, pending, state -> Triple(set, pending, state) }
                .collect { (set, pending, state) ->
                    _uiState.update { ui ->
                        val name = (state as? SessionState.Connected)?.peer?.displayName ?: ui.photographerName
                        val picking = set?.takeIf { it.step == PoseSetStep.PICKING }
                        when {
                            ui.gone -> ui

                            // The set this screen showed is over, another one took its place, or there is none,
                            // as after the app was restarted on this screen.
                            picking == null || (ui.setId != null && picking.setId != ui.setId) ->
                                ui.copy(gone = true, waiting = false)

                            else -> {
                                val ready = picking.ready
                                val selected = ui.selectedPoseId?.takeIf { id -> ready.any { it.first == id } }
                                ui.copy(
                                    setId = picking.setId,
                                    candidates = ready,
                                    selectedPoseId = selected ?: ready.firstOrNull()?.first,
                                    waiting = pending?.setId == picking.setId,
                                    reconnecting = state is SessionState.Reconnecting,
                                    photographerName = name,
                                )
                            }
                        }
                    }
                }
        }
    }

    /** The page on screen is the selected candidate, as on the photographer's phone. */
    fun select(poseId: String) {
        _uiState.update { ui ->
            if (!ui.waiting && ui.candidates.any { it.first == poseId }) ui.copy(selectedPoseId = poseId) else ui
        }
    }

    /** *Use this pose*: a request to the photographer's phone, which answers with the set's step (AD-7). */
    fun usePose() {
        val ui = _uiState.value
        val poseId = ui.selectedPoseId ?: return
        if (ui.waiting || ui.gone || ui.reconnecting) return
        poses.pick(poseId)
    }
}
