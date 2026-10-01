package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.FrameSink
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One photographer–subject session over WebRTC (Design 2.1).
 * Owner: Real-time.
 */
interface SessionManager {
    val state: StateFlow<SessionState>

    /** Messages from the other phone, already decoded. */
    val incoming: Flow<SessionMessage>

    suspend fun start(entry: SessionEntry, role: Role)

    /** Sent on the realtime or reliable data channel as listed in Design 2.5.1. */
    fun send(message: SessionMessage)

    /** Photographer: pass to CameraController.setFrameSink while connected. */
    fun videoSink(): FrameSink

    suspend fun leave(reason: EndReason = EndReason.LEFT)
}
