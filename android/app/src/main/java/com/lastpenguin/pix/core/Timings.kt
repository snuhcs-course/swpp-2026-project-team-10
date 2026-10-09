// AI-generated with ChatGPT Codex and Claude Code, 2026-10-01, reviewed by Dongje Park
package com.lastpenguin.pix.core

import android.os.SystemClock
import android.util.Log

/**
 * Named timestamps in logcat under one tag, so the NFR latencies can be read without extra tools (Design 2.8).
 * Examples: `seg.start`/`seg.end`, `pose.first`, `guide.sent`/`guide.applied`, `rtt`.
 * Times are this phone's `elapsedRealtime`, so never subtract them across phones directly: align two phones' logs
 * with the `ping.received` and `rtt` lines first (RtcSessionManager).
 */
object Timings {
    const val TAG = "PixTimings"

    /** Logs [name] with the current time and an optional [detail]. */
    fun mark(name: String, detail: String? = null) {
        Log.d(TAG, "${SystemClock.elapsedRealtime()} $name${detail?.let { " $it" }.orEmpty()}")
    }
}
