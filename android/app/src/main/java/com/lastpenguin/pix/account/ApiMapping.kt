// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.account

import com.lastpenguin.pix.core.network.AccountDto
import com.lastpenguin.pix.core.network.ApiErrorBody
import com.lastpenguin.pix.core.network.FriendDto
import com.lastpenguin.pix.core.network.IncomingRequestDto
import com.lastpenguin.pix.core.network.LookupDto
import com.lastpenguin.pix.core.network.OutgoingRequestDto
import com.lastpenguin.pix.core.network.UserDto
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

// From the REST payloads (Design 2.5.3) to the account models (Design 2.3), and from failures to AccountException.

internal fun AccountDto.toAccount() = Account(id, pixId, displayName, hasPassword, hasGoogle)

/** Friend endpoints answer only users with a Pix ID, so a missing one never reaches the screens in practice. */
internal fun UserDto.toPixUser() = PixUser(id, pixId.orEmpty(), displayName)

internal fun FriendDto.toFriend() = Friend(id, pixId.orEmpty(), displayName, online, inSession)

internal fun IncomingRequestDto.toFriendRequest() = FriendRequest(id, from.toPixUser(), createdAt)

internal fun OutgoingRequestDto.toSentRequest() = SentRequest(id, to.toPixUser(), createdAt)

internal fun LookupDto.toLookup(): UserLookup.Found {
    val relation = Relation.entries.firstOrNull { it.name == relation } ?: Relation.NONE
    return UserLookup.Found(user.toPixUser(), relation, requestId)
}

/** Runs one call, or a few, and turns any failure into an [AccountException]. Cancellation still propagates. */
internal suspend fun <T> accountCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: AccountException) {
    Result.failure(error)
} catch (error: HttpException) {
    Result.failure(error.toAccountException())
} catch (error: IOException) {
    Result.failure(AccountException(AccountError.OFFLINE))
} catch (error: SerializationException) {
    Result.failure(AccountException(AccountError.UNKNOWN, error.message.orEmpty()))
}

private val errorJson = Json { ignoreUnknownKeys = true }

/** The server's error code and message, if its body has the one error shape (Design 2.5.3). */
internal fun parseApiError(body: String?): Pair<String, String>? = try {
    body?.let { errorJson.decodeFromString<ApiErrorBody>(it).error }?.let { it.code to it.message }
} catch (error: SerializationException) {
    null
} catch (error: IllegalArgumentException) {
    null
}

/** The server's error codes (Design 2.5.3); INVALID_TOKEN is a Google ID token's. */
private val ERRORS = mapOf(
    "PIX_ID_TAKEN" to AccountError.PIX_ID_TAKEN,
    "PIX_ID_INVALID" to AccountError.PIX_ID_INVALID,
    "PASSWORD_INVALID" to AccountError.PASSWORD_INVALID,
    "INVALID_CREDENTIALS" to AccountError.INVALID_CREDENTIALS,
    "INVALID_TOKEN" to AccountError.INVALID_GOOGLE_TOKEN,
    "GOOGLE_IN_USE" to AccountError.GOOGLE_IN_USE,
    "GOOGLE_ALREADY_ADDED" to AccountError.GOOGLE_ALREADY_ADDED,
    "PIX_ID_REQUIRED" to AccountError.PIX_ID_REQUIRED,
    "PASSWORD_ALREADY_SET" to AccountError.PASSWORD_ALREADY_SET,
    "NOT_FOUND" to AccountError.NOT_FOUND,
    "INVALID_REQUEST" to AccountError.INVALID_REQUEST,
    "NOT_FRIENDS" to AccountError.NOT_FRIENDS,
    "BUSY" to AccountError.BUSY,
    "INVITATION_GONE" to AccountError.INVITATION_GONE,
    "UNAVAILABLE" to AccountError.UNAVAILABLE,
    "UNAUTHENTICATED" to AccountError.SIGNED_OUT,
    "INVALID_REFRESH_TOKEN" to AccountError.SIGNED_OUT,
)

internal fun HttpException.toAccountException(): AccountException {
    val (code, message) = parseApiError(response()?.errorBody()?.string()) ?: ("" to "")
    val error = ERRORS[code] ?: when (code()) {
        401 -> AccountError.SIGNED_OUT
        502, 503, 504 -> AccountError.UNAVAILABLE
        else -> AccountError.UNKNOWN
    }
    return AccountException(error, message)
}
