// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.core.network

import kotlinx.serialization.Serializable

// REST payloads (Design 2.5.3). Field names match the server's JSON.

/** Four poses for a scene, in the server's order; each becomes the description of one `POST /poses`. */
@Serializable
data class PoseSuggestionsDto(val poses: List<String>)

@Serializable
data class PoseResponseDto(
    /** Null for a description, which is every request this app sends; only apps from before Iteration 2 send ids. */
    val templateId: String? = null,
    val image: String, // Base64 JPEG
    val elapsedMs: Long,
)

@Serializable
data class ApiErrorBody(val error: ApiErrorDto)

@Serializable
data class ApiErrorDto(val code: String, val message: String = "")
