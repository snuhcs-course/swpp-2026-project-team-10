// AI-generated with Claude Code, 2026-10-22, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import android.content.Context
import com.lastpenguin.pix.core.network.AcceptedRequestDto
import com.lastpenguin.pix.core.network.AccountDto
import com.lastpenguin.pix.core.network.AuthDto
import com.lastpenguin.pix.core.network.FriendDto
import com.lastpenguin.pix.core.network.FriendRequestBody
import com.lastpenguin.pix.core.network.FriendRequestsDto
import com.lastpenguin.pix.core.network.FriendsDto
import com.lastpenguin.pix.core.network.GoogleTokenBody
import com.lastpenguin.pix.core.network.LogInBody
import com.lastpenguin.pix.core.network.LookupBody
import com.lastpenguin.pix.core.network.LookupDto
import com.lastpenguin.pix.core.network.PasswordBody
import com.lastpenguin.pix.core.network.PixAccountApi
import com.lastpenguin.pix.core.network.PixAuthApi
import com.lastpenguin.pix.core.network.RefreshBody
import com.lastpenguin.pix.core.network.SentRequestDto
import com.lastpenguin.pix.core.network.SignUpBody
import com.lastpenguin.pix.core.network.TokensDto
import com.lastpenguin.pix.core.network.UpdateMeBody
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

// Fakes of the account modules and of the REST API (Design 2.5.3), shared by the account and ui.account tests.

/** The server's error answer for [code], as Retrofit throws it. */
fun httpError(status: Int, code: String, message: String = ""): HttpException = HttpException(
    Response.error<Any>(
        status,
        """{"error":{"code":"$code","message":"$message"}}""".toResponseBody("application/json".toMediaType()),
    ),
)

fun accountDto(
    id: String = "u_1",
    pixId: String? = "test1",
    hasPassword: Boolean = true,
    hasGoogle: Boolean = false,
) = AccountDto(id, pixId, displayName = "Test Account 1", hasPassword = hasPassword, hasGoogle = hasGoogle)

fun authDto(
    user: AccountDto = accountDto(),
    access: String = "access-1",
    refresh: String = "refresh-1",
    expiresIn: Long = 900,
) = AuthDto(access, refresh, expiresIn, user)

/** Keeps the sign-in in memory and counts saves. */
class FakeSignInStore(var stored: StoredSignIn? = null) : SignInStore {
    var saves = 0

    override suspend fun load(): StoredSignIn? = stored

    override suspend fun save(signIn: StoredSignIn?) {
        saves++
        stored = signIn
    }
}

/** The sign-in endpoints. Each answer is a function a test replaces; every call is recorded. */
class FakeAuthApi : PixAuthApi {
    val calls = mutableListOf<Any>()
    var signUp: suspend (SignUpBody) -> AuthDto = { authDto(accountDto(pixId = it.pixId)) }
    var logIn: suspend (LogInBody) -> AuthDto = { authDto(accountDto(pixId = it.pixId)) }
    var refresh: suspend (
        RefreshBody,
    ) -> TokensDto = { TokensDto("access-${calls.size + 1}", "refresh-${calls.size + 1}", 900) }
    var google: suspend (GoogleTokenBody) -> AuthDto = { authDto(accountDto(hasPassword = false, hasGoogle = true)) }
    var googleSignUp: suspend (GoogleTokenBody) -> AuthDto =
        { authDto(accountDto(pixId = null, hasPassword = false, hasGoogle = true)) }

    override suspend fun signUp(body: SignUpBody): AuthDto = signUp.invoke(body.also { calls += it })

    override suspend fun logIn(body: LogInBody): AuthDto = logIn.invoke(body.also { calls += it })

    override suspend fun refresh(body: RefreshBody): TokensDto = refresh.invoke(body.also { calls += it })

    override suspend fun signInWithGoogle(body: GoogleTokenBody): AuthDto = google.invoke(body.also { calls += it })

    override suspend fun signUpWithGoogle(body: GoogleTokenBody): AuthDto =
        googleSignUp.invoke(body.also { calls += "google/signup" to it })
}

/** The endpoints with a token. Lists and answers are fields a test sets; every call is recorded by name. */
class FakeAccountApi : PixAccountApi {
    val calls = mutableListOf<String>()
    var me = accountDto()
    var failure: Throwable? = null
    var friends = listOf<FriendDto>()
    var requests = FriendRequestsDto()
    var lookup: suspend (LookupBody) -> LookupDto = { throw httpError(404, "NOT_FOUND") }
    var sent: SentRequestDto = SentRequestDto(id = "fr_1")
    var accepted: (String) -> FriendDto = { FriendDto("u_2", "test2", "Test Account 2", online = true) }
    val bodies = mutableListOf<Any>()

    private fun call(name: String, body: Any? = null) {
        calls += name
        body?.let { bodies += it }
        failure?.let { throw it }
    }

    override suspend fun signOut() = call("signOut")

    override suspend fun me(): AccountDto = me.also { call("me") }

    override suspend fun updateMe(body: UpdateMeBody): AccountDto {
        call("updateMe", body)
        me = me.copy(pixId = body.pixId ?: me.pixId, displayName = body.displayName ?: me.displayName)
        return me
    }

    override suspend fun addGoogle(body: GoogleTokenBody): AccountDto {
        call("addGoogle", body)
        me = me.copy(hasGoogle = true)
        return me
    }

