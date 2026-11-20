// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import java.util.concurrent.ConcurrentHashMap

/**
 * The real [TelemetrySink] (Design 2.8, Telemetry): it sends to [backend] only while the user allows it (Settings,
 * FR-10.6) and the build does ([buildAllows], `BuildConfig.TELEMETRY`, false in debug builds), and nothing until the
 * stored choice is read. Each place's error goes once per process, as a [ReportedError] without any message.
 */
class GatedTelemetry(private val backend: TelemetryBackend, private val buildAllows: Boolean) : TelemetrySink {

    @Volatile
    private var on = false

    private val reported = ConcurrentHashMap.newKeySet<String>()

    /** Whether this run turned Crashlytics off: then it collects again only from a later start. */
    @Volatile
    private var crashlyticsOffThisRun = false

    /**
     * The Settings switch, applied once the stored choice is read and at each change. Off stops our events and
     * Analytics at once and turns Crashlytics off, whose reports from before are deleted. Crashlytics decides at each
     * start whether to send the reports it holds, so they are deleted at the first start with it off, and it is turned
     * on again only at a start, after that deletion: on again in the run that turned it off, it stays off until then.
     */
    fun setAllowed(allowed: Boolean) {
        val next = allowed && buildAllows
        on = next
        backend.setAnalytics(next)
        when {
            !next -> {
                backend.setCrashlytics(false)
                backend.deleteUnsent()
                crashlyticsOffThisRun = true
            }

            crashlyticsOffThisRun -> Unit

            else -> {
                if (!backend.crashlyticsOn) backend.deleteUnsent()
                backend.setCrashlytics(true)
            }
        }
    }

    override fun event(event: TelemetryEvent) {
        if (on) backend.log(event.name, event.params)
    }

    override fun error(where: String, error: Throwable) {
        if (on && reported.add(where)) backend.record(ReportedError.of(where, error))
    }
}

/** What [GatedTelemetry] needs from Firebase, so its rules run in JVM tests; `FirebaseTelemetry` in the app. */
interface TelemetryBackend {
    /** Analytics' collection, at once; Analytics keeps the answer for the next start. */
    fun setAnalytics(on: Boolean)

    /** Whether Crashlytics collects: its kept answer, or the manifest's. */
    val crashlyticsOn: Boolean

    /** Crashlytics' collection; Crashlytics keeps the answer for the next start. */
    fun setCrashlytics(on: Boolean)

    /**
     * Deletes the reports Crashlytics holds. It decides at each start whether to send them, and holds them only in a run
     * that started with it off, so only there does this delete anything.
     */
    fun deleteUnsent()

    fun log(name: String, params: Map<String, Any>)

    fun record(error: Throwable)
}

/**
 * An error as Crashlytics receives it: its place and its class, with the original stack trace, and each cause the same
 * way. A message can hold an address, a server's answer, or a name, so none is kept (NFR-13).
 */
class ReportedError private constructor(message: String, cause: Throwable?) : Exception(message, cause) {
    companion object {
        /** Deep enough for any chain Pix catches; a chain that loops back ends there too. */
        private const val MAX_CAUSES = 8

        fun of(where: String, error: Throwable): ReportedError = scrub("$where: ${error.javaClass.name}", error, 0)

        private fun scrub(message: String, error: Throwable, depth: Int): ReportedError {
            val cause = error.cause?.takeIf { it !== error && depth < MAX_CAUSES }
            return ReportedError(message, cause?.let { scrub(it.javaClass.name, it, depth + 1) })
                .also { it.stackTrace = error.stackTrace }
        }
    }
}
