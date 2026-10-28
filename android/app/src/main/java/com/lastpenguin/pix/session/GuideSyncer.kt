// AI-generated with Claude Code, 2026-10-28, reviewed by Sungmin Jo and Dongje Park
package com.lastpenguin.pix.session

import android.util.Log
import com.lastpenguin.pix.core.Timings
import com.lastpenguin.pix.guide.GuideRepository
import com.lastpenguin.pix.guide.GuideState
import com.lastpenguin.pix.guide.ReferenceGuide
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the subject's guide the same as the photographer's (Design 2.5.1: guide.image.*, guide.state, guide.clear).
 *
 * Photographer ([startAsSender]): when the session is connected, sends the guide image unless the subject's `hello`
 * said it already has it, then the current state, then the guide's reference photo for the subject's thumbnail
 * (`guide.reference.*`, FR-3.9) unless the subject has that too; after that, every change from [guides]. A new guide
 * sends its image first and its reference after its state. Gesture steps go on the realtime channel at most every
 * [STEP_INTERVAL_MS] (the newest wins), the final value of a gesture at once on the reliable channel, and a removed
 * guide as `guide.clear`.
 *
 * Subject ([startAsReceiver]): rebuilds the image from its chunks, checks it, derives the outline from the cutout's
 * alpha ([GuideImageCodec]), and writes image and state into [mirror]. A state that arrives before its image is
 * kept and applied once the image is there. The reference photo goes into [mirrorReference], and goes when another
 * guide arrives or the guide is cleared. Stale `seq` is already dropped by the session.
 * Owner: Real-time (#9).
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
) {
    /** Encoded images by guide id, so a reconnecting subject or a replaced subject does not cost another encode. */
    private val encoded = LinkedHashMap<String, EncodedGuideImage>()

    /** Encoded reference photos by guide id, for the same reason. */
    private val encodedReferences = LinkedHashMap<String, EncodedGuideReference>()

    /** Who the next `guide.state` messages are attributed to: the subject while its `guide.move` gesture runs. */
    @Volatile
    private var attributedTo: Role? = null

    /**
     * From RemoteControlHandler, before it applies a `guide.move`: the guide changes that follow come from [role]'s
     * request, until the gesture's final state has been sent (Design 2.5.1, `guide.state` with `by`).
     */
    fun attribute(role: Role?) {
        attributedTo = role
    }

    /** Photographer: sends the guide image once and every state change while [scope] is active. */
    fun startAsSender(scope: CoroutineScope) {
        scope.launch {
            val peer = (session.state.value as? SessionState.Connected)?.peer
            var imageSent: String? = peer?.haveGuideId
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
                    if (guide.id != imageSent) imageSent = sendImage(guide)
                    sendState(guides.state.value, final = true)
                    if (guide.id != referenceSent) referenceSent = sendReference(guide)
                }
            }.collect { change ->
                val state = change.state
                val id = state.guideId
                if (id == null) {
                    throttle?.cancel()
                    queued = null
                    imageSent = null
                    referenceSent = null
                    sendClear()
                    return@collect
                }
                if (id != imageSent) {
                    val current = guides.guide.value
                    if (current != null && current.id == id) imageSent = sendImage(current)
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
    }

    /** Subject: applies incoming guide messages to [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        scope.launch {
            var assembler: GuideImageAssembler? = null
            var referenceAssembler: GuideReferenceAssembler? = null
            var pending: SessionMessage.GuideStateUpdate? = null
            session.incoming.collect { message ->
                when (message) {
                    is SessionMessage.GuideImageBegin -> {
                        assembler = GuideImageAssembler.start(message)
                        if (assembler ==
                            null
                        ) {
                            Log.w(TAG, "Refused guide image ${message.guideId}: ${message.bytes} bytes")
                        }
                    }

                    is SessionMessage.GuideImageChunk -> {
                        val current = assembler ?: return@collect
                        if (!current.add(message)) {
                            Log.w(TAG, "Dropped guide image ${message.guideId}: chunk ${message.index} out of order")
                            assembler = null
                        }
                    }

                    is SessionMessage.GuideImageEnd -> {
                        val current = assembler ?: return@collect
                        assembler = null
                        val begin = current.begin
                        val bytes = current.finish(message)
                        if (bytes == null) {
                            Log.w(TAG, "Dropped guide image ${begin.guideId}: size or CRC mismatch")
                            return@collect
                        }
                        val guide = withContext(dispatcher) { codec.decode(begin.guideId, begin.format, bytes) }
                        if (guide == null) {
                            Log.w(TAG, "Dropped guide image ${begin.guideId}: not a ${begin.format} image")
                            return@collect
                        }
                        mirror.setGuide(guide)
                        // Another guide's reference photo is not this one's.
                        if (mirrorReference.reference.value?.guideId != guide.id) mirrorReference.set(null)
                        Timings.mark("guide.applied", "image ${guide.id} ${bytes.size} B")
                        pending?.takeIf { it.state.guideId == guide.id }?.let { apply(it) }
                        pending = null
                    }

                    is SessionMessage.GuideStateUpdate -> {
                        val id = message.state.guideId
                        when {
                            id == null -> clearMirror()

                            mirror.guide.value?.id == id -> apply(message)

                            // The image is still on its way (chunks are bigger than a step): keep the newest state.
                            else -> pending = message
                        }
                    }

                    is SessionMessage.GuideReferenceBegin -> {
                        referenceAssembler = GuideReferenceAssembler.start(message)
                        if (referenceAssembler == null) {
                            Log.w(TAG, "Refused reference ${message.guideId}: ${message.bytes} bytes")
                        }
                    }

                    is SessionMessage.GuideReferenceChunk -> {
                        val current = referenceAssembler ?: return@collect
                        if (!current.add(message)) {
                            Log.w(TAG, "Dropped reference ${message.guideId}: chunk ${message.index} out of order")
                            referenceAssembler = null
                        }
                    }

                    is SessionMessage.GuideReferenceEnd -> {
                        val current = referenceAssembler ?: return@collect
                        referenceAssembler = null
                        val begin = current.begin
                        val bytes = current.finish(message)
                        if (bytes == null) {
                            Log.w(TAG, "Dropped reference ${begin.guideId}: size or CRC mismatch")
                            return@collect
                        }
                        val photo = withContext(dispatcher) { references.decode(begin.format, bytes) }
                        if (photo == null) {
                            Log.w(TAG, "Dropped reference ${begin.guideId}: not a ${begin.format} image")
                            return@collect
                        }
                        mirrorReference.set(GuideReference(begin.guideId, photo))
                        Timings.mark("guide.applied", "reference ${begin.guideId} ${bytes.size} B")
                    }

                    is SessionMessage.GuideClear -> {
                        assembler = null
                        referenceAssembler = null
                        pending = null
                        clearMirror()
                    }

                    else -> Unit
                }
            }
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

    private companion object {
        const val TAG = "PixGuideSync"
        const val STEP_INTERVAL_MS = 50L
        const val CACHED_IMAGES = 2
    }
}
