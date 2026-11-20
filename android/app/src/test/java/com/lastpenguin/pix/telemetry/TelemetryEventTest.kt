// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import com.lastpenguin.pix.generation.GenerationError
import com.lastpenguin.pix.generation.Situation
import com.lastpenguin.pix.guide.GuideSource
import com.lastpenguin.pix.session.EndReason
import com.lastpenguin.pix.session.Role
import com.lastpenguin.pix.session.SessionEntry
import com.lastpenguin.pix.session.SessionPartner
import com.lastpenguin.pix.session.SessionPlace
import com.lastpenguin.pix.telemetry.TelemetryEvent.GuideMade.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * The usage events as Analytics receives them (Design 2.8, Telemetry; NFR-13).
 * Partitions: each event's name and fields; each optional field present and absent (a pose set that ended with poses
 * or with each failure, a try that connected or failed, a photo the camera took or never took); every session entry
 * and every choice of each enum field, which become lowercase names; Analytics' limits on every event built from every
 * choice (name and keys of at most 40 letters, digits, and `_`, starting with a letter; none of its reserved names or
 * prefixes; at most 25 fields; only `String` values of at most 100 characters and `Long` values); and no field holding
 * what a person wrote or a name, such as a partner's or a room code, whatever the session's entry carries.
 */
class TelemetryEventTest {

    @Test
    fun guideMade_namesItsSourceOutcomeAndTime() {
        val event = TelemetryEvent.GuideMade(GuideSource.GALLERY, Outcome.NO_PERSON, ms = 1_234)

        assertEquals("guide_made", event.name)
        assertEquals(mapOf("source" to "gallery", "outcome" to "no_person", "ms" to 1_234L), event.params)
    }

    @Test
    fun posesEnded_withPoses_isOk_andCountsThem() {
        val event = TelemetryEvent.PosesEnded(
            described = false,
            situation = Situation.DEFAULT,
            failure = null,
            ready = 3,
            total = 4,
            ms = 21_000,
        )

        assertEquals("poses_ended", event.name)
        assertEquals(
            mapOf(
                "request" to "suggested",
                "situation" to Situation.DEFAULT.apiName,
                "outcome" to "ok",
                "ready" to 3L,
                "total" to 4L,
                "ms" to 21_000L,
            ),
            event.params,
        )
    }

    @Test
    fun posesEnded_withAFailure_namesIt_andNeverTheDescription() {
        val event = TelemetryEvent.PosesEnded(
            described = true,
            situation = Situation.DEFAULT,
            failure = GenerationError.RATE_LIMITED,
            ready = 0,
            total = 0,
            ms = 300,
        )

        assertEquals("described", event.params["request"])
        assertEquals("rate_limited", event.params["outcome"])
    }

    @Test
    fun sessionConnect_connected_andFailed() {
        val connected = TelemetryEvent.SessionConnect(SessionEntry.NewRoom, Role.PHOTOGRAPHER, failure = null, ms = 900)
        val failed = TelemetryEvent.SessionConnect(
            SessionEntry.RoomCode("123456"),
            Role.SUBJECT,
            EndReason.CONNECTION_LOST,
            ms = 15_000,
        )

        assertEquals("session_connect", connected.name)
        assertEquals(
            mapOf("entry" to "new_room", "role" to "photographer", "outcome" to "connected", "ms" to 900L),
            connected.params,
        )
        assertEquals(
            mapOf("entry" to "room_code", "role" to "subject", "outcome" to "connection_lost", "ms" to 15_000L),
            failed.params,
        )
    }

    @Test
    fun sessionConnect_namesEveryEntry_andNoneOfWhatItCarries() {
        val place = SessionPlace(sessionId = "session-secret", rejoinToken = "token-secret", iceServers = emptyList())
        val partner = SessionPartner(userId = "user-secret", displayName = "Test Account 1")
        val entries = mapOf(
            SessionEntry.NewRoom to "new_room",
            SessionEntry.RoomCode("123456") to "room_code",
            SessionEntry.Rejoin("123456") to "rejoin",
            SessionEntry.Invite("invitation-secret", place, partner) to "invite",
            SessionEntry.Invitation("invitation-secret", place, partner) to "invitation",
        )

        entries.forEach { (entry, label) ->
            val params = TelemetryEvent.SessionConnect(entry, Role.SUBJECT, failure = null, ms = 1).params
            assertEquals(label, params["entry"])
            val values = params.values.joinToString(" ")
            listOf("123456", "secret", "Test Account 1").forEach { assertFalse(values, it in values) }
        }
    }

