package com.lastpenguin.pix.core.network

import kotlinx.serialization.Serializable

// REST payloads (Design 2.5.3). Field names match the server's JSON.

@Serializable
data class PoseTemplateDto(val id: String, val label: String)

@Serializable
data class PoseResponseDto(
    val templateId: String,
    val image: String, // Base64 JPEG
    val elapsedMs: Long,
)

@Serializable
data class ApiErrorBody(val error: ApiErrorDto)

@Serializable
data class ApiErrorDto(val code: String, val message: String = "")
