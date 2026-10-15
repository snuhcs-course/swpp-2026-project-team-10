// AI-generated with ChatGPT Codex and Claude Code, 2026-10-15, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [GuideRepository] kept in memory for the app process only (Design 2.8, Caching).
 * [frameAspect] is the current frame's width / height (AD-10), which every change is clamped to; when it changes,
 * an `update(final = true) { it }` clamps the guide to the new frame (Design 2.6.2).
 * Writers are serialized so changes are based on the latest guide and emitted in the same order.
 * Update callbacks only compute the next state; they must not call back into the repository.
 * Bitmaps are borrowed: removing/replacing a guide releases our reference without recycling images still in use by a View.
 * Owner: Camera/Overlay (#6).
 */
class InMemoryGuideRepository(
    private val frameAspect: () -> Float = { GuideGeometry.DEFAULT_FRAME_ASPECT },
) : GuideRepository {

    private val _guide = MutableStateFlow<ReferenceGuide?>(null)
    override val guide: StateFlow<ReferenceGuide?> = _guide.asStateFlow()

    private val _state = MutableStateFlow(GuideState())
    override val state: StateFlow<GuideState> = _state.asStateFlow()

    // Non-suspending writers must not lose a final event when a collector falls behind. This buffer grows on demand;
    // with no subscribers, replay = 0 retains nothing. Sync collectors should hand off network work without waiting here.
    private val _changes = MutableSharedFlow<GuideChange>(extraBufferCapacity = Int.MAX_VALUE)
    override val changes: SharedFlow<GuideChange> = _changes.asSharedFlow()

    @Synchronized
    override fun setGuide(guide: ReferenceGuide?) {
        val initial = GuideState(guideId = guide?.id)
        // Validate before publishing either value, so an invalid replacement leaves the previous guide intact.
        val state = if (guide == null) initial else GuideGeometry.clamp(initial, guide.aspect, frameAspect())
        _guide.value = guide
        publish(state, final = true)
    }

    @Synchronized
    override fun update(final: Boolean, change: (GuideState) -> GuideState) {
        val guide = _guide.value ?: return
        // Image identity belongs to setGuide; an edit cannot attach state to an image we do not have.
        val requested = change(_state.value).copy(guideId = guide.id)
        publish(GuideGeometry.clamp(requested, guide.aspect, frameAspect()), final)
    }

    private fun publish(state: GuideState, final: Boolean) {
        _state.value = state
        // StateFlow conflates equal values, but the end of a gesture must still reach GuideSyncer.
        check(_changes.tryEmit(GuideChange(state, final))) { "Guide change buffer exhausted" }
    }
}
