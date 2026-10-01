package com.lastpenguin.pix.session

import com.lastpenguin.pix.guide.GuideRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps the subject's guide the same as the photographer's (Design 2.5.1: guide.image.*, guide.state, guide.clear).
 * Owner: Server/AI/Sync (#9).
 */
class GuideSyncer(
    private val session: SessionManager,
    /** The photographer's guide, read when sending. */
    private val guides: GuideRepository,
    /** The subject's copy, written when receiving. */
    private val mirror: GuideRepository,
) {
    /** Photographer: sends the guide image once and every state change while [scope] is active. */
    fun startAsSender(scope: CoroutineScope) {
        TODO("#9: guides.changes → guide.state; a new guide → guide.image.begin/chunk/end")
    }

    /** Subject: applies incoming guide messages to [mirror] while [scope] is active. */
    fun startAsReceiver(scope: CoroutineScope) {
        TODO("#9: rebuild the outline from the cutout's alpha (OutlineExtractor), drop stale seq")
    }
}
