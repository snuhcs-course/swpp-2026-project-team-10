// AI-generated with Claude Code, 2026-10-31, reviewed by Joonhyung Han, Jaewan Park, and Dongje Park
package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.CameraFacing
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.GenerationEvent
import com.lastpenguin.pix.generation.PlannedPose
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseRequest
import com.lastpenguin.pix.generation.RemotePoseGenerator
import com.lastpenguin.pix.generation.TipSource
import com.lastpenguin.pix.session.DescriptionRequest
import com.lastpenguin.pix.session.DescriptionShare
import com.lastpenguin.pix.session.PoseShare
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SubjectPick
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The scene photo on Camera, Generate poses, Generating poses, Pick a pose, and Couldn't create poses (R&S 6.3). */
data class GenerationUiState(
    /** The photo the user took and confirmed; kept for *Try again* and *Try other poses*. */
    val scene: Bitmap? = null,
    /** The scene photo is a selfie from the front camera: the server makes selfie poses of it (FR-1.14). */
    val selfie: Boolean = false,
    /** What the set was asked for; *Try again* asks for it again. */
    val request: PoseRequest? = null,
    /** The description field's text on Generate poses, kept for *Try other poses* and when the service rejects it. */
    val description: String = "",
    /** The service rejected every pose of the description: Couldn't create poses and Generate poses say so (US-5). */
    val descriptionRejected: Boolean = false,
    /**
     * In a session, whose field holds the description on Generate poses (Design 2.6.5): this phone's while it types,
     * the subject's while theirs does, then nobody; the other phone's field waits meanwhile.
     */
    val descriptionWriter: Role? = null,
    /** The poses of the set, once known; one slot each. */
    val poses: List<PlannedPose> = emptyList(),
    /** Results so far by pose id; the four slots fill as they arrive. */
    val candidates: Map<String, CandidateEvent> = emptyMap(),
    val selectedPoseId: String? = null,
    val phase: Phase = Phase.IDLE,
    /** The shutter gave no photo; told on Camera until the next attempt. */
    val shotFailed: Boolean = false,
    /**
     * The phone's light is turned on for the scene photo, a frame of the preview that a photo's flash would not reach
     * (FR-4.1). It is on only while the scene is framed, and stays chosen for *Shoot again*.
     */
    val light: Boolean = false,
    /**
     * Whose *Use this pose* took a candidate of this set (Design 2.6.5): this phone's, or in a session the subject's,
     * which Pick a pose then goes on with as with its own. The first one decides; nothing is selected after it.
     */
    val chosenBy: Role? = null,
) {
    /**
     * FRAMING and REVIEWING are the scene photo step on the Camera screen: the live camera, whose shutter takes the
     * photo, then the photo with *Use this photo* and *Shoot again*. DESCRIBING is Generate poses. Nothing is sent
     * before GENERATING.
     */
    enum class Phase { IDLE, FRAMING, REVIEWING, DESCRIBING, GENERATING, PICKING, FAILED }

    val readyCount: Int get() = candidates.values.count { it is CandidateEvent.Ready }

    /** The image *Use this pose* sends to Reference confirm. */
    val selectedImage: Bitmap?
        get() = (candidates[selectedPoseId] as? CandidateEvent.Ready)?.image

    /**
     * What the selected candidate's tip is asked from: the scene and the pose it was made with (FR-4.10). None for a
     * selfie: the tip tells a photographer standing in front of the person how to hold the phone.
     */
    val tipSource: TipSource?
        get() {
            if (selfie) return null
            val pose = poses.firstOrNull { it.id == selectedPoseId } ?: return null
            return scene?.let { TipSource(it, pose.text) }
        }
}

/** What *Use this pose* hands on to Reference confirm: the candidate, and what its tip is asked from (FR-4.10). */
data class PoseUse(val image: Bitmap, val tipSource: TipSource?)

