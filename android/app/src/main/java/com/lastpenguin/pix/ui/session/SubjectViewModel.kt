// AI-generated with ChatGPT Codex and Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.AspectRatio
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
    /** The zoom to show: the pinch's value during the gesture, until the photographer echoes applied zoom in `camera.state`. */
    val zoom: Float = 1f,
    /** The photographer's frame (AD-10), from `camera.state`: the video, the grid, and the guide fit to it. */
    val aspect: AspectRatio = AspectRatio.R4_3,
    val guide: ReferenceGuide? = null,
    val guideState: GuideState = GuideState(),
    /**
     * Guide-edit mode (AD-17): while on, drags and pinches on the live video go to the guide; while off, a pinch
     * zooms the photographer's camera. Off until the subject turns it on, and off again when the guide goes.
     */
    val guideEditing: Boolean = false,
) {
    val canZoom: Boolean
        get() = connected && minZoom.isFinite() && maxZoom.isFinite() && minZoom > 0f && maxZoom > minZoom
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

    private var adjustingZoom = false
    private var queued: Float? = null
    private var sendJob: Job? = null

    init {
        // The photographer may have echoed a zoom before this screen existed; later echoes come through incoming.
        session.peerCamera.value?.let { camera -> _uiState.update { it.copy(zoom = camera.zoom) } }
        viewModelScope.launch {
            // Kept by the session, so a state sent before this screen existed, or before `incoming` was collected,
            // is here too. Outside a pinch it is also the zoom to show.
            session.peerCamera.collect { camera ->
                _uiState.update {
                    it.copy(
                        aspect = camera?.aspect ?: AspectRatio.R4_3,
                        zoom = camera?.zoom?.takeUnless { adjustingZoom } ?: it.zoom,
                    )
                }
            }
        }
        viewModelScope.launch {
            session.state.collect { state ->
                val connected = state as? SessionState.Connected
                if (connected == null) endZoomInteraction()
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
            session.peerCapabilities.collect { caps ->
                _uiState.update { it.copy(minZoom = caps?.minZoom ?: 1f, maxZoom = caps?.maxZoom ?: 1f) }
                if (!_uiState.value.canZoom) endZoomInteraction()
            }
        }
        viewModelScope.launch {
            session.incoming.collect { message ->
                when (message) {
                    is SessionMessage.CameraStateUpdate -> {
                        // While the slider is moving its own steps rule the readout; the echo wins once it ends.
                        if (!adjustingZoom) _uiState.update { it.copy(zoom = message.zoom) }
                        if (message.by == Role.SUBJECT && message.final) Timings.mark("zoom.echo", "${message.zoom}")
                    }

                    else -> Unit
                }
            }
        }
        // The mirror is written by GuideSyncer while the session runs; the overlay draws it (FR-6.7).
        viewModelScope.launch {
            mirror.guide.collect { guide ->
                _uiState.update { it.copy(guide = guide, guideEditing = it.guideEditing && guide != null) }
            }
        }
        viewModelScope.launch {
            // A guide the photographer hid cannot be edited, so the mode ends and the pinch zooms again.
            mirror.state.collect { state ->
                _uiState.update { it.copy(guideState = state, guideEditing = it.guideEditing && state.visible) }
            }
        }
    }

    /** The guide-edit mode toggle; there is no mode without a visible guide. */
    fun setGuideEditing(on: Boolean) {
        _uiState.update { it.copy(guideEditing = on && it.guide != null && it.guideState.visible) }
    }

    /**
     * From GuideOverlayView.onGesture while guide-edit mode is on. Owning the gesture here is what keeps it away
     * from camera zoom; moving the photographer's guide with it is Iteration 2's `guide.move` (FR-7.2).
     */
    @Suppress("UNUSED_PARAMETER")
    fun onGuideGesture(dx: Float, dy: Float, scale: Float, final: Boolean) {
        // TODO(#57): send guide.move steps at most every 50 ms and the final value reliably; follow optimistically.
    }

    /**
     * One pinch step, or its last value when [final]. The readout follows at once; steps go
     * to the photographer at most every [SEND_INTERVAL_MS] (the newest wins), the final value right away, and the
     * echoed `camera.state` decides what stays (Design 2.6.5).
     */
    fun onZoomGesture(ratio: Float, final: Boolean) {
        // A disabled or interrupted pinch must release echo suppression even if its final request is rejected.
        if (final) endZoomInteraction()
        val ui = _uiState.value
        if (!ratio.isFinite() || !ui.canZoom) return
        val clamped = ratio.coerceIn(ui.minZoom, ui.maxZoom)
        _uiState.update { it.copy(zoom = clamped) }
        if (final) {
            send(clamped, final = true)
            return
        }
        adjustingZoom = true
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

    private fun endZoomInteraction() {
        adjustingZoom = false
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
