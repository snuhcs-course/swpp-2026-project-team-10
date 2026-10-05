package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.GenerationConsent
import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseTemplate
import com.lastpenguin.pix.generation.RemotePoseGenerator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The scene photo on Camera, Generating poses, Pick a pose, and Couldn't create poses (R&S 6.3). */
data class GenerationUiState(
    /** The photo the user took and confirmed; kept for *Try again* and *Try other poses*. */
    val scene: Bitmap? = null,
    val templates: List<PoseTemplate> = emptyList(),
    /** Results so far by template id; the four slots fill as they arrive. */
    val candidates: Map<String, CandidateEvent> = emptyMap(),
    val selectedTemplateId: String? = null,
    val phase: Phase = Phase.IDLE,
    /** The shutter gave no photo; told on Camera until the next attempt. */
    val shotFailed: Boolean = false,
) {
    /**
     * FRAMING and REVIEWING are the scene photo step on the Camera screen: the live camera, whose shutter takes the
     * photo, then the photo with *Use this photo* and *Shoot again*. Nothing is sent before GENERATING.
     */
    enum class Phase { IDLE, FRAMING, REVIEWING, GENERATING, PICKING, FAILED }

    val readyCount: Int get() = candidates.values.count { it is CandidateEvent.Ready }

    /** The image *Use this pose* sends to Reference confirm. */
    val selectedImage: Bitmap?
        get() = (candidates[selectedTemplateId] as? CandidateEvent.Ready)?.image
}

/**
 * Pose generation (Design 2.2, Figure 8). Shared by the Camera screen, for the scene photo, and the three
 * generation screens (activity scope).
 * Owner: Server/AI/Sync (#7).
 */
class GenerationViewModel(
    private val generator: PoseGenerator,
    private val camera: CameraController,
    private val consent: GenerationConsent,
    private val limitMs: Long = RemotePoseGenerator.TIMEOUT_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private var generating: Job? = null
    private var shooting: Job? = null

    private var consentGiven = false

    init {
        viewModelScope.launch { consentGiven = consent.isGiven() }
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

    /** The shutter during FRAMING: the camera's current frame becomes the photo to confirm. */
    fun takeScene() {
        if (_uiState.value.phase != GenerationUiState.Phase.FRAMING || shooting?.isActive == true) return
        shooting = viewModelScope.launch {
            val frame = camera.grabFrame()
            frame.exceptionOrNull()?.let { Log.w(TAG, "No scene photo: $it") }
            _uiState.update { state ->
                when {
                    // Cancelled while the frame was being converted.
                    state.phase != GenerationUiState.Phase.FRAMING -> state

                    frame.isSuccess -> state.copy(
                        scene = frame.getOrNull(),
                        phase = GenerationUiState.Phase.REVIEWING,
                        shotFailed = false,
                    )

                    else -> state.copy(shotFailed = true)
                }
            }
        }
    }

    /** *Shoot again*: the photo is dropped and the live camera is shown again. */
    fun retakeScene() {
        _uiState.update {
            val reviewing = it.phase == GenerationUiState.Phase.REVIEWING
            if (reviewing) GenerationUiState(phase = GenerationUiState.Phase.FRAMING) else it
        }
    }

    /** *Use this photo*: only now is the photo sent, and the poses are generated from it. */
    fun useScene() {
        val state = _uiState.value
        if (state.phase == GenerationUiState.Phase.REVIEWING && state.scene != null) start(state.scene)
    }

    fun start(scene: Bitmap) {
        generating?.cancel()
        _uiState.value = GenerationUiState(scene = scene, phase = GenerationUiState.Phase.GENERATING)
        generating = viewModelScope.launch { generate(scene) }
    }

    /** *Try other poses* and *Try again*: same scene, new seed. Does nothing when there is no scene to resend. */
    fun retry() {
        _uiState.value.scene?.let(::start)
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
        _uiState.value = GenerationUiState()
    }

    /** Only a finished candidate can be selected, and only once generation has ended (R&S 6.3). */
    fun select(templateId: String) {
        _uiState.update {
            val selectable = it.phase == GenerationUiState.Phase.PICKING &&
                it.candidates[templateId] is CandidateEvent.Ready
            if (selectable) it.copy(selectedTemplateId = templateId) else it
        }
    }

    /**
     * Ends in PICKING when at least one candidate arrived and in FAILED otherwise (FR-4.7). One limit covers the whole
     * run, fetching the templates included, so Generating poses never lasts longer than the 30 s it says (NFR-4).
     */
    private suspend fun generate(scene: Bitmap) {
        _uiState.value = GenerationUiState(scene = scene, phase = GenerationUiState.Phase.GENERATING)
        Timings.mark("pose.start")
        val inTime = withTimeoutOrNull(limitMs) {
            try {
                val templates = generator.templates()
                _uiState.update { it.copy(templates = templates) }
                generator.generate(scene, templates, Random.nextLong()).collect { event ->
                    if (event is CandidateEvent.Ready && _uiState.value.readyCount == 0) Timings.mark("pose.first")
                    _uiState.update { it.copy(candidates = it.candidates + (event.templateId to event)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The templates could not be fetched; whatever arrived before a later failure is still shown.
                Log.w(TAG, "Generation stopped: $error")
            }
        }
        if (inTime == null) stopUnanswered()
        val state = _uiState.value
        Timings.mark("pose.done", "${state.readyCount} of ${state.templates.size} ready")
        val phase = if (state.readyCount > 0) GenerationUiState.Phase.PICKING else GenerationUiState.Phase.FAILED
        _uiState.update { it.copy(phase = phase) }
    }

    /** The limit ended the collector, and with it the requests still open: their poses count as timed out. */
    private fun stopUnanswered() {
        _uiState.update { state ->
            val unanswered = state.templates.filter { it.id !in state.candidates }
            for (template in unanswered) Log.w(TAG, "${template.id}: no answer within $limitMs ms")
            val timedOut = unanswered.associate { it.id to CandidateEvent.Failed(it.id, GenerationError.TIMEOUT) }
            state.copy(candidates = state.candidates + timedOut)
        }
    }

    private companion object {
        const val TAG = "PixPoses"
    }
}
