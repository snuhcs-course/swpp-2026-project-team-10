// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han
package com.lastpenguin.pix.session

import android.graphics.Bitmap
import android.util.Log
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.session.protocol.DescriptionStep
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
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

/** What the subject asked of the photographer's *Generate poses* (Design 2.5.1, `pose.description.*`). */
sealed interface DescriptionRequest {
    /** The subject's field: [holding] while it types, with its [text] so far, then false with its last text. */
    data class Edit(val descriptionId: String, val holding: Boolean, val text: String) : DescriptionRequest

    /** The subject's *Generate 4 poses* with its [text], or *Skip* when [suggest]. */
    data class Go(val descriptionId: String, val text: String, val suggest: Boolean) : DescriptionRequest

    /** The link to the subject dropped or the session ended: a field the subject held is free again. */
    data object Gone : DescriptionRequest
}

/**
 * What *Generate poses* tells the subject's phone (Design 2.6.5, *Generate poses on both phones*): the photographer's
 * side of [DescriptionSyncer], which GenerationViewModel calls as the description goes on. Calls for a description that
 * is not the open one, or that already ended, do nothing, so each description ends once.
 */
interface DescriptionShare {
    /** The subject's edits and its *Generate 4 poses* or *Skip* for the open description, in the order they came. */
    val requests: Flow<DescriptionRequest>

    /** Generate poses opened as [descriptionId] with the scene photo [scene], in place of any other description. */
    fun open(descriptionId: String, scene: Bitmap, text: String, rejected: Boolean)

    /** The description is now [text], with [writer]'s field holding it; null while nobody types. */
    fun update(descriptionId: String, text: String, writer: Role?)

    /** A set started from the description ([started]), or Generate poses closed without one. */
    fun end(descriptionId: String, started: Boolean)

    /** For a phone with nothing to share, such as the ViewModel's tests. */
    object None : DescriptionShare {
        override val requests: Flow<DescriptionRequest> = emptyFlow()

        override fun open(descriptionId: String, scene: Bitmap, text: String, rejected: Boolean) = Unit

        override fun update(descriptionId: String, text: String, writer: Role?) = Unit

        override fun end(descriptionId: String, started: Boolean) = Unit
    }
}

/** The photographer's open *Generate poses* as the subject has it (Design 2.6.5). An ended one is not kept. */
data class MirrorDescription(
    val descriptionId: String,
    val text: String = "",
    /** The phone whose field holds the description; null while nobody types. */
    val writer: Role? = null,
    val rejected: Boolean = false,
    /** The scene photo's copy, once it arrived. */
    val scene: Bitmap? = null,
)

/** How a description the subject knew ended: a set [started] from it, or Generate poses closed without one. */
data class DescriptionEnd(val descriptionId: String, val started: Boolean)

/**
 * Shows the photographer's *Generate poses* on the subject's *Generate poses* (Design 2.5.1: pose.description,
 * pose.scene.*, pose.description.edit, pose.description.go; Design 2.6.5, *Generate poses on both phones*). One field
 * at a time holds the description: the photographer's phone, the single source of truth (AD-7), decides whose.
 *
 * Photographer ([DescriptionShare], [startAsSender]): keeps the description as GenerationViewModel reports it and,
 * while the session is connected, sends each change, and after the first one the scene photo's copy
 * ([PoseCandidateCodec], encoded off the main thread). A sender started again after the link reconnects (Design
 * 2.6.7) sends the description as it is now, or only the end of one that ended during the drop. The subject's edits and
 * its *Generate 4 poses* or *Skip* go to [requests], and so does [DescriptionRequest.Gone] when the sender stops, so a
 * field the subject held does not stay held; deciding is GenerationViewModel's.
 *
 * Subject ([startAsReceiver]): keeps the description in [mirror], in memory only, through a reconnect, and drops it at
 * its end, at another description, and when the session ends, saying in [ended] how it ended. [edit] sends the field's
 * text, and [go] sends *Generate 4 poses* or *Skip* and waits [GO_TIMEOUT_MS] for the description's end; otherwise
 * [goFailed].
 * Owner: Shared steps (Iter3-P16).
 */
