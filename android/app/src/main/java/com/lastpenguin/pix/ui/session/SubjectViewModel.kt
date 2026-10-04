package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.RemoteVideo
import com.lastpenguin.pix.session.Role
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
    /** Both data channels are up, so remote zoom can be used. */
    val connected: Boolean = false,
    /** From `camera.capabilities`. */
    val zoomStops: List<Float> = emptyList(),
    /** The zoom to show: the chip just tapped, until the photographer echoes the applied zoom in `camera.state`. */
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
                val connected = state as? SessionState.Connected
                _uiState.update {
                    it.copy(
                        connected = connected != null,
                        photographerName = connected?.peer?.displayName ?: it.photographerName,
                    )
                }
            }
        }
        viewModelScope.launch {
            session.incoming.collect { message ->
                when (message) {
                    is SessionMessage.Capabilities -> _uiState.update {
                        it.copy(zoomStops = message.capabilities.zoomStops)
                    }

                    is SessionMessage.CameraStateUpdate -> {
                        _uiState.update { it.copy(zoom = message.zoom) }
                        if (message.by == Role.SUBJECT && message.final) Timings.mark("zoom.echo", "${message.zoom}")
                    }

                    else -> Unit
                }
            }
        }
        // TODO(#9): mirror.guide and mirror.state → uiState.guide and guideState.
    }

    /** Shows the chip at once, asks the photographer, and snaps to the echoed `camera.state` (Design 2.6.5). */
    fun onZoomChip(ratio: Float) {
        if (!ratio.isFinite() || !_uiState.value.connected) return
        _uiState.update { it.copy(zoom = ratio) }
        session.send(SessionMessage.ZoomSet(ratio))
        Timings.mark("zoom.sent", "$ratio")
    }
}
