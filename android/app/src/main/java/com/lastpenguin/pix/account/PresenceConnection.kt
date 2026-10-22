// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.account

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Opens the signaling socket that marks the user online. */
interface PresenceSockets {
    /** Opens a socket with [accessToken] and suspends until it closes; true if it was open first. Cancelling closes it. */
    suspend fun hold(accessToken: String): Boolean
}

/**
 * Shows the user online to friends while Pix is in the foreground (FR-5.9, decided in Iter2-P13): while someone is
 * signed in and the app is in front, it holds one signaling socket opened with the access token, which is how the
 * server knows presence (Design 2.5.2). The socket sends nothing; the server checks the token only when it opens. A
 * closed socket opens again after 1 s, doubling up to 30 s while it keeps failing. Signing out, another user signing
 * in, or the app going to the background closes it. Sessions keep their own socket (`session/`). Owner: Accounts (#54).
 */
class PresenceConnection(
    private val auth: AuthRepository,
    private val foreground: Flow<Boolean>,
    private val sockets: PresenceSockets,
    private val scope: CoroutineScope,
) {
    fun start(): Job = scope.launch {
        val user = auth.state.map { (it as? AuthState.SignedIn)?.account?.userId }
        combine(user, foreground) { userId, inFront -> userId.takeIf { inFront } }
            .distinctUntilChanged()
            .collectLatest { userId -> if (userId != null) holdOpen() }
    }

    private suspend fun holdOpen() {
        var retryMs = FIRST_RETRY_MS
        while (true) {
            val token = auth.freshAccessToken()
            if (token != null && sockets.hold(token)) retryMs = FIRST_RETRY_MS
            delay(retryMs)
            retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    companion object {
        const val FIRST_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L
    }
}

/** [PresenceSockets] on OkHttp, at `ws://<host>/ws` like the session's socket. */
class OkHttpPresenceSockets(private val client: OkHttpClient, private val url: String) : PresenceSockets {

    override suspend fun hold(accessToken: String): Boolean = suspendCancellableCoroutine { continuation ->
        val opened = AtomicBoolean(false)
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true)) continuation.resume(opened.get())
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.set(true)
                    Log.i(TAG, "Online")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish()

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish()
            },
        )
        continuation.invokeOnCancellation {
            done.set(true)
            socket.close(NORMAL_CLOSURE, null)
        }
    }

    private companion object {
        const val TAG = "PixPresence"
        const val NORMAL_CLOSURE = 1000
    }
}
