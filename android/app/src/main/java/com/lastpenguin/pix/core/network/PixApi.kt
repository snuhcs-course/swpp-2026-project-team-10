// AI-generated with Claude Code, 2026-10-30, reviewed by Jaewan Park, Dongje Park, and Sungmin Jo
package com.lastpenguin.pix.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

/** Pix server REST API (Design 2.5.3): pose generation, which works without an account (FR-5.1). */
interface PixApi {

    /**
     * Four poses for the scene: suggested ones when [description] is null (FR-4.4), or the description followed by
     * three variations of it, so that the four candidates differ (FR-4.8). [selfie] is `true` for a selfie's scene,
     * which gets selfie poses (FR-1.14). A null part is left out of the request.
     */
    @Multipart
    @POST("api/v1/pose-suggestions")
    suspend fun suggestPoses(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody?,
        @Part("selfie") selfie: RequestBody?,
    ): PoseSuggestionsDto

    /** One candidate per call; the app sends one call per pose of the set in parallel. [selfie] as above. */
    @Multipart
    @POST("api/v1/poses")
    suspend fun createPose(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody,
        @Part("seed") seed: RequestBody,
        @Part("selfie") selfie: RequestBody?,
    ): PoseResponseDto

    /**
     * How to hold the phone for the candidate the user took (FR-4.10): `scene` as sent to [createPose], `candidate`,
     * the pose it was made with, and the language the sentence is written in, `en` or `ko` (FR-10.2). `502
     * UPSTREAM_ERROR` also when the server could not use its model's answer.
     */
    @Multipart
    @POST("api/v1/pose-tips")
    suspend fun poseTip(
        @Part scene: MultipartBody.Part,
        @Part candidate: MultipartBody.Part,
        @Part("pose") pose: RequestBody,
        @Part("language") language: RequestBody,
    ): PoseTipDto
}

/**
 * The sign-in endpoints, which give a phone its tokens and need none themselves, and sign-up's Pix ID check, which
 * needs none either. Their client is not the pose client,
 * whose four long requests would hold up a refresh, and not the account client, whose calls wait for the refresh.
 */
interface PixAuthApi {

    /** `201`; `400 PIX_ID_INVALID`, `400 PASSWORD_INVALID`, `409 PIX_ID_TAKEN` (FR-5.2, FR-5.11). */
    @POST("api/v1/auth/signup")
    suspend fun signUp(@Body body: SignUpBody): AuthDto

    /** Whether a sign-up could take the Pix ID now, for *Check* (FR-5.15); `400 PIX_ID_INVALID`. */
    @POST("api/v1/auth/check-pix-id")
    suspend fun checkPixId(@Body body: PixIdCheckBody): PixIdCheckDto

    /** `401 INVALID_CREDENTIALS` for a wrong password and an unknown Pix ID alike. */
    @POST("api/v1/auth/login")
    suspend fun logIn(@Body body: LogInBody): AuthDto

    /** New tokens for a refresh token, which then stops working; `401 INVALID_REFRESH_TOKEN` ends the sign-in. */
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshBody): TokensDto

    /** `404 NOT_REGISTERED` when no account uses this Google account (FR-5.13); `401 INVALID_TOKEN`; `503`. */
    @POST("api/v1/auth/google")
    suspend fun signInWithGoogle(@Body body: GoogleTokenBody): AuthDto

    /** A new account named after the Google account, without a Pix ID yet; `409 GOOGLE_IN_USE`. */
    @POST("api/v1/auth/google/signup")
    suspend fun signUpWithGoogle(@Body body: GoogleTokenBody): AuthDto
}

/**
 * The endpoints marked *auth* in Design 2.5.3. Their client adds the access token and refreshes it once on
 * `401 UNAUTHENTICATED` (`account/BearerTokens.kt`); a `401` that still comes through means the sign-in ended.
 */
interface PixAccountApi {

    /** Signs this phone out: its refresh token and device token stop working at once. */
    @DELETE("api/v1/auth/session")
    suspend fun signOut()

    @GET("api/v1/me")
    suspend fun me(): AccountDto

    /** `409 PIX_ID_TAKEN`, `400 PIX_ID_INVALID`, `401 INVALID_CREDENTIALS` (a password account's wrong password). */
    @PUT("api/v1/me")
    suspend fun updateMe(@Body body: UpdateMeBody): AccountDto

    /** `409 GOOGLE_IN_USE`, `409 GOOGLE_ALREADY_ADDED`, `401 INVALID_TOKEN` (FR-5.12, FR-5.13). */
    @PUT("api/v1/me/google")
    suspend fun addGoogle(@Body body: GoogleTokenBody): AccountDto

    /** `400 PASSWORD_INVALID`, `403 PIX_ID_REQUIRED`, `409 PASSWORD_ALREADY_SET` (FR-5.14). */
    @PUT("api/v1/me/password")
    suspend fun addPassword(@Body body: PasswordBody): AccountDto

    /** Exact Pix ID only, in the body so that no log prints it (FR-5.5); `404 NOT_FOUND`. */
    @POST("api/v1/users/lookup")
    suspend fun lookUp(@Body body: LookupBody): LookupDto

    @GET("api/v1/friend-requests")
    suspend fun friendRequests(): FriendRequestsDto

    @POST("api/v1/friend-requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestBody): SentRequestDto

    @POST("api/v1/friend-requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") requestId: String): AcceptedRequestDto

    @POST("api/v1/friend-requests/{id}/decline")
    suspend fun declineFriendRequest(@Path("id") requestId: String)

    /** Withdraws a request the caller sent. */
    @DELETE("api/v1/friend-requests/{id}")
    suspend fun withdrawFriendRequest(@Path("id") requestId: String)

    /** Friends ordered by display name, with `online` and `inSession` (FR-5.9). */
    @GET("api/v1/friends")
    suspend fun friends(): FriendsDto

    /** `204` also for someone who is not a friend (FR-5.10). */
    @DELETE("api/v1/friends/{userId}")
    suspend fun removeFriend(@Path("userId") userId: String)

    /** This phone's FCM token for the sign-in, so its pushes reach this phone (Design 2.6.6). */
    @POST("api/v1/devices")
    suspend fun registerDevice(@Body body: DeviceBody)

    /** `403 NOT_FRIENDS`, `409 BUSY` (FR-6.1, FR-6.9). */
    @POST("api/v1/invitations")
    suspend fun invite(@Body body: InviteBody): SentInvitationDto

    /** Needs no sign-in, so a link opens it on any phone (FR-6.4); `404 NOT_FOUND`. */
    @GET("api/v1/invitations/{id}")
    suspend fun invitation(@Path("id") invitationId: String): OpenedInvitationDto

    /** The friend; `404 NOT_FOUND` for someone else's invitation, `410 INVITATION_GONE` once answered or expired. */
    @POST("api/v1/invitations/{id}/accept")
    suspend fun acceptInvitation(@Path("id") invitationId: String): SessionPlaceDto

    @POST("api/v1/invitations/{id}/decline")
    suspend fun declineInvitation(@Path("id") invitationId: String)

    /** The photographer. */
    @POST("api/v1/invitations/{id}/cancel")
    suspend fun cancelInvitation(@Path("id") invitationId: String)
}
