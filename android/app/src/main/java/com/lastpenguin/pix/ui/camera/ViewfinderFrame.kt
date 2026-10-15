// AI-generated with ChatGPT Codex and Claude Code, 2026-10-15, reviewed by Dongje Park
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
