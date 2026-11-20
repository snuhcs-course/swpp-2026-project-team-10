// AI-generated with Claude Code, 2026-11-20, reviewed by Sungmin Jo
package com.lastpenguin.pix.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who holds the photographer's zoom (FR-7.6, Design 2.6.5, #236): only one phone zooms at a time. The pinch that
 * starts first holds the zoom from its first step to its final value; the other phone's pinch changes nothing
 * meanwhile, and stays held back until its own fingers lift. It lives on the photographer's phone, which decides
 * (AD-7): `CameraViewModel` takes it for the photographer's pinch, `RemoteControlHandler` for the subject's and sends
 * each change to the subject's phone as `camera.zoom.hold`. A single final value (a quick step, a TalkBack action)
 * never takes it; it only checks that the other phone does not hold it.
 * Owner: Real-time (#10, #236).
 */
class ZoomHold {
    private val _holder = MutableStateFlow<Role?>(null)

    /** The phone whose pinch holds the zoom now, or null when the zoom is free. */
    val holder: StateFlow<Role?> = _holder.asStateFlow()

    /** True when [role] holds the zoom after this call: taken when it was free, or already its own. */
    fun take(role: Role): Boolean {
        if (_holder.compareAndSet(null, role)) return true
        return _holder.value == role
    }

    /** Frees the zoom if [role] holds it; another phone's hold stays. */
    fun release(role: Role) {
        _holder.compareAndSet(role, null)
    }

    /** True unless the other phone holds the zoom, for a single final value of [role]'s. */
    fun mayChange(role: Role): Boolean = _holder.value.let { it == null || it == role }
}
