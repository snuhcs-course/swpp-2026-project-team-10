// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Pix server REST API (Design 2.5.3). */
interface PixApi {

    /**
     * Four poses for the scene: suggested ones when [description] is null (FR-4.4), or the description followed by
     * three variations of it, so that the four candidates differ (FR-4.8). A null part is left out of the request.
     */
    @Multipart
    @POST("api/v1/pose-suggestions")
    suspend fun suggestPoses(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody?,
    ): PoseSuggestionsDto

    /** One candidate per call; the app sends one call per pose of the set in parallel. */
    @Multipart
    @POST("api/v1/poses")
    suspend fun createPose(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody,
        @Part("seed") seed: RequestBody,
    ): PoseResponseDto
}
