// AI-generated with ChatGPT Codex and Claude Code, 2026-10-26, reviewed by Dongje Park
package com.lastpenguin.pix.ui.camera

/** The same fit-centered image area as CameraX and PreviewView, in the chosen aspect ratio (AD-10). */
internal data class ViewfinderFrame(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** [frameAspect] is the frame's width / height: 3:4 by default, 9:16, or 1:1. */
internal fun fitViewfinderFrame(viewWidth: Float, viewHeight: Float, frameAspect: Float = 3f / 4f): ViewfinderFrame {
    if (!viewWidth.isFinite() || !viewHeight.isFinite() || viewWidth <= 0f || viewHeight <= 0f ||
        !frameAspect.isFinite() || frameAspect <= 0f
    ) {
        return ViewfinderFrame(0f, 0f, 0f, 0f)
    }
    val scale = minOf(viewWidth / frameAspect, viewHeight)
    val width = frameAspect * scale
    val height = scale
    return ViewfinderFrame((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
}

/**
 * The part of a screen [screenHeight] tall that the camera's image fills (R&S 6.2), from [top] to [bottom], and how
 * much of it lies under the top bar ([underTopBar]) and under the controls below ([underControls]).
 */
internal data class PreviewArea(val top: Float, val bottom: Float, val underTopBar: Float, val underControls: Float)

/**
 * The image fills the screen's [width] at [frameAspect] (width / height). It sits between the top bar ([topBar] tall)
 * and the controls ([controls] tall) when it fits there, in the middle of that space. A taller one grows up under the
 * top bar first, to the top of the screen, and only what it still needs down under the controls; one taller than the
 * whole screen takes all of it and narrows to fit.
 */
internal fun previewArea(
    width: Float,
    screenHeight: Float,
    topBar: Float,
    controls: Float,
    frameAspect: Float,
): PreviewArea {
    val between = PreviewArea(topBar, screenHeight - controls, 0f, 0f)
    if (!width.isFinite() || width <= 0f || !frameAspect.isFinite() || frameAspect <= 0f) return between
    val missing = width / frameAspect - (between.bottom - between.top)
    if (missing <= 0f) return between
    val up = minOf(missing, topBar)
    val down = minOf(missing - up, controls)
    return PreviewArea(topBar - up, between.bottom + down, up, down)
}
