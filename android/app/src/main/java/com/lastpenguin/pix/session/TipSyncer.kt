// AI-generated with Claude Code, 2026-11-17, reviewed by Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.generation.GuideTip
import com.lastpenguin.pix.generation.PoseTip
import com.lastpenguin.pix.generation.TipState
import com.lastpenguin.pix.session.protocol.SessionMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The photographer's tip on the subject's phone, with the guide it is for: shown only with that guide (FR-4.10). */
data class SharedTip(val guideId: String, val tip: PoseTip)

/**
 * The tip of the guide on the photographer's camera for the subject's phone (FR-4.10, #213; Design 2.5.1), which
 * shows it on a tap. The photographer sends the tip once it is there (`guide.tip`), and says so when the guide has
 * none or none any more (`guide.tip.clear`): no guide, a tip still asked for, one that failed, and a saved copy of
 * the guide, which keeps the guide's id and has no tip. The subject keeps the last one in [mirror], by guide id, so
 * the tip and the guide's image may come in either order. The tip is the server's text, in the photographer's screen
 * language.
 * Owner: Pose tips.
 */
class TipSyncer(
    private val session: SessionManager,
    /** The tip of the guide on Camera (`PoseTips.onCamera`). */
    private val onCamera: StateFlow<GuideTip?>,
) {
    private val _mirror = MutableStateFlow<SharedTip?>(null)

    /** On the subject: the photographer's tip, or null when the guide on their camera has none. */
    val mirror: StateFlow<SharedTip?> = _mirror.asStateFlow()

    /** The receiver whose end drops the tip; an older one that ends later leaves the newer session's tip. */
    private var receiver: CoroutineScope? = null

    /**
     * Sends the tip and each change of it while the subject is connected. A sender starts from nothing, so the one
     * started after a reconnect says what the guide has now, a tip or none, whatever the drop lost.
     */
    fun startAsSender(scope: CoroutineScope) {
        scope.launch {
            var sent: SessionMessage? = null
            onCamera.collect { tip ->
                val message = message(tip)
                if (message == sent) return@collect
                session.send(message)
                sent = message
            }
        }
    }

    /** Keeps the photographer's tip from the moment the subject joins, through a reconnect, until the session ends. */
    fun startAsReceiver(scope: CoroutineScope) {
        receiver = scope
        scope.launch {
            try {
                session.incoming.collect { message ->
                    when (message) {
                        is SessionMessage.GuideTip -> _mirror.value = shared(message)
                        SessionMessage.GuideTipClear -> _mirror.value = null
                        else -> Unit
                    }
                }
            } finally {
                if (receiver === scope) {
                    receiver = null
                    _mirror.value = null
                }
            }
        }
    }

    private companion object {
        /** Only a tip that is there is sent; one being asked for or failed is none for the subject. */
        fun message(tip: GuideTip?): SessionMessage {
            val ready = (tip?.state as? TipState.Ready)?.tip ?: return SessionMessage.GuideTipClear
            return SessionMessage.GuideTip(tip.guideId, ready.text)
        }

        /** The tip as received; one without words is no tip. */
        fun shared(message: SessionMessage.GuideTip): SharedTip? {
            val text = message.text.trim()
            return if (text.isEmpty()) null else SharedTip(message.guideId, PoseTip(text))
        }
    }
}
