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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Subject view (R&S 6.6, FR-6.6, FR-7.1). */
data class SubjectUiState(
    /** For "Dongje's camera"; arrives in `hello`. */
    val photographerName: String? = null,
    /** Both data channels are up, so remote zoom can be used. */
    val connected: Boolean = false,
    /** The photographer's zoom range, from `camera.capabilities`; equal until it arrives. */
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    /** The zoom to show: the pinch in progress, until the photographer echoes the applied zoom in `camera.state`. */
    val zoom: Float = 1f,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
) {
    val canZoom: Boolean get() = connected && maxZoom > minZoom
}

/**
 * Subject view (Design 2.2, Figure 7). [video] is where the screen attaches its renderer.
 * Owner: Real-time (#8 live view, #10 remote zoom), with the guide from #9.
 */
class SubjectViewModel(
    private val session: SessionManager,
    private val mirror: GuideRepository,
    val video: RemoteVideo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    private var pinching = false
    private var queued: Float? = null
    private var sendJob: Job? = null

    init {
        // The photographer may have echoed a zoom before this screen existed; later echoes come through incoming.
        session.peerZoom.value?.let { zoom -> _uiState.update { it.copy(zoom = zoom) } }
        viewModelScope.launch {
            session.state.collect { state ->
                val connected = state as? SessionState.Connected
                if (connected == null) endPinch()
                _uiState.update {
                    it.copy(
                        connected = connected != null,
                        photographerName = connected?.peer?.displayName ?: it.photographerName,
                    )
                }
            }
        }
        viewModelScope.launch {
            // Kept by the session, so it is there even if the message came before this screen existed.
            session.peerCapabilities.filterNotNull().collect { caps ->
                _uiState.update { it.copy(minZoom = caps.minZoom, maxZoom = caps.maxZoom) }
            }
        }
        viewModelScope.launch {
            session.incoming.collect { message ->
                when (message) {
                    is SessionMessage.CameraStateUpdate -> {
                        // While a pinch is in progress its own steps rule the readout; the echo wins once it ends.
                        if (!pinching) _uiState.update { it.copy(zoom = message.zoom) }
                        if (message.by == Role.SUBJECT && message.final) Timings.mark("zoom.echo", "${message.zoom}")
                    }

                    else -> Unit
                }
            }
        }
        // TODO(#9): mirror.guide and mirror.state → uiState.guide and guideState.
    }

    /**
     * One step of a pinch on the live view, or its last value when [final]. The readout follows at once; steps go
     * to the photographer at most every [SEND_INTERVAL_MS] (the newest wins), the final value right away, and the
     * echoed `camera.state` decides what stays (Design 2.6.5).
     */
    fun onZoomGesture(ratio: Float, final: Boolean) {
        val ui = _uiState.value
        if (!ratio.isFinite() || !ui.canZoom) return
        val clamped = ratio.coerceIn(ui.minZoom, ui.maxZoom)
        _uiState.update { it.copy(zoom = clamped) }
        if (final) {
            endPinch()
            send(clamped, final = true)
            return
        }
        pinching = true
        if (sendJob?.isActive == true) {
            queued = clamped
            return
        }
        send(clamped, final = false)
        sendJob = viewModelScope.launch {
            while (true) {
                delay(SEND_INTERVAL_MS)
                val next = queued ?: break
                queued = null
                send(next, final = false)
            }
        }
    }

    private fun endPinch() {
        pinching = false
        queued = null
        sendJob?.cancel()
        sendJob = null
    }

    private fun send(ratio: Float, final: Boolean) {
        session.send(SessionMessage.ZoomSet(ratio, final))
        Timings.mark("zoom.sent", "$ratio${if (final) " final" else ""}")
    }

    private companion object {
        const val SEND_INTERVAL_MS = 50L
    }
}
