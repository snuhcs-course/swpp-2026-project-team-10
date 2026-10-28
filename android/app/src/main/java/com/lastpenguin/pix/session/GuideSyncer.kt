// AI-generated with Claude Code, 2026-10-28, reviewed by Joonhyung Han, Sungmin Jo, and Dongje Park
package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a guide made on the subject's phone goes (Design 2.6.5, *Guide from the subject*). */
interface SubjectGuideTarget {
    /** The guide on Subject view, so *Add a pose guide* asks to replace it. */
    val current: ReferenceGuide?

    /** Sends [guide] to the photographer; a guide that cannot be sent is reported in [GuideSyncer.offerFailed]. */
    fun offer(guide: ReferenceGuide)
}

/**
 * Keeps the subject's guide the same as the photographer's (Design 2.5.1: guide.image.*, guide.state, guide.clear).
 *
 * Photographer ([startAsSender]): when the session is connected, sends the guide image unless the subject's `hello`
 * said it already has it, then the current state, then the guide's reference photo for the subject's thumbnail
 * (`guide.reference.*`, FR-3.9) unless the subject has that too; after that, every change from [guides]. A new guide
 * sends its image first and its reference after its state. Gesture steps go on the realtime channel at most every
 * [STEP_INTERVAL_MS] (the newest wins), the final value of a gesture at once on the reliable channel, and a removed
 * guide as `guide.clear`. A guide image from the subject is adopted as the photographer's own guide at its `anchor`
 * ([adopted]), and is not sent back.
 *
 * Subject ([startAsReceiver]): rebuilds the image from its chunks, checks it, derives the outline from the mask
 * ([GuideImageCodec]), and writes image and state into [mirror]. A state that arrives before its image is
 * kept and applied once the image is there. The reference photo goes into [mirrorReference], and goes when another
 * guide arrives or the guide is cleared. Stale `seq` is already dropped by the session. A guide made on this phone
 * ([offer]) is drawn at once and sent; until a `guide.state` names it, the photographer's own messages are kept
 * aside, and if none does within [OFFER_TIMEOUT_MS], the mirror shows the photographer's guide again.
 * Owner: Real-time (#9); the guide from the subject Iter3-P9.
 */
