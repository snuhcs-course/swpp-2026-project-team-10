// AI-generated with Claude Code, 2026-11-20, reviewed by Jaewan Park
package com.lastpenguin.pix.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * [TelemetryBackend] on Firebase (Design 2.8, Telemetry): usage events to Analytics, errors to Crashlytics, which also
 * catches crashes and freezes (ANRs) by itself. No user ID is ever set, so nothing ties a report to a Pix account.
 * Needs a device and the app's Firebase project; checked by hand (Testing Documentation 2.1.3).
 */
class FirebaseTelemetry(context: Context) : TelemetryBackend {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun setCollection(on: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(on)
        analytics.setAnalyticsCollectionEnabled(on)
    }

    override fun deleteUnsent() = crashlytics.deleteUnsentReports()

    override fun log(name: String, params: Map<String, Any>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is Long -> bundle.putLong(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(name, bundle)
    }

    override fun record(error: Throwable) = crashlytics.recordException(error)
}
