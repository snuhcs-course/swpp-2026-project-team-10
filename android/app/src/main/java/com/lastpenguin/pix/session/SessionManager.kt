// AI-generated with Claude Code, 2026-10-01, reviewed by Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.CameraCapabilities
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

    /** Messages from the other phone, already decoded. [incoming] does not replay: a late subscriber misses them. */
    val incoming: Flow<SessionMessage>

    /** Subject: the photographer's `camera.capabilities`, kept so a screen that opens late still gets it. */
    val peerCapabilities: StateFlow<CameraCapabilities?>

    /** Subject: the zoom from the last `camera.state`, for the same reason; null until one arrives. */
    val peerZoom: StateFlow<Float?>

    suspend fun start(entry: SessionEntry, role: Role)

    /** Sent on the realtime or reliable data channel as listed in Design 2.5.1. */
    fun send(message: SessionMessage)

    /** Photographer: pass to CameraController.setFrameSink while connected. */
    fun videoSink(): FrameSink

    suspend fun leave(reason: EndReason = EndReason.LEFT)
}