    @Test
    fun sessionDrop_recoveredOrNot() {
        val recovered = TelemetryEvent.SessionDrop(Role.SUBJECT, recovered = true, ms = 4_000)
        val lost = TelemetryEvent.SessionDrop(Role.PHOTOGRAPHER, recovered = false, ms = 10_000)

        assertEquals("session_drop", recovered.name)
        assertEquals(mapOf("role" to "subject", "recovered" to 1L, "ms" to 4_000L), recovered.params)
        assertEquals(mapOf("role" to "photographer", "recovered" to 0L, "ms" to 10_000L), lost.params)
    }

    @Test
    fun photoTaken_withTheShutterTime_andWithoutOne() {
        val saved = TelemetryEvent.PhotoTaken(saved = true, shutterMs = 120, ms = 900, inSession = true)
        val failed = TelemetryEvent.PhotoTaken(saved = false, shutterMs = null, ms = 50, inSession = false)

        assertEquals("photo_taken", saved.name)
        assertEquals(
            mapOf("outcome" to "saved", "shutter_ms" to 120L, "ms" to 900L, "in_session" to 1L),
            saved.params,
        )
        assertEquals(mapOf("outcome" to "failed", "ms" to 50L, "in_session" to 0L), failed.params)
    }

    @Test
    fun everyEvent_fitsAnalyticsLimits() {
        val events = everyEvent()
        // Built from every choice, so a renamed enum entry or a new event is checked too.
        assertTrue(events.size > 50)
        events.forEach { event ->
            assertTrue(event.name, NAME.matches(event.name))
            assertFalse(event.name, event.name in RESERVED_EVENTS)
            assertTrue(event.name, event.params.size <= 25)
            event.params.forEach { (key, value) ->
                assertTrue("${event.name}.$key", NAME.matches(key))
                assertFalse("${event.name}.$key", RESERVED_PREFIXES.any { key.startsWith(it) })
                when (value) {
                    is Long -> Unit
                    is String -> assertTrue("${event.name}.$key", value.length <= 100)
                    else -> throw AssertionError("${event.name}.$key is a ${value.javaClass.simpleName}")
                }
            }
        }
    }

    private fun everyEvent(): List<TelemetryEvent> = buildList {
        GuideSource.entries.forEach { source ->
            Outcome.entries.forEach { add(TelemetryEvent.GuideMade(source, it, ms = 1)) }
        }
        Situation.entries.forEach { situation ->
            (GenerationError.entries + null).forEach { failure ->
                listOf(true, false).forEach { described ->
                    add(TelemetryEvent.PosesEnded(described, situation, failure, ready = 1, total = 4, ms = 1))
                }
            }
        }
        listOf(SessionEntry.NewRoom, SessionEntry.RoomCode("123456"), SessionEntry.Rejoin("123456")).forEach { entry ->
            Role.entries.forEach { role ->
                (EndReason.entries + null).forEach { add(TelemetryEvent.SessionConnect(entry, role, it, ms = 1)) }
            }
        }
        Role.entries.forEach { role ->
            listOf(true, false).forEach { add(TelemetryEvent.SessionDrop(role, it, ms = 1)) }
        }
        listOf(true, false).forEach { saved ->
            listOf(1L, null).forEach { add(TelemetryEvent.PhotoTaken(saved, it, ms = 1, inSession = saved)) }
        }
    }

    private companion object {
        val NAME = Regex("[a-zA-Z][a-zA-Z0-9_]{0,39}")

        /** Analytics' own events (its *Automatically collected events* and reserved names). */
        val RESERVED_EVENTS = setOf(
            "ad_activeview",
            "ad_click",
            "ad_exposure",
            "ad_impression",
            "ad_query",
            "ad_reward",
            "adunit_exposure",
            "app_background",
            "app_clear_data",
            "app_exception",
            "app_install",
            "app_remove",
            "app_store_refund",
            "app_update",
            "app_upgrade",
            "error",
            "first_open",
            "first_visit",
            "in_app_purchase",
            "notification_dismiss",
            "notification_foreground",
            "notification_open",
            "notification_receive",
            "os_update",
            "screen_view",
            "session_start",
            "session_start_with_rollout",
            "user_engagement",
        )
        val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")
    }
}
