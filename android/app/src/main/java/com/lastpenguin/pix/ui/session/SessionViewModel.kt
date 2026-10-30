// AI-generated with Claude Code, 2026-10-30, reviewed by Sungmin Jo and Joonhyung Han
package com.lastpenguin.pix.ui.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastpenguin.pix.account.AccountError
import com.lastpenguin.pix.account.AccountException
import com.lastpenguin.pix.account.InvitationManager
import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.account.SentInvitation
import com.lastpenguin.pix.camera.CameraController
import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.IcePath
import com.lastpenguin.pix.session.MobileDataNotice
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.RemoteAction
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Short notices for the photographer's camera screen. */
sealed interface SessionNotice {
    /** The other phone left or dropped: "Junhyeong left" (FR-6.10, FR-6.11). */
    data class PeerGone(val name: String, val reason: EndReason) : SessionNotice

    /** The subject changed the zoom: "Junhyeong set zoom to 2×" (FR-7.4). */
    data class RemoteZoom(val name: String, val ratio: Float) : SessionNotice

    /** The subject moved or resized the guide: "Junhyeong moved the guide" (FR-7.2, FR-7.4). */
    data class RemoteGuideMove(val name: String) : SessionNotice

    /** The subject set the brightness: "Junhyeong set brightness to +1"; [index] is the applied exposure index. */
    data class RemoteBrightness(val name: String, val index: Int) : SessionNotice

    /** The subject changed the flash: "Junhyeong turned the flash on". */
    data class RemoteFlash(val name: String, val mode: FlashMode) : SessionNotice

    /** A guide made on the subject's phone is now the guide: "Junhyeong set a pose guide" (FR-6.15, FR-7.4). */
    data class RemoteGuide(val name: String) : SessionNotice
}

/** What *Invite* did (FR-6.1, FR-6.9): the invitation went out, or why it did not. */
sealed interface InviteResult {
    data object Sent : InviteResult

    data class Failed(val friend: PixUser, val error: AccountError) : InviteResult
}

/**
 * Starts, joins, and ends a session, with a room code or an invitation (R&S 6.5–6.7, Design 2.4, 2.6.6). Shared by
 * the session screens and the Camera screen's live badge (activity scope).
 *
 * On the photographer's phone, camera frames go to the session from the moment a subject starts connecting,
 * and the camera's capabilities are sent once the data channels are up. Guide sync (#9), remote zoom (#10), and the
 * photographer's Generate poses, candidates, and Reference confirm on the subject's screens of the same steps
 * (Iter3-P16) hook in at the same place.
 *
 * Owner: Real-time (#8), with guide sync (#9) and remote zoom (#10).
 */
