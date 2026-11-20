// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import java.util.Collections
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Records what reaches [Telemetry] during one test, then puts the do-nothing sink back, so no test sees another's
 * events (Testing Documentation 2.1.8). Use as `@get:Rule val telemetry = TelemetryRecorder()`.
 */
class TelemetryRecorder : TestWatcher() {
    /** In the order sent; read from the test's thread, written from any. */
    val events: MutableList<TelemetryEvent> = Collections.synchronizedList(mutableListOf())
    val errors: MutableList<Pair<String, Throwable>> = Collections.synchronizedList(mutableListOf())

    private val sink = object : TelemetrySink {
        override fun event(event: TelemetryEvent) {
            events += event
        }

        override fun error(where: String, error: Throwable) {
            errors += where to error
        }
    }

    /** The events of type [T], in order. */
    inline fun <reified T : TelemetryEvent> sent(): List<T> = synchronized(events) { events.filterIsInstance<T>() }

    override fun starting(description: Description) = Telemetry.install(sink)

    override fun finished(description: Description) = Telemetry.reset()
}
