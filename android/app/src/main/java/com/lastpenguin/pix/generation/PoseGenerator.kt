package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/**
 * Generates pose candidates for the current scene (Design 2.6.3).
 * Owner: Server/AI/Sync.
 */
interface PoseGenerator {
    /** GET /api/v1/pose-templates. */
    suspend fun templates(): List<PoseTemplate>

    /** Emits one [CandidateEvent] per template as each finishes. Cancel the collector to cancel the requests. */
    fun generate(scene: Bitmap, templates: List<PoseTemplate>, seed: Long): Flow<CandidateEvent>
}
