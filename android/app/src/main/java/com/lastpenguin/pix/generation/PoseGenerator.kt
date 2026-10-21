// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/**
 * Generates pose candidates for the current scene (Design 2.6.3).
 * Owner: Server/AI/Sync.
 */
interface PoseGenerator {
    /**
     * Emits [GenerationEvent.Planned] with the [POSE_COUNT] poses of [request] (for [PoseRequest.Suggested] once the
     * server has chosen them), then one [CandidateEvent] per pose as each finishes. Throws when the poses cannot be
     * had, for example offline. Cancel the collector to cancel the requests.
     */
    fun generate(scene: Bitmap, request: PoseRequest, seed: Long): Flow<GenerationEvent>
}
