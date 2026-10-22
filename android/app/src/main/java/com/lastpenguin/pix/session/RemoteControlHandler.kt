// AI-generated with Claude Code, 2026-10-22, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A remote action the photographer's phone applied, for the notice "Junhyeong set zoom to 2×" (FR-7.4). */
sealed interface RemoteAction {
    data class Zoom(val ratio: Float) : RemoteAction

    /** The subject moved or resized the guide; one per gesture (FR-7.2). */
    data object GuideMove : RemoteAction

    /** The subject set the brightness to the exposure index [index] (FR-7.3). */
    data class Brightness(val index: Int) : RemoteAction

    data class Flash(val mode: FlashMode) : RemoteAction
}

/**
 * Photographer: applies the subject's requests and echoes what was applied (Design 2.6.5, Figure 3).
 *
 * **Zoom.** The echo follows the camera's *applied* zoom, not the request, so the subject also sees the
 * photographer's own zoom changes and both phones always end on the same value (FR-7.6). A pinch arrives as a
 * stream of non-final requests followed by one final value: every step is applied, but only the final one is
 * reported as an action (one notice per pinch) and, when the camera does not apply it within [ECHO_TIMEOUT_MS],
 * answered with the current zoom so the subject's readout snaps back. The camera may hold a request while it
 * opens another lens or finishes saving a photo, so an answered request keeps its attribution for [PENDING_MS]:
 * when the held request is applied later, it is still the subject's and still gets its one notice.
 *
 * **Brightness and flash (It 2, FR-7.3).** `camera.exposure.set` is clamped to the exposure range and applied
 * with the same step-then-final pattern, and `camera.flash.set` is applied when the camera has a flash; a request
 * the camera cannot take (no range, no flash) is answered with the unchanged state. While an earlier request is
 * still outstanding, even a value the camera holds now is submitted again, so the final value supersedes the
 * earlier one. A final request the camera has not reported applied after [ECHO_TIMEOUT_MS] is confirmed if the
 * camera holds its value by then, and otherwise answered with the current state, so the subject's control snaps
 * back. Every `camera.state` carries the whole applied camera state (Design 2.5.1):
 * a change of exposure, flash, or aspect ratio, from either phone, is sent as soon as the camera reports it,
 * attributed to the subject when it matches the subject's pending request.
 *
 * **Guide moves (It 2, FR-7.2).** `guide.move` carries the center and height the subject asks for, in frame
 * coordinates; [guides] clamps it with the limits of the photographer's frame and publishes the change, which
 * `GuideSyncer` echoes as `guide.state`; [attributeGuideChanges] tells the syncer that those changes are the
 * subject's. Requests are applied in arrival order, so the later change wins when both people move the guide
 * (FR-7.6). A move without a visible guide, or for a guide that has since been replaced, is dropped: a hidden
 * guide cannot be edited on either phone, and a gesture never crosses guide identities.
 * Owner: Real-time (#10, #57).
 */
