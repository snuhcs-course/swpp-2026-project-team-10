package com.lastpenguin.pix.generation

import android.graphics.Bitmap
import com.lastpenguin.pix.core.network.PixApi
import kotlinx.coroutines.flow.Flow

/**
 * [PoseGenerator] through the Pix server: one `POST /poses` per template, in parallel (Design 2.6.3).
 * Owner: Server/AI/Sync (#7).
 */
class RemotePoseGenerator(
    private val api: PixApi,
    private val timeoutMs: Long = 30_000,
) : PoseGenerator {

    override suspend fun templates(): List<PoseTemplate> =
        TODO("#7: GET /pose-templates, cached in memory after the first call")

    override fun generate(scene: Bitmap, templates: List<PoseTemplate>, seed: Long): Flow<CandidateEvent> =
        TODO("#7: JPEG 1024 px, one request per template, a CandidateEvent per response")
}
