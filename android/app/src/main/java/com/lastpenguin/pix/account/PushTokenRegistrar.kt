// AI-generated with Claude Code, 2026-10-29, reviewed by Sungmin Jo
package com.lastpenguin.pix.account

import android.util.Log
import com.lastpenguin.pix.core.network.DeviceBody
import com.lastpenguin.pix.core.network.PixAccountApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** This phone's FCM token: Firebase in the app (`push/FirebasePushTokens.kt`), a fake in tests. */
fun interface PushTokens {
    /** The current token, made the first time it is asked for; null when Firebase cannot give one. */
    suspend fun current(): String?
}

/**
 * Registers this phone's FCM token with the server (`POST /devices`, Design 2.6.6), so invitations and friend requests
 * reach it as pushes: once for each sign-in, the one at app start included, and again whenever Firebase rotates the
 * token while someone is signed in. The server keeps one device per sign-in and drops it at sign-out, so nothing is
 * unregistered here. A registration that fails is tried again at the next sign-in or app start. Owner: Real-time (#119).
 */
class PushTokenRegistrar(
    private val api: PixAccountApi,
    private val auth: StateFlow<AuthState>,
    private val tokens: PushTokens,
    private val scope: CoroutineScope,
) {
    private val rotated = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun start(): Job = scope.launch {
        launch {
            auth.map { (it as? AuthState.SignedIn)?.account?.userId }
                .distinctUntilChanged()
                .collect { userId -> if (userId != null) tokens.current()?.let { register(it) } }
        }
        launch { rotated.collect { token -> if (auth.value is AuthState.SignedIn) register(token) } }
    }

    /** Firebase made a new token (`FirebaseMessagingService.onNewToken`). */
    fun onNewToken(token: String) {
        rotated.tryEmit(token)
    }

    private suspend fun register(token: String) {
        accountCall { api.registerDevice(DeviceBody(token, PLATFORM)) }
            .onFailure { Log.w(TAG, "Couldn't register this phone for pushes: ${(it as? AccountException)?.error}") }
    }

    private companion object {
        const val TAG = "PixPush"
        const val PLATFORM = "android"
    }
}
