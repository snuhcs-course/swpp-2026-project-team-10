package com.lastpenguin.pix.core

import android.os.SystemClock
import android.util.Log

/**
 * Named timestamps in logcat under one tag, so the NFR latencies can be read without extra tools (Design 2.8).
 * Examples: `seg.start`/`seg.end`, `pose.first`, `guide.sent`/`guide.applied`, `rtt`.
 * Read them with `adb logcat -s PixTimings`.
 */
object Timings {
    const val TAG = "PixTimings"

    /** Logs [name] with the time since boot in milliseconds and an optional [detail]. */
    fun mark(name: String, detail: String? = null) {
        val elapsed = SystemClock.elapsedRealtime()
        Log.d(TAG, if (detail == null) "$name t=$elapsed" else "$name t=$elapsed $detail")
    }
}
