// AI-generated with Claude Code, 2026-10-30, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.util.Log
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.PoseSetStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The subject's *Use this pose* on the candidate [poseId] of the set [setId] (Design 2.5.1, `pose.pick`). */
data class SubjectPick(val setId: String, val poseId: String)

/**
 * What pose generation tells the subject's phone about its set (Design 2.6.5): the photographer's side of
 * [PoseSyncer], which GenerationViewModel calls as the set goes on. Calls for a set that is not the open one, or
 * that already ended, do nothing, so each set ends once.
 */
interface PoseShare {
    /** The subject's picks while the set is PICKING, in the order they came; the first unchosen one decides. */
    val picks: Flow<SubjectPick>

    /** *Generate 4 poses* or *Skip* started the set [setId], in place of any other. */
    fun open(setId: String)

    /** The candidate [poseId] of [setId] arrived. */
    fun candidate(setId: String, poseId: String, image: Bitmap)

    /** *Pick a pose* opened with the created candidates [poseIds], in its order. */
    fun picking(setId: String, poseIds: List<String>)

    /** The first *Use this pose*, [by] whom. */
    fun chosen(setId: String, poseId: String, by: Role)

    /** The set ended without a choice: Back, *Try other poses*, *Cancel*, or no candidate. */
    fun close(setId: String)

    /** For a phone with nothing to share, such as the ViewModel's tests. */
    object None : PoseShare {
        override val picks: Flow<SubjectPick> = emptyFlow()

        override fun open(setId: String) = Unit

        override fun candidate(setId: String, poseId: String, image: Bitmap) = Unit

        override fun picking(setId: String, poseIds: List<String>) = Unit

        override fun chosen(setId: String, poseId: String, by: Role) = Unit

        override fun close(setId: String) = Unit
    }
}

/**
 * The photographer's open set as the subject has it (Design 2.6.5): GENERATING or PICKING, with the copies that
 * arrived. An ended set is not kept.
 */
data class MirrorPoseSet(
    val setId: String,
    val step: PoseSetStep,
    /** With PICKING: the photographer's created candidates, in its order. */
    val poseIds: List<String> = emptyList(),
    val candidates: Map<String, Bitmap> = emptyMap(),
) {
    /** What the subject's *Pick a pose* shows: the candidates of [poseIds] whose copy is here, in their order. */
    val ready: List<Pair<String, Bitmap>>
        get() = poseIds.mapNotNull { id -> candidates[id]?.let { id to it } }
}

/**
 * How a set the subject knew ended: [step] is CHOSEN, with [by] whose *Use this pose* it was, or CLOSED. [wasPicking]
 * says whether the subject's *Pick a pose* had the set, so whether a notice is worth showing.
 */
data class PoseSetEnd(val setId: String, val step: PoseSetStep, val by: Role?, val wasPicking: Boolean)

/**
 * Shows the photographer's candidates on the subject's *Pick a pose* (Design 2.5.1: pose.step, pose.candidate.*,
 * pose.pick; Design 2.6.5, *Pick a pose on both phones*).
 *
 * Photographer ([PoseShare], [startAsSender]): keeps the open set as GenerationViewModel reports it and, while the
 * session is connected, sends what the subject does not have yet, in order: the step, each candidate's copy, one at
 * a time, then PICKING or the set's end. A sender started again after the link reconnects (Design 2.6.7) sends the
 * open set again, or only the end of a set that ended during the drop, which closes it on a subject that still holds
 * it. The subject's picks of the PICKING set go to [picks]; deciding is GenerationViewModel's, which calls [chosen].
 *
 * Subject ([startAsReceiver]): keeps the set in [mirror], in memory only, through a reconnect, and drops it at its end,
 * at another set, and when the session ends, saying in [ended] how it ended. [pick] sends `pose.pick` and waits
 * [PICK_TIMEOUT_MS] for a step that answers it; otherwise [pickFailed].
 * Owner: Shared steps (Iter3-P16).
 */
