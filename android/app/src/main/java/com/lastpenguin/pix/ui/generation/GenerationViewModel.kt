package com.lastpenguin.pix.ui.generation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.generation.CandidateEvent
import com.lastpenguin.pix.generation.PoseGenerator
import com.lastpenguin.pix.generation.PoseTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Generating poses, Pick a pose, and Couldn't create poses (R&S 6.3). */
data class GenerationUiState(
    val scene: Bitmap? = null,
    val templates: List<PoseTemplate> = emptyList(),
    /** Results so far by template id; the four slots fill as they arrive. */
    val candidates: Map<String, CandidateEvent> = emptyMap(),
    val selectedTemplateId: String? = null,
    val phase: Phase = Phase.IDLE,
) {
    enum class Phase { IDLE, GENERATING, PICKING, FAILED }
}

/**
 * Pose generation screens (Design 2.2, Figure 8). Shared by the three screens (activity scope).
 * Owner: Server/AI/Sync (#7).
 */
class GenerationViewModel(
    private val generator: PoseGenerator,
    private val camera: CameraController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    /** *Generate poses here*: takes the scene photo with camera.grabFrame() (FR-4.1), then [start]. */
    fun startFromCamera() {
        // TODO(#7)
    }

    fun start(scene: Bitmap) {
        // TODO(#7): generator.generate(scene, templates, seed); PICKING when all are done, or at 30 s with at least one.
    }

    /** *Try other poses* and *Try again*: same scene, new seed. */
    fun retry() {
        // TODO(#7)
    }

    fun cancel() {
        // TODO(#7): cancel the generation job.
    }

    fun select(templateId: String) {
        // TODO(#7)
    }
}
