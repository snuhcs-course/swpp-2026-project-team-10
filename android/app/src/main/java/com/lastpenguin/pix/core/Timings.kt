package com.lastpenguin.pix.core

import android.os.SystemClock
import android.util.Log

/**
 * Named timestamps in logcat under one tag, so the NFR latencies can be read without extra tools (Design 2.8).
 * Examples: `seg.start`/`seg.end`, `pose.first`, `guide.sent`/`guide.applied`, `rtt`.
 */
object Timings {
    const val TAG = "PixTimings"

    /** Logs [name] with the current time and an optional [detail]. */
    fun mark(name: String, detail: String? = null) {
        Log.d(TAG, "${SystemClock.elapsedRealtime()} $name${detail?.let { " $it" }.orEmpty()}")
    }
}
