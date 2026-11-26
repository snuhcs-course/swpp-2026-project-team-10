// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park and Dongje Park
package com.lastpenguin.pix.session

import com.lastpenguin.pix.camera.FlashMode
import com.lastpenguin.pix.camera.ShutterTimer
import com.lastpenguin.pix.session.protocol.SessionMessage

/**
 * What the photographer did on their own camera, which the subject's phone says in a line (FR-7.4, #292), as the
 * photographer's phone says the subject's ([RemoteAction]). Each goes as `camera.did` once, when the action ends.
 * The photographer's own focus is not here: it goes as `camera.focus`, since the subject's phone draws its box too.
 * Owner: Real-time (#292).
 */
sealed interface PhotographerAction {
    /** A pinch's last value, a quick step, or a zoom action: the ratio asked for. */
    data class Zoom(val ratio: Float) : PhotographerAction

    /** The end of a drag for the brightness: the exposure index it left. */
    data class Brightness(val index: Int) : PhotographerAction

    data class Flash(val mode: FlashMode) : PhotographerAction

    /** A tap on the menu's Timer: what the timer is now (FR-1.17, #318). */
    data class Timer(val timer: ShutterTimer) : PhotographerAction

    /** The end of a drag or a pinch that moved the guide. */
    data object GuideMove : PhotographerAction

    /** A guide put on the camera from a photo or the library. */
    data object Guide : PhotographerAction

    /** *Hide* ([visible] false) or *Show* on the guide's toolbar (FR-3.6). */
    data class GuideVisible(val visible: Boolean) : PhotographerAction

    /** The end of a drag on the opacity slider: the [opacity] it left, 0.1–0.9 (FR-3.4). */
    data class GuideOpacity(val opacity: Float) : PhotographerAction

    /** ✕ on the guide's toolbar took the guide off the camera. */
    data object GuideRemove : PhotographerAction

    /** *Undo* put the removed guide back where it was. */
    data object GuideRestore : PhotographerAction
}

private const val ZOOM = "zoom"
private const val BRIGHTNESS = "brightness"
private const val FLASH = "flash"
private const val TIMER = "timer"
private const val GUIDE_MOVE = "guide.move"
private const val GUIDE = "guide"
private const val GUIDE_HIDE = "guide.hide"
private const val GUIDE_SHOW = "guide.show"
private const val GUIDE_OPACITY = "guide.opacity"
private const val GUIDE_REMOVE = "guide.remove"
private const val GUIDE_RESTORE = "guide.restore"

/** The message that tells the subject's phone of this action. */
fun PhotographerAction.toMessage(): SessionMessage.PhotographerDid = when (this) {
    is PhotographerAction.Zoom -> SessionMessage.PhotographerDid(ZOOM, zoom = ratio)
    is PhotographerAction.Brightness -> SessionMessage.PhotographerDid(BRIGHTNESS, exposure = index)
    is PhotographerAction.Flash -> SessionMessage.PhotographerDid(FLASH, flash = mode)
    is PhotographerAction.Timer -> SessionMessage.PhotographerDid(TIMER, seconds = timer.seconds)
    PhotographerAction.GuideMove -> SessionMessage.PhotographerDid(GUIDE_MOVE)
    PhotographerAction.Guide -> SessionMessage.PhotographerDid(GUIDE)
    is PhotographerAction.GuideVisible -> SessionMessage.PhotographerDid(if (visible) GUIDE_SHOW else GUIDE_HIDE)
    is PhotographerAction.GuideOpacity -> SessionMessage.PhotographerDid(GUIDE_OPACITY, opacity = opacity)
    PhotographerAction.GuideRemove -> SessionMessage.PhotographerDid(GUIDE_REMOVE)
    PhotographerAction.GuideRestore -> SessionMessage.PhotographerDid(GUIDE_RESTORE)
}

/** The action a message names, or null for one this phone does not know or cannot show, as a newer phone may send. */
fun SessionMessage.PhotographerDid.toAction(): PhotographerAction? = when (action) {
    ZOOM -> PhotographerAction.Zoom(zoom).takeIf { zoom.isFinite() && zoom > 0f }
    BRIGHTNESS -> PhotographerAction.Brightness(exposure)
    FLASH -> PhotographerAction.Flash(flash)
    TIMER -> ShutterTimer.ofSeconds(seconds)?.let { PhotographerAction.Timer(it) }
    GUIDE_MOVE -> PhotographerAction.GuideMove
    GUIDE -> PhotographerAction.Guide
    GUIDE_HIDE -> PhotographerAction.GuideVisible(false)
    GUIDE_SHOW -> PhotographerAction.GuideVisible(true)
    GUIDE_OPACITY -> PhotographerAction.GuideOpacity(opacity).takeIf { opacity.isFinite() && opacity > 0f }
    GUIDE_REMOVE -> PhotographerAction.GuideRemove
    GUIDE_RESTORE -> PhotographerAction.GuideRestore
    else -> null
}
