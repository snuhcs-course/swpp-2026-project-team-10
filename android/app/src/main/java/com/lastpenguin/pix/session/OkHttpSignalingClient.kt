package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.session.signaling.SignalCodec
import com.lastpenguin.pix.session.signaling.SignalMessage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * [SignalingClient] on an OkHttp WebSocket (Design 2.5.2). Only the current socket's events are published,
 * so a socket closed by [close] or replaced by [connect] stays silent.
 * Owner: Real-time (#8).
 */
class OkHttpSignalingClient(
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(PING_SECONDS, TimeUnit.SECONDS).build(),
) : SignalingClient {

    private val _events = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SignalEvent> = _events.asSharedFlow()

    private val lock = Any()
    private var socket: WebSocket? = null

    override fun connect(url: String) {
        close()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isCurrent(webSocket)) _events.tryEmit(SignalEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                val message = SignalCodec.decode(text)
                if (message ==
                    null
                ) {
                    Log.w(TAG, "Ignoring an unknown signaling message")
                } else {
                    _events.tryEmit(SignalEvent.Received(message))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (drop(webSocket)) _events.tryEmit(SignalEvent.Closed(null))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (drop(webSocket)) _events.tryEmit(SignalEvent.Closed(t))
            }
        }
        synchronized(lock) {
            socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        }
    }

    override fun send(signal: SignalMessage) {
        val current = synchronized(lock) { socket }
        if (current == null || !current.send(SignalCodec.encode(signal))) {
            Log.w(TAG, "Signaling message not sent: no open socket")
        }
    }

    override fun close() {
        val current = synchronized(lock) { socket.also { socket = null } }
        current?.close(NORMAL_CLOSURE, null)
    }

    private fun isCurrent(webSocket: WebSocket): Boolean = synchronized(lock) { socket === webSocket }

    /** Forgets [webSocket] if it is the current one. True when it was. */
    private fun drop(webSocket: WebSocket): Boolean = synchronized(lock) {
        if (socket === webSocket) {
            socket = null
            true
        } else {
            false
        }
    }

    private companion object {
        const val TAG = "PixSignaling"
        const val NORMAL_CLOSURE = 1000
        const val PING_SECONDS = 15L
    }
}
