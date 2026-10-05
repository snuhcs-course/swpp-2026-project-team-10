package com.lastpenguin.pix.guide

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A stand-in for [InMemoryGuideRepository] until #6 (P11) fills it in, so *Use this guide* works on the phone
 * ("a fake until P11 lands", #5). It stores the guide and applies changes as given, without
 * GuideGeometry.clamp. When #6 is merged, delete this file and put InMemoryGuideRepository back in AppContainer.
 * Owner: PM (#5).
 */
class InterimGuideRepository : GuideRepository {

    private val _guide = MutableStateFlow<ReferenceGuide?>(null)
    override val guide: StateFlow<ReferenceGuide?> = _guide.asStateFlow()

    private val _state = MutableStateFlow(GuideState())
    override val state: StateFlow<GuideState> = _state.asStateFlow()

    private val _changes = MutableSharedFlow<GuideChange>(extraBufferCapacity = 64)
    override val changes: SharedFlow<GuideChange> = _changes.asSharedFlow()

    override fun setGuide(guide: ReferenceGuide?) {
        _guide.value = guide
        // A new guide starts at the default position (GuideRepository.setGuide).
        _state.value = GuideState(guideId = guide?.id)
        _changes.tryEmit(GuideChange(_state.value, final = true))
    }

    override fun update(final: Boolean, change: (GuideState) -> GuideState) {
        _state.value = change(_state.value)
        _changes.tryEmit(GuideChange(_state.value, final))
    }
}
