package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.signaling.SignalMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What happens on the signaling WebSocket. */
sealed interface SignalEvent {
    data object Opened : SignalEvent

    data class Received(val message: SignalMessage) : SignalEvent

    data class Closed(val error: Throwable?) : SignalEvent
}

/**
 * WebSocket to the server's signaling hub, `ws://<laptop address>:8000/ws` (Design 2.5.2).
 * Owner: Real-time (#8).
 */
class SignalingClient {

    private val _events = MutableSharedFlow<SignalEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalEvent> = _events.asSharedFlow()

    fun connect(url: String) {
        TODO("#8: OkHttp WebSocket")
    }

    fun send(signal: SignalMessage) {
        TODO("#8")
    }

    fun close() {
        TODO("#8")
    }
}
