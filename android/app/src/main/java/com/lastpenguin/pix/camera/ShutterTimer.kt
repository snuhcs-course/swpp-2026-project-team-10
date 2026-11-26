// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The shutter's timer (FR-1.17): off, or the seconds from the shutter to the photo. */
enum class ShutterTimer(val seconds: Int) {
    OFF(0),
    THREE(3),
    TEN(10),
    ;

    /** The choice after this one at a tap on the menu's Timer: off, 3 s, 10 s, and off again. */
    fun next(): ShutterTimer = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The timer of [seconds], or null for a number of seconds this Pix does not offer. */
        fun ofSeconds(seconds: Int): ShutterTimer? = entries.firstOrNull { it.seconds == seconds }
    }
}

/**
 * The timer as chosen (FR-1.17), kept while Pix runs, which starts with it off. One choice for the photographer's
 * camera, whoever sets it (#318): `CameraViewModel` for the menu on Camera, and `RemoteControlHandler` for the menu
 * on the subject's phone, which reaches it here while the photographer is on another screen, as it reaches the flash.
 */
class ShutterTimerChoice {
    private val _timer = MutableStateFlow(ShutterTimer.OFF)

    val timer: StateFlow<ShutterTimer> = _timer.asStateFlow()

    fun set(timer: ShutterTimer) {
        _timer.value = timer
    }
}
