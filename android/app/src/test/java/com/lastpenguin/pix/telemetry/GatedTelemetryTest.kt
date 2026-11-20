// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import com.lastpenguin.pix.session.Role
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * What reaches Firebase (Design 2.8, Telemetry; FR-10.6, NFR-13), through a fake backend.
 * Partitions: the stored choice not read yet (nothing sent); allowed in a build that allows it (events and errors
 * sent, collection on); allowed in a build that does not, a debug build (nothing, collection off); turned off after
 * being on (nothing more, collection off, the held reports deleted) and on again; the same place's error twice (sent
 * once) and two places' (each once); an error's message, its causes' messages, a chain that loops back, and a chain
 * longer than the limit (each scrubbed, the stack traces kept, the chain cut).
 * The facade: no sink (nothing, no failure), a sink installed (reached), and reset (nothing again).
 */
class GatedTelemetryTest {
    private val backend = FakeTelemetryBackend()
    private val drop = TelemetryEvent.SessionDrop(Role.SUBJECT, recovered = true, ms = 10)

    @Test
    fun beforeTheChoiceIsRead_sendsNothing() {
        val telemetry = GatedTelemetry(backend, buildAllows = true)

        telemetry.event(drop)
        telemetry.error("session.peer", IOException())

        assertTrue(backend.logged.isEmpty())
        assertTrue(backend.recorded.isEmpty())
        assertNull(backend.collection)
    }

    @Test
    fun allowed_inABuildThatAllowsIt_sendsEventsAndErrors() {
        val telemetry = GatedTelemetry(backend, buildAllows = true)

        telemetry.setAllowed(true)
        telemetry.event(drop)
        telemetry.error("session.peer", IOException())

        assertEquals(true, backend.collection)
        assertEquals(listOf(drop.name to drop.params), backend.logged)
        assertEquals(1, backend.recorded.size)
        assertEquals(0, backend.deletes)
    }

    @Test
    fun allowed_inADebugBuild_sendsNothing_andKeepsCollectionOff() {
        val telemetry = GatedTelemetry(backend, buildAllows = false)

        telemetry.setAllowed(true)
        telemetry.event(drop)
        telemetry.error("session.peer", IOException())

        assertEquals(false, backend.collection)
        assertTrue(backend.logged.isEmpty())
        assertTrue(backend.recorded.isEmpty())
    }

    @Test
    fun turnedOff_sendsNothingMore_andDeletesHeldReports_thenOnAgain() {
        val telemetry = GatedTelemetry(backend, buildAllows = true)
        telemetry.setAllowed(true)

        telemetry.setAllowed(false)
        telemetry.event(drop)
        telemetry.error("camera.bind", IllegalStateException())

        assertEquals(false, backend.collection)
        assertEquals(1, backend.deletes)
        assertTrue(backend.logged.isEmpty())
        assertTrue(backend.recorded.isEmpty())

        telemetry.setAllowed(true)
        telemetry.event(drop)
        assertEquals(true, backend.collection)
        assertEquals(1, backend.logged.size)
    }

    @Test
    fun aPlacesError_goesOncePerProcess_andEachPlaceOnce() {
        val telemetry = GatedTelemetry(backend, buildAllows = true)
        telemetry.setAllowed(true)

        repeat(3) { telemetry.error("camera.frame", IllegalStateException()) }
        telemetry.error("camera.bind", IllegalStateException())

        assertEquals(
            listOf("camera.frame: java.lang.IllegalStateException", "camera.bind: java.lang.IllegalStateException"),
            backend.recorded.map { it.message },
        )
    }

    @Test
    fun anError_losesEveryMessage_andKeepsItsClassesAndStackTraces() {
        val root = IOException("connect to 10.0.0.7 for Test Account 1 failed")
        val error = IllegalStateException("room 123456 of test1", root)

        val reported = ReportedError.of("session.signaling", error)

        assertEquals("session.signaling: java.lang.IllegalStateException", reported.message)
        assertArrayEquals(error.stackTrace, reported.stackTrace)
        val cause = reported.cause as ReportedError
        assertEquals("java.io.IOException", cause.message)
        assertArrayEquals(root.stackTrace, cause.stackTrace)
        assertNull(cause.cause)
        val text = generateSequence<Throwable>(reported) { it.cause }.joinToString { it.toString() }
        listOf("10.0.0.7", "Test Account 1", "123456", "test1").forEach { assertFalse(text, it in text) }
    }

    @Test
    fun aChainThatLoopsBack_orIsTooLong_isCut() {
        val first = IOException("a")
        val second = IOException("b", first)
        first.initCause(second)
        val long = (1..20).fold(IOException("0")) { cause, i -> IOException("$i", cause) }

        val looped = generateSequence<Throwable>(ReportedError.of("loop", first)) { it.cause }.count()
        val cut = generateSequence<Throwable>(ReportedError.of("long", long)) { it.cause }.count()

        assertEquals(9, looped)
        assertEquals(9, cut)
    }

    @Test
    fun theFacade_withoutASink_doesNothing_andReachesOneOnceInstalled_untilReset() {
        val telemetry = GatedTelemetry(backend, buildAllows = true).also { it.setAllowed(true) }
        Telemetry.event(drop)
        assertTrue(backend.logged.isEmpty())

        Telemetry.install(telemetry)
        try {
            Telemetry.event(drop)
            Telemetry.error("guide.make", IOException())
        } finally {
            Telemetry.reset()
        }
        Telemetry.event(drop)

        assertEquals(1, backend.logged.size)
        assertEquals(1, backend.recorded.size)
    }
}

/** Records what [GatedTelemetry] passes on; [collection] is null until it is set. */
class FakeTelemetryBackend : TelemetryBackend {
    var collection: Boolean? = null
    var deletes = 0
    val logged = mutableListOf<Pair<String, Map<String, Any>>>()
    val recorded = mutableListOf<Throwable>()

    override fun setCollection(on: Boolean) {
        collection = on
    }

    override fun deleteUnsent() {
        deletes++
    }

    override fun log(name: String, params: Map<String, Any>) {
        logged += name to params
    }

    override fun record(error: Throwable) {
        recorded += error
    }
}
