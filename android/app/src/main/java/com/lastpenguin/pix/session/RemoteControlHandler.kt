// AI-generated with Claude Code, 2026-11-25, reviewed by Jaewan Park, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraStatus
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.baseExposureIndex
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What became of the situation the subject picked for AI poses, on the photographer's phone (FR-6.15, #276): its user
 * is [ASKING]ed to agree to the AI notice first, the scene photo's step [STARTED], or the start was [REFUSED].
 */
enum class PoseStartStep { ASKING, STARTED, REFUSED }

/**
 * What the Camera screen did with a press of the subject's shutter (FR-7.8): took the photo; started the
 * photographer's timer, whose end takes it (FR-1.17); stopped the timer's countdown; left the timer as it was, for a
 * press for a photo that met a countdown or a cancel that found none; or refused the press.
 */
enum class ShutterOutcome {
    TAKEN,
    TIMER_STARTED,
    TIMER_STOPPED,
    TIMER_KEPT,
    REFUSED,
    ;

    /** True when the press was the timer's, so no photo was taken at once and none was refused. */
    val timers: Boolean get() = this == TIMER_STARTED || this == TIMER_STOPPED || this == TIMER_KEPT
}

/** A remote action the photographer's phone applied, for the notice "Joonhyung set zoom to 2×" (FR-7.4). */
sealed interface RemoteAction {
    data class Zoom(val ratio: Float) : RemoteAction

    /** The subject moved or resized the guide; one per gesture (FR-7.2). */
    data object GuideMove : RemoteAction

    /** The subject set the brightness to the exposure index [index] (FR-7.3). */
    data class Brightness(val index: Int) : RemoteAction

    data class Flash(val mode: FlashMode) : RemoteAction

    /** The subject's tap set the focus at [x], [y] of the photographer's frame, 0..1 (FR-7.7). */
    data class Focus(val x: Float, val y: Float) : RemoteAction

    /** The subject's shutter took a photo (FR-7.8, #314). */
    data object Shutter : RemoteAction

    /** The subject's shutter started the photographer's timer, whose end takes the photo (FR-7.8, FR-1.17). */
    data object TimerStart : RemoteAction

    /** The subject's shutter stopped the timer's countdown (FR-7.8, FR-1.17). */
    data object TimerStop : RemoteAction

    /** The subject started AI poses, and the scene photo's step began here (FR-6.15, #276). */
    data object PoseStart : RemoteAction
}

