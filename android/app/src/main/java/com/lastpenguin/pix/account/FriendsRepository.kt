// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import kotlinx.coroutines.flow.StateFlow

/**
 * Friends and friend requests of the signed-in user (Design 2.1, FR-5.4–5.10). A failure is an [AccountException]. The
 * lists hold what the last [refresh] or answer brought, and empty when another user signs in. Without push (until
 * Iter2-P15) the Friends screen refreshes them while it is shown. [RemoteFriendsRepository] is the real one.
 * Owner: Accounts (#54).
 */
interface FriendsRepository {
    val friends: StateFlow<List<Friend>>

    /** Received and pending, newest first. */
    val requests: StateFlow<List<FriendRequest>>

    /** Sent and not answered yet, newest first (FR-5.16). */
    val sentRequests: StateFlow<List<SentRequest>>

    /** Loads the three lists again. */
    suspend fun refresh(): Result<Unit>

    /** Exact match only (FR-5.5); NotFound for an unknown Pix ID. */
    suspend fun search(pixId: String): Result<UserLookup>

    suspend fun request(userId: String): Result<RequestOutcome>

    suspend fun accept(requestId: String): Result<Friend>

    suspend fun decline(requestId: String): Result<Unit>

    /** Withdraws a request the user sent. It leaves [sentRequests] even on NOT_FOUND: it was answered already. */
    suspend fun cancel(requestId: String): Result<Unit>

    suspend fun remove(userId: String): Result<Unit>
}
