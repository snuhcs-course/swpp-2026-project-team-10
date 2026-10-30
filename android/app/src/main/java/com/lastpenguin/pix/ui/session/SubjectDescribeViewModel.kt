// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.ui.session

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.session.DescriptionEnd
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The subject's *Generate poses* (R&S 6.6): the photographer's scene photo and the description both phones write. */
data class SubjectDescribeUiState(
    /** The description this screen opened with; null before it is there. */
    val descriptionId: String? = null,
    val scene: Bitmap? = null,
    /** The description as the photographer's phone has it. */
    val text: String = "",
    /** Whose field holds the description; null while nobody types. */
    val writer: Role? = null,
    val rejected: Boolean = false,
    /** *Generate 4 poses* or *Skip* was tapped, and the photographer's phone has not answered yet. */
    val waiting: Boolean = false,
    /** The description this screen opened with ended or was replaced: the screen goes back to Subject view. */
    val gone: Boolean = false,
    /** The link is reconnecting (Design 2.6.7): the field and the buttons wait. */
    val reconnecting: Boolean = false,
    val photographerName: String? = null,
)

/**
 * The subject's *Generate poses* (Design 2.6.5, *Generate poses on both phones*): shows the photographer's description
 * from [descriptions], sends this phone's typing while its field holds the description, and sends *Generate 4 poses* or
 * *Skip*, which the photographer's phone decides.
 * Owner: Shared steps (Iter3-P16).
 */
class SubjectDescribeViewModel(
    private val session: SessionManager,
    private val descriptions: DescriptionSyncer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectDescribeUiState())
    val uiState: StateFlow<SubjectDescribeUiState> = _uiState.asStateFlow()

    /** Each description that ended while this screen was in front, for its notice (Design 2.6.5). */
    val ended: Flow<DescriptionEnd> = descriptions.ended

    /** Each *Generate 4 poses* or *Skip* that the photographer's phone did not answer in time. */
    val goFailed: Flow<String> = descriptions.goFailed

    /** Whether this phone's field is focused, so it holds the description unless the photographer's does. */
    private var typing = false

    init {
        viewModelScope.launch {
            combine(descriptions.mirror, descriptions.going, session.state) { description, going, state ->
                Triple(description, going, state)
            }.collect { (description, going, state) ->
                _uiState.update { ui ->
                    val name = (state as? SessionState.Connected)?.peer?.displayName ?: ui.photographerName
                    when {
                        ui.gone -> ui

                        // The description this screen showed is over, another took its place, or there is none, as
                        // after the app was restarted on this screen.
                        description == null ||
                            (ui.descriptionId != null && description.descriptionId != ui.descriptionId) ->
                            ui.copy(gone = true, waiting = false)

                        else -> ui.copy(
                            descriptionId = description.descriptionId,
                            scene = description.scene,
                            text = description.text,
                            writer = description.writer,
                            rejected = description.rejected,
                            waiting = going == description.descriptionId,
                            reconnecting = state is SessionState.Reconnecting,
                            photographerName = name,
                        )
                    }
                }
            }
        }
    }

    /** The field took focus, which holds the description while the photographer's does not, or lost it, with [text]. */
    fun onFocus(focused: Boolean, text: String) {
        if (focused == typing) return
        val ui = _uiState.value
        if (focused && (ui.writer == Role.PHOTOGRAPHER || ui.reconnecting)) return
        typing = focused
        descriptions.edit(holding = focused, text = text)
    }

    /** This phone's [text], while its field holds the description; a chip's text otherwise, which is sent at once. */
    fun onTextChanged(text: String) {
        val ui = _uiState.value
        if (ui.writer == Role.PHOTOGRAPHER || ui.reconnecting) return
        if (typing) {
            descriptions.edit(holding = true, text = text)
        } else {
            descriptions.edit(holding = true, text = text)
            descriptions.edit(holding = false, text = text)
        }
    }

    /** *Generate 4 poses* with [text], or *Skip* when [suggest]: a request to the photographer's phone (AD-7). */
    fun go(text: String, suggest: Boolean) {
        val ui = _uiState.value
        if (ui.waiting || ui.gone || ui.reconnecting || ui.writer == Role.PHOTOGRAPHER) return
        descriptions.go(text, suggest)
    }
}