/**
 * Photographer: applies the subject's requests and echoes what was applied (Design 2.6.5, Figure 3).
 *
 * **Zoom.** The echo follows the camera's *applied* zoom, not the request, so the subject also sees the
 * photographer's own zoom changes and both phones always end on the same value. A pinch arrives as a
 * stream of non-final requests followed by one final value: every step is applied, but only the final one is
 * reported as an action (one notice per pinch) and, when the camera does not apply it within [ECHO_TIMEOUT_MS],
 * answered with the current zoom so the subject's readout snaps back. The camera may hold a request while it
 * opens another lens or finishes saving a photo, so an answered request keeps its attribution for [PENDING_MS]:
 * when the held request is applied later, it is still the subject's and still gets its one notice.
 *
 * **One phone zooms at a time (FR-7.6, #236).** The subject's pinch takes [zoomHold] at its first step and frees it
 * at its final value, or [HOLD_TIMEOUT_MS] after its last request when the final value never comes. While the
 * photographer's pinch holds it, the subject's requests are dropped, and so is the rest of a subject's pinch up to its
 * final value, which is answered with the camera's zoom so the subject's readout comes back, or up to its cancel (a
 * `camera.zoom.set` with `cancel`), which changes nothing. Each change of the holder goes to the subject as
 * `camera.zoom.hold`.
 *
 * **Brightness and flash (It 2, FR-7.3).** `camera.exposure.set` is clamped to the exposure range and applied
 * with the same step-then-final pattern, and `camera.flash.set` is applied when the camera has a flash; a request
 * the camera cannot take (no range, no flash) is answered with the unchanged state. While an earlier request is
 * still outstanding, even a value the camera holds now is submitted again, so the final value supersedes the
 * earlier one. A final request the camera has not reported applied after [ECHO_TIMEOUT_MS] is confirmed if the
 * camera holds its value by then, and otherwise answered with the current state, so the subject's control snaps
 * back. Every `camera.state` carries the whole applied camera state (Design 2.5.1):
 * a change of exposure, flash, aspect ratio, or the camera in use (#201), from either phone, is sent as soon as the
 * camera reports it, attributed to the subject when it matches the subject's pending request.
 *
 * **Focus (It 5, FR-7.7).** `camera.focus.set` is the subject's tap, in fractions of the photographer's frame as the
 * photographer's preview shows it. It does what a tap on Camera does (FR-1.4): the camera focuses and meters there
 * when it can focus on a point, and the brightness goes back to the base, 0 EV (FR-1.11).
 * The point has no echo, since focus has no lasting state. The tap is answered with the `camera.state` that carries
 * the base, as the subject's and marked `focused`, so the subject's bar knows where a drag starts from and takes no
 * other echo for it: at once when the camera has the base already, and otherwise when the camera reports it, or
 * with where the camera is when it has not after [ECHO_TIMEOUT_MS]. It makes no brightness notice. A point outside the frame is
 * dropped, and so is a tap while the camera is not ready, as on Camera. The photographer's own tap goes the other
 * way as `camera.focus` ([focusedHere]), so the subject's phone shows the focus box too.
 *
 * **Guide moves (It 2, FR-7.2).** `guide.move` carries the center and height the subject asks for, in frame
 * coordinates; [guides] clamps it with the limits of the photographer's frame and publishes the change, which
 * `GuideSyncer` echoes as `guide.state`; [attributeGuideChanges] tells the syncer that those changes are the
 * subject's. Requests are applied in arrival order, so the later change wins when both people move the guide
 * (FR-7.6). A move without a visible guide, or for a guide that has since been replaced, is dropped: a hidden
 * guide cannot be edited on either phone, and a gesture never crosses guide identities.
 *
 * **Shutter (It 5, FR-7.8, #314).** `camera.shutter` is the subject's press. The photo is the Camera screen's to take
 * ([shutter]), as at its own shutter, with its timer when that is on, and every press is answered with
 * `camera.shutter.answer`.
 *
 * **AI poses (It 5, FR-6.15, #276).** `pose.start` is the situation the subject picked. The Camera screen begins the
 * scene photo for it ([poseStarter]), as if its own user had picked it, and the request is answered with
 * `pose.start.answer`.
 * Owner: Real-time (#10, #57).
 */
