package com.lastpenguin.pix.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Pix server REST API (Design 2.5.3). */
interface PixApi {

    @GET("api/v1/pose-templates")
    suspend fun poseTemplates(): List<PoseTemplateDto>

    /** One candidate per call; the app sends one call per template in parallel. */
    @Multipart
    @POST("api/v1/poses")
    suspend fun createPose(
        @Part image: MultipartBody.Part,
        @Part("templateId") templateId: RequestBody,
        @Part("seed") seed: RequestBody,
    ): PoseResponseDto
}
