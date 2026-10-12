// AI-generated with Claude Code, 2026-10-04, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import com.lastpenguin.pix.session.signaling.SignalMessage
import kotlinx.coroutines.flow.SharedFlow

/** What happens on the signaling WebSocket. */
sealed interface SignalEvent {
    data object Opened : SignalEvent

    data class Received(val message: SignalMessage) : SignalEvent

    /** The socket closed. [error] is null when the server or this phone closed it on purpose. */
    data class Closed(val error: Throwable?) : SignalEvent
}

/**
 * WebSocket to the server's signaling hub, `ws://<laptop address>:8000/ws` (Design 2.5.2).
 * [OkHttpSignalingClient] is the real one; tests use a fake. Owner: Real-time (#8).
 */
interface SignalingClient {
    val events: SharedFlow<SignalEvent>

    /** Opens a new socket; an earlier one is closed first. Events for the new socket follow. */
    fun connect(url: String)

    fun send(signal: SignalMessage)

    /** Closes the socket without emitting [SignalEvent.Closed]. */
    fun close()
}