    override suspend fun addPassword(body: PasswordBody): AccountDto {
        call("addPassword", body)
        me = me.copy(hasPassword = true)
        return me
    }

    override suspend fun lookUp(body: LookupBody): LookupDto {
        call("lookUp", body)
        return lookup(body)
    }

    override suspend fun friendRequests(): FriendRequestsDto = requests.also { call("friendRequests") }

    override suspend fun sendFriendRequest(body: FriendRequestBody): SentRequestDto = sent.also { call("send", body) }

    override suspend fun acceptFriendRequest(requestId: String): AcceptedRequestDto {
        call("accept:$requestId")
        return AcceptedRequestDto(accepted(requestId))
    }

    override suspend fun declineFriendRequest(requestId: String) = call("decline:$requestId")

    override suspend fun withdrawFriendRequest(requestId: String) = call("withdraw:$requestId")

    override suspend fun friends(): FriendsDto = FriendsDto(friends).also { call("friends") }

    override suspend fun removeFriend(userId: String) = call("remove:$userId")
}

/** An [AuthRepository] whose answers a test sets; it records the calls and signs in like the real one. */
class FakeAuthRepository(state: AuthState = AuthState.SignedOut) : AuthRepository {
    private val _state = MutableStateFlow(state)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    val calls = mutableListOf<String>()
    var account = Account("u_1", "test1", "Test Account 1", hasPassword = true, hasGoogle = false)
    var failure: AccountException? = null

    /** Failures of one operation only, by its name, such as "addGoogle". */
    val failures = mutableMapOf<String, AccountException>()
    var googleResult: GoogleSignIn? = null
    var token: String? = "access-1"

    fun signInAs(account: Account) {
        this.account = account
        _state.value = AuthState.SignedIn(account)
    }

    fun expire() {
        _state.value = AuthState.SignedOut
        _expired.tryEmit(Unit)
    }

    private fun answer(name: String, change: (Account) -> Account = { it }): Result<Account> {
        calls += name
        (failures[name.substringBefore(':')] ?: failure)?.let { return Result.failure(it) }
        signInAs(change(account))
        return Result.success(account)
    }

    override suspend fun signUp(pixId: String, password: String, displayName: String) =
        answer("signUp:$pixId") { it.copy(pixId = pixId, displayName = displayName) }

    override suspend fun logIn(pixId: String, password: String) = answer("logIn:$pixId")

    override suspend fun signInWithGoogle(idToken: String): Result<GoogleSignIn> {
        calls += "google:$idToken"
        failure?.let { return Result.failure(it) }
        val result = googleResult ?: GoogleSignIn.SignedIn(account)
        if (result is GoogleSignIn.SignedIn) signInAs(result.account)
        return Result.success(result)
    }

    override suspend fun signUpWithGoogle(idToken: String) =
        answer("googleSignUp:$idToken") { it.copy(pixId = null, hasPassword = false, hasGoogle = true) }

    override suspend fun addGoogle(idToken: String) = answer("addGoogle:$idToken") { it.copy(hasGoogle = true) }

    override suspend fun addPassword(password: String) = answer("addPassword") { it.copy(hasPassword = true) }

    override suspend fun setPixId(pixId: String, password: String?) = answer("setPixId:$pixId") {
        it.copy(pixId = pixId)
    }

    override suspend fun setDisplayName(displayName: String) =
        answer("setDisplayName:$displayName") { it.copy(displayName = displayName) }

    override suspend fun freshAccessToken(): String? = token

    override suspend fun signOut() {
        calls += "signOut"
        _state.value = AuthState.SignedOut
    }
}

/** Google's account chooser: the next answer is [result]; [requests] counts the times it opened. */
class FakeGoogleIdTokens(var result: Result<String> = Result.success("google-token")) : GoogleIdTokens {
    var requests = 0

    override suspend fun request(context: Context): Result<String> {
        requests++
        return result
    }
}

/** A [FriendsRepository] whose lists and answers a test sets; it records the calls. */
class FakeFriendsRepository : FriendsRepository {
    val friendsFlow = MutableStateFlow<List<Friend>>(emptyList())
    override val friends: StateFlow<List<Friend>> = friendsFlow.asStateFlow()

    val requestsFlow = MutableStateFlow<List<FriendRequest>>(emptyList())
    override val requests: StateFlow<List<FriendRequest>> = requestsFlow.asStateFlow()

    val calls = mutableListOf<String>()
    var failure: AccountException? = null
    var lookup: UserLookup = UserLookup.NotFound
    var outcome: RequestOutcome = RequestOutcome.Sent("fr_1")

    private fun <T> answer(name: String, value: () -> T): Result<T> {
        calls += name
        return failure?.let { Result.failure(it) } ?: Result.success(value())
    }

    override suspend fun refresh(): Result<Unit> = answer("refresh") {}

    override suspend fun search(pixId: String): Result<UserLookup> = answer("search:$pixId") { lookup }

    override suspend fun request(userId: String): Result<RequestOutcome> = answer("request:$userId") { outcome }

    override suspend fun accept(requestId: String): Result<Friend> = answer("accept:$requestId") {
        Friend("u_3", "test3", "Test Account 3", online = true, inSession = false)
    }

    override suspend fun decline(requestId: String): Result<Unit> = answer("decline:$requestId") {}

    override suspend fun cancel(requestId: String): Result<Unit> = answer("cancel:$requestId") {}

    override suspend fun remove(userId: String): Result<Unit> = answer("remove:$userId") {}
}
