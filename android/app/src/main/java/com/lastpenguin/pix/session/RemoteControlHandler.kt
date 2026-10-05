package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A remote action the photographer's phone applied, for "Junhyeong set zoom to 2×" (FR-7.4). */
sealed interface RemoteAction {
    data class Zoom(val ratio: Float) : RemoteAction
}

/**
 * Photographer: applies the subject's remote zoom and echoes the applied zoom (Design 2.6.5, Figure 3).
 *
 * The echo follows the camera's *applied* zoom, not the request, so the subject also sees the photographer's own
 * zoom changes and both phones always end on the same value (FR-7.6). A pinch arrives as a stream of non-final
 * requests followed by one final value: every step is applied, but only the final one is reported as an action
 * (one notice per pinch) and, when the camera does not apply it within [ECHO_TIMEOUT_MS], answered with the
 * current zoom so the subject's readout snaps back. The camera may hold a request while it opens another lens or
 * finishes saving a photo, so an answered request keeps its attribution for [PENDING_MS]: when the held request
 * is applied later, it is still the subject's and still gets its one notice.
 * Owner: Real-time (#10).
 */
class RemoteControlHandler(
    private val session: SessionManager,
    private val camera: CameraController,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _actions = MutableSharedFlow<RemoteAction>(extraBufferCapacity = 16)

    /** Applied remote actions, for the notice on the photographer's screen. */
    val actions: SharedFlow<RemoteAction> = _actions.asSharedFlow()

    private var scope: CoroutineScope? = null
    private var zoomJob: Job? = null
    private var finalJob: Job? = null
    private var timeoutJob: Job? = null
    private var pending: PendingZoom? = null

    /** Echoes every applied zoom while [scope] is active, starting with the current one. */
    fun start(scope: CoroutineScope) {
        stop()
        this.scope = scope
        zoomJob = scope.launch { camera.zoom.collect { zoom -> onApplied(zoom) } }
    }

    fun stop() {
        zoomJob?.cancel()
        finalJob?.cancel()
        timeoutJob?.cancel()
        zoomJob = null
        finalJob = null
        timeoutJob = null
        pending = null
        scope = null
    }

    /** Handles `camera.zoom.set`: clamp, camera.setZoom, then reply with `camera.state`. Other messages are ignored. */
    fun handle(message: SessionMessage) {
        val request = message as? SessionMessage.ZoomSet ?: return
        val caps = camera.capabilities.value
        val ratio = if (caps == null) request.ratio else request.ratio.coerceIn(caps.minZoom, caps.maxZoom)
        if (!ratio.isFinite()) return
        Timings.mark("zoom.received", "$ratio${if (request.final) " final" else ""}")
        timeoutJob?.cancel()
        timeoutJob = null
        if (request.final && abs(camera.zoom.value - ratio) < ZOOM_MATCH) {
            // The pinch's last step already landed, so the camera will not emit again: confirm it now.
            pending = null
            finalJob?.cancel()
            confirm(ratio)
            send(ratio, Role.SUBJECT, final = true)
            return
        }
        val queued = PendingZoom(ratio, clock(), request.final)
        pending = queued
        // A final echo still due for the previous step would send a stale zoom after the subject's pinch ended.
        // The newer request brings its own echoes: when applied, or from the timeout answer.
        finalJob?.cancel()
        camera.setZoom(ratio)
        if (!request.final) return
        timeoutJob = scope?.launch { answerIfStalled(queued) }
    }

    /**
     * After [ECHO_TIMEOUT_MS] with nothing applied, tells the subject where the zoom is. The request is not
     * forgotten: the camera may still be holding it (see the class comment), and [onApplied] attributes it then.
     */
    private suspend fun answerIfStalled(request: PendingZoom) {
        delay(ECHO_TIMEOUT_MS)
        // While the camera opens another lens the request is certainly held: wait rather than answer with a stale zoom.
        camera.status.first { it != CameraStatus.STARTING }
        if (pending !== request || request.answered) return
        request.answered = true
        send(camera.zoom.value, Role.PHOTOGRAPHER, final = true)
    }

    private fun onApplied(zoom: Float) {
        val request = pending
        val bySubject = request != null && abs(zoom - request.ratio) < ZOOM_MATCH && clock() - request.at <= PENDING_MS
        if (bySubject) {
            pending = null
            timeoutJob?.cancel()
            if (request.final) confirm(zoom)
        }
        val by = if (bySubject) Role.SUBJECT else Role.PHOTOGRAPHER
        send(zoom, by, final = false)
        finalJob?.cancel()
        finalJob = scope?.launch {
            delay(FINAL_DELAY_MS)
            send(zoom, by, final = true)
        }
    }

    private fun confirm(zoom: Float) {
        _actions.tryEmit(RemoteAction.Zoom(zoom))
        Timings.mark("zoom.applied", "$zoom")
    }

    private fun send(zoom: Float, by: Role, final: Boolean) {
        session.send(SessionMessage.CameraStateUpdate(zoom = zoom, by = by, final = final))
    }

    private class PendingZoom(val ratio: Float, val at: Long, val final: Boolean) {
        var answered = false
    }

    private companion object {
        const val ZOOM_MATCH = 0.01f

        /** How long a request stays the subject's: long enough for a lens switch or a photo save to finish. */
        const val PENDING_MS = 5_000L
        const val ECHO_TIMEOUT_MS = 500L
        const val FINAL_DELAY_MS = 100L
    }
}
