package com.lastpenguin.pix.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** The other phone left or dropped: "Junhyeong left" on the photographer's camera (FR-6.10, FR-6.11). */
data class PeerNotice(val name: String, val reason: EndReason)

/**
 * Starts, joins, and ends a room-code session (R&S 6.7, Design 2.4). Shared by the session screens
 * and the Camera screen's live badge (activity scope).
 *
 * On the photographer's phone, camera frames go to the session from the moment a subject starts connecting,
 * and the camera's capabilities are sent once the data channels are up. Guide sync (#9) and remote zoom (#10)
 * hook in at the same place.
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

    private val _transitions = MutableSharedFlow<SessionState>(extraBufferCapacity = 16)

    /** Every state change from the moment a screen subscribes, for one-shot navigation. Screens draw from [state]. */
    val transitions: SharedFlow<SessionState> = _transitions.asSharedFlow()

    private val _notices = MutableSharedFlow<PeerNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<PeerNotice> = _notices.asSharedFlow()

    /** The code the subject entered last. Session not found keeps it, and Reconnect reuses it (FR-8.3, FR-6.11). */
    var lastCode: String? = null
        private set

    private var role: Role? = null
    private var peerName: String? = null
    private var streaming = false
    private var connectedJob: Job? = null

    init {
        viewModelScope.launch { session.state.collect { onState(it) } }
        viewModelScope.launch { session.incoming.collect { onMessage(it) } }
    }

    /** Photographer: *Shoot together* → a new room code, unless a room is already open or live. */
    fun startRoom() {
        val current = state.value
        if (current is SessionState.Waiting || current is SessionState.Connecting ||
            current is SessionState.Connected
        ) {
            return
        }
        role = Role.PHOTOGRAPHER
        viewModelScope.launch { session.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER) }
    }

    /** Photographer: *Cancel* on Room code. The code expires. */
    fun cancelRoom() {
        viewModelScope.launch { session.leave(EndReason.CANCELLED) }
    }

    /** Subject: *Join* with 6 digits. */
    fun joinRoom(code: String) {
        lastCode = code
        role = Role.SUBJECT
        viewModelScope.launch { session.start(SessionEntry.RoomCode(code), Role.SUBJECT) }
    }

    /** *End session* (photographer) or *Leave* (subject). */
    fun leave() {
        viewModelScope.launch { session.leave(EndReason.LEFT) }
    }

    /** *Reconnect* on Connection lost: joins the same code again (FR-6.11). */
    fun reconnect() {
        lastCode?.let { joinRoom(it) }
    }

    private fun onState(state: SessionState) {
        _transitions.tryEmit(state)
        val connected = state as? SessionState.Connected
        if (connected != null) peerName = connected.peer.displayName

        // Frames start while the peer connection is still being set up, so the first video arrives sooner.
        val streamingNow = role == Role.PHOTOGRAPHER && (state is SessionState.Connecting || connected != null)
        if (streamingNow && !streaming) camera.setFrameSink(session.videoSink())
        if (!streamingNow && streaming) camera.setFrameSink(null)
        streaming = streamingNow

        val liveNow = connected?.role == Role.PHOTOGRAPHER
        if (liveNow && connectedJob == null) {
            connectedJob = viewModelScope.launch {
                camera.capabilities.filterNotNull().collect { session.send(SessionMessage.Capabilities(it)) }
            }
            // TODO(#9): guideSyncer.startAsSender(...) while connected.
            // TODO(#10): session.incoming → remoteControl.handle while connected.
        }
        if (!liveNow) {
            connectedJob?.cancel()
            connectedJob = null
        }
    }

    private fun onMessage(message: SessionMessage) {
        if (message !is SessionMessage.Leave) return
        val name = peerName ?: return
        _notices.tryEmit(PeerNotice(name, message.reason))
    }
}
