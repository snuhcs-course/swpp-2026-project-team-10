// AI-generated with Claude Code, 2026-11-23, reviewed by Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.guide.ReferenceGuide

/**
 * The guide image, and its reference photo, that this phone last got as the subject (Design 2.5.1). It outlives the
 * session, in memory only, so the next `hello` names them and the photographer does not send them again; a
 * `guide.clear` drops them. Subject view never draws from here: it draws the mirror, which [GuideSyncer] empties when
 * the session ends and fills from here once a `guide.state` names this guide (#268).
 */
class HeldGuide {
    @Volatile
    var guide: ReferenceGuide? = null

    /** The reference photo last received; it may be another guide's until that guide's image is here. */
    @Volatile
    var reference: GuideReference? = null

    /** For `hello`'s `haveGuideId`. */
    val guideId: String? get() = guide?.id

    /**
     * For `hello`'s `haveReferenceId`: the held guide, when its reference photo is here too, received or its own, as
     * a guide made on this phone has.
     */
    val referenceId: String?
        get() {
            val held = guide ?: return null
            return held.id.takeIf { reference?.guideId == held.id || held.reference != null }
        }
}
