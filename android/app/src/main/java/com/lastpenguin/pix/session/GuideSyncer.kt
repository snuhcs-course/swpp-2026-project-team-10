// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.NormalizedRect
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * guide as `guide.clear`, after which the subject holds no image. After a drop in the same session, what this phone
 * has sent says what the subject has, not the `hello`, which is still the one from the session's start (#268). A
 * guide image from the subject is adopted as the photographer's own guide at its `anchor` ([adopted]), with the
 * reference photo the subject sent before it, and neither is sent back.
 *
 * Subject ([startAsReceiver]): rebuilds the image from its chunks, checks it, derives the outline from the mask
 * ([GuideImageCodec]), and writes image and state into [mirror]. A state that arrives before its image is
 * kept and applied once the image is there. The reference photo goes into [mirrorReference], and goes when another
 * guide arrives or the guide is cleared. Stale `seq` is already dropped by the session. When the session ends, the
 * mirror and its reference are emptied, so the next session draws nothing of this one (#268); the image and its
 * reference stay in [held] for the next `hello`, and are drawn again once a `guide.state` names that guide. A guide
 * made on this phone ([offer]) is drawn at once and sent, its reference photo before its image; until a `guide.state`
 * names it, the photographer's own messages are kept aside, and if none does within [OFFER_TIMEOUT_MS], the mirror
 * shows the photographer's guide again.
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
    /** The subject's image and reference photo for the next `hello`, kept past the session. */
    private val held: HeldGuide = HeldGuide(),
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

    /**
     * Photographer: the guide whose reference photo the subject has, from its `hello`, a reference sent (or tried, for
     * a guide without one), or a guide adopted, whose photo came from the subject.
     */
    @Volatile
    private var peerHasReference: String? = null

    private val _adopted = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Photographer: the id of each guide adopted from the subject, for the Camera screen's notice (FR-7.4). */
    val adopted: SharedFlow<String> = _adopted.asSharedFlow()

    private val failures = Channel<String>(Channel.BUFFERED)

    /** Subject: the id of each offered guide that did not become the photographer's; kept until a screen reads it. */
    val offerFailed: Flow<String> = failures.receiveAsFlow()

    private val _offering = MutableStateFlow<String?>(null)

    /**
     * Subject: the id of the guide offered from this phone while no `guide.state` has named it yet, and null otherwise.
     * The photographer's phone does not have that guide yet, so it would drop a request for it: Subject view's panel
     * waits (FR-7.2).
     */
    val offering: StateFlow<String?> = _offering.asStateFlow()

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

    /**
     * Photographer: sends the guide image once and every state change while [scope] is active. [resumed] after a drop
     * in the same session: the subject's `hello` is still the one from the session's start, so [peerHas] and
     * [peerHasReference] stay as this phone left them.
     */
    fun startAsSender(scope: CoroutineScope, resumed: Boolean = false) {
        if (!resumed) {
            val peer = (session.state.value as? SessionState.Connected)?.peer
            peerHas = peer?.haveGuideId
            peerHasReference = peer?.haveReferenceId
        }
        scope.launch {
            var queued: GuideState? = null
            var throttle: Job? = null
            // The current guide goes once the changes are subscribed to: its image and reference take a while to
            // encode, and a change made meanwhile waits in the flow for the collector below instead of being lost.
            guides.changes.onSubscription {
                val guide = guides.guide.value
                if (guide == null) {
                    sendClear()
                } else {
                    if (guide.id != peerHas) sendImage(guide)
                    // A guide adopted from the subject while this one went out is sent by the collector: this one's
                    // state and reference would follow the adopted guide's otherwise, and show its thumbnail.
                    val state = guides.state.value
                    if (state.guideId == guide.id) sendState(state, final = true)
                    if (guide.id != peerHasReference && guides.guide.value?.id == guide.id) sendReference(guide)
                }
            }.collect { change ->
                val state = change.state
                val id = state.guideId
                if (id == null) {
                    throttle?.cancel()
                    queued = null
                    sendClear()
                    return@collect
                }
                if (id != peerHas) {
                    val current = guides.guide.value
                    if (current != null && current.id == id) sendImage(current)
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
                if (id != peerHasReference) {
                    val current = guides.guide.value
                    if (current != null && current.id == id) sendReference(current)
                }
            }
        }
        // A guide made on the subject's phone becomes this phone's guide (AD-7), with the reference photo sent before
        // its image: a guide is never changed once set, so its photo cannot follow it (#234).
        scope.launch {
            val images = GuideImageReceiver()
            val photos = GuideReferenceReceiver()
            var photo: GuideReference? = null
            session.incoming.collect { message ->
                photos.handle(message)?.let { received ->
                    photo = received
                    return@collect
                }
                val received = images.handle(message) ?: return@collect
                adopt(received, photo?.takeIf { it.guideId == received.guide.id })
                photo = null
            }
        }
    }

    /**
     * Subject: applies incoming guide messages to [mirror] while [scope] is active, which is one session. At its end
     * the mirror is emptied, as the other shared steps' copies are (#268).
     */
    fun startAsReceiver(scope: CoroutineScope) {
        val running = Receiver(scope)
        receiver = running
        scope.launch {
            try {
                session.incoming.collect { running.handle(it) }
            } finally {
                if (receiver === running) {
                    receiver = null
                    _offering.value = null
                }
                leaveMirror()
            }
        }
    }

    private fun adopt(received: Received, photo: GuideReference?) {
        // Its reference photo is for the Camera screen's thumbnail (FR-3.9), as for a guide made on this phone.
        val guide = received.placed.copy(reference = photo?.photo)
        // The subject has this image and its photo: the change that follows sends only its state, attributed to the
        // subject.
        peerHas = guide.id
        peerHasReference = guide.id
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

    /** Rebuilds one reference photo at a time from its `guide.reference.*` messages, on either phone. */
    private inner class GuideReferenceReceiver {
        private var assembler: GuideReferenceAssembler? = null

        /** The photo once its last chunk checks out; null for every other message. */
        suspend fun handle(message: SessionMessage): GuideReference? {
            when (message) {
                is SessionMessage.GuideReferenceBegin -> {
                    assembler = GuideReferenceAssembler.start(message)
                    if (assembler == null) Log.w(TAG, "Refused reference ${message.guideId}: ${message.bytes} bytes")
                }

                is SessionMessage.GuideReferenceChunk -> {
                    val current = assembler ?: return null
                    if (!current.add(message)) {
                        Log.w(TAG, "Dropped reference ${message.guideId}: chunk ${message.index} out of order")
                        assembler = null
                    }
                }

                is SessionMessage.GuideReferenceEnd -> {
                    val current = assembler ?: return null
                    assembler = null
                    val begin = current.begin
                    val bytes = current.finish(message)
                    if (bytes == null) {
                        Log.w(TAG, "Dropped reference ${begin.guideId}: size or CRC mismatch")
                        return null
                    }
                    val photo = withContext(dispatcher) { references.decode(begin.format, bytes) }
                    if (photo == null) {
                        Log.w(TAG, "Dropped reference ${begin.guideId}: not a ${begin.format} image")
                        return null
                    }
                    Timings.mark("guide.applied", "reference ${begin.guideId} ${bytes.size} B")
                    return GuideReference(begin.guideId, photo)
                }

                is SessionMessage.GuideClear -> assembler = null

                else -> Unit
            }
            return null
        }
    }

    private class Received(val begin: SessionMessage.GuideImageBegin, val guide: ReferenceGuide, val size: Int) {
        /**
         * The guide with the `anchor`, `source`, and `situation` its image carried: where the photographer starts a guide
         * from the subject, and what the subject's *Save guide* keeps (Design 2.5.1, #270). A box that cannot place a
         * guide is left out.
         */
        val placed: ReferenceGuide
            get() = guide.copy(
                anchor = begin.anchor?.takeIf(::usable),
                source = begin.source ?: GuideSource.GALLERY,
                situation = Situation.named(begin.situation),
            )
    }

    /**
     * The subject's side of one session. The photographer's guide and state are kept here as they arrive, and drawn
     * on [mirror] except while a guide offered from this phone waits for the photographer's answer.
     */
    private inner class Receiver(private val scope: CoroutineScope) {
        private val images = GuideImageReceiver()
        private val photos = GuideReferenceReceiver()

        /**
         * The photographer's guide as last received, and its reference photo; null before any or after a clear. They
         * live in [held], past this session: a subject that joins again starts with the image `hello` said it has, so
         * the photographer sends only its state, and that state draws it.
         */
        private var theirs: ReferenceGuide?
            get() = held.guide
            set(value) {
                held.guide = value
            }
        private var theirReference: GuideReference?
            get() = held.reference
            set(value) {
                held.reference = value
            }

        /** Its newest state in this session. */
        private var theirState: SessionMessage.GuideStateUpdate? = null

        /** The guide offered from this phone that no `guide.state` has named yet ([offering]), and its deadline. */
        private var offered: ReferenceGuide? = null
            set(value) {
                field = value
                _offering.value = value?.id
            }
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
                // The reference photo first: the photographer adopts the guide with it once the image is complete.
                sendReferenceOf(guide, "to the photographer")
                val image = encoded[guide.id] ?: withContext(dispatcher) { codec.encode(guide) }.also { cache(it) }
                session.send(image.begin(guide.anchor, guide.source, guide.situation))
                for (index in image.chunks.indices) session.send(image.chunk(index))
                session.send(image.end())
                Timings.mark("guide.sent", "image ${guide.id} ${image.bytes.size} B to the photographer")
                delay(OFFER_TIMEOUT_MS)
                if (offered?.id == guide.id) fail()
            }
        }

        suspend fun handle(message: SessionMessage) {
            images.handle(message)?.let { received ->
                theirs = received.placed
                Timings.mark("guide.applied", "image ${received.guide.id} ${received.size} B")
                if (offered == null) showTheirs()
                return
            }
            photos.handle(message)?.let { received ->
                theirReference = received
                // Only the shown guide's: a late reference of a guide since replaced must not take its corner.
                if (offered == null && mirror.guide.value?.id == received.guideId) mirrorReference.set(received)
                return
            }
            when (message) {
                is SessionMessage.GuideStateUpdate -> onState(message)

                is SessionMessage.GuideClear -> {
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

    /**
     * The subject counts as having [guide] from the start: a guide adopted from the subject while this one is encoded
     * sets [peerHas] to its own id, which this send must not overwrite, or the adopted guide's image would go back.
     */
    private suspend fun sendImage(guide: ReferenceGuide) {
        peerHas = guide.id
        val image = try {
            encoded[guide.id] ?: withContext(dispatcher) { codec.encode(guide) }.also { cache(it) }
        } catch (e: CancellationException) {
            // The link dropped during the encode and nothing went out: the subject does not have it (#268).
            if (peerHas == guide.id) peerHas = null
            throw e
        }
        // The anchor and source go along for the subject's *Save guide*; the state that follows places the guide.
        session.send(image.begin(guide.anchor, guide.source, guide.situation))
        for (index in image.chunks.indices) session.send(image.chunk(index))
        session.send(image.end())
        Timings.mark("guide.sent", "image ${guide.id} ${image.bytes.size} B in ${image.chunks.size} chunks")
    }

    private fun cache(image: EncodedGuideImage) {
        encoded[image.guideId] = image
        while (encoded.size > CACHED_IMAGES) encoded.remove(encoded.keys.first())
    }

    /**
     * Photographer: sends [guide]'s reference photo if it has one. The subject counts as having it from the start, so
     * each guide is tried once, and a guide adopted during the encode keeps its own id in [peerHasReference].
     */
    private suspend fun sendReference(guide: ReferenceGuide) {
        peerHasReference = guide.id
        try {
            sendReferenceOf(guide, "to the subject")
        } catch (e: CancellationException) {
            // As for the image: a drop during the encode sent nothing.
            if (peerHasReference == guide.id) peerHasReference = null
            throw e
        }
    }

    /** Sends [guide]'s reference photo as `guide.reference.*` if it has one, on either phone. */
    private suspend fun sendReferenceOf(guide: ReferenceGuide, to: String) {
        val reference = encodedReferences[guide.id]
            ?: withContext(dispatcher) { references.encode(guide) }?.also { cacheReference(it) }
            ?: return
        session.send(reference.begin())
        for (index in reference.chunks.indices) session.send(reference.chunk(index))
        session.send(reference.end())
        Timings.mark(
            "guide.sent",
            "reference ${guide.id} ${reference.bytes.size} B in ${reference.chunks.size} chunks $to",
        )
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

    /**
     * The subject drops the image and the reference photo it holds at a clear, also those its `hello` named, so the
     * next guide goes in full (#268).
     */
    private fun sendClear() {
        peerHas = null
        peerHasReference = null
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

    /** The session is over: Subject view draws nothing of it, while [held] keeps the image for the next `hello`. */
    private fun leaveMirror() {
        mirrorReference.set(null)
        if (mirror.guide.value != null) mirror.setGuide(null)
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