class RemoteControlHandler(
    private val session: SessionManager,
    private val camera: CameraController,
    private val guides: GuideRepository,
    private val attributeGuideChanges: (Role?) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _actions = MutableSharedFlow<RemoteAction>(extraBufferCapacity = 16)

    /** Applied remote actions, for the notice on the photographer's screen. */
    val actions: SharedFlow<RemoteAction> = _actions.asSharedFlow()

    private var scope: CoroutineScope? = null
    private var zoomJob: Job? = null
    private var settingsJob: Job? = null
    private var finalJob: Job? = null
    private var timeoutJob: Job? = null
    private var exposureTimeoutJob: Job? = null
    private var flashTimeoutJob: Job? = null
    private var pending: PendingValue<Float>? = null

    // False while the subject's pinch has sent steps but not its last value, so the camera still holds its lens.
    private var zoomSettled = true
    private var pendingExposure: PendingValue<Int>? = null
    private var pendingFlash: PendingValue<FlashMode>? = null

    /** Echoes every applied zoom while [scope] is active, starting with the current one. */
    fun start(scope: CoroutineScope) {
        stop()
        this.scope = scope
        zoomJob = scope.launch { camera.zoom.collect { zoom -> onApplied(zoom) } }
        // The first zoom echo above already carries the current values.
        settingsJob = scope.launch {
            camera.state.map {
                Triple(it.exposure, it.flash, it.aspect)
            }.distinctUntilChanged().drop(1).collect { (exposure, flash, _) ->
                onSettingsApplied(exposure, flash)
            }
        }
    }

    fun stop() {
        // A pinch whose last value never came (the link dropped, the session ended) would leave the camera holding a
        // lens, so every later photo could be an ultrawide crop: end it where the camera is (Design 2.6.5).
        if (!zoomSettled) {
            zoomSettled = true
            camera.setZoom(camera.zoom.value, final = true)
        }
        zoomJob?.cancel()
        settingsJob?.cancel()
        settingsJob = null
        finalJob?.cancel()
        timeoutJob?.cancel()
        exposureTimeoutJob?.cancel()
        flashTimeoutJob?.cancel()
        zoomJob = null
        finalJob = null
        timeoutJob = null
        exposureTimeoutJob = null
        flashTimeoutJob = null
        pending = null
        pendingExposure = null
        pendingFlash = null
        scope = null
    }

    /** Handles the subject's requests; other messages are ignored. */
    fun handle(message: SessionMessage) {
        when (message) {
            is SessionMessage.ZoomSet -> handleZoom(message)
            is SessionMessage.ExposureSet -> handleExposure(message)
            is SessionMessage.FlashSet -> handleFlash(message)
            is SessionMessage.GuideMove -> handleGuideMove(message)
            else -> Unit
        }
    }

    /** `camera.zoom.set`: clamp, camera.setZoom, then reply with `camera.state`. */
    private fun handleZoom(request: SessionMessage.ZoomSet) {
        val caps = camera.capabilities.value
        val ratio = if (caps == null) request.ratio else request.ratio.coerceIn(caps.minZoom, caps.maxZoom)
        if (!ratio.isFinite()) return
        Timings.mark("zoom.received", "$ratio${if (request.final) " final" else ""}")
        zoomSettled = request.final
        timeoutJob?.cancel()
        timeoutJob = null
        if (request.final && abs(camera.zoom.value - ratio) < SHOWN_MATCH) {
            // The pinch's last step already landed, so the camera will not emit again: confirm it now. The camera
            // still learns that the pinch ended, so a lens it held can settle (Design 2.6.5).
            pending = null
            finalJob?.cancel()
            camera.setZoom(ratio, final = true)
            confirm(RemoteAction.Zoom(ratio), "zoom.applied", "$ratio")
            send(ratio, Role.SUBJECT, final = true)
            return
        }
        val queued = PendingValue(ratio, clock(), request.final)
        pending = queued
        // A final echo still due for the previous step would send a stale zoom after the subject's pinch ended.
        // The newer request brings its own echoes: when applied, or from the timeout answer.
        finalJob?.cancel()
        // The subject's pinch holds the lens like a local one until its last value (Design 2.6.5).
        camera.setZoom(ratio, request.final)
        if (!request.final) return
        timeoutJob = scope?.launch {
            answerIfStalled(
                queued,
                isPending = { pending === queued },
                holds = { abs(camera.zoom.value - ratio) < ZOOM_MATCH },
            ) {
                pending = null
                confirm(RemoteAction.Zoom(ratio), "zoom.applied", "$ratio")
                send(ratio, Role.SUBJECT, final = true)
            }
        }
    }

    /**
     * `camera.exposure.set`: clamp to the exposure range, camera.setExposure, and the echo follows from the applied
     * state. Without an exposure range the request is answered with the unchanged state, so the subject's slider
     * snaps back.
     */
    private fun handleExposure(request: SessionMessage.ExposureSet) {
        Timings.mark("exposure.received", "${request.index}${if (request.final) " final" else ""}")
        val range = camera.capabilities.value?.exposureRange ?: 0..0
        if (range.first >= range.last) {
            send(camera.zoom.value, Role.PHOTOGRAPHER, final = true)
            return
        }
        val index = request.index.coerceIn(range)
        exposureTimeoutJob?.cancel()
        exposureTimeoutJob = null
        if (pendingExposure == null && camera.state.value.exposure == index) {
            // Already there and nothing outstanding, so the camera will not emit again: a final request is
            // confirmed and echoed now.
            if (request.final) {
                confirm(RemoteAction.Brightness(index), "exposure.applied", "$index")
                send(camera.zoom.value, Role.SUBJECT, final = true)
            }
            return
        }
        // An outstanding request may still move the camera, so the value is submitted even if held now: the
        // newest request supersedes the earlier one.
        val queued = PendingValue(index, clock(), request.final)
        pendingExposure = queued
        camera.setExposure(index)
        if (!request.final) return
        exposureTimeoutJob = scope?.launch {
            answerIfStalled(
                queued,
                isPending = { pendingExposure === queued },
                holds = { camera.state.value.exposure == index },
            ) {
                pendingExposure = null
                confirm(RemoteAction.Brightness(index), "exposure.applied", "$index")
                send(camera.zoom.value, Role.SUBJECT, final = true)
            }
        }
    }

    /** `camera.flash.set`: applied when the camera has a flash; otherwise ignored with an unchanged echo. */
    private fun handleFlash(request: SessionMessage.FlashSet) {
        Timings.mark("flash.received", "${request.mode}")
        if (camera.capabilities.value?.hasFlash != true) {
            send(camera.zoom.value, Role.PHOTOGRAPHER, final = true)
            return
        }
        flashTimeoutJob?.cancel()
        flashTimeoutJob = null
        if (pendingFlash == null && camera.state.value.flash == request.mode) {
            confirm(RemoteAction.Flash(request.mode), "flash.applied", "${request.mode}")
            send(camera.zoom.value, Role.SUBJECT, final = true)
            return
        }
        // Several taps may be outstanding: the newest mode is submitted even if held now, so that it wins.
        val queued = PendingValue(request.mode, clock(), final = true)
        pendingFlash = queued
        camera.setFlash(request.mode)
        flashTimeoutJob = scope?.launch {
            answerIfStalled(
                queued,
                isPending = { pendingFlash === queued },
                holds = { camera.state.value.flash == request.mode },
            ) {
                pendingFlash = null
                confirm(RemoteAction.Flash(request.mode), "flash.applied", "${request.mode}")
                send(camera.zoom.value, Role.SUBJECT, final = true)
            }
        }
    }

    /** `guide.move`: the photographer's repository clamps and applies it; GuideSyncer echoes the result. */
    private fun handleGuideMove(request: SessionMessage.GuideMove) {
        Timings.mark(
            "move.received",
            "${request.cx} ${request.cy} ${request.height}${if (request.final) " final" else ""}",
        )
        if (!request.cx.isFinite() || !request.cy.isFinite() || !request.height.isFinite() || request.height <= 0f) {
            return
        }
        // A hidden or absent guide cannot be edited on either phone (Design 2.6.2); the subject's toggle is off then.
        // A move for another guide is a gesture that began before the guide was replaced: it never crosses over.
        val guide = guides.guide.value ?: return
        if (!guides.state.value.visible || guide.id != request.guideId) return
        attributeGuideChanges(Role.SUBJECT)
        guides.update(request.final) { it.copy(cx = request.cx, cy = request.cy, height = request.height) }
        if (request.final) {
            confirm(RemoteAction.GuideMove, "move.applied", "${request.cx} ${request.cy} ${request.height}")
        }
    }

    /**
     * After [ECHO_TIMEOUT_MS] without the camera reporting the request applied: if the camera [holds] its value by
     * now (a request that superseded an earlier one, or that was already true, makes no state change to report),
     * [confirmed] marks it the subject's; otherwise the subject is told where the camera is, so its control snaps
     * back. The request is not forgotten then: the camera may still be holding it (see the class comment), and
     * [onApplied] or [onSettingsApplied] attributes it later, while [isPending] still says it is the newest one.
     */
    private suspend fun answerIfStalled(
        request: PendingValue<*>,
        isPending: () -> Boolean,
        holds: () -> Boolean,
        confirmed: () -> Unit,
    ) {
        delay(ECHO_TIMEOUT_MS)
        // While the camera opens another lens the request is certainly held: wait rather than answer with a stale state.
        camera.status.first { it != CameraStatus.STARTING }
        if (!isPending() || request.answered) return
        if (holds()) {
            confirmed()
            return
        }
        request.answered = true
        send(camera.zoom.value, Role.PHOTOGRAPHER, final = true)
    }

    private fun onApplied(zoom: Float) {
        val request = pending
        val bySubject = request != null && abs(zoom - request.value) < ZOOM_MATCH && clock() - request.at <= PENDING_MS
        if (bySubject) {
            pending = null
            timeoutJob?.cancel()
            if (request.final) confirm(RemoteAction.Zoom(zoom), "zoom.applied", "$zoom")
        }
        val by = if (bySubject) Role.SUBJECT else Role.PHOTOGRAPHER
        send(zoom, by, final = false)
        finalJob?.cancel()
        finalJob = scope?.launch {
            delay(FINAL_DELAY_MS)
            send(zoom, by, final = true)
        }
    }

    /**
     * Exposure, flash, or ratio changed on the camera. A change that matches the subject's pending request is the
     * subject's and gets its notice (brightness only for the slider's final value); a step of the subject's slider
     * is echoed as a step, everything else as a final state.
     */
    private fun onSettingsApplied(exposure: Int, flash: FlashMode) {
        val now = clock()
        val exposureRequest = pendingExposure?.takeIf { it.value == exposure && now - it.at <= PENDING_MS }
        val flashRequest = pendingFlash?.takeIf { it.value == flash && now - it.at <= PENDING_MS }
        if (exposureRequest != null) {
            pendingExposure = null
            exposureTimeoutJob?.cancel()
            if (exposureRequest.final) confirm(RemoteAction.Brightness(exposure), "exposure.applied", "$exposure")
        }
        if (flashRequest != null) {
            pendingFlash = null
            flashTimeoutJob?.cancel()
            confirm(RemoteAction.Flash(flash), "flash.applied", "$flash")
        }
        val bySubject = exposureRequest != null || flashRequest != null
        send(camera.zoom.value, if (bySubject) Role.SUBJECT else Role.PHOTOGRAPHER, exposureRequest?.final ?: true)
    }

    private fun confirm(action: RemoteAction, mark: String, detail: String) {
        _actions.tryEmit(action)
        Timings.mark(mark, detail)
    }

    private fun send(zoom: Float, by: Role, final: Boolean) {
        val applied = camera.state.value
        session.send(
            SessionMessage.CameraStateUpdate(
                zoom = zoom,
                by = by,
                final = final,
                exposure = applied.exposure,
                flash = applied.flash,
                aspect = applied.aspect,
            ),
        )
    }

    /** A zoom, brightness, or flash request waiting for the camera to report it applied. */
    private class PendingValue<T>(val value: T, val at: Long, val final: Boolean) {
        /** Answered by the timeout with the current state; a late application is still attributed. */
        var answered = false
    }

    private companion object {
        const val ZOOM_MATCH = 0.01f

        // How close the shown zoom must be for a pinch's last value to count as already applied: well inside the lens
        // hold band (CameraZoomPlan.HOLD_BAND), where a held lens shows its edge rather than the pinch's value.
        const val SHOWN_MATCH = 0.001f

        /** How long a request stays the subject's: long enough for a lens switch or a photo save to finish. */
        const val PENDING_MS = 5_000L
        const val ECHO_TIMEOUT_MS = 500L
        const val FINAL_DELAY_MS = 100L
    }
}
