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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the subject's guide the same as the photographer's (Design 2.5.1: guide.image.*, guide.state, guide.clear).
 *
 * Photographer ([startAsSender]): when the session is connected, sends the guide image unless the subject's `hello`
 * said it already has it, then the current state; after that, every change from [guides]. A new guide sends its
 * image first. Gesture steps go on the realtime channel at most every [STEP_INTERVAL_MS] (the newest wins), the
 * final value of a gesture at once on the reliable channel, and a removed guide as `guide.clear`.
 *
 * Subject ([startAsReceiver]): rebuilds the image from its chunks, checks it, derives the outline from the cutout's
 * alpha ([GuideImageCodec]), and writes image and state into [mirror]. A state that arrives before its image is
 * kept and applied once the image is there. Stale `seq` is already dropped by the session.
 * Owner: Real-time (#9).
 */
class GuideSyncer(
    private val session: SessionManager,
    /** The photographer's guide, read when sending. */
    private val guides: GuideRepository,
    /** The subject's copy, written when receiving. */
    private val mirror: GuideRepository,
    private val codec: GuideImageCodec,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Encoded images by guide id, so a reconnecting subject or a replaced subject does not cost another encode. */
    private val encoded = LinkedHashMap<String, EncodedGuideImage>()

    /** Photographer: sends the guide image once and every state change while [scope] is active. */
    fun startAsSender(scope: CoroutineScope) {
        scope.launch {
            val peerHas = (session.state.value as? SessionState.Connected)?.peer?.haveGuideId
            var imageSent: String? = peerHas
            val guide = guides.guide.value
            if (guide == null) {
                sendClear()
            } else {
                if (guide.id != peerHas) imageSent = sendImage(guide)
                sendState(guides.state.value, final = true)
            }

            var queued: GuideState? = null
            var throttle: Job? = null
            guides.changes.collect { change ->
                val state = change.state
                val id = state.guideId
                if (id == null) {
                    throttle?.cancel()
                    queued = null
                    imageSent = null
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
            }
        }
    }

    /** Subject: applies incoming guide messages to [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        scope.launch {
            var assembler: GuideImageAssembler? = null
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

                    is SessionMessage.GuideClear -> {
                        assembler = null
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

    private fun sendState(state: GuideState, final: Boolean) {
        session.send(SessionMessage.GuideStateUpdate(state, final))
        if (final) Timings.mark("guide.sent", "state ${state.guideId} final")
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
