// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import androidx.annotation.VisibleForTesting

/**
 * Crash reports and usage events from release builds (Design 2.8, Telemetry; FR-10.6), called from anywhere like
 * [com.lastpenguin.pix.core.Timings]. `AppContainer.create` installs the real sink ([GatedTelemetry] over Firebase);
 * until then, and in every test that installs none, both calls do nothing. An event carries only the fields its class
 * names ([TelemetryEvent]), and an error only its classes and stack trace: never a photo, a description, a name, a Pix
 * ID, a room code, or an exception's message (NFR-13).
 */
object Telemetry {
    @Volatile
    private var sink: TelemetrySink = TelemetrySink.None

    fun install(sink: TelemetrySink) {
        this.sink = sink
    }

    /** Back to doing nothing; a test that installed a sink calls it after (`TelemetryRecorder`). */
    @VisibleForTesting
    fun reset() {
        sink = TelemetrySink.None
    }

    fun event(event: TelemetryEvent) = sink.event(event)

    /**
     * A failure the app survived, from a `catch` that would otherwise only reach logcat. [where] names the place, such
     * as `session.signaling`, and must be a fixed string, never built from data.
     */
    fun error(where: String, error: Throwable) = sink.error(where, error)
}

/** Where [Telemetry] sends to. */
interface TelemetrySink {
    fun event(event: TelemetryEvent)

    fun error(where: String, error: Throwable)

    /** Sends nothing: before `AppContainer.create`, and in tests. */
    object None : TelemetrySink {
        override fun event(event: TelemetryEvent) = Unit

        override fun error(where: String, error: Throwable) = Unit
    }
}
