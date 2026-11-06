// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.ConfirmStep
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

/** The subject's *Use this guide* on the outline [guideId] of Reference confirm [confirmId] (`guide.confirm.use`). */
data class SubjectUse(val confirmId: String, val guideId: String)

/**
 * What Reference confirm tells the subject's phone about a pose chosen in a session (Design 2.6.5, *Your reference on
 * both phones*): the photographer's side of [ConfirmSyncer], which ReferenceViewModel calls as the confirm goes on.
 * Calls for a confirm that is not the open one, or that already ended, do nothing, so each confirm ends once.
 */
interface ConfirmShare {
    /** The subject's *Use this guide* while the outline it names is on screen, in the order they came. */
    val uses: Flow<SubjectUse>

    /** The chosen pose went to Reference confirm as [confirmId], in place of any other; its person is being found. */
    fun open(confirmId: String)

    /** *Choose another photo*: the person in another photo is being found for [confirmId]. */
    fun finding(confirmId: String)

    /** The person was found, and [guide]'s outline is on Reference confirm. */
    fun ready(confirmId: String, guide: ReferenceGuide)

    /** The first *Use this guide*, [by] whom, on the outline of [guideId]. */
    fun used(confirmId: String, guideId: String, by: Role)

    /** The confirm ended without a guide: Back, No person found, or a photo that could not be used. */
    fun close(confirmId: String)

    /** For a phone with nothing to share, such as the ViewModel's tests. */
    object None : ConfirmShare {
        override val uses: Flow<SubjectUse> = emptyFlow()

        override fun open(confirmId: String) = Unit

        override fun finding(confirmId: String) = Unit

        override fun ready(confirmId: String, guide: ReferenceGuide) = Unit

        override fun used(confirmId: String, guideId: String, by: Role) = Unit

        override fun close(confirmId: String) = Unit
    }
}

/**
 * The photographer's open Reference confirm as the subject has it (Design 2.6.5): FINDING while the person is found,
 * READY with the outline of [guideId]. An ended confirm is not kept.
 */
data class MirrorConfirm(
    val confirmId: String,
    val step: ConfirmStep,
    /** With READY: the guide whose outline the photographer's Reference confirm shows. */
    val guideId: String? = null,
    /** The outline that arrived last, with its mask; shown only while it is [guideId]'s. */
    val received: ReferenceGuide? = null,
) {
    /** What the subject's *Your reference* shows: the outline of [guideId] once it is here, else null. */
    val ready: ReferenceGuide?
        get() = received?.takeIf { step == ConfirmStep.READY && it.id == guideId }
}

/** How a confirm the subject knew ended: [step] is USED, with [by] whose *Use this guide* it was, or CLOSED. */
data class ConfirmEnd(val confirmId: String, val step: ConfirmStep, val by: Role?)

/**
 * Shows the photographer's Reference confirm of a pose chosen in a session on the subject's *Your reference* (Design
 * 2.5.1: guide.confirm, guide.confirm.image.*, guide.confirm.use; Design 2.6.5, *Reference confirm on both phones*).
 *
 * Photographer ([ConfirmShare], [startAsSender]): keeps the confirm as ReferenceViewModel reports it and, while the
 * session is connected, sends each step it reaches, and with READY then the outline's mask ([GuideImageCodec],
 * encoded off the main thread). A sender started again after the link reconnects (Design 2.6.7) sends the step the
 * confirm is at, with its outline, or only the end of a confirm that ended during the drop. The subject's uses of the
 * outline on screen go to [uses]; deciding is ReferenceViewModel's, which calls [used].
 *
 * Subject ([startAsReceiver]): keeps the confirm in [mirror], in memory only, through a reconnect, and drops it at its
 * end, at another confirm, and when the session ends, saying in [ended] how it ended. [use] sends `guide.confirm.use`
 * and waits [USE_TIMEOUT_MS] for a step that answers it; otherwise [useFailed]. The guide itself comes as any guide,
 * through GuideSyncer, once the photographer's phone sets it.
 * Owner: Shared steps (Iter3-P16).
 */