class SessionViewModel(
    private val session: SessionManager,
    private val camera: CameraController,
    private val guideSyncer: GuideSyncer,
    private val remoteControl: RemoteControlHandler,
    private val mobileDataNotice: MobileDataNotice,
    private val invitations: InvitationManager,
    private val poseSyncer: PoseSyncer? = null,
    private val confirmSyncer: ConfirmSyncer? = null,
    private val descriptionSyncer: DescriptionSyncer? = null,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state

    private val _transitions = MutableSharedFlow<SessionState>(extraBufferCapacity = 16)

    /** Every state change from the moment a screen subscribes, for one-shot navigation. Screens draw from [state]. */
    val transitions: SharedFlow<SessionState> = _transitions.asSharedFlow()

    private val _notices = MutableSharedFlow<SessionNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<SessionNotice> = _notices.asSharedFlow()

    /** Subject: each guide from this phone that did not reach the photographer, for "Couldn't send the guide". */
    val guideOfferFailed: Flow<String> = guideSyncer.offerFailed

    private val _mobileDataPending = MutableStateFlow(false)

    /**
     * True while "The live view uses mobile data" waits to be shown (FR-6.13, Design 2.6.7): the Camera screen or
     * Subject view shows it and calls [mobileDataNoticeShown], and only that remembers it, so a notice that no screen
     * showed is never lost. A session that ends first drops it.
     */
    val mobileDataPending: StateFlow<Boolean> = _mobileDataPending.asStateFlow()

    /** Said in this process; the stored flag may still be on its way. */
    private var mobileDataSaid = false

    /**
     * The code the subject entered last, kept only where it is reused: *Session not found* offers it for editing
     * (FR-8.3), and *Reconnect* on *Connection lost* joins it again (FR-6.11). *Leave*, the photographer's *End
     * session*, a room gone while reconnecting, and starting a room clear it, so *Join with code* opens empty (#81).
     */
    var lastCode: String? = null
        private set

    private val _invitation = MutableStateFlow<SentInvitation?>(null)

    /**
     * Photographer: the invitation *Invite sent* shows (FR-6.2). It stays after the session ends, so *Invite not
     * accepted* names the friend and *Invite again* knows whom to invite (FR-6.3).
     */
    val invitation: StateFlow<SentInvitation?> = _invitation.asStateFlow()

    private val _inviteResults = MutableSharedFlow<InviteResult>(extraBufferCapacity = 1)

    /**
     * What each *Invite* did, for the screen that asked while it is open: Friends or *Invite not accepted*. Only an open
     * one can show *Invite sent*, so an invitation the server makes once neither is, as when the photographer went back
     * to Camera before the answer, is cancelled rather than left waiting unseen.
     */
    val inviteResults: Flow<InviteResult> = _inviteResults.asSharedFlow()

    /** An invitation is on its way to the server, so a second tap sends none. */
    private var sendingInvite = false

    /** Counts the sessions started here, so an invitation answered after another one started knows it came too late. */
    private var started = 0

    /** Whether the current join is a *Reconnect*; decides what an `Ended` does with [lastCode]. */
    private var reconnecting = false

    private var role: Role? = null
    private var peerName: String? = null
    private var streaming = false
    private var connectedJob: Job? = null
    private var receiverJob: Job? = null
    private var backgroundJob: Job? = null

    /** The whole app in the background: the camera is released by its lifecycle, so the video pauses. */
    private val processObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> onAppBackground(true)
            Lifecycle.Event.ON_START -> onAppBackground(false)
            else -> Unit
        }
    }

    init {
        viewModelScope.launch { session.state.collect { onState(it) } }
        viewModelScope.launch { session.incoming.collect { onMessage(it) } }
        viewModelScope.launch { session.path.collect { onPath(it) } }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
    }

    /** Photographer: *Shoot together* → a new room code, unless a room is already open or live. */
    fun startRoom() {
        val current = state.value
        if (isOpen(current)) return
        lastCode = null // This phone is the photographer now; a code it joined earlier is stale.
        role = Role.PHOTOGRAPHER
        started++
        viewModelScope.launch { session.start(SessionEntry.NewRoom, Role.PHOTOGRAPHER) }
    }

    /** Photographer: *Cancel* on Room code. The code expires. */
    fun cancelRoom() {
        viewModelScope.launch { session.leave(EndReason.CANCELLED) }
    }

    /**
     * Photographer: *Invite* on Friends, or *Invite again* (FR-6.1–6.3). The server opens the session with this phone's
     * place, which it takes at once, waiting in `Inviting` for the friend. Ignored while a session is open or another
     * invitation is on its way. A session started while it is on its way, as with *Use a room code*, keeps this phone,
     * so the invitation is cancelled rather than left to a friend whose *Accept* would find nobody waiting; so is one
     * whose screen has closed meanwhile ([inviteResults]).
     */
    fun invite(friend: PixUser) {
        if (sendingInvite || isOpen(state.value)) return
        sendingInvite = true
        val startedBefore = started
        viewModelScope.launch {
            invitations.invite(friend.userId)
                .onSuccess { sent ->
                    if (started != startedBefore || _inviteResults.subscriptionCount.value == 0) {
                        invitations.cancel(sent.id)
                        return@onSuccess
                    }
                    lastCode = null
                    role = Role.PHOTOGRAPHER
                    started++
                    _invitation.value = sent
                    session.start(SessionEntry.Invite(sent.id, sent.place), Role.PHOTOGRAPHER)
                    _inviteResults.emit(InviteResult.Sent)
                }
                .onFailure { error ->
                    val reason = (error as? AccountException)?.error ?: AccountError.UNKNOWN
                    _inviteResults.emit(InviteResult.Failed(friend, reason))
                }
            sendingInvite = false
        }
    }

    /**
     * Photographer: *Cancel invite*, or *Invite sent* closed while waiting (FR-6.2). The phone leaves the session at
     * once, without waiting for the server's answer, so another flow such as *Use a room code* can start right away;
     * the cancel goes to the server alongside, and the friend's *Invitation* says it is no longer available either way
     * (FR-6.4). Once the friend has accepted, the invitation is answered, so only the session ends, also while the
     * phones are still connecting.
     */
    fun cancelInvite() {
        val sent = _invitation.value ?: return
        when (state.value) {
            is SessionState.Inviting -> {
                viewModelScope.launch { session.leave(EndReason.CANCELLED) }
                viewModelScope.launch { invitations.cancel(sent.id) }
            }

            is SessionState.Connecting -> viewModelScope.launch { session.leave(EndReason.CANCELLED) }

            else -> Unit
        }
    }

    /** A room or an invitation waits, or a session runs, on this phone, so *Accept* on Invitation must wait (FR-6.9). */
    val busy: Boolean
        get() = isOpen(state.value)

    /**
     * Subject: *Accept* on Invitation gave this phone its place, which the session takes now (FR-6.1). False, with
     * nothing changed, while another session is open here, which the session would keep instead.
     */
    fun joinInvitation(invitationId: String, place: SessionPlace): Boolean {
        if (busy) return false
        lastCode = null
        reconnecting = false
        role = Role.SUBJECT
        started++
        viewModelScope.launch { session.start(SessionEntry.Invitation(invitationId, place), Role.SUBJECT) }
        return true
    }

    /** Subject: *Join* with 6 digits. */
    fun joinRoom(code: String) = join(SessionEntry.RoomCode(code), code, reconnecting = false)

    /** *End session* (photographer) or *Leave* (subject). The subject leaves the room for good, so its code goes too. */
    fun leave() {
        lastCode = null
        viewModelScope.launch { session.leave(EndReason.LEFT) }
    }

    /**
     * *Reconnect* on Connection lost: takes this phone's place in the same session again, or joins the same code again
     * when it has no rejoin token (FR-6.11, Design 2.6.7).
     */
    fun reconnect() {
        lastCode?.let { join(SessionEntry.Rejoin(it), it, reconnecting = true) }
    }

    private fun join(entry: SessionEntry, code: String, reconnecting: Boolean) {
        lastCode = code
        this.reconnecting = reconnecting
        role = Role.SUBJECT
        started++
        viewModelScope.launch { session.start(entry, Role.SUBJECT) }
    }

    private fun onState(state: SessionState) {
        _transitions.tryEmit(state)
        if (state is SessionState.Ended) onEnded(state.reason)
        // A short drop keeps a notice that waits; the session it was for goes on.
        val reconnecting = state is SessionState.Reconnecting
        if (state !is SessionState.Connected && !reconnecting) _mobileDataPending.value = false
        val connected = state as? SessionState.Connected
        if (connected != null) peerName = connected.peer.displayName
        val inSession = state is SessionState.Connecting || connected != null || reconnecting

        // Frames start while the peer connection is still being set up, so the first video arrives sooner, and go on
        // while it reconnects, so the video comes back with the link (Design 2.6.7).
        val streamingNow = role == Role.PHOTOGRAPHER && inSession
        if (streamingNow && !streaming) camera.setFrameSink(session.videoSink())
        if (!streamingNow && streaming) camera.setFrameSink(null)
        streaming = streamingNow

        // The subject listens for the guide from the moment it joins: the photographer sends the image as soon as
        // its own side is connected, which can be before this phone's Connected, and incoming does not replay. It
        // keeps listening while the link reconnects, so the guide stays.
        val receivingNow = role == Role.SUBJECT && inSession
        if (receivingNow && receiverJob == null) {
            receiverJob = viewModelScope.launch {
                coroutineScope {
                    guideSyncer.startAsReceiver(this)
                    poseSyncer?.startAsReceiver(this)
                    confirmSyncer?.startAsReceiver(this)
                    descriptionSyncer?.startAsReceiver(this)
                }
            }
        }
        if (!receivingNow && receiverJob != null) {
            receiverJob?.cancel()
            receiverJob = null
        }

        // Stopped while reconnecting and started again at Connected, so the subject gets the capabilities, the
        // camera's state, the guide, Generate poses, the pose set, and the Reference confirm again, whatever a drop
        // lost (Design 2.6.7).
        val liveNow = connected?.role == Role.PHOTOGRAPHER
        if (liveNow && connectedJob == null) {
            connectedJob = viewModelScope.launch {
                coroutineScope {
                    launch {
                        camera.capabilities.filterNotNull().collect { session.send(SessionMessage.Capabilities(it)) }
                    }
                    remoteControl.start(this)
                    launch { session.incoming.collect { remoteControl.handle(it) } }
                    launch {
                        remoteControl.actions.collect { action ->
                            val name = peerName ?: return@collect
                            when (action) {
                                is RemoteAction.Zoom -> _notices.tryEmit(SessionNotice.RemoteZoom(name, action.ratio))

                                is RemoteAction.GuideMove -> _notices.tryEmit(SessionNotice.RemoteGuideMove(name))

                                is RemoteAction.Brightness ->
                                    _notices.tryEmit(SessionNotice.RemoteBrightness(name, action.index))

                                is RemoteAction.Flash -> _notices.tryEmit(SessionNotice.RemoteFlash(name, action.mode))
                            }
                        }
                    }
                    launch {
                        guideSyncer.adopted.collect {
                            val name = peerName ?: return@collect
                            _notices.tryEmit(SessionNotice.RemoteGuide(name))
                        }
                    }
                    guideSyncer.startAsSender(this)
                    poseSyncer?.startAsSender(this)
                    confirmSyncer?.startAsSender(this)
                    descriptionSyncer?.startAsSender(this)
                }
            }
        }
        if (!liveNow && connectedJob != null) {
            connectedJob?.cancel()
            connectedJob = null
            remoteControl.stop()
        }
    }

    /**
     * A live session survives the photographer's app being in the background for a while, with the subject's video
     * paused; after [BACKGROUND_LIMIT_MS] the photographer ends it (Design 2.8, Lifecycle).
     */
    private fun onAppBackground(background: Boolean) {
        backgroundJob?.cancel()
        backgroundJob = null
        if (!background || role != Role.PHOTOGRAPHER || !isLive(state.value)) return
        // The deadline holds through a drop: a session reconnecting, or back from it, still ends on time.
        backgroundJob = viewModelScope.launch {
            delay(BACKGROUND_LIMIT_MS)
            if (isLive(state.value)) session.leave(EndReason.LEFT)
        }
    }

    private fun isLive(state: SessionState) = state is SessionState.Connected || state is SessionState.Reconnecting

    /** A room waits, an invitation waits, or a session runs: no other session can start on this phone. */
    private fun isOpen(state: SessionState) = state is SessionState.Waiting || state is SessionState.Inviting ||
        state is SessionState.Connecting || isLive(state)

    /**
     * What an end does with the kept code (#81): *Connection lost* keeps it for *Reconnect*; an unknown or expired
     * code keeps it for editing on *Session not found*, unless a reconnect found the room gone; a reconnect that
     * failed otherwise keeps it for another try; every other end, such as *Leave* or the photographer's *End
     * session*, forgets it, so the next *Join with code* starts empty.
     */
    private fun onEnded(reason: EndReason) {
        val keep = when (reason) {
            EndReason.CONNECTION_LOST -> true
            EndReason.NOT_FOUND, EndReason.EXPIRED -> !reconnecting
            EndReason.LEFT, EndReason.PEER_LEFT, EndReason.CANCELLED -> false
            else -> reconnecting
        }
        if (!keep) lastCode = null
        reconnecting = false
    }

    /** A session's video goes over this phone's cellular network, as its selected candidate pair says (FR-6.13). */
    private suspend fun onPath(path: IcePath?) {
        if (path?.network != CELLULAR || mobileDataSaid || _mobileDataPending.value) return
        if (mobileDataNotice.wasShown()) return
        if (state.value is SessionState.Connected) _mobileDataPending.value = true
    }

    /** The Camera screen or Subject view showed the mobile-data notice; from now on it is not shown again. */
    fun mobileDataNoticeShown() {
        if (!_mobileDataPending.value) return
        _mobileDataPending.value = false
        mobileDataSaid = true
        viewModelScope.launch { mobileDataNotice.markShown() }
    }

    private fun onMessage(message: SessionMessage) {
        if (message !is SessionMessage.Leave) return
        val name = peerName ?: return
        _notices.tryEmit(SessionNotice.PeerGone(name, message.reason))
    }

    private companion object {
        const val BACKGROUND_LIMIT_MS = 60_000L

        /** WebRTC's `networkType` of a candidate on a cellular interface. */
        const val CELLULAR = "cellular"
    }
}
