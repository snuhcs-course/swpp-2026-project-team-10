// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.ui.session

import android.os.SystemClock
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
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.ConfirmSyncer
import com.lastpenguin.pix.session.DescriptionSyncer
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.GuideSyncer
import com.lastpenguin.pix.session.IcePath
import com.lastpenguin.pix.session.MobileDataNotice
import com.lastpenguin.pix.session.PhotoSyncer
import com.lastpenguin.pix.session.PoseStartStep
import com.lastpenguin.pix.session.PoseSyncer
import com.lastpenguin.pix.session.RemoteAction
import com.lastpenguin.pix.session.RemoteControlHandler
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionManager
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.session.SessionState
import com.lastpenguin.pix.session.ShutterOutcome
import com.lastpenguin.pix.session.TipSyncer
import com.lastpenguin.pix.session.protocol.SessionMessage
import com.lastpenguin.pix.ui.camera.CameraViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Short notices for the photographer's camera screen. */
sealed interface SessionNotice {
    /** The other phone left or dropped: "Joonhyung left" (FR-6.10, FR-6.11). */
    data class PeerGone(val name: String, val reason: EndReason) : SessionNotice

    /** The subject changed the zoom: "Joonhyung set zoom to 2×" (FR-7.4). */
    data class RemoteZoom(val name: String, val ratio: Float) : SessionNotice

    /** The subject set this phone's timer: "Joonhyung set the timer to 3 s", or "… turned the timer off" (#318). */
    data class RemoteTimer(val name: String, val timer: ShutterTimer) : SessionNotice

    /** The subject's shutter took a photo: "Joonhyung took a photo" (FR-7.8, #314). */
    data class RemoteShutter(val name: String) : SessionNotice

    /** The subject's shutter started this phone's timer: "Joonhyung pressed the shutter" (FR-7.8, FR-1.17). */
    data class RemoteShutterPress(val name: String) : SessionNotice

    /** The subject's shutter stopped the timer's countdown: "Joonhyung cancelled the timer" (FR-7.8, FR-1.17). */
    data class RemoteTimerCancel(val name: String) : SessionNotice

    /** The subject started AI poses, and this phone now takes the scene photo: "Joonhyung started AI poses" (#276). */
    data class RemotePoseStart(val name: String) : SessionNotice

    /** The subject moved or resized the guide: "Joonhyung moved the guide" (FR-7.2, FR-7.4). */
    data class RemoteGuideMove(val name: String) : SessionNotice

    /** The subject hid the guide or showed it: "Joonhyung hid the guide" (FR-7.2, FR-7.4). */
    data class RemoteGuideVisible(val name: String, val visible: Boolean) : SessionNotice

    /** The subject set the guide's opacity: "Joonhyung set the guide's opacity to 70%" (FR-7.2, FR-7.4). */
    data class RemoteGuideOpacity(val name: String, val opacity: Float) : SessionNotice

    /**
     * The subject removed the guide: "Joonhyung removed the guide", with *Undo*, which puts [guide] back at [state]
     * (FR-7.2, FR-7.4).
     */
    data class RemoteGuideRemove(val name: String, val guide: ReferenceGuide, val state: GuideState) : SessionNotice

    /** The subject's *Undo* put the guide back: "Joonhyung put the guide back" (FR-7.2, FR-7.4). */
    data class RemoteGuideRestore(val name: String) : SessionNotice

    /** The subject set the brightness: "Joonhyung set brightness to +1"; [index] is the applied exposure index. */
    data class RemoteBrightness(val name: String, val index: Int) : SessionNotice

    /** The subject changed the flash: "Joonhyung turned the flash on". */
    data class RemoteFlash(val name: String, val mode: FlashMode) : SessionNotice

    /** A guide made on the subject's phone is now the guide: "Joonhyung set a pose guide" (FR-6.15, FR-7.4). */
    data class RemoteGuide(val name: String) : SessionNotice

    /**
     * The subject's tap set the focus: "Joonhyung set the focus" (FR-7.7, FR-7.4). Shown for
     * [SessionViewModel.FOCUS_NOTICE_MS], and not for every tap (SessionViewModel.remoteFocus).
     */
    data class RemoteFocus(val name: String) : SessionNotice
}

/**
 * Where the other phone's tap set the focus (FR-7.7), for the focus box here: in fractions of the image as this phone
 * shows it, the photographer's frame on Camera and its mirror on Subject view.
 */
data class RemoteFocus(val x: Float, val y: Float)

