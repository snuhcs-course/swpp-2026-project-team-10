package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.RemoteVideo
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Subject view (R&S 6.6, FR-6.6, FR-7.1). */
data class SubjectUiState(
    /** For "Dongje's camera"; arrives in `hello`. */
    val photographerName: String? = null,
    /** From `camera.capabilities`. */
    val zoomStops: List<Float> = emptyList(),
    /** From `camera.state`. */
    val zoom: Float = 1f,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
)

/**
 * Subject view (Design 2.2, Figure 7). [video] is where the screen attaches its renderer.
 * Owner: Real-time (#8 live view, #10 zoom chips), with the guide from #9.
 */
class SubjectViewModel(
    private val session: SessionManager,
    private val mirror: GuideRepository,
    val video: RemoteVideo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is SessionState.Connected) {
                    _uiState.update {
                        it.copy(photographerName = state.peer.displayName)
                    }
                }
            }
        }
        viewModelScope.launch {
            session.incoming.collect { message ->
                when (message) {
                    is SessionMessage.Capabilities -> _uiState.update {
                        it.copy(zoomStops = message.capabilities.zoomStops)
                    }

                    is SessionMessage.CameraStateUpdate -> _uiState.update { it.copy(zoom = message.zoom) }

                    else -> Unit
                }
            }
        }
        // TODO(#9): mirror.guide and mirror.state → uiState.guide and guideState.
    }

    fun onZoomChip(ratio: Float) {
        // TODO(#10): show it at once, send camera.zoom.set, then snap to the echoed camera.state (Design 2.6.5).
    }
}
