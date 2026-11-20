// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry

/**
 * The usage events Pix sends (Design 2.8, Telemetry), one class each, so nothing else can be sent: each field is a
 * choice from a fixed list, a count, or a time in milliseconds, never text a person wrote or a name (NFR-13). [params]
 * holds only `String` and `Long` values, the two kinds Analytics keeps. Each event answers one requirement measured on
 * real phones, beside the `Timings` marks of a test run (Testing Documentation 2.2.3).
 */
sealed interface TelemetryEvent {
    /** Analytics' event name: lowercase letters and `_`, none of its reserved names. */
    val name: String

    val params: Map<String, Any>

    /** A guide from a gallery photo or a generated pose, made or not, and how long the step took (NFR-3). */
    data class GuideMade(val source: GuideSource, val outcome: Outcome, val ms: Long) : TelemetryEvent {
        enum class Outcome { MADE, NO_PERSON, FAILED }

        override val name = "guide_made"
        override val params: Map<String, Any> =
            mapOf("source" to source.label, "outcome" to outcome.label, "ms" to ms)
    }

    /**
     * A set of poses at its end (FR-4.7, NFR-4): suggested or described, the situation, how it ended ([failure] null
     * when a pose came), how many of its poses came, and how long it took.
     */
    data class PosesEnded(
        val described: Boolean,
        val situation: Situation,
        val failure: GenerationError?,
        val ready: Int,
        val total: Int,
        val ms: Long,
    ) : TelemetryEvent {
        override val name = "poses_ended"
        override val params: Map<String, Any> = mapOf(
            "request" to if (described) "described" else "suggested",
            "situation" to situation.apiName,
            "outcome" to (failure?.label ?: "ok"),
            "ready" to ready.toLong(),
            "total" to total.toLong(),
            "ms" to ms,
        )
    }

    /**
     * One try to connect two phones (NFR-10): how it was entered, this phone's role, and whether the link came up
     * ([failure] null) or why not, with the time from the subject's start or the photographer's peer joining.
     */
    data class SessionConnect(
        val entry: SessionEntry,
        val role: Role,
        val failure: EndReason?,
        val ms: Long,
    ) : TelemetryEvent {
        override val name = "session_connect"
        override val params: Map<String, Any> = mapOf(
            "entry" to entry.label,
            "role" to role.label,
            "outcome" to (failure?.label ?: "connected"),
            "ms" to ms,
        )
    }

    /** A link that dropped while connected (NFR-11): whether it came back within the 10 s, and when. */
    data class SessionDrop(val role: Role, val recovered: Boolean, val ms: Long) : TelemetryEvent {
        override val name = "session_drop"
        override val params: Map<String, Any> =
            mapOf("role" to role.label, "recovered" to recovered.flag, "ms" to ms)
    }

    /**
     * A shutter press (NFR-19): whether the photo was saved, when the camera took it ([shutterMs], null if it never
     * did) and when it ended, from the press, and whether it was taken in a session.
     */
    data class PhotoTaken(
        val saved: Boolean,
        val shutterMs: Long?,
        val ms: Long,
        val inSession: Boolean,
    ) : TelemetryEvent {
        override val name = "photo_taken"
        override val params: Map<String, Any> = buildMap {
            put("outcome", if (saved) "saved" else "failed")
            shutterMs?.let { put("shutter_ms", it) }
            put("ms", ms)
            put("in_session", inSession.flag)
        }
    }
}

private val Enum<*>.label: String get() = name.lowercase()

private val Boolean.flag: Long get() = if (this) 1L else 0L

private val SessionEntry.label: String
    get() = when (this) {
        SessionEntry.NewRoom -> "new_room"
        is SessionEntry.RoomCode -> "room_code"
        is SessionEntry.Rejoin -> "rejoin"
        is SessionEntry.Invite -> "invite"
        is SessionEntry.Invitation -> "invitation"
    }
