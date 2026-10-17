// AI-generated with ChatGPT Codex and Claude Code, 2026-10-16, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.AspectRatio
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.frameAspect
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.guide.GuideGeometry
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

/** Subject view (R&S 6.6, FR-6.6, FR-7.1–7.3). */
data class SubjectUiState(
    /** For "Dongje's camera"; arrives in `hello`. */
    val photographerName: String? = null,
    /** Both data channels are up, so remote control can be used. */
    val connected: Boolean = false,
    /** The photographer's zoom range, from `camera.capabilities`; equal until it arrives. */
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    /** The zoom to show: the pinch's value during the gesture, until the photographer echoes applied zoom in `camera.state`. */
    val zoom: Float = 1f,
    /** The photographer's frame (AD-10), from `camera.state`: the video, the grid, and the guide fit to it. */
    val aspect: AspectRatio = AspectRatio.R4_3,
    /** The photographer's exposure range and EV per index, from `camera.capabilities`; 0..0 hides the control. */
    val exposureRange: IntRange = 0..0,
    val exposureStep: Float = 0f,
    /** The brightness to show: the slider's value during a drag, until the photographer echoes the applied one. */
    val exposure: Int = 0,
    /** Whether the brightness slider is open above the readout. */
    val brightnessOpen: Boolean = false,
    /** False hides the flash button (FR-7.3). */
    val hasFlash: Boolean = false,
    /** The flash to show: the tapped mode, until the photographer echoes the applied one. */
    val flash: FlashMode = FlashMode.OFF,
    val guide: ReferenceGuide? = null,
    /** The guide to draw: the mirror's state, or this phone's own gesture while it runs (Design 2.6.5). */
    val guideState: GuideState = GuideState(),
    /**
     * Guide-edit mode (AD-17): while on, drags and pinches on the live video move the photographer's guide; while
     * off, a pinch zooms the photographer's camera. Off until the subject turns it on, and off again when the guide goes.
     */
    val guideEditing: Boolean = false,
) {
    val canZoom: Boolean
        get() = connected && minZoom.isFinite() && maxZoom.isFinite() && minZoom > 0f && maxZoom > minZoom

    /** Whether the photographer's camera has a brightness range to set (FR-7.3). */
    val hasBrightness: Boolean
        get() = exposureRange.first < exposureRange.last
}

/**
 * Subject view (Design 2.2, Figure 7). [video] is where the screen attaches its renderer.
 *
 * Every control is a request to the photographer (AD-7): the screen shows the requested value at once, sends steps
 * at most every [SEND_INTERVAL_MS] and the final value right away ([StepSender]), and the photographer's echo in
 * `camera.state` or `guide.state` decides what stays once the gesture is over (Design 2.6.5).
 * Owner: Real-time (#8 live view, #10 remote zoom, #57 remote controls), with the guide from #9.
 */