/** The subject's notice about a photo from the photographer (FR-6.14), on whichever screen of the session is in front. */
sealed interface PhotoNotice {
    data object Saved : PhotoNotice

    /** "Couldn't save Dongje's photo". */
    data class SaveFailed(val name: String) : PhotoNotice
}

/** Photos of a session with [name] that did not reach the subject's album (FR-6.14). */
data class PhotosNotSent(val name: String, val count: Int)

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
 * and the camera's capabilities are sent once the data channels are up. Guide sync (#9), remote zoom (#10), the
 * photographer's Generate poses, candidates, and Reference confirm on the subject's screens of the same steps
 * (Iter3-P16), and the session's photos (Iter3-P17) hook in at the same place.
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
    private val photoSyncer: PhotoSyncer? = null,
    private val tipSyncer: TipSyncer? = null,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state

    /** The link's delay for the latency indicator (FR-10.3); null until measured and while reconnecting. */
    val latencyMs: StateFlow<Int?> = session.latencyMs

    private val _transitions = MutableSharedFlow<SessionState>(extraBufferCapacity = 16)

    /** Every state change from the moment a screen subscribes, for one-shot navigation. Screens draw from [state]. */
    val transitions: SharedFlow<SessionState> = _transitions.asSharedFlow()

    private val _notices = MutableSharedFlow<SessionNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<SessionNotice> = _notices.asSharedFlow()

    /** Runs while a pinch that ended without its last value is still named ([ZOOMING_LINGER_MS]). */
    private var zoomingLinger: Job? = null

    private val _subjectZooming = MutableStateFlow<String?>(null)

    /**
     * Photographer: the subject's name while their pinch changes the zoom (FR-7.4, #292), from its first step, for
     * Camera's line "… is zooming". Null again once the pinch's last value is applied, just before
     * [SessionNotice.RemoteZoom] says the ratio; a pinch that ends without one stays named [ZOOMING_LINGER_MS]
     * longer, the time its last value may take to be applied.
     */
    val subjectZooming: StateFlow<String?> = _subjectZooming.asStateFlow()

    private val _reconnectingWith = MutableStateFlow<String?>(null)

    /**
     * Photographer: the subject's name while the link that dropped is tried again (FR-6.12, #293), for Camera's line
     * "Reconnecting with …". It has no time of its own: null again once the link is back or the session gave the
     * wait up, which [SessionNotice.PeerGone] then says.
     */
    val reconnectingWith: StateFlow<String?> = _reconnectingWith.asStateFlow()

    private val _poseStartAnswers = Channel<PoseStartStep>(Channel.BUFFERED)

    /** How many times this phone asked for AI poses, the number of its last request; and the request that waits. */
    private var poseRequests = 0
    private var poseRequestWaiting: Int? = null

    /** True once the photographer's phone said that its user is asked to agree, for the request that waits. */
    private var poseRequestAsked = false

    /** The last request the time limit gave up: its answer may still come, kept behind a full buffer or a drop. */
    private var poseRequestGivenUp: Int? = null

    /** Runs while a request waits for the photographer's phone to say anything ([POSE_START_WAIT_MS]). */
    private var poseStartWait: Job? = null

    /**
     * Subject: what became of each situation this phone picked for AI poses (FR-6.15, #276): the photographer's phone
     * began the scene photo, could not, or asks its user to agree first, after which one of the two follows. No word
     * within [POSE_START_WAIT_MS], and nothing that could be asked, count as refused. Subject view says each in a
     * line; buffered, so one that comes under another screen is said when Subject view is back, in the same session.
     */
    val poseStartAnswers: Flow<PoseStartStep> = _poseStartAnswers.receiveAsFlow()

    private val _remoteFocus = MutableSharedFlow<RemoteFocus>(extraBufferCapacity = 16)

    /**
     * Each of the subject's taps to focus (FR-7.7), for the box on Camera. Its notice, [SessionNotice.RemoteFocus],
     * comes with the first tap only: a tap while that notice shows ([FOCUS_NOTICE_MS]) or in the
     * [FOCUS_NOTICE_QUIET_MS] after it makes none, so taps in a row do not keep saying the same line.
     */
    val remoteFocus: SharedFlow<RemoteFocus> = _remoteFocus.asSharedFlow()

    private val _photoNotices = Channel<PhotoNotice>(Channel.UNLIMITED)

    /**
     * Subject: shown by MainActivity, because Subject view may be under another step of the session. Each waits to be
     * read, so one that comes while the activity is stopped is shown when it starts again.
     */
    val photoNotices: Flow<PhotoNotice> = _photoNotices.receiveAsFlow()

    /** Subject: the photographer's shutter, for Subject view's blink; one while no screen listens is not kept. */
    val shutters: Flow<Unit> = photoSyncer?.shutters ?: emptyFlow()

    private val _photosNotSent = Channel<PhotosNotSent>(Channel.UNLIMITED)

    /** Photographer: each waits for the Camera screen to read it, which may be in front only later. */
    val photosNotSent: Flow<PhotosNotSent> = _photosNotSent.receiveAsFlow()

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
     * session*, a room gone while reconnecting, and starting a room clear it, so *See their camera* opens empty (#81).
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

    private val _sendingInvite = MutableStateFlow(false)

    /**
     * An invitation is on its way to the server, so a second tap sends none, and *Invite again* waits. A state, not a
     * result, so a screen that was stopped meanwhile still sees the request end, also one dropped unseen.
     */
    val sendingInvite: StateFlow<Boolean> = _sendingInvite.asStateFlow()

    /** Counts the sessions started here, so an invitation answered after another one started knows it came too late. */
    private var started = 0

    /** Whether the current join is a *Reconnect*; decides what an `Ended` does with [lastCode]. */
    private var reconnecting = false

    private var role: Role? = null
    private var peerName: String? = null
    private var streaming = false

    /** The link dropped and the session goes on: the next Connected resumes it, with the `hello` it began with. */
    private var afterDrop = false

    private var connectedJob: Job? = null

    /** When this session's last "set the focus" notice began, by [clock]; null before the first. */
    private var focusNoticeAt: Long? = null
    private var receiverJob: Job? = null
    private var photoJob: Job? = null
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
     * Photographer: *Invite* on Share my camera, or *Invite again* (FR-6.1–6.3). The server opens the session with this
     * phone's place, which it takes at once, waiting in `Inviting` for the friend. Ignored while a session is open or
     * another invitation is on its way. A session started while it is on its way, as with *Show a code*, keeps this
     * phone, so the invitation is cancelled rather than left to a friend whose *Accept* would find nobody waiting; so
     * is one whose screen has closed meanwhile ([inviteResults]).
     */
    fun invite(friend: PixUser) {
        if (_sendingInvite.value || isOpen(state.value)) return
        _sendingInvite.value = true
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
                    val partner = SessionPartner(friend.userId, friend.displayName)
                    session.start(SessionEntry.Invite(sent.id, sent.place, partner), Role.PHOTOGRAPHER)
                    _inviteResults.emit(InviteResult.Sent)
                }
                .onFailure { error ->
                    val reason = (error as? AccountException)?.error ?: AccountError.UNKNOWN
                    _inviteResults.emit(InviteResult.Failed(friend, reason))
                }
            _sendingInvite.value = false
        }
    }

    /**
     * Photographer: *Cancel invite*, or *Invite sent* closed while waiting (FR-6.2). The phone leaves the session at
     * once, without waiting for the server's answer, so another flow such as *Show a code* can start right away;
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
     * Subject: ends the room this phone waits in with nobody, as after its subject left, which keeps it open for a
     * *Reconnect* (Design 2.4) with nothing on Camera to say so, since this phone is to be in the photo now (#188).
     * Returns once the room is gone, and whether a session can start here: one with someone in it, or on its way,
     * stays, so *Accept* is refused then (FR-6.9).
     */
    suspend fun endWaitingRoom(): Boolean {
        if (state.value is SessionState.Waiting) viewModelScope.launch { session.leave(EndReason.CANCELLED) }.join()
        return !busy
    }

    /** [endWaitingRoom] without waiting for it: *I'll be in the photo*, as See their camera opens. */
    fun leaveWaitingRoom() {
        viewModelScope.launch { endWaitingRoom() }
    }

    /**
     * Subject: *Accept* on Invitation gave this phone its place, which the session takes now (FR-6.1). False, with
     * nothing changed, while another session is open here, which the session would keep instead.
     */
    fun joinInvitation(invitationId: String, place: SessionPlace, partner: SessionPartner): Boolean {
        if (busy) return false
        lastCode = null
        reconnecting = false
        role = Role.SUBJECT
        started++
        viewModelScope.launch { session.start(SessionEntry.Invitation(invitationId, place, partner), Role.SUBJECT) }
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
        // From the state alone, never from what an earlier Connected said: a state flow may never show this reader
        // that Connected, and the name kept from it could then be another session's (#293).
        val dropped = state as? SessionState.Reconnecting
        _reconnectingWith.value = dropped?.peer?.displayName.takeIf { dropped?.role == Role.PHOTOGRAPHER }
        val inSession = state is SessionState.Connecting || connected != null || reconnecting
        if (!inSession) forgetPoseStarts()

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
                    tipSyncer?.startAsReceiver(this)
                    photoSyncer?.startAsReceiver(this)
                    launch { photoSyncer?.received?.collect(::onPhotoReceived) }
                }
            }
        }
        if (!receivingNow && receiverJob != null) {
            receiverJob?.cancel()
            receiverJob = null
        }

        // Unlike the rest it runs through a reconnect, because a photo goes on from where it was (Design 2.6.7).
        val sendingPhotos = role == Role.PHOTOGRAPHER && inSession
        if (sendingPhotos && photoJob == null) {
            photoJob = viewModelScope.launch {
                coroutineScope {
                    photoSyncer?.startAsSender(this)
                    launch {
                        photoSyncer?.sendFailed?.collect {
                            sessionPeer()?.let { name -> _photosNotSent.trySend(PhotosNotSent(name, 1)) }
                        }
                    }
                }
            }
        }
        if (!sendingPhotos && photoJob != null) {
            photoJob?.cancel()
            photoJob = null
            val dropped = photoSyncer?.drop() ?: 0
            // The name now, since another session may start during the wait for the end's own notice to be read.
            val name = peerName
            if (dropped > 0 && name != null) {
                viewModelScope.launch {
                    delay(CameraViewModel.NOTICE_MS)
                    _photosNotSent.trySend(PhotosNotSent(name, dropped))
                }
            }
        }

        // Stopped while reconnecting and started again at Connected, so the subject gets the capabilities, the
        // camera's state, the guide and its tip, Generate poses, the pose set, and the Reference confirm again, whatever
        // a drop lost (Design 2.6.7).
        val liveNow = connected?.role == Role.PHOTOGRAPHER
        // Read for this Connected only: any other state between ends the session, and the next one has its own hello.
        val resumed = afterDrop && connected != null
        afterDrop = reconnecting
        if (liveNow && connectedJob == null) {
            // Another session's first tap to focus is said again; a reconnect is the same session.
            if (!resumed) focusNoticeAt = null
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
                                is RemoteAction.Zoom -> {
                                    // The ratio's line takes the place of the pinch's, unless the next pinch began.
                                    if (remoteControl.zoomHold.holder.value != Role.SUBJECT) noOneZooming()
                                    _notices.tryEmit(SessionNotice.RemoteZoom(name, action.ratio))
                                }

                                is RemoteAction.GuideMove -> _notices.tryEmit(SessionNotice.RemoteGuideMove(name))

                                is RemoteAction.GuideVisible ->
                                    _notices.tryEmit(SessionNotice.RemoteGuideVisible(name, action.visible))

                                is RemoteAction.GuideOpacity ->
                                    _notices.tryEmit(SessionNotice.RemoteGuideOpacity(name, action.opacity))

                                is RemoteAction.GuideRemove ->
                                    _notices.tryEmit(SessionNotice.RemoteGuideRemove(name, action.guide, action.state))

                                is RemoteAction.GuideRestore -> _notices.tryEmit(SessionNotice.RemoteGuideRestore(name))

                                is RemoteAction.Brightness ->
                                    _notices.tryEmit(SessionNotice.RemoteBrightness(name, action.index))

                                is RemoteAction.Flash -> _notices.tryEmit(SessionNotice.RemoteFlash(name, action.mode))

                                is RemoteAction.Timer ->
                                    _notices.tryEmit(SessionNotice.RemoteTimer(name, action.timer))

                                is RemoteAction.Shutter -> _notices.tryEmit(SessionNotice.RemoteShutter(name))

                                is RemoteAction.TimerStart ->
                                    _notices.tryEmit(SessionNotice.RemoteShutterPress(name))

                                is RemoteAction.TimerStop -> _notices.tryEmit(SessionNotice.RemoteTimerCancel(name))

                                is RemoteAction.PoseStart -> _notices.tryEmit(SessionNotice.RemotePoseStart(name))

                                is RemoteAction.Focus -> {
                                    _remoteFocus.tryEmit(RemoteFocus(action.x, action.y))
                                    val now = clock()
                                    val last = focusNoticeAt
                                    if (last == null || now - last >= FOCUS_NOTICE_MS + FOCUS_NOTICE_QUIET_MS) {
                                        focusNoticeAt = now
                                        _notices.tryEmit(SessionNotice.RemoteFocus(name))
                                    }
                                }
                            }
                        }
                    }
                    launch {
                        guideSyncer.adopted.collect {
                            val name = peerName ?: return@collect
                            _notices.tryEmit(SessionNotice.RemoteGuide(name))
                        }
                    }
                    launch {
                        // The subject's pinch holds the zoom from its first step to its end (FR-7.6), and Camera names
                        // them meanwhile (#292). Its end waits for the applied ratio's notice before the name goes.
                        remoteControl.zoomHold.holder.collect { holder ->
                            if (holder == Role.SUBJECT) {
                                zoomingLinger?.cancel()
                                _subjectZooming.value = peerName
                            } else if (_subjectZooming.value != null && zoomingLinger?.isActive != true) {
                                zoomingLinger = launch {
                                    delay(ZOOMING_LINGER_MS)
                                    _subjectZooming.value = null
                                }
                            }
                        }
                    }
                    guideSyncer.startAsSender(this, resumed)
                    poseSyncer?.startAsSender(this)
                    confirmSyncer?.startAsSender(this)
                    descriptionSyncer?.startAsSender(this)
                    tipSyncer?.startAsSender(this)
                }
            }
        }
        if (!liveNow && connectedJob != null) {
            connectedJob?.cancel()
            connectedJob = null
            remoteControl.stop()
            noOneZooming()
        }
    }

    /** The subject's pinch is no longer named on Camera (#292). */
    private fun noOneZooming() {
        zoomingLinger?.cancel()
        zoomingLinger = null
        _subjectZooming.value = null
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

    private suspend fun onPhotoReceived(saved: Boolean) {
        if (saved) {
            _photoNotices.trySend(PhotoNotice.Saved)
        } else {
            sessionPeer()?.let { name -> _photoNotices.trySend(PhotoNotice.SaveFailed(name)) }
        }
    }

    /**
     * The other phone's name, for a photo of this session: a small one can come before this phone's state collector
     * has seen Connected, so [peerName] may be missing or still the last session's.
     */
    private suspend fun sessionPeer(): String? {
        val state = session.state.first { it !is SessionState.Connecting }
        return (state as? SessionState.Connected)?.peer?.displayName ?: peerName
    }

    /** A room waits, an invitation waits, or a session runs: no other session can start on this phone. */
    private fun isOpen(state: SessionState) = state is SessionState.Waiting || state is SessionState.Inviting ||
        state is SessionState.Connecting || isLive(state)

    /**
     * What an end does with the kept code (#81): *Connection lost* keeps it for *Reconnect*; an unknown or expired
     * code keeps it for editing on *Session not found*, unless a reconnect found the room gone; a reconnect that
     * failed otherwise keeps it for another try; every other end, such as *Leave* or the photographer's *End
     * session*, forgets it, so the next *See their camera* starts empty.
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

    /**
     * The Camera screen, while it is in front, takes up the presses of the subject's shutter and says what it did
     * with each (FR-7.8, #314); null once it is not, and a press is then answered as not taken.
     */
    fun setRemoteShutter(taker: ((cancel: Boolean) -> ShutterOutcome)?) {
        remoteControl.shutter = taker
    }

    /**
     * The Camera screen, while it is in front, begins the scene photo for the situation the subject picked (FR-6.15,
     * #276), and says whether it began; null once it is not, and a request is then answered as not begun.
     */
    fun setRemotePoseStarter(starter: ((Situation, (PoseStartStep) -> Unit) -> Unit)?) {
        remoteControl.poseStarter = starter
    }

    /** [starter]'s screen is no longer in front; a screen that came after it keeps its own. */
    fun dropRemotePoseStarter(starter: (Situation, (PoseStartStep) -> Unit) -> Unit) {
        if (remoteControl.poseStarter === starter) remoteControl.poseStarter = null
    }

    /**
     * Subject: asks the photographer's phone to begin AI poses for [situation] (FR-6.15, #276). The photographer takes
     * the scene photo, and the steps after it show on both phones as when the photographer starts them.
     */
    fun askForPoses(situation: Situation) {
        val connected = state.value as? SessionState.Connected
        if (connected?.role != Role.SUBJECT) {
            // Nothing can be asked: the link dropped while the sheet was open.
            _poseStartAnswers.trySend(PoseStartStep.REFUSED)
            return
        }
        if (poseRequestWaiting != null) {
            // One request at a time: a second one would wait behind the first on the photographer's phone, which
            // begins the first and refuses the second, and this phone would say the refusal. The first's answer comes.
            if (poseRequestAsked) _poseStartAnswers.trySend(PoseStartStep.ASKING)
            return
        }
        // Numbered, so only its own answers count: an earlier request's, come late, is not this one's.
        val request = ++poseRequests
        poseRequestWaiting = request
        session.send(SessionMessage.PoseStart(situation.apiName, request))
        poseStartWait?.cancel()
        poseStartWait = viewModelScope.launch {
            delay(POSE_START_WAIT_MS)
            // No word of it in time, not even that the photographer is asked: as far as this phone knows, nothing began.
            if (poseRequestWaiting == request) {
                poseRequestWaiting = null
                poseRequestGivenUp = request
                _poseStartAnswers.trySend(PoseStartStep.REFUSED)
            }
        }
    }

    /**
     * What the photographer's phone says of the request that waits. An answer to the request the limit gave up is
     * heard too, since the photographer's phone acted on it: that its user is asked brings the request back, with
     * "Waiting for … to agree" after the refusal this phone said too soon, and that the poses began is said. Its
     * refusal was said at the limit. Any other request's answer is dropped.
     */
    private fun onPoseStartAnswer(answer: SessionMessage.PoseStartAnswer) {
        if (answer.request != poseRequestWaiting) {
            if (answer.request != poseRequestGivenUp) return
            if (answer.asking) {
                // Only while no later request waits: that one's own answer comes.
                if (poseRequestWaiting != null) return
                poseRequestGivenUp = null
                poseRequestWaiting = answer.request
                poseRequestAsked = true
                _poseStartAnswers.trySend(PoseStartStep.ASKING)
            } else if (answer.started) {
                poseRequestGivenUp = null
                _poseStartAnswers.trySend(PoseStartStep.STARTED)
            }
            return
        }
        poseStartWait?.cancel()
        poseStartWait = null
        if (answer.asking) {
            // The photographer reads the AI notice: the request waits for their answer, however long that takes.
            poseRequestAsked = true
            _poseStartAnswers.trySend(PoseStartStep.ASKING)
            return
        }
        poseRequestWaiting = null
        poseRequestAsked = false
        _poseStartAnswers.trySend(if (answer.started) PoseStartStep.STARTED else PoseStartStep.REFUSED)
    }

    /**
     * The session is over: its request no longer waits, and an answer no screen read is not said in the next session,
     * whose photographer is another.
     */
    private fun forgetPoseStarts() {
        poseStartWait?.cancel()
        poseStartWait = null
        poseRequestWaiting = null
        poseRequestAsked = false
        poseRequestGivenUp = null
        while (_poseStartAnswers.tryReceive().isSuccess) Unit
    }

    /** [taker]'s screen is no longer in front; a screen that came after it keeps its own. */
    fun dropRemoteShutter(taker: (cancel: Boolean) -> ShutterOutcome) {
        if (remoteControl.shutter === taker) remoteControl.shutter = null
    }

    private fun onMessage(message: SessionMessage) {
        if (message is SessionMessage.PoseStartAnswer) {
            onPoseStartAnswer(message)
            return
        }
        if (message !is SessionMessage.Leave) return
        val name = peerName ?: return
        _notices.tryEmit(SessionNotice.PeerGone(name, message.reason))
    }

    companion object {
        /**
         * How long "Joonhyung set the focus" shows. Shorter than the other notices: the focus box beside it already
         * says what happened, and it goes after about a second too.
         */
        const val FOCUS_NOTICE_MS = 1_500L

        /** How long after that notice another tap to focus makes none. */
        const val FOCUS_NOTICE_QUIET_MS = 2_000L

        /**
         * How long a pinch that ended stays named without its last value applied (#292): above the photographer's
         * wait for the camera (`RemoteControlHandler.ECHO_TIMEOUT_MS`), so the line holds until the ratio's notice.
         */
        const val ZOOMING_LINGER_MS = 1_000L

        /**
         * How long the subject's start of AI poses waits for the photographer's phone to say anything (#276). That
         * phone answers at once, also that its user is being asked, so this is the link's time alone.
         */
        const val POSE_START_WAIT_MS = 2_000L

        private const val BACKGROUND_LIMIT_MS = 60_000L

        /** WebRTC's `networkType` of a candidate on a cellular interface. */
        private const val CELLULAR = "cellular"
    }
}
