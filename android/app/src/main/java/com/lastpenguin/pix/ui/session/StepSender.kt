// AI-generated with Claude Code, 2026-10-16, reviewed by Sungmin Jo
package com.lastpenguin.pix.ui.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sends a gesture's values the way Design 2.6.5 wants them, for zoom, brightness, and guide moves alike: the first
 * step goes out at once, further steps at most every [intervalMs] with the newest winning, and the final value at
 * once; [send] does the sending and gets the `final` flag. While a gesture runs ([adjusting]), the screen shows its
 * own values and lets the photographer's echoes wait; the first echo after the final value wins.
 */
internal class StepSender<T : Any>(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val send: (value: T, final: Boolean) -> Unit,
) {
    /** True from the first step until the final value or a cancellation. */
    var adjusting = false
        private set

    private var queued: T? = null
    private var job: Job? = null

    /** A step of the gesture; the newest one waiting goes out when the interval is over. */
    fun step(value: T) {
        adjusting = true
        if (job?.isActive == true) {
            queued = value
            return
        }
        send(value, false)
        job = scope.launch {
            while (true) {
                delay(intervalMs)
                val next = queued ?: break
                queued = null
                send(next, false)
            }
        }
    }

    /** The gesture's last value, sent at once; the gesture is over. */
    fun finish(value: T) {
        cancel()
        send(value, true)
    }

    /** The gesture is over without a value to send: rejected, disconnected, or interrupted. */
    fun cancel() {
        adjusting = false
        queued = null
        job?.cancel()
        job = null
    }
}