class SubjectViewModel(
    private val session: SessionManager,
    private val mirror: GuideRepository,
    val video: RemoteVideo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectUiState())
    val uiState: StateFlow<SubjectUiState> = _uiState.asStateFlow()

    private val zoomSender = StepSender<Float>(viewModelScope, SEND_INTERVAL_MS) { ratio, final ->
        session.send(SessionMessage.ZoomSet(ratio, final))
        Timings.mark("zoom.sent", "$ratio${if (final) " final" else ""}")
    }
    private val exposureSender = StepSender<Int>(viewModelScope, SEND_INTERVAL_MS) { index, final ->
        session.send(SessionMessage.ExposureSet(index, final))
        Timings.mark("exposure.sent", "$index${if (final) " final" else ""}")
    }
    private val guideSender = StepSender<GuideState>(viewModelScope, SEND_INTERVAL_MS) { state, final ->
        val guideId = state.guideId ?: return@StepSender
        session.send(SessionMessage.GuideMove(guideId, state.cx, state.cy, state.height, final))
        Timings.mark("move.sent", "${state.cx} ${state.cy} ${state.height}${if (final) " final" else ""}")
    }

    init {
        // The photographer may have echoed a state before this screen existed; later echoes come through incoming.
        session.peerCamera.value?.let { camera ->
            _uiState.update { it.copy(zoom = camera.zoom, exposure = camera.exposure, flash = camera.flash) }
        }
        viewModelScope.launch {
            // Kept by the session, so a state sent before this screen existed, or before `incoming` was collected,
            // is here too. Outside a gesture it is also the value to show.
            session.peerCamera.collect { camera ->
                _uiState.update {
                    it.copy(
                        aspect = camera?.aspect ?: AspectRatio.R4_3,
                        zoom = camera?.zoom?.takeUnless { zoomSender.adjusting } ?: it.zoom,
                        exposure = camera?.exposure?.takeUnless { exposureSender.adjusting } ?: it.exposure,
                        flash = camera?.flash ?: it.flash,
                    )
                }
            }
        }
        viewModelScope.launch {
            session.state.collect { state ->
                val connected = state as? SessionState.Connected
                if (connected == null) endInteractions()
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
                val range = caps?.exposureRange ?: 0..0
                _uiState.update {
                    it.copy(
                        minZoom = caps?.minZoom ?: 1f,
                        maxZoom = caps?.maxZoom ?: 1f,
                        exposureRange = range,
                        exposureStep = caps?.exposureStep ?: 0f,
                        brightnessOpen = it.brightnessOpen && range.first < range.last,
                        hasFlash = caps?.hasFlash ?: false,
                    )
                }
                val ui = _uiState.value
                if (!ui.canZoom) zoomSender.cancel()
                if (!ui.hasBrightness) exposureSender.cancel()
            }
        }
        viewModelScope.launch {
            session.incoming.collect { message ->
                if (message !is SessionMessage.CameraStateUpdate) return@collect
                // While a pinch or the slider is moving its own steps rule the display; the echo wins once it ends.
                _uiState.update {
                    it.copy(
                        zoom = if (zoomSender.adjusting) it.zoom else message.zoom,
                        exposure = if (exposureSender.adjusting) it.exposure else message.exposure,
                        flash = message.flash,
                    )
                }
                if (message.by == Role.SUBJECT && message.final) {
                    Timings.mark("zoom.echo", "${message.zoom} exposure=${message.exposure} flash=${message.flash}")
                }
            }
        }
        // The mirror is written by GuideSyncer while the session runs; the overlay draws it (FR-6.7).
        viewModelScope.launch {
            mirror.guide.collect { guide ->
                val previous = _uiState.value.guide
                _uiState.update { it.copy(guide = guide, guideEditing = it.guideEditing && guide != null) }
                // A gesture never crosses guide identities (Design 2.6.2): a removed or replaced guide ends it.
                if (guide?.id != previous?.id) guideSender.cancel()
            }
        }
        viewModelScope.launch {
            mirror.state.collect { state ->
                _uiState.update {
                    it.copy(
                        // This phone's own gesture is drawn until it ends; the photographer's echo then settles the
                        // guide. Hiding and opacity stay the photographer's even meanwhile (FR-7.2).
                        guideState = if (guideSender.adjusting) {
                            it.guideState.copy(visible = state.visible, opacity = state.opacity)
                        } else {
                            state
                        },
                        // A guide the photographer hid cannot be edited, so the mode ends and the pinch zooms again.
                        guideEditing = it.guideEditing && state.visible,
                    )
                }
            }
        }
    }

    /** The guide-edit mode toggle; there is no mode without a visible guide. */
    fun setGuideEditing(on: Boolean) {
        _uiState.update { it.copy(guideEditing = on && it.guide != null && it.guideState.visible) }
        if (!on) guideSender.cancel()
    }

    /**
     * From GuideOverlayView.onGesture while guide-edit mode is on (FR-7.2): the guide follows the finger here,
     * within the photographer's limits for its frame, and the photographer gets `guide.move` with the result; its
     * echoed `guide.state` settles the guide once the gesture is over (Design 2.6.5).
     */
    fun onGuideGesture(dx: Float, dy: Float, scale: Float, final: Boolean) {
        val ui = _uiState.value
        val guide = ui.guide
        if (guide == null || !ui.guideEditing || !ui.connected) {
            if (final) guideSender.cancel()
            return
        }
        if (!dx.isFinite() || !dy.isFinite() || !scale.isFinite() || scale <= 0f) return
        val current = ui.guideState
        val requested = current.copy(
            guideId = guide.id,
            cx = current.cx + dx,
            cy = current.cy + dy,
            height = current.height * scale,
        )
        val moved = GuideGeometry.clamp(requested, guide.aspect, ui.aspect.frameAspect)
        _uiState.update { it.copy(guideState = moved) }
        if (final) guideSender.finish(moved) else guideSender.step(moved)
    }

    /**
     * One pinch step, or its last value when [final] (FR-7.1). The readout follows at once; steps go to the
     * photographer at most every [SEND_INTERVAL_MS] (the newest wins), the final value right away, and the echoed
     * `camera.state` decides what stays (Design 2.6.5).
     */
    fun onZoomGesture(ratio: Float, final: Boolean) {
        val ui = _uiState.value
        if (!ratio.isFinite() || !ui.canZoom) {
            // A disabled or interrupted pinch must release echo suppression even if its final request is rejected.
            if (final) zoomSender.cancel()
            return
        }
        val clamped = ratio.coerceIn(ui.minZoom, ui.maxZoom)
        _uiState.update { it.copy(zoom = clamped) }
        if (final) zoomSender.finish(clamped) else zoomSender.step(clamped)
    }

    /** From the brightness slider (FR-7.3): an exposure index, shown at once and sent like a zoom step. */
    fun onExposureGesture(index: Int, final: Boolean) {
        val ui = _uiState.value
        if (!ui.connected || !ui.hasBrightness) {
            if (final) exposureSender.cancel()
            return
        }
        val clamped = index.coerceIn(ui.exposureRange)
        _uiState.update { it.copy(exposure = clamped) }
        if (final) exposureSender.finish(clamped) else exposureSender.step(clamped)
    }

    fun setBrightnessOpen(open: Boolean) {
        _uiState.update { it.copy(brightnessOpen = open && it.hasBrightness) }
    }

    /** The flash button cycles off, auto, on, like the photographer's own (FR-7.3); the echo confirms or reverts it. */
    fun onFlashTap() {
        val ui = _uiState.value
        if (!ui.connected || !ui.hasFlash) return
        val next = when (ui.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        _uiState.update { it.copy(flash = next) }
        session.send(SessionMessage.FlashSet(next))
        Timings.mark("flash.sent", "$next")
    }

    private fun endInteractions() {
        zoomSender.cancel()
        exposureSender.cancel()
        guideSender.cancel()
    }

    private companion object {
        const val SEND_INTERVAL_MS = 50L
    }
}