class DescriptionSyncer(
    private val session: SessionManager,
    private val codec: PoseCandidateCodec,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DescriptionShare {

    // ---- Photographer -----------------------------------------------------------------------------------------

    /** The photographer's last description as GenerationViewModel reported it; an ended one stays, without its photo. */
    private data class SharedDescription(
        val descriptionId: String,
        val step: DescriptionStep,
        val scene: Bitmap? = null,
        val text: String = "",
        val writer: Role? = null,
        val rejected: Boolean = false,
    ) {
        /** What the subject is told of it; text, writer, and rejected only while it is open. */
        val message: SessionMessage.PoseDescription
            get() = if (step == DescriptionStep.OPEN) {
                SessionMessage.PoseDescription(descriptionId, step, text, writer, rejected)
            } else {
                SessionMessage.PoseDescription(descriptionId, step)
            }
    }

    private val shared = MutableStateFlow<SharedDescription?>(null)

    private val subjectRequests = Channel<DescriptionRequest>(Channel.BUFFERED)
    override val requests: Flow<DescriptionRequest> = subjectRequests.receiveAsFlow()

    override fun open(descriptionId: String, scene: Bitmap, text: String, rejected: Boolean) {
        shared.value = SharedDescription(descriptionId, DescriptionStep.OPEN, scene, text, rejected = rejected)
    }

    override fun update(descriptionId: String, text: String, writer: Role?) {
        shared.update { description ->
            if (description?.descriptionId == descriptionId && description.step == DescriptionStep.OPEN) {
                description.copy(text = text, writer = writer)
            } else {
                description
            }
        }
    }

    override fun end(descriptionId: String, started: Boolean) {
        shared.update { description ->
            if (description?.descriptionId == descriptionId && description.step == DescriptionStep.OPEN) {
                SharedDescription(descriptionId, if (started) DescriptionStep.STARTED else DescriptionStep.CLOSED)
            } else {
                description
            }
        }
    }

    /** Photographer: sends the description while [scope] is active, and passes the subject's requests on. */
    fun startAsSender(scope: CoroutineScope) {
        scope.launch {
            // What this subject has of the description it heard of last; a new sender (a reconnect) starts from
            // nothing, so it sends the description as it is now, or only the end of one that ended before.
            var announced: String? = null
            var sent: SessionMessage.PoseDescription? = null
            var sceneSent = false
            shared.collect { description ->
                if (description == null) return@collect
                if (description.descriptionId != announced) {
                    announced = description.descriptionId
                    sent = null
                    sceneSent = false
                }
                val message = description.message
                if (message != sent) {
                    send(message)
                    sent = message
                }
                val scene = description.scene ?: return@collect
                if (sceneSent) return@collect
                val copy = withContext(dispatcher) { codec.encode(description.descriptionId, scene) }
                // A description that ended or was replaced while the photo was encoded sends no photo; a change of
                // its text meanwhile goes after the photo, from the newer value.
                val now = shared.value
                if (now?.descriptionId != description.descriptionId || now.step != DescriptionStep.OPEN) return@collect
                sceneSent = true
                if (copy == null) {
                    Log.w(TAG, "Could not encode the scene photo")
                    return@collect
                }
                session.send(copy.sceneBegin(description.descriptionId))
                for (index in copy.chunks.indices) session.send(copy.sceneChunk(description.descriptionId, index))
                session.send(copy.sceneEnd(description.descriptionId))
                Timings.mark("pose.sent", "scene ${copy.bytes.size} B in ${copy.chunks.size} chunks")
            }
        }
        scope.launch {
            session.incoming.collect { message ->
                val open = shared.value?.takeIf { it.step == DescriptionStep.OPEN }?.descriptionId
                when (message) {
                    is SessionMessage.PoseDescriptionEdit -> if (message.descriptionId == open) {
                        subjectRequests.trySend(
                            DescriptionRequest.Edit(message.descriptionId, message.holding, message.text),
                        )
                    }

                    is SessionMessage.PoseDescriptionGo -> if (message.descriptionId == open) {
                        subjectRequests.trySend(
                            DescriptionRequest.Go(message.descriptionId, message.text, message.suggest),
                        )
                    }

                    else -> Unit
                }
            }
        }
        scope.launch {
            try {
                awaitCancellation()
            } finally {
                // The subject cannot type while the link is down, and may never come back.
                subjectRequests.trySend(DescriptionRequest.Gone)
            }
        }
    }

    private fun send(message: SessionMessage.PoseDescription) {
        session.send(message)
        val writer = message.writer?.let { " by $it" }.orEmpty()
        Timings.mark("pose.sent", "description ${message.step.name.lowercase()} ${message.descriptionId}$writer")
    }

    // ---- Subject ----------------------------------------------------------------------------------------------

    private val _mirror = MutableStateFlow<MirrorDescription?>(null)

    /** Subject: the photographer's open Generate poses; null when there is none. */
    val mirror: StateFlow<MirrorDescription?> = _mirror.asStateFlow()

    private val _going = MutableStateFlow<String?>(null)

    /** Subject: the description whose *Generate 4 poses* or *Skip* no end has answered yet. */
    val going: StateFlow<String?> = _going.asStateFlow()

    private val endings = Channel<DescriptionEnd>(Channel.BUFFERED)

    /** Subject: each description that ended, for the notice; kept until a screen reads it. */
    val ended: Flow<DescriptionEnd> = endings.receiveAsFlow()

    private val failures = Channel<String>(Channel.BUFFERED)

    /** Subject: each *Generate 4 poses* or *Skip* that no end answered within [GO_TIMEOUT_MS], or that was not sent. */
    val goFailed: Flow<String> = failures.receiveAsFlow()

    /** Subject: the receiver's scope for this session, where [go]'s deadline runs. */
    private var receiver: CoroutineScope? = null
    private var deadline: Job? = null

    /** Subject: keeps the photographer's description in [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        receiver = scope
        scope.launch {
            var assembler: SceneAssembler? = null
            try {
                session.incoming.collect { message ->
                    when (message) {
                        is SessionMessage.PoseDescription -> onDescription(message)

                        is SessionMessage.PoseSceneBegin -> {
                            // Only the held description's, and once: the photographer sends it again after a
                            // reconnect.
                            val held = _mirror.value
                            val wanted = held?.descriptionId == message.descriptionId && held.scene == null
                            assembler = if (wanted) SceneAssembler.start(message) else null
                            if (wanted && assembler == null) Log.w(TAG, "Refused the scene photo: ${message.bytes} B")
                        }

                        is SessionMessage.PoseSceneChunk -> {
                            val current = assembler ?: return@collect
                            if (!current.add(message)) {
                                Log.w(TAG, "Dropped the scene photo: chunk ${message.index} out of order")
                                assembler = null
                            }
                        }

                        is SessionMessage.PoseSceneEnd -> {
                            val current = assembler ?: return@collect
                            assembler = null
                            receive(current, message)
                        }

                        else -> Unit
                    }
                }
            } finally {
                // The description goes with the session. It stays while the link reconnects, which the receiver
                // runs through.
                if (receiver === scope) {
                    receiver = null
                    deadline?.cancel()
                    _going.value = null
                    _mirror.value = null
                }
            }
        }
    }

    private suspend fun receive(assembler: SceneAssembler, end: SessionMessage.PoseSceneEnd) {
        val begin = assembler.begin
        val bytes = assembler.finish(end)
        if (bytes == null) {
            Log.w(TAG, "Dropped the scene photo: size or CRC mismatch")
            return
        }
        val photo = withContext(dispatcher) { codec.decode(begin.format, bytes) }
        if (photo == null) {
            Log.w(TAG, "Dropped the scene photo: not a ${begin.format} image")
            return
        }
        _mirror.update { if (it?.descriptionId == begin.descriptionId) it.copy(scene = photo) else it }
        Timings.mark("pose.applied", "scene ${bytes.size} B")
    }

    private fun onDescription(message: SessionMessage.PoseDescription) {
        val current = _mirror.value
        when (message.step) {
            DescriptionStep.OPEN -> {
                if (current?.descriptionId != message.descriptionId) answer()
                _mirror.value = MirrorDescription(
                    message.descriptionId,
                    message.text,
                    message.writer,
                    message.rejected,
                    current?.takeIf { it.descriptionId == message.descriptionId }?.scene,
                )
            }

            DescriptionStep.STARTED, DescriptionStep.CLOSED -> when {
                current == null -> Unit

                current.descriptionId == message.descriptionId -> {
                    answer()
                    _mirror.value = null
                    endings.trySend(DescriptionEnd(message.descriptionId, message.step == DescriptionStep.STARTED))
                }

                // The photographer tells only of its last description, so the one held here is over; how it ended is
                // unknown, so no notice.
                else -> {
                    answer()
                    _mirror.value = null
                }
            }
        }
        Timings.mark("pose.applied", "description ${message.step.name.lowercase()} ${message.descriptionId}")
    }

    private fun answer() {
        deadline?.cancel()
        _going.value = null
    }

    private fun connected(): Boolean = (session.state.value as? SessionState.Connected)?.role == Role.SUBJECT

    /**
     * Subject: this phone's field [holding] the description, with its [text]; false, and nothing sent, without an open
     * description or a session.
     */
    fun edit(holding: Boolean, text: String): Boolean {
        val description = _mirror.value ?: return false
        if (!connected()) return false
        session.send(SessionMessage.PoseDescriptionEdit(description.descriptionId, holding, text))
        return true
    }

    /**
     * Subject: *Generate 4 poses* with [text], or *Skip* when [suggest]. False, and a [goFailed] when nothing could be
     * sent: no description, the photographer's field holding it, or no session; false while an earlier one still waits.
     */
    fun go(text: String, suggest: Boolean): Boolean {
        val description = _mirror.value
        val scope = receiver
        if (_going.value != null) return false
        if (description == null || description.writer == Role.PHOTOGRAPHER || scope == null || !connected()) {
            failures.trySend(description?.descriptionId.orEmpty())
            return false
        }
        val id = description.descriptionId
        _going.value = id
        session.send(SessionMessage.PoseDescriptionGo(id, text, suggest))
        Timings.mark("pose.sent", "description go $id${if (suggest) " suggested" else ""}")
        deadline = scope.launch {
            delay(GO_TIMEOUT_MS)
            if (_going.value == id) {
                _going.value = null
                Log.w(TAG, "Generate poses from this phone was not answered")
                failures.trySend(id)
            }
        }
        return true
    }

    internal companion object {
        const val TAG = "PixDescriptionSync"

        /** How long the subject waits for the photographer's answer to its *Generate 4 poses*, as for a pick. */
        const val GO_TIMEOUT_MS = PoseSyncer.PICK_TIMEOUT_MS
    }
}

/** Subject: collects the scene photo's chunks with a candidate copy's limits and the guide image's checks. */
class SceneAssembler private constructor(
    val begin: SessionMessage.PoseSceneBegin,
    private val image: GuideImageAssembler,
) {
    /** False when the chunk is not the next one of this photo: the transfer is broken and is dropped. */
    fun add(chunk: SessionMessage.PoseSceneChunk): Boolean = chunk.descriptionId == begin.descriptionId &&
        image.add(SessionMessage.GuideImageChunk(begin.descriptionId, chunk.index, chunk.data))

    /** The photo's bytes, or null when a chunk is missing, the size differs, or the CRC does not match. */
    fun finish(end: SessionMessage.PoseSceneEnd): ByteArray? {
        if (end.descriptionId != begin.descriptionId) return null
        return image.finish(SessionMessage.GuideImageEnd(begin.descriptionId, end.crc32))
    }

    companion object {
        /** Null for a `begin` that cannot describe a photo the photographer sends, refused before any chunk. */
        fun start(begin: SessionMessage.PoseSceneBegin): SceneAssembler? {
            val limit = JpegPoseCandidateCodec.MAX_LONG_SIDE
            if (begin.bytes > PoseCandidateAssembler.MAX_BYTES || begin.width > limit || begin.height > limit) {
                return null
            }
            val image = SessionMessage.GuideImageBegin(
                begin.descriptionId,
                begin.format,
                begin.width,
                begin.height,
                begin.bytes,
                begin.chunks,
            )
            return GuideImageAssembler.start(image)?.let { SceneAssembler(begin, it) }
        }
    }
}

/** A candidate's copy codec's output as the scene photo of Generate poses [descriptionId] (Design 2.5.1). */
internal fun EncodedPoseCandidate.sceneBegin(descriptionId: String) =
    SessionMessage.PoseSceneBegin(descriptionId, format, width, height, bytes.size, chunks.size)

internal fun EncodedPoseCandidate.sceneChunk(descriptionId: String, index: Int) =
    SessionMessage.PoseSceneChunk(descriptionId, index, chunks[index])

internal fun EncodedPoseCandidate.sceneEnd(descriptionId: String) = SessionMessage.PoseSceneEnd(descriptionId, crc32)
