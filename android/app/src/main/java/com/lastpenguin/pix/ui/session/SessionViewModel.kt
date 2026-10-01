package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Starts, joins, and ends a room-code session (R&S 6.7, Design 2.4). Shared by the session screens
 * and the Camera screen's live badge (activity scope).
 *
 * When the state becomes Connected:
 * - photographer: camera.setFrameSink(session.videoSink()), guideSyncer.startAsSender, remote zoom through remoteControl
 * - subject: guideSyncer.startAsReceiver
 *
 * Owner: Real-time (#8), with guide sync (#9) and remote zoom (#10).
 */
class SessionViewModel(
    private val session: SessionManager,
    private val camera: CameraController,
    private val guideSyncer: GuideSyncer,
    private val remoteControl: RemoteControlHandler,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state

    /** Photographer: *Shoot together* → a new room code. */
    fun startRoom() {
        // TODO(#8): session.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER).
    }

    /** Photographer: *Cancel* on Room code. The code expires. */
    fun cancelRoom() {
        // TODO(#8): session.leave(EndReason.CANCELLED).
    }

    /** Subject: *Join* with 6 digits. */
    fun joinRoom(code: String) {
        // TODO(#8): session.start(SessionEntry.RoomCode(code), Role.SUBJECT).
    }

    /** *End session* (photographer) or *Leave* (subject). */
    fun leave() {
        // TODO(#8): session.leave().
    }

    /** *Reconnect* on Connection lost (FR-6.11). */
    fun reconnect() {
        // TODO(#8)
    }
}
