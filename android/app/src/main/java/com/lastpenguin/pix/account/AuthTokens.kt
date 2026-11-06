// AI-generated with Claude Code, 2026-10-28, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lastpenguin.pix.core.network.AccountDto
import com.lastpenguin.pix.core.network.AuthDto
import com.lastpenguin.pix.core.network.PixAuthApi
import com.lastpenguin.pix.core.network.RefreshBody
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** What the phone keeps of a sign-in: the account and its tokens. [accessExpiresAt] is in epoch milliseconds. */
@Serializable
data class StoredSignIn(
    val user: AccountDto,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
)

/** Where the sign-in is kept between app starts. */
interface SignInStore {
    suspend fun load(): StoredSignIn?

    /** Null forgets the sign-in. */
    suspend fun save(signIn: StoredSignIn?)
}

/** Where [BearerTokens] gets the access token for a request. */
interface AccessTokenSource {
    /** The access token, refreshed first if it expires within a minute; null when signed out or the refresh failed. */
    suspend fun freshAccessToken(): String?

    /** The server refused [rejected]: a new token, or the current one if another call already refreshed it. */
    suspend fun refreshAfterRejection(rejected: String): String?
}

/**
 * The phone's sign-in (Design 2.5.3, *Session tokens*): the account, its access and refresh tokens, and one refresh at
 * a time for every caller. A refresh token is spent by its refresh, and a spent one that comes back ends the session on
 * the server, so two refreshes must never run at once; a call that waited for one takes its new token instead. A
 * refused refresh token signs the phone out and emits [expired]; a refresh without an answer keeps the tokens.
 * Owner: Accounts (#54).
 */
class AuthTokens(
    private val store: SignInStore,
    private val api: PixAuthApi,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccessTokenSource {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    private val lock = Mutex()
    private var current: StoredSignIn? = null
    private val loaded = CompletableDeferred<Unit>()

    // Counts the writes of the kept account but new tokens, so a read from the server that one of them overtook is
    // dropped, also when the write left the account as it was: its answer is still the newer one.
    private var accountWrites = 0L

    init {
        scope.launch {
            lock.withLock {
                current = store.load()
                publish()
            }
            loaded.complete(Unit)
        }
    }

    /** Keeps a new sign-in in place of any earlier one. */
    suspend fun signIn(auth: AuthDto): Account {
        loaded.await()
        lock.withLock { set(StoredSignIn(auth.user, auth.accessToken, auth.refreshToken, expiry(auth.expiresIn))) }
        return auth.user.toAccount()
    }

    /** The account changed on the server; the tokens stay. Ignored once the phone signed out or in as someone else. */
    suspend fun update(user: AccountDto): Account {
        loaded.await()
        lock.withLock {
            val signIn = current
            if (signIn != null && signIn.user.id == user.id) set(signIn.copy(user = user))
        }
        return user.toAccount()
    }

    /**
     * The account as [read] from the server, where another phone signed in to it may have changed it; the tokens stay.
     * Ignored like [update], and also when the account changed on this phone while the read was on its way, because
     * that change's answer is the newer one. The lock is not held during [read], which may refresh the tokens.
     */
    suspend fun reload(read: suspend () -> AccountDto) {
        loaded.await()
        val before = lock.withLock { accountWrites }
        val user = read()
        lock.withLock {
            val signIn = current ?: return@withLock
            if (accountWrites != before || signIn.user.id != user.id) return@withLock
            // The same account is not written again, though Friends reads it every 10 s.
            if (signIn.user != user) set(signIn.copy(user = user))
        }
    }

    suspend fun clear() {
        loaded.await()
        lock.withLock { set(null) }
    }

    override suspend fun freshAccessToken(): String? {
        loaded.await()
        val signIn = lock.withLock { current } ?: return null
        if (clock() < signIn.accessExpiresAt - EARLY_REFRESH_MS) return signIn.accessToken
        return refresh(stale = signIn.accessToken)
    }

    override suspend fun refreshAfterRejection(rejected: String): String? {
        loaded.await()
        return refresh(stale = rejected)
    }

    private suspend fun refresh(stale: String): String? = lock.withLock {
        val signIn = current ?: return@withLock null
        // Another call refreshed while this one waited for the lock.
        if (signIn.accessToken != stale) return@withLock signIn.accessToken
        val tokens = try {
            api.refresh(RefreshBody(signIn.refreshToken))
        } catch (error: HttpException) {
            if (error.code() == UNAUTHORIZED) {
                Log.i(TAG, "The server ended the sign-in")
                set(null)
                _expired.tryEmit(Unit)
            }
            return@withLock null
        } catch (error: IOException) {
            return@withLock null
        } catch (error: SerializationException) {
            return@withLock null
        }
        set(
            signIn.copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accessExpiresAt = expiry(tokens.expiresIn),
            ),
            newTokensOnly = true,
        )
        tokens.accessToken
    }

    private suspend fun set(signIn: StoredSignIn?, newTokensOnly: Boolean = false) {
        if (!newTokensOnly) accountWrites++
        current = signIn
        store.save(signIn)
        publish()
    }

    private fun publish() {
        _state.value = current?.let { AuthState.SignedIn(it.user.toAccount()) } ?: AuthState.SignedOut
    }

    private fun expiry(expiresInSeconds: Long): Long = clock() + expiresInSeconds * 1000

    companion object {
        /** Refresh this long before the access token expires, so a request does not race its expiry. */
        const val EARLY_REFRESH_MS = 60_000L

        private const val UNAUTHORIZED = 401
        private const val TAG = "PixAccount"
    }
}

private val Context.accountStore by preferencesDataStore(name = "account")

/**
 * Keeps the sign-in in Jetpack DataStore, in the app's private files (`datastore/account.preferences_pb`), decided in
 * Iter2-P13 without Keystore encryption. Backups are off, and `data_extraction_rules.xml` keeps the file out of a
 * transfer to a new phone: a copied refresh token would be spent twice and end the session on both. A storage error
 * counts as signed out.
 */
class DataStoreSignInStore(context: Context) : SignInStore {

    private val store = context.applicationContext.accountStore

    override suspend fun load(): StoredSignIn? = try {
        store.data.first()[SIGN_IN]?.let { json.decodeFromString<StoredSignIn>(it) }
    } catch (error: IOException) {
        Log.w(TAG, "Could not read the sign-in", error)
        null
    } catch (error: SerializationException) {
        Log.w(TAG, "Could not read the sign-in", error)
        null
    }

    override suspend fun save(signIn: StoredSignIn?) {
        try {
            store.edit { preferences ->
                if (signIn == null) preferences.remove(SIGN_IN) else preferences[SIGN_IN] = json.encodeToString(signIn)
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not save the sign-in", error)
        }
    }

    private companion object {
        const val TAG = "PixAccount"
        val SIGN_IN = stringPreferencesKey("sign_in")
        val json = Json { ignoreUnknownKeys = true }
    }
}