class PoseSyncer(
    private val session: SessionManager,
    private val codec: PoseCandidateCodec,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PoseShare {

    // ---- Photographer -----------------------------------------------------------------------------------------

    /** The photographer's last set as GenerationViewModel reported it; an ended one stays, without its candidates. */
    private data class SharedSet(
        val setId: String,
        val step: PoseSetStep,
        /** In the order they arrived. */
        val candidates: List<Pair<String, Bitmap>> = emptyList(),
        val poseIds: List<String> = emptyList(),
        val chosen: String? = null,
        val by: Role? = null,
    ) {
        val open: Boolean get() = step == PoseSetStep.GENERATING || step == PoseSetStep.PICKING
    }

    private val shared = MutableStateFlow<SharedSet?>(null)

    private val subjectPicks = Channel<SubjectPick>(Channel.BUFFERED)
    override val picks: Flow<SubjectPick> = subjectPicks.receiveAsFlow()

    override fun open(setId: String) {
        shared.value = SharedSet(setId, PoseSetStep.GENERATING)
    }

    override fun candidate(setId: String, poseId: String, image: Bitmap) {
        shared.update { set ->
            if (set?.setId == setId && set.step == PoseSetStep.GENERATING) {
                set.copy(candidates = set.candidates + (poseId to image))
            } else {
                set
            }
        }
    }

    override fun picking(setId: String, poseIds: List<String>) {
        shared.update { set ->
            if (set?.setId == setId && set.step == PoseSetStep.GENERATING) {
                set.copy(step = PoseSetStep.PICKING, poseIds = poseIds)
            } else {
                set
            }
        }
    }

    override fun chosen(setId: String, poseId: String, by: Role) {
        shared.update { set ->
            if (set?.setId == setId && set.step == PoseSetStep.PICKING) {
                set.copy(step = PoseSetStep.CHOSEN, candidates = emptyList(), chosen = poseId, by = by)
            } else {
                set
            }
        }
    }

    override fun close(setId: String) {
        shared.update { set ->
            if (set?.setId == setId && set.open) set.copy(step = PoseSetStep.CLOSED, candidates = emptyList()) else set
        }
    }

    /** Photographer: sends the open set while [scope] is active, and passes the subject's picks on. */
    fun startAsSender(scope: CoroutineScope) {
        scope.launch {
            // What this subject has of the set it heard of last; a new sender (a reconnect) starts from nothing.
            var announced: String? = null
            var sentStep: PoseSetStep? = null
            val sent = mutableSetOf<String>()

            // The copies that went out; PICKING names only these, so the subject is never told of one it cannot have.
            // One that names none still says the photographer is picking; the subject then opens no Pick a pose.
            val delivered = mutableSetOf<String>()
            shared.collect { set ->
                if (set == null) return@collect
                if (set.setId != announced) {
                    announced = set.setId
                    sent.clear()
                    delivered.clear()
                    // A set that ended before this sender heard of it goes as its end alone: a subject that held it
                    // through a reconnect closes it, and any other drops the set it holds or has nothing to drop.
                    val step = if (set.open) PoseSetStep.GENERATING else set.step
                    send(SessionMessage.PoseStep(set.setId, step, poseId = set.chosen, by = set.by))
                    sentStep = step
                }
                for ((poseId, image) in set.candidates) {
                    // A set that ended or was replaced while an earlier copy was encoded sends no more of them.
                    if (shared.value?.let { it.setId == set.setId && it.open } != true) break
                    if (!sent.add(poseId)) continue
                    if (sendCandidate(set.setId, poseId, image)) delivered += poseId
                }
                // A set that moved on while a copy was encoded sends the step it is at now, from the newer value,
                // never this one's: a PICKING sent for a set already closed would open the subject's Pick a pose.
                val now = shared.value
                if (now?.setId != set.setId || now.step != set.step) return@collect
                val ended = sentStep == PoseSetStep.CHOSEN || sentStep == PoseSetStep.CLOSED
                when (set.step) {
                    PoseSetStep.GENERATING -> Unit

                    PoseSetStep.PICKING -> if (sentStep == PoseSetStep.GENERATING) {
                        val poseIds = set.poseIds.filter { it in delivered }
                        send(SessionMessage.PoseStep(set.setId, PoseSetStep.PICKING, poseIds = poseIds))
                        sentStep = PoseSetStep.PICKING
                    }

                    PoseSetStep.CHOSEN, PoseSetStep.CLOSED -> if (!ended) {
                        send(SessionMessage.PoseStep(set.setId, set.step, poseId = set.chosen, by = set.by))
                        sentStep = set.step
                    }
                }
            }
        }
        scope.launch {
            session.incoming.collect { message ->
                if (message !is SessionMessage.PosePick) return@collect
                val set = shared.value
                // Only the set the photographer is picking from; the step already sent answers any other pick.
                if (set?.setId == message.setId && set.step == PoseSetStep.PICKING) {
                    subjectPicks.trySend(SubjectPick(message.setId, message.poseId))
                }
            }
        }
    }

    /** False for a candidate whose copy could not be made, for example one that would not fit the subject's limit. */
    private suspend fun sendCandidate(setId: String, poseId: String, image: Bitmap): Boolean {
        val copy = withContext(dispatcher) { codec.encode(poseId, image) }
        if (copy == null) {
            Log.w(TAG, "Could not encode candidate $poseId")
            return false
        }
        session.send(copy.begin(setId))
        for (index in copy.chunks.indices) session.send(copy.chunk(setId, index))
        session.send(copy.end(setId))
        Timings.mark("pose.sent", "candidate $poseId ${copy.bytes.size} B in ${copy.chunks.size} chunks")
        return true
    }

    private fun send(step: SessionMessage.PoseStep) {
        session.send(step)
        val detail = step.poseId?.let { " $it by ${step.by}" } ?: ""
        Timings.mark("pose.sent", "${step.step.name.lowercase()} ${step.setId}$detail")
    }

    // ---- Subject ----------------------------------------------------------------------------------------------

    private val _mirror = MutableStateFlow<MirrorPoseSet?>(null)

    /** Subject: the photographer's open set; null when there is none. */
    val mirror: StateFlow<MirrorPoseSet?> = _mirror.asStateFlow()

    private val _pending = MutableStateFlow<SubjectPick?>(null)

    /** Subject: the pick no step has answered yet, while *Pick a pose* waits for the photographer. */
    val pending: StateFlow<SubjectPick?> = _pending.asStateFlow()

    private val endings = Channel<PoseSetEnd>(Channel.BUFFERED)

    /** Subject: each set that ended, for the notice; kept until a screen reads it. */
    val ended: Flow<PoseSetEnd> = endings.receiveAsFlow()

    private val failures = Channel<String>(Channel.BUFFERED)

    /** Subject: each pick that no step answered within [PICK_TIMEOUT_MS], or that could not be sent. */
    val pickFailed: Flow<String> = failures.receiveAsFlow()

    /** Subject: the receiver's scope for this session, where [pick]'s deadline runs. */
    private var receiver: CoroutineScope? = null
    private var deadline: Job? = null

    /** Subject: keeps the photographer's set in [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        receiver = scope
        scope.launch {
            var assembler: PoseCandidateAssembler? = null
            try {
                session.incoming.collect { message ->
                    when (message) {
                        is SessionMessage.PoseStep -> onStep(message)

                        is SessionMessage.PoseCandidateBegin -> {
                            // Only for the set being shown; a copy of an ended or replaced set is let go, and so is
                            // one this phone holds already, which the photographer sends again after a reconnect.
                            val held = _mirror.value
                            val shown = held?.setId == message.setId && message.poseId !in held.candidates
                            assembler = if (shown) PoseCandidateAssembler.start(message) else null
                            if (shown && assembler == null) {
                                Log.w(TAG, "Refused candidate ${message.poseId}: ${message.bytes} B")
                            }
                        }

                        is SessionMessage.PoseCandidateChunk -> {
                            val current = assembler ?: return@collect
                            if (!current.add(message)) {
                                Log.w(TAG, "Dropped candidate ${message.poseId}: chunk ${message.index} out of order")
                                assembler = null
                            }
                        }

                        is SessionMessage.PoseCandidateEnd -> {
                            val current = assembler ?: return@collect
                            assembler = null
                            receive(current, message)
                        }

                        else -> Unit
                    }
                }
            } finally {
                // The set goes with the session. It stays while the link reconnects, which the receiver runs through.
                if (receiver === scope) {
                    receiver = null
                    deadline?.cancel()
                    _pending.value = null
                    _mirror.value = null
                }
            }
        }
    }

    private suspend fun receive(assembler: PoseCandidateAssembler, end: SessionMessage.PoseCandidateEnd) {
        val begin = assembler.begin
        val bytes = assembler.finish(end)
        if (bytes == null) {
            Log.w(TAG, "Dropped candidate ${begin.poseId}: size or CRC mismatch")
            return
        }
        val image = withContext(dispatcher) { codec.decode(begin.format, bytes) }
        if (image == null) {
            Log.w(TAG, "Dropped candidate ${begin.poseId}: not a ${begin.format} image")
            return
        }
        _mirror.update { set ->
            if (set?.setId == begin.setId) set.copy(candidates = set.candidates + (begin.poseId to image)) else set
        }
        Timings.mark("pose.applied", "candidate ${begin.poseId} ${bytes.size} B")
    }

    private fun onStep(message: SessionMessage.PoseStep) {
        val current = _mirror.value
        when (message.step) {
            PoseSetStep.GENERATING -> if (current?.setId != message.setId) {
                answer(null)
                _mirror.value = MirrorPoseSet(message.setId, PoseSetStep.GENERATING)
            }

            PoseSetStep.PICKING -> if (current?.setId == message.setId) {
                _mirror.value = current.copy(step = PoseSetStep.PICKING, poseIds = message.poseIds)
            }

            PoseSetStep.CHOSEN, PoseSetStep.CLOSED -> when {
                current == null -> Unit

                current.setId == message.setId -> {
                    answer(message.setId)
                    _mirror.value = null
                    endings.trySend(
                        PoseSetEnd(message.setId, message.step, message.by, current.step == PoseSetStep.PICKING),
                    )
                }

                // The photographer tells only of its last set, so the one held here is over, as after a drop in
                // which it ended and another set came and went; how it ended is unknown, so no notice.
                else -> {
                    answer(null)
                    _mirror.value = null
                }
            }
        }
        Timings.mark("pose.applied", "${message.step.name.lowercase()} ${message.setId}")
    }

    /** A step of [setId] answers the pick waiting for it; another set's start drops any (null). */
    private fun answer(setId: String?) {
        if (setId == null || _pending.value?.setId == setId) {
            deadline?.cancel()
            _pending.value = null
        }
    }

    /**
     * Subject: *Use this pose* on [poseId] of the PICKING set. False, and a [pickFailed] when nothing could be sent:
     * no such set or candidate, or no session; false while an earlier pick still waits.
     */
    fun pick(poseId: String): Boolean {
        val set = _mirror.value
        val scope = receiver
        val connected = (session.state.value as? SessionState.Connected)?.role == Role.SUBJECT
        if (_pending.value != null) return false
        if (set == null || set.step != PoseSetStep.PICKING || poseId !in set.candidates || scope == null ||
            !connected
        ) {
            failures.trySend(poseId)
            return false
        }
        val pick = SubjectPick(set.setId, poseId)
        _pending.value = pick
        session.send(SessionMessage.PosePick(set.setId, poseId))
        Timings.mark("pose.sent", "pick $poseId of ${set.setId}")
        deadline = scope.launch {
            delay(PICK_TIMEOUT_MS)
            if (_pending.value == pick) {
                _pending.value = null
                Log.w(TAG, "Pick $poseId was not answered")
                failures.trySend(poseId)
            }
        }
        return true
    }

    internal companion object {
        const val TAG = "PixPoseSync"

        /** How long the subject waits for the photographer's answer to its pick (Design 2.6.5), as for a guide. */
        const val PICK_TIMEOUT_MS = GuideSyncer.OFFER_TIMEOUT_MS
    }
}
