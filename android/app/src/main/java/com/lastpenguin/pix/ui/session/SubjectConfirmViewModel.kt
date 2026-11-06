// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.ConfirmEnd
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The subject's *Your reference* (R&S 6.6): the outline on the photographer's Reference confirm. */
data class SubjectConfirmUiState(
    /** The confirm this screen opened with; null before it is there. */
    val confirmId: String? = null,
    /** The photographer's outline once it is here; null while their phone finds the person. */
    val guide: ReferenceGuide? = null,
    /** *Use this guide* was tapped, and the photographer's phone has not answered yet. */
    val waiting: Boolean = false,
    /** The confirm this screen opened with ended or was replaced: the screen goes back to Subject view. */
    val gone: Boolean = false,
    /** The link is reconnecting (Design 2.6.7): the outline stays, and *Use this guide* waits. */
    val reconnecting: Boolean = false,
    val photographerName: String? = null,
)

/**
 * The subject's *Your reference* (Design 2.6.5, *Reference confirm on both phones*): shows the photographer's open
 * Reference confirm from [confirms] and sends *Use this guide* as a use, which the photographer's phone decides.
 * Owner: Shared steps (Iter3-P16).
 */
class SubjectConfirmViewModel(
    private val session: SessionManager,
    private val confirms: ConfirmSyncer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectConfirmUiState())
    val uiState: StateFlow<SubjectConfirmUiState> = _uiState.asStateFlow()

    /** Each confirm that ended while this screen was in front, for its notice (Design 2.6.5). */
    val ended: Flow<ConfirmEnd> = confirms.ended

    /** Each use that the photographer's phone did not answer in time, for "Couldn't send your choice". */
    val useFailed: Flow<String> = confirms.useFailed

    init {
        viewModelScope.launch {
            combine(confirms.mirror, confirms.pending, session.state) { confirm, pending, state ->
                Triple(confirm, pending, state)
            }.collect { (confirm, pending, state) ->
                _uiState.update { ui ->
                    val name = (state as? SessionState.Connected)?.peer?.displayName ?: ui.photographerName
                    when {
                        ui.gone -> ui

                        // The confirm this screen showed is over, another one took its place, or there is none, as
                        // after the app was restarted on this screen.
                        confirm == null || (ui.confirmId != null && confirm.confirmId != ui.confirmId) ->
                            ui.copy(gone = true, waiting = false)

                        else -> ui.copy(
                            confirmId = confirm.confirmId,
                            guide = confirm.ready,
                            waiting = pending?.confirmId == confirm.confirmId,
                            reconnecting = state is SessionState.Reconnecting,
                            photographerName = name,
                        )
                    }
                }
            }
        }
    }

    /** *Use this guide*: a request to the photographer's phone, which answers with the confirm's step (AD-7). */
    fun useGuide() {
        val ui = _uiState.value
        val guide = ui.guide ?: return
        if (ui.waiting || ui.gone || ui.reconnecting) return
        confirms.use(guide.id)
    }
}
