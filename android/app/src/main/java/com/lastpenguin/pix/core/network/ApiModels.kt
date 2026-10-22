// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
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

// ---- Accounts and friends (It 2, #54) ------------------------------------------------------------------------------
// Request bodies leave out a null field, because the converter's Json does not encode default values.

@Serializable
data class SignUpBody(val pixId: String, val password: String, val displayName: String)

@Serializable
data class LogInBody(val pixId: String, val password: String)

@Serializable
data class PixIdCheckBody(val pixId: String)

/** `POST /auth/check-pix-id`. */
@Serializable
data class PixIdCheckDto(val available: Boolean)

@Serializable
data class RefreshBody(val refreshToken: String)

/** `POST /auth/google`, `POST /auth/google/signup`, and `PUT /me/google`. */
@Serializable
data class GoogleTokenBody(val idToken: String)

@Serializable
data class PasswordBody(val password: String)

/** `PUT /me`: either field may be left out; [password] is needed only to change a password account's Pix ID. */
@Serializable
data class UpdateMeBody(val pixId: String? = null, val displayName: String? = null, val password: String? = null)

/** The caller's own account. [pixId] is null for an account made with Google until it chooses one. */
@Serializable
data class AccountDto(
    val id: String,
    val pixId: String? = null,
    val displayName: String,
    val hasPassword: Boolean,
    val hasGoogle: Boolean,
)

/** A sign-in: the account and its tokens. [expiresIn] is the access token's lifetime in seconds. */
@Serializable
data class AuthDto(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: AccountDto)

@Serializable
data class TokensDto(val accessToken: String, val refreshToken: String, val expiresIn: Long)

@Serializable
data class UserDto(val id: String, val pixId: String? = null, val displayName: String)

@Serializable
data class LookupBody(val pixId: String)

/** [relation] is NONE, SELF, FRIEND, REQUESTED, or INCOMING; [requestId] is set for the last two. */
@Serializable
data class LookupDto(val user: UserDto, val relation: String, val requestId: String? = null)

@Serializable
data class FriendRequestBody(val userId: String)

@Serializable
data class FriendDto(
    val id: String,
    val pixId: String? = null,
    val displayName: String,
    val online: Boolean,
    val inSession: Boolean = false,
)

@Serializable
data class FriendsDto(val friends: List<FriendDto>)

@Serializable
data class IncomingRequestDto(val id: String, val from: UserDto, val createdAt: Long)

@Serializable
data class OutgoingRequestDto(val id: String, val to: UserDto, val createdAt: Long)

@Serializable
data class FriendRequestsDto(
    val incoming: List<IncomingRequestDto> = emptyList(),
    val outgoing: List<OutgoingRequestDto> = emptyList(),
)

/**
 * `POST /friend-requests`: `201` with the request ([id], [to], [createdAt]), or `200` with [friend] when the other
 * user had already sent the caller a request, which is then accepted.
 */
@Serializable
data class SentRequestDto(
    val id: String? = null,
    val to: UserDto? = null,
    val createdAt: Long? = null,
    val friend: FriendDto? = null,
)

/** `POST /friend-requests/{id}/accept`. */
@Serializable
data class AcceptedRequestDto(val friend: FriendDto)
