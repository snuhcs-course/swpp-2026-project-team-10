// AI-generated with Claude Code, 2026-10-01, reviewed by Dongje Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap

/** A pose the server knows how to generate. The prompt text stays on the server (Design 2.6.3). */
data class PoseTemplate(val id: String, val label: String)

enum class GenerationError { OFFLINE, TIMEOUT, RATE_LIMITED, REJECTED, SERVER }

/** One result per template. */
sealed interface CandidateEvent {
    val templateId: String

    data class Ready(override val templateId: String, val image: Bitmap) : CandidateEvent

    data class Failed(override val templateId: String, val error: GenerationError) : CandidateEvent
}
