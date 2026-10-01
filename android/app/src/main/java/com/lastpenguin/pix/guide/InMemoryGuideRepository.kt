package com.lastpenguin.pix.guide

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [GuideRepository] kept in memory for the app process only (Design 2.8, Caching).
 * Owner: Camera/Overlay (#6).
 */
class InMemoryGuideRepository : GuideRepository {

    private val _guide = MutableStateFlow<ReferenceGuide?>(null)
    override val guide: StateFlow<ReferenceGuide?> = _guide.asStateFlow()

    private val _state = MutableStateFlow(GuideState())
    override val state: StateFlow<GuideState> = _state.asStateFlow()

    private val _changes = MutableSharedFlow<GuideChange>(extraBufferCapacity = 64)
    override val changes: SharedFlow<GuideChange> = _changes.asSharedFlow()

    override fun setGuide(guide: ReferenceGuide?) {
        TODO("#6: store the guide, reset the state to the default position, emit a GuideChange")
    }

    override fun update(final: Boolean, change: (GuideState) -> GuideState) {
        TODO("#6: apply the change, clamp with GuideGeometry.clamp, emit a GuideChange")
    }
}