class RemoteControlHandler(
    private val session: SessionManager,
    private val camera: CameraController,
    private val guides: GuideRepository,
    private val attributeGuideChanges: (Role?) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    /** Who holds the zoom; `CameraViewModel` takes it for the photographer's own pinch. */
    val zoomHold: ZoomHold = ZoomHold(),
) {
    /**
     * Takes up a press of the subject's shutter (FR-7.8, #314), a press for a photo or one to cancel the timer's
     * countdown, and says what it did. The Camera screen sets it while it is in front, since only there is the camera
     * bound and the shutter the camera's own; null otherwise.
     */
    var shutter: ((cancel: Boolean) -> ShutterOutcome)? = null

    /**
     * Begins a pose's scene photo for the situation the subject picked (FR-6.15, #276), and tells its second argument
     * whether it began: at once, or once the photographer answered the AI notice. The Camera screen sets it while it
     * is in front, as it does [shutter]; null otherwise.
     */
    var poseStarter: ((Situation, (PoseStartStep) -> Unit) -> Unit)? = null

    private val _actions = MutableSharedFlow<RemoteAction>(extraBufferCapacity = 16)

    /** Applied remote actions, for the notice on the photographer's screen. */
    val actions: SharedFlow<RemoteAction> = _actions.asSharedFlow()

    private var scope: CoroutineScope? = null
    private var zoomJob: Job? = null
    private var settingsJob: Job? = null
    private var holderJob: Job? = null
    private var holdTimeoutJob: Job? = null
    private var subjectHeldBack = false
    private var finalJob: Job? = null
    private var timeoutJob: Job? = null
    private var exposureTimeoutJob: Job? = null
    private var flashTimeoutJob: Job? = null
    private var baseTimeoutJob: Job? = null
    private var pending: PendingValue<Float>? = null

    // False while the subject's pinch has sent steps but not its last value, so the camera still holds its lens.
    private var zoomSettled = true
    private var pendingExposure: PendingValue<Int>? = null
    private var pendingFlash: PendingValue<FlashMode>? = null

    // True while the photographer's timer counts down (countdownHere): the subject's flash waits, as theirs does.
    private var photographerCounting = false

    // The base brightness a tap to focus asked for, until the camera reports it: its echo is the subject's, without
    // the notice of a brightness the subject chose.
    private var pendingBase: PendingValue<Int>? = null

    /** Echoes every applied zoom while [scope] is active, starting with the current one. */
    fun start(scope: CoroutineScope) {
        stop()
        this.scope = scope
        zoomJob = scope.launch { camera.zoom.collect { zoom -> onApplied(zoom) } }
        // The first zoom echo above already carries the current values.
        settingsJob = scope.launch {
            combine(camera.state.map { Triple(it.exposure, it.flash, it.aspect) }, camera.facing, ::Pair)
                .distinctUntilChanged()
                .drop(1)
                .collect { (settings, _) -> onSettingsApplied(settings.first, settings.second) }
        }
        // The current holder first, so a subject that connects or reconnects mid-pinch knows it.
        holderJob = scope.launch { zoomHold.holder.collect { session.send(SessionMessage.ZoomHoldUpdate(it)) } }
    }

    fun stop() {
        // A pinch whose last value never came (the link dropped, the session ended) would leave the camera holding a
        // lens, so every later photo could be an ultrawide crop: end it where the camera is (Design 2.6.5).
        if (!zoomSettled) {
            zoomSettled = true
            camera.setZoom(camera.zoom.value, final = true)
        }
        zoomHold.release(Role.SUBJECT)
        subjectHeldBack = false
        zoomJob?.cancel()
        settingsJob?.cancel()
        settingsJob = null
        holderJob?.cancel()
        holderJob = null
        holdTimeoutJob?.cancel()
        holdTimeoutJob = null
        finalJob?.cancel()
        timeoutJob?.cancel()
        exposureTimeoutJob?.cancel()
        flashTimeoutJob?.cancel()
        baseTimeoutJob?.cancel()
        baseTimeoutJob = null
        zoomJob = null
        finalJob = null
        timeoutJob = null
        exposureTimeoutJob = null
        flashTimeoutJob = null
        pending = null
        pendingExposure = null
        pendingFlash = null
        pendingBase = null
        scope = null
    }

    /**
     * The photographer's own tap focused at [x], [y] of the frame (FR-7.7): the subject's phone shows the focus box
     * there too. Only while a session runs; the brightness that the tap put back goes as `camera.state`.
     */
    fun focusedHere(x: Float, y: Float) {
        if (scope == null) return
        session.send(SessionMessage.Focused(x, y))
        Timings.mark("focus.told", "$x $y")
    }

    /**
     * The photographer did [action] on their own camera (FR-7.4, #292): the subject's phone says it in a line. Only
     * while a session runs.
     */
    fun didHere(action: PhotographerAction) {
        if (scope == null) return
        session.send(action.toMessage())
        Timings.mark("did.told", action.toMessage().action)
    }

    /**
     * The photographer's timer has [left] seconds before its photo, or 0 as it ends (FR-1.17): the subject's phone
     * shows the countdown too. Only while a session runs.
     */
    fun countdownHere(left: Int) {
        photographerCounting = left > 0
        if (scope == null) return
        session.send(SessionMessage.Countdown(left))
        Timings.mark("timer.told", "$left")
    }

    /** Handles the subject's requests; other messages are ignored. */
    fun handle(message: SessionMessage) {
        when (message) {
            is SessionMessage.ZoomSet -> if (message.cancel) handleZoomCancel() else handleZoom(message)
            is SessionMessage.ExposureSet -> handleExposure(message)
            is SessionMessage.FlashSet -> handleFlash(message)
            is SessionMessage.FocusSet -> handleFocus(message)
            is SessionMessage.GuideMove -> handleGuideMove(message)
            is SessionMessage.ShutterPress -> handleShutter(message)
            is SessionMessage.PoseStart -> handlePoseStart(message)
            else -> Unit
        }
    }

    /**
     * `pose.start`: the Camera screen begins the scene photo for the subject's situation, if it is in front and can
     * (FR-6.15, #276). Every request is answered, by its number: a refusal at once, and a start when it began. That
     * waits for the photographer when the AI notice is up, which the subject's phone is told at once, so that it does
     * not give the request up. An answer that comes once this session is over tells no one; one that comes after a
     * drop of the same session is told, or the subject's phone would wait for it without end.
     */
    private fun handlePoseStart(request: SessionMessage.PoseStart) {
        val situation = Situation.named(request.situation)
        val starter = poseStarter
        // The session this request came in, which may be over when the photographer answers the notice. By its id,
        // not by this handler's run: a drop stops the handler and the link's return starts it anew, in one session.
        val asked = sessionId()
        var decided = false
        val answer = { step: PoseStartStep ->
            // One last answer, and none once this session is over. While the link reconnects, the session keeps it.
            if (!decided && asked != null && sessionId() == asked) {
                decided = step != PoseStartStep.ASKING
                val started = step == PoseStartStep.STARTED
                Timings.mark("poses.remote", step.name.lowercase())
                session.send(
                    SessionMessage.PoseStartAnswer(started, request.request, asking = step == PoseStartStep.ASKING),
                )
                if (started) _actions.tryEmit(RemoteAction.PoseStart)
            }
        }
        if (situation == null || starter == null) answer(PoseStartStep.REFUSED) else starter(situation, answer)
    }

    /** The id of the session that runs or reconnects; null outside one. */
    private fun sessionId(): String? = when (val state = session.state.value) {
        is SessionState.Connected -> state.sessionId
        is SessionState.Reconnecting -> state.sessionId
        else -> null
    }

    /**
     * `camera.shutter`: the Camera screen takes the photo, if it is in front and can, or its timer takes the press
     * up (FR-1.17), and the press is answered either way, so the subject's shutter is free again. Nothing is kept for
     * later: a press is one moment, which is why it comes on the realtime channel, at once or not at all.
     */
    private fun handleShutter(request: SessionMessage.ShutterPress) {
        val outcome = shutter?.invoke(request.cancel) ?: ShutterOutcome.REFUSED
        Timings.mark("shutter.remote", outcome.name.lowercase())
        // Named by its press, so an answer that comes late is not taken for the next press's.
        session.send(
            SessionMessage.ShutterAnswer(outcome == ShutterOutcome.TAKEN, request.press, timer = outcome.timers),
        )
        when (outcome) {
            ShutterOutcome.TAKEN -> _actions.tryEmit(RemoteAction.Shutter)
            ShutterOutcome.TIMER_STARTED -> _actions.tryEmit(RemoteAction.TimerStart)
            ShutterOutcome.TIMER_STOPPED -> _actions.tryEmit(RemoteAction.TimerStop)
            ShutterOutcome.TIMER_KEPT, ShutterOutcome.REFUSED -> Unit
        }
    }

    /** `camera.zoom.set`: clamp, camera.setZoom, then reply with `camera.state`. */
    private fun handleZoom(request: SessionMessage.ZoomSet) {
        val caps = camera.capabilities.value
        val ratio = if (caps == null) request.ratio else request.ratio.coerceIn(caps.minZoom, caps.maxZoom)
        if (!ratio.isFinite()) return
        Timings.mark("zoom.received", "$ratio${if (request.final) " final" else ""}")
        if (!holdForSubject(request.final)) {
            // The photographer's pinch holds the zoom: nothing changes, and a final request learns where the camera is.
            Timings.mark("zoom.held", "$ratio")
            if (request.final) send(camera.zoom.value, Role.PHOTOGRAPHER, final = true)
            return
        }
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
        // The subject's pinch holds the lens like a local one until its last value (Design 2.6.5). That value, and a
        // quick step's one value, glide where the camera can show it, as the photographer's own do (#310); the camera's
        // states on the way are the photographer's echoes, and the one that arrives is the subject's.
        if (request.final) camera.glideZoom(ratio) else camera.setZoom(ratio, final = false)
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
     * Whether the subject's request may zoom now (FR-7.6): not while the photographer's pinch holds [zoomHold]. A step
     * takes it, or keeps it for another [HOLD_TIMEOUT_MS]; a final value frees it. A pinch with a step held back stays
     * held back up to its final value or its cancel, even once the zoom is free, since an older subject keeps sending
     * its steps.
     */
    private fun holdForSubject(final: Boolean): Boolean {
        holdTimeoutJob?.cancel()
        holdTimeoutJob = null
        if (subjectHeldBack) {
            if (final) subjectHeldBack = false
            return false
        }
        if (final) {
            if (!zoomHold.mayChange(Role.SUBJECT)) return false
            zoomHold.release(Role.SUBJECT)
            return true
        }
        if (!zoomHold.take(Role.SUBJECT)) {
            subjectHeldBack = true
            return false
        }
        holdTimeoutJob = scope?.launch {
            delay(HOLD_TIMEOUT_MS)
            holdTimeoutJob = null
            // The pinch's last value never came (a lost link, a stuck gesture).
            freeSubjectHold()
        }
        return true
    }

    /**
     * A `camera.zoom.set` with `cancel`: the subject's pinch stopped, held back by the photographer's (FR-7.6). It
     * changes no zoom: that pinch's requests are no longer dropped, and a hold the subject still has is freed.
     */
    private fun handleZoomCancel() {
        subjectHeldBack = false
        if (zoomHold.holder.value == Role.SUBJECT) freeSubjectHold()
    }

    /** The subject's pinch is over without its last value: the zoom is free again, and the lens settles where it is. */
    private fun freeSubjectHold() {
        holdTimeoutJob?.cancel()
        holdTimeoutJob = null
        zoomHold.release(Role.SUBJECT)
        if (!zoomSettled) {
            zoomSettled = true
            camera.setZoom(camera.zoom.value, final = true)
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

    /**
     * `camera.flash.set`: applied when the camera has a flash; otherwise ignored with an unchanged echo, and so while
     * the photographer's timer counts down, as their own Flash waits then (FR-1.17).
     */
    private fun handleFlash(request: SessionMessage.FlashSet) {
        Timings.mark("flash.received", "${request.mode}")
        if (camera.capabilities.value?.hasFlash != true || photographerCounting) {
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

    /**
     * `camera.focus.set`: camera.setFocusPoint where the camera takes a point, and the base brightness again. Only a
     * point the camera took is an action: without point focus the tap is only where the brightness bar comes up.
     */
    private fun handleFocus(request: SessionMessage.FocusSet) {
        val (x, y) = request
        Timings.mark("focus.received", "$x $y")
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return
        if (camera.status.value != CameraStatus.READY) return
        val caps = camera.capabilities.value ?: return
        // The subject's phone asks only of a camera that focuses on a point or has a brightness range.
        if (!caps.supportsFocusPoint && caps.baseExposureIndex() == null) return
        if (caps.supportsFocusPoint) camera.setFocusPoint(x, y)
        val base = caps.baseExposureIndex()
        if (base == null || (pendingExposure == null && camera.state.value.exposure == base)) {
            // No brightness to set, or already there with nothing outstanding, so the camera will not emit again:
            // answered now.
            send(camera.zoom.value, Role.SUBJECT, final = true, focused = true)
        } else {
            // A drag's request still outstanding is left alone: its answer or its late application still tells the
            // subject where the camera is.
            val queued = PendingValue(base, clock(), final = true)
            pendingBase = queued
            camera.setExposure(base)
            baseTimeoutJob?.cancel()
            baseTimeoutJob = scope?.launch {
                // A camera that does not take the base reports nothing: the tap is then answered with where the camera
                // is, and the base is no longer waited for, so a later change to that value is not taken for it.
                delay(ECHO_TIMEOUT_MS)
                camera.status.first { it != CameraStatus.STARTING }
                if (pendingBase !== queued) return@launch
                pendingBase = null
                send(camera.zoom.value, Role.SUBJECT, final = true, focused = true)
            }
        }
        if (caps.supportsFocusPoint) confirm(RemoteAction.Focus(x, y), "focus.applied", "$x $y")
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
     * Exposure, flash, ratio, or the camera in use changed. A change that matches the subject's pending request is the
     * subject's and gets its notice (brightness only for the bar's final value, and none for the base that a tap to
     * focus set); a step of the subject's bar is echoed as a step, everything else as a final state.
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
        val baseRequest = pendingBase?.takeIf { it.value == exposure && now - it.at <= PENDING_MS }
        if (baseRequest != null) {
            pendingBase = null
            baseTimeoutJob?.cancel()
        }
        val bySubject = exposureRequest != null || flashRequest != null || baseRequest != null
        send(
            camera.zoom.value,
            if (bySubject) Role.SUBJECT else Role.PHOTOGRAPHER,
            exposureRequest?.final ?: true,
            focused = baseRequest != null,
        )
    }

    private fun confirm(action: RemoteAction, mark: String, detail: String) {
        _actions.tryEmit(action)
        Timings.mark(mark, detail)
    }

    /** [focused]: this state answers the subject's tap to focus (`camera.focus.set`). */
    private fun send(zoom: Float, by: Role, final: Boolean, focused: Boolean = false) {
        val applied = camera.state.value
        session.send(
            SessionMessage.CameraStateUpdate(
                zoom = zoom,
                by = by,
                final = final,
                exposure = applied.exposure,
                flash = applied.flash,
                aspect = applied.aspect,
                facing = camera.facing.value,
                focused = focused,
            ),
        )
    }

    /** A zoom, brightness, or flash request waiting for the camera to report it applied. */
    private class PendingValue<T>(val value: T, val at: Long, val final: Boolean) {
        /** Answered by the timeout with the current state; a late application is still attributed. */
        var answered = false
    }

    companion object {
        /**
         * How long a final request, or the base of a tap to focus, waits for the camera to report it before it is
         * answered with the current state. The subject's phone waits longer than this for a tap's answer
         * (`SubjectViewModel`), so keep the two together.
         */
        const val ECHO_TIMEOUT_MS = 500L

        private const val ZOOM_MATCH = 0.01f

        // How close the shown zoom must be for a pinch's last value to count as already applied: well inside the lens
        // hold band (CameraZoomPlan.HOLD_BAND), where a held lens shows its edge rather than the pinch's value.
        private const val SHOWN_MATCH = 0.001f

        /** How long a request stays the subject's: long enough for a lens switch or a photo save to finish. */
        private const val PENDING_MS = 5_000L
        private const val FINAL_DELAY_MS = 100L

        /**
         * How long the subject's pinch keeps the zoom after its last request. A still finger sends nothing, so it is
         * well above the 50 ms between steps; a pinch ended normally frees it at its final value.
         */
        private const val HOLD_TIMEOUT_MS = 2_000L
    }
}