/**
 * Pose generation (Design 2.2, Figure 8). Shared by the Camera screen, for the scene photo, and the four
 * generation screens (activity scope). In a session, Generate poses goes to the subject's phone through
 * [descriptions], where either phone writes the description, one at a time, and starts the set; each set goes to the
 * subject's *Pick a pose* through [share], and the subject's first pick of it counts as a *Use this pose* (Design
 * 2.6.5).
 * Owner: Server/AI/Sync (#7); Generate poses and the set on both phones Iter3-P16.
 */
class GenerationViewModel(
    private val generator: PoseGenerator,
    private val camera: CameraController,
    private val consent: GenerationConsent,
    private val limitMs: Long = RemotePoseGenerator.TIMEOUT_MS,
    private val share: PoseShare = PoseShare.None,
    private val descriptions: DescriptionShare = DescriptionShare.None,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private var generating: Job? = null
    private var shooting: Job? = null

    private var consentGiven = false

    /** The set being generated or picked from, as [share] knows it; null between sets. */
    private var setId: String? = null

    /** Generate poses as [descriptions] knows it, while the phase is DESCRIBING; null otherwise. */
    private var descriptionId: String? = null

    init {
        // Only ever sets it: an answer read before the user agreed must not take that agreement back.
        viewModelScope.launch { if (consent.isGiven()) consentGiven = true }
        viewModelScope.launch { share.picks.collect(::onSubjectPick) }
        viewModelScope.launch { descriptions.requests.collect(::onSubjectRequest) }
        // The camera keeps its light across rebinds, so it is turned off as soon as the scene is no longer framed.
        viewModelScope.launch {
            uiState.map { it.phase == GenerationUiState.Phase.FRAMING && it.light }
                .distinctUntilChanged()
                .collect(camera::setTorch)
        }
    }

    override fun onCleared() {
        camera.setTorch(false)
    }

    /**
     * True until the user has agreed that the scene photo goes to an external AI service (FR-4.2). Read on the tap,
     * so it also holds while the stored answer is still loading: without consent nothing is sent.
     */
    val needsConsent: Boolean get() = !consentGiven

    /** The user agreed to the notice; it is not shown again. */
    fun onConsentGiven() {
        consentGiven = true
        viewModelScope.launch { consent.give() }
    }

    /** *Generate poses here*: the Camera screen now takes the scene photo (FR-4.1). Nothing is sent yet. */
    fun beginScene() {
        cancel()
        _uiState.value = GenerationUiState(phase = GenerationUiState.Phase.FRAMING)
    }

    /**
     * The shutter during FRAMING: the camera's current frame becomes the photo to confirm. With the light chosen but not
     * on yet, the frame waits until the camera has it on, then [LIGHT_SETTLE_MS] for a frame lit by it and for the
     * exposure to follow, so the scene is not taken dark; after [LIGHT_WAIT_MS] without it, the frame is taken as it is.
     * A lens without a flash, such as the front camera after a switch, never has the light, so nothing waits for it.
     */
    fun takeScene() {
        if (_uiState.value.phase != GenerationUiState.Phase.FRAMING || shooting?.isActive == true) return
        shooting = viewModelScope.launch {
            val canLight = camera.capabilities.value?.hasFlash == true
            if (_uiState.value.light && canLight && !camera.state.value.torch) {
                val lit = withTimeoutOrNull(LIGHT_WAIT_MS) { camera.state.first { it.torch } }
                if (lit != null) delay(LIGHT_SETTLE_MS)
            }
            // The camera the frame comes from, read with it: a selfie gets selfie poses (FR-1.14).
            val selfie = camera.facing.value == CameraFacing.FRONT
            val frame = camera.grabFrame()
            frame.exceptionOrNull()?.let { Log.w(TAG, "No scene photo: $it") }
            _uiState.update { state ->
                when {
                    // Cancelled while the frame was being converted.
                    state.phase != GenerationUiState.Phase.FRAMING -> state

                    frame.isSuccess -> state.copy(
                        scene = frame.getOrNull(),
                        selfie = selfie,
                        phase = GenerationUiState.Phase.REVIEWING,
                        shotFailed = false,
                    )

                    else -> state.copy(shotFailed = true)
                }
            }
        }
    }

    /** *Shoot again*: the photo is dropped and the live camera is shown again, with the light as it was. */
    fun retakeScene() {
        _uiState.update {
            val reviewing = it.phase == GenerationUiState.Phase.REVIEWING
            if (reviewing) GenerationUiState(phase = GenerationUiState.Phase.FRAMING, light = it.light) else it
        }
    }

    /** The light button while the scene is framed (FR-4.1). */
    fun toggleLight() {
        _uiState.update { if (it.phase == GenerationUiState.Phase.FRAMING) it.copy(light = !it.light) else it }
    }

    /** *Use this photo*: Generate poses asks what to pose (FR-4.8). Still nothing is sent. */
    fun useScene() {
        val state = _uiState.value
        if (state.phase != GenerationUiState.Phase.REVIEWING || state.scene == null) return
        _uiState.update { it.copy(phase = GenerationUiState.Phase.DESCRIBING) }
        openDescription()
    }

    /** Generate poses opened: the subject's phone shows it too, with the scene photo and the text kept (FR-6.15). */
    private fun openDescription() {
        val state = _uiState.value
        val scene = state.scene ?: return
        val id = UUID.randomUUID().toString()
        descriptionId = id
        _uiState.update { it.copy(descriptionWriter = null) }
        descriptions.open(id, scene, state.description, state.descriptionRejected)
    }

    /** The description field changed; kept so the text survives a rejected set (US-5). Not while the subject types. */
    fun onDescriptionChanged(text: String) {
        val state = _uiState.value
        if (state.phase != GenerationUiState.Phase.DESCRIBING || state.descriptionWriter == Role.SUBJECT) return
        _uiState.update { it.copy(description = text) }
        reportDescription()
    }

    /**
     * This phone's description field took or lost focus: it holds the description while the subject's does not
     * (Design 2.6.5). False when the subject's field holds it, so this one is not to be typed in.
     */
    fun onDescriptionFocus(focused: Boolean): Boolean {
        val state = _uiState.value
        if (state.phase != GenerationUiState.Phase.DESCRIBING) return false
        val writer = state.descriptionWriter
        if (writer == Role.SUBJECT) return !focused
        val next = if (focused) Role.PHOTOGRAPHER else null
        if (writer != next) {
            _uiState.update { it.copy(descriptionWriter = next) }
            reportDescription()
        }
        return true
    }

    private fun reportDescription() {
        val id = descriptionId ?: return
        val state = _uiState.value
        descriptions.update(id, state.description, state.descriptionWriter)
    }

    /**
     * The subject's field and buttons on Generate poses (FR-6.15), requests like its others (AD-7): an edit is taken
     * while this phone's field does not hold the description, and *Generate 4 poses* or *Skip* the same way, with the
     * subject's text while its field held it.
     */
    private fun onSubjectRequest(request: DescriptionRequest) {
        val state = _uiState.value
        val describing = state.phase == GenerationUiState.Phase.DESCRIBING
        when (request) {
            is DescriptionRequest.Edit -> {
                if (!describing || request.descriptionId != descriptionId) return
                val writer = state.descriptionWriter
                when {
                    writer == Role.PHOTOGRAPHER -> return

                    request.holding -> _uiState.update {
                        it.copy(description = request.text, descriptionWriter = Role.SUBJECT)
                    }

                    writer == Role.SUBJECT -> _uiState.update {
                        it.copy(description = request.text, descriptionWriter = null)
                    }

                    else -> return
                }
                reportDescription()
            }

            is DescriptionRequest.Go -> {
                if (!describing || request.descriptionId != descriptionId) return
                val writer = state.descriptionWriter
                if (writer == Role.PHOTOGRAPHER) return
                val text = if (writer == Role.SUBJECT) request.text else state.description
                _uiState.update { it.copy(description = text, descriptionWriter = null) }
                if (request.suggest) suggestPoses() else generate(text)
            }

            DescriptionRequest.Gone -> if (describing && state.descriptionWriter == Role.SUBJECT) {
                _uiState.update { it.copy(descriptionWriter = null) }
                reportDescription()
            }
        }
    }

    /** Generate poses ended: a set [started] from it, or it closed, also on the subject's phone. */
    private fun endDescription(started: Boolean) {
        descriptionId?.let { descriptions.end(it, started) }
        descriptionId = null
    }

    /**
     * *Generate 4 poses*: four candidates of the description, or of four suggested poses when the field is blank
     * (US-5). Only from Generate poses.
     */
    fun generate(description: String) {
        val state = _uiState.value
        if (!state.canStart || state.scene == null) return
        start(state.scene, state.selfie, PoseRequest.of(description), description)
    }

    /** *Skip, suggest poses for me*: the server chooses four poses for the scene (FR-4.4). */
    fun suggestPoses() {
        val state = _uiState.value
        if (!state.canStart || state.scene == null) return
        start(state.scene, state.selfie, PoseRequest.Suggested, state.description)
    }

    /** Generate poses can start a set: not while the subject's field holds the description (Design 2.6.5). */
    private val GenerationUiState.canStart: Boolean
        get() = phase == GenerationUiState.Phase.DESCRIBING && descriptionWriter != Role.SUBJECT

    /**
     * *Try other poses* on Pick a pose and *Edit description* on Couldn't create poses: back to Generate poses with
     * the photo and the text kept and the set discarded; nothing is sent until the user asks again (FR-4.6, US-5).
     */
    fun describeAgain() {
        val state = _uiState.value
        val ended = state.phase == GenerationUiState.Phase.PICKING || state.phase == GenerationUiState.Phase.FAILED
        if (!ended || state.scene == null) return
        _uiState.update {
            it.copy(
                phase = GenerationUiState.Phase.DESCRIBING,
                poses = emptyList(),
                candidates = emptyMap(),
                selectedPoseId = null,
                chosenBy = null,
            )
        }
        closeSet()
        openDescription()
    }

    /**
     * *Try again* on Couldn't create poses: the same scene and request with a new seed; suggestions are asked for
     * again, so they can differ. Does nothing when there is nothing to resend.
     */
    fun retry() {
        val state = _uiState.value
        if (state.scene != null && state.request != null) {
            start(state.scene, state.selfie, state.request, state.description)
        }
    }

    private fun start(scene: Bitmap, selfie: Boolean, request: PoseRequest, description: String) {
        generating?.cancel()
        endDescription(started = true)
        closeSet()
        val id = UUID.randomUUID().toString()
        setId = id
        share.open(id)
        _uiState.value = GenerationUiState(
            scene = scene,
            selfie = selfie,
            request = request,
            description = description,
            phase = GenerationUiState.Phase.GENERATING,
        )
        generating = viewModelScope.launch { generate(id, scene, selfie, request) }
    }

    /** The open set ends without a choice, for the subject's *Pick a pose* too; one that was chosen stays chosen. */
    private fun closeSet() {
        setId?.let(share::close)
        setId = null
    }

    /**
     * *Cancel* and *Back to camera*: stops the requests and discards the scene and the candidates. Also called once
     * *Use this pose* has handed the chosen image on, so the other images do not stay in memory.
     */
    fun cancel() {
        shooting?.cancel()
        shooting = null
        generating?.cancel()
        generating = null
        endDescription(started = false)
        closeSet()
        _uiState.value = GenerationUiState()
    }

    /** Only a finished candidate can be selected, only once generation has ended, and not after a choice (R&S 6.3). */
    fun select(poseId: String) {
        _uiState.update {
            val selectable = it.phase == GenerationUiState.Phase.PICKING && it.chosenBy == null &&
                it.candidates[poseId] is CandidateEvent.Ready
            if (selectable) it.copy(selectedPoseId = poseId) else it
        }
    }

    /**
     * *Use this pose*: the selected candidate, unless the subject's pick came first (Design 2.6.5), in which case
     * null, and Pick a pose goes on with the subject's. The set counts as chosen here before Reference confirm opens.
     */
    fun usePose(): PoseUse? {
        val state = _uiState.value
        val image = state.selectedImage
        val poseId = state.selectedPoseId
        if (state.phase != GenerationUiState.Phase.PICKING || state.chosenBy != null || image == null ||
            poseId == null
        ) {
            return null
        }
        _uiState.update { it.copy(chosenBy = Role.PHOTOGRAPHER) }
        setId?.let { share.chosen(it, poseId, Role.PHOTOGRAPHER) }
        return PoseUse(image, state.tipSource)
    }

    /** The subject's *Use this pose* (FR-6.15): taken only for this set while nobody has chosen (AD-7). */
    private fun onSubjectPick(pick: SubjectPick) {
        val state = _uiState.value
        val open = pick.setId == setId && state.phase == GenerationUiState.Phase.PICKING && state.chosenBy == null
        if (!open || state.candidates[pick.poseId] !is CandidateEvent.Ready) return
        _uiState.update { it.copy(selectedPoseId = pick.poseId, chosenBy = Role.SUBJECT) }
        share.chosen(pick.setId, pick.poseId, Role.SUBJECT)
    }

    /**
     * Ends in PICKING when at least one candidate arrived and in FAILED otherwise (FR-4.7), saying so when the service
     * rejected every pose of a description (US-5). One limit covers the whole run, the suggestions included, so
     * Generating poses never lasts longer than the 60 s it says (NFR-4).
     */
    private suspend fun generate(id: String, scene: Bitmap, selfie: Boolean, request: PoseRequest) {
        Timings.mark(
            "pose.start",
            (if (request is PoseRequest.Suggested) "suggested" else "described") + if (selfie) " selfie" else "",
        )
        val inTime = withTimeoutOrNull(limitMs) {
            try {
                generator.generate(scene, request, Random.nextLong(), selfie).collect { event -> receive(id, event) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The poses could not be had; whatever arrived before a later failure is still shown.
                Log.w(TAG, "Generation stopped: $error")
            }
        }
        if (inTime == null) stopUnanswered()
        val state = _uiState.value
        Timings.mark("pose.done", "${state.readyCount} of ${state.poses.size} ready")
        val rejected = request is PoseRequest.Described && state.poses.isNotEmpty() &&
            state.poses.all { (state.candidates[it.id] as? CandidateEvent.Failed)?.error == GenerationError.REJECTED }
        _uiState.update {
            if (it.readyCount > 0) {
                it.copy(phase = GenerationUiState.Phase.PICKING)
            } else {
                it.copy(phase = GenerationUiState.Phase.FAILED, descriptionRejected = rejected)
            }
        }
        val ended = _uiState.value
        if (ended.phase == GenerationUiState.Phase.PICKING) {
            // In the pager's order, which the subject's Pick a pose follows.
            share.picking(id, ended.poses.map { it.id }.filter { ended.candidates[it] is CandidateEvent.Ready })
        } else {
            closeSet()
        }
    }

    private fun receive(id: String, event: GenerationEvent) {
        when (event) {
            is GenerationEvent.Planned -> {
                Timings.mark("pose.planned")
                _uiState.update { it.copy(poses = event.poses) }
            }

            is CandidateEvent -> {
                if (event is CandidateEvent.Ready && _uiState.value.readyCount == 0) Timings.mark("pose.first")
                _uiState.update { it.copy(candidates = it.candidates + (event.poseId to event)) }
                if (event is CandidateEvent.Ready) share.candidate(id, event.poseId, event.image)
            }
        }
    }

    /** The limit ended the collector, and with it the requests still open: their poses count as timed out. */
    private fun stopUnanswered() {
        _uiState.update { state ->
            val unanswered = state.poses.filter { it.id !in state.candidates }
            for (pose in unanswered) Log.w(TAG, "${pose.id}: no answer within $limitMs ms")
            val timedOut = unanswered.associate { it.id to CandidateEvent.Failed(it.id, GenerationError.TIMEOUT) }
            state.copy(candidates = state.candidates + timedOut)
        }
    }

    companion object {
        private const val TAG = "PixPoses"

        /** How long the scene photo waits for the light the user turned on (FR-4.1). */
        const val LIGHT_WAIT_MS = 1_000L

        /** How long after the light comes on the scene photo is taken: the camera's exposure follows it. */
        const val LIGHT_SETTLE_MS = 500L
    }
}
