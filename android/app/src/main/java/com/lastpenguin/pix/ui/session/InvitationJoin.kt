// AI-generated with Claude Code, 2026-11-05, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import com.lastpenguin.pix.account.PixUser
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.session.SessionState

/**
 * Where the session an accepted invitation started stands, for the screen that accepted it: Invitation, or See their
 * camera's received invitations (FR-6.4, FR-6.17). Both follow `SessionViewModel.state`, not its transitions, so a
 * session that connected or ended while the screen was stopped still decides what follows when it comes back.
 */
enum class InvitationJoin {
    /** This phone has its place: Subject view. */
    JOINED,

    /** The photographer left between the accept and this phone taking its place. */
    GONE,

    /** Ended on this phone, by a cancel or a leave: nothing to say. */
    CLOSED,

    /** Ended for any other reason: "Couldn't connect". */
    FAILED,
    ;

    companion object {
        /** What [state] means for that session; null while it is still on its way. */
        fun of(state: SessionState): InvitationJoin? = when (state) {
            is SessionState.Connecting -> JOINED.takeIf { state.sessionId != null }

            is SessionState.Connected, is SessionState.Reconnecting -> JOINED

            is SessionState.Ended -> when (state.reason) {
                EndReason.NOT_FOUND, EndReason.EXPIRED -> GONE
                EndReason.CANCELLED, EndReason.LEFT -> CLOSED
                else -> FAILED
            }

            else -> null
        }
    }
}

/**
 * Takes the place an accept gave, with the friend who invited as the partner. False, with nothing changed, while
 * another session is open here: one opened after *Accept* was tapped, so the invitation is answered and gone for this
 * phone.
 */
fun SessionViewModel.joinAccepted(invitationId: String, place: SessionPlace, from: PixUser): Boolean =
    joinInvitation(invitationId, place, SessionPartner(from.userId, from.displayName))