class ConfirmSyncer(
    private val session: SessionManager,
    private val codec: GuideImageCodec,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ConfirmShare {

    // ---- Photographer -----------------------------------------------------------------------------------------

    /** The photographer's last confirm as ReferenceViewModel reported it; an ended one stays, without its guide. */
    private data class SharedConfirm(
        val confirmId: String,
        val step: ConfirmStep,
        /** With READY and USED: the guide on Reference confirm. */
        val guideId: String? = null,
        /** With READY only, for its outline's mask. */
        val guide: ReferenceGuide? = null,
        val by: Role? = null,
    ) {
        val open: Boolean get() = step == ConfirmStep.FINDING || step == ConfirmStep.READY
    }

    private val shared = MutableStateFlow<SharedConfirm?>(null)

    private val subjectUses = Channel<SubjectUse>(Channel.BUFFERED)
    override val uses: Flow<SubjectUse> = subjectUses.receiveAsFlow()

    override fun open(confirmId: String) {
        shared.value = SharedConfirm(confirmId, ConfirmStep.FINDING)
    }

    override fun finding(confirmId: String) {
        shared.update { confirm ->
            if (confirm?.confirmId == confirmId && confirm.open) {
                SharedConfirm(confirmId, ConfirmStep.FINDING)
            } else {
                confirm
            }
        }
    }

    override fun ready(confirmId: String, guide: ReferenceGuide) {
        shared.update { confirm ->
            if (confirm?.confirmId == confirmId && confirm.step == ConfirmStep.FINDING) {
                confirm.copy(step = ConfirmStep.READY, guideId = guide.id, guide = guide)
            } else {
                confirm
            }
        }
    }

    override fun used(confirmId: String, guideId: String, by: Role) {
        shared.update { confirm ->
            if (confirm?.confirmId == confirmId && confirm.step == ConfirmStep.READY && confirm.guideId == guideId) {
                confirm.copy(step = ConfirmStep.USED, guide = null, by = by)
            } else {
                confirm
            }
        }
    }

    override fun close(confirmId: String) {
        shared.update { confirm ->
            if (confirm?.confirmId == confirmId && confirm.open) {
                SharedConfirm(confirmId, ConfirmStep.CLOSED)
            } else {
                confirm
            }
        }
    }

    /** Photographer: sends the confirm while [scope] is active, and passes the subject's uses on. */
    fun startAsSender(scope: CoroutineScope) {
        scope.launch {
            // What this subject has of the confirm it heard of last; a new sender (a reconnect) starts from nothing,
            // so it sends the step the confirm is at, or only the end of one that ended before it heard of it.
            var announced: String? = null
            var sentStep: Pair<ConfirmStep, String?>? = null
            var sentImage: String? = null
            shared.collect { confirm ->
                if (confirm == null) return@collect
                if (confirm.confirmId != announced) {
                    announced = confirm.confirmId
                    sentStep = null
                    sentImage = null
                }
                val step = confirm.step to confirm.guideId
                if (step != sentStep) {
                    send(SessionMessage.GuideConfirm(confirm.confirmId, confirm.step, confirm.guideId, confirm.by))
                    sentStep = step
                }
                val guide = confirm.guide ?: return@collect
                if (guide.id == sentImage) return@collect
                val image = withContext(dispatcher) { codec.encode(guide) }
                // A confirm that moved on while the mask was encoded sends the step it is at now, from the newer
                // value: the subject has no use for the outline of a guide that is no longer on screen.
                val now = shared.value
                if (now?.confirmId != confirm.confirmId || now.guide?.id != guide.id) return@collect
                session.send(image.confirmBegin(confirm.confirmId))
                for (index in image.chunks.indices) session.send(image.confirmChunk(confirm.confirmId, index))
                session.send(image.confirmEnd(confirm.confirmId))
                sentImage = guide.id
                Timings.mark("guide.sent", "confirm outline ${guide.id} ${image.bytes.size} B")
            }
        }
        scope.launch {
            session.incoming.collect { message ->
                if (message !is SessionMessage.GuideConfirmUse) return@collect
                val confirm = shared.value
                // Only the outline on screen; the step already sent answers a use of any other.
                if (confirm?.confirmId == message.confirmId && confirm.step == ConfirmStep.READY &&
                    confirm.guideId == message.guideId
                ) {
                    subjectUses.trySend(SubjectUse(message.confirmId, message.guideId))
                }
            }
        }
    }

    private fun send(step: SessionMessage.GuideConfirm) {
        session.send(step)
        val detail = step.guideId?.let { " $it" }.orEmpty() + step.by?.let { " by $it" }.orEmpty()
        Timings.mark("guide.sent", "confirm ${step.step.name.lowercase()} ${step.confirmId}$detail")
    }

    // ---- Subject ----------------------------------------------------------------------------------------------

    private val _mirror = MutableStateFlow<MirrorConfirm?>(null)

    /** Subject: the photographer's open confirm; null when there is none. */
    val mirror: StateFlow<MirrorConfirm?> = _mirror.asStateFlow()

    private val _pending = MutableStateFlow<SubjectUse?>(null)

    /** Subject: the use no step has answered yet, while *Your reference* waits for the photographer. */
    val pending: StateFlow<SubjectUse?> = _pending.asStateFlow()

    private val endings = Channel<ConfirmEnd>(Channel.BUFFERED)

    /** Subject: each confirm that ended, for the notice; kept until a screen reads it. */
    val ended: Flow<ConfirmEnd> = endings.receiveAsFlow()

    private val failures = Channel<String>(Channel.BUFFERED)

    /** Subject: each use that no step answered within [USE_TIMEOUT_MS], or that could not be sent. */
    val useFailed: Flow<String> = failures.receiveAsFlow()

    /** Subject: the receiver's scope for this session, where [use]'s deadline runs. */
    private var receiver: CoroutineScope? = null
    private var deadline: Job? = null

    /** Subject: keeps the photographer's confirm in [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        receiver = scope
        scope.launch {
            var assembler: ConfirmImageAssembler? = null
            try {
                session.incoming.collect { message ->
                    when (message) {
                        is SessionMessage.GuideConfirm -> onStep(message)

                        is SessionMessage.GuideConfirmImageBegin -> {
                            // Only the outline the held confirm shows; one this phone holds already, which the
                            // photographer sends again after a reconnect, is not read again.
                            val held = _mirror.value
                            val wanted = held?.confirmId == message.confirmId && held.guideId == message.guideId &&
                                held.received?.id != message.guideId
                            assembler = if (wanted) ConfirmImageAssembler.start(message) else null
                            if (wanted && assembler == null) {
                                Log.w(TAG, "Refused outline ${message.guideId}: ${message.width} × ${message.height}")
                            }
                        }

                        is SessionMessage.GuideConfirmImageChunk -> {
                            val current = assembler ?: return@collect
                            if (!current.add(message)) {
                                Log.w(TAG, "Dropped outline ${message.guideId}: chunk ${message.index} out of order")
                                assembler = null
                            }
                        }

                        is SessionMessage.GuideConfirmImageEnd -> {
                            val current = assembler ?: return@collect
                            assembler = null
                            receive(current, message)
                        }

                        else -> Unit
                    }
                }
            } finally {
                // The confirm goes with the session. It stays while the link reconnects, which the receiver runs
                // through.
                if (receiver === scope) {
                    receiver = null
                    deadline?.cancel()
                    _pending.value = null
                    _mirror.value = null
                }
            }
        }
    }

    private suspend fun receive(assembler: ConfirmImageAssembler, end: SessionMessage.GuideConfirmImageEnd) {
        val begin = assembler.begin
        val bytes = assembler.finish(end)
        if (bytes == null) {
            Log.w(TAG, "Dropped outline ${begin.guideId}: size or CRC mismatch")
            return
        }
        // The outline is traced from the mask as for the guide itself (Design 2.6.1, step 5).
        val guide = withContext(dispatcher) { codec.decode(begin.guideId, begin.format, bytes) }
        if (guide == null) {
            Log.w(TAG, "Dropped outline ${begin.guideId}: not a ${begin.format} image")
            return
        }
        _mirror.update { confirm ->
            if (confirm?.confirmId == begin.confirmId && confirm.guideId == begin.guideId) {
                confirm.copy(received = guide)
            } else {
                confirm
            }
        }
        Timings.mark("guide.applied", "confirm outline ${begin.guideId} ${bytes.size} B")
    }

    private fun onStep(message: SessionMessage.GuideConfirm) {
        val current = _mirror.value
        when (message.step) {
            ConfirmStep.FINDING, ConfirmStep.READY -> {
                val guideId = message.guideId.takeIf { message.step == ConfirmStep.READY }
                // Another confirm, or another photo on this one: a use of the outline that was on screen goes.
                val pending = _pending.value
                if (pending != null && (pending.confirmId != message.confirmId || pending.guideId != guideId)) {
                    answer()
                }
                // An outline already here stays for the same guide, as when the photographer sends it again.
                val received = current?.takeIf { it.confirmId == message.confirmId }?.received
                    ?.takeIf { it.id == guideId }
                _mirror.value = MirrorConfirm(message.confirmId, message.step, guideId, received)
            }

            ConfirmStep.USED, ConfirmStep.CLOSED -> when {
                current == null -> Unit

                current.confirmId == message.confirmId -> {
                    answer()
                    _mirror.value = null
                    endings.trySend(ConfirmEnd(message.confirmId, message.step, message.by))
                }

                // The photographer tells only of its last confirm, so the one held here is over, as after a drop in
                // which it ended and another came and went; how it ended is unknown, so no notice.
                else -> {
                    answer()
                    _mirror.value = null
                }
            }
        }
        Timings.mark("guide.applied", "confirm ${message.step.name.lowercase()} ${message.confirmId}")
    }

    /** The use waiting is answered: by its confirm's end, or by another outline or confirm in its place. */
    private fun answer() {
        deadline?.cancel()
        _pending.value = null
    }

    /**
     * Subject: *Use this guide* on the outline [guideId] on screen. False, and a [useFailed] when nothing could be
     * sent: no such outline, or no session; false while an earlier use still waits.
     */
    fun use(guideId: String): Boolean {
        val confirm = _mirror.value
        val scope = receiver
        val connected = (session.state.value as? SessionState.Connected)?.role == Role.SUBJECT
        if (_pending.value != null) return false
        if (confirm == null || confirm.ready?.id != guideId || scope == null || !connected) {
            failures.trySend(guideId)
            return false
        }
        val use = SubjectUse(confirm.confirmId, guideId)
        _pending.value = use
        session.send(SessionMessage.GuideConfirmUse(confirm.confirmId, guideId))
        Timings.mark("guide.sent", "confirm use $guideId of ${confirm.confirmId}")
        deadline = scope.launch {
            delay(USE_TIMEOUT_MS)
            if (_pending.value == use) {
                _pending.value = null
                Log.w(TAG, "Use of $guideId was not answered")
                failures.trySend(guideId)
            }
        }
        return true
    }

    internal companion object {
        const val TAG = "PixConfirmSync"

        /** How long the subject waits for the photographer's answer to its use (Design 2.6.5), as for a pick. */
        const val USE_TIMEOUT_MS = PoseSyncer.PICK_TIMEOUT_MS
    }
}

/** Subject: collects one outline's chunks with the guide image's checks ([GuideImageAssembler]). */
class ConfirmImageAssembler private constructor(
    val begin: SessionMessage.GuideConfirmImageBegin,
    private val image: GuideImageAssembler,
) {
    /** False when the chunk is not the next one of this outline: the transfer is broken and is dropped. */
    fun add(chunk: SessionMessage.GuideConfirmImageChunk): Boolean = chunk.confirmId == begin.confirmId &&
        image.add(SessionMessage.GuideImageChunk(chunk.guideId, chunk.index, chunk.data))

    /** The mask's bytes, or null when a chunk is missing, the size differs, or the CRC does not match. */
    fun finish(end: SessionMessage.GuideConfirmImageEnd): ByteArray? {
        if (end.confirmId != begin.confirmId) return null
        return image.finish(SessionMessage.GuideImageEnd(end.guideId, end.crc32))
    }

    companion object {
        /** Null for a `begin` that cannot describe a mask the photographer sends, refused before any chunk. */
        fun start(begin: SessionMessage.GuideConfirmImageBegin): ConfirmImageAssembler? {
            val fits = begin.width <= MaskGuideImageCodec.MAX_WIDTH && begin.height <= MaskGuideImageCodec.MAX_HEIGHT
            if (!fits) return null
            val image = SessionMessage.GuideImageBegin(
                begin.guideId,
                begin.format,
                begin.width,
                begin.height,
                begin.bytes,
                begin.chunks,
            )
            return GuideImageAssembler.start(image)?.let { ConfirmImageAssembler(begin, it) }
        }
    }
}

/** The guide image's mask as the outline of Reference confirm [confirmId] (Design 2.5.1, `guide.confirm.image.*`). */
internal fun EncodedGuideImage.confirmBegin(confirmId: String) =
    SessionMessage.GuideConfirmImageBegin(confirmId, guideId, format, width, height, bytes.size, chunks.size)

internal fun EncodedGuideImage.confirmChunk(confirmId: String, index: Int) =
    SessionMessage.GuideConfirmImageChunk(confirmId, guideId, index, chunks[index])

internal fun EncodedGuideImage.confirmEnd(confirmId: String) =
    SessionMessage.GuideConfirmImageEnd(confirmId, guideId, crc32)
