// AI-generated with Claude Code, 2026-10-01, reviewed by Dongje Park
package com.lastpenguin.pix.guide

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** One applied change. [final] is true when a gesture ended (Design 2.5.1: last value goes on the reliable channel). */
data class GuideChange(val state: GuideState, val final: Boolean)

/**
 * Single source of truth for one guide (Design 2.1).
 * The photographer's guide and the subject's mirror are separate instances.
 */
interface GuideRepository {
    val guide: StateFlow<ReferenceGuide?>
    val state: StateFlow<GuideState>

    /** Every applied change, for guide sync. */
    val changes: SharedFlow<GuideChange>

    /** null removes the guide. A new guide starts at the default position. */
    fun setGuide(guide: ReferenceGuide?)

    fun update(final: Boolean, change: (GuideState) -> GuideState)
}