class GuideSyncer(
    private val session: SessionManager,
    /** The photographer's guide, read when sending. */
    private val guides: GuideRepository,
    /** The subject's copy, written when receiving. */
    private val mirror: GuideRepository,
    private val codec: GuideImageCodec,
    private val references: GuideReferenceCodec,
    /** The subject's copy of the reference photo, written when receiving. */
    private val mirrorReference: MirrorReference,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SubjectGuideTarget {
    /** Encoded images by guide id, so a reconnecting subject or a replaced subject does not cost another encode. */
    private val encoded = LinkedHashMap<String, EncodedGuideImage>()

    /** Encoded reference photos by guide id, for the same reason. */
    private val encodedReferences = LinkedHashMap<String, EncodedGuideReference>()

    /** Who the next `guide.state` messages are attributed to: the subject while its `guide.move` gesture runs. */
    @Volatile
    private var attributedTo: Role? = null

    /** Photographer: the guide whose image the subject has, from its `hello`, an image sent, or a guide adopted. */
    @Volatile
    private var peerHas: String? = null

    private val _adopted = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Photographer: the id of each guide adopted from the subject, for the Camera screen's notice (FR-7.4). */
    val adopted: SharedFlow<String> = _adopted.asSharedFlow()

    private val failures = Channel<String>(Channel.BUFFERED)

    /** Subject: the id of each offered guide that did not become the photographer's; kept until a screen reads it. */
    val offerFailed: Flow<String> = failures.receiveAsFlow()

    /** Subject: the receiver running for this session, which [offer] hands its guide to. */
    private var receiver: Receiver? = null

    /**
     * From RemoteControlHandler, before it applies a `guide.move`: the guide changes that follow come from [role]'s
     * request, until the gesture's final state has been sent (Design 2.5.1, `guide.state` with `by`).
     */
    fun attribute(role: Role?) {
        attributedTo = role
    }

    override val current: ReferenceGuide?
        get() = mirror.guide.value

    /** Subject: [guide] goes on Subject view at once and to the photographer, who adopts it (Design 2.6.5). */
    override fun offer(guide: ReferenceGuide) {
        val running = receiver
        val connected = session.state.value as? SessionState.Connected
        if (running == null || connected?.role != Role.SUBJECT) {
            failures.trySend(guide.id)
            return
        }
        running.offer(guide)
    }

    /** Photographer: sends the guide image once and every state change while [scope] is active. */
    fun startAsSender(scope: CoroutineScope) {
        val peer = (session.state.value as? SessionState.Connected)?.peer
        peerHas = peer?.haveGuideId
        scope.launch {
            var referenceSent: String? = peer?.haveReferenceId
            var queued: GuideState? = null
            var throttle: Job? = null
            // The current guide goes once the changes are subscribed to: its image and reference take a while to
            // encode, and a change made meanwhile waits in the flow for the collector below instead of being lost.
            guides.changes.onSubscription {
                val guide = guides.guide.value
                if (guide == null) {
                    sendClear()
                } else {
                    if (guide.id != peerHas) peerHas = sendImage(guide)
                    sendState(guides.state.value, final = true)
                    if (guide.id != referenceSent) referenceSent = sendReference(guide)
                }
            }.collect { change ->
                val state = change.state
                val id = state.guideId
                if (id == null) {
                    throttle?.cancel()
                    queued = null
                    peerHas = null
                    referenceSent = null
                    sendClear()
                    return@collect
                }
                if (id != peerHas) {
                    val current = guides.guide.value
                    if (current != null && current.id == id) peerHas = sendImage(current)
                }
                if (change.final) {
                    throttle?.cancel()
                    queued = null
                    sendState(state, final = true)
                } else if (throttle?.isActive == true) {
                    queued = state
                } else {
                    sendState(state, final = false)
                    throttle = launch {
                        while (true) {
                            delay(STEP_INTERVAL_MS)
                            val next = queued ?: break
                            queued = null
                            sendState(next, final = false)
                        }
                    }
                }
                // After the guide's image and state, so the subject draws the guide first.
                if (id != referenceSent) {
                    val current = guides.guide.value
                    if (current != null && current.id == id) referenceSent = sendReference(current)
                }
            }
        }
        // A guide made on the subject's phone becomes this phone's guide (AD-7).
        scope.launch {
            val images = GuideImageReceiver()
            session.incoming.collect { message ->
                val received = images.handle(message) ?: return@collect
                adopt(received)
            }
        }
    }

    /** Subject: applies incoming guide messages to [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        val running = Receiver(scope)
        receiver = running
        scope.launch {
            try {
                session.incoming.collect { running.handle(it) }
            } finally {
                if (receiver === running) receiver = null
            }
        }
    }

    private fun adopt(received: Received) {
        val begin = received.begin
        val guide = received.guide.copy(
            anchor = begin.anchor?.takeIf(::usable),
            source = begin.source ?: GuideSource.GALLERY,
        )
        // The subject has this image: the change that follows sends only its state, attributed to the subject.
        peerHas = guide.id
        attributedTo = Role.SUBJECT
        guides.setGuide(guide)
        Timings.mark("guide.applied", "image ${guide.id} ${received.size} B from the subject")
        _adopted.tryEmit(guide.id)
    }

    /** Rebuilds one guide image at a time from its messages, on either phone. */
    private inner class GuideImageReceiver {
        private var assembler: GuideImageAssembler? = null

        /** The guide once its last chunk checks out; null for every other message. */
        suspend fun handle(message: SessionMessage): Received? {
            when (message) {
                is SessionMessage.GuideImageBegin -> {
                    assembler = GuideImageAssembler.start(message)
                    if (assembler == null) Log.w(TAG, "Refused guide image ${message.guideId}: ${message.bytes} bytes")
                }

                is SessionMessage.GuideImageChunk -> {
                    val current = assembler ?: return null
                    if (!current.add(message)) {
                        Log.w(TAG, "Dropped guide image ${message.guideId}: chunk ${message.index} out of order")
                        assembler = null
                    }
                }

                is SessionMessage.GuideImageEnd -> {
                    val current = assembler ?: return null
                    assembler = null
                    val begin = current.begin
                    val bytes = current.finish(message)
                    if (bytes == null) {
                        Log.w(TAG, "Dropped guide image ${begin.guideId}: size or CRC mismatch")
                        return null
                    }
                    val guide = withContext(dispatcher) { codec.decode(begin.guideId, begin.format, bytes) }
                    if (guide == null) {
                        Log.w(TAG, "Dropped guide image ${begin.guideId}: not a ${begin.format} image")
                        return null
                    }
                    return Received(begin, guide, bytes.size)
                }

                is SessionMessage.GuideClear -> assembler = null

                else -> Unit
            }
            return null
        }
    }

    private class Received(val begin: SessionMessage.GuideImageBegin, val guide: ReferenceGuide, val size: Int)

    /**
     * The subject's side of one session. The photographer's guide and state are kept here as they arrive, and drawn
     * on [mirror] except while a guide offered from this phone waits for the photographer's answer.
     */
    private inner class Receiver(private val scope: CoroutineScope) {
        private val images = GuideImageReceiver()
        private var referenceAssembler: GuideReferenceAssembler? = null

        /** The photographer's guide as last received, and its newest state; null before any or after a clear. */
        private var theirs: ReferenceGuide? = null
        private var theirState: SessionMessage.GuideStateUpdate? = null
        private var theirReference: GuideReference? = null

        /** The guide offered from this phone that no `guide.state` has named yet, and its deadline. */
        private var offered: ReferenceGuide? = null
        private var deadline: Job? = null

        /** The last guide offered, so a photographer's answer that comes after the deadline still finds its image. */
        private var lastOffered: ReferenceGuide? = null

        fun offer(guide: ReferenceGuide) {
            offered = guide
            lastOffered = guide
            mirror.setGuide(guide)
            mirrorReference.set(guide.reference?.let { GuideReference(guide.id, it) })
            deadline?.cancel()
            deadline = scope.launch {
                val image = encoded[guide.id] ?: withContext(dispatcher) { codec.encode(guide) }.also { cache(it) }
                session.send(image.begin(guide.anchor, guide.source))
                for (index in image.chunks.indices) session.send(image.chunk(index))
                session.send(image.end())
                Timings.mark("guide.sent", "image ${guide.id} ${image.bytes.size} B to the photographer")
                delay(OFFER_TIMEOUT_MS)
                if (offered?.id == guide.id) fail()
            }
        }

        suspend fun handle(message: SessionMessage) {
            images.handle(message)?.let { received ->
                theirs = received.guide
                Timings.mark("guide.applied", "image ${received.guide.id} ${received.size} B")
                if (offered == null) showTheirs()
                return
            }
            when (message) {
                is SessionMessage.GuideStateUpdate -> onState(message)

                is SessionMessage.GuideReferenceBegin -> {
                    referenceAssembler = GuideReferenceAssembler.start(message)
                    if (referenceAssembler == null) {
                        Log.w(TAG, "Refused reference ${message.guideId}: ${message.bytes} bytes")
                    }
                }

                is SessionMessage.GuideReferenceChunk -> {
                    val current = referenceAssembler ?: return
                    if (!current.add(message)) {
                        Log.w(TAG, "Dropped reference ${message.guideId}: chunk ${message.index} out of order")
                        referenceAssembler = null
                    }
                }

                is SessionMessage.GuideReferenceEnd -> {
                    val current = referenceAssembler ?: return
                    referenceAssembler = null
                    val begin = current.begin
                    val bytes = current.finish(message)
                    if (bytes == null) {
                        Log.w(TAG, "Dropped reference ${begin.guideId}: size or CRC mismatch")
                        return
                    }
                    val photo = withContext(dispatcher) { references.decode(begin.format, bytes) }
                    if (photo == null) {
                        Log.w(TAG, "Dropped reference ${begin.guideId}: not a ${begin.format} image")
                        return
                    }
                    theirReference = GuideReference(begin.guideId, photo)
                    if (offered == null) mirrorReference.set(theirReference)
                    Timings.mark("guide.applied", "reference ${begin.guideId} ${bytes.size} B")
                }

                is SessionMessage.GuideClear -> {
                    referenceAssembler = null
                    theirs = null
                    theirState = null
                    theirReference = null
                    // The photographer may still adopt the offered guide; until then it stays.
                    if (offered == null) clearMirror()
                }

                else -> Unit
            }
        }

        private fun onState(message: SessionMessage.GuideStateUpdate) {
            val id = message.state.guideId
            if (id == null) {
                theirs = null
                theirState = null
                theirReference = null
                if (offered == null) clearMirror()
                return
            }
            // The photographer adopted a guide offered from here: it is now theirs, and the image is already here.
            val mine = lastOffered?.takeIf { it.id == id }
            if (mine != null && theirs?.id != id) theirs = mine
            theirState = message
            if (offered?.id == id) {
                offered = null
                deadline?.cancel()
                showTheirs()
                return
            }
            if (offered != null) return
            when (mirror.guide.value?.id) {
                id -> apply(message)

                // The image is still on its way (chunks are bigger than a step): it applies the newest state.
                else -> if (theirs?.id == id) showTheirs()
            }
        }

        /** No `guide.state` named the offered guide in time: the photographer's guide is drawn again. */
        private fun fail() {
            val guide = offered ?: return
            offered = null
            Log.w(TAG, "Guide ${guide.id} from this phone was not adopted")
            showTheirs()
            failures.trySend(guide.id)
        }

        private fun showTheirs() {
            val guide = theirs
            if (guide == null) {
                clearMirror()
                return
            }
            if (mirror.guide.value !== guide) mirror.setGuide(guide)
            // Another guide's reference photo is not this one's; a guide from this phone has its own.
            val reference = theirReference?.takeIf { it.guideId == guide.id }
                ?: guide.reference?.let { GuideReference(guide.id, it) }
            if (mirrorReference.reference.value?.guideId != guide.id ||
                reference != null
            ) {
                mirrorReference.set(reference)
            }
            theirState?.takeIf { it.state.guideId == guide.id }?.let(::apply)
        }
    }

    private suspend fun sendImage(guide: ReferenceGuide): String {
        val image = encoded[guide.id] ?: withContext(dispatcher) { codec.encode(guide) }.also { cache(it) }
        session.send(image.begin())
        for (index in image.chunks.indices) session.send(image.chunk(index))
        session.send(image.end())
        Timings.mark("guide.sent", "image ${guide.id} ${image.bytes.size} B in ${image.chunks.size} chunks")
        return guide.id
    }

    private fun cache(image: EncodedGuideImage) {
        encoded[image.guideId] = image
        while (encoded.size > CACHED_IMAGES) encoded.remove(encoded.keys.first())
    }

    /** Sends [guide]'s reference photo if it has one, and returns its id either way, so each guide is tried once. */
    private suspend fun sendReference(guide: ReferenceGuide): String {
        val reference = encodedReferences[guide.id]
            ?: withContext(dispatcher) { references.encode(guide) }?.also { cacheReference(it) }
            ?: return guide.id
        session.send(reference.begin())
        for (index in reference.chunks.indices) session.send(reference.chunk(index))
        session.send(reference.end())
        Timings.mark("guide.sent", "reference ${guide.id} ${reference.bytes.size} B in ${reference.chunks.size} chunks")
        return guide.id
    }

    private fun cacheReference(reference: EncodedGuideReference) {
        encodedReferences[reference.guideId] = reference
        while (encodedReferences.size > CACHED_IMAGES) encodedReferences.remove(encodedReferences.keys.first())
    }

    private fun sendState(state: GuideState, final: Boolean) {
        val by = attributedTo ?: Role.PHOTOGRAPHER
        session.send(SessionMessage.GuideStateUpdate(state, final, by))
        if (final) {
            Timings.mark("guide.sent", "state ${state.guideId} final by $by")
            if (by == Role.SUBJECT) attributedTo = null
        }
    }

    private fun sendClear() {
        session.send(SessionMessage.GuideClear)
        Timings.mark("guide.sent", "clear")
    }

    private fun apply(message: SessionMessage.GuideStateUpdate) {
        mirror.update(message.final) { message.state }
        if (message.final) Timings.mark("guide.applied", "state ${message.state.guideId} final")
    }

    private fun clearMirror() {
        mirrorReference.set(null)
        if (mirror.guide.value != null) {
            mirror.setGuide(null)
            Timings.mark("guide.applied", "clear")
        }
    }

    internal companion object {
        const val TAG = "PixGuideSync"
        const val STEP_INTERVAL_MS = 50L
        const val CACHED_IMAGES = 2

        /** How long the subject waits for the photographer to adopt its guide (Design 2.6.5). */
        const val OFFER_TIMEOUT_MS = 5_000L

        /** An `anchor` from the subject that can place a guide: finite, inside the photo, and with an area. */
        fun usable(anchor: NormalizedRect): Boolean {
            val values = listOf(anchor.left, anchor.top, anchor.right, anchor.bottom)
            return values.all { it.isFinite() && it in 0f..1f } && anchor.right > anchor.left &&
                anchor.bottom > anchor.top
        }
    }
}
