// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

// Accounts and friends (Design 2.3, R&S F5). Shared contract: change only with the Design Documentation.

/** The signed-in user's own account. [pixId] is null for an account made with Google until it chooses one. */
data class Account(
    val userId: String,
    val pixId: String?,
    val displayName: String,
    /** The ways the account signs in: offer *Add a password* or *Add Google* for the missing one (FR-5.12, 5.14). */
    val hasPassword: Boolean,
    val hasGoogle: Boolean,
)

/** Whether this phone is signed in. Everything but friends and invitations works signed out (FR-5.1). */
sealed interface AuthState {
    /** The kept sign-in is still being read when the app starts. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val account: Account) : AuthState
}

/** The answer to *Continue with Google*. */
sealed interface GoogleSignIn {
    data class SignedIn(val account: Account) : GoogleSignIn

    /** No Pix account uses this Google account: ask whether the person already has one (FR-5.13). */
    data object NotRegistered : GoogleSignIn
}

/** Another user, as a lookup or a friend request shows them. */
data class PixUser(val userId: String, val pixId: String, val displayName: String)

data class Friend(
    val userId: String,
    val pixId: String,
    val displayName: String,
    /** Pix is open on the friend's phone (FR-5.9). */
    val online: Boolean,
    /** The friend is in a session, a waiting room included, so an invitation would be refused (FR-6.9). */
    val inSession: Boolean,
)

/** A pending request someone sent this user (FR-5.7). */
data class FriendRequest(val id: String, val from: PixUser, val createdAt: Long)

sealed interface UserLookup {
    /** [relation] decides the button (FR-5.8); [requestId] is the pending request for REQUESTED and INCOMING. */
    data class Found(val user: PixUser, val relation: Relation, val requestId: String?) : UserLookup

    data object NotFound : UserLookup
}

enum class Relation { NONE, SELF, FRIEND, REQUESTED, INCOMING }

/** What *Add* did. */
sealed interface RequestOutcome {
    data class Sent(val requestId: String) : RequestOutcome

    /** The other user had already sent a request, so the two are friends now. */
    data class BecameFriends(val friend: Friend) : RequestOutcome
}

/** Why an account or friend call failed: the server's error code (Design 2.5.3), or the phone's own reason. */
enum class AccountError {
    PIX_ID_TAKEN,
    PIX_ID_INVALID,
    PASSWORD_INVALID,
    INVALID_CREDENTIALS,
    INVALID_GOOGLE_TOKEN,
    GOOGLE_IN_USE,
    GOOGLE_ALREADY_ADDED,
    PIX_ID_REQUIRED,
    PASSWORD_ALREADY_SET,
    NOT_FOUND,
    INVALID_REQUEST,

    /** The server could not reach Google or its database; try again later. */
    UNAVAILABLE,

    /** The sign-in ended (the refresh token was refused) or there was none. */
    SIGNED_OUT,

    /** No answer from the server. */
    OFFLINE,
    UNKNOWN,
}

/** A failed account call. [serverMessage] names the broken rule for PIX_ID_INVALID and PASSWORD_INVALID (FR-5.3, 5.11). */
class AccountException(val error: AccountError, val serverMessage: String = "") : Exception("$error $serverMessage")
